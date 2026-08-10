package tw.nekomimi.nekogram;

import tw.nekomimi.nekogram.MeeroStrings;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MeeroX v161 (his pick: every mock feature except Quick Replies - this is
 * "صانع الثيمات / Theme Mixer"): compose your own theme identity by feel,
 * no .attheme knowledge needed.
 *
 * Pick three things:
 *   1. Accent      - the signature color (outgoing bubbles, links, FAB, ticks)
 *   2. Background  - AMOLED black / dark graphite / midnight blue / paper white
 *   3. Incoming    - bubble color of the other side (follow / pure black /
 *     bubble       graphite / tinted by your accent)
 *
 * Apply generates a real .attheme and hands it to Telegram's official
 * Theme.applyThemeFile pipeline, so the result is a genuine theme listed
 * with your other themes (Telegram Settings -> Chat Settings / Themes) and
 * switching back is one tap there.
 */
public class MeeroThemeMixer {

    public static class Accent {
        public final String nameKey;
        public final int color;

        Accent(String nameKey, int color) {
            this.nameKey = nameKey;
            this.color = color;
        }
    }

    public static class Background {
        public final String nameKey;
        public final int bg;      // windowBackgroundWhite
        public final int elev;    // elevated cards / panels
        public final boolean light;

        Background(String nameKey, int bg, int elev, boolean light) {
            this.nameKey = nameKey;
            this.bg = bg;
            this.elev = elev;
            this.light = light;
        }
    }

    /* v189 (batch 3C): every palette colour and blend number below now comes
     * from the sealed motion table (dom 'C') through MeeroCore; the literal
     * paths are kept byte-identical for the no-lib fallback only. rf/rc pick
     * native-when-ready, legacy otherwise - behaviour is exactly the same. */
    private static volatile float[] mixR;
    private static volatile int[] mixC;
    private static volatile int[] mixInB;

    private static float rf(int i, float legacy) {
        float[] r = mixR;
        if (r == null && MeeroCore.motionCore()) {
            r = MeeroCore.nMixerRecipe();
            if (r != null && r.length == 16) mixR = r; else r = null;
        }
        return r != null ? r[i] : legacy;
    }

    private static int rc(int i, int legacy) {
        int[] c = mixC;
        if (c == null && MeeroCore.motionCore()) {
            c = MeeroCore.nMixerColors();
            if (c != null && c.length == 8) mixC = c; else c = null;
        }
        return c != null ? c[i] : legacy;
    }

    private static int rib(int i, int legacy) {
        int[] b = mixInB;
        if (b == null && MeeroCore.motionCore()) {
            b = MeeroCore.nMixerInBubble();
            if (b != null && b.length == 4) mixInB = b; else b = null;
        }
        return b != null ? b[i] : legacy;
    }

    private static Accent[] accents;
    private static Background[] backgrounds;
    private static int accentDef = 1;
    private static int bgDef = 0;

    public static synchronized Accent[] accents() {
        if (accents == null) {
            final String[] keys = {
                    "MixerAccentBlue", "MixerAccentRose", "MixerAccentViolet", "MixerAccentMint",
                    "MixerAccentOrange", "MixerAccentSky", "MixerAccentRed", "MixerAccentGold",
            };
            int[] c = MeeroCore.motionCore() ? MeeroCore.nMixerAccents() : null;
            if (c != null && c.length == 9) {
                accentDef = c[8];
            } else {
                c = new int[]{0xFF0A84FF, 0xFFFF4E8A, 0xFFBF5AF2, 0xFF30D158,
                        0xFFFF9F0A, 0xFF40C8E0, 0xFFFF453A, 0xFFE7B416};
                accentDef = 1;
            }
            accents = new Accent[keys.length];
            for (int i = 0; i < keys.length; i++) accents[i] = new Accent(keys[i], c[i]);
        }
        return accents;
    }

