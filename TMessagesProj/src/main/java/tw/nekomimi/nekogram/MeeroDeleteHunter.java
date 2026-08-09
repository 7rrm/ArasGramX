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
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

/**
 * MeeroX v103: delete/edit catcher ("صائد الحاذف").
 *
 * Two one-line hooks inside Ayu's save pipeline (AyuMessagesController) call
 * us whenever the other side deletes or edits a message. We then post an
 * instant system notification and append the event - with the original text
 * (and the new text for edits) - to the on-device hunter log (newest first,
 * capped at 150). Own messages and service messages are never captured.
 * Off by default? No - passive notification feature, master switch lives on
 * the screen; while off the behavior is exactly stock.
 *
 * v185 (batch 2C): the hunter log is the most sensitive store on the device
 * (a forensic record of what people tried to erase), so its heart moved
 * into libmeerocore: the 150-cap newest-first ring, the removal key machine
 * (Java-hashCode parity computed on UTF-16 units) and the capture gate run
 * natively, and the log persists as an opaque seed-sealed blob - a prefs
 * dump no longer shows whoever deleted what. Legacy JSON is imported once
 * and its plaintext dropped. Java keeps the Ayu handshake, name lookups and
 * the system notification, plus the byte-identical legacy fallback.
 */
public final class MeeroDeleteHunter {

    private MeeroDeleteHunter() {}

    private static final String CHANNEL_ID = "meero_hunter";
    private static volatile boolean nativeLoaded;

    public static final class LogItem {
        public long t;
        public long id;      // user the message came from (or dialog user)
        public String who = "";
        public String kind = "";   // "delete" | "edit"
        public String oldValue = ""; // original text
        public String newValue = ""; // new text (edit only)
    }

    // ---------------- log storage ----------------

    private static JSONArray readLog() {
        try {
            String raw = NekoConfig.meeroDeleteLog.String();
            if (!TextUtils.isEmpty(raw)) return new JSONArray(raw);
        } catch (Throwable ignore) {}
        return new JSONArray();
    }

    private static synchronized void writeLog(JSONArray log) {
        NekoConfig.meeroDeleteLog.setConfigString(log == null ? "" : log.toString());
    }

