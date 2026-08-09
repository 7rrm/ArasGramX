package tw.nekomimi.nekogram.settings;

import tw.nekomimi.nekogram.MeeroStrings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import tw.nekomimi.nekogram.MeeroWatch;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v102: watch change log (newest first, capped at 150 in the engine).
 *
 * Every row shows who changed what and when (old -> new for text fields).
 * Photo-change rows open a viewer for the locally cached old/new photo and
 * can save it to the gallery. Everything is local on-device data.
 */
public class MeeroWatchLogActivity extends BaseNekoSettingsActivity {

    private int headerRow;
    private int logStartRow;
    private int logEndRow;
    private int emptyRow;
    private int clearRow;
    private int infoRow;

    private final ArrayList<MeeroWatch.LogItem> items = new ArrayList<>();

    @Override
    protected void updateRows() {
        super.updateRows();
        reload();
        headerRow = addRow();
        logStartRow = rowCount;
        for (int i = 0; i < items.size(); i++) addRow();
        logEndRow = rowCount;
        emptyRow = items.isEmpty() ? addRow() : -1;
        clearRow = items.isEmpty() ? -1 : addRow();
        infoRow = addRow();
    }

    private void reload() {
        items.clear();
        items.addAll(MeeroWatch.getLog());
    }

    // MeeroX v129: opt into the fixed glass design (chrome, cards,
    // mock switches, entrance stagger) via the shared support pass.
    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return MeeroStrings.s(296);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
        listAdapter.notifyDataSetChanged();
    }

    private String timeOf(long sec) {
        // v111: seconds matter for message tracking ("بتواريخه وبثوانيه").
        return new SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(new Date(sec * 1000L));
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position >= logStartRow && position < logEndRow) {
            MeeroWatch.LogItem item = items.get(position - logStartRow);
            if ("photo".equals(item.what)) {
                showPhotoOptions(item);
            }
        } else if (position == clearRow && clearRow >= 0) {
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(MeeroStrings.s(291))
                    .setMessage(MeeroStrings.s(292))
                    .setPositiveButton(MeeroStrings.s(291), (dialog, which) -> {
                        MeeroWatch.clearLog();
                        updateRows();
                        listAdapter.notifyDataSetChanged();
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        }
    }

    private void showPhotoOptions(MeeroWatch.LogItem item) {
        ArrayList<CharSequence> options = new ArrayList<>();
        ArrayList<String> paths = new ArrayList<>();
        if (!TextUtils.isEmpty(item.oldPath) && new File(item.oldPath).exists()) {
            options.add(MeeroStrings.s(309));
            paths.add(item.oldPath);
        }
        if (!TextUtils.isEmpty(item.newPath) && new File(item.newPath).exists()) {
            options.add(MeeroStrings.s(308));
            paths.add(item.newPath);
        }
        if (options.isEmpty()) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, MeeroStrings.s(306)).show();
            return;
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle(item.who)
                .setItems(options.toArray(new CharSequence[0]), (dialog, which) -> showPhoto(paths.get(which)))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void showPhoto(String path) {
        Context context = getParentActivity();
        if (context == null) return;
        BackupImageView imageView = new BackupImageView(context);
        imageView.setImage(ImageLocation.getForPath(path), null, (android.graphics.drawable.Drawable) null, (Object) null);
        int size = AndroidUtilities.dp(280);
        FrameLayout container = new FrameLayout(context);
        container.addView(imageView, new FrameLayout.LayoutParams(size, size, android.view.Gravity.CENTER));
        new AlertDialog.Builder(context)
                .setView(container)
                .setPositiveButton(MeeroStrings.s(307), (dialog, which) -> saveToGallery(path))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void saveToGallery(String path) {
        try {
            File src = new File(path);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, src.getName());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MeeroWatch");
            }
            Uri uri = getParentActivity().getContentResolver().insert(
                    Build.VERSION.SDK_INT >= 29
                            ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("insert null");
            try (OutputStream out = getParentActivity().getContentResolver().openOutputStream(uri);
                 FileInputStream in = new FileInputStream(src)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, MeeroStrings.s(312)).show();
        } catch (Throwable ignore) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, MeeroStrings.s(311)).show();
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_TEXT || type == TYPE_DETAIL_SETTINGS;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        headerCell.setText(MeeroStrings.s(296));
                    }
                    break;
                case TYPE_DETAIL_SETTINGS:
                    TextDetailSettingsCell detailCell = (TextDetailSettingsCell) holder.itemView;
                    if (position >= logStartRow && position < logEndRow) {
                        MeeroWatch.LogItem item = items.get(position - logStartRow);
                        String title = item.who + "  •  " + MeeroWatch.whatText(item.what);
                        String detail;
                        if ("photo".equals(item.what)) {
                            detail = timeOf(item.t) + "  •  " + MeeroStrings.s(305);
                        } else if (TextUtils.isEmpty(item.oldValue)) {
                            // v111: message entries carry a single detail
                            // string (no old -> new arrow).
                            detail = item.newValue + "  •  " + timeOf(item.t);
                        } else {
                            String newV = TextUtils.isEmpty(item.newValue) ? "—" : item.newValue;
                            detail = item.oldValue + "  ←  " + newV + "  •  " + timeOf(item.t);
                        }
                        detailCell.setMultilineDetail(true);
                        detailCell.setTextAndValue(title, detail, position + 1 < logEndRow);
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == emptyRow) {
                        textCell.setTextAndValue(MeeroStrings.s(293), "", true);
                    } else if (position == clearRow && clearRow >= 0) {
                        textCell.setTextAndValue(MeeroStrings.s(291), "", true);
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == infoRow) {
                        cell.setText(MeeroStrings.s(294));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) {
                return TYPE_HEADER;
            } else if (position >= logStartRow && position < logEndRow && logEndRow > logStartRow) {
                return TYPE_DETAIL_SETTINGS;
            } else if (position == emptyRow || position == clearRow && clearRow >= 0) {
                return TYPE_TEXT;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
