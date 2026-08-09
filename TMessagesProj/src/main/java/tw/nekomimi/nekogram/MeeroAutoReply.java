package tw.nekomimi.nekogram;

import tw.nekomimi.nekogram.MeeroStrings;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
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
 *
 * v184 (batch 2B): the working heart moved into libmeerocore - exclusions,
 * per-chat cooldowns, the reply window with its weekday mask, the rule /
 * pool / night / general / default ladder with {name} substitution and the
 * emoji suffix, plus the persistent store itself (rules, exclusions, pool)
 * now live natively, persisted as an opaque seed-sealed blob the settings
 * screens pass through untouched. Java keeps the Telegram handshake and a
 * byte-identical legacy path for builds without the lib.
 */
public final class MeeroAutoReply {

    private MeeroAutoReply() {}

    private static final ConcurrentHashMap<Long, Long> lastReplyAt = new ConcurrentHashMap<>();
    private static volatile boolean started;
    private static volatile boolean nativeLoaded;

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
        // they also have a custom text rule. (native builds check it together
        // with the cooldown inside libmeerocore.)
        final boolean nativeCore = MeeroCore.ready();
        if (!nativeCore && isExcluded(dialogId)) return;

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

        // Gate 6: per-chat cooldown (native builds fold the exclusion check
        // in here; on pass the stamp lands immediately, before the delay, so
        // a burst of messages schedules exactly one reply).
        if (nativeCore) {
            ensureNativeLoaded();
            if (!MeeroCore.nArShouldReply(dialogId, now, NekoConfig.meeroAutoReplyCooldown.Int())) return;
        } else {
            Long last = lastReplyAt.get(dialogId);
            long cooldownMs = NekoConfig.meeroAutoReplyCooldown.Int() * 60_000L;
            if (last != null && now - last < cooldownMs) return;
            lastReplyAt.put(dialogId, now);
        }

