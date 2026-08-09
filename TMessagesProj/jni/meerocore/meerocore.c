/*
 * MeeroX v183 (batch 2A) - "libmeerocore": native hearts for the security
 * feature group. Java keeps only thin obfuscated facades that pass plain
 * inputs; every decision/check below lives strictly inside this .so.
 *
 * Group 2A contents:
 *   - chat-lock core: legacy-compatible digest (SHA-256 of "salt:code",
 *     byte-identical to the Java scheme so EXISTING user codes keep
 *     working), a v2 seed-bound derive (off-device cracking dies because
 *     the meerovault seed never leaves this process), constant-time compare
 *   - audit chain seal: HMAC-SHA256(seed, prev_entry | entry) so the local
 *     unlock-attempt log becomes tamper-evident (verify wired next batch)
 *   - api-key engine: the rotation policy (which errors deserve a key
 *     switch, duplicate skipping, next-index walk) + the key table itself,
 *     served to Java one call at a time; slot 0 (build key) is installed
 *     once by Java because native cannot know BuildConfig values
 *
 * The seed is borrowed from libmeerovault (linked, loaded first by the
 * Java facade). Per his standing rule NOTHING here ever kills or blocks
 * the user; failures return safe values and Java falls back to the proven
 * v182 behaviour.
 */

/* v184 follow-up: localtime_r is POSIX, not C11 - bionic declares it
 * unconditionally (and the NDK default is gnu11), so device builds were
 * always correct, but a strict -std=c11 host compile must see the
 * prototype too. Declare the intent explicitly; harmless on bionic. */
#define _POSIX_C_SOURCE 200809L

#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

/* ---------------- link-time borrow: the shared meerovault seed --------- */
extern jbyteArray JNICALL
Java_tw_nekomimi_nekogram_MeeroVaultSeed_fingerprintSeedNative(JNIEnv *env, jclass clazz);

static int mc_seed(JNIEnv *env, unsigned char out[32]) {
    jbyteArray a = Java_tw_nekomimi_nekogram_MeeroVaultSeed_fingerprintSeedNative(env, NULL);
    if (a == NULL) return 0;
    if ((*env)->GetArrayLength(env, a) != 32) {
        (*env)->DeleteLocalRef(env, a);
        return 0;
    }
    (*env)->GetByteArrayRegion(env, a, 0, 32, (jbyte *) out);
    (*env)->DeleteLocalRef(env, a);
    return 1;
}

/* ---------------- compact SHA-256 (public-domain style) ---------------- */

typedef struct {
    uint32_t h[8];
    uint64_t len;
    unsigned char buf[64];
    size_t buf_len;
} mc_sha256_ctx;

static const uint32_t MC_K[64] = {
    0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u, 0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
    0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u, 0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
    0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu, 0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
    0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u, 0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
    0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u, 0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
    0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u, 0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
    0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u, 0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
    0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u, 0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u
};

#define MC_RR(x, n) (((x) >> (n)) | ((x) << (32 - (n))))

static void mc_sha256_block(mc_sha256_ctx *c, const unsigned char p[64]) {
    uint32_t w[64];
    for (int i = 0; i < 16; i++) {
        w[i] = ((uint32_t) p[i * 4] << 24) | ((uint32_t) p[i * 4 + 1] << 16)
             | ((uint32_t) p[i * 4 + 2] << 8) | (uint32_t) p[i * 4 + 3];
    }
    for (int i = 16; i < 64; i++) {
        uint32_t s0 = MC_RR(w[i - 15], 7) ^ MC_RR(w[i - 15], 18) ^ (w[i - 15] >> 3);
        uint32_t s1 = MC_RR(w[i - 2], 17) ^ MC_RR(w[i - 2], 19) ^ (w[i - 2] >> 10);
        w[i] = w[i - 16] + s0 + w[i - 7] + s1;
    }
    uint32_t a = c->h[0], b = c->h[1], cc = c->h[2], d = c->h[3],
             e = c->h[4], f = c->h[5], g = c->h[6], h = c->h[7];
    for (int i = 0; i < 64; i++) {
        uint32_t S1 = MC_RR(e, 6) ^ MC_RR(e, 11) ^ MC_RR(e, 25);
        uint32_t ch = (e & f) ^ ((~e) & g);
        uint32_t t1 = h + S1 + ch + MC_K[i] + w[i];
        uint32_t S0 = MC_RR(a, 2) ^ MC_RR(a, 13) ^ MC_RR(a, 22);
        uint32_t mj = (a & b) ^ (a & cc) ^ (b & cc);
        uint32_t t2 = S0 + mj;
        h = g; g = f; f = e; e = d + t1; d = cc; cc = b; b = a; a = t1 + t2;
    }
    c->h[0] += a; c->h[1] += b; c->h[2] += cc; c->h[3] += d;
    c->h[4] += e; c->h[5] += f; c->h[6] += g; c->h[7] += h;
}

static void mc_sha256(const unsigned char *data, size_t len, unsigned char out[32]) {
    mc_sha256_ctx c;
    c.h[0] = 0x6a09e667u; c.h[1] = 0xbb67ae85u; c.h[2] = 0x3c6ef372u; c.h[3] = 0xa54ff53au;
    c.h[4] = 0x510e527fu; c.h[5] = 0x9b05688cu; c.h[6] = 0x1f83d9abu; c.h[7] = 0x5be0cd19u;
    c.len = 0; c.buf_len = 0;
    while (len >= 64 - c.buf_len) {
        size_t take = 64 - c.buf_len;
        if (c.buf_len == 0 && len >= 64) {
            mc_sha256_block(&c, data);
            data += 64; len -= 64; c.len += 64;
            continue;
        }
        memcpy(c.buf + c.buf_len, data, take);
        mc_sha256_block(&c, c.buf);
        data += take; len -= take; c.len += 64;
        c.buf_len = 0;
    }
    if (len > 0) {
        memcpy(c.buf + c.buf_len, data, len);
        c.buf_len += len; c.len += len;
    }
    uint64_t bits = c.len * 8;
    c.buf[c.buf_len++] = 0x80;
    if (c.buf_len > 56) {
        while (c.buf_len < 64) c.buf[c.buf_len++] = 0;
        mc_sha256_block(&c, c.buf);
        c.buf_len = 0;
    }
    while (c.buf_len < 56) c.buf[c.buf_len++] = 0;
    for (int i = 7; i >= 0; i--) c.buf[c.buf_len++] = (unsigned char) (bits >> (i * 8));
    mc_sha256_block(&c, c.buf);
    for (int i = 0; i < 8; i++) {
        out[i * 4] = (unsigned char) (c.h[i] >> 24);
        out[i * 4 + 1] = (unsigned char) (c.h[i] >> 16);
        out[i * 4 + 2] = (unsigned char) (c.h[i] >> 8);
        out[i * 4 + 3] = (unsigned char) c.h[i];
    }
    memset(&c, 0, sizeof(c));
}

