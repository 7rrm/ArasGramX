/* MeeroX v187 (batch 3A) - the glass-family design BRAIN, pure C, no JNI.
 *
 * Everything here is a mathematical reproduction of the Java mock-parity
 * formulas (MeeroGlassTheme palette, MeeroGlassSwitch geometry, MeeroGlass
 * hairline, MeeroGlassSupport card rules). The payload arrives already
 * unsealed (dom 'G') from meerocore.c; this header only parses + computes,
 * so a desktop gcc harness can exercise the exact device logic.
 *
 * Java fallback paths keep the legacy literals, byte-identical, R8-scrambled.
 */
#ifndef MEERO_GLASS_H
#define MEERO_GLASS_H

#include <math.h>
#include <stdint.h>
#include <string.h>

#define MG_PAL_N 18
#define MG_FLT_N 48        /* [0..15] switch params, [16..47] ui consts */
#define MG_GEOM_N 24

static uint32_t mg_pal[MG_PAL_N][2];   /* [id][0]=night [1]=day */
static float mg_flt[MG_FLT_N];
static int mg_ok = 0;

static uint32_t mg_u32(const unsigned char *p) {
    return (uint32_t) p[0] | ((uint32_t) p[1] << 8)
         | ((uint32_t) p[2] << 16) | ((uint32_t) p[3] << 24);
}

/* parse the unsealed payload; 1 on success */
static int mg_init(const unsigned char *pay, size_t n) {
    size_t need = 4 + 4 + (size_t) MG_PAL_N * 8 + 4 + (size_t) MG_FLT_N * 4;
    if (pay == NULL || n != need) return 0;
    if (pay[0] != 'M' || pay[1] != 'G' || pay[2] != 'T' || pay[3] != '1') return 0;
    if (mg_u32(pay + 4) != MG_PAL_N) return 0;
    const unsigned char *p = pay + 8;
    for (int i = 0; i < MG_PAL_N; i++) {
        mg_pal[i][0] = mg_u32(p + i * 8);
        mg_pal[i][1] = mg_u32(p + i * 8 + 4);
    }
    p += MG_PAL_N * 8;
    if (mg_u32(p) != MG_FLT_N) return 0;
    p += 4;
    for (int i = 0; i < MG_FLT_N; i++) {
        uint32_t b = mg_u32(p + i * 4);
        memcpy(&mg_flt[i], &b, 4);
    }
    mg_ok = 1;
    return 1;
}

static int mg_ready(void) { return mg_ok; }

static int32_t mg_color(int id, int night) {
    if (!mg_ok || id < 0 || id >= MG_PAL_N) return -1;
    return (int32_t) mg_pal[id][night ? 0 : 1];
}

static void mg_switch_params(float out[16]) {
    memcpy(out, mg_flt, 16 * sizeof(float));
}
static void mg_ui_consts(float out[32]) {
    memcpy(out, mg_flt + 16, 32 * sizeof(float));
}

/* AndroidUtilities.dp(): value==0 -> 0 else (int)Math.ceil((double)(density*value))
 * with the float32 product Java performs. Mirrored bit for bit. */
static float mg_dp(float density, float v) {
    if (v == 0.0f) return 0.0f;
    float prod = density * v;
    return (float) (int) ceil((double) prod);
}

static float mg_clampf(float v, float lo, float hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

/* The mock switch geometry. Exact mirror of MeeroGlassSwitch.onDraw math.
 * out[24]: 0 left 1 top 2 right 3 bottom 4 radius 5 cy 6 cx 7 tx 8 thumbR
 * 9 p 10 glowR 11 shadowCy 12 shadowR 13 shRectL 14 shRectT 15 shRectR
 * 16 shRectB 17 knobL 18 knobT 19 knobR 20 knobB 21 stretch 22 offX 23 onX */
static void mg_geom(float density, float w, float h,
                    float progress, float press, int rtl, float out[MG_GEOM_N]) {
    const float aspect = mg_flt[1];       /* 48/28            */
    const float insetR = mg_flt[2];       /* 3/28             */
    const float thumbR = mg_flt[3];       /* 11/28            */
    const float glowPad = mg_flt[4];      /* 9 dp             */
    const float shDy = mg_flt[5];         /* 2 dp             */
    const float shPad = mg_flt[6];        /* 3 dp             */
    const float stretchR = mg_flt[7];     /* 2/11             */

    float trackH = fminf(h, mg_dp(density, mg_flt[0]));
    float trackW = fminf(w, trackH * aspect);
    float left = (w - trackW) / 2.0f;
    float top = (h - trackH) / 2.0f;
    float right = left + trackW;
    float bottom = top + trackH;
    float radius = trackH / 2.0f;
    float cy = top + radius;
    float cx = (left + right) / 2.0f;

    float inset = trackH * insetR;
    float thumb = trackH * thumbR;
    float edgeL = left + inset + thumb;
    float edgeR = right - inset - thumb;
    float offX = rtl ? edgeR : edgeL;
    float onX = rtl ? edgeL : edgeR;

    float tx = offX + (onX - offX) * progress;   /* overshoot allowed */
    float lo = fminf(offX, onX), hi = fmaxf(offX, onX);
    tx = mg_clampf(tx, lo, hi);
    float p = mg_clampf(progress, 0.0f, 1.0f);

    float glowR = radius + mg_dp(density, glowPad);
    float shCy = cy + mg_dp(density, shDy);
    float shR = thumb + mg_dp(density, shPad);
    float shPadPx = mg_dp(density, shPad);

    float stretch = thumb * stretchR * press;
    float side = tx >= cx ? 1.0f : -1.0f;
    float outerEdge = tx + side * thumb;
    float cx2 = outerEdge - side * (thumb + stretch);

    out[0] = left;     out[1] = top;      out[2] = right;
    out[3] = bottom;   out[4] = radius;   out[5] = cy;
    out[6] = cx;       out[7] = tx;       out[8] = thumb;
    out[9] = p;        out[10] = glowR;   out[11] = shCy;
    out[12] = shR;
    out[13] = tx - thumb - shPadPx;   out[14] = cy - thumb;
    out[15] = tx + thumb + shPadPx;   out[16] = cy + thumb + mg_dp(density, 4.0f);
    out[17] = cx2 - thumb - stretch;  out[18] = cy - thumb;
    out[19] = cx2 + thumb + stretch;  out[20] = cy + thumb;
    out[21] = stretch; out[22] = offX;    out[23] = onX;
}

/* MeeroGlass.borderColor: reference-app rule. Double-precision luma like
 * the Java literals 0.299/0.587/0.114; threshold < 128; alpha 255*0.18=45. */
static int32_t mg_border(int32_t base) {
    int r = (base >> 16) & 0xFF, g = (base >> 8) & 0xFF, b = base & 0xFF;
    double lum = 0.299 * (double) r + 0.587 * (double) g + 0.114 * (double) b;
    uint32_t rgb = lum < 128 ? 0x00FFFFFFu : 0x00000000u;
    return (int32_t) (0x2D000000u | rgb);
}

/* MeeroGlassSupport.legacyCardPos */
static int mg_cardpos(int first, int last) {
    if (first && last) return 1;   /* CARD_SINGLE */
    if (first) return 2;           /* CARD_TOP    */
    if (last) return 4;            /* CARD_BOTTOM */
    return 3;                      /* CARD_MID    */
}

#endif /* MEERO_GLASS_H */
