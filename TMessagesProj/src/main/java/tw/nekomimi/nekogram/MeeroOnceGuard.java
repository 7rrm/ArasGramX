package tw.nekomimi.nekogram;

import tw.nekomimi.nekogram.MeeroStrings;

import android.app.Activity;
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
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
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

    // ---------------- v109: legal/religious consent gate (user-requested) ----------------

    /** True only when the user pressed "موافق/agree" on the consent sheet at
     *  least once - the ONLY thing that ever turns this on. Any declined or
     *  interrupted entry leaves it false, so the sheet shows again next time
     *  (exactly the accepted-once / decline-until-accept rule he approved). */
    public static boolean consentGiven() {
        return NekoConfig.meeroOnceConsent.Bool();
    }

    /** Shows the Iraqi consent sheet whenever consent was never granted.
     *  موافق -> persist consent + onAccepted; رفض -> optionally closes the
     *  calling screen (the screen-entry path), so there is no way into the
     *  feature without pressing agree. The sheet cannot be dismissed
     *  neutrally (no outside-tap / back dismiss) - a choice is mandatory. */
    public static void ensureConsentDialog(final BaseFragment fragment, final Runnable onAccepted,
                                           final boolean finishOnDecline) {
        try {
            if (consentGiven()) {
                if (onAccepted != null) onAccepted.run();
                return;
            }
            final Activity act = fragment.getParentActivity();
            if (act == null || act.isFinishing()) {
                if (finishOnDecline) {
                    try {
                        fragment.finishFragment();
                    } catch (Throwable ignore) {}
                }
                return;
            }
            AlertDialog dlg = new AlertDialog.Builder(act)
                    .setTitle(MeeroStrings.s(178))
                    .setMessage(MeeroStrings.s(177))
                    .setPositiveButton(MeeroStrings.s(175), (d, w) -> {
                        NekoConfig.meeroOnceConsent.setConfigBool(true);
                        if (onAccepted != null) onAccepted.run();
                    })
                    .setNegativeButton(MeeroStrings.s(176), (d, w) -> {
                        if (finishOnDecline) {
                            try {
                                fragment.finishFragment();
                            } catch (Throwable ignore) {}
                        }
                    })
                    .create();
            dlg.setCancelable(false);
            dlg.setCanceledOnTouchOutside(false);
            dlg.show();
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }

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
                        MeeroStrings.s(186), NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }
            Intent intent = new Intent(ctx, LaunchActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.nagram_notification)
                    .setContentTitle(MeeroStrings.s(186))
                    .setContentText(MeeroStrings.s(185))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            NotificationManagerCompat.from(ctx).notify(("o:" + System.currentTimeMillis()).hashCode(), builder.build());
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        }
    }
}
