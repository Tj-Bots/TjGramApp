package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;

/** The swipe-back TjGram page embedded in a chat's three-dot menu. */
public final class TjChatMenuSubmenu {
    public final ActionBarPopupWindow.ActionBarPopupWindowLayout layout;
    private final LinearLayout buttonsLayout;
    private final Theme.ResourcesProvider resourcesProvider;

    public TjChatMenuSubmenu(Context context, PopupSwipeBackLayout swipeBackLayout,
                             Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourcesProvider);
        layout.setFitItems(true);

        ActionBarMenuSubItem back = ActionBarMenuItem.addItem(layout, R.drawable.msg_arrow_back,
                LocaleController.getString(R.string.Back), false, resourcesProvider);
        back.setOnClickListener(view -> {
            if (swipeBackLayout != null) {
                swipeBackLayout.closeForeground();
            }
        });

        FrameLayout gap = new FrameLayout(context);
        gap.setMinimumWidth(dp(220));
        gap.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator, resourcesProvider));
        layout.addView(gap);
        LinearLayout.LayoutParams gapParams = (LinearLayout.LayoutParams) gap.getLayoutParams();
        if (LocaleController.isRTL) {
            gapParams.gravity = Gravity.RIGHT;
        }
        gapParams.width = LayoutHelper.MATCH_PARENT;
        gapParams.height = dp(8);
        gap.setLayoutParams(gapParams);

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(buttonsLayout);
    }

    public ActionBarMenuSubItem addRow(int icon, CharSequence text, boolean checked, Runnable action) {
        ActionBarMenuSubItem item = ActionBarMenuItem.addItem(buttonsLayout, icon, text, true, resourcesProvider);
        item.setChecked(checked);
        item.setOnClickListener(view -> action.run());
        return item;
    }

    public ActionBarMenuSubItem addAction(int icon, CharSequence text, Runnable action) {
        ActionBarMenuSubItem item = ActionBarMenuItem.addItem(buttonsLayout, icon, text, false, resourcesProvider);
        item.setOnClickListener(view -> action.run());
        return item;
    }

    public void clear() {
        buttonsLayout.removeAllViews();
    }
}
