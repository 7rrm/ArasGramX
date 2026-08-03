package tw.nekomimi.nekogram;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;

/**
 * MeeroX v105-v106: view-once guard ("حارس عرض-مرة") - button edition.
 *
 * v105 shipped a receipt-time auto-downloader; the user preferred the
 * manual approach the fork already had, so v106 reworked the feature:
 *  - while this switch is ON, the context menu of a self-destruct / once
 *    photo or video shows its "burn" and "save to gallery" buttons (the
 *    fork's own saver, gated in ChatActivity's AyuMoments menu block);
 *  - while OFF, once media behaves exactly like official Telegram: no
 *    save button at all.
 *
 * This class is now just the confirmation notification + the gallery
 * counter, bumped when the user taps "save to gallery" on a once item.
 * Nothing is fetched automatically anymore and nothing leaves the device.
 */
public final class MeeroOnceGuard {

    private MeeroOnceGuard() {}

    private static final String CHANNEL_ID = "meero_once_guard";

    /** Called from ChatActivity's OPTION_TTL_SAVE handler (the Ayu saver):
     *  bumps the on-screen counter and confirms with a small notification. */
    public static void onUserSaveTap() {
        try {
            NekoConfig.meeroOnceSavedCount.setConfigInt(NekoConfig.meeroOnceSavedCount.Int() + 1);
            notifySaved();
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }

    private static void notifySaved() {
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
            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.nagram_notification)
                    .setContentTitle(LocaleController.getString(R.string.MeeroOnceTitle))
                    .setContentText(LocaleController.getString(R.string.MeeroOnceSavedNotif))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            NotificationManagerCompat.from(ctx).notify(("o:" + System.currentTimeMillis()).hashCode(), builder.build());
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }
}