    public static synchronized Background[] backgrounds() {
        if (backgrounds == null) {
            final String[] keys = {"MixerBgAmoled", "MixerBgGraphite", "MixerBgMidnight", "MixerBgPaper"};
            int[] b = MeeroCore.motionCore() ? MeeroCore.nMixerBackgrounds() : null;
            if (b != null && b.length == 13) {
                bgDef = b[12];
            } else {
                b = new int[]{0xFF000000, 0xFF1C1C1E, 0,
                        0xFF141418, 0xFF1F1F25, 0,
                        0xFF0E1626, 0xFF182640, 0,
                        0xFFF2F2F7, 0xFFFFFFFF, 1};
                bgDef = 0;
            }
            backgrounds = new Background[keys.length];
            for (int i = 0; i < keys.length; i++) {
                backgrounds[i] = new Background(keys[i], b[i * 3], b[i * 3 + 1], b[i * 3 + 2] != 0);
            }
        }
        return backgrounds;
    }

    public static Accent accent() {
        int i = NekoConfig.meeroMixerAccent.Int();
        if (i < 0 || i >= accents().length) i = accentDef;
        return accents()[i];
    }

    public static Background background() {
        int i = NekoConfig.meeroMixerBg.Int();
        if (i < 0 || i >= backgrounds().length) i = bgDef;
        return backgrounds()[i];
    }

    /** 0 follow-dark / 1 pure black / 2 graphite / 3 accent-tinted. */
    public static int inBubbleColor() {
        final int choice = NekoConfig.meeroMixerInBubble.Int();
        final Background b = background();
        switch (choice) {
            case 1:
                return rib(0, 0xFF000000);
            case 2:
                return rib(1, 0xFF26262B);
            case 3:
                return ColorUtils.blendARGB(accent().color, b.bg, rf(10, 0.80f));
            case 0:
            default:
                return b.light ? rib(2, 0xFFFFFFFF) : rib(3, 0xFF2A2A2E);
        }
    }

    public static String[] accentNames() {
        final String[] out = new String[accents().length];
        for (int i = 0; i < out.length; i++) out[i] = MeeroStrings.s(accents()[i].nameKey);
        return out;
    }

    public static String[] backgroundNames() {
        final String[] out = new String[backgrounds().length];
        for (int i = 0; i < out.length; i++) out[i] = MeeroStrings.s(backgrounds()[i].nameKey);
        return out;
    }

    public static String[] inBubbleNames() {
        return new String[]{
                MeeroStrings.s(339),
                MeeroStrings.s(338),
                MeeroStrings.s(340),
                MeeroStrings.s(341),
        };
    }

    // ----------------------------------------------------------- builder

    private static void put(Map<String, Integer> m, String key, int color) {
        m.put(key, color);
    }

