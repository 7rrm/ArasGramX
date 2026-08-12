package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.BLUE);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        // MeeroX v210: on some ROMs (MIUI observed by the owner) resolving a
        // launcher alias can throw (component not found / security), which
        // crashed the whole chat-settings screen while SCROLLING the icon
        // row. The picker must never die for a cosmetic probe.
        try {
            Context ctx = ApplicationLoader.applicationContext;
            int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
            return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.BLUE;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            // MeeroX v210: never let one stubborn alias kill the rest of the
            // switch (same MIUI class of failure as above).
            try {
                pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.color.ic_launcher_nagram_background, R.drawable.ic_launcher_nagram_foreground, R.string.AppIconDefault),
        GOOGLE("GoogleIcon", R.mipmap.icon_background_google, R.drawable.ic_launcher_nagram_google_foreground, R.string.AppIconGoogle),
        COLORFUL("ColorfulIcon", R.mipmap.icon_background_colorful, R.drawable.ic_launcher_nagram_colorful_foreground, R.string.AppIconColorful),
        DARKGREEN("DarkGreenIcon", R.mipmap.icon_background_darkgreen, R.drawable.ic_launcher_nagram_darkgreen_foreground, R.string.AppIconDarkGreen),
        NEON("NeonIcon", R.mipmap.icon_background_neon, R.drawable.ic_launcher_nagram_neon_foreground, R.string.AppIconNeon),
        NIELLO("NielloIcon", R.drawable.ic_launcher_nagram_round_niello_background, R.drawable.ic_launcher_nagram_round_niello_foreground, R.string.AppIconNiello),
        BLUE("BlueIcon", R.color.nagram_block_round_background, R.drawable.ic_launcher_nagram_blue_foreground, R.string.AppIconBlue),
        DARKBLUE("DarkBlueIcon", R.color.nagram_dark_blue_background, R.drawable.ic_launcher_nagram_dark_blue_foreground, R.string.AppIconDarkBlue),
        BLURBLUE("BlurBlueIcon", R.drawable.ic_launcher_nagram_blur_blue_background, R.drawable.ic_launcher_nagram_blur_blue_foreground, R.string.AppIconBlurBlue),
        TELEGRAM("TelegramIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconTelegramOriginal),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_6_foreground_sa, R.string.AppIconVintage),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua),
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium),
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_5_foreground_sa, R.string.AppIconTurbo),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox),
        // MeeroX v171 - the four M designs he ordered; titles live in the
        // encrypted string vault (numeric vault id since v186 - even the
        // title key names leave no readable trace in DEX)
        MBOLD("MeeroMBoldIcon", R.color.meero_icon_dark_bg, R.drawable.meero_m_bold_foreground, 455),
        MMARKER("MeeroMMarkerIcon", R.color.meero_icon_blue_bg, R.drawable.meero_m_marker_foreground, 456),
        MTILE("MeeroMTileIcon", R.color.meero_icon_blue_bg, R.drawable.meero_m_tile_foreground, 457),
        MDUO("MeeroMDuoIcon", R.color.meero_icon_dark_bg, R.drawable.meero_m_duo_foreground, 458);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final String titleKey;
        public final int vaultTitle;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.titleKey = null;
            this.vaultTitle = -1;
            this.premium = premium;
        }

        /* v186 (batch 2D): M-icon titles by numeric vault id (long keeps the
         *  overload distinct from the R.string int form). */
        LauncherIcon(String key, int background, int foreground, long vaultTitle) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = 0;
            this.titleKey = null;
            this.vaultTitle = (int) vaultTitle;
            this.premium = false;
        }

        /** title of the picker row - vault strings for the new M icons,
         *  resource strings for legacy entries. */
        public String getTitle() {
            if (vaultTitle >= 0) {
                return tw.nekomimi.nekogram.MeeroStrings.s(vaultTitle);
            }
            return titleKey != null
                    ? tw.nekomimi.nekogram.MeeroStrings.s(titleKey)
                    : org.telegram.messenger.LocaleController.getString(title);
        }

        public boolean isNekoX() {
            return this == DEFAULT;
        }
    }
}
