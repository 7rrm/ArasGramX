package tw.nekomimi.nekogram;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.FilterCreateActivity;

import java.util.ArrayList;

import org.telegram.messenger.support.LongSparseIntArray;

/**
 * MeeroX v161 (approved feature set, his pick: "الميزات كلها ماعدا الردود
 * الجاهزة"): Smart Folders - one-tap folders built from compound rules.
 *
 * Honest scope (also stated in the UI): Telegram's synced folder engine
 * itself enforces these rules, so a smart folder keeps working on every
 * device and respects the official server limit. That engine speaks in
 * flags, not counts - "unread >= 5" from the mock is therefore delivered as
 * "unread" (exclude_read); the count rule cannot be synced server-side and
 * is documented as a v2 idea.
 */
public class MeeroSmartFolders {

    public static class Preset {
        public final String titleKey;
        public final String ruleKey;
        public final int flags;
        public final String emoticon;
        public final int color; // 0..7 folder colors, matches FilterCreateActivity

        Preset(String titleKey, String ruleKey, int flags, String emoticon, int color) {
            this.titleKey = titleKey;
            this.ruleKey = ruleKey;
            this.flags = flags;
            this.emoticon = emoticon;
            this.color = color;
        }
    }

    private static Preset[] presets;

    public static synchronized Preset[] presets() {
        if (presets == null) {
            final int C = MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
            final int NC = MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
            final int G = MessagesController.DIALOG_FILTER_FLAG_GROUPS;
            final int CH = MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
            final int B = MessagesController.DIALOG_FILTER_FLAG_BOTS;
            final int XM = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
            final int XR = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
            final int XA = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
            presets = new Preset[]{
                    // Mock card 1: channels + unread + not muted (count>5 not server-syncable)
                    new Preset("SmartFolderUnreadChannels", "SmartFolderUnreadChannelsRule", CH | XR | XM, "\uD83D\uDCE2", 1),
                    // Mock card 3: bots & service notifications
                    new Preset("SmartFolderBots", "SmartFolderBotsRule", B | XA, "\uD83E\uDD16", 5),
                    // Unread private chats & groups
                    new Preset("SmartFolderUnreadChats", "SmartFolderUnreadChatsRule", C | NC | G | XR, "\uD83D\uDCE5", 0),
                    // Family: contacts, excluding archived clutter
                    new Preset("SmartFolderFamily", "SmartFolderFamilyRule", C | XA, "\u2764\uFE0F", 2),
                    // Active groups only (nothing muted)
                    new Preset("SmartFolderActiveGroups", "SmartFolderActiveGroupsRule", G | XM, "\uD83D\uDC65", 3),
            };
        }
        return presets;
    }

    /** True when a folder carrying this preset's exact localized title exists. */
    public static boolean exists(Preset p) {
        final String title = MeeroStrings.s(p.titleKey);
        final MessagesController mc = MessagesController.getInstance(UserConfig.selectedAccount);
        for (MessagesController.DialogFilter f : mc.dialogFilters) {
            if (title.equalsIgnoreCase(f.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates the preset as a real synced folder through the official
     * FilterCreateActivity pipeline (server request + local model + storage).
     * A full folder list surfaces as a server error toast from the pipeline.
     *
     * @return true when the request was queued; false when skipped/failed
     */
    public static void create(BaseFragment fragment, Preset p, Runnable onDone) {
        if (fragment == null || fragment.getMessagesController() == null) {
            return;
        }
        final MessagesController.DialogFilter filter = new MessagesController.DialogFilter();
        filter.id = 2;
        while (fragment.getMessagesController().dialogFiltersById.get(filter.id) != null) {
            filter.id++;
        }
        filter.name = MeeroStrings.s(p.titleKey);
        filter.color = p.color;
        FilterCreateActivity.saveFilterToServer(
                filter,
                p.flags,
                null,
                filter.name,
                new ArrayList<TLRPC.MessageEntity>(),
                false,
                p.color,
                new ArrayList<Long>(),
                new ArrayList<Long>(),
                new LongSparseIntArray(),
                true,   // creatingNew
                false,  // atBegin
                false,  // hasUserChanged
                false,  // resetUnreadCounter
                true,   // progress (spinner while the server round-trips)
                fragment,
                onDone);
    }
}
