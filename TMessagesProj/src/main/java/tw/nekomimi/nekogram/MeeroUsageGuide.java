package tw.nekomimi.nekogram;

import tw.nekomimi.nekogram.MeeroStrings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/**
 * MeeroX v111 (user-requested): one shared "طريقة الاستخدام" popup.
 */
public final class MeeroUsageGuide {

    private MeeroUsageGuide() {}

    public static void show(BaseFragment fragment, String textKey) {
        if (fragment == null || textKey == null) return;
        show(fragment.getParentActivity(), textKey);
    }

    public static void show(Context context, String textKey) {
        if (context == null || textKey == null) return;
        showDialog(context, MeeroStrings.s(textKey));
    }

    public static void show(BaseFragment fragment, int textId) {
        if (fragment == null) return;
        show(fragment.getParentActivity(), textId);
    }

    public static void show(Context context, int textId) {
        if (context == null) return;
        showDialog(context, MeeroStrings.s(textId));
    }

    private static void showDialog(Context context, String message) {
        if (context == null || message == null) return;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(
                AndroidUtilities.dp(24),
                AndroidUtilities.dp(20),
                AndroidUtilities.dp(24),
                AndroidUtilities.dp(16)
        );

        // النص
        TextView messageView = new TextView(context);
        messageView.setText(message);
        messageView.setTextSize(15);
        messageView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        messageView.setGravity(Gravity.CENTER);
        messageView.setPadding(0, 0, 0, AndroidUtilities.dp(28));
        layout.addView(messageView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // حاوية الزر (في المنتصف)
        LinearLayout buttonContainer = new LinearLayout(context);
        buttonContainer.setGravity(Gravity.CENTER);
        buttonContainer.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(8));

        Button button = new Button(context);
        button.setText(MeeroStrings.s(269)); // "فهمت"
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        
        // Padding صغير للزر البيضاوي الرفيع
        button.setPadding(
                AndroidUtilities.dp(109),
                AndroidUtilities.dp(9),
                AndroidUtilities.dp(109),
                AndroidUtilities.dp(9)
        );

        // شكل بيضاوي رفيع (مثل iOS)
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Theme.getColor(Theme.key_dialogButton));
        drawable.setCornerRadius(AndroidUtilities.dp(100));
        button.setBackground(drawable);

        // إغلاق النافذة
        final AlertDialog[] dialogRef = new AlertDialog[1];

        button.setOnClickListener(v -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
        });

        buttonContainer.addView(button);
        layout.addView(buttonContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(MeeroStrings.s(268)) // "طريقة الاستخدام"
                .setView(layout)
                .setPositiveButton(null, null);

        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialogRef[0] = dialog;
        dialog.show();
    }
}
