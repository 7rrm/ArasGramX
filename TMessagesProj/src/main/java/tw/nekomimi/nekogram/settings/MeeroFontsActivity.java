package tw.nekomimi.nekogram.settings;

import tw.nekomimi.nekogram.MeeroStrings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;

import java.io.InputStream;
import java.util.ArrayList;

import tw.nekomimi.nekogram.MeeroFonts;

/**
 * Font picker: bundled faces plus any .ttf/.otf the user imports.
 * Each row previews itself in the face it offers.
 */
public class MeeroFontsActivity extends BaseNekoSettingsActivity {

    private static final int PICK_FONT = 4711;

    private final ArrayList<MeeroFonts.Option> options = new ArrayList<>();

    private int fontsStartRow;
    private int fontsEndRow;
    private int addFontRow;
    private int dividerRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        options.clear();
        options.addAll(MeeroFonts.getOptions());

        fontsStartRow = rowCount;
        for (int i = 0; i < options.size(); i++) {
            addRow();
        }
        fontsEndRow = rowCount;

        dividerRow = addRow();
        addFontRow = addRow();
    }

    // MeeroX v129: opt into the fixed glass design (chrome, cards,
    // mock switches, entrance stagger) via the shared support pass.
    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return MeeroStrings.s("MeeroFontSection");
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position >= fontsStartRow && position < fontsEndRow) {
            MeeroFonts.Option o = options.get(position - fontsStartRow);
            MeeroFonts.setSelected(o.id);
            AndroidUtilities.clearTypefaceCache();
            listAdapter.notifyItemRangeChanged(fontsStartRow, options.size());
            if (parentLayout != null) {
                parentLayout.rebuildAllFragmentViews(true, true);
            }
        } else if (position == addFontRow) {
            pickFont();
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (position >= fontsStartRow && position < fontsEndRow) {
            MeeroFonts.Option o = options.get(position - fontsStartRow);
            if (o.id.startsWith(MeeroFonts.CUSTOM_PREFIX) && getParentActivity() != null) {
                AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                b.setTitle(o.title);
                b.setMessage(MeeroStrings.s("MeeroFontDeleteConfirm"));
                b.setPositiveButton(getString(R.string.Delete), (d, w) -> {
                    MeeroFonts.deleteCustom(o.id);
                    AndroidUtilities.clearTypefaceCache();
                    updateRows();
                    listAdapter.notifyDataSetChanged();
                    if (parentLayout != null) {
                        parentLayout.rebuildAllFragmentViews(true, true);
                    }
                });
                b.setNegativeButton(getString(R.string.Cancel), null);
                showDialog(b.create());
                return true;
            }
        }
        return false;
    }

    private void pickFont() {
        if (getParentActivity() == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(
                    Intent.createChooser(i, MeeroStrings.s("MeeroFontPick")), PICK_FONT);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private String nameOf(Uri uri) {
        try {
            Cursor c = ApplicationLoader.applicationContext.getContentResolver()
                    .query(uri, null, null, null, null);
            if (c != null) {
                try {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0 && c.moveToFirst()) return c.getString(idx);
                } finally {
                    c.close();
                }
            }
        } catch (Throwable ignore) {}
        String p = uri.getLastPathSegment();
        if (p != null) {
            int s = p.lastIndexOf('/');
            return s >= 0 ? p.substring(s + 1) : p;
        }
        return "font.ttf";
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_FONT || resultCode != Activity.RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        String id = null;
        try (InputStream in = ApplicationLoader.applicationContext
                .getContentResolver().openInputStream(uri)) {
            if (in != null) {
                id = MeeroFonts.importFont(in, nameOf(uri));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }

        if (id == null) {
            if (getParentActivity() != null) {
                AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                b.setTitle("MeeroX");
                b.setMessage(MeeroStrings.s("MeeroFontBadFormat"));
                b.setPositiveButton(getString(R.string.OK), null);
                showDialog(b.create());
            }
            return;
        }

        MeeroFonts.setSelected(id);
        AndroidUtilities.clearTypefaceCache();
        updateRows();
        listAdapter.notifyDataSetChanged();
        if (parentLayout != null) {
            parentLayout.rebuildAllFragmentViews(true, true);
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
            int type = holder.getItemViewType();
            if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                MeeroFonts.Option o = options.get(position - fontsStartRow);
                boolean selected = o.id.equals(MeeroFonts.getSelected());
                cell.setTextAndCheck(o.title, selected, position < fontsEndRow - 1);
                Typeface tf = MeeroFonts.previewOf(o.id);
                cell.setTypeface(tf != null ? tf : Typeface.DEFAULT);
            } else if (type == TYPE_SETTINGS) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setText(MeeroStrings.s("MeeroFontAdd"), false);
                cell.getTextView().setTypeface(Typeface.DEFAULT);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= fontsStartRow && position < fontsEndRow) return TYPE_CHECK;
            if (position == addFontRow) return TYPE_SETTINGS;
            return TYPE_SHADOW;
        }
    }
}
