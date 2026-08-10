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

/* v186 (batch 2D) anti-debug shield - defined in the tail section; blob
 * writers below consult it before persisting anything to disk. */
static int mc_ad_blocked(void);

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

/* ---- v186 (batch 2D): UTF-8 -> real UTF-16, then NewString ---------------
 * NewStringUTF expects MODIFIED UTF-8: on ART a standard 4-byte sequence
 * (every emoji) is invalid there and gets garbled or rejected. All v184/185
 * free-text returns went through it, so an emoji hit/pool/resolve could
 * render broken - owned catch of batch 2D. Every free-text jstring now
 * leaves through NewString(jchar*, units), which has no such quirk. */
static int mc_u16(const char *s, jchar **out) {
    *out = NULL;
    if (s == NULL) s = "";
    const unsigned char *p = (const unsigned char *) s;
    int units = 0;
    for (const unsigned char *q = p; *q; q++) {
        if ((*q & 0xC0) != 0x80) units += (*q >= 0xF0) ? 2 : 1;
    }
    jchar *b = malloc((size_t) (units + 1) * sizeof(jchar));
    if (b == NULL) return -1;
    int w = 0;
    while (*p) {
        uint32_t cp;
        int n;
        if (*p < 0x80) { cp = *p; n = 1; }
        else if ((*p & 0xE0) == 0xC0) { cp = ((uint32_t) (p[0] & 0x1F) << 6) | (uint32_t) (p[1] & 0x3F); n = 2; }
        else if ((*p & 0xF0) == 0xE0) { cp = ((uint32_t) (p[0] & 0x0F) << 12) | ((uint32_t) (p[1] & 0x3F) << 6) | (uint32_t) (p[2] & 0x3F); n = 3; }
        else if ((*p & 0xF8) == 0xF0) { cp = ((uint32_t) (p[0] & 0x07) << 18) | ((uint32_t) (p[1] & 0x3F) << 12) | ((uint32_t) (p[2] & 0x3F) << 6) | (uint32_t) (p[3] & 0x3F); n = 4; }
        else { cp = *p; n = 1; }
        if (cp > 0xFFFF) {
            uint32_t u = cp - 0x10000;
            b[w++] = (jchar) (0xD800 + (u >> 10));
            b[w++] = (jchar) (0xDC00 + (u & 0x3FF));
        } else {
            b[w++] = (jchar) cp;
        }
        p += n;
    }
    *out = b;
    return w;
}

static jstring mc_jstr(JNIEnv *env, const char *utf8) {
    jchar *b = NULL;
    int n = mc_u16(utf8, &b);
    if (n < 0) return NULL;
    jstring r = (*env)->NewString(env, b, (jsize) n);
    free(b);
    return r;
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
    return mc_jstr(env, hex);
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
    jstring r = mc_jstr(env, tmp);
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
    if (mc_ad_blocked()) return NULL; /* v186: no disk writes while traced */
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
    jstring r = mc_jstr(env, b);
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
    jstring r = (w == NULL) ? NULL : mc_jstr(env, w);
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
    jstring r = mc_jstr(env, hit);
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
    if (mc_ad_blocked()) return NULL; /* v186: no disk writes while traced */
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
    jstring r = mc_jstr(env, b);
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
    jstring r = (t == NULL || *t == 0) ? NULL : mc_jstr(env, t);
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
    jstring r = (t == NULL) ? NULL : mc_jstr(env, t);
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
    jstring r = mc_jstr(env, out);
    free(out);
    return r;
}

/*
 * ============================================================================
 * MeeroX v185 (batch 2C) - organization & radar hearts: the smart-folder
 * preset design (garbled table), the delete-hunter log + key machine, the
 * watch list + snapshot differ + change log + message-notify throttle, and
 * the activity-stats decision layer (local-day bounds, dry-chat policy,
 * hourly assembly). Same law as 2B: Java keeps the Telegram handshake and
 * the screens; every decided thing lives here. Hunter/watch stores persist
 * as opaque seed-sealed blobs (dom 'D' / 'W'); legacy JSON is imported once
 * and its plaintext dropped. Failures answer safe values, the v182/v184
 * Java paths stay as the degraded fallback.
 * ============================================================================
 */

/* ---- Smart Folders: static design table ------------------------------------
 * Flag values mirror MessagesController.DIALOG_FILTER_FLAG_* exactly
 * (0x01 contacts, 0x02 non-contacts, 0x04 groups, 0x08 channels, 0x10 bots,
 * 0x20 exclude-muted, 0x40 exclude-read, 0x80 exclude-archived); parity
 * locked by the 2C vector suite against the old Java table. Nothing here is
 * persisted - the table IS the design, so it is garbled like the emoji. */

static const unsigned char SF_T1[] = {0xf4,0xca,0xc6,0xd5,0xd3,0xe1,0xc8,0xcb,0xc3,0xc2,0xd5,0xf2,0xc9,0xd5,0xc2,0xc6,0xc3,0xe4,0xcf,0xc6,0xc9,0xc9,0xc2,0xcb,0xd4,0xa7};
static const unsigned char SF_R1[] = {0xf4,0xca,0xc6,0xd5,0xd3,0xe1,0xc8,0xcb,0xc3,0xc2,0xd5,0xf2,0xc9,0xd5,0xc2,0xc6,0xc3,0xe4,0xcf,0xc6,0xc9,0xc9,0xc2,0xcb,0xd4,0xf5,0xd2,0xcb,0xc2,0xa7};
static const unsigned char SF_T2[] = {0xf4,0xca,0xc6,0xd5,0xd3,0xe1,0xc8,0xcb,0xc3,0xc2,0xd5,0xe5,0xc8,0xd3,0xd4,0xa7};
static const unsigned char SF_R2[] = {0xf4,0xca,0xc6,0xd5,0xd3,0xe1,0xc8,0xcb,0xc3,0xc2,0xd5,0xe5,0xc8,0xd3,0xd4,0xf5,0xd2,0xcb,0xc2,0xa7};
static const unsigned char SF_T3[] = {0xf4,0xca,0xc6,0xd5,0xd3,0xe1,0xc8,0xcb,0xc3,0xc2,0xd5,0xf2,0xc9,0xd5,0xc2,0xc6,0xc3,0xe4,0xcf,0xc6,0xd3,0xd4,0xa7};
static const unsigned char SF_R3[] = {0xf4,0xca,0xc6,0xd5,0xd3,0xe1,0xc8,0xcb,0xc3,0xc2,0xd5,0xf2,0xc9,0xd5,0xc2,0xc6,0xc3,0xe4,0xcf,0xc6,0xd3,0xd4,0xf5,0xd2,0xcb,0xc2,0xa7};
static const unsigned char SF_T4[] = {0xf4,0xca,0xc6,0xd5,0xd3,0xe1,0xc8,0xcb,0xc3,0xc2,0xd5,0xe1,0xc6,0xca,0xce,0xcb,0xde,0xa7};
static const unsigned char SF_R4[] = {0xf4,0xca,0xc6,0xd5,0xd3,0xe1,0xc8,0xcb,0xc3,0xc2,0xd5,0xe1,0xc6,0xca,0xce,0xcb,0xde,0xf5,0xd2,0xcb,0xc2,0xa7};
static const unsigned char SF_T5[] = {0xf4,0xca,0xc6,0xd5,0xd3,0xe1,0xc8,0xcb,0xc3,0xc2,0xd5,0xe6,0xc4,0xd3,0xce,0xd1,0xc2,0xe0,0xd5,0xc8,0xd2,0xd7,0xd4,0xa7};
static const unsigned char SF_R5[] = {0xf4,0xca,0xc6,0xd5,0xd3,0xe1,0xc8,0xcb,0xc3,0xc2,0xd5,0xe6,0xc4,0xd3,0xce,0xd1,0xc2,0xe0,0xd5,0xc8,0xd2,0xd7,0xd4,0xf5,0xd2,0xcb,0xc2,0xa7};
static const unsigned char SF_E1[] = {0x57,0x38,0x34,0x05,0xa7};
static const unsigned char SF_E2[] = {0x57,0x38,0x03,0x31,0xa7};
static const unsigned char SF_E3[] = {0x57,0x38,0x34,0x02,0xa7};
static const unsigned char SF_E4[] = {0x45,0x3a,0x03,0x48,0x1f,0x28,0xa7};
static const unsigned char SF_E5[] = {0x57,0x38,0x36,0x02,0xa7};

