package org.telegram.ui.Components;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjMediaLibrary;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.PhotoViewer;

/** A single playback route shared by library, catalog, versions and episode navigation. */
public final class TjMediaPlayback {
    private TjMediaPlayback() { }

    public static void open(BaseFragment host, TjMediaLibrary.Entry entry, long positionMs) {
        if (host.getParentActivity() == null || !entry.isAccountAvailable()) return;
        if (!PhotoViewer.getInstance().openTjMedia(host, entry.message, positionMs)) {
            host.showDialog(new AlertDialog.Builder(host.getParentActivity())
                    .setMessage(TjLocale.getString(R.string.TjMediaPlaybackError))
                    .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
        }
    }
}