/* HMAC-SHA256 with a 32-byte key (the meerovault seed). */
static void mc_hmac32(const unsigned char key[32], const unsigned char *data, size_t len,
                      unsigned char out[32]) {
    unsigned char k[64];
    memset(k, 0, sizeof(k));
    memcpy(k, key, 32);
    unsigned char inner[64 + 2048];
    /* entry strings here are tiny JSON snippets; size guard below keeps the
     * stack frame bounded - long inputs use a two-buffer fallback */
    if (len > 2048) {
        unsigned char *dyn = malloc(64 + len + 32);
        if (dyn == NULL) { memset(out, 0, 32); return; }
        for (int i = 0; i < 64; i++) dyn[i] = k[i] ^ 0x36;
        memcpy(dyn + 64, data, len);
        unsigned char ih[32];
        mc_sha256(dyn, 64 + len, ih);
        for (int i = 0; i < 64; i++) dyn[i] = k[i] ^ 0x5c;
        memcpy(dyn + 64, ih, 32);
        mc_sha256(dyn, 96, out);
        memset(dyn, 0, 64 + len + 32);
        free(dyn);
        memset(ih, 0, 32);
        return;
    }
    for (int i = 0; i < 64; i++) inner[i] = k[i] ^ 0x36;
    memcpy(inner + 64, data, len);
    unsigned char ih[32];
    mc_sha256(inner, 64 + len, ih);
    unsigned char outer[64 + 32];
    for (int i = 0; i < 64; i++) outer[i] = k[i] ^ 0x5c;
    memcpy(outer + 64, ih, 32);
    mc_sha256(outer, 96, out);
    memset(inner, 0, sizeof(inner));
    memset(outer, 0, sizeof(outer));
    memset(ih, 0, 32);
    memset(k, 0, sizeof(k));
}

/* ---------------- small helpers ---------------- */

static jbyteArray mc_bytes(JNIEnv *env, const unsigned char *b, int n) {
    jbyteArray a = (*env)->NewByteArray(env, n);
    if (a != NULL) (*env)->SetByteArrayRegion(env, a, 0, n, (const jbyte *) b);
    return a;
}

static char *mc_utf(JNIEnv *env, jstring s) {
    if (s == NULL) return NULL;
    const char *p = (*env)->GetStringUTFChars(env, s, NULL);
    if (p == NULL) return NULL;
    const size_t n = strlen(p) + 1;
    char *cp = malloc(n);
    if (cp != NULL) memcpy(cp, p, n);
    (*env)->ReleaseStringUTFChars(env, s, p);
    return cp;
}

#define MC_CLASS(verb) Java_tw_nekomimi_nekogram_MeeroCore_##verb

/* ---------------- lock core ---------------- */

/* legacy scheme, byte-identical to the old Java digest:
 * SHA256( (saltB64 + ":" + code).utf8 ). Existing hashes verify, so no
 * user ever has to reset a code after the update. */
JNIEXPORT jbyteArray JNICALL MC_CLASS(nLegacyDigest)(JNIEnv *env, jclass c, jstring saltB64, jstring code) {
    (void) c;
    char *salt = mc_utf(env, saltB64);
    char *cd = mc_utf(env, code);
    if (salt == NULL || cd == NULL) { free(salt); free(cd); return NULL; }
    size_t mlen = strlen(salt) + 1 + strlen(cd);
    char *m = malloc(mlen);
    if (m == NULL) { free(salt); free(cd); return NULL; }
    memcpy(m, salt, strlen(salt));
    m[strlen(salt)] = ':';
    memcpy(m + strlen(salt) + 1, cd, strlen(cd) + 1);
    unsigned char d[32];
    mc_sha256((const unsigned char *) m, mlen, d);
    memset(m, 0, mlen); free(m); free(salt); free(cd);
    return mc_bytes(env, d, 32);
}

/* v2 scheme: SHA256(seed | "MCLK2" | NUL | saltB64 | ":" | code).
 * The seed never leaves native memory, so a stolen hash database cannot be
 * cracked offline on another machine. */
JNIEXPORT jbyteArray JNICALL MC_CLASS(nLockDerive)(JNIEnv *env, jclass c, jstring saltB64, jstring code) {
    (void) c;
    unsigned char seed[32];
    if (!mc_seed(env, seed)) return NULL;
    char *salt = mc_utf(env, saltB64);
    char *cd = mc_utf(env, code);
    if (salt == NULL || cd == NULL) { free(salt); free(cd); memset(seed, 0, 32); return NULL; }
    size_t sl = strlen(salt), cl = strlen(cd);
    size_t mlen = 32 + 6 + sl + 1 + cl;
    unsigned char *m = malloc(mlen);
    if (m == NULL) { free(salt); free(cd); memset(seed, 0, 32); return NULL; }
    memcpy(m, seed, 32);
    memcpy(m + 32, "MCLK2", 5);
    m[37] = 0;
    memcpy(m + 38, salt, sl);
    m[38 + sl] = ':';
    memcpy(m + 39 + sl, cd, cl);
    unsigned char d[32];
    mc_sha256(m, mlen, d);
    memset(m, 0, mlen); free(m); free(salt); free(cd); memset(seed, 0, 32);
    return mc_bytes(env, d, 32);
}

/* constant-time compare: identical length only; mismatch cost must not
 * depend on where the first differing byte sits. */
JNIEXPORT jboolean JNICALL MC_CLASS(nConstEq)(JNIEnv *env, jclass c, jbyteArray a, jbyteArray b) {
    (void) c;
    if (a == NULL || b == NULL) return JNI_FALSE;
    jsize la = (*env)->GetArrayLength(env, a);
    jsize lb = (*env)->GetArrayLength(env, b);
    if (la != lb || la == 0) return JNI_FALSE;
    jbyte *pa = (*env)->GetByteArrayElements(env, a, NULL);
    jbyte *pb = (*env)->GetByteArrayElements(env, b, NULL);
    if (pa == NULL || pb == NULL) {
        if (pa != NULL) (*env)->ReleaseByteArrayElements(env, a, pa, JNI_ABORT);
        if (pb != NULL) (*env)->ReleaseByteArrayElements(env, b, pb, JNI_ABORT);
        return JNI_FALSE;
    }
    unsigned char diff = 0;
    for (jsize i = 0; i < la; i++) diff |= (unsigned char) (pa[i] ^ pb[i]);
    (*env)->ReleaseByteArrayElements(env, a, pa, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, b, pb, JNI_ABORT);
    return diff == 0 ? JNI_TRUE : JNI_FALSE;
}

/* audit chain seal: hex HMAC-SHA256(seed, prevJson | "\n" | entryJson).
 * Java stores it next to each audit entry; a broken link exposes edits to
 * the local log (the verify screen plugs in next batch). */
JNIEXPORT jstring JNICALL MC_CLASS(nAuditMac)(JNIEnv *env, jclass c, jstring prevJson, jstring entryJson) {
    (void) c;
    unsigned char seed[32];
    if (!mc_seed(env, seed)) return NULL;
    char *pv = mc_utf(env, prevJson);
    char *en = mc_utf(env, entryJson);
    if (pv == NULL || en == NULL) { free(pv); free(en); memset(seed, 0, 32); return NULL; }
    size_t pl = strlen(pv), el = strlen(en);
    unsigned char *m = malloc(pl + 1 + el);
    if (m == NULL) { free(pv); free(en); memset(seed, 0, 32); return NULL; }
    memcpy(m, pv, pl);
    m[pl] = '\n';
    memcpy(m + pl + 1, en, el);
    unsigned char mac[32];
    mc_hmac32(seed, m, pl + 1 + el, mac);
    memset(m, 0, pl + 1 + el); free(m); free(pv); free(en); memset(seed, 0, 32);
    char hex[65];
    static const char HEXC[] = "0123456789abcdef";
    for (int i = 0; i < 32; i++) { hex[i * 2] = HEXC[mac[i] >> 4]; hex[i * 2 + 1] = HEXC[mac[i] & 15]; }
    hex[64] = 0;
    memset(mac, 0, 32);
    return (*env)->NewStringUTF(env, hex);
}

