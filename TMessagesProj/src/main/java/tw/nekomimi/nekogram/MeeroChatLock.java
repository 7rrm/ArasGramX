package tw.nekomimi.nekogram;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.json.JSONArray;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * MeeroX v106: per-chat lock ("قفل المحادثة").
 *
 * Locking a chat does two things (user-approved design):
 *  1) every entry into that ChatActivity is covered by an opaque gate and a
 *     BiometricPrompt (biometrics OR the device lock) pops on top - it never
 *     matters how the chat was opened (list, search, notification, link),
 *     because all of them create a ChatActivity. Leaving the chat
 *     (onFragmentDestroy) relocks instantly, so handing the phone over
 *     mid-session still needs the fingerprint to get back in. Screen
 *     rotation also relocks (documented).
 *  2) the chat is muted forever server-side (the official
 *     setDialogNotificationsSettings path), so system notifications reveal
 *     nothing; while the app is alive our own watcher posts a content-free
 *     "new message in a locked chat" notification instead. We remember which
 *     dialogs WE muted, and only those get their defaults restored on
 *     unlock - a chat the user muted by hand stays muted.
 *
 * Master switch is OFF by default; while off, gateNeeded() is always false
 * and behavior is exactly stock. Unlocked ids live in memory only.
 */
public final class MeeroChatLock {

    private MeeroChatLock() {}

    private static final String CHANNEL_ID = "meero_chat_lock";
    private static final String GATE_TAG = "meero_gate_cover";
    private static final long THROTTLE_MS = 10_000L;

    private static final Set<Long> unlocked = Collections.synchronizedSet(new HashSet<Long>());
    private static final ConcurrentHashMap<Long, Long> lastNotifyAt = new ConcurrentHashMap<>();
    private static long promptingDialogId = Long.MIN_VALUE;
    private static volatile boolean started;

    // ---------------- lock list ----------------

    private static JSONArray readIds(String raw) {
        try {
            if (!TextUtils.isEmpty(raw)) return new JSONArray(raw);
        } catch (Throwable ignore) {}
        return new JSONArray();
    }

    public static synchronized boolean isListed(long dialogId) {
        JSONArray array = readIds(NekoConfig.meeroChatLockList.String());
        for (int i = 0; i < array.length(); i++) {
            if (array.optLong(i, Long.MIN_VALUE) == dialogId) return true;
        }
        return false;
    }

    public static synchronized ArrayList<Long> getLockedIds() {
        ArrayList<Long> out = new ArrayList<>();
        JSONArray array = readIds(NekoConfig.meeroChatLockList.String());
        for (int i = 0; i < array.length(); i++) {
            long id = array.optLong(i, Long.MIN_VALUE);
            if (id != Long.MIN_VALUE) out.add(id);
        }
        return out;
    }

    private static boolean weMuted(long dialogId) {
        JSONArray array = readIds(NekoConfig.meeroChatLockMuted.String());
        for (int i = 0; i < array.length(); i++) {
            if (array.optLong(i, Long.MIN_VALUE) == dialogId) return true;
        }
        return false;
    }

    /** Adds the chat to the lock list and mutes it forever (server-side,
     *  user-approved) so nothing leaks into system notifications. */
    public static synchronized void addLocked(int account, long dialogId) {
        if (isListed(dialogId)) return;
        JSONArray array = readIds(NekoConfig.meeroChatLockList.String());
        array.put(dialogId);
        NekoConfig.meeroChatLockList.setConfigString(array.toString());
        try {
            NotificationsController.getInstance(account)
                    .setDialogNotificationsSettings(dialogId, 0, NotificationsController.SETTING_MUTE_FOREVER);
            JSONArray muted = readIds(NekoConfig.meeroChatLockMuted.String());
            muted.put(dialogId);
            NekoConfig.meeroChatLockMuted.setConfigString(muted.toString());
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }

    /** Removes the lock and restores notification defaults - but only if WE
     *  were the ones who muted it; a chat muted by hand stays muted. */
    public static synchronized void removeLocked(int account, long dialogId) {
        JSONArray array = readIds(NekoConfig.meeroChatLockList.String());
        JSONArray out = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            long id = array.optLong(i, Long.MIN_VALUE);
            if (id != Long.MIN_VALUE && id != dialogId) out.put(id);
        }
        NekoConfig.meeroChatLockList.setConfigString(out.toString());
        unlocked.remove(dialogId);
        if (weMuted(dialogId)) {
            try {
                NotificationsController.getInstance(account)
                        .setDialogNotificationsSettings(dialogId, 0, NotificationsController.SETTING_MUTE_UNMUTE);
            } catch (Throwable t) {
                if (BuildVars.LOGS_ENABLED) FileLog.e(t);
            }
            JSONArray muted = readIds(NekoConfig.meeroChatLockMuted.String());
            JSONArray kept = new JSONArray();
            for (int i = 0; i < muted.length(); i++) {
                long id = muted.optLong(i, Long.MIN_VALUE);
                if (id != Long.MIN_VALUE && id != dialogId) kept.put(id);
            }
            NekoConfig.meeroChatLockMuted.setConfigString(kept.toString());
        }
    }

    // ---------------- gate state ----------------

    /** The master switch AND the per-chat listing both have to agree. */
    public static boolean isEnabledFor(long dialogId) {
        return NekoConfig.meeroChatLock.Bool() && isListed(dialogId);
    }

    public static boolean gateNeeded(long dialogId) {
        return isEnabledFor(dialogId) && !unlocked.contains(dialogId);
    }