typedef struct { const unsigned char *tk, *rk, *emo; int flags; int color; } mc_sf_p;

static const mc_sf_p MC_SF[5] = {
    { SF_T1, SF_R1, SF_E1, 0x68, 1 }, /* channels + unread + not muted    */
    { SF_T2, SF_R2, SF_E2, 0x90, 5 }, /* bots, archived clutter excluded  */
    { SF_T3, SF_R3, SF_E3, 0x47, 0 }, /* unread private chats & groups    */
    { SF_T4, SF_R4, SF_E4, 0x81, 2 }, /* contacts, no archived clutter    */
    { SF_T5, SF_R5, SF_E5, 0x24, 3 }, /* active (non-muted) groups        */
};

/* Java equalsIgnoreCase parity for our titles: ASCII letters fold, every
 * other byte compares exactly (the localized titles are Arabic - no case). */
static int mc_sf_title_eq(const char *a, const char *b) {
    if (a == NULL || b == NULL) return 0;
    for (;; a++, b++) {
        unsigned char x = (unsigned char) *a, y = (unsigned char) *b;
        if (x >= 'A' && x <= 'Z') x = (unsigned char) (x | 0x20);
        if (y >= 'A' && y <= 'Z') y = (unsigned char) (y | 0x20);
        if (x != y) return 0;
        if (x == 0) return 1;
    }
}

/* ---- shared: next tab-separated field (in place, unescaped by caller) ---- */

static char *mc_nextf(char **p) {
    char *s = *p;
    char *t = strchr(s, '\t');
    if (t != NULL) {
        *t = 0;
        *p = t + 1;
    } else {
        *p = s + strlen(s);
    }
    return s;
}

/* ---- Java String.hashCode parity (UTF-16 code units, 31*x+unit, wrap) ---- */

static jint mc_hash16(const char *s) {
    uint32_t h = 0;
    const unsigned char *p = (const unsigned char *) (s == NULL ? "" : s);
    while (*p) {
        uint32_t cp;
        int n;
        if (*p < 0x80) { cp = *p; n = 1; }
        else if ((*p & 0xE0) == 0xC0) { cp = ((uint32_t) (p[0] & 0x1F) << 6) | (uint32_t) (p[1] & 0x3F); n = 2; }
        else if ((*p & 0xF0) == 0xE0) { cp = ((uint32_t) (p[0] & 0x0F) << 12) | ((uint32_t) (p[1] & 0x3F) << 6) | (uint32_t) (p[2] & 0x3F); n = 3; }
        else if ((*p & 0xF8) == 0xF0) { cp = ((uint32_t) (p[0] & 0x07) << 18) | ((uint32_t) (p[1] & 0x3F) << 12) | ((uint32_t) (p[2] & 0x3F) << 6) | (uint32_t) (p[3] & 0x3F); n = 4; }
        else { cp = *p; n = 1; }
        if (cp > 0xFFFF) { /* surrogate pair like Java sees it */
            uint32_t u = cp - 0x10000;
            h = h * 31u + (0xD800u + (u >> 10));
            h = h * 31u + (0xDC00u + (u & 0x3FFu));
        } else {
            h = h * 31u + cp;
        }
        p += n;
    }
    return (jint) h;
}

/* ---- Delete Hunter (dom 'D'): ring log + key machine ----------------------- */

typedef struct { jlong t, id; char *kind, *who, *oldv, *newv; } mc_dh_item;

static mc_dh_item *DH_LOG;
static int DH_LEN, DH_CAP, DH_LOADED;

static void mc_dh_free_item(mc_dh_item *it) {
    free(it->kind);
    free(it->who);
    free(it->oldv);
    free(it->newv);
    it->kind = it->who = it->oldv = it->newv = NULL;
}

static void mc_dh_reset(void) {
    for (int i = 0; i < DH_LEN; i++) mc_dh_free_item(&DH_LOG[i]);
    free(DH_LOG);
    DH_LOG = NULL;
    DH_LEN = 0;
    DH_CAP = 0;
}

/* head = runtime insert (newest first, cap 150 like the old JSON head-put);
 * head = 0 is the legacy-import path (preserve order, stop at the cap). */
static void mc_dh_push(jlong t, jlong id, const char *kind, const char *who,
                       const char *oldv, const char *newv, int head) {
    if (!head && DH_LEN >= 150) return;
    if (head && DH_LEN >= 150) {
        mc_dh_free_item(&DH_LOG[DH_LEN - 1]);
        DH_LEN--;
    }
    if (!mc_grow((void **) &DH_LOG, &DH_CAP, DH_LEN + 1, sizeof(mc_dh_item))) return;
    mc_dh_item it;
    it.t = t;
    it.id = id;
    it.kind = mc_dup(kind == NULL ? "" : kind);
    it.who = mc_dup(who == NULL ? "" : who);
    it.oldv = mc_dup(oldv == NULL ? "" : oldv);
    it.newv = mc_dup(newv == NULL ? "" : newv);
    if (head) {
        if (DH_LEN > 0) memmove(DH_LOG + 1, DH_LOG, (size_t) DH_LEN * sizeof(mc_dh_item));
        DH_LOG[0] = it;
    } else {
        DH_LOG[DH_LEN] = it;
    }
    DH_LEN++;
}

/* entry key: t_id_kind_javaHash(old) - the old screen-key format verbatim */
static char *mc_dh_key(jlong t, jlong id, const char *kind, const char *oldv) {
    char buf[128];
    snprintf(buf, sizeof(buf), "%lld_%lld_%s_%d", (long long) t, (long long) id,
             kind == NULL ? "" : kind, (int) mc_hash16(oldv));
    return mc_dup(buf);
}

static int mc_dh_load(const unsigned char seed[32], const char *blob) {
    mc_dh_reset();
    DH_LOADED = 1;
    size_t n;
    unsigned char *raw = mc_unseal(seed, 'D', blob, &n);
    if (raw == NULL) return 0;
    char *p = (char *) raw;
    while (*p) {
        char *nl = strchr(p, '\n');
        if (nl != NULL) *nl = 0;
        if (p[0] == 'L' && p[1] == '\t') {
            char *cur = p + 2;
            jlong t = (jlong) strtoll(mc_nextf(&cur), NULL, 10);
            jlong id = (jlong) strtoll(mc_nextf(&cur), NULL, 10);
            char *kind = mc_nextf(&cur);
            char *who = mc_nextf(&cur);
            char *oldv = mc_nextf(&cur);
            char *newv = mc_nextf(&cur);
            mc_unesc(kind);
            mc_unesc(who);
            mc_unesc(oldv);
            mc_unesc(newv);
            mc_dh_push(t, id, kind, who, oldv, newv, 0);
        }
        if (nl == NULL) break;
        p = nl + 1;
    }
    memset(raw, 0, n);
    free(raw);
    return 1;
}

static char *mc_dh_blob(const unsigned char seed[32]) {
    mc_sb s;
    memset(&s, 0, sizeof(s));
    char num[32];
    for (int i = 0; i < DH_LEN; i++) {
        mc_dh_item *it = &DH_LOG[i];
        char *k = mc_esc(it->kind == NULL ? "" : it->kind);
        char *w = mc_esc(it->who == NULL ? "" : it->who);
        char *o = mc_esc(it->oldv == NULL ? "" : it->oldv);
        char *nw = mc_esc(it->newv == NULL ? "" : it->newv);
        if (k == NULL || w == NULL || o == NULL || nw == NULL) {
            free(k); free(w); free(o); free(nw);
            continue;
        }
        mc_sb_s(&s, "L\t");
        snprintf(num, sizeof(num), "%lld", (long long) it->t);
        mc_sb_s(&s, num);
        mc_sb_c(&s, '\t');
        snprintf(num, sizeof(num), "%lld", (long long) it->id);
        mc_sb_s(&s, num);
        mc_sb_c(&s, '\t');
        mc_sb_s(&s, k);
        mc_sb_c(&s, '\t');
        mc_sb_s(&s, w);
        mc_sb_c(&s, '\t');
        mc_sb_s(&s, o);
        mc_sb_c(&s, '\t');
        mc_sb_s(&s, nw);
        mc_sb_c(&s, '\n');
        free(k); free(w); free(o); free(nw);
    }
    if (s.b == NULL) {
        s.b = mc_dup("");
        s.len = 0;
        s.cap = 1;
    }
    char *blob = mc_seal(seed, 'D', (const unsigned char *) (s.b == NULL ? "" : s.b), s.len);
    if (s.b != NULL) {
        memset(s.b, 0, s.cap);
        free(s.b);
    }
    return blob;
}

