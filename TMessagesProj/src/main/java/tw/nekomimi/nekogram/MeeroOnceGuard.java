package tw.nekomimi.nekogram;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * MeeroX v105: view-once guard ("حارس عرض-مرة").
 *
 * The fork does NOT ship a view-once saver (we checked: only a generic
 * message cloner exists) - so this is built from scratch. For every fresh
 * incoming view-once photo or video (media.ttl_seconds == 0x7FFFFFFF, the
 * same check the client itself uses for the "once" bubbles) we pull the
 * full file through the regular FileLoader, wait for it to land in the
 * cache (FileLoader has no per-load callback, so we poll for up to ~60s)
 * and copy it into the public gallery under "MeeroX Once". A short
 * notification confirms each save. Everything stays on the device and the
 * sender is never told anything. Downloads use data exactly like
 * auto-download does. Master switch is OFF by default; while off, nothing
 * is fetched and nothing is stored.
 */
public final class MeeroOnceGuard {

    private MeeroOnceGuard() {}

    private static final String CHANNEL_ID = "meero_once_guard";
    private static final int MAX_POLL_ATTEMPTS = 75; // 75 x 800ms = 60s
    private static volatile boolean started;

    public static void start() {
        if (started) return;
        synchronized (MeeroOnceGuard.class) {
            if (started) return;
            started = true;
            // v100 timing-safe pattern: observers on every slot unconditionally;
            // the account is re-validated per event because configs load after
            // Application.onCreate.
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
        if (!NekoConfig.meeroOnceGuard.Bool()) return;
        if (!UserConfig.getInstance(account).isClientActivated()) return;
        if (args == null || args.length < 3) return;

        long now = System.currentTimeMillis();
        long dialogId = (Long) args[0];
        @SuppressWarnings("unchecked")
        ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
        boolean scheduled = (Boolean) args[2];
        if (scheduled || messages == null) return;

        for (MessageObject mo : messages) {
            if (mo == null || mo.isOut()) continue;
            if (mo.messageOwner == null || mo.messageOwner.action != null) continue;
            if (now - mo.messageOwner.date * 1000L > 120_000L) continue; // restored history
            if (isViewOnce(mo)) {
                guard(account, mo);
            }
        }
    }

    /** View-once = self-destruct sentinel, exactly like the client's own
     *  isVoiceOnce()/isRoundOnce(). Photos and videos only (round video
     *  counts as video). */
    private static boolean isViewOnce(MessageObject mo) {
        TLRPC.MessageMedia media = mo.messageOwner.media;
        if (media == null || media.ttl_seconds != 0x7FFFFFFF) return false;
        return media.photo != null || mo.isVideo() || mo.isRoundVideo();
    }

    private static void guard(final int account, final MessageObject mo) {
        try {
            TLRPC.MessageMedia media = mo.messageOwner.media;
            final boolean isPhoto = media.photo != null && !mo.isVideo() && !mo.isRoundVideo();
            final File target;
            final FileLoader loader = FileLoader.getInstance(account);
            if (isPhoto) {
                TLRPC.Photo photo = media.photo;
                if (photo == null || photo.sizes == null || photo.sizes.isEmpty()) return;
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                if (size == null || size.location == null) return;
                ImageLocation location = ImageLocation.getForPhoto(size, photo);
                if (location == null || location.location == null) return;
                loader.loadFile(location, mo, "jpg", FileLoader.PRIORITY_HIGH, 0);
                target = loader.getPathToAttach(location.location, "jpg", true);
            } else {
                TLRPC.Document doc = mo.getDocument();
                if (doc == null) return;
                loader.loadFile(doc, mo, FileLoader.PRIORITY_HIGH, 0);
                target = loader.getPathToAttach(doc, true);
            }
            if (target == null) return;
            poll(account, mo, target, isPhoto, 0);
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }

    private static void poll(final int account, final MessageObject mo, final File target, final boolean isPhoto, final int attempt) {
        if (target.exists() && target.length() > 0) {
            saveToGallery(account, mo, target, isPhoto);
            return;
        }
        if (attempt >= MAX_POLL_ATTEMPTS) return; // give up quietly
        AndroidUtilities.runOnUIThread(() -> poll(account, mo, target, isPhoto, attempt + 1), 800);
    }

    private static void saveToGallery(final int account, final MessageObject mo, final File src, final boolean isPhoto) {
        final String who = chatTitle(account, mo.getDialogId());
        Utilities.globalQueue.postRunnable(() -> {
            try {
                Context ctx = ApplicationLoader.applicationContext;
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, "once_" + System.currentTimeMillis() + (isPhoto ? ".jpg" : ".mp4"));
                values.put(MediaStore.MediaColumns.MIME_TYPE, isPhoto ? "image/jpeg" : "video/mp4");
                if (Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, (isPhoto ? "Pictures" : "Movies") + "/MeeroX Once");
                }
                Uri collection = isPhoto
                        ? (Build.VERSION.SDK_INT >= 29 ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) : MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        : (Build.VERSION.SDK_INT >= 29 ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) : MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                Uri uri = ctx.getContentResolver().insert(collection, values);
                if (uri == null) return;
                boolean ok = false;
                try (OutputStream out = ctx.getContentResolver().openOutputStream(uri);
                     FileInputStream in = new FileInputStream(src)) {
                    byte[] buffer = new byte[64 * 1024];
                    int r;
                    while ((r = in.read(buffer)) != -1) {
                        out.write(buffer, 0, r);
                    }
                    ok = true;
                }
                if (!ok) {
                    ctx.getContentResolver().delete(uri, null, null);
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    NekoConfig.meeroOnceSavedCount.setConfigInt(NekoConfig.meeroOnceSavedCount.Int() + 1);
                    notifySaved(who, isPhoto);
                });
            } catch (Throwable t) {
                if (BuildVars.LOGS_ENABLED) FileLog.e(t);
            }
        });
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
        return LocaleController.getString(R.string.MeeroHunterSomeone);
    }

    private static void notifySaved(String who, boolean isPhoto) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        LocaleController.getString(R.string.MeeroOnceTitle), NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }
            Intent intent = new Intent(ctx, LaunchActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String kind = LocaleController.getString(isPhoto ? R.string.MeeroOnceKindPhoto : R.string.MeeroOnceKindVideo);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.nagram_notification)
                    .setContentTitle(LocaleController.getString(R.string.MeeroOnceTitle))
                    .setContentText(who + " • " + kind)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            NotificationManagerCompat.from(ctx).notify(("o:" + System.currentTimeMillis()).hashCode(), builder.build());
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }
}
