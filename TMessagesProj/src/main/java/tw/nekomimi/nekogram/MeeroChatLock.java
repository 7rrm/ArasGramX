package tw.nekomimi.nekogram;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.Base64;
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
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * MeeroX v106 + v107: per-chat lock ("قفل المحادثة / المحادثات المخفية").
 *
 * Locking a chat does (user-approved design):
 *  1) every entry into that ChatActivity is covered by an opaque gate and an
 *     unlock prompt pops on top - by default the system biometric/device lock,
 *     or the in-app 8-digit code when the user picks that method (v107). It
 *     never matters how the chat was opened (list, search, notification,
 *     link), because all of them create a ChatActivity. Leaving the chat
 *     (onFragmentDestroy) relocks instantly, so handing the phone over
 *     mid-session still needs the secret to get back in. Screen rotation
 *     also relocks (documented).
 *  2) v107: the chat is hidden from the main list, folders, archive and
 *     every search surface (results, messages, recents). It reappears ONLY
 *     inside the hidden-chats vault screen, reached from the bottom-bar
 *     chats long-press popup, behind one unlock per session.
 *  3) the chat is muted forever server-side (the official
 *     setDialogNotificationsSettings path), so system notifications reveal
 *     nothing; while the app is alive our own watcher posts a content-free
 *     "new message in a locked chat" notification instead. We remember which
 *     dialogs WE muted, and only those get their defaults restored on
 *     unlock - a chat the user muted by hand stays muted.
 *
 * The 8-digit code is stored as a salted SHA-256 hash in local config only;
 * there is intentionally NO recovery path for a forgotten code (disclosed to
 * the user in the settings info row).
 *
 * Master switch is OFF by default; while off, gateNeeded()/isHiddenDialogId()
 * are always false and behavior is exactly stock. Unlocked ids and the vault
 * session flag live in memory only.
 */
public final class MeeroChatLock {

    private MeeroChatLock() {}

    public static final int METHOD_SYSTEM = 0;
    public static final int METHOD_CODE8 = 1;

    private static final String CHANNEL_ID = "meero_chat_lock";
    private static final String GATE_TAG = "meero_gate_cover";
    private static final long THROTTLE_MS = 10_000L;

    private static final Set<Long> unlocked = Collections.synchronizedSet(new HashSet<Long>());
    private static final ConcurrentHashMap<Long, Long> lastNotifyAt = new ConcurrentHashMap<>();
    private static long promptingDialogId = Long.MIN_VALUE;
    private static volatile boolean vaultUnlocked;
    private static volatile boolean promptingVault;
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

    // ---------------- unlock method + 8-digit code (v107) ----------------

    /** 0 = system biometric/device lock (default), 1 = in-app 8-digit code. */
    public static int getMethod() {
        return NekoConfig.meeroChatLockMethod.Int() == METHOD_CODE8 ? METHOD_CODE8 : METHOD_SYSTEM;
    }

    public static void setMethod(int method) {
        NekoConfig.meeroChatLockMethod.setConfigInt(method == METHOD_CODE8 ? METHOD_CODE8 : METHOD_SYSTEM);
    }

    public static boolean hasCode() {
        return !TextUtils.isEmpty(NekoConfig.meeroChatLockCodeHash.String());
    }

    private static String digest(String saltB64, String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest((saltB64 + ":" + code).getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Stores the code as a salted SHA-256 hash. The raw code is never kept. */
    public static synchronized boolean setCode(String code) {
        if (code == null || code.length() != 8) return false;
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            String saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP);
            String hash = digest(saltB64, code);
            if (hash == null) return false;
            NekoConfig.meeroChatLockCodeSalt.setConfigString(saltB64);
            NekoConfig.meeroChatLockCodeHash.setConfigString(hash);
            return true;
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
            return false;
        }
    }

    public static boolean verifyCode(String code) {
        if (code == null || code.length() != 8 || !hasCode()) return false;
        String actual = digest(NekoConfig.meeroChatLockCodeSalt.String(), code);
        return NekoConfig.meeroChatLockCodeHash.String().equals(actual);
    }

    // ---------------- hiding locked chats from lists/search (v107) ----------------

    /** True only when hiding is actually active: master on AND at least one
     *  locked chat. Used for the bottom-bar popup entry. */
    public static boolean hasHiddenDialogs() {
        return NekoConfig.meeroChatLock.Bool() && !getLockedIds().isEmpty();
    }

