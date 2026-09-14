package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.messenger.tj.TjTmdb;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/**
 * Asking for the catalogue key, and checking it before anything is kept.
 *
 * A key that was pasted short fails silently everywhere it is used later - the screens simply come
 * back empty, which reads as a broken app rather than a mistyped key. So the key is tried against
 * TMDB first, and only a key that answered is stored. What was typed is handed back on failure so
 * it can be corrected rather than retyped.
 *
 * The key belongs to the person, not to the app: it is stored encrypted against the account that
 * entered it, and it is never written to a log.
 */
public final class TjTmdbKeyDialog {

    private static final String SIGN_UP = "https://www.themoviedb.org/settings/api";

    private TjTmdbKeyDialog() { }

    public interface Result {
        void saved();
    }

    public static void show(BaseFragment fragment, Result onSaved) {
        show(fragment, "", onSaved);
    }

    private static void show(BaseFragment fragment, String prefill, Result onSaved) {
        Context context = fragment.getParentActivity();
        if (context == null) return;
        final int account = fragment.getCurrentAccount();
        final long owner = UserConfig.getInstance(account).getClientUserId();

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);

        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setSingleLine(true);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        input.setHint(TjLocale.getString(R.string.TjMediaKeyHint));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setPadding(dp(20), dp(12), dp(20), dp(12));
        input.setText(prefill);
        if (!prefill.isEmpty()) input.setSelection(prefill.length());
        TjMediaInputStyle.apply(input, TjLocale.getString(R.string.TjMediaKeyHint));
        panel.addView(input, LayoutHelper.createLinear(-1, -2));

        TextView link = new TextView(context);
        link.setTextSize(14);
        link.setText(TjLocale.getString(R.string.TjWatchKeyWhere));
        link.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        link.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        link.setPadding(dp(22), dp(14), dp(22), dp(4));
        link.setOnClickListener(v -> Browser.openUrl(v.getContext(), SIGN_UP));
        panel.addView(link, LayoutHelper.createLinear(-1, -2));

        fragment.showDialog(new AlertDialog.Builder(context)
                .setTitle(TjLocale.getString(R.string.TjMediaMetadata))
                .setMessage(TjLocale.getString(R.string.TjMediaMetadataInfo) + "\n\n"
                        + TjLocale.getString(R.string.TjMediaAttribution))
                .setView(panel)
                .setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    input.setText("");
                    check(fragment, account, owner, value, onSaved);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    private static void check(BaseFragment fragment, int account, long owner, String value, Result onSaved) {
        Context context = fragment.getParentActivity();
        if (context == null) return;
        AlertDialog progress = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
        progress.show();
        TjTmdb.verify(value, error -> {
            try { progress.dismiss(); } catch (Exception ignore) { }
            if (fragment.getParentActivity() == null) return;
            if (error != TjTmdb.OK) {
                failed(fragment, account, owner, value, error, onSaved);
                return;
            }
            Utilities.globalQueue.postRunnable(() -> {
                boolean saved = UserConfig.getInstance(account).getClientUserId() == owner
                        && TjConfig.setMediaMetadataCredential(account, owner, value);
                AndroidUtilities.runOnUIThread(() -> {
                    if (fragment.getParentActivity() == null) return;
                    if (saved) {
                        if (onSaved != null) onSaved.saved();
                    } else {
                        failed(fragment, account, owner, value, TjTmdb.INVALID, onSaved);
                    }
                });
            });
        });
    }

    private static void failed(BaseFragment fragment, int account, long owner, String value, int error, Result onSaved) {
        Context context = fragment.getParentActivity();
        if (context == null) return;
        fragment.showDialog(new AlertDialog.Builder(context)
                .setTitle(TjLocale.getString(R.string.TjMediaMetadata))
                .setMessage(TjLocale.getString(error == TjTmdb.NETWORK ? R.string.TjWatchOffline
                        : error == TjTmdb.RATE_LIMIT ? R.string.TjWatchKeyBusy : R.string.TjWatchKeyRejected))
                .setPositiveButton(TjLocale.getString(R.string.TjWatchKeyRetry),
                        (dialog, which) -> show(fragment, value, onSaved))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }
}