/* ---------------- api-key engine ---------------- */

/* Tables hold the pool. Strings are XOR-garbled (0xA7) so a plain strings
 * dump shows neither the ids list wording nor the hashes in one sweep; the
 * engine logic below is the part that actually hides. Slot 0 comes from
 * Java (build key), hence NULL until installed. */
static const unsigned char XK = 0xA7;
static int mc_key_id0;
static char mc_key_hash0[64];
static int mc_key0_installed;

/* xor-obfuscated hash literals (each 32 chars + NUL) */
static const unsigned char H1[] = {0x94,0x94,0xc3,0x94,0x90,0x95,0x9e,0x91,0x95,0xc1,0xc6,0xc3,0xc5,0x97,0x96,0xc3,0xc1,0x93,0x90,0xc2,0x91,0xc4,0xc2,0xc2,0xc3,0x93,0xc2,0x94,0x94,0xc4,0xc3,0x91,0x00};
static const unsigned char H2[] = {0xc5,0x96,0x9f,0x93,0x93,0x96,0xc6,0x96,0xc1,0xc1,0x91,0x97,0x90,0xc2,0x96,0x97,0xc6,0x9e,0x9f,0x9e,0x9f,0x9e,0x96,0xc6,0x92,0x93,0x91,0x95,0xc2,0x91,0x95,0x90,0x00};
static const unsigned char H3[] = {0xc2,0xc5,0x97,0x91,0xc3,0x93,0xc6,0xc5,0xc1,0xc5,0x93,0x9e,0xc3,0xc4,0x94,0xc2,0xc2,0xc5,0x96,0xc6,0xc2,0xc5,0x9e,0x9f,0xc6,0xc2,0x97,0xc1,0x92,0x9f,0x96,0xc2,0x00};
static const unsigned char H4[] = {0x97,0x96,0x93,0xc5,0x94,0x92,0xc5,0x91,0x96,0x9f,0x93,0x96,0x97,0x97,0xc5,0x97,0x9f,0x92,0xc5,0x97,0xc3,0x97,0x92,0x90,0x95,0xc1,0x9e,0xc5,0x92,0x96,0x97,0x94,0x00};

static const int MC_IDS[4] = {11535358, 2040, 6, 4};
#define MC_KEY_N 5

static void mc_unxor(char *out, const unsigned char *enc) {
    int i = 0;
    while (enc[i]) { out[i] = (char) (enc[i] ^ XK); i++; }
    out[i] = 0;
}

JNIEXPORT void JNICALL MC_CLASS(nInstallKey0)(JNIEnv *env, jclass c, jint id, jstring hash) {
    (void) c;
    if (mc_key0_installed) return;
    char *h = mc_utf(env, hash);
    if (h != NULL) {
        strncpy(mc_key_hash0, h, sizeof(mc_key_hash0) - 1);
        mc_key_hash0[sizeof(mc_key_hash0) - 1] = 0;
        mc_key_id0 = id;
        mc_key0_installed = 1;
        memset(h, 0, strlen(h));
        free(h);
    }
}

JNIEXPORT jint JNICALL MC_CLASS(nKeyCount)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    return MC_KEY_N;
}

static jint mc_id_at(int idx) {
    if (idx == 0) return mc_key0_installed ? mc_key_id0 : 0;
    return MC_IDS[idx - 1];
}

JNIEXPORT jint JNICALL MC_CLASS(nKeyId)(JNIEnv *env, jclass c, jint idx) {
    (void) env; (void) c;
    if (idx < 0 || idx >= MC_KEY_N) return 0;
    return mc_id_at(idx);
}

JNIEXPORT jstring JNICALL MC_CLASS(nKeyHash)(JNIEnv *env, jclass c, jint idx) {
    (void) c;
    if (idx < 0 || idx >= MC_KEY_N) return NULL;
    char tmp[64];
    if (idx == 0) {
        if (!mc_key0_installed) return NULL;
        strcpy(tmp, mc_key_hash0);
    } else {
        const unsigned char *enc = H1;
        if (idx == 2) enc = H2; else if (idx == 3) enc = H3; else if (idx == 4) enc = H4;
        mc_unxor(tmp, enc);
    }
    jstring r = (*env)->NewStringUTF(env, tmp);
    memset(tmp, 0, sizeof(tmp));
    return r;
}

/* rotation: walk forward skipping ids that repeat an earlier slot. */
JNIEXPORT jint JNICALL MC_CLASS(nKeyAdvance)(JNIEnv *env, jclass c, jint cur) {
    (void) env; (void) c;
    int next = cur + 1;
    while (next < MC_KEY_N) {
        jint cand = mc_id_at(next);
        int dup = 0;
        for (int i = 0; i < next; i++) {
            if (mc_id_at(i) == cand) { dup = 1; break; }
        }
        if (!dup) return next;
        next++;
    }
    return -1;
}

/* which transport errors blame the credential (and so deserve a rotate) */
JNIEXPORT jboolean JNICALL MC_CLASS(nIsKeyError)(JNIEnv *env, jclass c, jstring err) {
    (void) c;
    char *e = mc_utf(env, err);
    if (e == NULL) return JNI_FALSE;
    static const char *MARKS[] = {
        "API_ID_INVALID", "API_ID_PUBLISHED_FLOOD", "API_ID_RESTRICTED",
        "AUTH_KEY_DUPLICATED", "CONNECTION_API_ID_INVALID"
    };
    jboolean hit = JNI_FALSE;
    for (int i = 0; i < 5; i++) {
        if (strstr(e, MARKS[i]) != NULL) { hit = JNI_TRUE; break; }
    }
    memset(e, 0, strlen(e));
    free(e);
    return hit;
}

/*
 * ============================================================================
 * MeeroX v184 (batch 2B) - automation group hearts: the auto-reply engine
 * and the keyword-alert engine. From here down, Java keeps only the thin
 * obfuscated shell: the Telegram handshake (send / notify) and the screens.
 * Every decision - rules, exclusions, pool selection, window & weekday
 * gating, cooldown, throttle, text resolution, keyword splitting/matching -
 * lives inside this .so. The stores themselves persist as opaque
 * seed-sealed blobs (XOR stream keyed by SHA-256(seed|"MBLOB"|dom|block),
 * sealed with HMAC-SHA256), so a prefs dump reveals nothing and edits fail
 * the MAC. Failures return safe empty values; without the lib Java runs
 * the proven legacy JSON path byte-for-byte (v182 behaviour).
 * ============================================================================
 */

static pthread_mutex_t mc_mu = PTHREAD_MUTEX_INITIALIZER;

/* ---- small string helpers (POSIX strdup is unavailable under -std=c11) --- */

static char *mc_dup(const char *s) {
    if (s == NULL) return NULL;
    size_t n = strlen(s) + 1;
    char *r = malloc(n);
    if (r != NULL) memcpy(r, s, n);
    return r;
}

static char *mc_ndup(const char *s, size_t n) {
    char *r = malloc(n + 1);
    if (r == NULL) return NULL;
    memcpy(r, s, n);
    r[n] = 0;
    return r;
}

/* ---- dynamic vectors ----------------------------------------------------- */

typedef struct { jlong id; char *s; } mc_idstr;
typedef struct { mc_idstr *e; int len; int cap; } mc_idstr_v;

static int mc_idstr_find(mc_idstr_v *v, jlong id) {
    for (int i = 0; i < v->len; i++) {
        if (v->e[i].id == id) return i;
    }
    return -1;
}

