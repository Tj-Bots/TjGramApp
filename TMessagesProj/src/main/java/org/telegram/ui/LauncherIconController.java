package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.drawable.tjgram_icon_bg, R.mipmap.ic_launcher_foreground, R.string.AppIconDefault),
        TJ_BLACK("TjBlackIcon", R.drawable.tj_icon_black_bg, R.mipmap.tj_icon_black_foreground, R.string.TjAppIconBlack),
        TJ_WHITE("TjWhiteIcon", R.drawable.tj_icon_white_bg, R.mipmap.tj_icon_white_foreground, R.string.TjAppIconWhite),
        TJ_MIDNIGHT("TjMidnightIcon", R.drawable.tj_icon_midnight_bg, R.mipmap.tj_icon_midnight_foreground, R.string.TjAppIconMidnight),
        TJ_LIGHT("TjLightIcon", R.drawable.tj_icon_light_bg, R.mipmap.tj_icon_light_foreground, R.string.TjAppIconLight),
        TJ_RED("TjRedIcon", R.drawable.tj_icon_red_bg, R.mipmap.tj_icon_red_foreground, R.string.TjAppIconRed),
        TJ_GREEN("TjGreenIcon", R.drawable.tj_icon_green_bg, R.mipmap.tj_icon_green_foreground, R.string.TjAppIconGreen),
        TJ_PURPLE("TjPurpleIcon", R.drawable.tj_icon_purple_bg, R.mipmap.tj_icon_purple_foreground, R.string.TjAppIconPurple),
        TJ_GOLD("TjGoldIcon", R.drawable.tj_icon_gold_bg, R.mipmap.tj_icon_gold_foreground, R.string.TjAppIconGold),
        TJ_PINK("TjPinkIcon", R.drawable.tj_icon_pink_bg, R.mipmap.tj_icon_pink_foreground, R.string.TjAppIconPink),
        TJ_ORANGE("TjOrangeIcon", R.drawable.tj_icon_orange_bg, R.mipmap.tj_icon_orange_foreground, R.string.TjAppIconOrange),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_6_foreground_sa, R.string.AppIconVintage),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua),
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium, true),
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_5_foreground_sa, R.string.AppIconTurbo, true),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox, true);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
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
            this.premium = premium;
        }
    }
}