/* one entry as an escaped TSV line: t \t id \t kind \t who \t old \t new */
static char *mc_dh_line(int idx) {
    if (idx < 0 || idx >= DH_LEN) return NULL;
    mc_dh_item *it = &DH_LOG[idx];
    mc_sb s;
    memset(&s, 0, sizeof(s));
    char num[32];
    snprintf(num, sizeof(num), "%lld", (long long) it->t);
    mc_sb_s(&s, num);
    mc_sb_c(&s, '\t');
    snprintf(num, sizeof(num), "%lld", (long long) it->id);
    mc_sb_s(&s, num);
    char *k = mc_esc(it->kind == NULL ? "" : it->kind);
    char *w = mc_esc(it->who == NULL ? "" : it->who);
    char *o = mc_esc(it->oldv == NULL ? "" : it->oldv);
    char *nw = mc_esc(it->newv == NULL ? "" : it->newv);
    if (k != NULL) { mc_sb_c(&s, '\t'); mc_sb_s(&s, k); }
    if (w != NULL) { mc_sb_c(&s, '\t'); mc_sb_s(&s, w); }
    if (o != NULL) { mc_sb_c(&s, '\t'); mc_sb_s(&s, o); }
    if (nw != NULL) { mc_sb_c(&s, '\t'); mc_sb_s(&s, nw); }
    free(k); free(w); free(o); free(nw);
    if (s.b == NULL) return mc_dup("");
    return s.b;
}

/* remove every entry whose key sits in the '\n'-joined set; returns count */
static int mc_dh_remove(const char *keys) {
    if (keys == NULL || *keys == 0) return 0;
    int removed = 0;
    for (int i = 0; i < DH_LEN;) {
        mc_dh_item *it = &DH_LOG[i];
        char *key = mc_dh_key(it->t, it->id, it->kind, it->oldv);
        int hit = 0;
        if (key != NULL) {
            size_t kl = strlen(key);
            const char *p = keys;
            for (;;) {
                const char *nl = strchr(p, '\n');
                size_t ln = nl == NULL ? strlen(p) : (size_t) (nl - p);
                if (ln == kl && memcmp(p, key, kl) == 0) { hit = 1; break; }
                if (nl == NULL) break;
                p = nl + 1;
            }
            free(key);
        }
        if (hit) {
            mc_dh_free_item(it);
            for (int j = i; j + 1 < DH_LEN; j++) DH_LOG[j] = DH_LOG[j + 1];
            DH_LEN--;
            removed++;
        } else {
            i++;
        }
    }
    return removed;
}

/* ---- Watch (dom 'W'): list + snapshots + change log + msg throttle -------- */

typedef struct { jlong id; int on; } mc_we;

static mc_we *W_ENTS;
static int W_EL, W_EC;

typedef struct {
    jlong id;
    unsigned mask; /* 1 name, 2 user, 4 photo, 8 bio, 16 bday */
    char *name, *user, *bio, *bday;
    jlong photo;
} mc_ws;

static mc_ws *W_SNAP;
static int W_SL, W_SC;

typedef struct {
    jlong t, id;
    char *who, *what, *oldv, *newv, *oldp, *newp;
} mc_wl_item;

static mc_wl_item *W_LOG;
static int W_LL, W_LC, W_LOADED;
static mc_mark W_MARK[256];

static int mc_w_find(jlong id) {
    for (int i = 0; i < W_EL; i++) {
        if (W_ENTS[i].id == id) return i;
    }
    return -1;
}

static void mc_ws_free(mc_ws *r) {
    free(r->name);
    free(r->user);
    free(r->bio);
    free(r->bday);
    r->name = r->user = r->bio = r->bday = NULL;
}

static mc_ws *mc_w_snap_get(jlong id, int create) {
    for (int i = 0; i < W_SL; i++) {
        if (W_SNAP[i].id == id) return &W_SNAP[i];
    }
    if (!create) return NULL;
    if (!mc_grow((void **) &W_SNAP, &W_SC, W_SL + 1, sizeof(mc_ws))) return NULL;
    mc_ws *r = &W_SNAP[W_SL++];
    memset(r, 0, sizeof(*r));
    r->id = id;
    return r;
}

static void mc_ws_set_str(char **slot, unsigned *mask, unsigned bit, const char *v) {
    free(*slot);
    *slot = mc_dup(v == NULL ? "" : v);
    *mask |= bit;
}

/* returns dup (caller frees) */
static int mc_w_add(jlong id) {
    if (mc_w_find(id) >= 0) return 0;
    if (!mc_grow((void **) &W_ENTS, &W_EC, W_EL + 1, sizeof(mc_we))) return 0;
    W_ENTS[W_EL].id = id;
    W_ENTS[W_EL].on = 1;
    W_EL++;
    return 1;
}

static void mc_w_remove(jlong id) {
    int i = mc_w_find(id);
    if (i >= 0) {
        for (int j = i; j + 1 < W_EL; j++) W_ENTS[j] = W_ENTS[j + 1];
        W_EL--;
    }
    for (int k = 0; k < W_SL;) {
        if (W_SNAP[k].id == id) {
            mc_ws_free(&W_SNAP[k]);
            for (int j = k; j + 1 < W_SL; j++) W_SNAP[j] = W_SNAP[j + 1];
            W_SL--;
        } else {
            k++;
        }
    }
}

/* diff: baseline (no record at all) merges silently, flags 0 - the old
 * snap==null path. Bits: 1 name, 2 username, 4 photo. Pack carries the OLD
 * values so Java can log them; the record itself is already merged when the
 * pack arrives. */
static char *mc_w_diff_user(jlong id, const char *name, const char *user, jlong photo) {
    mc_ws *r = mc_w_snap_get(id, 1);
    mc_sb s;
    memset(&s, 0, sizeof(s));
    char num[32];
    if (r == NULL) {
        mc_sb_s(&s, "0\t\t\t0");
        return s.b == NULL ? mc_dup("0\t\t\t0") : s.b;
    }
    if (r->mask == 0) { /* fresh record: silent baseline (Java snap==null) */
        mc_ws_set_str(&r->name, &r->mask, 1, name);
        mc_ws_set_str(&r->user, &r->mask, 2, user);
        r->photo = photo;
        r->mask |= 4;
        mc_sb_s(&s, "0\t\t\t0");
        return s.b == NULL ? mc_dup("0\t\t\t0") : s.b;
    }
    int flags = 0;
    const char *on = r->name == NULL ? "" : r->name;
    const char *ou = r->user == NULL ? "" : r->user;
    if (strcmp(name == NULL ? "" : name, on) != 0) flags |= 1;
    if (strcmp(user == NULL ? "" : user, ou) != 0) flags |= 2;
    if (photo != r->photo) flags |= 4;
    snprintf(num, sizeof(num), "%d", flags);
    mc_sb_s(&s, num);
    char *e1 = mc_esc(on);
    char *e2 = mc_esc(ou);
    mc_sb_c(&s, '\t');
    if (e1 != NULL) mc_sb_s(&s, e1);
    mc_sb_c(&s, '\t');
    if (e2 != NULL) mc_sb_s(&s, e2);
    mc_sb_c(&s, '\t');
    snprintf(num, sizeof(num), "%lld", (long long) r->photo);
    mc_sb_s(&s, num);
    free(e1);
    free(e2);
    if (flags & 1) mc_ws_set_str(&r->name, &r->mask, 1, name);
    if (flags & 2) mc_ws_set_str(&r->user, &r->mask, 2, user);
    if (flags & 4) r->photo = photo;
    return s.b == NULL ? mc_dup("0\t\t\t0") : s.b;
}

