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

public final class MeeroKeywordAlert {

    private MeeroKeywordAlert() {}

    private static final String CHANNEL_ID = "meero_keyword";
    private static final long THROTTLE_MS = 30_000L;
    private static volatile boolean started;
    private static volatile boolean nativeLoaded;
    private static final ConcurrentHashMap<Long, Long> lastNotifyAt = new ConcurrentHashMap<>();

    public static final class Entry {
        public long dialogId;
        public String words = "";
    }

    // ============================================================
    // 📝 سجل التنبيهات (Alert Log)
    // ============================================================
    public static final class LogItem {
        public long timestamp;
        public long dialogId;
        public String who = "";
        public String chat = "";
        public String message = "";
        public String matchedWord = "";
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
        if (!UserConfig.getInstance(account).isClientActivated()) return;
        if (args == null || args.length < 3) return;

        long now = System.currentTimeMillis();
        long dialogId = (Long) args[0];
        @SuppressWarnings("unchecked")
        ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
        boolean scheduled = (Boolean) args[2];
        if (scheduled) return;
        if (dialogId == UserConfig.getInstance(account).getClientUserId()) return;
        if (dialogId == 777000) return;

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
            if (now - msg.messageOwner.date * 1000L > 120_000L) continue;
            String text = msg.messageOwner.message;
            if (TextUtils.isEmpty(text)) continue;
            String lower = text.toLowerCase(Locale.ROOT);

            String matchedWord = null;

            if (nativeCore) {
                String hit = MeeroCore.nKwMatch(dialogId, msg.messageOwner.date, now, lower);
                if (hit == null) continue;
                matchedWord = hit;
                String who = senderName(account, msg, dialogId);
                String chat = chatTitle(account, dialogId);
                notifyHit(who, chat, text, matchedWord, dialogId);
                continue;
            }

            for (Entry entry : entries) {
                if (entry.dialogId != 0 && entry.dialogId != dialogId) continue;
                
                // 🔹 دعم كلمات متعددة - البحث عن كل كلمة في النص
                String[] words = entry.words.split("[,،]");
                for (String w : words) {
                    String word = w.trim();
                    if (word.length() < 2) continue;
                    if (lower.contains(word.toLowerCase(Locale.ROOT))) {
                        matchedWord = word;
                        break;
                    }
                }
                
                if (matchedWord == null) continue;
                
                Long last = lastNotifyAt.get(dialogId);
                if (last != null && now - last < THROTTLE_MS) break;
                lastNotifyAt.put(dialogId, now);
                String who = senderName(account, msg, dialogId);
                String chat = chatTitle(account, dialogId);
                notifyHit(who, chat, text, matchedWord, dialogId);
                break;
            }
        }
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
        return MeeroStrings.s(127);
    }

    // ============================================================
    // 📝 تسجيل التنبيه في السجل
    // ============================================================
    private static void addToLog(long dialogId, String who, String chat, String message, String matchedWord) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("id", dialogId);
            o.put("who", who);
            o.put("chat", chat);
            o.put("msg", message);
            o.put("word", matchedWord);

            JSONArray array;
            String existing = NekoConfig.getPreferences().getString("meeroKeywordLog", "");
            if (!TextUtils.isEmpty(existing)) {
                array = new JSONArray(existing);
            } else {
                array = new JSONArray();
            }

            JSONArray newArray = new JSONArray();
            newArray.put(o);
            for (int i = 0; i < array.length() && i < 199; i++) {
                newArray.put(array.get(i));
            }

            NekoConfig.getPreferences().edit().putString("meeroKeywordLog", newArray.toString()).apply();
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }

    public static ArrayList<LogItem> getLog() {
        ArrayList<LogItem> items = new ArrayList<>();
        String existing = NekoConfig.getPreferences().getString("meeroKeywordLog", "");
        if (TextUtils.isEmpty(existing)) return items;
        try {
            JSONArray array = new JSONArray(existing);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                LogItem item = new LogItem();
                item.timestamp = o.optLong("t");
                item.dialogId = o.optLong("id");
                item.who = o.optString("who", "");
                item.chat = o.optString("chat", "");
                item.message = o.optString("msg", "");
                item.matchedWord = o.optString("word", "");
                items.add(item);
            }
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
        return items;
    }

    public static void clearLog() {
        NekoConfig.getPreferences().edit().remove("meeroKeywordLog").apply();
    }

    private static void notifyHit(String who, String chat, String fullText, String matchedWord, long dialogId) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        MeeroStrings.s(162), NotificationManager.IMPORTANCE_DEFAULT);
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
                    .setContentTitle(MeeroStrings.s(162) + " - \"" + matchedWord + "\"")
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            NotificationManagerCompat.from(ctx).notify(("k:" + System.currentTimeMillis()).hashCode(), builder.build());

            // تسجيل في السجل
            addToLog(dialogId, who, chat, fullText, matchedWord);

        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }

    // ---------------- keyword sets ----------------

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

    private static String normalizeWords(String words) {
        if (words == null) return null;
        String t = words.trim();
        if (t.isEmpty()) return null;
        return t.toLowerCase(Locale.ROOT);
    }

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
