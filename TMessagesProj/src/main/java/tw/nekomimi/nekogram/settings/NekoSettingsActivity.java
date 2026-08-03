package tw.nekomimi.nekogram.settings;

import static android.view.View.OVER_SCROLL_NEVER;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.Cells.SettingsSearchCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DocumentSelectActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.AppRestartHelper;
import tw.nekomimi.nekogram.helpers.CloudSettingsHelper;
import tw.nekomimi.nekogram.helpers.PasscodeHelper;
import tw.nekomimi.nekogram.helpers.SettingsBackupHelper;
import tw.nekomimi.nekogram.helpers.SettingsHelper;
import tw.nekomimi.nekogram.helpers.SettingsSearchResult;
import tw.nekomimi.nekogram.utils.AlertUtil;

public class NekoSettingsActivity extends BaseNekoSettingsActivity {

    private static final int MENU_SEARCH = 1;
    private static final int MENU_SYNC = 2;

    /**
     * MeeroX: section titles. This screen had none - every entry was a plain
     * row - so the grouped-card styling had nothing to title. These give each
     * card a heading the way the reference settings layout does.
     */
    private int meeroHeaderCategories;
    private int meeroHeaderData;
    private int meeroHeaderInfo;

    /**
     * MeeroX: entry for the combined MeeroX screen.
     *
     * Sits first in the categories card because it is the fork's own section -
     * the rows under it are all stock Nekogram ones.
     */
    private int meeroRow;
    /** MeeroX v98: Auto-reply entry directly below the MeeroX section row. */
    private int autoReplyRow;
    /** MeeroX v102: activity details + account watching entries (v102 requests). */
    private int activityStatsRow;
    private int watchRow;
    /** MeeroX v103: delete/edit catcher entry. */
    private int hunterRow;
    /** MeeroX v105: keyword alert + view-once guard entries. */
    private int keywordRow;
    private int onceGuardRow;

    private int generalRow;
    private int translatorRow;
    private int chatRow;
    private int passcodeRow;
    private int experimentRow;
    private int fontsRow;
    private int categoriesEndRow;

    private int importSettingsRow;
    private int exportSettingsRow;
    private int resetSettingsRow;
    private int appRestartRow;
    private int nSettingsEndRow;


    private int aboutRow;