/* bio/bday carry their own baseline flags (Java snap.has("bio")/has("bday"));
 * who for these log rows is the snapshotted name, "" when unknown. */
static char *mc_w_diff_full(jlong id, const char *bio, const char *bday) {
    mc_ws *r = mc_w_snap_get(id, 1);
    mc_sb s;
    memset(&s, 0, sizeof(s));
    char num[32];
    if (r == NULL) return mc_dup("0\t\t\t");
    int flags = 0;
    const char *ob = (r->mask & 8) && r->bio != NULL ? r->bio : "";
    const char *od = (r->mask & 16) && r->bday != NULL ? r->bday : "";
    if ((r->mask & 8) && strcmp(bio == NULL ? "" : bio, r->bio == NULL ? "" : r->bio) != 0) flags |= 1;
    if ((r->mask & 16) && strcmp(bday == NULL ? "" : bday, r->bday == NULL ? "" : r->bday) != 0) flags |= 2;
    snprintf(num, sizeof(num), "%d", flags);
    mc_sb_s(&s, num);
    char *e1 = mc_esc(ob);
    char *e2 = mc_esc(od);
    char *e3 = mc_esc((r->mask & 1) && r->name != NULL ? r->name : "");
    mc_sb_c(&s, '\t');
    if (e1 != NULL) mc_sb_s(&s, e1);
    mc_sb_c(&s, '\t');
    if (e2 != NULL) mc_sb_s(&s, e2);
    mc_sb_c(&s, '\t');
    if (e3 != NULL) mc_sb_s(&s, e3);
    free(e1);
    free(e2);
    free(e3);
    if (!(r->mask & 8) || (flags & 1)) mc_ws_set_str(&r->bio, &r->mask, 8, bio);
    if (!(r->mask & 16) || (flags & 2)) mc_ws_set_str(&r->bday, &r->mask, 16, bday);
    return s.b == NULL ? mc_dup("0\t\t\t") : s.b;
}

static void mc_wl_free_item(mc_wl_item *it) {
    free(it->who);
    free(it->what);
    free(it->oldv);
    free(it->newv);
    free(it->oldp);
    free(it->newp);
    it->who = it->what = it->oldv = it->newv = it->oldp = it->newp = NULL;
}

static void mc_wl_push(jlong t, jlong id, const char *what, const char *who,
                       const char *oldv, const char *newv, const char *oldp,
                       const char *newp, int head) {
    if (!head && W_LL >= 150) return;
    if (head && W_LL >= 150) {
        mc_wl_free_item(&W_LOG[W_LL - 1]);
        W_LL--;
    }
    if (!mc_grow((void **) &W_LOG, &W_LC, W_LL + 1, sizeof(mc_wl_item))) return;
    mc_wl_item it;
    it.t = t;
    it.id = id;
    it.who = mc_dup(who == NULL ? "" : who);
    it.what = mc_dup(what == NULL ? "" : what);
    it.oldv = mc_dup(oldv == NULL ? "" : oldv);
    it.newv = mc_dup(newv == NULL ? "" : newv);
    it.oldp = mc_dup(oldp == NULL ? "" : oldp);
    it.newp = mc_dup(newp == NULL ? "" : newp);
    if (head) {
        if (W_LL > 0) memmove(W_LOG + 1, W_LOG, (size_t) W_LL * sizeof(mc_wl_item));
        W_LOG[0] = it;
    } else {
        W_LOG[W_LL] = it;
    }
    W_LL++;
}

/* entry as escaped TSV: t \t id \t what \t who \t old \t new \t oldp \t newp */
static char *mc_wl_line(int idx) {
    if (idx < 0 || idx >= W_LL) return NULL;
    mc_wl_item *it = &W_LOG[idx];
    mc_sb s;
    memset(&s, 0, sizeof(s));
    char num[32];
    snprintf(num, sizeof(num), "%lld", (long long) it->t);
    mc_sb_s(&s, num);
    mc_sb_c(&s, '\t');
    snprintf(num, sizeof(num), "%lld", (long long) it->id);
    mc_sb_s(&s, num);
    const char *raw[6] = { it->what, it->who, it->oldv, it->newv, it->oldp, it->newp };
    for (int i = 0; i < 6; i++) {
        char *e = mc_esc(raw[i] == NULL ? "" : raw[i]);
        mc_sb_c(&s, '\t');
        if (e != NULL) mc_sb_s(&s, e);
        free(e);
    }
    if (s.b == NULL) return mc_dup("");
    return s.b;
}

static void mc_w_reset_all(void) {
    free(W_ENTS);
    W_ENTS = NULL;
    W_EL = 0;
    W_EC = 0;
    for (int i = 0; i < W_SL; i++) mc_ws_free(&W_SNAP[i]);
    free(W_SNAP);
    W_SNAP = NULL;
    W_SL = 0;
    W_SC = 0;
    for (int i = 0; i < W_LL; i++) mc_wl_free_item(&W_LOG[i]);
    free(W_LOG);
    W_LOG = NULL;
    W_LL = 0;
    W_LC = 0;
}

static int mc_w_load(const unsigned char seed[32], const char *blob) {
    mc_w_reset_all();
    W_LOADED = 1;
    size_t n;
    unsigned char *raw = mc_unseal(seed, 'W', blob, &n);
    if (raw == NULL) return 0;
    char *p = (char *) raw;
    while (*p) {
        char *nl = strchr(p, '\n');
        if (nl != NULL) *nl = 0;
        if (p[0] == 'E' && p[1] == '\t') {
            char *cur = p + 2;
            jlong id = (jlong) strtoll(mc_nextf(&cur), NULL, 10);
            int on = atoi(mc_nextf(&cur));
            if (mc_w_add(id)) W_ENTS[mc_w_find(id)].on = on ? 1 : 0;
        } else if (p[0] == 'S' && p[1] == '\t') {
            char *cur = p + 2;
            jlong id = (jlong) strtoll(mc_nextf(&cur), NULL, 10);
            unsigned mask = (unsigned) atoi(mc_nextf(&cur));
            char *name = mc_nextf(&cur);
            char *user = mc_nextf(&cur);
            jlong photo = (jlong) strtoll(mc_nextf(&cur), NULL, 10);
            char *bio = mc_nextf(&cur);
            char *bday = mc_nextf(&cur);
            mc_unesc(name);
            mc_unesc(user);
            mc_unesc(bio);
            mc_unesc(bday);
            mc_ws *r = mc_w_snap_get(id, 1);
            if (r != NULL) {
                mc_ws_set_str(&r->name, &r->mask, 1, name);
                mc_ws_set_str(&r->user, &r->mask, 2, user);
                r->photo = photo;
                mc_ws_set_str(&r->bio, &r->mask, 8, bio);
                mc_ws_set_str(&r->bday, &r->mask, 16, bday);
                r->mask = mask; /* presence bits are authoritative */
            }
        } else if (p[0] == 'L' && p[1] == '\t') {
            char *cur = p + 2;
            jlong t = (jlong) strtoll(mc_nextf(&cur), NULL, 10);
            jlong id = (jlong) strtoll(mc_nextf(&cur), NULL, 10);
            char *what = mc_nextf(&cur);
            char *who = mc_nextf(&cur);
            char *oldv = mc_nextf(&cur);
            char *newv = mc_nextf(&cur);
            char *oldp = mc_nextf(&cur);
            char *newp = mc_nextf(&cur);
            mc_unesc(what);
            mc_unesc(who);
            mc_unesc(oldv);
            mc_unesc(newv);
            mc_unesc(oldp);
            mc_unesc(newp);
            mc_wl_push(t, id, what, who, oldv, newv, oldp, newp, 0);
        }
        if (nl == NULL) break;
        p = nl + 1;
    }
    memset(raw, 0, n);
    free(raw);
    return 1;
}