    /** Cheap per-item check used by the search adapters. */
    public static boolean isHiddenDialogId(long dialogId) {
        return NekoConfig.meeroChatLock.Bool() && isListed(dialogId);
    }

    private static HashSet<Long> lockedIdSet() {
        HashSet<Long> set = new HashSet<>();
        JSONArray array = readIds(NekoConfig.meeroChatLockList.String());
        for (int i = 0; i < array.length(); i++) {
            long id = array.optLong(i, Long.MIN_VALUE);
            if (id != Long.MIN_VALUE) set.add(id);
        }
        return set;
    }

    /** Returns {@code src} untouched when hiding is inactive; otherwise a
     *  filtered copy with locked dialogs removed (callers keep their original
     *  list instance so nothing upstream is mutated). */
    public static ArrayList<TLRPC.Dialog> filterLocked(ArrayList<TLRPC.Dialog> src) {
        if (src == null || src.isEmpty() || !NekoConfig.meeroChatLock.Bool()) return src;
        HashSet<Long> ids = lockedIdSet();
        if (ids.isEmpty()) return src;
        ArrayList<TLRPC.Dialog> out = new ArrayList<>(src.size());
        boolean removed = false;
        for (TLRPC.Dialog d : src) {
            if (d != null && ids.contains(d.id)) {
                removed = true;
                continue;
            }
            out.add(d);
        }
        return removed ? out : src;
    }

    // ---------------- vault session (v107) ----------------

    public static boolean isVaultUnlocked() {
        return vaultUnlocked;
    }

    public static void markVaultUnlocked() {
        vaultUnlocked = true;
    }