static int mc_grow(void **arr, int *cap, int need, size_t sz) {
    if (need <= *cap) return 1;
    int nc = *cap == 0 ? 8 : *cap * 2;
    while (nc < need) nc *= 2;
    void *p = realloc(*arr, nc * sz);
    if (p == NULL) return 0;
    *arr = p;
    *cap = nc;
    return 1;
}

/* s == NULL or "" means remove; replacement order of other entries kept. */
static void mc_idstr_put(mc_idstr_v *v, jlong id, const char *s) {
    int i = mc_idstr_find(v, id);
    if (s == NULL || *s == 0) {
        if (i >= 0) {
            free(v->e[i].s);
            for (int j = i; j + 1 < v->len; j++) v->e[j] = v->e[j + 1];
            v->len--;
        }
        return;
    }
    char *cp = mc_dup(s);
    if (cp == NULL) return;
    if (i >= 0) {
        free(v->e[i].s);
        v->e[i].s = cp;
        return;
    }
    if (!mc_grow((void **) &v->e, &v->cap, v->len + 1, sizeof(mc_idstr))) {
        free(cp);
        return;
    }
    v->e[v->len].id = id;
    v->e[v->len].s = cp;
    v->len++;
}

static const char *mc_idstr_get(mc_idstr_v *v, jlong id) {
    int i = mc_idstr_find(v, id);
    return i >= 0 ? v->e[i].s : NULL;
}

static void mc_idstr_reset(mc_idstr_v *v) {
    for (int i = 0; i < v->len; i++) free(v->e[i].s);
    free(v->e);
    v->e = NULL;
    v->len = 0;
    v->cap = 0;
}

typedef struct { jlong *e; int len; int cap; } mc_id_v;

static int mc_id_has(mc_id_v *v, jlong id) {
    for (int i = 0; i < v->len; i++) {
        if (v->e[i] == id) return 1;
    }
    return 0;
}

static void mc_id_add(mc_id_v *v, jlong id) {
    if (mc_id_has(v, id)) return;
    if (!mc_grow((void **) &v->e, &v->cap, v->len + 1, sizeof(jlong))) return;
    v->e[v->len++] = id;
}

static void mc_id_del(mc_id_v *v, jlong id) {
    for (int i = 0; i < v->len; i++) {
        if (v->e[i] == id) {
            for (int j = i; j + 1 < v->len; j++) v->e[j] = v->e[j + 1];
            v->len--;
            return;
        }
    }
}

static void mc_id_reset(mc_id_v *v) {
    free(v->e);
    v->e = NULL;
    v->len = 0;
    v->cap = 0;
}

typedef struct { char **e; int len; int cap; } mc_str_v;

static void mc_str_push(mc_str_v *v, const char *s) {
    if (s == NULL || *s == 0) return;
    char *cp = mc_dup(s);
    if (cp == NULL) return;
    if (!mc_grow((void **) &v->e, &v->cap, v->len + 1, sizeof(char *))) {
        free(cp);
        return;
    }
    v->e[v->len++] = cp;
}

static void mc_str_set(mc_str_v *v, int idx, const char *s) {
    if (idx < 0 || idx >= v->len || s == NULL || *s == 0) return;
    char *cp = mc_dup(s);
    if (cp == NULL) return;
    free(v->e[idx]);
    v->e[idx] = cp;
}

static void mc_str_del(mc_str_v *v, int idx) {
    if (idx < 0 || idx >= v->len) return;
    free(v->e[idx]);
    for (int j = idx; j + 1 < v->len; j++) v->e[j] = v->e[j + 1];
    v->len--;
}

static void mc_str_reset(mc_str_v *v) {
    for (int i = 0; i < v->len; i++) free(v->e[i]);
    free(v->e);
    v->e = NULL;
    v->len = 0;
    v->cap = 0;
}

/* per-chat {id, ms} maps for cooldown / throttle; oldest slot evicted. */
typedef struct { jlong id; jlong ms; } mc_mark;

static jlong mc_mark_get(mc_mark *t, int n, jlong id) {
    for (int i = 0; i < n; i++) {
        if (t[i].id == id && t[i].ms != 0) return t[i].ms;
    }
    return 0;
}

static void mc_mark_put(mc_mark *t, int n, jlong id, jlong ms) {
    for (int i = 0; i < n; i++) {
        if (t[i].id == id || t[i].ms == 0) {
            t[i].id = id;
            t[i].ms = ms;
            return;
        }
    }
    int old = 0;
    for (int i = 1; i < n; i++) {
        if (t[i].ms < t[old].ms) old = i;
    }
    t[old].id = id;
    t[old].ms = ms;
}

/* ---- string builder ------------------------------------------------------ */

typedef struct { char *b; size_t len; size_t cap; } mc_sb;

static void mc_sb_raw(mc_sb *s, const char *txt, size_t n) {
    if (s->len + n + 1 > s->cap) {
        size_t nc = s->cap == 0 ? 256 : s->cap;
        while (nc < s->len + n + 1) nc *= 2;
        char *p = realloc(s->b, nc);
        if (p == NULL) return;
        s->b = p;
        s->cap = nc;
    }
    memcpy(s->b + s->len, txt, n);
    s->len += n;
    s->b[s->len] = 0;
}

static void mc_sb_s(mc_sb *s, const char *txt) {
    mc_sb_raw(s, txt, strlen(txt));
}

static void mc_sb_c(mc_sb *s, char c) {
    mc_sb_raw(s, &c, 1);
}

/* ---- base64 (standard, no whitespace) ------------------------------------ */

static const char MC_B64[] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static char *mc_b64enc(const unsigned char *d, size_t n) {
    size_t o = (n + 2) / 3 * 4;
    char *r = malloc(o + 1);
    if (r == NULL) return NULL;
    size_t w = 0;
    for (size_t i = 0; i < n; i += 3) {
        uint32_t v = (uint32_t) d[i] << 16;
        if (i + 1 < n) v |= (uint32_t) d[i + 1] << 8;
        if (i + 2 < n) v |= d[i + 2];
        r[w++] = MC_B64[(v >> 18) & 63];
        r[w++] = MC_B64[(v >> 12) & 63];
        r[w++] = i + 1 < n ? MC_B64[(v >> 6) & 63] : '=';
        r[w++] = i + 2 < n ? MC_B64[v & 63] : '=';
    }
    r[w] = 0;
    return r;
}

static int mc_b64v(char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+') return 62;
    if (c == '/') return 63;
    return -1;
}

static unsigned char *mc_b64dec(const char *s, size_t *outn) {
    size_t n = strlen(s);
    if (n == 0 || n % 4 != 0) return NULL;
    size_t pad = (n >= 2 && s[n - 1] == '=') ? (s[n - 2] == '=' ? 2 : 1) : 0;
    size_t o = n / 4 * 3 - pad;
    unsigned char *r = malloc(o + 1);
    if (r == NULL) return NULL;
    size_t w = 0;
    for (size_t i = 0; i < n; i += 4) {
        int v0 = mc_b64v(s[i]), v1 = mc_b64v(s[i + 1]);
        int v2 = i + 2 < n - pad ? mc_b64v(s[i + 2]) : 0;
        int v3 = i + 3 < n - pad ? mc_b64v(s[i + 3]) : 0;
        if (v0 < 0 || v1 < 0 || v2 < 0 || v3 < 0) {
            free(r);
            return NULL;
        }
        uint32_t v = ((uint32_t) v0 << 18) | ((uint32_t) v1 << 12)
                   | ((uint32_t) v2 << 6) | (uint32_t) v3;
        if (w < o) r[w++] = (unsigned char) (v >> 16);
        if (w < o) r[w++] = (unsigned char) (v >> 8);
        if (w < o) r[w++] = (unsigned char) v;
    }
    *outn = o;
    return r;
}