static char *mc_w_blob(const unsigned char seed[32]) {
    mc_sb s;
    memset(&s, 0, sizeof(s));
    char num[32];
    for (int i = 0; i < W_EL; i++) {
        mc_sb_s(&s, "E\t");
        snprintf(num, sizeof(num), "%lld", (long long) W_ENTS[i].id);
        mc_sb_s(&s, num);
        mc_sb_c(&s, '\t');
        mc_sb_c(&s, W_ENTS[i].on ? '1' : '0');
        mc_sb_c(&s, '\n');
    }
    for (int i = 0; i < W_SL; i++) {
        mc_ws *r = &W_SNAP[i];
        char *en = mc_esc(r->name == NULL ? "" : r->name);
        char *eu = mc_esc(r->user == NULL ? "" : r->user);
        char *eb = mc_esc(r->bio == NULL ? "" : r->bio);
        char *ed = mc_esc(r->bday == NULL ? "" : r->bday);
        if (en == NULL || eu == NULL || eb == NULL || ed == NULL) {
            free(en); free(eu); free(eb); free(ed);
            continue;
        }
        mc_sb_s(&s, "S\t");
        snprintf(num, sizeof(num), "%lld", (long long) r->id);
        mc_sb_s(&s, num);
        mc_sb_c(&s, '\t');
        snprintf(num, sizeof(num), "%u", r->mask);
        mc_sb_s(&s, num);
        mc_sb_c(&s, '\t');
        mc_sb_s(&s, en);
        mc_sb_c(&s, '\t');
        mc_sb_s(&s, eu);
        mc_sb_c(&s, '\t');
        snprintf(num, sizeof(num), "%lld", (long long) r->photo);
        mc_sb_s(&s, num);
        mc_sb_c(&s, '\t');
        mc_sb_s(&s, eb);
        mc_sb_c(&s, '\t');
        mc_sb_s(&s, ed);
        mc_sb_c(&s, '\n');
        free(en); free(eu); free(eb); free(ed);
    }
    for (int i = 0; i < W_LL; i++) {
        char *line = mc_wl_line(i);
        if (line == NULL) continue;
        mc_sb_c(&s, 'L');
        mc_sb_c(&s, '\t');
        mc_sb_s(&s, line);
        mc_sb_c(&s, '\n');
        free(line);
    }
    if (s.b == NULL) {
        s.b = mc_dup("");
        s.len = 0;
        s.cap = 1;
    }
    char *blob = mc_seal(seed, 'W', (const unsigned char *) (s.b == NULL ? "" : s.b), s.len);
    if (s.b != NULL) {
        memset(s.b, 0, s.cap);
        free(s.b);
    }
    return blob;
}

/* instant-alert switch gate + per-person 5 s throttle; the mark lands on
 * pass, so a spam burst notifies once (the LOG is never throttled). */
static int mc_w_msg_pass(jlong id, jlong nowMs, int enabled) {
    if (!enabled) return 0;
    jlong last = mc_mark_get(W_MARK, 256, id);
    if (last != 0 && nowMs - last < 5000) return 0;
    mc_mark_put(W_MARK, 256, id, nowMs);
    return 1;
}

/* ---- Activity Stats: decision layer (bounds, dry policy, hourly) ---------- */

static int AS_H[24];
static int AS_H_HAS;

typedef struct { jlong id, sec; int dry; } mc_as_d;

static mc_as_d *AS_D;
static int AS_DL, AS_DC;

static void mc_as_reset(void) {
    memset(AS_H, 0, sizeof(AS_H));
    AS_H_HAS = 0;
    free(AS_D);
    AS_D = NULL;
    AS_DL = 0;
    AS_DC = 0;
}

/* Java: seen.add(uid) consumes the dedup slot BEFORE the out check - the
 * first row for a dialog wins, even when it is an outgoing one. */
static void mc_as_dry_feed(jlong uid, jlong sec, jint out) {
    for (int i = 0; i < AS_DL; i++) {
        if (AS_D[i].id == uid) return;
    }
    if (!mc_grow((void **) &AS_D, &AS_DC, AS_DL + 1, sizeof(mc_as_d))) return;
    AS_D[AS_DL].id = uid;
    AS_D[AS_DL].sec = sec;
    AS_D[AS_DL].dry = out == 0 ? 1 : 0;
    AS_DL++;
}

static int mc_as_dry_cmp(const void *a, const void *b) {
    const mc_as_d *x = (const mc_as_d *) a, *y = (const mc_as_d *) b;
    if (x->dry != y->dry) return y->dry - x->dry;         /* dry rows first    */
    if (x->sec > y->sec) return -1;                        /* freshest first    */
    if (x->sec < y->sec) return 1;
    return 0;
}

static void mc_as_dry_sort(void) {
    qsort(AS_D, (size_t) AS_DL, sizeof(mc_as_d), mc_as_dry_cmp);
}

/* "midnightLocal\tweek(7d)\tmonth(30d)" in epoch seconds; mktime handles
 * the local zone exactly like the old Calendar code. */
static char *mc_as_bounds(jlong nowMs) {
    time_t t = (time_t) (nowMs / 1000);
    struct tm tmv;
    memset(&tmv, 0, sizeof(tmv));
    if (localtime_r(&t, &tmv) == NULL) return NULL;
    tmv.tm_hour = 0;
    tmv.tm_min = 0;
    tmv.tm_sec = 0;
    time_t mid = mktime(&tmv);
    jlong nowSec = nowMs / 1000;
    char buf[96];
    snprintf(buf, sizeof(buf), "%lld\t%lld\t%lld", (long long) mid,
             (long long) (nowSec - 7LL * 86400LL), (long long) (nowSec - 30LL * 86400LL));
    return mc_dup(buf);
}

/* ================= JNI surface (thin locked wrappers) ====================== */

static jstring mc_js(JNIEnv *env, char *malloced) {
    if (malloced == NULL) return NULL;
    jstring r = mc_jstr(env, malloced);
    memset(malloced, 0, strlen(malloced));
    free(malloced);
    return r;
}

JNIEXPORT jint JNICALL MC_CLASS(nSfCount)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    return 5;
}

JNIEXPORT jstring JNICALL MC_CLASS(nSfTitleKeyAt)(JNIEnv *env, jclass c, jint idx) {
    (void) c;
    char buf[64];
    if (idx < 0 || idx > 4) return NULL;
    mc_unemo(buf, MC_SF[idx].tk);
    return mc_js(env, mc_dup(buf));
}

JNIEXPORT jstring JNICALL MC_CLASS(nSfRuleKeyAt)(JNIEnv *env, jclass c, jint idx) {
    (void) c;
    char buf[64];
    if (idx < 0 || idx > 4) return NULL;
    mc_unemo(buf, MC_SF[idx].rk);
    return mc_js(env, mc_dup(buf));
}

JNIEXPORT jstring JNICALL MC_CLASS(nSfEmoticonAt)(JNIEnv *env, jclass c, jint idx) {
    (void) c;
    char buf[16];
    if (idx < 0 || idx > 4) return NULL;
    mc_unemo(buf, MC_SF[idx].emo);
    return mc_js(env, mc_dup(buf));
}

JNIEXPORT jint JNICALL MC_CLASS(nSfFlagsAt)(JNIEnv *env, jclass c, jint idx) {
    (void) env; (void) c;
    if (idx < 0 || idx > 4) return 0;
    return (jint) MC_SF[idx].flags;
}

JNIEXPORT jint JNICALL MC_CLASS(nSfColorAt)(JNIEnv *env, jclass c, jint idx) {
    (void) env; (void) c;
    if (idx < 0 || idx > 4) return 0;
    return (jint) MC_SF[idx].color;
}

JNIEXPORT jboolean JNICALL MC_CLASS(nSfTitleEq)(JNIEnv *env, jclass c, jstring a, jstring b) {
    (void) c;
    char *x = mc_utf(env, a);
    char *y = mc_utf(env, b);
    jboolean r = (x != NULL && y != NULL && mc_sf_title_eq(x, y)) ? JNI_TRUE : JNI_FALSE;
    free(x);
    free(y);
    return r;
}

/* ---- delete hunter ---- */

