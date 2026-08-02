package tw.nekomimi.nekogram;

import android.text.TextUtils;

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
import java.util.concurrent.ConcurrentHashMap;

/**
 * MeeroX v98: Auto-reply engine.
 *
 * Watches NotificationCenter.didReceiveNewMessages for every logged-in account
 * and, when every safety gate passes, sends one automatic reply in a private
 * chat after a small delay. Never marks anything read (ghost-safe), never
 * replies to groups/channels/bots/Saved Messages, and never replies while the
 * user is looking at that chat with the screen on.
 *
 * Session state (per-chat last reply time) lives in memory only: an app
 * restart resets cooldowns, which is the documented behavior.
 */
public final class MeeroAutoReply {

    private MeeroAutoReply() {}

    private static final ConcurrentHashMap<Long, Long> lastReplyAt = new ConcurrentHashMap<>();
    private static volatile boolean started;

    /** Idempotent. Call once from LaunchActivity.onCreate. */
    public static void start() {
        if (started) return;
        synchronized (MeeroAutoReply.class) {
            if (started) return;
            started = true;
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                if (!UserConfig.getInstance(account).isClientActivated()) continue;
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
        final String text = resolveText(user, account);
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

    private static String resolveText(TLRPC.User user, int account) {
        String template = NekoConfig.meeroAutoReplyText.String();
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
}
