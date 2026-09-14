package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.messenger.tj.TjTmdb;
import org.telegram.messenger.tj.TjWatchHistory;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TjSettingsStyle;
import org.telegram.ui.Components.TjTmdbKeyDialog;

/**
 * What the Watch screen needs to know before it can show anything: the catalogue key, whose it is,
 * and what this device remembers about what was watched. Both are the person's own - the key is
 * stored encrypted against the account that entered it, and the history never leaves the device.
 */
public class TjWatchSettingsActivity extends BaseFragment {

    private LinearLayout body;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(TjLocale.getString(R.string.TjWatchSettings));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = scroll;

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, AndroidUtilities.dp(16));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));

        build();
        return fragmentView;
    }

    private void build() {
        Context context = body.getContext();
        body.removeAllViews();
        boolean hasKey = TjTmdb.available(currentAccount);

        addHeader(context, TjLocale.getString(R.string.TjMediaMetadata));

        TextSettingsCell key = new TextSettingsCell(context);
        key.setTextAndValue(TjLocale.getString(R.string.TjWatchKey),
                TjLocale.getString(hasKey ? R.string.TjWatchKeySet : R.string.TjWatchKeyMissing), true);
        key.setOnClickListener(v -> TjTmdbKeyDialog.show(this, this::build));
        addCard(key, true, false);

        TextSettingsCell where = new TextSettingsCell(context);
        where.setText(TjLocale.getString(R.string.TjWatchKeyWhere), hasKey);
        where.setOnClickListener(v -> Browser.openUrl(v.getContext(), "https://www.themoviedb.org/settings/api"));
        addCard(where, false, !hasKey);

        if (hasKey) {
            TextSettingsCell remove = new TextSettingsCell(context);
            remove.setText(TjLocale.getString(R.string.TjWatchKeyRemove), false);
            remove.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
            remove.setOnClickListener(v -> removeKey());
            addCard(remove, false, true);
        }

        addInfo(context, TjLocale.getString(R.string.TjMediaMetadataInfo) + "\n\n"
                + TjLocale.getString(R.string.TjMediaAttribution));

        addHeader(context, TjLocale.getString(R.string.TjWatchHistory));

        TextSettingsCell history = new TextSettingsCell(context);
        history.setText(TjLocale.getString(R.string.TjWatchHistory), true);
        history.setOnClickListener(v -> presentFragment(new TjWatchHistoryActivity()));
        addCard(history, true, false);

        TextSettingsCell clear = new TextSettingsCell(context);
        clear.setText(TjLocale.getString(R.string.TjWatchClearHistory), false);
        clear.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        clear.setOnClickListener(v -> askToClear());
        addCard(clear, false, true);

        addInfo(context, TjLocale.getString(R.string.TjWatchHistoryInfo));
    }

    /**
     * The shared card look, on a plain column rather than a list. The style helper hands back rows
     * carrying a list's layout parameters, which a column cannot lay out, so the background is kept
     * and the parameters are replaced with the column's own.
     */
    private void addCard(View view, boolean first, boolean last) {
        TjSettingsStyle.card(view, first, last);
        body.addView(view, LayoutHelper.createLinear(-1, -2,
                TjSettingsStyle.SIDE_MARGIN, 0, TjSettingsStyle.SIDE_MARGIN, 0));
    }

    private void addHeader(Context context, String title) {
        android.widget.TextView header = TjSettingsStyle.header(context);
        header.setText(title);
        body.addView(header, LayoutHelper.createLinear(-1, -2));
    }

    private void addInfo(Context context, String text) {
        TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
        info.setText(text);
        TjSettingsStyle.plain(info);
        body.addView(info, LayoutHelper.createLinear(-1, -2));
    }

    private void removeKey() {
        if (getParentActivity() == null) return;
        final int account = currentAccount;
        final long owner = UserConfig.getInstance(account).getClientUserId();
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(TjLocale.getString(R.string.TjWatchKeyRemove))
                .setMessage(TjLocale.getString(R.string.TjWatchKeyRemoveInfo))
                .setPositiveButton(TjLocale.getString(R.string.TjWatchForget), (dialog, which) ->
                        Utilities.globalQueue.postRunnable(() -> {
                            TjConfig.setMediaMetadataCredential(account, owner, "");
                            AndroidUtilities.runOnUIThread(() -> {
                                if (getParentActivity() != null) build();
                            });
                        }))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    private void askToClear() {
        if (getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(TjLocale.getString(R.string.TjWatchClearHistory))
                .setMessage(TjLocale.getString(R.string.TjWatchClearHistoryInfo))
                .setPositiveButton(TjLocale.getString(R.string.TjWatchForget),
                        (dialog, which) -> TjWatchHistory.clear())
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }
}