    /** MeeroX: section titles follow the grouped-card style switch. */
    private static boolean meeroHeadersEnabled() {
        try {
            return tw.nekomimi.nekogram.NekoConfig.meeroCards.Bool();
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        meeroHeaderCategories = meeroHeadersEnabled() ? addRow() : -1;
        meeroRow = addRow();
        autoReplyRow = addRow();
        activityStatsRow = addRow();
        watchRow = addRow();
        hunterRow = addRow();
        keywordRow = addRow();
        onceGuardRow = addRow();
        generalRow = addRow();
        translatorRow = addRow();
        chatRow = addRow();
        if (!PasscodeHelper.isSettingsHidden()) {
            passcodeRow = addRow();
        } else {
            passcodeRow = -1;
        }
        experimentRow = addRow();
        fontsRow = addRow();
        categoriesEndRow = addRow();

        meeroHeaderData = meeroHeadersEnabled() ? addRow() : -1;
        importSettingsRow = addRow();
        exportSettingsRow = addRow();
        resetSettingsRow = addRow();
        appRestartRow = addRow();
        nSettingsEndRow = addRow();

        meeroHeaderInfo = meeroHeadersEnabled() ? addRow() : -1;
        aboutRow = addRow();
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_SEARCH, R.drawable.outline_header_search, resourcesProvider);
        menu.addItem(MENU_SYNC, R.drawable.cloud_sync, resourcesProvider);
        // MeeroX: group the two header buttons under one rounded surface, the
        // way the chats header does. This screen has no liquid-glass factory,
        // so the capsule is a tinted rounded rect rather than real blur.
        meeroGroupHeaderButtons(menu);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_SEARCH) {
                    showSettingsSearchDialog();
                } else if (id == MENU_SYNC) {
                    CloudSettingsHelper.getInstance().showDialog(NekoSettingsActivity.this);
                }
            }
        });

        return view;
    }

    /**
     * @noinspection SizeReplaceableByIsEmpty
     */
    private void showSettingsSearchDialog() {
        try {
            Activity parent = getParentActivity();
            if (parent == null) return;

            ArrayList<SettingsSearchResult> results = SettingsHelper.onCreateSearchArray(fragment -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    presentFragment(fragment);
                } catch (Exception ignore) {
                }
            }));

            final ArrayList<SettingsSearchResult> filtered = new ArrayList<>(results);
            final String[] currentQuery = new String[]{""};
            final int searchHeight = dp(36);
            final int clearSize = dp(36);
            final int pad = dp(12);

            LinearLayout containerLayout = new LinearLayout(parent);
            containerLayout.setOrientation(LinearLayout.VERTICAL);
            containerLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

            FrameLayout searchFrame = new FrameLayout(parent);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, searchHeight + dp(12));
            layoutParams.leftMargin = dp(10);
            layoutParams.rightMargin = dp(10);
            layoutParams.topMargin = dp(6);
            layoutParams.bottomMargin = dp(2);
            searchFrame.setLayoutParams(layoutParams);
            searchFrame.setClipToPadding(true);
            searchFrame.setClipChildren(true);

            ImageView searchIcon = new ImageView(parent);
            searchIcon.setScaleType(ImageView.ScaleType.CENTER);
            searchIcon.setImageResource(R.drawable.ic_ab_search_solar);
            searchIcon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            searchFrame.addView(searchIcon, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            EditTextBoldCursor searchField = new EditTextBoldCursor(parent);
            searchField.setHint(getString(R.string.Search));
            searchField.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            searchField.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
            searchField.setSingleLine(true);
            searchField.setBackground(null);
            searchField.setInputType(InputType.TYPE_CLASS_TEXT);
            searchField.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedRegular));
            searchField.setPadding(dp(61), pad / 2, dp(48), pad / 2);
            searchField.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL));
            searchFrame.addView(searchField);

            ImageView clearButton = new ImageView(parent);
            clearButton.setScaleType(ImageView.ScaleType.CENTER);
            clearButton.setImageResource(R.drawable.ic_close_white);
            clearButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
            clearButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            clearButton.setLayoutParams(new FrameLayout.LayoutParams(clearSize, clearSize, Gravity.END | Gravity.CENTER_VERTICAL));
            searchFrame.addView(clearButton);
            containerLayout.addView(searchFrame);

            AlertDialog.Builder builder = new AlertDialog.Builder(parent, resourceProvider);
            builder.setView(containerLayout);
            builder.setNegativeButton(getString(R.string.Close), null);
            final AlertDialog dialog = builder.create();
            dialog.setOnShowListener(d -> {
                try {
                    searchField.requestFocus();
                    AndroidUtilities.showKeyboard(searchField);
                } catch (Exception ignore) {
                }
            });

            RecyclerListView searchListView = new RecyclerListView(parent);
            searchListView.setOverScrollMode(OVER_SCROLL_NEVER);
            searchListView.setLayoutManager(new LinearLayoutManager(parent, LinearLayoutManager.VERTICAL, false));

            var adapter = new RecyclerListView.SelectionAdapter() {
                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return true;
                }

                @NonNull
                @Override
                public RecyclerListView.Holder onCreateViewHolder(@NonNull ViewGroup parent1, int viewType) {
                    View view = new SettingsSearchCell(parent);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(view);
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    SettingsSearchCell cell = (SettingsSearchCell) holder.itemView;
                    SettingsSearchResult r = filtered.get(position);
                    String[] path = r.path2 != null ? new String[]{r.path1, r.path2} : new String[]{r.path1};
                    CharSequence titleToSet = r.searchTitle == null ? "" : r.searchTitle;
                    String q = currentQuery[0];
                    if (q != null && !q.isEmpty() && titleToSet.length() > 0) {
                        SpannableStringBuilder ss = new SpannableStringBuilder(titleToSet);
                        String lower = titleToSet.toString().toLowerCase();
                        String[] parts = q.split("\\s+");
                        int highlightColor = getThemedColor(Theme.key_windowBackgroundWhiteBlueText4);
                        for (String p : parts) {
                            if (p.isEmpty()) continue;
                            int idx = 0;
                            while (true) {
                                int found = lower.indexOf(p, idx);
                                if (found == -1) break;
                                try {
                                    ss.setSpan(new ForegroundColorSpan(highlightColor), found, found + p.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } catch (Exception ignore) {
                                }
                                idx = found + p.length();
                            }
                        }
                        titleToSet = ss;
                    }
                    cell.setTextAndValueAndIcon(titleToSet, path, r.iconResId, position < filtered.size() - 1);
                }

                @Override
                public int getItemCount() {
                    return filtered.size();
                }
            };

            searchListView.setAdapter(adapter);
            searchListView.setOnItemClickListener((v, position) -> {
                if (position < 0 || position >= filtered.size()) return;
                SettingsSearchResult r = filtered.get(position);
                try {
                    if (r.openRunnable != null) r.openRunnable.run();
                } catch (Exception ignore) {
                }
                dialog.dismiss();
            });

            containerLayout.addView(searchListView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

            searchField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void afterTextChanged(Editable s) {
                    String q = s.toString().toLowerCase().trim();
                    currentQuery[0] = q;
                    filtered.clear();
                    if (q.isEmpty()) {
                        filtered.addAll(results);
                    } else {
                        String[] parts = q.split("\\s+");
                        for (SettingsSearchResult item : results) {
                            String title = item.searchTitle == null ? "" : item.searchTitle.toLowerCase();
                            boolean ok = true;
                            for (String p : parts) {
                                if (!title.contains(p)) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (ok) filtered.add(item);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    searchIcon.setVisibility(q.length() > 20 ? View.GONE : View.VISIBLE);
                    clearButton.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                }
            });

            clearButton.setOnClickListener(v -> {
                searchField.setText("");
                searchField.requestFocus();
                AndroidUtilities.showKeyboard(searchField);
            });
            clearButton.setVisibility(View.GONE);

            showDialog(dialog);
        } catch (Exception ignore) {
        }
    }

    /**
     * MeeroX: one capsule behind the search and overflow buttons.
     *
     * ActionBar measures its menu at the full bar height, so the background is
     * drawn centred at the control size instead of filling the view.
     */
    private void meeroGroupHeaderButtons(ActionBarMenu menu) {
        if (menu == null) {
            return;
        }
        try {
            if (!tw.nekomimi.nekogram.NekoConfig.meeroDialogsStyle.Bool()) {
                return;
            }
        } catch (Throwable e) {
            return;
        }
        final int size = AndroidUtilities.dp(48);
        final int tint = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        final int bg = androidx.core.graphics.ColorUtils.setAlphaComponent(tint, 26);
        menu.setGlassMode(true);
        menu.setBackground(new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint paint =
                    new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            private final android.graphics.RectF r = new android.graphics.RectF();

            @Override
            public void draw(@NonNull android.graphics.Canvas canvas) {
                final android.graphics.Rect b = getBounds();
                final int top = b.top + (b.height() - size) / 2;
                r.set(b.left, top, b.right, top + size);
                paint.setColor(bg);
                canvas.drawRoundRect(r, size / 2f, size / 2f, paint);
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter cf) {
                paint.setColorFilter(cf);
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        });
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.NekoSettings);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == meeroRow) {
            presentFragment(new MeeroSettingsActivity());
        } else if (position == autoReplyRow) {
            presentFragment(new MeeroAutoReplyActivity());
        } else if (position == activityStatsRow) {
            presentFragment(new MeeroActivityStatsActivity());
        } else if (position == watchRow) {
            presentFragment(new MeeroWatchActivity());
        } else if (position == hunterRow) {
            presentFragment(new MeeroDeleteHunterActivity());
        } else if (position == keywordRow) {
            presentFragment(new MeeroKeywordAlertActivity());
        } else if (position == onceGuardRow) {
            presentFragment(new MeeroOnceGuardActivity());
        } else if (position == chatRow) {
            presentFragment(new NekoChatSettingsActivity());
        } else if (position == generalRow) {
            presentFragment(new NekoGeneralSettingsActivity());
        } else if (position == passcodeRow) {
            presentFragment(new NekoPasscodeSettingsActivity());
        } else if (position == experimentRow) {
            presentFragment(new NekoExperimentalSettingsActivity());
        } else if (position == fontsRow) {
            presentFragment(new MeeroFontsActivity());
        } else if (position == translatorRow) {
            presentFragment(new NekoTranslatorSettingsActivity());
        } else if (position == aboutRow) {
            presentFragment(new NekoAboutActivity());
        } else if (position == importSettingsRow) {
            if (Build.VERSION.SDK_INT >= 33) {
                openFilePicker();
            } else {
                DocumentSelectActivity activity = getDocumentSelectActivity(getParentActivity());
                if (activity != null) {
                    presentFragment(activity);
                }
            }
        } else if (position == resetSettingsRow) {
            AlertUtil.showConfirm(getParentActivity(),
                    getString(R.string.ResetSettingsAlert),
                    R.drawable.msg_reset,
                    getString(R.string.Reset),
                    true,
                    () -> {
                        ApplicationLoader.applicationContext.getSharedPreferences("nekocloud", Activity.MODE_PRIVATE).edit().clear().commit();
                        ApplicationLoader.applicationContext.getSharedPreferences("nekox_config", Activity.MODE_PRIVATE).edit().clear().commit();
                        NekoConfig.getPreferences().edit().clear().commit();
                        AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class));
                    });
        } else if (position == exportSettingsRow) {
            SettingsBackupHelper.backupSettings(getParentActivity(), resourceProvider);
        } else if (position == appRestartRow) {
            AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class));
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
            int viewType = holder.getItemViewType();
            switch (viewType) {
                case TYPE_HEADER: {
                    final tw.nekomimi.nekogram.ui.cells.HeaderCell header =
                            (tw.nekomimi.nekogram.ui.cells.HeaderCell) holder.itemView;
                    if (position == meeroHeaderCategories) {
                        header.setText(getString(R.string.MeeroSectionCategories));
                    } else if (position == meeroHeaderData) {
                        header.setText(getString(R.string.MeeroSectionData));
                    } else if (position == meeroHeaderInfo) {
                        header.setText(getString(R.string.MeeroSectionInfo));
                    }
                    break;
                }
                case TYPE_SHADOW: {
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case TYPE_TEXT: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == meeroRow) {
                        textCell.setTextAndIcon(getString(R.string.MeeroSettingsTitle), R.drawable.msg_photo_settings_solar, true);
                    } else if (position == autoReplyRow) {
                        textCell.setTextAndIcon(getString(R.string.MeeroAutoReplyTitle), R.drawable.input_reply_solar, true);
                    } else if (position == activityStatsRow) {
                        textCell.setTextAndIcon(getString(R.string.MeeroStatsTitle), R.drawable.msg_stats_solar, true);
                    } else if (position == watchRow) {
                        textCell.setTextAndIcon(getString(R.string.MeeroWatchTitle), R.drawable.msg_views_solar, true);
                    } else if (position == hunterRow) {
                        textCell.setTextAndIcon(getString(R.string.MeeroHunterTitle), R.drawable.baseline_delete_forever_24, true);
                    } else if (position == keywordRow) {
                        textCell.setTextAndIcon(getString(R.string.MeeroKeywordTitle), R.drawable.msg_search_solar, true);
                    } else if (position == onceGuardRow) {
                        textCell.setTextAndIcon(getString(R.string.MeeroOnceTitle), R.drawable.msg_download_solar, true);
                    } else if (position == chatRow) {
                        textCell.setTextAndIcon(getString(R.string.Chat), R.drawable.msg_discussion, true);
                    } else if (position == generalRow) {
                        textCell.setTextAndIcon(getString(R.string.General), R.drawable.msg_theme, true);
                    } else if (position == translatorRow) {
                        textCell.setTextAndIcon(getString(R.string.TranslatorSettings), R.drawable.ic_translate, true);
                    } else if (position == passcodeRow) {
                        textCell.setTextAndIcon(getString(R.string.PasscodeNeko), R.drawable.msg_permissions, true);
                    } else if (position == experimentRow) {
                        textCell.setTextAndIcon(getString(R.string.Experimental), R.drawable.msg_fave, true);
                    } else if (position == fontsRow) {
                        textCell.setTextAndValueAndIcon(getString(R.string.MeeroFontSection), tw.nekomimi.nekogram.MeeroFonts.titleOf(tw.nekomimi.nekogram.MeeroFonts.getSelected()), R.drawable.msg_photo_text_regular, true);
                    } else if (position == importSettingsRow) {
                        textCell.setTextAndIcon(getString(R.string.ImportSettings), R.drawable.msg_photo_settings_solar, true);
                    } else if (position == exportSettingsRow) {
                        textCell.setTextAndIcon(getString(R.string.BackupSettings), R.drawable.msg_instant_link_solar, true);
                    } else if (position == resetSettingsRow) {
                        textCell.setTextAndIcon(getString(R.string.ResetSettings), R.drawable.msg_reset_solar, true);
                    } else if (position == appRestartRow) {
                        textCell.setTextAndIcon(getString(R.string.RestartApp), R.drawable.msg_retry_solar, true);
                    } else if (position == aboutRow) {
                        textCell.setTextAndIcon(getString(R.string.About), R.drawable.msg_info, true);
                    }
                    break;
                }

            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position != -1 && (position == meeroHeaderCategories
                    || position == meeroHeaderData || position == meeroHeaderInfo)) {
                return TYPE_HEADER;
            }
            if (position == categoriesEndRow || position == nSettingsEndRow) {
                return TYPE_SHADOW;
            } else if (position == meeroRow ||
                    position == chatRow || position == generalRow || position == passcodeRow || position == experimentRow || position == translatorRow ||
                    position == autoReplyRow ||
                    position == activityStatsRow || position == watchRow || position == hunterRow ||
                    position == keywordRow || position == onceGuardRow ||
                    position == fontsRow ||
                    position == importSettingsRow || position == exportSettingsRow || position == resetSettingsRow || position == appRestartRow ||
                    position == aboutRow) {
                return TYPE_TEXT;
            }
            return TYPE_SHADOW;
        }
    }

    private DocumentSelectActivity getDocumentSelectActivity(Activity parent) {
        try {
            if (parent.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                parent.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                return null;
            }
        } catch (Throwable ignore) {
        }
        DocumentSelectActivity fragment = new DocumentSelectActivity(false);
        fragment.setMaxSelectedFiles(1);
        fragment.setAllowPhoto(false);
        fragment.setDelegate(new DocumentSelectActivity.DocumentSelectActivityDelegate() {
            @Override
            public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files, String caption, boolean notify, int scheduleDate) {
                activity.finishFragment();
                SettingsBackupHelper.importSettings(parent, new File(files.get(0)));
            }

            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
            }

            @Override
            public void startDocumentSelectActivity() {
            }
        });
        return fragment;
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, 21);
        } catch (android.content.ActivityNotFoundException ex) {
            AlertUtil.showSimpleAlert(getParentActivity(), ex);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == 21 && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                File cacheDir = AndroidUtilities.getCacheDir();
                String tempFile = UUID.randomUUID().toString().replace("-", "") + ".nekox-settings.json";
                File file = new File(cacheDir.getPath(), tempFile);
                try {
                    final InputStream inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        OutputStream outputStream = new FileOutputStream(file);
                        final byte[] buffer = new byte[4 * 1024];
                        int read;
                        while ((read = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }
                        inputStream.close();
                        outputStream.flush();
                        outputStream.close();
                        SettingsBackupHelper.importSettings(getParentActivity(), file);
                    }
                } catch (Exception ignore) {
                }
            }
            super.onActivityResultFragment(requestCode, resultCode, data);
        }
    }
}