JNIEXPORT jint JNICALL MC_CLASS(nDhLoad)(JNIEnv *env, jclass c, jstring blob) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    unsigned char seed[32];
    int ok = mc_seed(env, seed);
    char *b = ok ? mc_utf(env, blob) : NULL;
    jint r = ok ? (jint) mc_dh_load(seed, b) : 0;
    free(b);
    memset(seed, 0, 32);
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jstring JNICALL MC_CLASS(nDhBlob)(JNIEnv *env, jclass c) {
    (void) c;
    if (mc_ad_blocked()) return NULL; /* v186: no disk writes while traced */
    pthread_mutex_lock(&mc_mu);
    unsigned char seed[32];
    if (!mc_seed(env, seed)) {
        pthread_mutex_unlock(&mc_mu);
        return NULL;
    }
    char *b = mc_dh_blob(seed);
    memset(seed, 0, 32);
    pthread_mutex_unlock(&mc_mu);
    return mc_js(env, b);
}

/* head=0 import keeps legacy order; head=1 is the runtime newest-first put */
JNIEXPORT void JNICALL MC_CLASS(nDhAdd)(JNIEnv *env, jclass c, jlong t, jlong id,
                                        jstring kind, jstring who, jstring oldv,
                                        jstring newv, jint head) {
    (void) c;
    char *k = mc_utf(env, kind);
    char *w = mc_utf(env, who);
    char *o = mc_utf(env, oldv);
    char *nw = mc_utf(env, newv);
    pthread_mutex_lock(&mc_mu);
    mc_dh_push(t, id, k, w, o, nw, head);
    pthread_mutex_unlock(&mc_mu);
    free(k); free(w); free(o); free(nw);
}

JNIEXPORT jint JNICALL MC_CLASS(nDhCount)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jint r = DH_LEN;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jstring JNICALL MC_CLASS(nDhAt)(JNIEnv *env, jclass c, jint idx) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    char *line = mc_dh_line(idx);
    pthread_mutex_unlock(&mc_mu);
    return mc_js(env, line);
}

JNIEXPORT void JNICALL MC_CLASS(nDhClear)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    mc_dh_reset();
    pthread_mutex_unlock(&mc_mu);
}

JNIEXPORT jstring JNICALL MC_CLASS(nDhKey)(JNIEnv *env, jclass c, jlong t, jlong id,
                                           jstring kind, jstring oldv) {
    (void) c;
    char *k = mc_utf(env, kind);
    char *o = mc_utf(env, oldv);
    pthread_mutex_lock(&mc_mu);
    char *key = mc_dh_key(t, id, k, o);
    pthread_mutex_unlock(&mc_mu);
    free(k);
    free(o);
    return mc_js(env, key);
}

JNIEXPORT jint JNICALL MC_CLASS(nDhRemove)(JNIEnv *env, jclass c, jstring keys) {
    (void) c;
    char *ks = mc_utf(env, keys);
    pthread_mutex_lock(&mc_mu);
    jint r = (jint) mc_dh_remove(ks);
    pthread_mutex_unlock(&mc_mu);
    free(ks);
    return r;
}

/* gate 2 of the Java captureCheck (master switch stays a Java config read):
 * real content, not outgoing, sender known and not us. */
JNIEXPORT jboolean JNICALL MC_CLASS(nDhCapture)(JNIEnv *env, jclass c, jint out,
                                                jint hasAction, jlong fromUid, jlong selfId) {
    (void) env; (void) c;
    if (out != 0 || hasAction != 0) return JNI_FALSE;
    if (fromUid == 0 || fromUid == selfId) return JNI_FALSE;
    return JNI_TRUE;
}

/* ---- watch ---- */

JNIEXPORT jint JNICALL MC_CLASS(nWLoad)(JNIEnv *env, jclass c, jstring blob) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    unsigned char seed[32];
    int ok = mc_seed(env, seed);
    char *b = ok ? mc_utf(env, blob) : NULL;
    jint r = ok ? (jint) mc_w_load(seed, b) : 0;
    free(b);
    memset(seed, 0, 32);
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jstring JNICALL MC_CLASS(nWBlob)(JNIEnv *env, jclass c) {
    (void) c;
    if (mc_ad_blocked()) return NULL; /* v186: no disk writes while traced */
    pthread_mutex_lock(&mc_mu);
    unsigned char seed[32];
    if (!mc_seed(env, seed)) {
        pthread_mutex_unlock(&mc_mu);
        return NULL;
    }
    char *b = mc_w_blob(seed);
    memset(seed, 0, 32);
    pthread_mutex_unlock(&mc_mu);
    return mc_js(env, b);
}

JNIEXPORT jboolean JNICALL MC_CLASS(nWAdd)(JNIEnv *env, jclass c, jlong id) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jboolean r = mc_w_add(id) ? JNI_TRUE : JNI_FALSE;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT void JNICALL MC_CLASS(nWRemove)(JNIEnv *env, jclass c, jlong id) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    mc_w_remove(id);
    pthread_mutex_unlock(&mc_mu);
}

JNIEXPORT void JNICALL MC_CLASS(nWSetOn)(JNIEnv *env, jclass c, jlong id, jint on) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    int i = mc_w_find(id);
    if (i >= 0) W_ENTS[i].on = on != 0 ? 1 : 0;
    pthread_mutex_unlock(&mc_mu);
}

JNIEXPORT jboolean JNICALL MC_CLASS(nWIsWatched)(JNIEnv *env, jclass c, jlong id) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jboolean r = mc_w_find(id) >= 0 ? JNI_TRUE : JNI_FALSE;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jboolean JNICALL MC_CLASS(nWIsOn)(JNIEnv *env, jclass c, jlong id) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    int i = mc_w_find(id);
    jboolean r = (i >= 0 && W_ENTS[i].on) ? JNI_TRUE : JNI_FALSE;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jint JNICALL MC_CLASS(nWCount)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jint r = W_EL;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jlong JNICALL MC_CLASS(nWEntryIdAt)(JNIEnv *env, jclass c, jint idx) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jlong r = (idx < 0 || idx >= W_EL) ? 0 : W_ENTS[idx].id;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jboolean JNICALL MC_CLASS(nWEntryOnAt)(JNIEnv *env, jclass c, jint idx) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jboolean r = (idx < 0 || idx >= W_EL) ? JNI_FALSE : (W_ENTS[idx].on ? JNI_TRUE : JNI_FALSE);
    pthread_mutex_unlock(&mc_mu);
    return r;
}

/* legacy snapshot import: mask presence bits authoritative (has() parity) */
JNIEXPORT void JNICALL MC_CLASS(nWSnapImport)(JNIEnv *env, jclass c, jlong id, jint mask,
                                              jstring name, jstring user, jlong photo,
                                              jstring bio, jstring bday) {
    (void) c;
    char *n = mc_utf(env, name);
    char *u = mc_utf(env, user);
    char *b = mc_utf(env, bio);
    char *d = mc_utf(env, bday);
    pthread_mutex_lock(&mc_mu);
    mc_ws *r = mc_w_snap_get(id, 1);
    if (r != NULL) {
        mc_ws_set_str(&r->name, &r->mask, 1, n);
        mc_ws_set_str(&r->user, &r->mask, 2, u);
        r->photo = photo;
        mc_ws_set_str(&r->bio, &r->mask, 8, b);
        mc_ws_set_str(&r->bday, &r->mask, 16, d);
        r->mask = (unsigned) mask;
    }
    pthread_mutex_unlock(&mc_mu);
    free(n); free(u); free(b); free(d);
}

/* pack: flags \t oldName \t oldUser \t oldPhotoId (record already merged) */
JNIEXPORT jstring JNICALL MC_CLASS(nWDiffUser)(JNIEnv *env, jclass c, jlong id,
                                               jstring name, jstring user, jlong photo) {
    (void) c;
    char *n = mc_utf(env, name);
    char *u = mc_utf(env, user);
    pthread_mutex_lock(&mc_mu);
    char *pack = mc_w_diff_user(id, n, u, photo);
    pthread_mutex_unlock(&mc_mu);
    free(n);
    free(u);
    return mc_js(env, pack);
}

/* pack: flags \t oldBio \t oldBday \t whoName (record already merged) */
JNIEXPORT jstring JNICALL MC_CLASS(nWDiffFull)(JNIEnv *env, jclass c, jlong id,
                                               jstring bio, jstring bday) {
    (void) c;
    char *b = mc_utf(env, bio);
    char *d = mc_utf(env, bday);
    pthread_mutex_lock(&mc_mu);
    char *pack = mc_w_diff_full(id, b, d);
    pthread_mutex_unlock(&mc_mu);
    free(b);
    free(d);
    return mc_js(env, pack);
}