/* ---- field escaping: '%'->%25 '\t'->%09 '\n'->%0A '\r'->%0D --------------- */

static char *mc_esc(const char *s) {
    size_t n = 0;
    for (const char *p = s; *p; p++) {
        n += (*p == '%' || *p == '\t' || *p == '\n' || *p == '\r') ? 3 : 1;
    }
    char *r = malloc(n + 1);
    if (r == NULL) return NULL;
    char *w = r;
    for (const char *p = s; *p; p++) {
        if (*p == '%') { memcpy(w, "%25", 3); w += 3; }
        else if (*p == '\t') { memcpy(w, "%09", 3); w += 3; }
        else if (*p == '\n') { memcpy(w, "%0A", 3); w += 3; }
        else if (*p == '\r') { memcpy(w, "%0D", 3); w += 3; }
        else *w++ = *p;
    }
    *w = 0;
    return r;
}

/* in-place unescape (output can never be longer than input) */
static void mc_unesc(char *s) {
    char *r = s, *w = s;
    while (*r) {
        if (*r == '%' && r[1] == '2' && r[2] == '5') { *w++ = '%'; r += 3; }
        else if (*r == '%' && r[1] == '0' && r[2] == '9') { *w++ = '\t'; r += 3; }
        else if (*r == '%' && r[1] == '0' && (r[2] == 'A' || r[2] == 'a')) { *w++ = '\n'; r += 3; }
        else if (*r == '%' && r[1] == '0' && (r[2] == 'D' || r[2] == 'd')) { *w++ = '\r'; r += 3; }
        else *w++ = *r++;
    }
    *w = 0;
}

/* Java String.trim() parity: strip chars <= 0x20 from both ends (only the
 * ASCII range can match, which is exactly what trim does too). */
static void mc_trim(char *s) {
    size_t n = strlen(s);
    while (n > 0 && (unsigned char) s[n - 1] <= 0x20) s[--n] = 0;
    size_t lead = 0;
    while (s[lead] && (unsigned char) s[lead] <= 0x20) lead++;
    if (lead) memmove(s, s + lead, strlen(s + lead) + 1);
}

/* Java String.length() parity (UTF-16 units): 4-byte UTF-8 chars count 2,
 * everything else counts 1 - so a lone emoji is length 2 like in Java. */
static int mc_len16(const char *s) {
    int u = 0;
    for (const unsigned char *p = (const unsigned char *) s; *p; p++) {
        if ((*p & 0xC0) != 0x80) u += (*p >= 0xF0) ? 2 : 1;
    }
    return u;
}

/* ---- seed-bound seal: XOR stream + HMAC -----------------------------------*/

static void mc_ks(const unsigned char seed[32], char dom, uint32_t blk,
                  unsigned char out[32]) {
    unsigned char m[42];
    memcpy(m, seed, 32);
    memcpy(m + 32, "MBLOB", 5);
    m[37] = (unsigned char) dom;
    m[38] = (unsigned char) (blk >> 24);
    m[39] = (unsigned char) (blk >> 16);
    m[40] = (unsigned char) (blk >> 8);
    m[41] = (unsigned char) blk;
    mc_sha256(m, 42, out);
    memset(m, 0, sizeof(m));
}

static void mc_xor_stream(const unsigned char seed[32], char dom,
                          unsigned char *buf, size_t n) {
    unsigned char k[32];
    size_t off = 0;
    uint32_t blk = 0;
    while (off < n) {
        mc_ks(seed, dom, blk++, k);
        size_t take = n - off < 32 ? n - off : 32;
        for (size_t i = 0; i < take; i++) buf[off + i] ^= k[i];
        off += take;
    }
    memset(k, 0, 32);
}

/* "MS1" + base64( mac32 | ciphertext ); mac = HMAC(seed, dom | enc) */
static char *mc_seal(const unsigned char seed[32], char dom,
                     const unsigned char *raw, size_t n) {
    unsigned char *enc = malloc(n == 0 ? 1 : n);
    if (enc == NULL) return NULL;
    memcpy(enc, raw, n);
    mc_xor_stream(seed, dom, enc, n);
    unsigned char *mm = malloc(1 + n);
    if (mm == NULL) { memset(enc, 0, n); free(enc); return NULL; }
    mm[0] = (unsigned char) dom;
    memcpy(mm + 1, enc, n);
    unsigned char mac[32];
    mc_hmac32(seed, mm, 1 + n, mac);
    memset(mm, 0, 1 + n);
    free(mm);
    unsigned char *pack = malloc(32 + n);
    if (pack == NULL) { memset(enc, 0, n); free(enc); memset(mac, 0, 32); return NULL; }
    memcpy(pack, mac, 32);
    memcpy(pack + 32, enc, n);
    memset(mac, 0, 32);
    memset(enc, 0, n);
    free(enc);
    char *b64 = mc_b64enc(pack, 32 + n);
    memset(pack, 0, 32 + n);
    free(pack);
    if (b64 == NULL) return NULL;
    char *r = malloc(4 + strlen(b64));
    if (r == NULL) { free(b64); return NULL; }
    memcpy(r, "MS1", 3);
    strcpy(r + 3, b64);
    free(b64);
    return r;
}

static unsigned char *mc_unseal(const unsigned char seed[32], char dom,
                                const char *blob, size_t *n_out) {
    if (blob == NULL || blob[0] != 'M' || blob[1] != 'S' || blob[2] != '1') return NULL;
    size_t n;
    unsigned char *pack = mc_b64dec(blob + 3, &n);
    if (pack == NULL || n < 32) {
        free(pack);
        return NULL;
    }
    size_t el = n - 32;
    unsigned char *enc = pack + 32;
    unsigned char *mm = malloc(1 + el);
    if (mm == NULL) { memset(pack, 0, n); free(pack); return NULL; }
    mm[0] = (unsigned char) dom;
    memcpy(mm + 1, enc, el);
    unsigned char mac[32];
    mc_hmac32(seed, mm, 1 + el, mac);
    memset(mm, 0, 1 + el);
    free(mm);
    unsigned char diff = 0;
    for (int i = 0; i < 32; i++) diff |= (unsigned char) (mac[i] ^ pack[i]);
    memset(mac, 0, 32);
    if (diff != 0) {
        memset(pack, 0, n);
        free(pack);
        return NULL;
    }
    unsigned char *raw = malloc(el + 1);
    if (raw == NULL) { memset(pack, 0, n); free(pack); return NULL; }
    memcpy(raw, enc, el);
    raw[el] = 0;
    memset(pack, 0, n);
    free(pack);
    mc_xor_stream(seed, dom, raw, el);
    raw[el] = 0;
    *n_out = el;
    return raw;
}

/* ---- live tables (native-owned) ------------------------------------------*/

static mc_idstr_v AR_RULES;   /* auto-reply per-chat texts   */
static mc_id_v    AR_EXCL;    /* auto-reply excluded chats   */
static mc_str_v   AR_POOL;    /* auto-reply random pool      */
static mc_idstr_v KW_ENTRIES; /* keyword sets id -> words    */
static mc_mark    AR_COOL[256];
static mc_mark    KW_MARK[256];
static uint32_t   AR_RNG = 0x9E3779B9u;
static int        AR_LOADED;  /* paranoid re-load guard      */
static int        KW_LOADED;

