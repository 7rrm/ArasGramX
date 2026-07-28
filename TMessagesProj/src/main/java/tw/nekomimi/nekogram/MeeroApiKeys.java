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
 * Rather than pick one and hope it keeps working, the keys are kept as a list.
 * When auth.sendCode comes back with an error that points at the credential
 * rather than the phone number, the next key in the list is selected and the
 * request can simply be retried. The choice is remembered, so a user who has
 * been moved onto a working key stays there.
 *
 * A user-supplied key from NekoXConfig always wins - this only governs which
 * built-in credential is used when there is no custom one.
 */
public class MeeroApiKeys {

    /**
     * Built-in credentials, tried in order.
     *
     * The first entry is whatever the build was compiled with, so a build-time
     * override through TELEGRAM_APP_ID still takes precedence. The rest are
     * public credentials that established Telegram forks ship.
     */
    private static final int[] IDS = {
            BuildConfig.APP_ID,
            6,
            2040,
            4,
    };

    private static final String[] HASHES = {
            BuildConfig.APP_HASH,
            "eb06d4abfb49dc3eeb1aeb98ae0f581e",
            "b18441a1ff607e10a989891a5462e627",
            "014b35b6184100b085b0d0572f9b5103",
    };

    private static final String PREFS = "meerox_api";
    private static final String KEY_INDEX = "api_index";

    private static int index = -1;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int index() {
        if (index < 0) {
            try {
                index = prefs().getInt(KEY_INDEX, 0);
            } catch (Throwable e) {
                index = 0;
            }
            if (index < 0 || index >= IDS.length) {
                index = 0;
            }
        }
        return index;
    }

    /** Duplicate entries are skipped, since IDS[0] may equal one of the rest. */
    private static boolean isDuplicate(int candidate) {
        for (int i = 0; i < candidate; i++) {
            if (IDS[i] == IDS[candidate]) {
                return true;
            }
        }
        return false;
    }

    public static int currentId() {
        return IDS[index()];
    }

    public static String currentHash() {
        return HASHES[index()];
    }

    /**
     * Moves to the next usable credential.
     *
     * @return true when another one was available
     */
    public static boolean advance() {
        int next = index() + 1;
        while (next < IDS.length && isDuplicate(next)) {
            next++;
        }
        if (next >= IDS.length) {
            return false;
        }
        index = next;
        try {
            prefs().edit().putInt(KEY_INDEX, index).apply();
        } catch (Throwable ignore) {
        }
        FileLog.d("MeeroX: switching to api id " + IDS[index]);
        return true;
    }

    /** Goes back to the first credential, for a manual reset. */
    public static void reset() {
        index = 0;
        try {
            prefs().edit().putInt(KEY_INDEX, 0).apply();
        } catch (Throwable ignore) {
        }
    }

    /**
     * Whether an auth.sendCode failure blames the credential rather than the
     * phone number.
     *
     * Only these are worth retrying on another key. A wrong or banned number
     * fails the same way whichever credential asks, and rotating through the
     * pool for those would just burn every key's rate limit.
     */
    public static boolean isKeyError(String errorText) {
        if (errorText == null) {
            return false;
        }
        return errorText.contains("API_ID_INVALID")
                || errorText.contains("API_ID_PUBLISHED_FLOOD")
                || errorText.contains("API_ID_RESTRICTED")
                || errorText.contains("AUTH_KEY_DUPLICATED")
                || errorText.contains("CONNECTION_API_ID_INVALID");
    }

    /** A short description of the active key, for the settings screen. */
    public static String describe() {
        return "api_id " + currentId() + " (" + (index() + 1) + "/" + IDS.length + ")";
    }
}