/* head=1 runtime put; head=0 legacy import (order kept, cap enforced) */
JNIEXPORT void JNICALL MC_CLASS(nWLogAdd)(JNIEnv *env, jclass c, jlong t, jlong id,
                                          jstring what, jstring who, jstring oldv,
                                          jstring newv, jstring oldp, jstring newp, jint head) {
    (void) c;
    char *wt = mc_utf(env, what);
    char *wo = mc_utf(env, who);
    char *ov = mc_utf(env, oldv);
    char *nv = mc_utf(env, newv);
    char *op = mc_utf(env, oldp);
    char *np = mc_utf(env, newp);
    pthread_mutex_lock(&mc_mu);
    mc_wl_push(t, id, wt, wo, ov, nv, op, np, head);
    pthread_mutex_unlock(&mc_mu);
    free(wt); free(wo); free(ov); free(nv); free(op); free(np);
}

JNIEXPORT jint JNICALL MC_CLASS(nWLogCount)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jint r = W_LL;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jstring JNICALL MC_CLASS(nWLogAt)(JNIEnv *env, jclass c, jint idx) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    char *line = mc_wl_line(idx);
    pthread_mutex_unlock(&mc_mu);
    return mc_js(env, line);
}

JNIEXPORT void JNICALL MC_CLASS(nWLogClear)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    for (int i = 0; i < W_LL; i++) mc_wl_free_item(&W_LOG[i]);
    free(W_LOG);
    W_LOG = NULL;
    W_LL = 0;
    W_LC = 0;
    pthread_mutex_unlock(&mc_mu);
}

JNIEXPORT jboolean JNICALL MC_CLASS(nWMsgNotifyPass)(JNIEnv *env, jclass c, jlong id,
                                                     jlong nowMs, jint enabled) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jboolean r = mc_w_msg_pass(id, nowMs, enabled) ? JNI_TRUE : JNI_FALSE;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

/* ---- activity stats decisions ---- */

JNIEXPORT void JNICALL MC_CLASS(nAsReset)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    mc_as_reset();
    pthread_mutex_unlock(&mc_mu);
}

/* "midnightLocal\tweek\tmonth" epoch seconds (device-local midnight) */
JNIEXPORT jstring JNICALL MC_CLASS(nAsBounds)(JNIEnv *env, jclass c, jlong nowMs) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    char *b = mc_as_bounds(nowMs);
    pthread_mutex_unlock(&mc_mu);
    return mc_js(env, b);
}

JNIEXPORT void JNICALL MC_CLASS(nAsSetHour)(JNIEnv *env, jclass c, jint h, jint count) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    if (h >= 0 && h < 24) {
        AS_H[h] = (int) count;
        if (count > 0) AS_H_HAS = 1;
    }
    pthread_mutex_unlock(&mc_mu);
}

JNIEXPORT jint JNICALL MC_CLASS(nAsHourAt)(JNIEnv *env, jclass c, jint h) {
    (void) env; (void) c;
    if (h < 0 || h > 23) return 0;
    pthread_mutex_lock(&mc_mu);
    jint r = AS_H[h];
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jboolean JNICALL MC_CLASS(nAsHasHourly)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jboolean r = AS_H_HAS ? JNI_TRUE : JNI_FALSE;
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT void JNICALL MC_CLASS(nAsDryFeed)(JNIEnv *env, jclass c, jlong uid,
                                            jlong lastSec, jint out) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    mc_as_dry_feed(uid, lastSec, out);
    pthread_mutex_unlock(&mc_mu);
}

/* sorts dry-first/freshest-first, then reports the dry (owed-reply) count */
JNIEXPORT jint JNICALL MC_CLASS(nAsDryCount)(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    mc_as_dry_sort();
    int r = 0;
    for (int i = 0; i < AS_DL; i++) {
        if (AS_D[i].dry) r++;
    }
    pthread_mutex_unlock(&mc_mu);
    return r;
}

/* top rows only (max 5): the freshest waits. i beyond the dry run = 0/0 */
JNIEXPORT jlong JNICALL MC_CLASS(nAsDryTopIdAt)(JNIEnv *env, jclass c, jint idx) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jlong r = 0;
    int drySeen = 0, pos = 0;
    for (int i = 0; i < AS_DL && drySeen < 5; i++) {
        if (!AS_D[i].dry) break; /* sorted: dry rows lead */
        if (pos++ == idx) { r = AS_D[i].id; }
        drySeen++;
    }
    pthread_mutex_unlock(&mc_mu);
    return r;
}

JNIEXPORT jlong JNICALL MC_CLASS(nAsDryTopSecAt)(JNIEnv *env, jclass c, jint idx) {
    (void) env; (void) c;
    pthread_mutex_lock(&mc_mu);
    jlong r = 0;
    int drySeen = 0, pos = 0;
    for (int i = 0; i < AS_DL && drySeen < 5; i++) {
        if (!AS_D[i].dry) break;
        if (pos++ == idx) { r = AS_D[i].sec; }
        drySeen++;
    }
    pthread_mutex_unlock(&mc_mu);
    return r;
}

/*
 * ============================================================================
 * MeeroX v186 (batch 2D) - deep hardening: the MeeroStrings vault sealed at
 * build time (served whole, once per process), and the passive anti-debug
 * shield. The shield's law (his v166 rule, unchanged): NEVER kills, never
 * blocks, never pops anything; while a tracer/frida/xposed kit is attached,
 * the sealed stores simply stop being WRITTEN BACK - reads and the whole
 * user experience keep working, only forensic capture is starved. Cached
 * 60 s so the probe cost never lands on the hot path.
 * ============================================================================
 */

static int mc_ad_cached = -1;
static long long mc_ad_at;

/* reads the whole file lowercase; returns 1 when any needle shows up */
static int mc_ad_scan(const char *path, const char *const *needles, int nn) {
    FILE *f = fopen(path, "r");
    if (f == NULL) return 0;
    char line[512];
    int hit = 0;
    while (!hit && fgets(line, sizeof(line), f) != NULL) {
        for (char *q = line; *q; q++) {
            if (*q >= 'A' && *q <= 'Z') *q = (char) (*q | 0x20);
        }
        for (int i = 0; i < nn; i++) {
            if (strstr(line, needles[i]) != NULL) { hit = 1; break; }
        }
    }
    fclose(f);
    return hit;
}

static int mc_ad_probe(void) {
    /* 1) an actual tracer (ptrace/jdwp/frida attach) */
    FILE *f = fopen("/proc/self/status", "r");
    if (f != NULL) {
        char line[256];
        int traced = 0;
        while (fgets(line, sizeof(line), f) != NULL) {
            if (strncmp(line, "TracerPid:", 10) == 0) {
                traced = atoi(line + 10) != 0;
                break;
            }
        }
        fclose(f);
        if (traced) return 1;
    }
    /* 2) instrumentation frameworks mapped into this process */
    static const char *const MAP_NEEDLES[] = {
        "frida", "gum-js", "gadget", "xposed", "substrate"
    };
    if (mc_ad_scan("/proc/self/maps", MAP_NEEDLES, 5)) return 1;
    /* 3) the classic instrumentation control ports listening locally
     *    (hex column of /proc/net/tcp*: 69A2/69A3 = 27042/3, 5D8A = 23946) */
    static const char *const TCP_NEEDLES[] = { ":69a2 ", ":69a3 ", ":5d8a " };
    if (mc_ad_scan("/proc/net/tcp", TCP_NEEDLES, 3)) return 1;
    if (mc_ad_scan("/proc/net/tcp6", TCP_NEEDLES, 3)) return 1;
    return 0;
}

static int mc_ad_blocked(void) {
    struct timespec ts;
    long long now = 0;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) == 0) now = (long long) ts.tv_sec;
    if (mc_ad_cached < 0 || now - mc_ad_at >= 60) {
        mc_ad_cached = mc_ad_probe();
        mc_ad_at = now;
    }
    return mc_ad_cached;
}

