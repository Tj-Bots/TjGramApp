package org.telegram.ui;

import org.telegram.messenger.tj.TjSettingsLinks;
import org.telegram.ui.ActionBar.BaseFragment;

/** Opens existing UI only. Invoked by LaunchActivity after its login/passcode gates. */
final class TjSettingsLinkNavigator {
    private TjSettingsLinkNavigator() { }

    static BaseFragment create(TjSettingsLinks.Section section, int account) {
        BaseFragment fragment;
        switch (section) {
            case GHOST:
                fragment = new TjPrivacySettingsActivity(TjPrivacySettingsActivity.PAGE_GHOST);
                break;
            case ARCHIVE:
                fragment = new TjPrivacySettingsActivity(TjPrivacySettingsActivity.PAGE_ARCHIVE);
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
            case PLAYER:
                fragment = TjSettingsActivity.forPlayerSettings();
                break;
            default:
                throw new IllegalArgumentException("Unknown TjGram settings section");
        }
        fragment.setCurrentAccount(account);
        return fragment;
    }
}
