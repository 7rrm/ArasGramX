package tw.nekomimi.nekogram;

import android.content.SharedPreferences;
import android.graphics.Typeface;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * App-wide font selection for MeeroX.
 *
 * Bundled faces live in assets/fonts/, user-supplied .ttf/.otf files are
 * copied into files/meerox_fonts/. The chosen face is returned from
 * AndroidUtilities.getTypeface(), which every text view in the app resolves
 * through, so a change applies everywhere at once.
 */
public class MeeroFonts {

    /* v189 (batch 3C): the bundled-face table (ids, titles, asset paths) and
     * the user-file prefix now come from the sealed motion table (dom 'C');
     * the literals below only serve the no-lib fallback, byte-identical. */
    public static final String DEFAULT = "default";
    public static final String CUSTOM_PREFIX = initPrefix();

    private static volatile String[] fontTab;

    private static String[] fontTab() {
        String[] t = fontTab;
        if (t == null && MeeroCore.motionCore()) {
            t = MeeroCore.nFonts();
            if (t != null && t.length == 31) fontTab = t; else t = null;
        }
        return t;
    }

    private static String initPrefix() {
        try {
            if (MeeroCore.motionCore()) {
                String[] t = MeeroCore.nFonts();
                if (t != null && t.length == 31 && t[30] != null) return t[30];
            }
        } catch (Throwable ignore) {
        }
        return "custom:";
    }

    private static final String PREFS = "meerox_fonts";
    private static final HashMap<String, Typeface> cache = new HashMap<>();

    private static String selected;
    private static boolean loaded;

    public static class Option {
        public final String id;
        public final String title;
        /** asset path, or null for the stock font / a user file */
        public final String asset;

        Option(String id, String title, String asset) {
            this.id = id;
            this.title = title;
            this.asset = asset;
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    public static void load() {
        if (loaded) return;
        selected = prefs().getString("font", DEFAULT);
        loaded = true;
    }

    public static String getSelected() {
        load();
        return selected;
    }

    public static void setSelected(String id) {
        load();
        selected = id;
        prefs().edit().putString("font", id).apply();
        cache.clear();
    }

    public static File customDir() {
        File d = new File(ApplicationLoader.applicationContext.getFilesDir(), "meerox_fonts");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static ArrayList<Option> getOptions() {
        ArrayList<Option> list = new ArrayList<>();
        final String[] t = fontTab();
        if (t != null) {
            for (int i = 0; i < 30; i += 3) {
                list.add(new Option(t[i], t[i + 1], t[i + 2].isEmpty() ? null : t[i + 2]));
            }
        } else {
            list.add(new Option(DEFAULT, "Default", null));
            list.add(new Option("ios15", "iOS 15", "fonts/meerox_f8.ttf"));
            list.add(new Option("arabicui", "Arabic UI Text", "fonts/meerox_f1.ttf"));
            list.add(new Option("arabicuidisplay", "Arabic UI Display", "fonts/meerox_f6.ttf"));
            list.add(new Option("arefruqaa", "Aref Ruqaa", "fonts/meerox_f3.ttf"));
            list.add(new Option("gs45", "GS45 Arabic", "fonts/meerox_f7.ttf"));
            list.add(new Option("cairo", "Cairo", "fonts/meero_cairo.ttf"));
            list.add(new Option("tajawal", "Tajawal", "fonts/meero_tajawal.ttf"));
            list.add(new Option("almarai", "Almarai", "fonts/meero_almarai.ttf"));
            list.add(new Option("inter", "Inter", "fonts/meero_inter.ttf"));
        }

        File[] files = customDir().listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName().toLowerCase();
                if (n.endsWith(".ttf") || n.endsWith(".otf")) {
                    list.add(new Option(CUSTOM_PREFIX + f.getName(), f.getName(), null));
                }
            }
        }
        return list;
    }

    public static String titleOf(String id) {
        for (Option o : getOptions()) {
            if (o.id.equals(id)) return o.title;
        }
        return "Default";
    }

    /** Copies a user-picked font into our directory. Returns its option id, or null. */
    public static String importFont(InputStream in, String displayName) {
        try {
            String safe = displayName.replaceAll("[^A-Za-z0-9._-]", "_");
            if (!safe.toLowerCase().endsWith(".ttf") && !safe.toLowerCase().endsWith(".otf")) {
                safe = safe + ".ttf";
            }
            File out = new File(customDir(), safe);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
            }
            // Reject anything Android cannot actually load, so a bad file
            // cannot leave the UI without a usable font.
            Typeface probe = Typeface.createFromFile(out);
            if (probe == null) {
                out.delete();
                return null;
            }
            return CUSTOM_PREFIX + out.getName();
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    public static boolean deleteCustom(String id) {
        if (id == null || !id.startsWith(CUSTOM_PREFIX)) return false;
        File f = new File(customDir(), id.substring(CUSTOM_PREFIX.length()));
        boolean ok = f.delete();
        if (ok && id.equals(getSelected())) setSelected(DEFAULT);
        cache.clear();
        return ok;
    }

    private static Typeface resolve(String id) {
        if (id == null || DEFAULT.equals(id)) return null;
        Typeface c = cache.get(id);
        if (c != null) return c;
        try {
            Typeface tf;
            if (id.startsWith(CUSTOM_PREFIX)) {
                File f = new File(customDir(), id.substring(CUSTOM_PREFIX.length()));
                if (!f.exists()) return null;
                tf = Typeface.createFromFile(f);
            } else {
                String asset = null;
                for (Option o : getOptions()) {
                    if (o.id.equals(id)) { asset = o.asset; break; }
                }
                if (asset == null) return null;
                tf = Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(), asset);
            }
            if (tf != null) cache.put(id, tf);
            return tf;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    /** Preview face for a row in the picker. */
    public static Typeface previewOf(String id) {
        return resolve(id);
    }

    /**
     * The face the whole app should use, or null to keep Telegram's own.
     * Bold/italic are derived so headers and emphasis stay consistent.
     */
    public static Typeface appTypeface(boolean bold, boolean italic) {
        Typeface base = resolve(getSelected());
        if (base == null) return null;

        int style = Typeface.NORMAL;
        if (bold && italic) style = Typeface.BOLD_ITALIC;
        else if (bold) style = Typeface.BOLD;
        else if (italic) style = Typeface.ITALIC;

        if (style == Typeface.NORMAL) return base;

        String key = getSelected() + "#" + style;
        Typeface c = cache.get(key);
        if (c != null) return c;
        Typeface derived = Typeface.create(base, style);
        cache.put(key, derived);
        return derived;
    }

    public static void clearCache() {
        cache.clear();
    }
}