/* ---- auto-reply store: 'R'\tid\ttext  'X'\tid  'P'\ttext ------------------*/

static void mc_ar_reset_store(void) {
    mc_idstr_reset(&AR_RULES);
    mc_id_reset(&AR_EXCL);
    mc_str_reset(&AR_POOL);
}

static void mc_ar_load_line(char *line) {
    if (line[0] == 'R' && line[1] == '\t') {
        char *p = line + 2;
        char *tab = strchr(p, '\t');
        if (tab == NULL) return;
        *tab = 0;
        jlong id = (jlong) strtoll(p, NULL, 10);
        char *text = tab + 1;
        mc_unesc(text);
        mc_idstr_put(&AR_RULES, id, text);
    } else if (line[0] == 'X' && line[1] == '\t') {
        jlong id = (jlong) strtoll(line + 2, NULL, 10);
        mc_id_add(&AR_EXCL, id);
    } else if (line[0] == 'P' && line[1] == '\t') {
        char *text = line + 2;
        mc_unesc(text);
        mc_str_push(&AR_POOL, text);
    }
}

static int mc_ar_load(const unsigned char seed[32], const char *blob) {
    mc_ar_reset_store();
    AR_LOADED = 1;
    size_t n;
    unsigned char *raw = mc_unseal(seed, 'A', blob, &n);
    if (raw == NULL) return 0;
    char *p = (char *) raw;
    while (*p) {
        char *nl = strchr(p, '\n');
        if (nl != NULL) *nl = 0;
        mc_ar_load_line(p);
        if (nl == NULL) break;
        p = nl + 1;
    }
    memset(raw, 0, n);
    free(raw);
    return 1;
}

static char *mc_ar_blob(const unsigned char seed[32]) {
    mc_sb s;
    memset(&s, 0, sizeof(s));
    char num[32];
    for (int i = 0; i < AR_RULES.len; i++) {
        char *e = mc_esc(AR_RULES.e[i].s);
        if (e == NULL) continue;
        mc_sb_c(&s, 'R');
        mc_sb_c(&s, '\t');
        snprintf(num, sizeof(num), "%lld", (long long) AR_RULES.e[i].id);
        mc_sb_s(&s, num);
        mc_sb_c(&s, '\t');
        mc_sb_s(&s, e);
        mc_sb_c(&s, '\n');
        free(e);
    }
    for (int i = 0; i < AR_EXCL.len; i++) {
        mc_sb_c(&s, 'X');
        mc_sb_c(&s, '\t');
        snprintf(num, sizeof(num), "%lld", (long long) AR_EXCL.e[i]);
        mc_sb_s(&s, num);
        mc_sb_c(&s, '\n');
    }
    for (int i = 0; i < AR_POOL.len; i++) {
        char *e = mc_esc(AR_POOL.e[i]);
        if (e == NULL) continue;
        mc_sb_c(&s, 'P');
        mc_sb_c(&s, '\t');
        mc_sb_s(&s, e);
        mc_sb_c(&s, '\n');
        free(e);
    }
    if (s.b == NULL) {
        s.b = mc_dup("");
        s.len = 0;
        s.cap = 1;
    }
    char *blob = mc_seal(seed, 'A', (const unsigned char *) (s.b == NULL ? "" : s.b), s.len);
    if (s.b != NULL) {
        memset(s.b, 0, s.cap);
        free(s.b);
    }
    return blob;
}

/* ---- keyword store: 'K'\tid\twords ----------------------------------------*/

static int mc_kw_load(const unsigned char seed[32], const char *blob) {
    mc_idstr_reset(&KW_ENTRIES);
    KW_LOADED = 1;
    size_t n;
    unsigned char *raw = mc_unseal(seed, 'K', blob, &n);
    if (raw == NULL) return 0;
    char *p = (char *) raw;
    while (*p) {
        char *nl = strchr(p, '\n');
        if (nl != NULL) *nl = 0;
        if (p[0] == 'K' && p[1] == '\t') {
            char *q = p + 2;
            char *tab = strchr(q, '\t');
            if (tab != NULL) {
                *tab = 0;
                mc_unesc(tab + 1);
                mc_idstr_put(&KW_ENTRIES, (jlong) strtoll(q, NULL, 10), tab + 1);
            }
        }
        if (nl == NULL) break;
        p = nl + 1;
    }
    memset(raw, 0, n);
    free(raw);
    return 1;
}

static char *mc_kw_blob(const unsigned char seed[32]) {
    mc_sb s;
    memset(&s, 0, sizeof(s));
    char num[32];
    for (int i = 0; i < KW_ENTRIES.len; i++) {
        char *e = mc_esc(KW_ENTRIES.e[i].s);
        if (e == NULL) continue;
        mc_sb_c(&s, 'K');
        mc_sb_c(&s, '\t');
        snprintf(num, sizeof(num), "%lld", (long long) KW_ENTRIES.e[i].id);
        mc_sb_s(&s, num);
        mc_sb_c(&s, '\t');
        mc_sb_s(&s, e);
        mc_sb_c(&s, '\n');
        free(e);
    }
    if (s.b == NULL) {
        s.b = mc_dup("");
        s.len = 0;
        s.cap = 1;
    }
    char *blob = mc_seal(seed, 'K', (const unsigned char *) (s.b == NULL ? "" : s.b), s.len);
    if (s.b != NULL) {
        memset(s.b, 0, s.cap);
        free(s.b);
    }
    return blob;
}

/* ---- keyword matcher ------------------------------------------------------*/

/* Split a word list on ',' and U+060C (arabic comma, UTF-8 D8 8C), trim
 * each word, keep Java-parity length >= 2 (UTF-16 units) and do a plain
 * contains against the Java-lowercased message text. Returns a malloc'd
 * copy of the winning word, or NULL. */
static char *mc_kw_hit_words(const char *words, const char *lower) {
    const unsigned char *seg = (const unsigned char *) words;
    const unsigned char *p = seg;
    for (;;) {
        int sep = 0;
        if (*p == 0) sep = 9;            /* end marker */
        else if (*p == ',') sep = 1;
        else if (p[0] == 0xD8 && p[1] == 0x8C) sep = 2;
        if (sep) {
            if (p > seg) {
                char *w = mc_ndup((const char *) seg, (size_t) (p - seg));
                if (w != NULL) {
                    mc_trim(w);
                    if (mc_len16(w) >= 2 && strstr(lower, w) != NULL) return w;
                    free(w);
                }
            }
            if (sep == 9) break;
            p += (sep == 2) ? 2 : 1;
            seg = p;
        } else {
            p++;
        }
    }
    return NULL;
}

/* One call per message: freshness gate (2 min), entry order scan with the
 * per-chat 30 s throttle. NULL = stay silent (no hit, or hit but throttled
 * - exactly the old visible behaviour in both cases). */
static char *mc_kw_match(jlong dialogId, jlong msgDateSec, jlong nowMs,
                         const char *lower) {
    if (lower == NULL) return NULL;
    if (nowMs - msgDateSec * 1000 > 120000) return NULL;
    for (int i = 0; i < KW_ENTRIES.len; i++) {
        if (KW_ENTRIES.e[i].id != 0 && KW_ENTRIES.e[i].id != dialogId) continue;
        char *hit = mc_kw_hit_words(KW_ENTRIES.e[i].s, lower);
        if (hit == NULL) continue;
        jlong last = mc_mark_get(KW_MARK, 256, dialogId);
        if (last != 0 && nowMs - last < 30000) {
            free(hit);
            return NULL;
        }
        mc_mark_put(KW_MARK, 256, dialogId, nowMs);
        return hit;
    }
    return NULL;
}

