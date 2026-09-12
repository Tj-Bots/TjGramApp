/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;


import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.ui.TjSettingsActivity;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.DrawerActionCell;
import org.telegram.ui.Cells.DrawerAddCell;
import org.telegram.ui.Cells.DrawerAccountsCell;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Cells.DrawerUserCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SideMenultItemAnimator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DrawerLayoutAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private DrawerLayoutContainer mDrawerLayoutContainer;
    private ArrayList<Item> items = new ArrayList<>(11);
    private ArrayList<Integer> accountNumbers = new ArrayList<>();
    private boolean accountsShown;
    public DrawerProfileCell profileCell;
    private SideMenultItemAnimator itemAnimator;
    private DrawerAccountsCell.Listener accountsListener;

    public DrawerLayoutAdapter(Context context, SideMenultItemAnimator animator, DrawerLayoutContainer drawerLayoutContainer) {
        mContext = context;
        mDrawerLayoutContainer = drawerLayoutContainer;
        itemAnimator = animator;
        accountsShown = UserConfig.getActivatedAccountsCount() > 1 && MessagesController.getGlobalMainSettings().getBoolean("accountsShown", true);
        Theme.createCommonDialogResources(context);
        resetItems();
    }

    private int getAccountRowsCount() {
        return 2; // One bounded account card followed by its divider.
    }

    public void setAccountsListener(DrawerAccountsCell.Listener listener) {
        accountsListener = listener;
    }

    @Override
    public int getItemCount() {
        int count = items.size() + 2;
        if (accountsShown) {
            count += getAccountRowsCount();
        }
        return count;
    }

    public void setAccountsShown(boolean value, boolean animated) {
        if (accountsShown == value || itemAnimator.isRunning()) {
            return;
        }
        accountsShown = value;
        if (profileCell != null) {
            profileCell.setAccountsShown(accountsShown, animated);
        }
        MessagesController.getGlobalMainSettings().edit().putBoolean("accountsShown", accountsShown).apply();
        if (animated) {
            itemAnimator.setShouldClipChildren(false);
            if (accountsShown) {
                notifyItemRangeInserted(2, getAccountRowsCount());
            } else {
                notifyItemRangeRemoved(2, getAccountRowsCount());
            }
        } else {
            notifyDataSetChanged();
        }
    }

    public boolean isAccountsShown() {
        return accountsShown;
    }

    private View.OnClickListener onPremiumDrawableClick;
    public void setOnPremiumDrawableClick(View.OnClickListener listener) {
        onPremiumDrawableClick = listener;
    }

    @Override
    public void notifyDataSetChanged() {
        resetItems();
        super.notifyDataSetChanged();
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int itemType = holder.getItemViewType();
        return itemType == 3;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = profileCell = new DrawerProfileCell(mContext, mDrawerLayoutContainer) {
                    @Override
                    protected void onPremiumClick() {
                        if (onPremiumDrawableClick != null) {
                            onPremiumDrawableClick.onClick(this);
                        }
                    }
                };
                break;
            case 2:
                view = new DividerCell(mContext);
                break;
            case 3:
                view = new DrawerActionCell(mContext);
                break;
            case 4:
                view = new DrawerUserCell(mContext);
                break;
            case 5:
                view = new DrawerAddCell(mContext);
                break;
            case 6:
                view = new DrawerAccountsCell(mContext);
                break;
            case 1:
            default:
                view = new EmptyCell(mContext, AndroidUtilities.dp(8));
                break;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case 0: {
                DrawerProfileCell profileCell = (DrawerProfileCell) holder.itemView;
                profileCell.setUser(MessagesController.getInstance(UserConfig.selectedAccount).getUser(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()), accountsShown);
                profileCell.updateColors();
                break;
            }
            case 3: {
                DrawerActionCell drawerActionCell = (DrawerActionCell) holder.itemView;
                position -= 2;
                if (accountsShown) {
                    position -= getAccountRowsCount();
                }
                items.get(position).bind(drawerActionCell);
                drawerActionCell.setPadding(0, 0, 0, 0);
                break;
            }
            case 4: {
                DrawerUserCell drawerUserCell = (DrawerUserCell) holder.itemView;
                drawerUserCell.setAccount(accountNumbers.get(position - 2));
                applyAccountCardStyle(drawerUserCell, position);
                break;
            }
            case 5: {
                applyAccountCardStyle(holder.itemView, position);
                break;
            }
            case 6: {
                DrawerAccountsCell cell = (DrawerAccountsCell) holder.itemView;
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) cell.getLayoutParams();
                params.leftMargin = params.rightMargin = AndroidUtilities.dp(12);
                params.topMargin = params.bottomMargin = AndroidUtilities.dp(8);
                cell.setLayoutParams(params);
                cell.setAccounts(accountNumbers, accountsListener);
                break;
            }
        }
    }

    private void applyAccountCardStyle(View view, int position) {
        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
        params.leftMargin = params.rightMargin = AndroidUtilities.dp(12);
        int first = getFirstAccountPosition();
        int last = 1 + accountNumbers.size();
        if (accountNumbers.size() < UserConfig.MAX_ACCOUNT_COUNT) {
            last++;
        }
        params.topMargin = position == first ? AndroidUtilities.dp(8) : 0;
        params.bottomMargin = position == last ? AndroidUtilities.dp(8) : 0;
        view.setLayoutParams(params);

        float radius = AndroidUtilities.dp(14);
        float top = position == first ? radius : 0;
        float bottom = position == last ? radius : 0;
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chats_menuItemText), 0.07f));
        background.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        view.setBackground(background);
    }

    @Override
    public int getItemViewType(int i) {
        if (i == 0) {
            return 0;
        } else if (i == 1) {
            return 1;
        }
        i -= 2;
        if (accountsShown) {
            if (i == 0) return 6;
            if (i == 1) return 2;
            i -= getAccountRowsCount();
        }
        if (i < 0 || i >= items.size() || items.get(i) == null) {
            return 2;
        }
        return 3;
    }

    public void swapElements(int fromIndex, int toIndex) {
        int idx1 = fromIndex - 2;
        int idx2 = toIndex - 2;
        if (idx1 < 0 || idx2 < 0 || idx1 >= accountNumbers.size() || idx2 >= accountNumbers.size()) {
            return;
        }
        Collections.swap(accountNumbers, idx1, idx2);
        saveAccountOrder();
        notifyItemMoved(fromIndex, toIndex);
    }

    public void setAccountOrder(ArrayList<Integer> order) {
        if (order == null || order.size() != accountNumbers.size()
                || !order.containsAll(accountNumbers)) {
            return;
        }
        accountNumbers.clear();
        accountNumbers.addAll(order);
        saveAccountOrder();
    }

    private void saveAccountOrder() {
        org.telegram.messenger.tj.TjAccountOrder.save(accountNumbers);
    }

    private void resetItems() {
        accountNumbers.clear();
        accountNumbers.addAll(org.telegram.messenger.tj.TjAccountOrder.activeAccounts());

        items.clear();
        if (!UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()) {
            return;
        }
        // This fork's resources no longer carry the seasonal (_ny / _14 / _hw) icon variants,
        // so the drawer always uses the plain set.
        int newGroupIcon = R.drawable.msg_groups;
        int newChannelIcon = R.drawable.msg_channel;
        int contactsIcon = R.drawable.msg_contacts;
        int callsIcon = R.drawable.msg_calls;
        int savedIcon = R.drawable.msg_saved;
        int settingsIcon = R.drawable.msg_settings_old;

        // TjGram menu. Keep the profile first, followed by the Ghost controls.
        items.add(new Item(100, TjLocale.getString(R.string.TjMyProfile), R.drawable.msg_openprofile));
        if (TjConfig.showGhostInDrawer()) {
            boolean ghostOn = TjSettingsActivity.isGhostModeEnabled();
            items.add(new Item(104, TjLocale.getString(ghostOn ? R.string.TjGhostModeOff : R.string.TjGhostModeOn), ghostOn ? R.drawable.tj_ghost_off : R.drawable.tj_ghost));
        }
        if (TjConfig.showKillInDrawer()) {
            items.add(new Item(105, TjLocale.getString(R.string.TjKillApp), R.drawable.msg_disable));
        }
        UserConfig me = UserConfig.getInstance(UserConfig.selectedAccount);
        boolean botAccount = me.getCurrentUser() != null && me.getCurrentUser().bot;
        if (!botAccount && me.isPremium()) {
            if (me.getEmojiStatus() != null) {
                items.add(new Item(15, LocaleController.getString(R.string.ChangeEmojiStatus), R.drawable.msg_smile_status));
            } else {
                items.add(new Item(15, LocaleController.getString(R.string.SetEmojiStatus), R.drawable.msg_smile_status));
            }
        }
        items.add(null);

        if (!botAccount) {
            items.add(new Item(2, LocaleController.getString(R.string.NewGroup), newGroupIcon));
            items.add(new Item(4, LocaleController.getString(R.string.NewChannel), newChannelIcon));
            items.add(null);
            items.add(new Item(6, LocaleController.getString(R.string.Contacts), contactsIcon));
            items.add(new Item(10, LocaleController.getString(R.string.Calls), callsIcon));
            items.add(new Item(11, LocaleController.getString(R.string.SavedMessages), savedIcon));
        }
        items.add(new Item(101, LocaleController.getString(R.string.Filters), R.drawable.msg_folders));
        items.add(new Item(102, TjLocale.getString(R.string.TjChatCounters), R.drawable.msg_stats));
        if (TjConfig.showMediaInDrawer()) {
            items.add(new Item(106, TjLocale.getString(R.string.TjMediaCenter), R.drawable.msg_media));
        }
        items.add(null);

        items.add(new Item(8, LocaleController.getString(R.string.Settings), settingsIcon));
        items.add(new Item(103, TjLocale.getString(R.string.TjSettings), R.drawable.msg_settings));
    }

    public int getId(int position) {
        position -= 2;
        if (accountsShown) {
            position -= getAccountRowsCount();
        }
        if (position < 0 || position >= items.size()) {
            return -1;
        }
        Item item = items.get(position);
        return item != null ? item.id : -1;
    }

    public int getFirstAccountPosition() {
        if (!accountsShown) {
            return RecyclerView.NO_POSITION;
        }
        return 2;
    }

    public int getLastAccountPosition() {
        if (!accountsShown) {
            return RecyclerView.NO_POSITION;
        }
        return 1 + accountNumbers.size();
    }

    private static class Item {
        public int icon;
        public String text;
        public int id;

        public Item(int id, String text, int icon) {
            this.icon = icon;
            this.id = id;
            this.text = text;
        }

        public void bind(DrawerActionCell actionCell) {
            actionCell.setTextAndIcon(id, text, icon);
        }
    }
}
