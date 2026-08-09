package tw.nekomimi.nekogram;

import tw.nekomimi.nekogram.MeeroStrings;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MeeroX v105: keyword alert ("منبه الكلمات المفتاحية").
 *
 * Watches NotificationCenter.didReceiveNewMessages for every account slot
 * (the same v100 timing-safe pattern as the auto-reply: observers attach
 * unconditionally at Application.onCreate, activation is re-checked per
 * event). A new incoming message that contains one of the configured words
 * posts an instant system notification - even for muted groups. An entry is
 * a dialog id plus a comma separated word list; dialog id 0 means "every
 * chat". Words under 2 letters never match, and per-chat alerts are
 * throttled to one per 30 seconds so buzzing groups stay usable. All
 * matching is on-device; while the master switch is off the watcher does
 * nothing at all. Off by default (user opt-in).
 *
 * v184 (batch 2B): the matching heart moved into libmeerocore - freshness,
 * entry scan order, comma + arabic-comma splitting, trimming, the 2-letter
 * floor and the 30 s per-chat throttle all run natively, and the word sets
 * persist as an opaque seed-sealed blob. Java keeps the system notification
 * plumbing and a byte-identical legacy JSON path for builds without the lib.
 */
public final class MeeroKeywordAlert {

    private MeeroKeywordAlert() {}

    private static final String CHANNEL_ID = "meero_keyword";
    private static final long THROTTLE_MS = 30_000L; // legacy fallback path only
    private static volatile boolean started;
    private static volatile boolean nativeLoaded;
    private static final ConcurrentHashMap<Long, Long> lastNotifyAt = new ConcurrentHashMap<>(); // legacy fallback only

    public static final class Entry {
        public long dialogId;      // 0 = every chat
        public String words = "";
    }