    /** Builds the .attheme content for the current picks. */
    public static Map<String, Integer> buildColors() {
        final Accent a = accent();
        final Background b = background();
        final int black = rc(4, 0xFF000000);
        final int white = rc(5, 0xFFFFFFFF);
        final int darker = ColorUtils.blendARGB(a.color, black, rf(0, 0.18f));
        final int darkish = ColorUtils.blendARGB(a.color, black, rf(1, 0.08f));
        final int lighter = ColorUtils.blendARGB(a.color, white, rf(2, 0.22f));
        final int textP = b.light ? black : white;
        final int textS = b.light ? rc(6, 0x99000000) : rc(7, 0x99FFFFFF);
        final int in = inBubbleColor();
        final int inSel = ColorUtils.blendARGB(in, b.light ? black : white, rf(3, 0.12f));
        final boolean accentDark = ColorUtils.calculateLuminance(a.color) < rf(4, 0.55f);
        final int onAccent = accentDark ? white : black;
        final int selector = ColorUtils.setAlphaComponent(a.color, (int) rf(5, 42f));
        final int barIcon = a.color;

        final Map<String, Integer> m = new LinkedHashMap<>();
        // App bar: elevated surface, white/dark text, accent icons (iOS style).
        put(m, "actionBarDefault", b.elev);
        put(m, "actionBarDefaultTitle", textP);
        put(m, "actionBarDefaultSubtitle", textS);
        put(m, "actionBarDefaultIcon", barIcon);
        put(m, "actionBarDefaultSelector", ColorUtils.setAlphaComponent(textP, (int) rf(15, 24f)));
        put(m, "actionBarTabActiveText", a.color);
        put(m, "actionBarTabLine", a.color);
        // Surfaces.
        put(m, "windowBackgroundWhite", b.bg);
        put(m, "windowBackgroundGray", b.elev);
        put(m, "dialogBackground", b.elev);
        put(m, "windowBackgroundWhiteBlueText4", a.color);
        // Chats list.
        put(m, "chats_name", textP);
        put(m, "chats_nameMessage", textS);
        put(m, "chats_attachMessage", a.color);
        put(m, "chats_date", textS);
        put(m, "chats_verifiedBackground", a.color);
        put(m, "chats_verifiedCheck", onAccent);
        put(m, "chats_onlineCircle", rc(0, 0xFF30D158));
        put(m, "chats_unreadCounter", a.color);
        put(m, "chats_unreadCounterMuted", b.light ? rc(1, 0xFF8E8E93) : rc(2, 0xFF565A60));
        put(m, "chats_actionBackground", a.color);
        put(m, "chats_actionPressedBackground", darker);
        put(m, "chats_pinnedOverlay", ColorUtils.setAlphaComponent(b.bg, (int) rf(6, 200f)));
        put(m, "chats_tabUnreadActiveBackground", a.color);
        put(m, "chats_tabUnreadUnactiveBackground", ColorUtils.blendARGB(a.color, b.bg, rf(7, 0.5f)));
        // Generic press feedback.
        put(m, "listSelectorSDK21", selector);
        // Input panel.
        put(m, "chat_messagePanelBackground", b.elev);
        put(m, "chat_messagePanelIcons", textS);
        put(m, "chat_messagePanelText", textP);
        put(m, "chat_messagePanelHint", textS);
        put(m, "chat_messagePanelSend", a.color);
        put(m, "chat_messagePanelShadow", ColorUtils.setAlphaComponent(black, (int) (b.light ? rf(11, 20f) : rf(12, 90f))));
        // Bubbles.
        put(m, "chat_inBubble", in);
        put(m, "chat_inBubbleSelected", inSel);
        put(m, "chat_outBubble", a.color);
        put(m, "chat_outBubbleSelected", darker);
        put(m, "chat_messageTextIn", textP);
        put(m, "chat_messageTextOut", onAccent);
        put(m, "chat_messageLinkIn", b.light ? accentDarkLink(a.color) : lighter);
        put(m, "chat_messageLinkOut", onAccent);
        put(m, "chat_inPreviewLine", a.color);
        put(m, "chat_outPreviewLine", onAccent);
        put(m, "chat_inReplyNameText", b.light ? accentDarkLink(a.color) : lighter);
        put(m, "chat_outReplyNameText", onAccent);
        put(m, "chat_inTimeText", textS);
        put(m, "chat_outTimeText", ColorUtils.setAlphaComponent(onAccent, (int) rf(13, 179f)));
        put(m, "chat_serviceBackground", ColorUtils.setAlphaComponent(b.elev, (int) rf(14, 230f)));
        put(m, "chat_serviceText", textP);
        put(m, "chat_inSentClock", textS);
        put(m, "chat_outSentClock", onAccent);
        put(m, "chat_outSentCheck", onAccent);
        put(m, "chat_outSentCheckRead", onAccent);
        put(m, "chat_mediaSentCheck", rc(3, 0xFFFFFFFF));
        // Toggles & controls.
        put(m, "checkbox", a.color);
        put(m, "checkboxCheck", onAccent);
        put(m, "switchTrackChecked", a.color);
        put(m, "progressCircle", a.color);
        return m;
    }

    /** Accent readable on light paper (pull it a little darker). */
    private static int accentDarkLink(int accent) {
        return ColorUtils.calculateLuminance(accent) > rf(8, 0.6f)
                ? ColorUtils.blendARGB(accent, rc(4, 0xFF000000), rf(9, 0.28f))
                : accent;
    }

    private static final String THEME_NAME = "MeeroX Mix.attheme";

    /**
     * Generates and applies the mixed theme through the official pipeline.
     * Call on the UI thread.
     *
     * @return true on success
     */
    public static boolean apply() {
        try {
            final File file = new File(ApplicationLoader.applicationContext.getCacheDir(), "meero_mix.attheme");
            final StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : buildColors().entrySet()) {
                sb.append(e.getKey()).append('=').append(String.format("#%08X", e.getValue())).append('\n');
            }
            FileWriter w = new FileWriter(file, false);
            try {
                w.write(sb.toString());
                w.flush();
            } finally {
                w.close();
            }
            return Theme.applyThemeFile(file, THEME_NAME, null, false) != null;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }
}
