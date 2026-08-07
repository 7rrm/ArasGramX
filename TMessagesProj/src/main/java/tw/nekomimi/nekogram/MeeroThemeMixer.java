package tw.nekomimi.nekogram;

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
        public final int nameRes;
        public final int color;

        Accent(int nameRes, int color) {
            this.nameRes = nameRes;
            this.color = color;
        }
    }

    public static class Background {
        public final int nameRes;
        public final int bg;      // windowBackgroundWhite
        public final int elev;    // elevated cards / panels
        public final boolean light;

        Background(int nameRes, int bg, int elev, boolean light) {
            this.nameRes = nameRes;
            this.bg = bg;
            this.elev = elev;
            this.light = light;
        }
    }

    private static Accent[] accents;
    private static Background[] backgrounds;

    public static synchronized Accent[] accents() {
        if (accents == null) {
            accents = new Accent[]{
                    new Accent(R.string.MixerAccentBlue, 0xFF0A84FF),
                    new Accent(R.string.MixerAccentRose, 0xFFFF4E8A),
                    new Accent(R.string.MixerAccentViolet, 0xFFBF5AF2),
                    new Accent(R.string.MixerAccentMint, 0xFF30D158),
                    new Accent(R.string.MixerAccentOrange, 0xFFFF9F0A),
                    new Accent(R.string.MixerAccentSky, 0xFF40C8E0),
                    new Accent(R.string.MixerAccentRed, 0xFFFF453A),
                    new Accent(R.string.MixerAccentGold, 0xFFE7B416),
            };
        }
        return accents;
    }

    public static synchronized Background[] backgrounds() {
        if (backgrounds == null) {
            backgrounds = new Background[]{
                    new Background(R.string.MixerBgAmoled, 0xFF000000, 0xFF1C1C1E, false),
                    new Background(R.string.MixerBgGraphite, 0xFF141418, 0xFF1F1F25, false),
                    new Background(R.string.MixerBgMidnight, 0xFF0E1626, 0xFF182640, false),
                    new Background(R.string.MixerBgPaper, 0xFFF2F2F7, 0xFFFFFFFF, true),
            };
        }
        return backgrounds;
    }

    public static Accent accent() {
        int i = NekoConfig.meeroMixerAccent.Int();
        if (i < 0 || i >= accents().length) i = 1;
        return accents()[i];
    }

    public static Background background() {
        int i = NekoConfig.meeroMixerBg.Int();
        if (i < 0 || i >= backgrounds().length) i = 0;
        return backgrounds()[i];
    }

    /** 0 follow-dark / 1 pure black / 2 graphite / 3 accent-tinted. */
    public static int inBubbleColor() {
        final int choice = NekoConfig.meeroMixerInBubble.Int();
        final Background b = background();
        switch (choice) {
            case 1:
                return 0xFF000000;
            case 2:
                return 0xFF26262B;
            case 3:
                return ColorUtils.blendARGB(accent().color, b.bg, 0.80f);
            case 0:
            default:
                return b.light ? 0xFFFFFFFF : 0xFF2A2A2E;
        }
    }

    public static String[] accentNames() {
        final String[] out = new String[accents().length];
        for (int i = 0; i < out.length; i++) out[i] = LocaleController.getString(accents()[i].nameRes);
        return out;
    }

    public static String[] backgroundNames() {
        final String[] out = new String[backgrounds().length];
        for (int i = 0; i < out.length; i++) out[i] = LocaleController.getString(backgrounds()[i].nameRes);
        return out;
    }

    public static String[] inBubbleNames() {
        return new String[]{
                LocaleController.getString(R.string.MixerInBubbleFollow),
                LocaleController.getString(R.string.MixerInBubbleBlack),
                LocaleController.getString(R.string.MixerInBubbleGraphite),
                LocaleController.getString(R.string.MixerInBubbleTinted),
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
        final int darker = ColorUtils.blendARGB(a.color, 0xFF000000, 0.18f);
        final int darkish = ColorUtils.blendARGB(a.color, 0xFF000000, 0.08f);
        final int lighter = ColorUtils.blendARGB(a.color, 0xFFFFFFFF, 0.22f);
        final int textP = b.light ? 0xFF000000 : 0xFFFFFFFF;
        final int textS = b.light ? 0x99000000 : 0x99FFFFFF;
        final int in = inBubbleColor();
        final int inSel = ColorUtils.blendARGB(in, b.light ? 0xFF000000 : 0xFFFFFFFF, 0.12f);
        final boolean accentDark = ColorUtils.calculateLuminance(a.color) < 0.55f;
        final int onAccent = accentDark ? 0xFFFFFFFF : 0xFF000000;
        final int selector = ColorUtils.setAlphaComponent(a.color, 42);
        final int barIcon = a.color;

        final Map<String, Integer> m = new LinkedHashMap<>();
        // App bar: elevated surface, white/dark text, accent icons (iOS style).
        put(m, "actionBarDefault", b.elev);
        put(m, "actionBarDefaultTitle", textP);
        put(m, "actionBarDefaultSubtitle", textS);
        put(m, "actionBarDefaultIcon", barIcon);
        put(m, "actionBarDefaultSelector", ColorUtils.setAlphaComponent(textP, 24));
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
        put(m, "chats_onlineCircle", 0xFF30D158);
        put(m, "chats_unreadCounter", a.color);
        put(m, "chats_unreadCounterMuted", b.light ? 0xFF8E8E93 : 0xFF565A60);
        put(m, "chats_actionBackground", a.color);
        put(m, "chats_actionPressedBackground", darker);
        put(m, "chats_pinnedOverlay", ColorUtils.setAlphaComponent(b.bg, 200));
        put(m, "chats_tabUnreadActiveBackground", a.color);
        put(m, "chats_tabUnreadUnactiveBackground", ColorUtils.blendARGB(a.color, b.bg, 0.5f));
        // Generic press feedback.
        put(m, "listSelectorSDK21", selector);
        // Input panel.
        put(m, "chat_messagePanelBackground", b.elev);
        put(m, "chat_messagePanelIcons", textS);
        put(m, "chat_messagePanelText", textP);
        put(m, "chat_messagePanelHint", textS);
        put(m, "chat_messagePanelSend", a.color);
        put(m, "chat_messagePanelShadow", ColorUtils.setAlphaComponent(0xFF000000, b.light ? 20 : 90));
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
        put(m, "chat_outTimeText", ColorUtils.setAlphaComponent(onAccent, 179));
        put(m, "chat_serviceBackground", ColorUtils.setAlphaComponent(b.elev, 230));
        put(m, "chat_serviceText", textP);
        put(m, "chat_inSentClock", textS);
        put(m, "chat_outSentClock", onAccent);
        put(m, "chat_outSentCheck", onAccent);
        put(m, "chat_outSentCheckRead", onAccent);
        put(m, "chat_mediaSentCheck", 0xFFFFFFFF);
        // Toggles & controls.
        put(m, "checkbox", a.color);
        put(m, "checkboxCheck", onAccent);
        put(m, "switchTrackChecked", a.color);
        put(m, "progressCircle", a.color);
        return m;
    }

    /** Accent readable on light paper (pull it a little darker). */
    private static int accentDarkLink(int accent) {
        return ColorUtils.calculateLuminance(accent) > 0.6f
                ? ColorUtils.blendARGB(accent, 0xFF000000, 0.28f)
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