    public static void start() {
        if (started) return;
        synchronized (MeeroKeywordAlert.class) {
            if (started) return;
            started = true;
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                NotificationCenter.getInstance(account).addObserver((id, account1, args) -> {
                    if (id == NotificationCenter.didReceiveNewMessages) {
                        onNewMessages(account1, args);
                    }
                }, NotificationCenter.didReceiveNewMessages);
            }
        }
    }

    private static void onNewMessages(int account, Object[] args) {
        if (!NekoConfig.meeroKeywordAlert.Bool()) return;
        // per-event activation check: configs load after Application.onCreate
        if (!UserConfig.getInstance(account).isClientActivated()) return;
        if (args == null || args.length < 3) return;

        long now = System.currentTimeMillis();
        long dialogId = (Long) args[0];
        @SuppressWarnings("unchecked")
        ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
        boolean scheduled = (Boolean) args[2];
        if (scheduled) return;
        if (dialogId == UserConfig.getInstance(account).getClientUserId()) return; // Saved Messages
        if (dialogId == 777000) return; // Telegram service account

        final boolean nativeCore = MeeroCore.ready();
        ArrayList<Entry> entries = null;
        if (nativeCore) {
            ensureNativeLoaded();
            if (MeeroCore.nKwCount() == 0) return;
        } else {
            entries = getEntries();
            if (entries.isEmpty()) return;
        }
        if (messages == null) return;

        for (MessageObject msg : messages) {
            if (msg == null || msg.isOut()) continue;
            if (msg.messageOwner == null || msg.messageOwner.action != null) continue;
            if (now - msg.messageOwner.date * 1000L > 120_000L) continue; // restored history
            String text = msg.messageOwner.message; // captions live here too
            if (TextUtils.isEmpty(text)) continue;
            String lower = text.toLowerCase(Locale.ROOT);

            if (nativeCore) {
                // one alert per message is enough: the native core returns
                // the winning word, or null for no-hit / throttled
                String hit = MeeroCore.nKwMatch(dialogId, msg.messageOwner.date, now, lower);
                if (hit == null) continue;
                String who = senderName(account, msg, dialogId);
                String chat = chatTitle(account, dialogId);
                notifyHit(who, chat, text);
                continue;
            }

            for (Entry entry : entries) {
                if (entry.dialogId != 0 && entry.dialogId != dialogId) continue;
                String hit = firstHit(entry.words, lower);
                if (hit == null) continue;
                // one alert per chat per 30 seconds; rapid-fire chats stay sane
                Long last = lastNotifyAt.get(dialogId);
                if (last != null && now - last < THROTTLE_MS) break;
                lastNotifyAt.put(dialogId, now);
                String who = senderName(account, msg, dialogId);
                String chat = chatTitle(account, dialogId);
                notifyHit(who, chat, text);
                break; // one alert per message is enough
            }
        }
    }

    private static String firstHit(String words, String lowerText) {
        for (String w : words.split("[,،]")) {
            String t = w.trim();
            if (t.length() >= 2 && lowerText.contains(t.toLowerCase(Locale.ROOT))) return t;
        }
        return null;
    }

    private static String senderName(int account, MessageObject msg, long dialogId) {
        try {
            MessagesController mc = MessagesController.getInstance(account);
            if (msg.messageOwner.from_id != null && msg.messageOwner.from_id.user_id != 0) {
                TLRPC.User u = mc.getUser(msg.messageOwner.from_id.user_id);
                if (u != null) return UserObject.getUserName(u);
            }
        } catch (Throwable ignore) {}
        return chatTitle(account, dialogId);
    }

    private static String chatTitle(int account, long dialogId) {
        try {
            MessagesController mc = MessagesController.getInstance(account);
            if (DialogObject.isUserDialog(dialogId)) {
                TLRPC.User u = mc.getUser(dialogId);
                if (u != null) return UserObject.getUserName(u);
            } else {
                TLRPC.Chat c = mc.getChat(-dialogId);
                if (c != null && !TextUtils.isEmpty(c.title)) return c.title;
            }
        } catch (Throwable ignore) {}
        return MeeroStrings.s("MeeroHunterSomeone");
    }

    private static void notifyHit(String who, String chat, String fullText) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        MeeroStrings.s("MeeroKeywordTitle"), NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }
            Intent intent = new Intent(ctx, LaunchActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String snippet = fullText.replace('\n', ' ').trim();
            if (snippet.length() > 100) snippet = snippet.substring(0, 100) + "…";
            String body = chat.equals(who) ? who + ": " + snippet : chat + " • " + who + ": " + snippet;
            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.nagram_notification)
                    .setContentTitle(MeeroStrings.s("MeeroKeywordTitle"))
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            NotificationManagerCompat.from(ctx).notify(("k:" + System.currentTimeMillis()).hashCode(), builder.build());
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }

    // ---------------- keyword sets (JSON [{"id":long,"words":"a,b"}]) ----------------

    private static JSONArray readEntries() {
        try {
            String raw = NekoConfig.meeroKeywordRules.String();
            if (!TextUtils.isEmpty(raw)) return new JSONArray(raw);
        } catch (Throwable ignore) {}
        return new JSONArray();
    }

    private static synchronized void writeEntries(JSONArray array) {
        NekoConfig.meeroKeywordRules.setConfigString(array == null ? "" : array.toString());
    }

    public static synchronized ArrayList<Entry> getEntries() {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            ArrayList<Entry> out = new ArrayList<>();
            int n = MeeroCore.nKwCount();
            for (int i = 0; i < n; i++) {
                String words = MeeroCore.nKwWordsAt(i);
                if (words == null || words.trim().isEmpty()) continue;
                Entry e = new Entry();
                e.dialogId = MeeroCore.nKwIdAt(i);
                e.words = words;
                out.add(e);
            }
            return out;
        }
        ArrayList<Entry> out = new ArrayList<>();
        JSONArray array = readEntries();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) continue;
            Entry e = new Entry();
            e.dialogId = o.optLong("id");
            e.words = o.optString("words", "");
            if (!TextUtils.isEmpty(e.words.trim())) out.add(e);
        }
        return out;
    }

    public static int getEntryCount() {
        return getEntries().size();
    }

    /** One set per dialog id (0 = the global "all chats" set). Empty words = remove. */
    public static synchronized void upsertEntry(long dialogId, String words) {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            MeeroCore.nKwUpsert(dialogId, normalizeWords(words));
            persistNative();
            return;
        }
        JSONArray array = readEntries();
        JSONArray out = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null || o.optLong("id") == dialogId) continue;
            out.put(o);
        }
        if (words != null && !TextUtils.isEmpty(words.trim())) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", dialogId);
                o.put("words", words.trim());
                out.put(o);
            } catch (Throwable ignore) {}
        }
        writeEntries(out);
    }

    public static synchronized void removeEntry(long dialogId) {
        upsertEntry(dialogId, null);
    }

    // --- v184 (batch 2B): native store lifecycle ---------------------------

    /** Words are stored lowercase (matching is case-insensitive anyway);
     *  null/empty means remove. */
    private static String normalizeWords(String words) {
        if (words == null) return null;
        String t = words.trim();
        if (t.isEmpty()) return null;
        return t.toLowerCase(Locale.ROOT);
    }

    /** One-shot per process: decrypt the sealed store into native memory.
     *  On a fresh/tampered blob the legacy JSON key is imported once, the
     *  sealed store is written, and the plaintext key is dropped. */
    private static synchronized void ensureNativeLoaded() {
        if (nativeLoaded || !MeeroCore.ready()) return;
        nativeLoaded = true;
        String blob = NekoConfig.meeroKeywordStore.String();
        int r = MeeroCore.nKwLoad(TextUtils.isEmpty(blob) ? null : blob);
        if (r != 1) {
            importLegacyToNative();
            persistNative();
        }
        if (!TextUtils.isEmpty(NekoConfig.meeroKeywordRules.String())) {
            NekoConfig.meeroKeywordRules.setConfigString("");
        }
    }

    private static void persistNative() {
        if (!MeeroCore.ready()) return;
        String blob = MeeroCore.nKwBlob();
        if (!TextUtils.isEmpty(blob)) {
            NekoConfig.meeroKeywordStore.setConfigString(blob);
        }
    }

    private static void importLegacyToNative() {
        JSONArray array = readEntries();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) continue;
            String words = normalizeWords(o.optString("words", ""));
            if (words != null) {
                MeeroCore.nKwUpsert(o.optLong("id"), words);
            }
        }
    }
}
