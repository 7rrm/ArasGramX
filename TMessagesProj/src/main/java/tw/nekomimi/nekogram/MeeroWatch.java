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
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;

/**
 * MeeroX v102: account watching ("مراقبة الحسابات").
 *
 * Watch any user (contacts, or any account by username / known id). Changes
 * to name, username, profile photo, bio and birthday produce an instant
 * system notification plus an entry in the on-device change log. Old and new
 * profile photos are cached locally so the log can show/download them.
 *
 * Per-person ON/OFF switches gate every check - pausing one person never
 * affects the rest. Everything is read-only observation; nothing is ever
 * changed or sent under the user's name. Engine attaches in
 * Application.onCreate to every account slot (v100 timing-safe pattern);
 * the master switch is checked per event.
 */
public final class MeeroWatch {

    private MeeroWatch() {}

    private static final String CHANNEL_ID = "meero_watch";

    public static final class Entry {
        public long id;
        public boolean on;
    }

    public static final class LogItem {
        public long t;
        public long id;
        public String who = "";
        public String what = "";
        public String oldValue = "";
        public String newValue = "";
        public String oldPath = "";
        public String newPath = "";
    }

    // ---------------- storage ----------------

    private static JSONArray readList() {
        try {
            String raw = NekoConfig.meeroWatchList.String();
            if (!TextUtils.isEmpty(raw)) return new JSONArray(raw);
        } catch (Throwable ignore) {}
        return new JSONArray();
    }

    private static synchronized void writeList(JSONArray array) {
        NekoConfig.meeroWatchList.setConfigString(array == null ? "" : array.toString());
    }

    private static JSONObject readData() {
        try {
            String raw = NekoConfig.meeroWatchData.String();
            if (!TextUtils.isEmpty(raw)) return new JSONObject(raw);
        } catch (Throwable ignore) {}
        return new JSONObject();
    }

    private static synchronized void writeData(JSONObject data) {
        NekoConfig.meeroWatchData.setConfigString(data == null ? "" : data.toString());
    }

    private static JSONArray readLog() {
        try {
            String raw = NekoConfig.meeroWatchLog.String();
            if (!TextUtils.isEmpty(raw)) return new JSONArray(raw);
        } catch (Throwable ignore) {}
        return new JSONArray();
    }

    private static synchronized void writeLog(JSONArray log) {
        NekoConfig.meeroWatchLog.setConfigString(log == null ? "" : log.toString());
    }

    // ---------------- public list API ----------------

