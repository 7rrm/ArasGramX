/* MeeroX v189 (batch 3C) - motion/engine family design BRAIN, pure C, no JNI:
 * theme-mixer recipe (accents, backgrounds, in-bubble rules, blend/alpha
 * constants), bundled-font table, janitor policy, haptics weight map,
 * typing-status ratios, connecting-ring geometry, smooth-pass warm-up
 * policy and the iOS intro metrics. Payload arrives already unsealed
 * (dom 'C'). Java keeps byte-identical legacy fallbacks (R8-scrambled). */
#ifndef MEERO_MOTION_H
#define MEERO_MOTION_H

#include <stdint.h>
#include <string.h>

#define MM_FN 52
#define MM_IN 44
#define MM_SN 31
#define MM_SBUF 1024

static float mm_flt[MM_FN];
static uint32_t mm_int[MM_IN];
static char mm_str[MM_SBUF];
static uint16_t mm_soff[MM_SN];
static uint16_t mm_slen[MM_SN];
static int mm_ok = 0;

static uint32_t mm_u32(const unsigned char *p) {
    return (uint32_t) p[0] | ((uint32_t) p[1] << 8)
         | ((uint32_t) p[2] << 16) | ((uint32_t) p[3] << 24);
}

static int mm_init(const unsigned char *pay, size_t n) {
    size_t need = 4 + 4 + (size_t) MM_FN * 4 + 4 + (size_t) MM_IN * 4 + 4;
    if (pay == NULL || n < need) return 0;
    if (pay[0] != 'M' || pay[1] != 'M' || pay[2] != 'T' || pay[3] != '1') return 0;
    if (mm_u32(pay + 4) != MM_FN) return 0;
    const unsigned char *p = pay + 8;
    for (int i = 0; i < MM_FN; i++) {
        uint32_t b = mm_u32(p + i * 4);
        memcpy(&mm_flt[i], &b, 4);
    }
    p += (size_t) MM_FN * 4;
    if (mm_u32(p) != MM_IN) return 0;
    p += 4;
    for (int i = 0; i < MM_IN; i++) mm_int[i] = mm_u32(p + i * 4);
    p += (size_t) MM_IN * 4;
    if (mm_u32(p) != MM_SN) return 0;
    p += 4;
    size_t used = 0;
    for (int i = 0; i < MM_SN; i++) {
        if ((size_t) (p + 2 - pay) > n) return 0;
        uint16_t ln = (uint16_t) (p[0] | ((uint16_t) p[1] << 8));
        p += 2;
        if ((size_t) (p + ln - pay) > n) return 0;
        if (used + ln + 1 > MM_SBUF) return 0;
        mm_soff[i] = (uint16_t) used;
        mm_slen[i] = ln;
        memcpy(mm_str + used, p, ln);
        used += ln;
        mm_str[used++] = 0;
        p += ln;
    }
    if ((size_t) (p - pay) != n) return 0;
    mm_ok = 1;
    return 1;
}

static int mm_ready(void) { return mm_ok; }

static float mm_f(int i) {
    if (!mm_ok || i < 0 || i >= MM_FN) return -1.0f;
    return mm_flt[i];
}

static uint32_t mm_i(int i) {
    if (!mm_ok || i < 0 || i >= MM_IN) return 0xFFFFFFFFu;
    return mm_int[i];
}

static const char *mm_s(int i) {
    if (!mm_ok || i < 0 || i >= MM_SN) return NULL;
    return mm_str + mm_soff[i];
}

#endif /* MEERO_MOTION_H */