/* ---- auto-reply decision gates --------------------------------------------*/

/* Exclusion + per-chat cooldown in one atomic step; stamps on pass so a
 * burst of messages schedules exactly one reply (old gate 2.5 + gate 6). */
static int mc_ar_should_reply(jlong dialogId, jlong nowMs, jint coolMin) {
    if (mc_id_has(&AR_EXCL, dialogId)) return 0;
    jlong last = mc_mark_get(AR_COOL, 256, dialogId);
    jlong cool = ((jlong) coolMin) * 60000;
    if (last != 0 && nowMs - last < cool) return 0;
    mc_mark_put(AR_COOL, 256, dialogId, nowMs);
    return 1;
}

/* Reply window: disabled passes; off weekday fails; equal bounds = all day;
 * start > end means the window crosses midnight. Device-local time, same
 * as the Java Calendar code it replaces. */
static int mc_window_pass(int enabled, int daysMask, int startMin, int endMin,
                          jlong nowMs) {
    if (!enabled) return 1;
    time_t t = (time_t) (nowMs / 1000);
    struct tm tmv;
    memset(&tmv, 0, sizeof(tmv));
    if (localtime_r(&t, &tmv) == NULL) return 1;
    int dayBit = 1 << tmv.tm_wday;
    if ((daysMask & dayBit) == 0) return 0;
    if (startMin == endMin) return 1;
    int now = tmv.tm_hour * 60 + tmv.tm_min;
    if (startMin < endMin) return now >= startMin && now < endMin;
    return now >= startMin || now < endMin;
}

/* random emoji suffix table (XOR 0xA7 garbled so a strings dump shows
 * nothing; decoded lazily only when a suffix is actually appended) */
static const unsigned char EMO1[] = {0x45,0x3b,0x22,0x00}; /* ✅ */
static const unsigned char EMO2[] = {0x57,0x38,0x36,0x2b,0x00}; /* 👌 */
static const unsigned char EMO3[] = {0x57,0x38,0x2b,0x3e,0x00}; /* 🌙 */
static const unsigned char EMO4[] = {0x45,0x3d,0x06,0x00}; /* ⚡ */
static const unsigned char EMO5[] = {0x57,0x38,0x3e,0x28,0x00}; /* 🙏 */
static const unsigned char EMO6[] = {0x57,0x38,0x35,0x0c,0x00}; /* 💫 */
static const unsigned char EMO7[] = {0x45,0x3f,0x32,0x00}; /* ☕ */
static const unsigned char EMO8[] = {0x57,0x38,0x2b,0x38,0x00}; /* 🌟 */
static const unsigned char *const MC_EMO[8] = {
    EMO1, EMO2, EMO3, EMO4, EMO5, EMO6, EMO7, EMO8
};

static void mc_unemo(char *out, const unsigned char *enc) {
    int i = 0;
    while (enc[i]) {
        out[i] = (char) (enc[i] ^ XK);
        i++;
    }
    out[i] = 0;
}

/* replace every "{name}" marker with firstName (Java String.replace works
 * on the template, so the name itself is never re-scanned) */
static char *mc_replace_mark(const char *tpl, const char *name) {
    static const char MRK[] = { '{', 'n', 'a', 'm', 'e', '}', 0 };
    if (name == NULL) name = "";
    const size_t nl = strlen(name), ml = 6, tl = strlen(tpl);
    size_t cnt = 0;
    for (const char *p = tpl; *p; p++) {
        if (strncmp(p, MRK, ml) == 0) {
            cnt++;
            p += ml - 1; /* markers never overlap (Java replace parity) */
        }
    }
    char *out = malloc(tl + cnt * (nl > ml ? nl - ml : 0) + 1);
    if (out == NULL) return mc_dup(tpl);
    char *w = out;
    const char *p = tpl;
    while (*p) {
        if (strncmp(p, MRK, ml) == 0) {
            memcpy(w, name, nl);
            w += nl;
            p += ml;
        } else {
            *w++ = *p++;
        }
    }
    *w = 0;
    return out;
}

/* the full ladder, byte-identical to the old Java pipeline:
 * per-chat rule > random pool > night text (only while the window actively
 * gates) > general text > localized default; then {name} substitution,
 * then the optional emoji suffix. */
static char *mc_ar_resolve(jlong dialogId, int poolOn, int nightActive,
                           const char *general, const char *night,
                           const char *deflt, const char *firstName,
                           int emojiOn, jlong nowMs) {
    const char *pick = mc_idstr_get(&AR_RULES, dialogId);
    if (pick == NULL || *pick == 0) {
        pick = NULL;
        if (poolOn && AR_POOL.len > 0) {
            uint32_t r = (uint32_t) nowMs * 2654435761u ^ (AR_RNG += 2246822519u);
            const char *pl = AR_POOL.e[r % (uint32_t) AR_POOL.len];
            if (pl != NULL && *pl != 0) pick = pl;
        }
        if (pick == NULL) {
            pick = (nightActive && night != NULL && *night != 0)
                 ? night
                 : ((general != NULL && *general != 0) ? general : NULL);
        }
        if (pick == NULL) pick = deflt == NULL ? "" : deflt;
    }
    char *out = mc_replace_mark(pick, firstName);
    if (out == NULL) return NULL;
    if (emojiOn) {
        char emo[8];
        uint32_t r = (uint32_t) nowMs * 1103515245u ^ (AR_RNG += 0x68BC21EBu);
        mc_unemo(emo, MC_EMO[r % 8u]);
        size_t ol = strlen(out), el = strlen(emo);
        char *nb = malloc(ol + 1 + el + 1);
        if (nb != NULL) {
            memcpy(nb, out, ol);
            nb[ol] = ' ';
            memcpy(nb + ol + 1, emo, el + 1);
            free(out);
            out = nb;
        }
        memset(emo, 0, sizeof(emo));
    }
    return out;
}

/* ---- JNI surface (thin wrappers over the pure cores above) ----------------*/

JNIEXPORT jint JNICALL MC_CLASS(nKwLoad)(JNIEnv *env, jclass c, jstring blob) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    unsigned char seed[32];
    int ok = mc_seed(env, seed);
    char *b = ok ? mc_utf(env, blob) : NULL;
    jint r = ok ? (jint) mc_kw_load(seed, b) : 0;
    free(b);
    memset(seed, 0, 32);
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jstring JNICALL MC_CLASS(nKwBlob)(JNIEnv *env, jclass c) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    unsigned char seed[32];
    if (!mc_seed(env, seed)) {
        pthread_mutex_unlock(&mc_mu);
        return NULL;
    }
    char *b = mc_kw_blob(seed);
    memset(seed, 0, 32);
    pthread_mutex_unlock(&mc_mu);
    if (b == NULL) return NULL;
    jstring r = (*env)->NewStringUTF(env, b);
    memset(b, 0, strlen(b));
    free(b);
    return r;
}

JNIEXPORT void JNICALL MC_CLASS(nKwUpsert)(JNIEnv *env, jclass c, jlong id, jstring words) {
    (void) c;
    char *w = mc_utf(env, words);
    pthread_mutex_lock(&mc_mu);
    mc_idstr_put(&KW_ENTRIES, id, w);
    pthread_mutex_unlock(&mc_mu);
    free(w);
}

