package tw.nekomimi.nekogram;

import tw.nekomimi.nekogram.MeeroStrings;

import android.content.Context;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * MeeroX v111 (user-requested): one shared "طريقة الاستخدام" popup.
 *
 * The long info footers at the bottom of the Meero feature sections grew
 * into screens of their own ("شرح كبير مخرب الشكل" - his words). Each of
 * those sections now ends with a tidy button instead; pressing it opens
 * this dialog with the SAME full explanation - no information is lost, the
 * screen just stops wearing it. Single "فهمت" button, reopenable anytime.
 */
public final class MeeroUsageGuide {

    private MeeroUsageGuide() {}

    /** Shows the usage dialog. Safe no-op without a live context. */
    public static void show(BaseFragment fragment, String textKey) {
        if (fragment == null || textKey == null) return;
        show(fragment.getParentActivity(), textKey);
    }

    public static void show(Context context, String textKey) {
        if (context == null || textKey == null) return;
        new AlertDialog.Builder(context)
                .setTitle(MeeroStrings.s(268))
                .setMessage(MeeroStrings.s(textKey))
                .setPositiveButton(MeeroStrings.s(269), null)
                .show();
    }

    /* v186 (batch 2D): numeric vault-id form - call sites no longer carry
     * the guide key as a readable DEX literal. */
    public static void show(BaseFragment fragment, int textId) {
        if (fragment == null) return;
        show(fragment.getParentActivity(), textId);
    }

    public static void show(Context context, int textId) {
        if (context == null) return;
        new AlertDialog.Builder(context)
                .setTitle(MeeroStrings.s(268))
                .setMessage(MeeroStrings.s(textId))
                .setPositiveButton(MeeroStrings.s(269), null)
                .show();
    }
}
