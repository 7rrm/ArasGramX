package tw.nekomimi.nekogram;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MeeroX v98-v100: Auto-reply engine.
 *
 * Watches NotificationCenter.didReceiveNewMessages for every account slot and,
 * when every safety gate passes, sends one automatic reply in a private chat
 * after a small delay. Never marks anything read (ghost-safe), never replies
 * to groups/channels/bots/Saved Messages, and never replies while the user is
 * looking at that chat with the screen on.
 *
 * v100 fix: observers now attach to every account slot unconditionally.
 * UserConfig only loads later (postInitApplication, launched by
 * LaunchActivity), so at Application.onCreate time isClientActivated() is
 * always false - the v99 registration gate left the engine with zero
 * observers and no replies whatsoever. An observer on an unused slot simply
 * never fires, and the account is still re-validated for every event.
 *
 * v100 feature: optional reply time window - outside its hours nothing is
 * sent. Disabled by default, keeping exact previous behavior.
 *
 * Session state (per-chat last reply time) lives in memory only: an app
 * restart resets cooldowns, which is the documented behavior.
 */
public final class MeeroAutoReply {

    private MeeroAutoReply() {}

    private static final ConcurrentHashMap<Long, Long> lastReplyAt = new ConcurrentHashMap<>();
    private static volatile boolean started;

