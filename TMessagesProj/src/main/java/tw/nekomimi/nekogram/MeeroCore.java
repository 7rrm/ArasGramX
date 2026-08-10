package tw.nekomimi.nekogram;

import java.security.MessageDigest;

/**
 * MeeroX v183 (batch 2A) - the thin kept shell of libmeerocore.so.
 *
 * Every byte of logic (lock verify/derive, audit seal, api-key engine) is
 * native; this class only carries the native bindings + readiness probe +
 * a degraded-mode Java mirror of the v2 derive so a build without the lib
 * (never on our stock APKs) keeps EXACTLY the same user-visible behaviour.
 *
 * Name-pinned in proguard-rules.pro like MeeroVaultSeed: JNI bindings are
 * name-mangled, so this class must never be renamed.
 */
public final class MeeroCore {

    private MeeroCore() {
    }

    private static volatile boolean ready;

    static {
        boolean ok = false;
        try {
            /* libmeerovault must already be mapped: meerocore links against
             * it for the shared seed. */
            MeeroVaultSeed.fingerprintSeed();
            System.loadLibrary("meerocore");
            ok = true;
        } catch (Throwable ignored) {
        }
        ready = ok;
    }

    public static boolean ready() {
        return ready;
    }

    /**
     * Degraded-mode v2 derive (libmeerocore absent). Byte-identical to the
     * native nLockDerive material: seed(32) | "MCLK2" | NUL | salt | ":" | code.
     */
    public static byte[] javaV2Derive(String saltB64, String code) {
        try {
            final byte[] seed = MeeroVaultSeed.fingerprintSeed();
            if (seed == null || seed.length != 32) {
                return null;
            }
            final byte[] salt = saltB64.getBytes("UTF-8");
            final byte[] cd = code.getBytes("UTF-8");
            final byte[] m = new byte[32 + 6 + salt.length + 1 + cd.length];
            System.arraycopy(seed, 0, m, 0, 32);
            final byte[] tag = "MCLK2".getBytes("UTF-8");
            System.arraycopy(tag, 0, m, 32, 5);
            m[37] = 0;
            System.arraycopy(salt, 0, m, 38, salt.length);
            m[38 + salt.length] = ':';
            System.arraycopy(cd, 0, m, 39 + salt.length, cd.length);
            return MessageDigest.getInstance("SHA-256").digest(m);
        } catch (Throwable t) {
            return null;
        }
    }

    /* ---------------- lock core ---------------- */
    public static native byte[] nLegacyDigest(String saltB64, String code);

    public static native byte[] nLockDerive(String saltB64, String code);

    public static native boolean nConstEq(byte[] a, byte[] b);

    public static native String nAuditMac(String prevJson, String entryJson);

    /* ---------------- api-key engine ---------------- */
    public static native void nInstallKey0(int id, String hash);

    public static native int nKeyCount();

    public static native int nKeyId(int index);

    public static native String nKeyHash(int index);

    public static native int nKeyAdvance(int current);

    public static native boolean nIsKeyError(String errorText);

    /* ---------------- automation group (MeeroX v184, batch 2B) ----------------
     * Persisted stores are opaque seed-sealed blobs written by nArBlob /
     * nKwBlob only; Java never parses them. nKwMatch returns the winning
     * word to alert on, or null to stay silent (no hit / throttled / stale
     * message). nArShouldReply performs exclusion + per-chat cooldown and
     * stamps on pass, exactly like the old gates 2.5 + 6 combined. */
    public static native int nKwLoad(String blob);

    public static native String nKwBlob();

    public static native void nKwUpsert(long dialogId, String words);

    public static native int nKwCount();

    public static native long nKwIdAt(int index);

    public static native String nKwWordsAt(int index);

    public static native String nKwMatch(long dialogId, long msgDateSec, long nowMs, String lowerText);

    public static native int nArLoad(String blob);

    public static native String nArBlob();

    public static native void nArUpsertRule(long dialogId, String text);

    public static native int nArRuleCount();

    public static native long nArRuleIdAt(int index);

    public static native String nArRuleText(long dialogId);

    public static native void nArAddExcl(long dialogId);

    public static native void nArDelExcl(long dialogId);

    public static native int nArExclCount();

    public static native long nArExclIdAt(int index);

    public static native boolean nArIsExcl(long dialogId);

    public static native int nArPoolCount();

    public static native String nArPoolAt(int index);

    public static native void nArPoolAdd(String text);

    public static native void nArPoolSet(int index, String text);

    public static native void nArPoolDel(int index);

    public static native boolean nArShouldReply(long dialogId, long nowMs, int cooldownMin);

    public static native boolean nArWindowPass(int enabled, int daysMask, int startMin, int endMin, long nowMs);

    public static native String nArResolveText(long dialogId, int poolOn, int nightActive, String general,
                                               String night, String defaultText, String firstName, int emojiOn, long nowMs);

    /* ---------------- organization & radar group (MeeroX v185, batch 2C) ---
     * Smart folders: static design table, never persisted. Delete hunter and
     * watch persist as opaque seed-sealed blobs authored by nDhBlob / nWBlob
     * only; Java never parses them (import runs once, plaintext keys die).
     * Activity stats stay SQL-fed; native owns the decision layer. */
    public static native int nSfCount();

