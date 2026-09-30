package org.telegram.ui;

import org.telegram.messenger.tj.TjSettingsLinks;
import org.telegram.ui.ActionBar.BaseFragment;

/** Opens existing UI only. Invoked by LaunchActivity after its login/passcode gates. */
final class TjSettingsLinkNavigator {
    private TjSettingsLinkNavigator() { }

    static BaseFragment create(TjSettingsLinks.Section section, String item, int account) {
        BaseFragment fragment;
        switch (section) {
            case GENERAL:
                fragment = TjSettingsActivity.forItem(item);
                break;
            case GHOST:
                fragment = TjPrivacySettingsActivity.forPage(TjPrivacySettingsActivity.PAGE_GHOST, item);
                break;
            case ARCHIVE:
                fragment = TjPrivacySettingsActivity.forPage(TjPrivacySettingsActivity.PAGE_ARCHIVE, item);
                break;
            case FILTERS:
                fragment = TjPrivacySettingsActivity.forPage(TjPrivacySettingsActivity.PAGE_FILTERS, item);
                break;
            case CUSTOMIZATION:
                fragment = TjPrivacySettingsActivity.forPage(TjPrivacySettingsActivity.PAGE_CUSTOMIZATION, item);
                break;
            case HOME:
                fragment = new TjSettingsHomeActivity();
                break;
            case OFFLINE_ACCOUNTS:
                fragment = new TjOfflineAccountsActivity();
                break;
            case WATCH_SETTINGS:
                fragment = new TjWatchSettingsActivity();
                break;
            case LOCAL_PREMIUM:
                fragment = TjPrivacySettingsActivity.forLocalPremium();
                break;
            case FOLDERS:
                fragment = new FiltersSetupActivity();
                break;
            case MEDIA_CENTER:
                fragment = new TjMediaCenterActivity();
                break;
            case MEDIA_METADATA:
                fragment = TjMediaCenterActivity.forSettingsLink(false);
                break;
            case MEDIA_LISTS:
                fragment = TjMediaCenterActivity.forSettingsLink(true);
                break;
            case ACCOUNT_LOG:
                fragment = new TjAccountLogSettingsActivity();
                break;
            case MENU_SHORTCUTS:
                fragment = new TjMenuShortcutsActivity();
                break;
            case WATCH:
                fragment = new TjWatchActivity();
                break;
            case PLAYER:
                fragment = item != null ? TjSettingsActivity.forItem(item) : TjSettingsActivity.forPlayerSettings();
                break;
            default:
                throw new IllegalArgumentException("Unknown TjGram settings section");
        }
        fragment.setCurrentAccount(account);
        return fragment;
    }
}
