package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;

import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AppGlobalConfig;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ColoredImageSpan;

import java.util.Locale;
import java.util.Objects;

import tw.nekomimi.nekogram.ui.icons.IconsResources;
import xyz.nextalone.nagram.NaConfig;

public class TimeStringHelper {
    public static SpannableStringBuilder deletedSpan;
    public static Drawable deletedDrawable;
    // MeeroX v218: palette for the deleted-mark trash icon; index 0 keeps the
    // long-standing behaviour (icon tinted with the time text paint).
    public static final int[] MEERO_TRASH_COLORS = {
        0,          // 0 = default (follow the time text color)
        0xFFFF3B30, // 1 red
        0xFFFF6D3D, // 2 orange
        0xFFFF2D92, // 3 pink
        0xFFE040FB, // 4 fuchsia
        0xFF9C27B0, // 5 purple
        0xFF536DFE, // 6 indigo
        0xFF448AFF  // 7 blue
    };
    private static int meeroTrashColorApplied = -1;
    public static SpannableStringBuilder editedSpan;
    public static Drawable editedDrawable;
    public static SpannableStringBuilder channelLabelSpan;
    public static Drawable channelLabelDrawable;
    public static SpannableStringBuilder translatedSpan;
    public static Drawable translatedDrawable;
    public static Drawable bookmarkDrawable;
    public static SpannableStringBuilder arrowSpan;
    public static Drawable arrowDrawable;
    public static SpannableStringBuilder forwardsSpan;
    public static Drawable forwardsDrawable;
    public ChatActivity.ThemeDelegate themeDelegate;

