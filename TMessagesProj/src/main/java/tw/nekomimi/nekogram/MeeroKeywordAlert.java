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
import org.telegram.messenger.LocaleController;
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
 */
public final class MeeroKeywordAlert {

    private MeeroKeywordAlert() {}

    private static final String CHANNEL_ID = "meero_keyword";
    private static final long THROTTLE_MS = 30_000L;
    private static volatile boolean started;
    private static final ConcurrentHashMap<Long, Long> lastNotifyAt = new ConcurrentHashMap<>();

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

        ArrayList<Entry> entries = getEntries();
        if (entries.isEmpty() || messages == null) return;

        for (MessageObject msg : messages) {
            if (msg == null || msg.isOut()) continue;
            if (msg.messageOwner == null || msg.messageOwner.action != null) continue;
            if (now - msg.messageOwner.date * 1000L > 120_000L) continue; // restored history
            String text = msg.messageOwner.message; // captions live here too
            if (TextUtils.isEmpty(text)) continue;
            String lower = text.toLowerCase(Locale.ROOT);
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
}
