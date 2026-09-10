package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
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
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.tj.TjCommunity;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TjCategoryCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TjSettingsStyle;

import java.util.ArrayList;

/** The home of TjGram's own settings: what each screen holds, and how to get there. */
public class TjSettingsHomeActivity extends BaseFragment {

    private static final int TYPE_BRAND = 0;
    private static final int TYPE_HEADER = 1;
    private static final int TYPE_CATEGORY = 2;
    private static final int TYPE_INFO = 3;

    private static final int GHOST = 1;
    private static final int ARCHIVE = 2;
    private static final int FILTERS = 3;
    private static final int CUSTOMIZATION = 4;
    private static final int ADVANCED = 5;
    private static final int CHANNEL = 10;
    private static final int DISCUSSION = 11;

    private final ArrayList<Item> items = new ArrayList<>();
    private Adapter adapter;

    private static class Item {
        final int type;
        final int id;
        final CharSequence text;
        final CharSequence subtitle;
        final int icon;
        final int color;

        Item(int type, int id, CharSequence text, CharSequence subtitle, int icon, int color) {
            this.type = type;
            this.id = id;
            this.text = text;
            this.subtitle = subtitle;
            this.icon = icon;
            this.color = color;
        }

        static Item header(CharSequence text) {
            return new Item(TYPE_HEADER, 0, text, null, 0, 0);
        }

        static Item category(int id, int text, int subtitle, int icon, int color) {
            return new Item(TYPE_CATEGORY, id, TjLocale.getString(text), TjLocale.getString(subtitle), icon, color);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(TjLocale.getString(R.string.TjSettings));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setCastShadows(false);
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
        list.setPadding(0, 0, 0, dp(24));
        list.setVerticalScrollBarEnabled(false);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            switch (items.get(position).id) {
                case GHOST:
                    presentFragment(new TjPrivacySettingsActivity(TjPrivacySettingsActivity.PAGE_GHOST));
                    break;
                case ARCHIVE:
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

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            // The subtitles carry live state - how much of Ghost mode is on, and so on.
            buildItems();
            adapter.notifyDataSetChanged();
        }
    }

    private void buildItems() {
        items.clear();
        items.add(new Item(TYPE_BRAND, 0, null, null, 0, 0));
        items.add(Item.header(TjLocale.getString(R.string.TjCategories)));
        items.add(new Item(TYPE_CATEGORY, GHOST, TjLocale.getString(R.string.TjGhostMode),
                ghostSummary(), R.drawable.tj_ghost, TjSettingsStyle.GHOST_COLOR));
        items.add(Item.category(ARCHIVE, R.string.TjArchiveInsights, R.string.TjArchiveInsightsDesc,
                R.drawable.msg_archive, TjSettingsStyle.ARCHIVE_COLOR));
        items.add(Item.category(FILTERS, R.string.TjMessageFilters, R.string.TjMessageFiltersDesc,
                R.drawable.msg_folders, TjSettingsStyle.FILTERS_COLOR));
        items.add(Item.category(CUSTOMIZATION, R.string.TjCustomization, R.string.TjCustomizationDesc,
                R.drawable.msg_palette, TjSettingsStyle.APPEARANCE_COLOR));
        items.add(Item.category(ADVANCED, R.string.TjAdvancedSettings, R.string.TjAdvancedSettingsDesc,
                R.drawable.msg_settings, TjSettingsStyle.ADVANCED_COLOR));
        items.add(Item.header(TjLocale.getString(R.string.TjLinks)));
        items.add(new Item(TYPE_CATEGORY, CHANNEL, TjLocale.getString(R.string.TjChannel),
                TjCommunity.CHANNEL_USERNAME, R.drawable.msg_channel, TjSettingsStyle.CHANNEL_COLOR));
        items.add(new Item(TYPE_CATEGORY, DISCUSSION, TjLocale.getString(R.string.TjDiscussions),
                TjCommunity.DISCUSSION_USERNAME, R.drawable.msg_groups, TjSettingsStyle.DISCUSSION_COLOR));
        items.add(new Item(TYPE_INFO, 0, TjLocale.getString(R.string.TjSettingsFooter), null, 0, 0));
    }

    /** Ghost mode is the headline feature, so its row says how much of it is actually on. */
    private String ghostSummary() {
        if (!TjConfig.ghostEnabled()) {
            return TjLocale.getString(R.string.TjGhostModeDesc);
        }
        int enabled = 0;
        if (TjConfig.hideReads()) enabled++;
        if (TjConfig.hideStoryReads()) enabled++;
        if (TjConfig.hideOnline()) enabled++;
        if (TjConfig.hideTyping()) enabled++;
        if (TjConfig.forceOffline()) enabled++;
        return TjLocale.getString(R.string.TjGhostModeOnValue) + "  " + enabled + "/5";
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            View view;
            if (viewType == TYPE_BRAND) {
                view = new BrandView(context);
            } else if (viewType == TYPE_HEADER) {
                view = TjSettingsStyle.header(context);
            } else if (viewType == TYPE_INFO) {
                view = new TextInfoPrivacyCell(context);
            } else {
                view = new TjCategoryCell(context);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            if (item.type == TYPE_HEADER) {
                ((TextView) holder.itemView).setText(item.text);
            } else if (item.type == TYPE_INFO) {
                ((TextInfoPrivacyCell) holder.itemView).setText(item.text);
            } else if (item.type == TYPE_CATEGORY) {
                boolean first = items.get(position - 1).type != TYPE_CATEGORY;
                boolean last = position + 1 >= items.size() || items.get(position + 1).type != TYPE_CATEGORY;
                TjSettingsStyle.card(holder.itemView, first, last);
                ((TjCategoryCell) holder.itemView).set(item.icon, item.color, item.text, item.subtitle, !last);
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
            return holder.getItemViewType() == TYPE_CATEGORY;
        }
    }

    /** App icon, name and version, on a soft halo of the app's own accent colour. */
    private class BrandView extends FrameLayout {

        private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ImageView icon;
        private final LinearLayout content;

        BrandView(Context context) {
            super(context);
            setWillNotDraw(false);
            setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_HORIZONTAL);
            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL, 0, 18, 0, 22));

            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            try {
                icon.setImageDrawable(context.getApplicationInfo().loadIcon(context.getPackageManager()));
            } catch (Exception ignore) {
                icon.setImageResource(R.mipmap.ic_launcher);
            }
            content.addView(icon, LayoutHelper.createLinear(88, 88, Gravity.CENTER_HORIZONTAL));

            TextView name = new TextView(context);
            name.setText("TjGram");
            name.setTextSize(24);
            name.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setGravity(Gravity.CENTER);
            content.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

            TextView version = new TextView(context);
            version.setText(versionName(context));
            version.setTextSize(13);
            version.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            version.setGravity(Gravity.CENTER);
            version.setPadding(dp(10), dp(4), dp(10), dp(5));
            version.setBackground(TjSettingsStyle.badge(
                    Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader), 0.12f)));
            content.addView(version, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            haloPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader), 0.10f));
            canvas.drawCircle(getMeasuredWidth() / 2f,
                    content.getTop() + icon.getTop() + icon.getHeight() / 2f, dp(60), haloPaint);
        }
    }

    private static String versionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (Exception ignore) {
            return "";
        }
    }
}
