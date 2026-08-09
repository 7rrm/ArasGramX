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

#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

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

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) vm; (void) reserved;
    return JNI_VERSION_1_6;
}