    public static synchronized ArrayList<Entry> getEntries() {
        ArrayList<Entry> out = new ArrayList<>();
        JSONArray array = readList();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) continue;
            Entry e = new Entry();
            e.id = o.optLong("id");
            e.on = o.optBoolean("on", true);
            out.add(e);
        }
        return out;
    }

    public static int count() {
        return getEntries().size();
    }

    public static boolean isWatched(long id) {
        ArrayList<Entry> entries = getEntries();
        for (Entry e : entries) if (e.id == id) return true;
        return false;
    }

    private static boolean isOn(long id) {
        ArrayList<Entry> entries = getEntries();
        for (Entry e : entries) if (e.id == id) return e.on;
        return false;
    }

    /** Returns false when the id is already watched. */
    public static synchronized boolean add(long id) {
        if (isWatched(id)) return false;
        JSONArray array = readList();
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("on", true);
            array.put(o);
        } catch (Throwable ignore) {}
        writeList(array);
        return true;
    }

    public static synchronized void remove(long id) {
        JSONArray array = readList();
        JSONArray out = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o != null && o.optLong("id") == id) continue;
            if (o != null) out.put(o);
        }
        writeList(out);
        JSONObject data = readData();
        data.remove(Long.toString(id));
        writeData(data);
    }

    public static synchronized void setOn(long id, boolean on) {
        JSONArray array = readList();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o != null && o.optLong("id") == id) {
                try { o.put("on", on); } catch (Throwable ignore) {}
            }
        }
        writeList(array);
    }

    // ---------------- public log API ----------------

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
            li.what = o.optString("what", "");
            li.oldValue = o.optString("old", "");
            li.newValue = o.optString("new", "");
            li.oldPath = o.optString("oldPath", "");
            li.newPath = o.optString("newPath", "");
            out.add(li);
        }
        return out;
    }

    public static synchronized void clearLog() {
        writeLog(new JSONArray());
    }

    // ---------------- engine ----------------

    private static volatile boolean started;

    public static void start() {
        if (started) return;
        synchronized (MeeroWatch.class) {
            if (started) return;
            started = true;
            // v100 pattern: attach to every slot unconditionally - UserConfig is
            // not loaded yet at Application.onCreate, activation is checked per
            // event instead.
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                NotificationCenter.getInstance(account).addObserver((id, account1, args) -> {
                    if (id == NotificationCenter.updateInterfaces) {
                        onUpdateInterfaces(account1, args);
                    }
                }, NotificationCenter.updateInterfaces);
                NotificationCenter.getInstance(account).addObserver((id, account1, args) -> {
                    if (id == NotificationCenter.userInfoDidLoad) {
                        onUserInfoLoaded(account1, args);
                    }
                }, NotificationCenter.userInfoDidLoad);
            }
            org.telegram.messenger.AndroidUtilities.runOnUIThread(periodic, 60_000);
        }
    }

    private static final Runnable periodic = new Runnable() {
        @Override
        public void run() {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) refresh(a, true);
            }
            org.telegram.messenger.AndroidUtilities.runOnUIThread(this, 3_600_000);
        }
    };

    /** Called from LaunchActivity on cold start: refresh everything on. */
    public static void onAppForeground() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) refresh(a, true);
        }
    }

    /** Trigger a baseline refresh right after adding someone. */
    public static void onAdded(long id) {
        refresh(UserConfig.selectedAccount, true);
    }

    private static void onUpdateInterfaces(int account, Object[] args) {
        if (!NekoConfig.meeroWatchEnabled.Bool() || count() == 0) return;
        if (!UserConfig.getInstance(account).isClientActivated()) return;
        if (args == null || args.length < 1) return;
        int mask = (Integer) args[0];
        if ((mask & (MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_AVATAR | MessagesController.UPDATE_MASK_STATUS)) == 0) return;
        refresh(account, false); // cheap local diff; full info stays periodic
    }

    private static void onUserInfoLoaded(int account, Object[] args) {
        if (!NekoConfig.meeroWatchEnabled.Bool()) return;
        if (args == null || args.length < 2) return;
        try {
            long uid = (Long) args[0];
            if (!isOn(uid)) return;
            TLRPC.UserFull userFull = (TLRPC.UserFull) args[1];
            diffFull(account, uid, userFull);
        } catch (Throwable ignore) {}
    }

    public static void refresh(int account, boolean withFullInfo) {
        if (!NekoConfig.meeroWatchEnabled.Bool()) return;
        ArrayList<Entry> entries = getEntries();
        MessagesController controller = MessagesController.getInstance(account);
        for (Entry e : entries) {
            if (!e.on) continue;
            TLRPC.User user = controller.getUser(e.id);
            if (user == null) continue;
            diffUser(account, user);
            if (withFullInfo) {
                try {
                    controller.loadFullUser(user, 0, true, null);
                } catch (Throwable ignore) {}
            }
        }
    }

    // ---------------- diffing ----------------

    private static String buildName(TLRPC.User user) {
        String first = user.first_name == null ? "" : user.first_name.trim();
        String last = user.last_name == null ? "" : user.last_name.trim();
        return (first + " " + last).trim();
    }

    private static void diffUser(int account, TLRPC.User user) {
        long id = user.id;
        if (!isOn(id)) return;
        String name = buildName(user);
        String username = user.username == null ? "" : user.username;
        long photoId = user.photo != null ? user.photo.photo_id : 0;

        JSONObject snap = readData().optJSONObject(Long.toString(id));
        if (snap == null) {
            // baseline - store silently, cache the photo for future "old" views
            mergeSnap(id, "name", name);
            mergeSnap(id, "user", username);
            mergeSnap(id, "photo", photoId);
            cachePhoto(account, user, id, photoId, 0);
            return;
        }
        String oldName = snap.optString("name", "");
        String oldUsername = snap.optString("user", "");
        long oldPhoto = snap.optLong("photo", 0);

        if (!TextUtils.equals(name, oldName)) {
            addLog(account, id, name, "name", oldName, name, null, null);
        }
        if (!TextUtils.equals(username, oldUsername)) {
            addLog(account, id, name, "username", oldUsername, username, null, null);
        }
        if (photoId != oldPhoto) {
            addLog(account, id, name, "photo", "", "", photoFilePath(id, oldPhoto), photoFilePath(id, photoId));
            cachePhoto(account, user, id, photoId, 0);
        }
        mergeSnap(id, "name", name);
        mergeSnap(id, "user", username);
        mergeSnap(id, "photo", photoId);
    }

    private static void diffFull(int account, long uid, TLRPC.UserFull userFull) {
        if (userFull == null) return;
        String bio = userFull.about == null ? "" : userFull.about;
        String bday = "";
        if (userFull.birthday != null) {
            bday = userFull.birthday.day + "/" + userFull.birthday.month + "/" + userFull.birthday.year;
        }
        JSONObject snap = readData().optJSONObject(Long.toString(uid));
        if (snap == null || !snap.has("bio")) {
            mergeSnap(uid, "bio", bio); // silent baseline
        } else {
            String old = snap.optString("bio", "");
            if (!TextUtils.equals(bio, old)) {
                addLog(account, uid, snap.optString("name", ""), "bio", old, bio, null, null);
                mergeSnap(uid, "bio", bio);
            }
        }
        snap = readData().optJSONObject(Long.toString(uid));
        if (snap == null || !snap.has("bday")) {
            mergeSnap(uid, "bday", bday); // silent baseline
        } else {
            String old = snap.optString("bday", "");
            if (!TextUtils.equals(bday, old)) {
                addLog(account, uid, snap.optString("name", ""), "bday", old, bday, null, null);
                mergeSnap(uid, "bday", bday);
            }
        }
    }

    private static synchronized void mergeSnap(long id, String key, Object value) {
        try {
            JSONObject data = readData();
            JSONObject snap = data.optJSONObject(Long.toString(id));
            if (snap == null) snap = new JSONObject();
            snap.put(key, value);
            data.put(Long.toString(id), snap);
            writeData(data);
        } catch (Throwable ignore) {}
    }

    private static synchronized void addLog(int account, long id, String who, String what,
                                           String oldValue, String newValue, String oldPath, String newPath) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis() / 1000L);
            o.put("id", id);
            o.put("who", who == null ? "" : who);
            o.put("what", what);
            o.put("old", oldValue == null ? "" : oldValue);
            o.put("new", newValue == null ? "" : newValue);
            if (oldPath != null) o.put("oldPath", oldPath);
            if (newPath != null) o.put("newPath", newPath);
            JSONArray log = readLog();
            JSONArray out = new JSONArray();
            out.put(o);
            for (int i = 0; i < log.length() && i < 149; i++) {
                JSONObject prev = log.optJSONObject(i);
                if (prev != null) out.put(prev);
            }
            writeLog(out);
        } catch (Throwable ignore) {}
        notifyChange(who, what);
    }

    // ---------------- photos ----------------

    private static File photoFile(long id, long photoId) {
        if (photoId == 0) return null;
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "watch");
        return new File(dir, id + "_" + photoId + ".jpg");
    }

    private static String photoFilePath(long id, long photoId) {
        File f = photoFile(id, photoId);
        return f == null ? "" : f.getAbsolutePath();
    }

    /** Ensures the big profile photo is cached under files/watch/ for the log. */
    private static void cachePhoto(int account, TLRPC.User user, long id, long photoId, int attempt) {
        if (photoId == 0 || user == null || user.photo == null) return;
        final File dest = photoFile(id, photoId);
        if (dest == null || dest.exists() || attempt > 3) return;
        try {
            ImageLocation location = ImageLocation.getForUser(user, ImageLocation.TYPE_BIG);
            if (location == null) return;
            File src = FileLoader.getInstance(account).getPathToAttach(location.location, "jpg", true);
            if (src != null && src.exists() && src.length() > 0) {
                File dir = dest.getParentFile();
                if (dir != null) dir.mkdirs();
                copyFile(src, dest);
            } else {
                FileLoader.getInstance(account).loadFile(location, user, null, 0, FileLoader.MEDIA_DIR_CACHE);
                final int next = attempt + 1;
                final TLRPC.User finalUser = user;
                final int finalAccount = account;
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> cachePhoto(finalAccount, finalUser, id, photoId, next), 1500);
            }
        } catch (Throwable ignore) {}
    }

    private static void copyFile(File src, File dest) {
        try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Throwable ignore) {}
    }

    // ---------------- notifications ----------------

    public static String whatText(String what) {
        switch (what == null ? "" : what) {
            case "name": return LocaleController.getString(R.string.MeeroWatchChangedName);
            case "username": return LocaleController.getString(R.string.MeeroWatchChangedUsername);
            case "bio": return LocaleController.getString(R.string.MeeroWatchChangedBio);
            case "bday": return LocaleController.getString(R.string.MeeroWatchChangedBday);
            case "photo": return LocaleController.getString(R.string.MeeroWatchChangedPhoto);
            default: return what == null ? "" : what;
        }
    }

    private static void notifyChange(String who, String what) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        LocaleController.getString(R.string.MeeroWatchTitle), NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }
            Intent intent = new Intent(ctx, LaunchActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.nagram_notification)
                    .setContentTitle(LocaleController.getString(R.string.MeeroWatchTitle))
                    .setContentText(TextUtils.isEmpty(who) ? whatText(what) : (who + " " + whatText(what)))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            NotificationManagerCompat.from(ctx).notify(("w:" + (who == null ? "" : who) + ":" + what + ":" + System.currentTimeMillis() / 60000).hashCode(), builder.build());
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }
}
