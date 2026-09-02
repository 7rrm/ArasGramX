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
import tw.nekomimi.nekogram.NekoConfig;

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
    // MeeroX v230: send-text-style picker row (bottom of the Fonts page).
    private int meeroStyleDividerRow;
    private int meeroSendStyleRow;

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
        meeroStyleDividerRow = addRow();
        meeroSendStyleRow = addRow();
    }

    // MeeroX v129: opt into the fixed glass design (chrome, cards,
    // mock switches, entrance stagger) via the shared support pass.
    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return MeeroStrings.s(97);
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
        } else if (position == meeroSendStyleRow) {
            showMeeroSendStyleDialog();
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (position >= fontsStartRow && position < fontsEndRow) {
            MeeroFonts.Option o = options.get(position - fontsStartRow);
            if (o.id.startsWith(MeeroFonts.CUSTOM_PREFIX) && getParentActivity() != null) {
                AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                b.setTitle(o.title);
                b.setMessage(MeeroStrings.s(95));
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

    // MeeroX v230: values 0..8 - the order matters, entityFor() in
    // MeeroMessageStyler maps the same indexes 1:1. Never reorder.
    private static final int[] MEERO_STYLE_RES = {
            R.string.MeeroStyleDefault,
            R.string.MeeroStyleBold,
            R.string.MeeroStyleItalic,
            R.string.MeeroStyleUnderline,
            R.string.MeeroStyleStrike,
            R.string.MeeroStyleSpoiler,
            R.string.MeeroStyleQuote,
            R.string.MeeroStyleMono,
            R.string.MeeroStyleCode
    };

    private int currentMeeroSendStyle() {
        try {
            final int v = NekoConfig.meeroSendTextStyle.Int();
            return v >= 0 && v < MEERO_STYLE_RES.length ? v : 0;
        } catch (Throwable ignore) {
            return 0;
        }
    }

    private void showMeeroSendStyleDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final int current = currentMeeroSendStyle();
        final String[] names = new String[MEERO_STYLE_RES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = (i == current ? "✓ " : "") + getString(MEERO_STYLE_RES[i]);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.MeeroSendStyleTitle));
        b.setItems(names, (d, which) -> {
            NekoConfig.meeroSendTextStyle.setConfigInt(which);
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(meeroSendStyleRow);
            }
        });
        showDialog(b.create());
    }

    private void pickFont() {
        if (getParentActivity() == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(
                    Intent.createChooser(i, MeeroStrings.s(96)), PICK_FONT);
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
                b.setMessage(MeeroStrings.s(94));
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
                if (position == meeroSendStyleRow) {
                    cell.setTextAndValue(getString(R.string.MeeroSendStyleTitle),
                            getString(MEERO_STYLE_RES[currentMeeroSendStyle()]), false);
                } else {
                    cell.setText(MeeroStrings.s(93), false);
                }
                cell.getTextView().setTypeface(Typeface.DEFAULT);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= fontsStartRow && position < fontsEndRow) return TYPE_CHECK;
            if (position == addFontRow || position == meeroSendStyleRow) return TYPE_SETTINGS;
            return TYPE_SHADOW;
        }
    }
}