    /** Idempotent. Called once from ApplicationLoader.onCreate so the engine
     *  is alive even when a push wakes the process in the background. */
    public static void start() {
        if (started) return;
        synchronized (MeeroAutoReply.class) {
            if (started) return;
            started = true;
            // v100: attach to EVERY slot unconditionally. At Application.onCreate
            // time the configs have not loaded yet (that happens in
            // postInitApplication from LaunchActivity), so isClientActivated()
            // is always false here - gating registration on it (v99) left the
            // engine with zero observers and no replies at all. An observer on
            // an unused slot simply never fires; the account is re-validated
            // for every event in onNewMessages.
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                NotificationCenter.getInstance(account).addObserver((id, account1, args) -> {
                    if (id == NotificationCenter.didReceiveNewMessages) {
                        onNewMessages(account1, args);
                    }
                }, NotificationCenter.didReceiveNewMessages);
            }
        }
    }

    private static void onNewMessages(int account, Object[] args) {
        // Gate 1: master switch (default off - explicit user opt-in).
        if (!NekoConfig.meeroAutoReply.Bool()) return;
        // The signed-in check happens per event, not at registration time:
        // configs are not loaded yet when start() runs at Application.onCreate.
        if (!UserConfig.getInstance(account).isClientActivated()) return;
        // v100: optional reply time window - nothing is sent outside its hours.
        if (!isWithinWindow()) return;
        if (args == null || args.length < 3) return;

        long now = System.currentTimeMillis();

        long dialogId = (Long) args[0];
        @SuppressWarnings("unchecked")
        ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
        boolean scheduled = (Boolean) args[2];

        // Gate 2: private real conversations only.
        if (scheduled) return;
        if (!DialogObject.isUserDialog(dialogId)) return;
        if (dialogId == UserConfig.getInstance(account).getClientUserId()) return; // Saved Messages
        if (dialogId == 777000) return; // Telegram service account

        // Gate 2.5 (v101): excluded people never receive any reply, even if
        // they also have a custom text rule.
        if (isExcluded(dialogId)) return;

        // Gate 3: at least one genuine, RECENT incoming content message.
        // (Ayu's deleted-history hook re-broadcasts old messages with this
        // same event - the 2-minute freshness gate filters those out.)
        boolean hasIncoming = false;
        if (messages != null) {
            for (MessageObject msg : messages) {
                if (msg == null || msg.isOut()) continue;
                if (msg.messageOwner == null || msg.messageOwner.action != null) continue; // service messages
                if (now - msg.messageOwner.date * 1000L > 120_000L) continue; // restored history
                hasIncoming = true;
                break;
            }
        }
        if (!hasIncoming) return;

        // Gate 4: never loop over bots (and no point replying to them).
        TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
        if (user != null && user.bot) return;

        // Gate 5: the user is literally looking at this chat right now.
        if (isChatVisible(dialogId)) return;

        // Gate 6: per-chat cooldown.
        Long last = lastReplyAt.get(dialogId);
        long cooldownMs = NekoConfig.meeroAutoReplyCooldown.Int() * 60_000L;
        if (last != null && now - last < cooldownMs) return;

        // Pass: schedule the reply. Mark the cooldown immediately so a burst
        // of messages schedules exactly one reply.
        lastReplyAt.put(dialogId, now);
        final String text = resolveText(user, account, getRuleText(dialogId));
        final int delayMs = Math.max(0, NekoConfig.meeroAutoReplyDelay.Int()) * 1000;
        final long finalDialogId = dialogId;
        final int finalAccount = account;
        AndroidUtilities.runOnUIThread(() -> {
            // Re-check the switch: the user might have turned it off inside
            // the delay window.
            if (!NekoConfig.meeroAutoReply.Bool()) return;
            SendMessagesHelper.getInstance(finalAccount)
                    .sendMessage(SendMessagesHelper.SendMessageParams.of(text, finalDialogId));
        }, delayMs);
    }

    /** v100: optional reply window. Disabled = reply around the clock (exact
     *  previous behavior). When enabled, replies are only sent inside
     *  [start, end) minutes-of-day; start later than end means the window
     *  crosses midnight (e.g. 23:00-08:00); equal values mean "all day".
     *  Messages arriving outside the window are skipped, not queued. */
    public static boolean isWithinWindow() {
        if (!NekoConfig.meeroAutoReplyWindow.Bool()) return true;
        int start = NekoConfig.meeroAutoReplyWindowStart.Int();
        int end = NekoConfig.meeroAutoReplyWindowEnd.Int();
        if (start == end) return true;
        Calendar cal = Calendar.getInstance();
        int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        if (start < end) return now >= start && now < end;
        return now >= start || now < end;
    }

    private static String resolveText(TLRPC.User user, int account, String ruleText) {
        String template = ruleText;
        if (TextUtils.isEmpty(template)) {
            template = NekoConfig.meeroAutoReplyText.String();
        }
        if (TextUtils.isEmpty(template)) {
            template = LocaleController.getString(R.string.MeeroAutoReplyDefaultText);
        }
        String firstName = "";
        if (user != null && !TextUtils.isEmpty(user.first_name)) {
            firstName = user.first_name;
        }
        return template.replace("{name}", firstName);
    }

    /** True only when the visible top fragment is that exact chat and the screen is on. */
    private static boolean isChatVisible(long dialogId) {
        if (!ApplicationLoader.isScreenOn) return false;
        try {
            BaseFragment fragment = LaunchActivity.getLastFragment();
            return fragment instanceof ChatActivity && ((ChatActivity) fragment).getDialogId() == dialogId;
        } catch (Throwable ignore) {
            return false;
        }
    }

    // --- Per-chat rules (MeeroX v99). JSON array: [{"id":123,"text":"..."}].
    // A rule only swaps the reply text for that chat; every other safety
    // gate (private-only, freshness, cooldown, not-while-viewing) still applies.

    private static JSONArray readRules() {
        try {
            String raw = NekoConfig.meeroAutoReplyRules.String();
            if (!TextUtils.isEmpty(raw)) return new JSONArray(raw);
        } catch (Throwable ignore) {}
        return new JSONArray();
    }

    private static synchronized void writeRules(JSONArray array) {
        NekoConfig.meeroAutoReplyRules.setConfigString(array == null ? "" : array.toString());
    }

    public static synchronized boolean hasRule(long dialogId) {
        return getRuleText(dialogId) != null;
    }

    public static synchronized String getRuleText(long dialogId) {
        JSONArray array = readRules();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o != null && o.optLong("id") == dialogId) {
                String text = o.optString("text", "");
                return TextUtils.isEmpty(text) ? null : text;
            }
        }
        return null;
    }

    /** Stable snapshot of rule dialog ids for the management screen. */
    public static synchronized ArrayList<Long> getRuleDialogIds() {
        ArrayList<Long> ids = new ArrayList<>();
        JSONArray array = readRules();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o != null) ids.add(o.optLong("id"));
        }
        return ids;
    }

    public static int getRuleCount() {
        return getRuleDialogIds().size();
    }

    public static synchronized void upsertRule(long dialogId, String text) {
        JSONArray array = readRules();
        JSONArray out = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null || o.optLong("id") == dialogId) continue;
            out.put(o);
        }
        if (!TextUtils.isEmpty(text)) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", dialogId);
                o.put("text", text);
                out.put(o);
            } catch (Throwable ignore) {}
        }
        writeRules(out);
    }

    public static synchronized void removeRule(long dialogId) {
        upsertRule(dialogId, null);
    }

    // --- Exclusions (MeeroX v101). JSON array of dialog ids: [123,456].
    // An excluded chat never receives any auto-reply - even if it has a
    // custom text rule. Removing the id restores normal behavior instantly.

    private static JSONArray readExclusions() {
        try {
            String raw = NekoConfig.meeroAutoReplyExclusions.String();
            if (!TextUtils.isEmpty(raw)) return new JSONArray(raw);
        } catch (Throwable ignore) {}
        return new JSONArray();
    }

    private static synchronized void writeExclusions(JSONArray array) {
        NekoConfig.meeroAutoReplyExclusions.setConfigString(array == null ? "" : array.toString());
    }

    public static synchronized boolean isExcluded(long dialogId) {
        JSONArray array = readExclusions();
        for (int i = 0; i < array.length(); i++) {
            if (array.optLong(i, Long.MIN_VALUE) == dialogId) return true;
        }
        return false;
    }

    /** Stable snapshot of excluded dialog ids for the management screen. */
    public static synchronized ArrayList<Long> getExclusionIds() {
        ArrayList<Long> ids = new ArrayList<>();
        JSONArray array = readExclusions();
        for (int i = 0; i < array.length(); i++) {
            long id = array.optLong(i, Long.MIN_VALUE);
            if (id != Long.MIN_VALUE) ids.add(id);
        }
        return ids;
    }

    public static int getExclusionCount() {
        return getExclusionIds().size();
    }

    public static synchronized void addExclusion(long dialogId) {
        if (isExcluded(dialogId)) return;
        JSONArray array = readExclusions();
        array.put(dialogId);
        writeExclusions(array);
    }

    public static synchronized void removeExclusion(long dialogId) {
        JSONArray array = readExclusions();
        JSONArray out = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            long id = array.optLong(i, Long.MIN_VALUE);
            if (id != Long.MIN_VALUE && id != dialogId) out.put(id);
        }
        writeExclusions(out);
    }
}