        // Resolve the text NOW (like the old code did): what gets delayed is
        // only the send itself.
        final String text;
        if (nativeCore) {
            final boolean nightActive = NekoConfig.meeroAutoReplyWindow.Bool()
                    && NekoConfig.meeroAutoReplyNightTextOn.Bool() && isWithinWindow();
            text = MeeroCore.nArResolveText(dialogId,
                    NekoConfig.meeroAutoReplyPoolOn.Bool() ? 1 : 0,
                    nightActive ? 1 : 0,
                    NekoConfig.meeroAutoReplyText.String(),
                    NekoConfig.meeroAutoReplyNightText.String(),
                    MeeroStrings.s(20),
                    user != null && !TextUtils.isEmpty(user.first_name) ? user.first_name : "",
                    NekoConfig.meeroAutoReplyRandomEmoji.Bool() ? 1 : 0,
                    now);
            if (text == null) return; // native hiccup: never crash, just stay silent
        } else {
            text = resolveText(user, account, getRuleText(dialogId));
        }

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
        if (MeeroCore.ready()) {
            return MeeroCore.nArWindowPass(
                    NekoConfig.meeroAutoReplyWindow.Bool() ? 1 : 0,
                    NekoConfig.meeroAutoReplyWindowDays.Int(),
                    NekoConfig.meeroAutoReplyWindowStart.Int(),
                    NekoConfig.meeroAutoReplyWindowEnd.Int(),
                    System.currentTimeMillis());
        }
        if (!NekoConfig.meeroAutoReplyWindow.Bool()) return true;
        Calendar cal = Calendar.getInstance();
        // v104: window weekdays bitmask - Sunday is bit 0 ... Saturday bit 6.
        // A day whose bit is off means the window simply does not run that day.
        int dayBit = 1 << (cal.get(Calendar.DAY_OF_WEEK) - 1);
        if ((NekoConfig.meeroAutoReplyWindowDays.Int() & dayBit) == 0) return false;
        int start = NekoConfig.meeroAutoReplyWindowStart.Int();
        int end = NekoConfig.meeroAutoReplyWindowEnd.Int();
        if (start == end) return true;
        int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        if (start < end) return now >= start && now < end;
        return now >= start || now < end;
    }

    /** v104: optional night reply text - replaces the general text only while
     *  the reply window actively gates (its switch + days + hours all pass).
     *  A per-chat rule and the random pool still take precedence.
     *  (legacy fallback path only; native builds decide inside libmeerocore) */
    private static String nightTextIfActive() {
        if (!NekoConfig.meeroAutoReplyWindow.Bool()) return null;
        if (!NekoConfig.meeroAutoReplyNightTextOn.Bool()) return null;
        if (!isWithinWindow()) return null;
        String night = NekoConfig.meeroAutoReplyNightText.String();
        return TextUtils.isEmpty(night) ? null : night;
    }

    private static String resolveText(TLRPC.User user, int account, String ruleText) {
        String template = ruleText;
        // v103: random pool (general reply only - a per-chat rule always wins).
        if (TextUtils.isEmpty(template) && NekoConfig.meeroAutoReplyPoolOn.Bool()) {
            String pooled = randomPoolText();
            if (!TextUtils.isEmpty(pooled)) template = pooled;
        }
        if (TextUtils.isEmpty(template)) {
            String mainText = NekoConfig.meeroAutoReplyText.String();
            // v104: the night text swaps in for the general text inside the
            // active window; outside it (or unset) the general text stands.
            template = nightTextIfActive();
            if (TextUtils.isEmpty(template)) {
                template = mainText;
            }
        }
        if (TextUtils.isEmpty(template)) {
            template = MeeroStrings.s(20);
        }
        String firstName = "";
        if (user != null && !TextUtils.isEmpty(user.first_name)) {
            firstName = user.first_name;
        }
        String out = template.replace("{name}", firstName);
        // v103: optional random emoji suffix.
        if (NekoConfig.meeroAutoReplyRandomEmoji.Bool()) {
            out += " " + RANDOM_EMOJI[POOL_RANDOM.nextInt(RANDOM_EMOJI.length)];
        }
        return out;
    }

    // --- Random reply pool (MeeroX v103). JSON array of strings.
    // A per-chat rule text always beats the pool; the pool is only the
    // general reply. Random emoji suffix applies to every reply.

    private static final java.util.Random POOL_RANDOM = new java.util.Random();
    private static final String[] RANDOM_EMOJI = {"✅", "👌", "🌙", "⚡", "🙏", "💫", "☕", "🌟"};

    private static JSONArray readPool() {
        try {
            String raw = NekoConfig.meeroAutoReplyPool.String();
            if (!TextUtils.isEmpty(raw)) return new JSONArray(raw);
        } catch (Throwable ignore) {}
        return new JSONArray();
    }

    private static synchronized void writePool(JSONArray array) {
        NekoConfig.meeroAutoReplyPool.setConfigString(array == null ? "" : array.toString());
    }

    private static String randomPoolText() {
        JSONArray pool = readPool();
        if (pool.length() == 0) return null;
        return pool.optString(POOL_RANDOM.nextInt(pool.length()), null);
    }

    public static synchronized ArrayList<String> getPoolTexts() {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            ArrayList<String> out = new ArrayList<>();
            int n = MeeroCore.nArPoolCount();
            for (int i = 0; i < n; i++) {
                String s = MeeroCore.nArPoolAt(i);
                if (!TextUtils.isEmpty(s)) out.add(s);
            }
            return out;
        }
        ArrayList<String> out = new ArrayList<>();
        JSONArray pool = readPool();
        for (int i = 0; i < pool.length(); i++) {
            String s = pool.optString(i, "");
            if (!TextUtils.isEmpty(s)) out.add(s);
        }
        return out;
    }

    public static int getPoolCount() {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            return MeeroCore.nArPoolCount();
        }
        return readPool().length();
    }

    public static synchronized void addPoolText(String text) {
        if (TextUtils.isEmpty(text)) return;
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            MeeroCore.nArPoolAdd(text);
            persistNative();
            return;
        }
        JSONArray pool = readPool();
        pool.put(text);
        writePool(pool);
    }

    /** Replaces the text at index (screen keeps indexes stable within a session view). */
    public static synchronized void setPoolText(int index, String text) {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            MeeroCore.nArPoolSet(index, text);
            persistNative();
            return;
        }
        JSONArray pool = readPool();
        if (index < 0 || index >= pool.length()) return;
        try {
            pool.put(index, text);
        } catch (Throwable ignore) {}
        writePool(pool);
    }

    public static synchronized void removePoolText(int index) {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            if (index < 0 || index >= MeeroCore.nArPoolCount()) return;
            MeeroCore.nArPoolDel(index);
            persistNative();
            return;
        }
        JSONArray pool = readPool();
        if (index < 0 || index >= pool.length()) return;
        JSONArray out = new JSONArray();
        for (int i = 0; i < pool.length(); i++) {
            if (i != index) {
                String s = pool.optString(i, null);
                if (s != null) out.put(s);
            }
        }
        writePool(out);
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
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            return MeeroCore.nArRuleText(dialogId);
        }
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
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            ArrayList<Long> ids = new ArrayList<>();
            int n = MeeroCore.nArRuleCount();
            for (int i = 0; i < n; i++) ids.add(MeeroCore.nArRuleIdAt(i));
            return ids;
        }
        ArrayList<Long> ids = new ArrayList<>();
        JSONArray array = readRules();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o != null) ids.add(o.optLong("id"));
        }
        return ids;
    }

    public static int getRuleCount() {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            return MeeroCore.nArRuleCount();
        }
        return getRuleDialogIds().size();
    }

    public static synchronized void upsertRule(long dialogId, String text) {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            MeeroCore.nArUpsertRule(dialogId, TextUtils.isEmpty(text) ? null : text);
            persistNative();
            return;
        }
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
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            return MeeroCore.nArIsExcl(dialogId);
        }
        JSONArray array = readExclusions();
        for (int i = 0; i < array.length(); i++) {
            if (array.optLong(i, Long.MIN_VALUE) == dialogId) return true;
        }
        return false;
    }

    /** Stable snapshot of excluded dialog ids for the management screen. */
    public static synchronized ArrayList<Long> getExclusionIds() {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            ArrayList<Long> ids = new ArrayList<>();
            int n = MeeroCore.nArExclCount();
            for (int i = 0; i < n; i++) ids.add(MeeroCore.nArExclIdAt(i));
            return ids;
        }
        ArrayList<Long> ids = new ArrayList<>();
        JSONArray array = readExclusions();
        for (int i = 0; i < array.length(); i++) {
            long id = array.optLong(i, Long.MIN_VALUE);
            if (id != Long.MIN_VALUE) ids.add(id);
        }
        return ids;
    }

    public static int getExclusionCount() {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            return MeeroCore.nArExclCount();
        }
        return getExclusionIds().size();
    }

    public static synchronized void addExclusion(long dialogId) {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            if (MeeroCore.nArIsExcl(dialogId)) return;
            MeeroCore.nArAddExcl(dialogId);
            persistNative();
            return;
        }
        if (isExcluded(dialogId)) return;
        JSONArray array = readExclusions();
        array.put(dialogId);
        writeExclusions(array);
    }

    public static synchronized void removeExclusion(long dialogId) {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            MeeroCore.nArDelExcl(dialogId);
            persistNative();
            return;
        }
        JSONArray array = readExclusions();
        JSONArray out = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            long id = array.optLong(i, Long.MIN_VALUE);
            if (id != Long.MIN_VALUE && id != dialogId) out.put(id);
        }
        writeExclusions(out);
    }

    // --- v184 (batch 2B): native store lifecycle ---------------------------

    /** One-shot per process: decrypt the sealed store into native memory.
     *  On a fresh/tampered blob the legacy JSON keys are imported once, the
     *  sealed store is written, and the plaintext keys are dropped. */
    private static synchronized void ensureNativeLoaded() {
        if (nativeLoaded || !MeeroCore.ready()) return;
        nativeLoaded = true;
        String blob = NekoConfig.meeroAutoReplyStore.String();
        int r = MeeroCore.nArLoad(TextUtils.isEmpty(blob) ? null : blob);
        if (r != 1) {
            importLegacyToNative();
            persistNative();
        }
        // the sealed store is authoritative now - plaintext leftovers go away
        if (!TextUtils.isEmpty(NekoConfig.meeroAutoReplyRules.String())) {
            NekoConfig.meeroAutoReplyRules.setConfigString("");
        }
        if (!TextUtils.isEmpty(NekoConfig.meeroAutoReplyExclusions.String())) {
            NekoConfig.meeroAutoReplyExclusions.setConfigString("");
        }
        if (!TextUtils.isEmpty(NekoConfig.meeroAutoReplyPool.String())) {
            NekoConfig.meeroAutoReplyPool.setConfigString("");
        }
    }

    private static void persistNative() {
        if (!MeeroCore.ready()) return;
        String blob = MeeroCore.nArBlob();
        if (!TextUtils.isEmpty(blob)) {
            NekoConfig.meeroAutoReplyStore.setConfigString(blob);
        }
    }

    private static void importLegacyToNative() {
        JSONArray rules = readRules();
        for (int i = 0; i < rules.length(); i++) {
            JSONObject o = rules.optJSONObject(i);
            if (o == null) continue;
            String text = o.optString("text", "");
            if (!TextUtils.isEmpty(text)) {
                MeeroCore.nArUpsertRule(o.optLong("id"), text);
            }
        }
        JSONArray exclusions = readExclusions();
        for (int i = 0; i < exclusions.length(); i++) {
            long id = exclusions.optLong(i, Long.MIN_VALUE);
            if (id != Long.MIN_VALUE) MeeroCore.nArAddExcl(id);
        }
        JSONArray pool = readPool();
        for (int i = 0; i < pool.length(); i++) {
            String s = pool.optString(i, "");
            if (!TextUtils.isEmpty(s)) MeeroCore.nArPoolAdd(s);
        }
    }
}