    public static synchronized ArrayList<LogItem> getLog() {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            ArrayList<LogItem> out = new ArrayList<>();
            int n = MeeroCore.nDhCount();
            for (int i = 0; i < n; i++) {
                String line = MeeroCore.nDhAt(i);
                if (line == null) continue;
                String[] f = line.split("\t", -1);
                if (f.length < 6) continue;
                LogItem li = new LogItem();
                try {
                    li.t = Long.parseLong(f[0]);
                    li.id = Long.parseLong(f[1]);
                } catch (Throwable ignore) { continue; }
                li.kind = unesc(f[2]);
                li.who = unesc(f[3]);
                li.oldValue = unesc(f[4]);
                li.newValue = unesc(f[5]);
                out.add(li);
            }
            return out;
        }
        ArrayList<LogItem> out = new ArrayList<>();
        JSONArray array = readLog();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) continue;
            LogItem li = new LogItem();
            li.t = o.optLong("t");
            li.id = o.optLong("id");
            li.who = o.optString("who", "");
            li.kind = o.optString("kind", "");
            li.oldValue = o.optString("old", "");
            li.newValue = o.optString("new", "");
            out.add(li);
        }
        return out;
    }

    public static synchronized void clearLog() {
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            MeeroCore.nDhClear();
            persistNative();
            return;
        }
        writeLog(new JSONArray());
    }

    // v104: multi-select removal. Keys are stable across sessions: a deleted
    // entry is identified by its time + sender + kind + original-text hash,
    // NOT by its position (new events may arrive while the user is picking).
    // v185: the key is minted natively (UTF-16 hashCode parity).
    public static String keyOf(LogItem li) {
        if (li == null) return "";
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            String k = MeeroCore.nDhKey(li.t, li.id, li.kind, li.oldValue);
            if (k != null) return k;
        }
        return li.t + "_" + li.id + "_" + li.kind + "_" + li.oldValue.hashCode();
    }

    /** Removes every log entry whose key is in the set. Returns the count. */
    public static synchronized int removeFromLog(java.util.HashSet<String> keys) {
        if (keys == null || keys.isEmpty()) return 0;
        if (MeeroCore.ready()) {
            ensureNativeLoaded();
            StringBuilder sb = new StringBuilder();
            for (String k : keys) {
                if (k == null || k.isEmpty()) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(k);
            }
            int removed = MeeroCore.nDhRemove(sb.toString());
            persistNative();
            return removed;
        }
        JSONArray array = readLog();
        JSONArray out = new JSONArray();
        int removed = 0;
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) continue;
            String key = o.optLong("t") + "_" + o.optLong("id") + "_"
                    + o.optString("kind", "") + "_" + o.optString("old", "").hashCode();
            if (keys.contains(key)) {
                removed++;
                continue;
            }
            out.put(o);
        }
        writeLog(out);
        return removed;
    }

    // ---------------- hooks (called from AyuMessagesController) ----------------

    public static void onMessageDeleted(com.radolyn.ayugram.messages.AyuSavePreferences prefs) {
        try {
            if (prefs == null) return;
            TLRPC.Message msg = prefs.getMessage();
            if (!captureCheck(prefs.getAccountId(), msg)) return;
            final int account = prefs.getAccountId();
            final long senderId = senderOf(prefs, msg);
            final String text = textOf(msg);
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() ->
                    addLog(account, senderId, "delete", text, ""));
        } catch (Throwable ignore) {}
    }

    public static void onMessageEdited(com.radolyn.ayugram.messages.AyuSavePreferences prefs, TLRPC.Message newMessage) {
        try {
            if (prefs == null || newMessage == null) return;
            TLRPC.Message oldMessage = prefs.getMessage();
            if (!captureCheck(prefs.getAccountId(), oldMessage)) return;
            final int account = prefs.getAccountId();
            final long senderId = senderOf(prefs, oldMessage);
            final String oldText = textOf(oldMessage);
            final String newText = textOf(newMessage);
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() ->
                    addLog(account, senderId, "edit", oldText, newText));
        } catch (Throwable ignore) {}
    }

    /** Gates: master switch, content message, not own/outgoing message. */
    private static boolean captureCheck(int account, TLRPC.Message msg) {
        if (!NekoConfig.meeroDeleteHunter.Bool()) return false;
        if (msg == null) return false;
        long self = UserConfig.getInstance(account).getClientUserId();
        if (MeeroCore.ready()) {
            // v185 (batch 2C): the capture decision lives in libmeerocore
            return MeeroCore.nDhCapture(msg.out ? 1 : 0, msg.action != null ? 1 : 0,
                    msg.from_id == null ? 0 : msg.from_id.user_id, self);
        }
        if (msg.action != null) return false;
        if (msg.out) return false; // only the OTHER side's messages
        if (msg.from_id == null) return false;
        return msg.from_id.user_id != self;
    }

    private static long senderOf(com.radolyn.ayugram.messages.AyuSavePreferences prefs, TLRPC.Message msg) {
        try {
            if (msg.from_id != null && msg.from_id.user_id != 0) return msg.from_id.user_id;
        } catch (Throwable ignore) {}
        try {
            return prefs.getFromUserId();
        } catch (Throwable ignore) {
            return 0;
        }
    }

    private static String textOf(TLRPC.Message msg) {
        if (!TextUtils.isEmpty(msg.message)) return msg.message;
        return MeeroStrings.s("MeeroHunterMedia");
    }

    // ---------------- log + notify (UI thread) ----------------

    private static String nameOf(int account, long userId) {
        try {
            TLRPC.User user = MessagesController.getInstance(account).getUser(userId);
            String name = user != null ? UserObject.getUserName(user) : null;
            if (!TextUtils.isEmpty(name)) return name;
        } catch (Throwable ignore) {}
        return MeeroStrings.s("MeeroHunterSomeone");
    }

    private static synchronized void addLog(int account, long senderId, String kind, String oldValue, String newValue) {
        String who = nameOf(account, senderId);
        if (MeeroCore.ready()) {
            // v185 (batch 2C): head insert + 150 cap + sealed persistence,
            // all inside libmeerocore
            ensureNativeLoaded();
            MeeroCore.nDhAdd(System.currentTimeMillis() / 1000L, senderId, kind, who,
                    oldValue == null ? "" : oldValue, newValue == null ? "" : newValue, 1);
            persistNative();
            notifyEvent(who, kind);
            return;
        }
        try {
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis() / 1000L);
            o.put("id", senderId);
            o.put("who", who);
            o.put("kind", kind);
            o.put("old", oldValue == null ? "" : oldValue);
            o.put("new", newValue == null ? "" : newValue);
            JSONArray log = readLog();
            JSONArray out = new JSONArray();
            out.put(o);
            for (int i = 0; i < log.length() && i < 149; i++) {
                JSONObject prev = log.optJSONObject(i);
                if (prev != null) out.put(prev);
            }
            writeLog(out);
        } catch (Throwable ignore) {}
        notifyEvent(who, kind);
    }

    // --- v185 (batch 2C): native store lifecycle ---------------------------

    /** One-shot per process: decrypt the sealed hunter log into native
     *  memory. On a fresh/tampered blob the legacy JSON key is imported once
     *  (newest-first order preserved), the sealed store is written, and the
     *  plaintext key is dropped. */
    private static synchronized void ensureNativeLoaded() {
        if (nativeLoaded || !MeeroCore.ready()) return;
        nativeLoaded = true;
        String blob = NekoConfig.meeroDeleteStore.String();
        int r = MeeroCore.nDhLoad(TextUtils.isEmpty(blob) ? null : blob);
        if (r != 1) {
            JSONArray array = readLog();
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                MeeroCore.nDhAdd(o.optLong("t"), o.optLong("id"),
                        o.optString("kind", ""), o.optString("who", ""),
                        o.optString("old", ""), o.optString("new", ""), 0);
            }
            persistNative();
        }
        if (!TextUtils.isEmpty(NekoConfig.meeroDeleteLog.String())) {
            NekoConfig.meeroDeleteLog.setConfigString("");
        }
    }

    private static void persistNative() {
        if (!MeeroCore.ready()) return;
        String blob = MeeroCore.nDhBlob();
        if (!TextUtils.isEmpty(blob)) {
            NekoConfig.meeroDeleteStore.setConfigString(blob);
        }
    }

    /** Inverts the native TSV escaping (%25 %09 %0A %0D) left to right. */
    private static String unesc(String s) {
        if (s == null || s.indexOf('%') < 0) return s == null ? "" : s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length();) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                String h = s.substring(i + 1, i + 3);
                if ("25".equals(h)) { out.append('%'); i += 3; continue; }
                if ("09".equals(h)) { out.append('\t'); i += 3; continue; }
                if ("0A".equalsIgnoreCase(h)) { out.append('\n'); i += 3; continue; }
                if ("0D".equalsIgnoreCase(h)) { out.append('\r'); i += 3; continue; }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    public static String kindText(String kind) {
        if ("edit".equals(kind)) return MeeroStrings.s("MeeroHunterEditedMsg");
        return MeeroStrings.s("MeeroHunterDeletedMsg");
    }

    private static void notifyEvent(String who, String kind) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        MeeroStrings.s("MeeroHunterTitle"), NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }
            Intent intent = new Intent(ctx, LaunchActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.nagram_notification)
                    .setContentTitle(MeeroStrings.s("MeeroHunterTitle"))
                    .setContentText(who + " " + kindText(kind))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            NotificationManagerCompat.from(ctx).notify(("h:" + System.currentTimeMillis()).hashCode(), builder.build());
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }
}
