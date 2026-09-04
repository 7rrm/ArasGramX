package tw.nekomimi.nekogram;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/**
 * MeeroX v194: one-time subscribe prompt for the owner's main channel,
 * pinned to the MeeroX settings screen (@Y_VBB, ordered by the owner).
 *
 * Behaviour (his exact spec):
 *  - shows every time the Meero settings screen is entered, until the user
 *    taps the join button;
 *  - deliberately non-cancelable: no back-key dismiss, no outside-touch
 *    dismiss, no cancel button - the only way out is the join button;
 *  - tapping it marks the promo done (global main settings - the same store
 *    the About-screen privacy acceptance already uses) and opens the
 *    channel in-app via openByUserName. Telegram law: the actual join
 *    happens inside the channel page; no client may auto-join a user from
 *    outside, so this is the deepest a prompt can legitimately go;
 *  - once done, it never shows again on this install.
 *
 * Texts ride the sealed MeeroStrings vault: the title reuses v193 row id
 * 463 ("AboutMainChannel"), body/button are the new v194 rows 464/465, so
 * the shipped DEX carries zero Arabic literals for this dialog. The whole
 * path is wrapped never-throw: any failure degrades to "no promo", never
 * to a crash on a settings visit.
 */
public final class MeeroChannelPromo {

    private MeeroChannelPromo() {
    }

    private static final String PREF_DONE = "meerox_channel_promo_done";
    private static final String CHANNEL = "InaRaS5";

    private static AlertDialog showing;

    public static void maybeShow(BaseFragment fragment) {
        try {
            if (fragment == null) {
                return;
            }
            final Activity activity = fragment.getParentActivity();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            if (MessagesController.getGlobalMainSettings().getBoolean(PREF_DONE, false)) {
                return;
            }
            if (showing != null && showing.isShowing()) {
                return;
            }

            // تصميم iOS
            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(
                    AndroidUtilities.dp(24),
                    AndroidUtilities.dp(20),
                    AndroidUtilities.dp(24),
                    AndroidUtilities.dp(16)
            );

            // النص
            TextView messageView = new TextView(activity);
            messageView.setText(MeeroStrings.s(464));
            messageView.setTextSize(15);
            messageView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            messageView.setGravity(Gravity.CENTER);
            messageView.setPadding(0, 0, 0, AndroidUtilities.dp(28));
            layout.addView(messageView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            // حاوية الزر (في المنتصف)
            LinearLayout buttonContainer = new LinearLayout(activity);
            buttonContainer.setGravity(Gravity.CENTER);
            buttonContainer.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(8));

            Button button = new Button(activity);
            button.setText(MeeroStrings.s(465)); // "انضم" أو "اشترك"
            button.setTextSize(15);
            button.setTextColor(Color.WHITE);
            button.setAllCaps(false);

            // زر بيضاوي رفيع (مثل iOS)
            button.setPadding(
                    AndroidUtilities.dp(100),  // ← عرض
                    AndroidUtilities.dp(9),  // ← ارتفاع
                    AndroidUtilities.dp(100),  // ← عرض
                    AndroidUtilities.dp(9)   // ← ارتفاع
            );

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setColor(Theme.getColor(Theme.key_dialogButton));
            drawable.setCornerRadius(AndroidUtilities.dp(100));
            button.setBackground(drawable);

            // عند الضغط
            button.setOnClickListener(v -> {
                try {
                    MessagesController.getGlobalMainSettings().edit()
                            .putBoolean(PREF_DONE, true).apply();
                } catch (Throwable ignore) {
                }
                try {
                    MessagesController.getInstance(fragment.getCurrentAccount())
                            .openByUserName(CHANNEL, fragment, 1);
                } catch (Throwable ignore) {
                }
                if (showing != null) {
                    showing.dismiss();
                }
            });

            buttonContainer.addView(button);
            layout.addView(buttonContainer, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(MeeroStrings.s(463)) // عنوان القناة
                    .setView(layout)
                    .setPositiveButton(null, null);

            AlertDialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setOnDismissListener(d -> showing = null);
            showing = dialog;
            dialog.show();

        } catch (Throwable ignore) {
            showing = null;
        }
    }
}
