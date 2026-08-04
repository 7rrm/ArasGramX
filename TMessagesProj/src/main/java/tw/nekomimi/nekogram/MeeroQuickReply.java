package tw.nekomimi.nekogram;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * MeeroX v117: quick-reply templates - the data store.
 *
 * A template is just saved text. Long-pressing the send button on an empty
 * message field lists them, and choosing one inserts its text into the input
 * box; the user still reviews and presses send themselves. Nothing is ever
 * sent or written anywhere automatically, so the feature is strictly a
 * display convenience - hence the master switch defaults to on, and off
 * removes the popup exactly like stock.
 *
 * Storage is a small JSON array inside a local config string (max
 * {@link #MAX_TEMPLATES} templates, {@link #MAX_LEN} chars each). No network,
 * no files, nothing shared with other apps.
 */
public final class MeeroQuickReply {

    public static final int MAX_TEMPLATES = 30;
    public static final int MAX_LEN = 450;
    private static final int PREVIEW_LEN = 42;

    public static final class Template {
        public long id;
        public String text;
        public long t; // creation time, seconds - also the ordering
    }

    private MeeroQuickReply() {
    }

    public static boolean enabled() {
        try {
            return NekoConfig.meeroQuickReply.Bool();
        } catch (Throwable t) {
            return false;
        }
    }

    public static ArrayList<Template> list() {
        ArrayList<Template> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(NekoConfig.meeroQuickReplyTemplates.String());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Template t = new Template();
                t.id = o.optLong("id");
                t.text = o.optString("text");
                t.t = o.optLong("t");
                if (t.id != 0 && t.text != null && !t.text.isEmpty()) {
                    out.add(t);
                }
            }
        } catch (JSONException ignored) {
        }
        out.sort((a, b) -> Long.compare(a.t, b.t));
        return out;
    }

    public static int count() {
        return list().size();
    }

    /** Adds a template. Returns false when the store is full or text is empty. */
    public static boolean add(String rawText) {
        String text = sane(rawText);
        if (text == null || count() >= MAX_TEMPLATES) {
            return false;
        }
        ArrayList<Template> all = list();
        Template t = new Template();
        t.id = System.nanoTime();
        t.text = text;
        t.t = System.currentTimeMillis() / 1000;
        all.add(t);
        save(all);
        return true;
    }

    /** Edits in place; false when the id is unknown or the new text is empty. */
    public static boolean update(long id, String rawText) {
        String text = sane(rawText);
        if (text == null) {
            return false;
        }
        ArrayList<Template> all = list();
        for (Template t : all) {
            if (t.id == id) {
                t.text = text;
                save(all);
                return true;
            }
        }
        return false;
    }

    public static boolean delete(long id) {
        ArrayList<Template> all = list();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == id) {
                all.remove(i);
                save(all);
                return true;
            }
        }
        return false;
    }

    /** One-line preview for menus and rows; keeps the first line, ellipsizes. */
    public static String previewOf(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').trim();
        return oneLine.length() <= PREVIEW_LEN ? oneLine : oneLine.substring(0, PREVIEW_LEN) + "…";
    }

    private static String sane(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() <= MAX_LEN ? t : t.substring(0, MAX_LEN);
    }

    private static void save(ArrayList<Template> all) {
        try {
            JSONArray arr = new JSONArray();
            for (Template t : all) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("text", t.text);
                o.put("t", t.t);
                arr.put(o);
            }
            NekoConfig.meeroQuickReplyTemplates.setConfigString(arr.toString());
        } catch (JSONException ignored) {
        }
    }
}
