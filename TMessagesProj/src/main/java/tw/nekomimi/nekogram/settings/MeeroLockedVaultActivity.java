package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.TopicsFragment;

import java.util.ArrayList;
import java.util.Arrays;

import tw.nekomimi.nekogram.MeeroChatLock;
import tw.nekomimi.nekogram.MeeroLockBadgeView;
import tw.nekomimi.nekogram.NekoConfig;

/**
 * MeeroX v107-v109: hidden-chats vault ("المحادثات المخفية").
 *
 * v109 (user-approved full redesign): the page got a real identity - a
 * branding header card with the shared gradient lock badge + a live counter,
 * avatar rows grouped inside rounded cards, a proper centered empty state,
 * and a one-glance footer line. All colors are theme-driven and the rows use
 * the stock UserCell so hidden chats show their real pictures.
 *
 * The only way in stays the bottom-bar chats long-press popup (bar_only),
 * behind one unlock per session - leaving the screen relocks the vault. A
 * tap opens the chat with this session's per-entry unlock granted; closing
 * the chat re-arms it again.
 */
public class MeeroLockedVaultActivity extends BaseNekoSettingsActivity {

    private int brandRow;
    private int listStartRow;
    private int listEndRow;
    private int emptyRow;
    private int infoRow;

    private final ArrayList<Long> locked = new ArrayList<>();

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        // Cover the vault before the prompt pops - the list behind must never
        // be readable while the gate is pending (picks the code screen for
        // the 8-digit method automatically).
        MeeroChatLock.attachVaultGate(this);
        // v109: soft first fade-in so the page eases in behind the gate.
        View toFade = view;
        toFade.setAlpha(0f);
        toFade.animate().alpha(1f).setStartDelay(15).setDuration(220).start();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        MeeroChatLock.maybePromptVault(this);
        updateRows();
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        // Vault locks the moment it is left - the next visit asks again.
        MeeroChatLock.lockVault();
    }

    @Override
    protected void updateRows() {
        super.updateRows();
        reload();
        brandRow = addRow();
        listStartRow = rowCount;
        for (int i = 0; i < locked.size(); i++) addRow();
        listEndRow = rowCount;
        emptyRow = locked.isEmpty() ? addRow() : -1;
        infoRow = addRow();
    }

    private void reload() {
        locked.clear();
        if (NekoConfig.meeroChatLock.Bool()) {
            locked.addAll(MeeroChatLock.getLockedIds());
        }
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroVaultTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private String titleOf(long dialogId) {
        MessagesController mc = MessagesController.getInstance(UserConfig.selectedAccount);
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = mc.getUser(dialogId);
            String name = user != null ? UserObject.getUserName(user) : null;
            if (!TextUtils.isEmpty(name)) return name;
        } else {
            TLRPC.Chat chat = mc.getChat(-dialogId);
            if (chat != null && !TextUtils.isEmpty(chat.title)) return chat.title;
        }
        return getString(R.string.MeeroRulesChatFallback);
    }

    private String subtitleOf(long dialogId) {
        TLRPC.Dialog dialog = MessagesController.getInstance(UserConfig.selectedAccount).dialogs_dict.get(dialogId);
        int unread = dialog != null ? dialog.unread_count : 0;
        if (unread > 0) {
            return LocaleController.formatString(R.string.MeeroVaultUnread, unread);
        }
        return getString(R.string.MeeroChatLockRowDetail);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position >= listStartRow && position < listEndRow) {
            final long dialogId = locked.get(position - listStartRow);
            // Grant the per-entry unlock so the chat gate does not double-ask
            // right after the vault gate passed; the chat relocks on exit as
            // usual (ChatActivity.onFragmentDestroy -> lockAgain).
            MeeroChatLock.markUnlocked(dialogId);
            Bundle bundle = new Bundle();
            if (dialogId < 0) {
                bundle.putLong("chat_id", -dialogId);
                if (MessagesController.getInstance(UserConfig.selectedAccount).isForum(dialogId)) {
                    presentFragment(new TopicsFragment(bundle));
                } else {
                    presentFragment(new ChatActivity(bundle));
                }
            } else {
                bundle.putLong("user_id", dialogId);
                presentFragment(new ChatActivity(bundle));
            }
        }
    }

    // ---------------- v109: custom rows ----------------

    /** Rounded card background; corners are rounded only where flags say so
     *  (first/last row of a one-card group). */
    private ShapeDrawable cardBg(Context context, boolean top, boolean bottom) {
        float r = AndroidUtilities.dp(14);
        float[] radii = new float[8];
        Arrays.fill(radii, 0);
        if (top) {
            radii[0] = radii[1] = radii[2] = radii[3] = r;
        }
        if (bottom) {
            radii[4] = radii[5] = radii[6] = radii[7] = r;
        }
        ShapeDrawable d = new ShapeDrawable(new RoundRectShape(radii, null, null));
        d.getPaint().setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        return d;
    }

    private static RecyclerView.LayoutParams rowLp(Context context, boolean topGap, boolean bottomGap) {
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = AndroidUtilities.dp(12);
        lp.rightMargin = AndroidUtilities.dp(12);
        if (topGap) lp.topMargin = AndroidUtilities.dp(8);
        return lp;
    }

    /** Branding header card: shared gradient lock badge + live counter. */
    private class BrandHeaderView extends FrameLayout {
        private final TextView countView;
        private final TextView subView;

        BrandHeaderView(Context context) {
            super(context);
            LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            column.setPadding(0, AndroidUtilities.dp(20), 0, AndroidUtilities.dp(18));
            FrameLayout.LayoutParams columnLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            addView(column, columnLp);

            MeeroLockBadgeView badge = new MeeroLockBadgeView(context);
            column.addView(badge, new LinearLayout.LayoutParams(AndroidUtilities.dp(68), AndroidUtilities.dp(68)));

            countView = new TextView(context);
            countView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            countView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            countView.setTypeface(AndroidUtilities.bold());
            countView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            countLp.topMargin = AndroidUtilities.dp(12);
            column.addView(countView, countLp);

            subView = new TextView(context);
            subView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            subView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = AndroidUtilities.dp(4);
            column.addView(subView, subLp);
        }

        void bind(int count) {
            countView.setText(LocaleController.formatString(R.string.MeeroVaultCount, count));
            subView.setText(getString(R.string.MeeroVaultBrandSub));
        }
    }

    /** Centered empty state: dimmed badge + text pair. */
    private class VaultEmptyView extends FrameLayout {

        VaultEmptyView(Context context) {
            super(context);
            LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER);
            int pad = AndroidUtilities.dp(28);
            column.setPadding(pad, AndroidUtilities.dp(26), pad, AndroidUtilities.dp(26));
            addView(column, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            MeeroLockBadgeView badge = new MeeroLockBadgeView(context);
            badge.setAlpha(0.45f); // quiet, "nothing locked here yet"
            column.addView(badge, new LinearLayout.LayoutParams(AndroidUtilities.dp(60), AndroidUtilities.dp(60)));

            TextView title = new TextView(context);
            title.setText(getString(R.string.MeeroVaultEmpty));
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTypeface(AndroidUtilities.bold());
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            titleLp.topMargin = AndroidUtilities.dp(12);
            column.addView(title, titleLp);

            TextView hint = new TextView(context);
            hint.setText(getString(R.string.MeeroVaultEmptyHint));
            hint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            hint.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            hintLp.topMargin = AndroidUtilities.dp(6);
            column.addView(hint, hintLp);
        }
    }

    private class ListAdapter extends BaseListAdapter {

        private static final int TYPE_VAULT_USER = 100;
        private static final int TYPE_VAULT_BRAND = 101;
        private static final int TYPE_VAULT_EMPTY = 102;

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_VAULT_USER;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            if (viewType == TYPE_VAULT_USER) {
                UserCell cell = new UserCell(mContext, 4, 0, false);
                cell.setLayoutParams(rowLp(mContext, false, false));
                return new RecyclerListView.Holder(cell);
            }
            if (viewType == TYPE_VAULT_BRAND) {
                BrandHeaderView brand = new BrandHeaderView(mContext);
                brand.setBackground(cardBg(mContext, true, true));
                RecyclerView.LayoutParams lp = rowLp(mContext, true, false);
                brand.setLayoutParams(lp);
                return new RecyclerListView.Holder(brand);
            }
            if (viewType == TYPE_VAULT_EMPTY) {
                VaultEmptyView empty = new VaultEmptyView(mContext);
                empty.setBackground(cardBg(mContext, true, true));
                empty.setLayoutParams(rowLp(mContext, true, false));
                empty.setMinimumHeight(AndroidUtilities.dp(190));
                return new RecyclerListView.Holder(empty);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_VAULT_BRAND:
                    ((BrandHeaderView) holder.itemView).bind(locked.size());
                    break;
                case TYPE_VAULT_USER:
                    UserCell userCell = (UserCell) holder.itemView;
                    if (position >= listStartRow && position < listEndRow) {
                        long dialogId = locked.get(position - listStartRow);
                        MessagesController mc = MessagesController.getInstance(UserConfig.selectedAccount);
                        Object obj = DialogObject.isUserDialog(dialogId)
                                ? mc.getUser(dialogId) : mc.getChat(-dialogId);
                        userCell.setData(obj, titleOf(dialogId), subtitleOf(dialogId),
                                0, position + 1 < listEndRow);
                        // one rounded card around the whole group of rows
                        userCell.setBackground(cardBg(mContext,
                                position == listStartRow, position == listEndRow - 1));
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == infoRow) {
                        cell.setText(getString(R.string.MeeroVaultInfo));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == brandRow) {
                return TYPE_VAULT_BRAND;
            }
            if (position >= listStartRow && position < listEndRow) {
                return TYPE_VAULT_USER;
            } else if (position == emptyRow) {
                return TYPE_VAULT_EMPTY;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
