package org.telegram.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.tj.TjCommunity;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/** Branded, navigational home for TjGram settings. Detailed controls live in child screens. */
public class TjSettingsHomeActivity extends BaseFragment {

    private static final int TYPE_BRAND = 0;
    private static final int TYPE_HEADER = 1;
    private static final int TYPE_ROW = 2;

    private static final int GHOST = 1;
    private static final int PRIVACY = 2;
    private static final int FILTERS = 3;
    private static final int CUSTOMIZATION = 4;
    private static final int ADVANCED = 5;
    private static final int CHANNEL = 10;
    private static final int DISCUSSION = 11;

    private final ArrayList<Item> items = new ArrayList<>();

    private static class Item {
        final int type;
        final int id;
        final String text;
        final String value;
        final int icon;

        Item(int type, int id, String text, String value, int icon) {
            this.type = type;
            this.id = id;
            this.text = text;
            this.value = value;
            this.icon = icon;
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        buildItems();
        FrameLayout root = new FrameLayout(context);
        fragmentView = root;
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, AndroidUtilities.dp(32));
        list.setAdapter(new Adapter());
        list.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            switch (items.get(position).id) {
                case GHOST:
                    presentFragment(new TjPrivacySettingsActivity(true));
                    break;
                case PRIVACY:
                    presentFragment(new TjPrivacySettingsActivity(TjPrivacySettingsActivity.PAGE_ARCHIVE));
                    break;
                case FILTERS:
                    presentFragment(new TjPrivacySettingsActivity(TjPrivacySettingsActivity.PAGE_FILTERS));
                    break;
                case CUSTOMIZATION:
                    presentFragment(new TjPrivacySettingsActivity(TjPrivacySettingsActivity.PAGE_CUSTOMIZATION));
                    break;
                case ADVANCED:
                    presentFragment(new TjSettingsActivity());
                    break;
                case CHANNEL:
                    Browser.openUrl(getParentActivity(), TjCommunity.CHANNEL_URL);
                    break;
                case DISCUSSION:
                    Browser.openUrl(getParentActivity(), TjCommunity.DISCUSSION_URL);
                    break;
            }
        });
        root.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return root;
    }

    private void buildItems() {
        items.clear();
        items.add(new Item(TYPE_BRAND, 0, null, null, 0));
        items.add(new Item(TYPE_HEADER, 0, TjLocale.getString(R.string.TjCategories), null, 0));
        items.add(new Item(TYPE_ROW, GHOST, TjLocale.getString(R.string.TjGhostMode), null, R.drawable.tj_ghost));
        items.add(new Item(TYPE_ROW, PRIVACY, TjLocale.getString(R.string.TjArchiveInsights), null, R.drawable.msg_archive));
        items.add(new Item(TYPE_ROW, FILTERS, TjLocale.getString(R.string.TjMessageFilters), null, R.drawable.msg_folders));
        items.add(new Item(TYPE_ROW, CUSTOMIZATION, TjLocale.getString(R.string.TjCustomization), null, R.drawable.msg_palette));
        items.add(new Item(TYPE_ROW, ADVANCED, TjLocale.getString(R.string.TjAdvancedSettings), null, R.drawable.msg_settings));
        items.add(new Item(TYPE_HEADER, 0, TjLocale.getString(R.string.TjLinks), null, 0));
        items.add(new Item(TYPE_ROW, CHANNEL, TjLocale.getString(R.string.TjChannel), TjCommunity.CHANNEL_USERNAME, R.drawable.msg_channel));
        items.add(new Item(TYPE_ROW, DISCUSSION, TjLocale.getString(R.string.TjDiscussions), TjCommunity.DISCUSSION_USERNAME, R.drawable.msg_groups));
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_BRAND) {
                view = createBrandView(parent.getContext());
            } else if (viewType == TYPE_HEADER) {
                view = new HeaderCell(parent.getContext());
            } else {
                view = new TextSettingsCell(parent.getContext());
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            if (item.type == TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText(item.text);
            } else if (item.type == TYPE_ROW) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                boolean divider = position + 1 < items.size() && items.get(position + 1).type == TYPE_ROW;
                cell.setTextAndValue(item.text, item.value, divider);
                cell.setIcon(item.icon);
                applyCard(cell, position);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_ROW;
        }
    }

    private View createBrandView(Context context) {
        LinearLayout content = new LinearLayout(context);
        content.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        content.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(28));

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        try {
            icon.setImageDrawable(context.getApplicationInfo().loadIcon(context.getPackageManager()));
        } catch (Exception ignore) {
            icon.setImageResource(R.mipmap.ic_launcher);
        }
        content.addView(icon, LayoutHelper.createLinear(96, 96, Gravity.CENTER_HORIZONTAL));

        TextView name = new TextView(context);
        name.setText("TjGram");
        name.setTextSize(28);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setGravity(Gravity.CENTER);
        content.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 42, Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0));

        TextView version = new TextView(context);
        version.setText(getVersion(context));
        version.setTextSize(16);
        version.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        version.setGravity(Gravity.CENTER);
        content.addView(version, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_HORIZONTAL));
        return content;
    }

    private String getVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (Exception ignore) {
            return "";
        }
    }

    private void applyCard(View view, int position) {
        ViewGroup.LayoutParams current = view.getLayoutParams();
        RecyclerView.LayoutParams params = current instanceof RecyclerView.LayoutParams
                ? (RecyclerView.LayoutParams) current
                : new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(params);
        params.leftMargin = params.rightMargin = AndroidUtilities.dp(16);
        boolean top = position == 0 || items.get(position - 1).type != TYPE_ROW;
        boolean bottom = position + 1 == items.size() || items.get(position + 1).type != TYPE_ROW;
        view.setBackground(Theme.createRoundRectDrawable(
                top ? AndroidUtilities.dp(14) : 0,
                bottom ? AndroidUtilities.dp(14) : 0,
                Theme.getColor(Theme.key_windowBackgroundWhite)));
    }
}
