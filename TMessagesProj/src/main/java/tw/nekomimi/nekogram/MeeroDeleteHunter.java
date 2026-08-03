package tw.nekomimi.nekogram;

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
 */
public final class MeeroDeleteHunter {

    private MeeroDeleteHunter() {}

    private static final String CHANNEL_ID = "meero_hunter";

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
        writeLog(new JSONArray());
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
        if (msg == null || msg.action != null) return false;
        if (msg.out) return false; // only the OTHER side's messages
        if (msg.from_id == null) return false;
        long self = UserConfig.getInstance(account).getClientUserId();
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
        return LocaleController.getString(R.string.MeeroHunterMedia);
    }

    // ---------------- log + notify (UI thread) ----------------

    private static String nameOf(int account, long userId) {
        try {
            TLRPC.User user = MessagesController.getInstance(account).getUser(userId);
            String name = user != null ? UserObject.getUserName(user) : null;
            if (!TextUtils.isEmpty(name)) return name;
        } catch (Throwable ignore) {}
        return LocaleController.getString(R.string.MeeroHunterSomeone);
    }

    private static synchronized void addLog(int account, long senderId, String kind, String oldValue, String newValue) {
        String who = nameOf(account, senderId);
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

    public static String kindText(String kind) {
        if ("edit".equals(kind)) return LocaleController.getString(R.string.MeeroHunterEditedMsg);
        return LocaleController.getString(R.string.MeeroHunterDeletedMsg);
    }

    private static void notifyEvent(String who, String kind) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        LocaleController.getString(R.string.MeeroHunterTitle), NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }
            Intent intent = new Intent(ctx, LaunchActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.nagram_notification)
                    .setContentTitle(LocaleController.getString(R.string.MeeroHunterTitle))
                    .setContentText(who + " " + kindText(kind))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            NotificationManagerCompat.from(ctx).notify(("h:" + System.currentTimeMillis()).hashCode(), builder.build());
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }
}
