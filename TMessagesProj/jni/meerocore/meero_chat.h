/* MeeroX v188 (batch 3B) - chat-surface family design BRAIN, pure C, no JNI:
 * bubble radius table + tailless mask + the four tail recipes (official iOS
 * crescent, classic wedge, whatsapp nub, stock nub), preview geometry
 * constants, MeeroShadow tiers, MeeroCards constants + lift curve + radii
 * rules + hairline math. Payload arrives already unsealed (dom 'B').
 * Java keeps byte-identical legacy fallbacks (R8-scrambled). */
#ifndef MEERO_CHAT_H
#define MEERO_CHAT_H

#include <math.h>
#include <stdint.h>
#include <string.h>

#define MB_NF 62

static float mb_flt[MB_NF];
static uint32_t mb_mask;
static int mb_ok = 0;

static uint32_t mb_u32(const unsigned char *p) {
    return (uint32_t) p[0] | ((uint32_t) p[1] << 8)
         | ((uint32_t) p[2] << 16) | ((uint32_t) p[3] << 24);
}

static int mb_init(const unsigned char *pay, size_t n) {
    size_t need = 4 + 4 + 4 + (size_t) MB_NF * 4;
    if (pay == NULL || n != need) return 0;
    if (pay[0] != 'M' || pay[1] != 'C' || pay[2] != 'B' || pay[3] != '1') return 0;
    mb_mask = mb_u32(pay + 4);
    if (mb_u32(pay + 8) != MB_NF) return 0;
    for (int i = 0; i < MB_NF; i++) {
        uint32_t b = mb_u32(pay + 12 + i * 4);
        memcpy(&mb_flt[i], &b, 4);
    }
    mb_ok = 1;
    return 1;
}

static int mb_ready(void) { return mb_ok; }

/* --- bubbles ------------------------------------------------------------ */
/* idx 0..7 radius dp; -1 = hand the caller's fallback back (stock) */
static float mb_radius(int style) {
    if (!mb_ok || style < 0 || style > 7) return -1.0f;
    return mb_flt[style];
}
static int mb_tailless(int style) {
    if (!mb_ok || style < 0 || style > 7) return 0;
    return (mb_mask >> style) & 1;
}
/* tail param packs; count returned, -1 when the style has no pack */
static int mb_tail(int style, float out[10]) {
    if (!mb_ok) return -1;
    switch (style) {
        case 1: memcpy(out, mb_flt + 12, 10 * sizeof(float)); return 10; /* official */
        case 4: memcpy(out, mb_flt + 22,  5 * sizeof(float)); return 5;  /* classic  */
        case 7: memcpy(out, mb_flt + 27,  5 * sizeof(float)); return 5;  /* whatsapp */
        case 0: memcpy(out, mb_flt + 32,  3 * sizeof(float)); return 3;  /* stock    */
        default: return -1;
    }
}
static void mb_preview(float out[4]) {   /* allowance, inset, radiusFb, edgePad */
    memcpy(out, mb_flt + 8, 4 * sizeof(float));
}

/* --- shadows ------------------------------------------------------------- */
/* out = {blurDp, dyDp, alpha} for tier+dark; tier is clamped like Java */
static void mb_shadow(int tier, int dark, float out[3]) {
    if (tier < 0) tier = 0;
    if (tier > 2) tier = 2;
    const float *t = mb_flt + 35 + tier * 4;
    out[0] = t[0];                    /* blurDp  */
    out[1] = t[1];                    /* dyDp    */
    out[2] = dark ? t[2] : t[3];      /* alpha   */
}
static float mb_dp(float density, float v) {
    if (v == 0.0f) return 0.0f;
    float prod = density * v;
    return (float) (int) ceil((double) prod);
}
/* Java: (int) Math.ceil(dp(blur + dy)) */
static int mb_shadow_inset(int tier, float density) {
    if (tier < 0) tier = 0;
    if (tier > 2) tier = 2;
    const float *t = mb_flt + 35 + tier * 4;
    return (int) ceilf(mb_dp(density, t[0] + t[1]));
}

/* --- cards --------------------------------------------------------------- */
static float mb_cardc(int i) { return mb_flt[47 + i]; }   /* 10 consts */

/* the hue-preserving lift decision: sat/val in, new val out.
 * step = base - satCoeff*min(1,sat); val<0.5 ? min(1,val+step)
 *                                    : max(0,val-step*recess) */
static float mb_lift_core(float sat, float val) {
    float minSat = sat < 1.0f ? sat : 1.0f;
    float step = mb_flt[57] - mb_flt[58] * minSat;
    step = (float) step;
    if (val < 0.5f) {
        float r = val + step;
        return r > 1.0f ? 1.0f : r;
    }
    float rec = val - step * mb_flt[59];
    return rec < 0.0f ? 0.0f : rec;
}

/* per-position corner zeroing: POS_FIRST=1 drops bottom, POS_LAST=3 drops
 * top, POS_MIDDLE=2 squares everything (POS_SINGLE=0 keeps all 8 = radius) */
static void mb_card_radii(int position, float radius, float out[8]) {
    for (int i = 0; i < 8; i++) out[i] = radius;
    if (position == 2) {
        for (int i = 0; i < 8; i++) out[i] = 0.0f;
    } else if (position == 1) {
        out[4] = out[5] = out[6] = out[7] = 0.0f;
    } else if (position == 3) {
        out[0] = out[1] = out[2] = out[3] = 0.0f;
    }
}

/* hairline on a card: lighten 10% on dark, darken 8% on light */
static int32_t mb_card_hairline(int32_t fill, int dark) {
    int a = (int) (((uint32_t) fill) >> 24);
    int r = ((uint32_t) fill >> 16) & 0xFF;
    int g = ((uint32_t) fill >> 8) & 0xFF;
    int b = ((uint32_t) fill) & 0xFF;
    float amt = mb_flt[dark ? 60 : 61];
    float delta = 255.0f * amt;
    if (dark) {
        r = (int) fminf(255.0f, r + delta);
        g = (int) fminf(255.0f, g + delta);
        b = (int) fminf(255.0f, b + delta);
    } else {
        r = (int) fmaxf(0.0f, r - delta);
        g = (int) fmaxf(0.0f, g - delta);
        b = (int) fmaxf(0.0f, b - delta);
    }
    return (int32_t) ((uint32_t)(a << 24) | (uint32_t)(r << 16)
                    | (uint32_t)(g << 8) | (uint32_t) b);
}

#endif /* MEERO_CHAT_H */
