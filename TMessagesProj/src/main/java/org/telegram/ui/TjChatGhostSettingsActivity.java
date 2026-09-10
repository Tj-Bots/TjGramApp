package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TjSettingsStyle;

import java.util.ArrayList;

/** Per-chat overrides layered on top of the account-wide Ghost settings. */
public class TjChatGhostSettingsActivity extends BaseFragment {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_VALUE = 1;
    private static final int TYPE_INFO = 2;
    private static final int TYPE_ACTION = 3;

    private static final int MODE = 1;
    private static final int READS = 2;
    private static final int TYPING = 3;
    private static final int RESET = 4;

    private static final class Item {
        final int type;
        final int id;
        final int text;

        Item(int type, int id, int text) {
            this.type = type;
            this.id = id;
            this.text = text;
        }
    }

    private final long dialogId;
    private final ArrayList<Item> items = new ArrayList<>();
    private ListAdapter adapter;

    public TjChatGhostSettingsActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    private static String text(int resource) {
        return TjLocale.getString(resource);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(text(R.string.TjChatGhostSettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        buildItems();
        FrameLayout frame = new FrameLayout(context);
        fragmentView = frame;
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        adapter = new ListAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((view, position) -> onItemClick(items.get(position)));
        frame.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    private void buildItems() {
        items.clear();
        items.add(new Item(TYPE_HEADER, 0, R.string.TjChatGhostBehavior));
        items.add(new Item(TYPE_VALUE, MODE, R.string.TjChatGhostMode));
        items.add(new Item(TYPE_VALUE, READS, R.string.TjChatGhostReadReceipts));
        items.add(new Item(TYPE_VALUE, TYPING, R.string.TjChatGhostTypingStatus));
        items.add(new Item(TYPE_INFO, 0, R.string.TjChatGhostModeInfo));
        items.add(new Item(TYPE_INFO, 0, R.string.TjChatGhostOnlineInfo));
        items.add(new Item(TYPE_ACTION, RESET, R.string.TjChatGhostReset));
    }

    private void onItemClick(Item item) {
        if (item.id == MODE) {
            chooseState(MODE, R.string.TjChatGhostMode,
                    new int[]{R.string.TjChatGhostInherit, R.string.TjChatGhostOn, R.string.TjChatGhostOff});
        } else if (item.id == READS) {
            chooseState(READS, R.string.TjChatGhostReadReceipts,
                    new int[]{R.string.TjChatGhostInherit, R.string.TjChatGhostHide, R.string.TjChatGhostSend});
        } else if (item.id == TYPING) {
            chooseState(TYPING, R.string.TjChatGhostTypingStatus,
                    new int[]{R.string.TjChatGhostInherit, R.string.TjChatGhostHide, R.string.TjChatGhostSend});
        } else if (item.id == RESET) {
            confirmReset();
        }
    }

    private void chooseState(int id, int title, int[] labels) {
        if (getContext() == null) {
            return;
        }
        CharSequence[] choices = new CharSequence[labels.length];
        for (int i = 0; i < labels.length; i++) {
            choices[i] = text(labels[i]);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(text(title));
        builder.setItems(choices, (dialog, which) -> {
            int state = which == 0 ? TjConfig.CHAT_GHOST_INHERIT
                    : which == 1 ? TjConfig.CHAT_GHOST_ON : TjConfig.CHAT_GHOST_OFF;
            if (id == MODE) {
                TjConfig.setChatGhostState(currentAccount, dialogId, state);
            } else if (id == READS) {
                TjConfig.setChatReadState(currentAccount, dialogId, state);
            } else {
                TjConfig.setChatTypingState(currentAccount, dialogId, state);
            }
            adapter.notifyDataSetChanged();
        });
        showDialog(builder.create());
    }

    private void confirmReset() {
        if (getContext() == null || !TjConfig.hasChatGhostOverrides(currentAccount, dialogId)) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(text(R.string.TjChatGhostResetTitle));
        builder.setMessage(text(R.string.TjChatGhostResetText));
        builder.setNegativeButton(org.telegram.messenger.LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(org.telegram.messenger.LocaleController.getString(R.string.Reset), (dialog, which) -> {
            TjConfig.clearChatGhostOverrides(currentAccount, dialogId);
            adapter.notifyDataSetChanged();
        });
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        dialog.setOnShowListener(ignored -> {
            View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (button instanceof android.widget.TextView) {
                ((android.widget.TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        });
    }

    private String stateLabel(int state, boolean effective, boolean master) {
        final int choice;
        if (state == TjConfig.CHAT_GHOST_INHERIT) {
            choice = R.string.TjChatGhostInherit;
        } else if (state == TjConfig.CHAT_GHOST_ON) {
            choice = master ? R.string.TjChatGhostOn : R.string.TjChatGhostHide;
        } else {
            choice = master ? R.string.TjChatGhostOff : R.string.TjChatGhostSend;
        }
        int result = effective ? R.string.TjChatGhostEffectiveOn : R.string.TjChatGhostEffectiveOff;
        return text(choice) + " · " + text(result);
    }

    private String valueFor(int id) {
        if (id == MODE) {
            return stateLabel(TjConfig.chatGhostState(currentAccount, dialogId),
                    TjConfig.chatGhostEnabled(currentAccount, dialogId), true);
        } else if (id == READS) {
            return stateLabel(TjConfig.chatReadState(currentAccount, dialogId),
                    TjConfig.hideReads(currentAccount, dialogId), false);
        }
        return stateLabel(TjConfig.chatTypingState(currentAccount, dialogId),
                TjConfig.hideTyping(currentAccount, dialogId), false);
    }

    private final class ListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_HEADER) {
                view = TjSettingsStyle.header(parent.getContext());
            } else if (viewType == TYPE_INFO) {
                view = new TextInfoPrivacyCell(parent.getContext());
            } else {
                view = new TextSettingsCell(parent.getContext());
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            View view = holder.itemView;
            if (item.type == TYPE_HEADER) {
                ((android.widget.TextView) view).setText(text(item.text));
                TjSettingsStyle.plain(view);
            } else if (item.type == TYPE_INFO) {
                ((TextInfoPrivacyCell) view).setText(text(item.text));
                TjSettingsStyle.plain(view);
            } else if (item.type == TYPE_VALUE) {
                ((TextSettingsCell) view).setTextAndValue(text(item.text), valueFor(item.id), position < 3);
                TjSettingsStyle.card(view, position == 1, position == 3);
            } else {
                TextSettingsCell cell = (TextSettingsCell) view;
                cell.setText(text(item.text), false);
                cell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                cell.setEnabled(TjConfig.hasChatGhostOverrides(currentAccount, dialogId));
                TjSettingsStyle.card(view, true, true);
            }
        }
    }
}
