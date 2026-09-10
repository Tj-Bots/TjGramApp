package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TjSettingsStyle;

import java.util.ArrayList;

/** Picks which actions are lifted out of the message menu into its shortcut row. */
public class TjMenuShortcutsActivity extends BaseFragment {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_INFO = 2;

    private final ArrayList<Integer> selected = new ArrayList<>();
    private ListAdapter adapter;

    @Override
    public View createView(Context context) {
        selected.clear();
        selected.addAll(TjMessageMenu.selected());

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(TjLocale.getString(R.string.TjMenuShortcutsChoose));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frame = new FrameLayout(context);
        fragmentView = frame;
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        adapter = new ListAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((view, position) -> {
            int index = position - 1;
            if (index < 0 || index >= TjMessageMenu.ACTIONS.length) {
                return;
            }
            int option = TjMessageMenu.ACTIONS[index];
            if (selected.contains(option)) {
                selected.remove((Integer) option);
            } else {
                if (selected.size() >= TjMessageMenu.MAX_SHORTCUTS) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                            LocaleController.formatString(R.string.TjMenuShortcutsLimit,
                                    TjMessageMenu.MAX_SHORTCUTS)).show();
                    return;
                }
                // Appended, so the order of the row is the order they were picked in.
                selected.add(option);
            }
            TjMessageMenu.setSelected(selected);
            adapter.notifyDataSetChanged();
        });
        frame.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_HEADER) {
                view = TjSettingsStyle.header(parent.getContext());
            } else if (viewType == TYPE_INFO) {
                view = new TextInfoPrivacyCell(parent.getContext());
            } else {
                view = new TextCheckCell(parent.getContext());
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int viewType = getItemViewType(position);
            if (viewType == TYPE_HEADER) {
                TjSettingsStyle.plain(holder.itemView);
                ((TextView) holder.itemView).setText(TjLocale.getString(R.string.TjMenuShortcutsChoose));
            } else if (viewType == TYPE_INFO) {
                TjSettingsStyle.plain(holder.itemView);
                ((TextInfoPrivacyCell) holder.itemView).setText(TjLocale.getString(R.string.TjMenuShortcutsInfo));
            } else {
                int index = position - 1;
                int option = TjMessageMenu.ACTIONS[index];
                boolean last = index == TjMessageMenu.ACTIONS.length - 1;
                TjSettingsStyle.card(holder.itemView, index == 0, last);
                String label = TjMessageMenu.label(option);
                int order = selected.indexOf(option);
                if (order >= 0) {
                    // The number says where in the row it will sit.
                    label = (order + 1) + ".  " + label;
                }
                ((TextCheckCell) holder.itemView).setTextAndCheck(label, order >= 0, !last);
            }
        }

        @Override
        public int getItemCount() {
            return TjMessageMenu.ACTIONS.length + 2;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return TYPE_HEADER;
            }
            return position <= TjMessageMenu.ACTIONS.length ? TYPE_CHECK : TYPE_INFO;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_CHECK;
        }
    }
}
