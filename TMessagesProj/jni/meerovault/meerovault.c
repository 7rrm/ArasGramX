/*
 * MeeroX v167 - "MeeroVault" native key carrier (kept PLAIN on purpose,
 * exactly like the tiny shell of the packer his WiFi app used).
 *
 * This library holds nothing but a shuffled 32-byte seed. The seed alone
 * cannot decrypt the vault: the runtime key is
 *     SHA256(seed | release-signing fingerprint) XOR keyMask-from-blob
 * so a re-signed clone derives a wrong key and the AES-GCM tag check kills
 * it. The 24 MB native heart (libtmessages.49.so) ships only encrypted at
 * assets/meero_vault/core.enc; decryption happens in Java (javax.crypto,
 * AES-GCM - no hand-written crypto in C by design, no test device).
 *
 * Layer-3 passive hardening lives here too: at JNI_OnLoad we switch core
 * dumps off (prctl PR_SET_DUMPABLE 0) and read TracerPid - LOG ONLY.
 * Per his standing order this must NEVER kill a running user: false
 * positives on exotic ROMs must not punish his audience.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <sys/prctl.h>
#include <android/log.h>

#define MV_TAG "MeeroVault"

/* Shuffled seed table: seed[(i*13+7)%32] = TABLE[i] ^ ((0x5A + i*7) & 0xFF).
 * Raw seed is duplicated in tools/MeeroVaultPacker.java - keep in sync. */
static const unsigned char MV_TABLE[32] = {
    0xFC, 0xCC, 0x97, 0x5A, 0x82, 0x8B, 0xEC, 0x24,
    0x90, 0x82, 0x73, 0x6E, 0x99, 0x5A, 0xDA, 0xAA,
    0x76, 0xD9, 0x18, 0x3A, 0x97, 0x0A, 0x8C, 0x53,
    0xEE, 0x2B, 0x72, 0xB0, 0x5F, 0x3F, 0x99, 0x7C
};

static void mv_unshuffle(unsigned char out[32]) {
    for (int i = 0; i < 32; i++) {
        out[(i * 13 + 7) % 32] = (unsigned char) (MV_TABLE[i] ^ ((0x5A + i * 7) & 0xFF));
    }
}

static void mv_passive_hardening(void) {
    /* Layer 3 (passive): make ptrace-attached dumps useless, then only
     * REPORT a tracer - never exit/kill (his rule: users only enjoy). */
    prctl(PR_SET_DUMPABLE, 0, 0, 0, 0);

    FILE *f = fopen("/proc/self/status", "r");
    if (f != NULL) {
        char line[256];
        while (fgets(line, sizeof(line), f) != NULL) {
            if (strncmp(line, "TracerPid:", 10) == 0) {
                int pid = atoi(line + 10);
                if (pid != 0) {
                    __android_log_print(ANDROID_LOG_WARN, MV_TAG,
                            "tracer attached (pid=%d) - noted, user untouched", pid);
                }
                break;
            }
        }
        fclose(f);
    }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    mv_passive_hardening();
    return JNI_VERSION_1_6;
}

/* tw.nekomimi.nekogram.MeeroVaultSeed.fingerprintSeedNative() */
JNIEXPORT jbyteArray JNICALL
Java_tw_nekomimi_nekogram_MeeroVaultSeed_fingerprintSeedNative(JNIEnv *env, jclass clazz) {
    unsigned char seed[32];
    mv_unshuffle(seed);
    jbyteArray arr = (*env)->NewByteArray(env, 32);
    if (arr != NULL) {
        (*env)->SetByteArrayRegion(env, arr, 0, 32, (const jbyte *) seed);
    }
    memset(seed, 0, sizeof(seed));
    return arr;
}

/* tw.nekomimi.nekogram.MeeroDexApp.seedNative() - same seed, same decode,
 * served to the v168 DEX-vault stub (kept static, jclass signature). */
JNIEXPORT jbyteArray JNICALL
Java_tw_nekomimi_nekogram_MeeroDexApp_seedNative(JNIEnv *env, jclass clazz) {
    unsigned char seed[32];
    mv_unshuffle(seed);
    jbyteArray arr = (*env)->NewByteArray(env, 32);
    if (arr != NULL) {
        (*env)->SetByteArrayRegion(env, arr, 0, 32, (const jbyte *) seed);
    }
    memset(seed, 0, sizeof(seed));
    return arr;
}