    public static CharSequence createBookmarkedString(MessageObject messageObject, int senderNameColor) {
        createSpan();
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder
                .append(messageObject.messageOwner.post_author != null ? " " : "")
                .append(createBookmarkSpan(senderNameColor))
                .append("  ")
                .append(LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        return spannableStringBuilder;
    }

    public static CharSequence createDeletedString(MessageObject messageObject, boolean isEdited, boolean isTranslated, int senderNameColor) {
        return createDeletedString(messageObject, isEdited, isTranslated, false, senderNameColor);
    }

    public static CharSequence createDeletedString(MessageObject messageObject, boolean isEdited, boolean isTranslated, boolean isBookmarked, int senderNameColor) {
        return createDeletedString(messageObject, isEdited, isTranslated, isBookmarked, senderNameColor, messageObject.messageOwner.edit_date);
    }

    public static CharSequence createDeletedString(MessageObject messageObject, boolean isEdited, boolean isTranslated, boolean isBookmarked, int senderNameColor, int editDate) {
        String editedStr = NaConfig.INSTANCE.getCustomEditedMessage().String();
        String editedStrFin = editedStr.isEmpty() ? getString(R.string.EditedMessage) : editedStr;
        String deletedStr = NaConfig.INSTANCE.getCustomDeletedMark().String();
        String deletedStrFin = deletedStr.isEmpty() ? getString(R.string.DeletedMessage) : deletedStr;
        boolean primaryEditedDate = isEdited && AppGlobalConfig.getInstance(messageObject.currentAccount).messagePrimaryEditedDate.get();

        createSpan();
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

        spannableStringBuilder
                .append(messageObject.messageOwner.post_author != null ? " " : "")
                .append(NaConfig.INSTANCE.getUseDeletedIcon().Bool() ? deletedSpan : deletedStrFin);
        if (isEdited) {
            spannableStringBuilder
                    .append("  ")
                    .append(primaryEditedDate ? LocaleController.formatPmEditedDate(editDate) : (NaConfig.INSTANCE.getUseEditedIcon().Bool() ? editedSpan : editedStrFin));
        }
        if (isTranslated) {
            spannableStringBuilder
                    .append("  ")
                    .append(createTranslatedString(messageObject, true, isBookmarked, senderNameColor));
        } else if (isBookmarked) {
            spannableStringBuilder
                    .append("  ")
                    .append(createBookmarkSpan(senderNameColor));
        }
        if (!primaryEditedDate) {
            spannableStringBuilder
                    .append("  ")
                    .append(LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        }
        return spannableStringBuilder;
    }

    public static CharSequence createEditedString(MessageObject messageObject, boolean isTranslated, int senderNameColor) {
        return createEditedString(messageObject, isTranslated, false, senderNameColor);
    }

    public static CharSequence createEditedString(MessageObject messageObject, boolean isTranslated, boolean isBookmarked, int senderNameColor) {
        return createEditedString(messageObject, isTranslated, isBookmarked, senderNameColor, messageObject.messageOwner.edit_date);
    }

    public static CharSequence createEditedString(MessageObject messageObject, boolean isTranslated, boolean isBookmarked, int senderNameColor, int editDate) {
        String editedStr = NaConfig.INSTANCE.getCustomEditedMessage().String();
        String editedStrFin = editedStr.isEmpty() ? getString(R.string.EditedMessage) : editedStr;
        boolean primaryEditedDate = AppGlobalConfig.getInstance(messageObject.currentAccount).messagePrimaryEditedDate.get();

        createSpan();
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

        spannableStringBuilder
                .append(messageObject.messageOwner.post_author != null ? " " : "")
                .append(primaryEditedDate ? LocaleController.formatPmEditedDate(editDate) : (NaConfig.INSTANCE.getUseEditedIcon().Bool() ? editedSpan : editedStrFin));
        if (isTranslated) {
            spannableStringBuilder
                    .append("  ")
                    .append(createTranslatedString(messageObject, true, isBookmarked, senderNameColor));
        } else if (isBookmarked) {
            spannableStringBuilder
                    .append("  ")
                    .append(createBookmarkSpan(senderNameColor));
        }
        if (!primaryEditedDate) {
            spannableStringBuilder
                    .append("  ")
                    .append(LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        }
        return spannableStringBuilder;
    }

    public static CharSequence createTranslatedString(MessageObject messageObject, boolean internal, int senderNameColor) {
        return createTranslatedString(messageObject, internal, false, senderNameColor);
    }

    public static CharSequence createTranslatedString(MessageObject messageObject, boolean internal, boolean isBookmarked, int senderNameColor) {
        createSpan();
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

        if (canShowLanguage(messageObject)) {
            spannableStringBuilder
                    .append(Locale.forLanguageTag(messageObject.messageOwner.originalLanguage).getDisplayName())
                    .append(" ")
                    .append(arrowSpan)
                    .append(" ")
                    .append(Locale.forLanguageTag(messageObject.messageOwner.translatedToLanguage).getDisplayName());
            if (isBookmarked) {
                spannableStringBuilder
                        .append("  ")
                        .append(createBookmarkSpan(senderNameColor));
            }
            spannableStringBuilder
                    .append(internal ? "" : "  ")
                    .append(internal ? "" : LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        } else {
            spannableStringBuilder
                    .append(internal || messageObject.messageOwner.post_author == null ? "" : " ")
                    .append(translatedSpan);
            if (isBookmarked) {
                spannableStringBuilder
                        .append("  ")
                        .append(createBookmarkSpan(senderNameColor));
            }
            spannableStringBuilder
                    .append(internal ? "" : "  ")
                    .append(internal ? "" : LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        }
        return spannableStringBuilder;
    }

    private static boolean canShowLanguage(MessageObject messageObject) {
        String fromCode = messageObject.messageOwner.originalLanguage;
        String toCode = messageObject.messageOwner.translatedToLanguage;
        if (TextUtils.isEmpty(fromCode) || TextUtils.isEmpty(toCode)) {
            return false;
        }
        if (messageObject.messageOwner.originalLanguage.equals(TranslateController.UNKNOWN_LANGUAGE)) {
            return false;
        }
        if (messageObject.messageOwner.post_author != null) {
            return false;
        }
        return MessagesController.getInstance(UserConfig.selectedAccount).getTranslateController().isManualTranslated(messageObject);
    }

    private static void createSpan() {
        if (editedDrawable == null) {
            editedDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_edit_solar)).mutate();
        }
        if (editedSpan == null) {
            editedSpan = new SpannableStringBuilder("\u200B");
            editedSpan.setSpan(new ColoredImageSpan(editedDrawable, true), 0, 1, 0);
        }

        // MeeroX v218: the trash spans are process-cached, so rebuild them
        // whenever the owner-picked color index changes.
        final int meeroTrashIdx = NaConfig.INSTANCE.getDeletedTrashColor().Int();
        if (deletedDrawable == null || meeroTrashColorApplied != meeroTrashIdx) {
            deletedDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_delete_solar)).mutate();
            deletedSpan = null;
            meeroTrashColorApplied = meeroTrashIdx;
        }
        if (deletedSpan == null) {
            deletedSpan = new SpannableStringBuilder("\u200B");
            ColoredImageSpan meeroTrashSpan = new ColoredImageSpan(deletedDrawable, true);
            if (meeroTrashIdx > 0 && meeroTrashIdx < MEERO_TRASH_COLORS.length) {
                // a fixed override wins over the time-paint tint inside the span
                meeroTrashSpan.setOverrideColor(MEERO_TRASH_COLORS[meeroTrashIdx]);
            }
            deletedSpan.setSpan(meeroTrashSpan, 0, 1, 0);
        }

        if (translatedDrawable == null) {
            if (NaConfig.INSTANCE.getIconReplacements().Int() == IconsResources.ICON_REPLACE_SOLAR) {
                translatedDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_translate_solar_12)).mutate();
            } else {
                translatedDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_translate_12)).mutate();
            }
        }
        if (translatedSpan == null) {
            translatedSpan = new SpannableStringBuilder("\u200B");
            translatedSpan.setSpan(new ColoredImageSpan(translatedDrawable, true), 0, 1, 0);
        }

        if (bookmarkDrawable == null) {
            bookmarkDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_fave_solar_12)).mutate();
        }

        if (arrowDrawable == null) {
            arrowDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.search_arrow));
        }
        if (arrowSpan == null) {
            arrowSpan = new SpannableStringBuilder("\u200B");
            arrowSpan.setSpan(new ColoredImageSpan(arrowDrawable, true), 0, 1, 0);
        }

        if (forwardsDrawable == null) {
            forwardsDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.forwards_solar)).mutate();
        }
        if (forwardsSpan == null) {
            forwardsSpan = new SpannableStringBuilder("\u200B");
            forwardsSpan.setSpan(new ColoredImageSpan(forwardsDrawable, true), 0, 1, 0);
        }
    }

    private static SpannableStringBuilder createBookmarkSpan(int senderNameColor) {
        if (bookmarkDrawable == null) {
            bookmarkDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_fave_solar_12)).mutate();
        }
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder("\u200B");
        ColoredImageSpan imageSpan = new ColoredImageSpan(bookmarkDrawable, true);
        imageSpan.setTopOffset(-1);
        imageSpan.setOverrideColor(senderNameColor);
        spannableStringBuilder.setSpan(imageSpan, 0, 1, 0);
        return spannableStringBuilder;
    }

    public static SpannableStringBuilder getChannelLabelSpan() {
        if (channelLabelDrawable == null) {
            channelLabelDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.channel_label_solar)).mutate();
        }
        if (channelLabelSpan == null) {
            channelLabelSpan = new SpannableStringBuilder("\u200B");
            channelLabelSpan.setSpan(new ColoredImageSpan(channelLabelDrawable, true), 0, 1, 0);
        }
        return channelLabelSpan;
    }

}