    public static native String nSfTitleKeyAt(int index);

    public static native String nSfRuleKeyAt(int index);

    public static native String nSfEmoticonAt(int index);

    public static native int nSfFlagsAt(int index);

    public static native int nSfColorAt(int index);

    public static native boolean nSfTitleEq(String a, String b);

    public static native int nDhLoad(String blob);

    public static native String nDhBlob();

    /* head=1 runtime newest-first put; head=0 legacy import (order kept). */
    public static native void nDhAdd(long tSec, long id, String kind, String who,
                                     String oldValue, String newValue, int head);

    public static native int nDhCount();

    /* escaped TSV: t \t id \t kind \t who \t old \t new */
    public static native String nDhAt(int index);

    public static native void nDhClear();

    public static native String nDhKey(long tSec, long id, String kind, String oldValue);

    public static native int nDhRemove(String keysTsv);

    public static native boolean nDhCapture(int out, int hasAction, long fromUid, long selfId);

    public static native int nWLoad(String blob);

    public static native String nWBlob();

    public static native boolean nWAdd(long id);

    public static native void nWRemove(long id);

    public static native void nWSetOn(long id, int on);

    public static native boolean nWIsWatched(long id);

    public static native boolean nWIsOn(long id);

    public static native int nWCount();

    public static native long nWEntryIdAt(int index);

    public static native boolean nWEntryOnAt(int index);

    public static native void nWSnapImport(long id, int mask, String name, String user,
                                           long photoId, String bio, String bday);

    /* pack: flags \t oldName \t oldUser \t oldPhotoId (record pre-merged) */
    public static native String nWDiffUser(long id, String name, String user, long photoId);

    /* pack: flags \t oldBio \t oldBday \t whoName (record pre-merged) */
    public static native String nWDiffFull(long id, String bio, String bday);

    public static native void nWLogAdd(long tSec, long id, String what, String who,
                                       String oldValue, String newValue, String oldPath,
                                       String newPath, int head);

    public static native int nWLogCount();

    /* escaped TSV: t \t id \t what \t who \t old \t new \t oldPath \t newPath */
    public static native String nWLogAt(int index);

    public static native void nWLogClear();

    public static native boolean nWMsgNotifyPass(long id, long nowMs, int enabled);

    public static native void nAsReset();

    /* "midnightLocal\tweek\tmonth" epoch seconds */
    public static native String nAsBounds(long nowMs);

    public static native void nAsSetHour(int hour, int count);

    public static native int nAsHourAt(int hour);

    public static native boolean nAsHasHourly();

    public static native void nAsDryFeed(long uid, long lastSec, int out);

    public static native int nAsDryCount();

    public static native long nAsDryTopIdAt(int index);

    public static native long nAsDryTopSecAt(int index);

    /* batch 2D: whole UI string table, sealed against the shared vault seed (dom 'S') */
    public static native String nStrTsv();

    /* batch 3A: the glass design brain (sealed table dom 'G') */
    public static native boolean nGlassReady();

    public static native int nGtColor(int id, boolean night);

    public static native float[] nGlassSwitchParams();

    public static native float[] nGlassUiConsts();

    public static native float[] nGlassSwitchGeom(float density, float w, float h,
                                                  float progress, float press, boolean rtl);

    public static native int nGlassBorder(int baseColor);

    public static native int nGlassCardPos(boolean first, boolean last);

    /** Library up AND the sealed glass table decoded; false -> legacy fallback. */
    public static boolean glassCore() {
        return ready() && nGlassReady();
    }

    /* batch 3B: chat-surface family brain (sealed table dom 'B') */
    public static native boolean nChatReady();

    public static native int nBBRadius(int style, int fallback);

    public static native boolean nBBTailless(int style);

    public static native float[] nBBTailParams(int style);

    public static native float[] nBBPreviewConsts();

    public static native float[] nShadowTier(int tier, boolean dark);

    public static native int nShadowInset(int tier, float density);

    public static native float nCardLiftCore(float sat, float val);

    public static native float[] nCardConsts();

    public static native float[] nCardRadii(int position, float radius);

    public static native int nCardHairline(int fill, boolean dark);

    /** Library up AND the sealed chat-surface table decoded. */
    public static boolean chatCore() {
        return ready() && nChatReady();
    }

    /* batch 3C: motion/engine family brain (sealed table dom 'C') */
    public static native boolean nMotionReady();

    public static native int[] nMixerAccents();

    public static native int[] nMixerBackgrounds();

    public static native int[] nMixerInBubble();

    public static native int[] nMixerColors();

    public static native float[] nMixerRecipe();

    public static native String[] nFonts();

    public static native float[] nJanitorPolicy();

    public static native int[] nHapticsMap();

    public static native float[] nStatusRecipe();

    public static native float[] nRingRecipe();

    public static native float[] nSmoothPolicy();

    public static native float[] nIntroRecipe();

    /** Library up AND the sealed motion table decoded; false -> legacy fallback. */
    public static boolean motionCore() {
        return ready() && nMotionReady();
    }
}