JNIEXPORT jint JNICALL MC_CLASS(nKwCount)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jint r = KW_ENTRIES.len;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jlong JNICALL MC_CLASS(nKwIdAt)(JNIEnv *env, jclass c, jint idx) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jlong r = (idx < 0 || idx >= KW_ENTRIES.len) ? 0 : KW_ENTRIES.e[idx].id;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jstring JNICALL MC_CLASS(nKwWordsAt)(JNIEnv *env, jclass c, jint idx) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    const char *w = (idx < 0 || idx >= KW_ENTRIES.len) ? NULL : KW_ENTRIES.e[idx].s;
    jstring r = (w == NULL) ? NULL : (*env)->NewStringUTF(env, w);
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jstring JNICALL MC_CLASS(nKwMatch)(JNIEnv *env, jclass c, jlong dialogId,
                                             jlong msgDateSec, jlong nowMs, jstring lower) {
    (void) c;
    char *t = mc_utf(env, lower);
    pthread_mutex_lock(&mc_mu);
    char *hit = (t == NULL) ? NULL : mc_kw_match(dialogId, msgDateSec, nowMs, t);
    pthread_mutex_unlock(&mc_mu);
    free(t);
    if (hit == NULL) return NULL;
    jstring r = (*env)->NewStringUTF(env, hit);
    free(hit);
    return r;
}

JNIEXPORT jint JNICALL MC_CLASS(nArLoad)(JNIEnv *env, jclass c, jstring blob) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    unsigned char seed[32];
    int ok = mc_seed(env, seed);
    char *b = ok ? mc_utf(env, blob) : NULL;
    jint r = ok ? (jint) mc_ar_load(seed, b) : 0;
    free(b);
    memset(seed, 0, 32);
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jstring JNICALL MC_CLASS(nArBlob)(JNIEnv *env, jclass c) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    unsigned char seed[32];
    if (!mc_seed(env, seed)) {
        pthread_mutex_unlock(&mc_mu);
        return NULL;
    }
    char *b = mc_ar_blob(seed);
    memset(seed, 0, 32);
    pthread_mutex_unlock(&mc_mu);
    if (b == NULL) return NULL;
    jstring r = (*env)->NewStringUTF(env, b);
    memset(b, 0, strlen(b));
    free(b);
    return r;
}

JNIEXPORT void JNICALL MC_CLASS(nArUpsertRule)(JNIEnv *env, jclass c, jlong id, jstring text) {
    (void) c;
    char *t = mc_utf(env, text);
    pthread_mutex_lock(&mc_mu);
    mc_idstr_put(&AR_RULES, id, t);
    pthread_mutex_unlock(&mc_mu);
    free(t);
}

JNIEXPORT jint JNICALL MC_CLASS(nArRuleCount)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jint r = AR_RULES.len;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jlong JNICALL MC_CLASS(nArRuleIdAt)(JNIEnv *env, jclass c, jint idx) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jlong r = (idx < 0 || idx >= AR_RULES.len) ? 0 : AR_RULES.e[idx].id;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jstring JNICALL MC_CLASS(nArRuleText)(JNIEnv *env, jclass c, jlong id) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    const char *t = mc_idstr_get(&AR_RULES, id);
    jstring r = (t == NULL || *t == 0) ? NULL : (*env)->NewStringUTF(env, t);
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT void JNICALL MC_CLASS(nArAddExcl)(JNIEnv *env, jclass c, jlong id) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    mc_id_add(&AR_EXCL, id);
    pthread_mutex_unlock(&mc_mu);
}

JNIEXPORT void JNICALL MC_CLASS(nArDelExcl)(JNIEnv *env, jclass c, jlong id) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    mc_id_del(&AR_EXCL, id);
    pthread_mutex_unlock(&mc_mu);
}

JNIEXPORT jint JNICALL MC_CLASS(nArExclCount)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jint r = AR_EXCL.len;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jlong JNICALL MC_CLASS(nArExclIdAt)(JNIEnv *env, jclass c, jint idx) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jlong r = (idx < 0 || idx >= AR_EXCL.len) ? 0 : AR_EXCL.e[idx];
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jboolean JNICALL MC_CLASS(nArIsExcl)(JNIEnv *env, jclass c, jlong id) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jboolean r = mc_id_has(&AR_EXCL, id) ? JNI_TRUE : JNI_FALSE;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jint JNICALL MC_CLASS(nArPoolCount)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jint r = AR_POOL.len;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jstring JNICALL MC_CLASS(nArPoolAt)(JNIEnv *env, jclass c, jint idx) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    const char *t = (idx < 0 || idx >= AR_POOL.len) ? NULL : AR_POOL.e[idx];
    jstring r = (t == NULL) ? NULL : (*env)->NewStringUTF(env, t);
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT void JNICALL MC_CLASS(nArPoolAdd)(JNIEnv *env, jclass c, jstring text) {
    (void) c;
    char *t = mc_utf(env, text);
    pthread_mutex_lock(&mc_mu);
    if (t != NULL) mc_str_push(&AR_POOL, t);
    pthread_mutex_unlock(&mc_mu);
    free(t);
}

JNIEXPORT void JNICALL MC_CLASS(nArPoolSet)(JNIEnv *env, jclass c, jint idx, jstring text) {
    (void) c;
    char *t = mc_utf(env, text);
    pthread_mutex_lock(&mc_mu);
    mc_str_set(&AR_POOL, idx, t);
    pthread_mutex_unlock(&mc_mu);
    free(t);
}

JNIEXPORT void JNICALL MC_CLASS(nArPoolDel)(JNIEnv *env, jclass c, jint idx) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    mc_str_del(&AR_POOL, idx);
    pthread_mutex_unlock(&mc_mu);
}

JNIEXPORT jboolean JNICALL MC_CLASS(nArShouldReply)(JNIEnv *env, jclass c, jlong dialogId,
                                                    jlong nowMs, jint coolMin) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jboolean r = mc_ar_should_reply(dialogId, nowMs, coolMin) ? JNI_TRUE : JNI_FALSE;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jboolean JNICALL MC_CLASS(nArWindowPass)(JNIEnv *env, jclass c, jint enabled,
                                                   jint daysMask, jint startMin, jint endMin,
                                                   jlong nowMs) {
    (void) env; (void) c;
    return mc_window_pass(enabled, daysMask, startMin, endMin, nowMs) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL MC_CLASS(nArResolveText)(JNIEnv *env, jclass c, jlong dialogId,
                                                   jint poolOn, jint nightActive, jstring general,
                                                   jstring night, jstring deflt, jstring firstName,
                                                   jint emojiOn, jlong nowMs) {
    (void) c;
    char *g = mc_utf(env, general);
    char *n = mc_utf(env, night);
    char *d = mc_utf(env, deflt);
    char *fn = mc_utf(env, firstName);
    pthread_mutex_lock(&mc_mu);
    char *out = mc_ar_resolve(dialogId, poolOn != 0, nightActive != 0, g, n, d, fn,
                              emojiOn != 0, nowMs);
    pthread_mutex_unlock(&mc_mu);
    free(g);
    free(n);
    free(d);
    free(fn);
    if (out == NULL) return NULL;
    jstring r = (*env)->NewStringUTF(env, out);
    free(out);
    return r;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) vm; (void) reserved;
    return JNI_VERSION_1_6;
}