    public static void markUnlocked(long dialogId) {
        if (isEnabledFor(dialogId)) {
            unlocked.add(dialogId);
        }
    }

    /** Called from ChatActivity.onFragmentDestroy - instant relock on exit. */
    public static void lockAgain(long dialogId) {
        unlocked.remove(dialogId);
    }

    // ---------------- gate cover ----------------

    /** Opaque, theme-colored cover with a lock glyph, added on top of the
     *  chat content before history has any chance to flash. */
    public static void attachGateCover(ViewGroup content) {
        if (content == null || content.findViewWithTag(GATE_TAG) != null) return;
        Context ctx = content.getContext();

        FrameLayout cover = new FrameLayout(ctx);
        cover.setTag(GATE_TAG);
        cover.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cover.setClickable(true); // swallows every touch

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.baseline_lock_base_24);
        icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), android.graphics.PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(64), AndroidUtilities.dp(64));
        box.addView(icon, iconLp);

        TextView title = new TextView(ctx);
        title.setText(LocaleController.getString(R.string.MeeroChatLockGateTitle));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = AndroidUtilities.dp(14);
        box.addView(title, titleLp);

        TextView sub = new TextView(ctx);
        sub.setText(LocaleController.getString(R.string.MeeroChatLockGateHint));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = AndroidUtilities.dp(6);
        box.addView(sub, subLp);

        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        cover.addView(box, boxLp);

        content.addView(cover, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    public static void removeGateCover(ViewGroup content) {
        if (content == null) return;
        View cover = content.findViewWithTag(GATE_TAG);
        if (cover != null) {
            content.removeView(cover);
        }
    }

    // ---------------- biometric prompt ----------------

    /** Called from ChatActivity.onResume; no-op unless the chat is locked
     *  and not yet unlocked this session. */
    public static void maybePromptGate(final org.telegram.ui.ChatActivity chat) {
        try {
            final long dialogId = chat.getDialogId();
            if (!gateNeeded(dialogId)) return;
            if (promptingDialogId == dialogId) return;
            Activity act = chat.getParentActivity();
            if (!(act instanceof FragmentActivity)) return;
            if (Build.VERSION.SDK_INT < 23) {
                allowWithoutHardware(chat, dialogId);
                return;
            }
            int canAuth = BiometricManager.from(act).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
                // no device lock configured at all - there is nothing to ask
                // for, so refusing entry would just lock the user out
                allowWithoutHardware(chat, dialogId);
                return;
            }
            promptingDialogId = dialogId;
            final FragmentActivity fa = (FragmentActivity) act;
            Executor executor = ContextCompat.getMainExecutor(fa);
            BiometricPrompt prompt = new BiometricPrompt(fa, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errMsgId, @NonNull CharSequence errString) {
                    promptingDialogId = Long.MIN_VALUE;
                    try {
                        chat.finishFragment();
                    } catch (Throwable ignore) {}
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    promptingDialogId = Long.MIN_VALUE;
                    markUnlocked(dialogId);
                    View fv = chat.fragmentView;
                    if (fv instanceof ViewGroup) {
                        removeGateCover((ViewGroup) fv);
                    }
                }
            });
            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(LocaleController.getString(R.string.MeeroChatLockGateTitle))
                    .setSubtitle(LocaleController.getString(R.string.MeeroChatLockGateSubtitle))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();
            prompt.authenticate(info);
        } catch (Throwable t) {
            promptingDialogId = Long.MIN_VALUE;
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }

    private static void allowWithoutHardware(org.telegram.ui.ChatActivity chat, long dialogId) {
        markUnlocked(dialogId);
        View fv = chat.fragmentView;
        if (fv instanceof ViewGroup) {
            removeGateCover((ViewGroup) fv);
        }
    }

    // ---------------- generic notification watcher ----------------

    public static void start() {
        if (started) return;
        synchronized (MeeroChatLock.class) {
            if (started) return;
            started = true;
            // v100 timing-safe pattern again: attach everywhere, validate per event.
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
        if (!NekoConfig.meeroChatLock.Bool()) return;
        if (!UserConfig.getInstance(account).isClientActivated()) return;
        if (args == null || args.length < 3) return;

        long now = System.currentTimeMillis();
        long dialogId = (Long) args[0];
        @SuppressWarnings("unchecked")
        ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
        boolean scheduled = (Boolean) args[2];
        if (scheduled || messages == null) return;
        if (!isListed(dialogId)) return;

        boolean fresh = false;
        for (MessageObject msg : messages) {
            if (msg == null || msg.isOut()) continue;
            if (msg.messageOwner == null || msg.messageOwner.action != null) continue;
            if (now - msg.messageOwner.date * 1000L > 120_000L) continue;
            fresh = true;
            break;
        }
        if (!fresh) return;

        Long last = lastNotifyAt.get(dialogId);
        if (last != null && now - last < THROTTLE_MS) return;
        lastNotifyAt.put(dialogId, now);
        notifyLockedMessage();
    }

    private static void notifyLockedMessage() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        LocaleController.getString(R.string.MeeroChatLockTitle), NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }
            Intent intent = new Intent(ctx, LaunchActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.nagram_notification)
                    .setContentTitle(LocaleController.getString(R.string.MeeroChatLockGateTitle))
                    .setContentText(LocaleController.getString(R.string.MeeroChatLockNewMessage))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            NotificationManagerCompat.from(ctx).notify(("l:" + System.currentTimeMillis()).hashCode(), builder.build());
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }
}
