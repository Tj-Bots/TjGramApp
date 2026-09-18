package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjAccountLog;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TjSettingsStyle;

/** Which of the things that can happen to an account are worth being told about. */
public class TjAccountLogSettingsActivity extends BaseFragment {

    private static final int[] TYPES = {
            TjAccountLog.TYPE_ADMIN_RIGHTS,
            TjAccountLog.TYPE_RESTRICTED,
            TjAccountLog.TYPE_MEMBERSHIP,
            TjAccountLog.TYPE_NEW_DEVICE,
    };

    private LinearLayout body;

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(TjLocale.getString(R.string.TjAccountLog));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
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
        boolean on = TjConfig.accountLog();

        TextCheckCell master = new TextCheckCell(context);
        master.setTextAndCheck(TjLocale.getString(R.string.TjAccountLog), on, false);
        master.setOnClickListener(v -> {
            boolean value = !TjConfig.accountLog();
            TjConfig.setAccountLog(value);
            build();
        });
        add(master, true, true);

        TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
        info.setText(TjLocale.getString(R.string.TjAccountLogInfo));
        TjSettingsStyle.plain(info);
        body.addView(info, LayoutHelper.createLinear(-1, -2));

        if (!on) {
            return;
        }

        android.widget.TextView header = TjSettingsStyle.header(context);
        header.setText(TjLocale.getString(R.string.TjAccountLogTypes));
        body.addView(header, LayoutHelper.createLinear(-1, -2));

        for (int i = 0; i < TYPES.length; i++) {
            final int type = TYPES[i];
            TextCheckCell cell = new TextCheckCell(context);
            cell.setTextAndCheck(name(type), TjConfig.accountLogType(type), i < TYPES.length - 1);
            cell.setOnClickListener(v -> {
                boolean value = !TjConfig.accountLogType(type);
                TjConfig.setAccountLogType(type, value);
                ((TextCheckCell) v).setChecked(value);
            });
            add(cell, i == 0, i == TYPES.length - 1);
        }

        TextInfoPrivacyCell note = new TextInfoPrivacyCell(context);
        note.setText(TjLocale.getString(R.string.TjAccountLogBlockedInfo));
        TjSettingsStyle.plain(note);
        body.addView(note, LayoutHelper.createLinear(-1, -2));
    }

    private void add(View view, boolean first, boolean last) {
        TjSettingsStyle.card(view, first, last);
        body.addView(view, LayoutHelper.createLinear(-1, -2,
                TjSettingsStyle.SIDE_MARGIN, 0, TjSettingsStyle.SIDE_MARGIN, 0));
    }

    private static String name(int type) {
        switch (type) {
            case TjAccountLog.TYPE_ADMIN_RIGHTS: return TjLocale.getString(R.string.TjAccountLogTypeAdmin);
            case TjAccountLog.TYPE_RESTRICTED: return TjLocale.getString(R.string.TjAccountLogTypeRestricted);
            case TjAccountLog.TYPE_MEMBERSHIP: return TjLocale.getString(R.string.TjAccountLogTypeMembership);
            default: return TjLocale.getString(R.string.TjAccountLogTypeDevice);
        }
    }
}
