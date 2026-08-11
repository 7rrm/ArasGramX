package tw.nekomimi.nekogram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;

/**
 * MeeroX: a pool of Telegram API credentials with automatic failover.
 *
 * Telegram rates each api_id, and the limits differ from one to the next. Id 4
 * - the official Android client's - is capped per device: the first sign-in on
 * a device succeeds and the second is refused before any code is sent, which
 * is exactly how "add another account" failed. Id 6, which NagramX ships, does
 * not carry that cap.
 *
 * v183 (batch 2A): the whole engine - the pool table, which errors deserve a
 * rotation, duplicate skipping, the next-index walk - moved into
 * libmeerocore.so. Java keeps only the rotation STATE (which slot is active)
 * in local prefs and asks the native engine one question at a time. When the
 * lib somehow cannot load, behaviour degrades to the single build-provided
 * credential with no rotation - same class of fallback as MeeroVault.
 *
 * A user-supplied key from NekoXConfig always wins - this only governs which
 * built-in credential is used when there is no custom one.
 */
public class MeeroApiKeys {

    private static final String PREFS = "meerox_api";
    private static final String KEY_INDEX = "api_index";
    /**
     * Bumped whenever the pool changes. A saved index points at a position in
     * the old list, so rebuilding the table must reset the saved choice once.
     */
    private static final String KEY_GENERATION = "api_generation";
    // v198: slot 0 reverted to Nagram's 11535358 - official ids cannot log
    // in on our signature (owner field evidence: Telegram's "SMS fees" $1
    // paywall instead of a code), so every index parked on an official slot
    // during the v197 attempts resets onto slot 0 once.
    private static final int GENERATION = 4;

    private static int index = -1;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int count() {
        if (MeeroCore.ready()) {
            try {
                final int n = MeeroCore.nKeyCount();
                if (n > 0) return n;
            } catch (Throwable ignored) {
            }
        }
        return 1; // degraded: only the build key
    }

    private static void ensureInstalled() {
        if (MeeroCore.ready()) {
            try {
                /* slot 0 of the native table is the build key, unknown to C */
                MeeroCore.nInstallKey0(BuildConfig.APP_ID, BuildConfig.APP_HASH);
            } catch (Throwable ignored) {
            }
        }
    }

    private static int index() {
        if (index < 0) {
            try {
                final SharedPreferences p = prefs();
                if (p.getInt(KEY_GENERATION, 0) != GENERATION) {
                    index = 0;
                    p.edit().putInt(KEY_INDEX, 0).putInt(KEY_GENERATION, GENERATION).apply();
                } else {
                    index = p.getInt(KEY_INDEX, 0);
                }
            } catch (Throwable e) {
                index = 0;
            }
            if (index < 0 || index >= count()) {
                index = 0;
            }
        }
        return index;
    }

    public static int currentId() {
        ensureInstalled();
        if (MeeroCore.ready()) {
            try {
                return MeeroCore.nKeyId(index());
            } catch (Throwable ignored) {
            }
        }
        return BuildConfig.APP_ID;
    }

    public static String currentHash() {
        ensureInstalled();
        if (MeeroCore.ready()) {
            try {
                final String h = MeeroCore.nKeyHash(index());
                if (h != null) return h;
            } catch (Throwable ignored) {
            }
        }
        return BuildConfig.APP_HASH;
    }

    /**
     * Moves to the next usable credential (decision made natively).
     *
     * @return true when another one was available
     */
    public static boolean advance() {
        ensureInstalled();
        if (!MeeroCore.ready()) {
            return false;
        }
        final int next;
        try {
            next = MeeroCore.nKeyAdvance(index());
        } catch (Throwable t) {
            return false;
        }
        if (next < 0) {
            return false;
        }
        index = next;
        try {
            prefs().edit().putInt(KEY_INDEX, index).putInt(KEY_GENERATION, GENERATION).apply();
        } catch (Throwable ignore) {
        }
        FileLog.d("MeeroX: switching to api id " + currentId());
        return true;
    }

    /** Goes back to the first credential, for a manual reset. */
    public static void reset() {
        index = 0;
        try {
            prefs().edit().putInt(KEY_INDEX, 0).putInt(KEY_GENERATION, GENERATION).apply();
        } catch (Throwable ignore) {
        }
    }

    /**
     * Whether an auth.sendCode failure blames the credential rather than the
     * phone number (marker set lives natively).
     */
    public static boolean isKeyError(String errorText) {
        if (errorText == null) {
            return false;
        }
        if (MeeroCore.ready()) {
            try {
                return MeeroCore.nIsKeyError(errorText);
            } catch (Throwable ignored) {
            }
        }
        return errorText.contains("API_ID_INVALID")
                || errorText.contains("API_ID_PUBLISHED_FLOOD")
                || errorText.contains("API_ID_RESTRICTED")
                || errorText.contains("AUTH_KEY_DUPLICATED")
                || errorText.contains("CONNECTION_API_ID_INVALID");
    }

    /** A short description of the active key, for the settings screen. */
    public static String describe() {
        return "api_id " + currentId() + " (" + (index() + 1) + "/" + count() + ")";
    }
}
