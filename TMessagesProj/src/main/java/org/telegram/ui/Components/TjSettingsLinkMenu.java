package org.telegram.ui.Components;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * The menu a long press on a TjGram setting opens: copy the link to that setting, or share it
 * straight into a chat. Opening the link walks to the setting and marks it - it changes nothing.
 */
public final class TjSettingsLinkMenu {

    private TjSettingsLinkMenu() {
    }

    /** Copy and share already added; the caller may add its own options before {@code show()}. */
    public static ItemOptions options(BaseFragment fragment, View anchor, String link) {
        return ItemOptions.makeOptions(fragment, anchor)
                .add(R.drawable.msg_copy, LocaleController.getString(R.string.CopyLink), () -> {
                    AndroidUtilities.addToClipboard(link);
                    BulletinFactory.of(fragment).createCopyLinkBulletin().show();
                })
                .add(R.drawable.msg_share, LocaleController.getString(R.string.ShareLink), () -> {
                    if (fragment.getParentActivity() == null) return;
                    fragment.showDialog(ShareAlert.createShareAlert(fragment.getParentActivity(), null, link, false, link, false));
                });
    }

    public static boolean show(BaseFragment fragment, View anchor, String link) {
        if (fragment == null || anchor == null || link == null) return false;
        options(fragment, anchor, link).show();
        return true;
    }
}