/* ---- sealed MeeroStrings vault (dom 'S'): raw mac|ct form, one shot ------ */

#include "meero_strtab.h"

static char *mc_strtab_tsv(const unsigned char seed[32], size_t *n_out) {
    if (MC_STRTAB_LEN < 32) return NULL;
    size_t el = (size_t) MC_STRTAB_LEN - 32;
    const unsigned char *enc = MC_STRTAB + 32;
    unsigned char *mm = malloc(1 + el);
    if (mm == NULL) return NULL;
    mm[0] = (unsigned char) 'S';
    memcpy(mm + 1, enc, el);
    unsigned char mac[32];
    mc_hmac32(seed, mm, 1 + el, mac);
    memset(mm, 0, 1 + el);
    free(mm);
    unsigned char diff = 0;
    for (int i = 0; i < 32; i++) diff |= (unsigned char) (mac[i] ^ MC_STRTAB[i]);
    memset(mac, 0, 32);
    if (diff != 0) return NULL;
    unsigned char *raw = malloc(el + 1);
    if (raw == NULL) return NULL;
    memcpy(raw, enc, el);
    mc_xor_stream(seed, 'S', raw, el);
    raw[el] = 0;
    *n_out = el;
    return (char *) raw;
}

JNIEXPORT jstring JNICALL MC_CLASS(nStrTsv)(JNIEnv *env, jclass c) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    unsigned char seed[32];
    size_t n = 0;
    char *tsv = NULL;
    if (mc_seed(env, seed)) {
        tsv = mc_strtab_tsv(seed, &n);
    }
    memset(seed, 0, 32);
    pthread_mutex_unlock(&mc_mu);
    jstring r = tsv == NULL ? NULL : mc_jstr(env, tsv);
    if (tsv != NULL) {
        memset(tsv, 0, n);
        free(tsv);
    }
    return r;
}

/* ============================================================================
 * MeeroX v187 - batch 3A: the glass-family design brain.
 *
 * The MeeroX glass look used to live as readable literals in DEX: the
 * fixed Glass-Night palette (18 colors x day/night), the mock switch
 * geometry ratios (48x28 pill, 3dp inset, 22dp knob, 2/11 press stretch),
 * the glow/shadow paddings and the card/section constants. All of that is
 * now a sealed table (dom 'G', same vault seed) decoded ONCE per process
 * into static memory; pure math sits in the JNI-free header meero_glass.h
 * so the desktop gcc harness exercises the exact device logic. Java keeps
 * byte-identical legacy fallbacks (R8-scrambled) for dev parity, and the
 * Canvas/Paint brush itself stays Java - Android renders there; only the
 * recipe is buried. Tampered table -> mg_init refuses -> Java falls back
 * to the legacy path, never a crash (the v166 law).
 * ============================================================================ */

#include "meero_glasstab.h"
#include "meero_glass.h"

/* generic raw (mac|ct, no base64) unseal - the strtab kept its own copy so
 * the 2D code path is untouched; this one is shared by design tables. */
static unsigned char *mc_raw_unseal(const unsigned char seed[32], char dom,
                                    const unsigned char *tab, size_t tabLen,
                                    size_t *n_out) {
    if (tabLen < 32) return NULL;
    size_t el = tabLen - 32;
    const unsigned char *enc = tab + 32;
    unsigned char *mm = malloc(1 + el);
    if (mm == NULL) return NULL;
    mm[0] = (unsigned char) dom;
    memcpy(mm + 1, enc, el);
    unsigned char mac[32];
    mc_hmac32(seed, mm, 1 + el, mac);
    memset(mm, 0, 1 + el);
    free(mm);
    unsigned char diff = 0;
    for (int i = 0; i < 32; i++) diff |= (unsigned char) (mac[i] ^ tab[i]);
    memset(mac, 0, 32);
    if (diff != 0) return NULL;
    unsigned char *raw = malloc(el + 1);
    if (raw == NULL) return NULL;
    memcpy(raw, enc, el);
    mc_xor_stream(seed, dom, raw, el);
    raw[el] = 0;
    *n_out = el;
    return raw;
}

static int mg_ensure(JNIEnv *env) {
    if (mg_ready()) return 1;
    unsigned char seed[32];
    int ok = 0;
    if (mc_seed(env, seed)) {
        size_t n = 0;
        unsigned char *raw = mc_raw_unseal(seed, 'G', MC_GLASSTAB,
                                           (size_t) MC_GLASSTAB_LEN, &n);
        if (raw != NULL) {
            ok = mg_init(raw, n);
            memset(raw, 0, n);
            free(raw);
        }
    }
    memset(seed, 0, 32);
    return ok;
}

static jfloatArray mc_farr(JNIEnv *env, const float *v, int n) {
    jfloatArray a = (*env)->NewFloatArray(env, n);
    if (a != NULL) (*env)->SetFloatArrayRegion(env, a, 0, n, v);
    return a;
}

/* 1 when the sealed glass table decoded fine (dev-parity probe) */
JNIEXPORT jboolean JNICALL MC_CLASS(nGlassReady)(JNIEnv *env, jclass c) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    int ok = mg_ensure(env);
    pthread_mutex_unlock(&mc_mu);
    return ok ? JNI_TRUE : JNI_FALSE;
}

/* fixed Glass-Night palette lookup: -1 => caller uses its legacy literal */
JNIEXPORT jint JNICALL MC_CLASS(nGtColor)(JNIEnv *env, jclass c,
                                          jint id, jboolean night) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    int32_t v = mg_ensure(env) ? mg_color((int) id, night == JNI_TRUE) : -1;
    pthread_mutex_unlock(&mc_mu);
    return (jint) v;
}

/* the mock switch's own constants (aspect, inset/thumb ratios, paddings,
 * bezier points, travel/press ms) */
JNIEXPORT jfloatArray JNICALL MC_CLASS(nGlassSwitchParams)(JNIEnv *env, jclass c) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    jfloatArray r = NULL;
    if (mg_ensure(env)) {
        float v[16];
        mg_switch_params(v);
        r = mc_farr(env, v, 16);
    }
    pthread_mutex_unlock(&mc_mu);
    return r;
}

/* card/section/header/chip/glow constants shared by both screen bases */
JNIEXPORT jfloatArray JNICALL MC_CLASS(nGlassUiConsts)(JNIEnv *env, jclass c) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    jfloatArray r = NULL;
    if (mg_ensure(env)) {
        float v[32];
        mg_ui_consts(v);
        r = mc_farr(env, v, 32);
    }
    pthread_mutex_unlock(&mc_mu);
    return r;
}

/* full mock-switch geometry for one frame (24 floats, see meero_glass.h) */
JNIEXPORT jfloatArray JNICALL MC_CLASS(nGlassSwitchGeom)(JNIEnv *env, jclass c,
        jfloat density, jfloat w, jfloat h,
        jfloat progress, jfloat press, jboolean rtl) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    jfloatArray r = NULL;
    if (mg_ensure(env)) {
        float out[MG_GEOM_N];
        mg_geom(density, w, h, progress, press, rtl == JNI_TRUE, out);
        r = mc_farr(env, out, MG_GEOM_N);
    }
    pthread_mutex_unlock(&mc_mu);
    return r;
}

/* glass hairline: luma rule + reference alpha, 0x7FFFFFFF => legacy path */
JNIEXPORT jint JNICALL MC_CLASS(nGlassBorder)(JNIEnv *env, jclass c, jint base) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    jint v = mg_ensure(env) ? (jint) mg_border(base)
                            : (jint) 0x7FFFFFFF;
    pthread_mutex_unlock(&mc_mu);
    return v;
}

/* card edge decision shared with the legacy-base screens */
JNIEXPORT jint JNICALL MC_CLASS(nGlassCardPos)(JNIEnv *env, jclass c,
                                               jboolean first, jboolean last) {
    (void) c;
    pthread_mutex_lock(&mc_mu);
    int v = mg_ensure(env) ? mg_cardpos(first == JNI_TRUE, last == JNI_TRUE)
                           : -1;
    pthread_mutex_unlock(&mc_mu);
    return (jint) v;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) vm; (void) reserved;
    return JNI_VERSION_1_6;
}
