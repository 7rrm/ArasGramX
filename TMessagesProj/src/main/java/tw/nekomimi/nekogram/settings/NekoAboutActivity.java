package tw.nekomimi.nekogram.settings;

import tw.nekomimi.nekogram.MeeroStrings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.LayoutHelper;

/**
 * MeeroX "About" screen.
 *
 * Upstream listed the NagramX author's own channels, source repo and Crowdin
 * page. Those are replaced with MeeroX's own links, and the rows that only
 * make sense for the upstream project (translation platform, datacenter
 * diagnostics) are dropped to keep the screen short.
 */
public class NekoAboutActivity extends BaseNekoSettingsActivity {

    private int versionRow;
    private int meeroEditionRow;
    private int meeroDiagRow;
    private int developerRow;
    private int mainChannelRow;
    private int channel1Row;
    private int privacyRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        versionRow = addRow();
        meeroEditionRow = addRow();
        meeroDiagRow = addRow();
        developerRow = addRow();
        mainChannelRow = addRow();
        channel1Row = addRow();
        privacyRow = addRow();
    }

    // MeeroX v131: opt into the fixed glass design (chrome, cards,
    // mock switches, entrance stagger) via the shared support pass.
    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.About);
    }

    private String versionName() {
        try {
            return BuildConfig.BUILD_VERSION_STRING;
        } catch (Throwable ignore) {
            return "";
        }
    }

    // MeeroX v226 DIAG: the capsule-hierarchy dump, on screen, one tap away.
    // Selectable + copyable so the owner can screenshot it or paste it -
    // no Android/data spelunking on Android 16 ever again.
    private void showMeeroDiagDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final String text = ChatActivityEnterView.meeroDiagSnapshot();

        ScrollView scroll = new ScrollView(getParentActivity());
        TextView body = new TextView(getParentActivity());
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        body.setTextIsSelectable(true);
        body.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10),
                AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        body.setText(text);
        scroll.addView(body);

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("تشخيص الكبسولة");
        builder.setView(scroll);
        builder.setPositiveButton(getString(R.string.Close), null);
        builder.setNeutralButton("نسخ", (d, w) -> {
            try {
                AndroidUtilities.addToClipboard(text);
                android.widget.Toast.makeText(getParentActivity(), "تم النسخ", android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignore) {
            }
        });
        showDialog(builder.create());
    }

    private void showPrivacyDialog() {
        if (getParentActivity() == null) {
            return;
        }

        LinearLayout content = new LinearLayout(getParentActivity());
        content.setOrientation(LinearLayout.VERTICAL);

        TextView body = new TextView(getParentActivity());
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        body.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        body.setLineSpacing(AndroidUtilities.dp(3), 1f);
        body.setText(MeeroStrings.s(203));
        body.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        content.addView(body, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 0));

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(MeeroStrings.s(204));
        builder.setView(content);
        builder.setPositiveButton(MeeroStrings.s(202), (d, w) -> {
            org.telegram.messenger.MessagesController.getGlobalMainSettings()
                    .edit().putBoolean("meerox_privacy_accepted", true).apply();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == developerRow) {
            MessagesController.getInstance(currentAccount)
                    .openByUserName("i55544", NekoAboutActivity.this, 1);
        } else if (position == mainChannelRow) {
            MessagesController.getInstance(currentAccount)
                    .openByUserName("Y_VBB", NekoAboutActivity.this, 1);
        } else if (position == channel1Row) {
            MessagesController.getInstance(currentAccount)
                    .openByUserName("nadoremalf", NekoAboutActivity.this, 1);
        } else if (position == privacyRow) {
            showPrivacyDialog();
        } else if (position == meeroDiagRow) {
            showMeeroDiagDialog();
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (holder.getItemViewType() == TYPE_SETTINGS) {
                TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                if (position == versionRow) {
                    textCell.setTextAndValue(MeeroStrings.s(278), versionName(), true);
                } else if (position == meeroEditionRow) {
                    textCell.setTextAndValue(MeeroStrings.s(10), String.valueOf(BuildConfig.MEERO_EDITION), true);
                } else if (position == meeroDiagRow) {
                    textCell.setTextAndValue("فحص كبسولة الرد/التعديل", "سجلات: " + ChatActivityEnterView.meeroDiagCount(), true);
                } else if (position == developerRow) {
                    textCell.setTextAndValue(MeeroStrings.s(82), "@i55544", true);
                } else if (position == mainChannelRow) {
                    textCell.setTextAndValue(MeeroStrings.s(463), "@Y_VBB", true);
                } else if (position == channel1Row) {
                    textCell.setTextAndValue(MeeroStrings.s(37), "@nadoremalf", true);
                } else if (position == privacyRow) {
                    textCell.setText(MeeroStrings.s(204), false);
                }
            }
        }

        @Override
        public boolean isEnabled(@NonNull RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            // The version rows are informational only.
            return position != versionRow && position != meeroEditionRow && super.isEnabled(holder);
        }

        @Override
        public int getItemViewType(int position) {
            return TYPE_SETTINGS;
        }
    }
}