    /** Called when the vault screen is destroyed - next entry asks again. */
    public static void lockVault() {
        vaultUnlocked = false;
        promptingVault = false; // never let a stale flag pin the gate shut
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

    /** Called from ChatActivity.onFragmentDestroy - instant relock on exit.
     *  v107: also drops the prompting flag for this chat - a destroyed
     *  fragment (e.g. screen rotation) can take its prompt with it, and a
     *  stale flag would otherwise keep the gate shut with no prompt. */
    public static void lockAgain(long dialogId) {
        unlocked.remove(dialogId);
        if (promptingDialogId == dialogId) {
            promptingDialogId = Long.MIN_VALUE;
        }
    }

    // ---------------- gate cover ----------------

    /** v107: gate title/hint follow the active unlock method. */
    public static CharSequence gateTitle() {
        return LocaleController.getString(R.string.MeeroChatLockGateTitle);
    }

    public static CharSequence gateHint() {
        return LocaleController.getString(getMethod() == METHOD_CODE8
                ? R.string.MeeroGateCodeHint : R.string.MeeroChatLockGateHint);
    }

    /** Opaque, theme-colored cover with a lock glyph, added on top of the
     *  chat content before history has any chance to flash. Tapping it
     *  re-fires the unlock prompt (second chance when a prompt was
     *  dismissed without finishing the fragment). v107: title/hint are
     *  parameterized so the vault screen can reuse the same cover, and the
     *  caller pins it as the LAST child so content can never cover it. */
    public static void attachGateCover(ViewGroup content, CharSequence titleText, CharSequence hintText, final Runnable onTap) {
        if (content == null || content.findViewWithTag(GATE_TAG) != null) return;
        Context ctx = content.getContext();

        FrameLayout cover = new FrameLayout(ctx);
        cover.setTag(GATE_TAG);
        cover.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cover.setClickable(true); // swallows every touch; a tap re-fires the prompt
        cover.setOnClickListener(v -> {
            if (onTap != null) {
                onTap.run();
            }
        });

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.baseline_lock_base_24);
        icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), android.graphics.PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(64), AndroidUtilities.dp(64));
        box.addView(icon, iconLp);

        TextView title = new TextView(ctx);
        title.setText(titleText);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = AndroidUtilities.dp(14);
        box.addView(title, titleLp);

        TextView sub = new TextView(ctx);
        sub.setText(hintText);
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

    // ---------------- unlock prompts (v107: shared by chat gate and vault) ----------------

    /** True when the device can actually ask for a biometric or the device
     *  lock. When false we fail OPEN (v106 policy, kept): refusing entry
     *  would just lock the owner out, because there is nothing to ask for. */
    public static boolean canAskSystem(Activity act) {
        try {
            if (Build.VERSION.SDK_INT < 23) return false;
            if (!(act instanceof FragmentActivity)) return false;
            return BiometricManager.from(act).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Throwable t) {
            return false;
        }
    }

    /** System biometric / device lock prompt, shared by the chat gate and
     *  the hidden-chats vault. */
    public static void authenticateSystem(Activity act, CharSequence titleText, CharSequence subtitleText,
                                          final Runnable onOk, final Runnable onCancel) {
        try {
            if (!canAskSystem(act)) {
                if (onCancel != null) onCancel.run();
                return;
            }
            final FragmentActivity fa = (FragmentActivity) act;
            Executor executor = ContextCompat.getMainExecutor(fa);
            BiometricPrompt prompt = new BiometricPrompt(fa, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errMsgId, @NonNull CharSequence errString) {
                    if (onCancel != null) onCancel.run();
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    if (onOk != null) onOk.run();
                }
            });
            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(titleText)
                    .setSubtitle(subtitleText)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();
            prompt.authenticate(info);
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
            if (onCancel != null) onCancel.run();
        }
    }

    /** Callback for {@link #promptCodeDialog}. onCode returns true when the
     *  entered code is accepted (dialog closes) or false to show the
     *  wrong-code error and keep the dialog open. */
    public interface CodeCallback {
        boolean onCode(String code);
        void onCancelled();
    }

    /** 8-digit numeric code dialog with LIVE validation: the moment the 8th
     *  digit lands the code is checked; a rejection clears the field and shows
     *  an inline error while the dialog - and the gate behind it - stay put.
     *  Only Cancel/back dismiss without success. (The fork's AlertDialog
     *  buttons always dismiss, so the positive-button route can't veto a
     *  wrong code - typing IS the submit.) */
    public static void promptCodeDialog(final Activity act, CharSequence titleText, CharSequence messageText,
                                        final CharSequence wrongText, final CodeCallback cb) {
        try {
            if (act == null || act.isFinishing()) {
                if (cb != null) cb.onCancelled();
                return;
            }
            Context ctx = act;
            FrameLayout container = new FrameLayout(ctx);
            final EditTextBoldCursor edit = new EditTextBoldCursor(ctx);
            edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
            edit.setTransformationMethod(PasswordTransformationMethod.getInstance());
            edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            edit.setGravity(Gravity.CENTER);
            edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
            edit.setHint("••••••••");
            container.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER, 20, 4, 20, 4));
            final boolean[] done = {false};
            final AlertDialog dlg = new AlertDialog.Builder(ctx)
                    .setTitle(titleText)
                    .setMessage(messageText)
                    .setView(container)
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), (d, w) -> {
                        AndroidUtilities.hideKeyboard(edit);
                        if (!done[0] && cb != null) {
                            done[0] = true;
                            cb.onCancelled();
                        }
                    })
                    .create();
            dlg.setOnCancelListener(d -> {
                AndroidUtilities.hideKeyboard(edit);
                if (!done[0] && cb != null) {
                    done[0] = true;
                    cb.onCancelled();
                }
            });
            edit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    try {
                        if (done[0] || s == null || s.length() != 8) return;
                        boolean accepted = cb == null || cb.onCode(s.toString());
                        if (accepted) {
                            done[0] = true;
                            AndroidUtilities.hideKeyboard(edit);
                            dlg.dismiss();
                        } else {
                            // dialog stays open; field resets for the next try
                            edit.setText("");
                            edit.setError(wrongText);
                        }
                    } catch (Throwable t) {
                        if (BuildVars.LOGS_ENABLED) FileLog.e(t);
                    }
                }
            });
            dlg.show();
            edit.requestFocus();
            AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(edit), 120);
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
            if (cb != null) cb.onCancelled();
        }
    }

    /** Called from ChatActivity.onResume; no-op unless the chat is locked
     *  and not yet unlocked this session. */
    public static void maybePromptGate(final org.telegram.ui.ChatActivity chat) {
        try {
            final long dialogId = chat.getDialogId();
            if (!gateNeeded(dialogId)) return;
            // v107 defense (user-reported): the cover must be the top-most
            // layer no matter when onResume fires. This is a no-op when it is
            // already attached, but re-pins it if anything removed/reordered
            // it, so chat content can never show through behind the prompt.
            View fv0 = chat.fragmentView;
            if (fv0 instanceof ViewGroup) {
                attachGateCover((ViewGroup) fv0, gateTitle(), gateHint(), () -> maybePromptGate(chat));
            }
            if (promptingDialogId == dialogId) return;
            final Activity act = chat.getParentActivity();
            if (act == null) return;

            if (getMethod() == METHOD_CODE8 && hasCode()) {
                promptingDialogId = dialogId;
                promptCodeDialog(act, gateTitle(),
                        LocaleController.getString(R.string.MeeroChatLockEnterCodeHint),
                        LocaleController.getString(R.string.MeeroChatLockCodeWrong),
                        new CodeCallback() {
                            @Override
                            public boolean onCode(String code) {
                                if (!verifyCode(code)) {
                                    return false; // dialog stays open with the error
                                }
                                promptingDialogId = Long.MIN_VALUE;
                                markUnlocked(dialogId);
                                View fv = chat.fragmentView;
                                if (fv instanceof ViewGroup) {
                                    removeGateCover((ViewGroup) fv);
                                }
                                return true;
                            }

                            @Override
                            public void onCancelled() {
                                promptingDialogId = Long.MIN_VALUE;
                                try {
                                    chat.finishFragment();
                                } catch (Throwable ignore) {}
                            }
                        });
                return;
            }

            // default: system biometric / device lock
            if (!canAskSystem(act)) {
                allowWithoutHardware(chat, dialogId);
                return;
            }
            promptingDialogId = dialogId;
            authenticateSystem(act,
                    LocaleController.getString(R.string.MeeroChatLockGateTitle),
                    LocaleController.getString(R.string.MeeroChatLockGateSubtitle),
                    () -> {
                        promptingDialogId = Long.MIN_VALUE;
                        markUnlocked(dialogId);
                        View fv = chat.fragmentView;
                        if (fv instanceof ViewGroup) {
                            removeGateCover((ViewGroup) fv);
                        }
                    },
                    () -> {
                        promptingDialogId = Long.MIN_VALUE;
                        try {
                            chat.finishFragment();
                        } catch (Throwable ignore) {}
                    });
        } catch (Throwable t) {
            promptingDialogId = Long.MIN_VALUE;
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }

    /** v107: gate for the hidden-chats vault screen. One successful unlock
     *  keeps the vault open until it is left; cancel closes the screen. */
    public static void maybePromptVault(final BaseFragment fragment) {
        try {
            if (!hasHiddenDialogs() || vaultUnlocked) return;
            if (promptingVault) return;
            View fv = fragment.fragmentView;
            if (fv instanceof ViewGroup) {
                attachGateCover((ViewGroup) fv,
                        LocaleController.getString(R.string.MeeroVaultTitle),
                        vaultGateHint(), () -> maybePromptVault(fragment));
            }
            final Activity act = fragment.getParentActivity();
            if (act == null) return;

            final Runnable onOk = () -> {
                promptingVault = false;
                markVaultUnlocked();
                View fv1 = fragment.fragmentView;
                if (fv1 instanceof ViewGroup) {
                    removeGateCover((ViewGroup) fv1);
                }
            };
            final Runnable onCancel = () -> {
                promptingVault = false;
                try {
                    fragment.finishFragment();
                } catch (Throwable ignore) {}
            };

            promptingVault = true;
            if (getMethod() == METHOD_CODE8 && hasCode()) {
                promptCodeDialog(act,
                        LocaleController.getString(R.string.MeeroVaultTitle),
                        LocaleController.getString(R.string.MeeroChatLockEnterCodeHint),
                        LocaleController.getString(R.string.MeeroChatLockCodeWrong),
                        new CodeCallback() {
                            @Override
                            public boolean onCode(String code) {
                                if (!verifyCode(code)) {
                                    return false;
                                }
                                onOk.run();
                                return true;
                            }

                            @Override
                            public void onCancelled() {
                                onCancel.run();
                            }
                        });
            } else if (canAskSystem(act)) {
                authenticateSystem(act,
                        LocaleController.getString(R.string.MeeroVaultTitle),
                        vaultGateHint(), onOk, onCancel);
            } else {
                // no system secret on the device at all - same fail-open
                // policy as the chat gate, otherwise the vault would be a
                // trap with no way in.
                onOk.run();
            }
        } catch (Throwable t) {
            promptingVault = false;
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }

    private static CharSequence vaultGateHint() {
        return LocaleController.getString(getMethod() == METHOD_CODE8
                ? R.string.MeeroGateCodeHint : R.string.MeeroVaultGateHint);
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
