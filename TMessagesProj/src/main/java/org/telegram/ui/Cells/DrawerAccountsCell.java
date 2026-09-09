package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/** A bounded, independently scrollable account card for the side drawer. */
public class DrawerAccountsCell extends LinearLayout {

    public interface Listener {
        void onAccountClick(int account);
        void onAccountPreview(int account);
        void onAddAccount();
        void onAccountsReordered(ArrayList<Integer> accounts);
    }

    /**
     * Accounts visible without scrolling. The pinned "add account" row sits below the list, so the
     * card is at most {@code MAX_VISIBLE_ROWS + 1} rows tall and the add button is always reachable.
     */
    private static final int MAX_VISIBLE_ROWS = 4;
    private static final int ROW_HEIGHT_DP = 48;

    private final RecyclerListView listView;
    private final DrawerAddCell addCell;
    private final AccountsAdapter adapter = new AccountsAdapter();
    private final ArrayList<Integer> accounts = new ArrayList<>();
    private final ItemTouchHelper itemTouchHelper;
    private final int touchSlop;

    private Listener listener;
    private boolean orderChanged;
    private int pendingPreviewAccount = -1;
    private float downX;
    private float downY;
    private boolean parentInterceptDisallowed;

    public DrawerAccountsCell(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipToOutline(true);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chats_menuItemText), 0.07f));
        background.setCornerRadius(AndroidUtilities.dp(14));
        setBackground(background);

        listView = new RecyclerListView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int rows = Math.max(1, Math.min(MAX_VISIBLE_ROWS, accounts.size()));
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        AndroidUtilities.dp(ROW_HEIGHT_DP * rows), MeasureSpec.EXACTLY));
            }
        };
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter);
        // A RecyclerView nested in the drawer's RecyclerView must not participate in nested
        // scrolling, otherwise the drawer consumes the gesture first and this list never moves.
        listView.setNestedScrollingEnabled(false);
        listView.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        addCell = new DrawerAddCell(context);
        addCell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
        addCell.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAddAccount();
            }
        });
        addView(addCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, ROW_HEIGHT_DP));

        itemTouchHelper = new ItemTouchHelper(new ReorderCallback());
        itemTouchHelper.attachToRecyclerView(listView);
    }

    public void setAccounts(ArrayList<Integer> value, Listener listener) {
        this.listener = listener;
        if (accounts.equals(value)) {
            // Rebinding the drawer must not restart an in-flight drag or reset the scroll position.
            return;
        }
        accounts.clear();
        accounts.addAll(value);
        addCell.setVisibility(accounts.size() < UserConfig.MAX_ACCOUNT_COUNT ? VISIBLE : GONE);
        adapter.notifyDataSetChanged();
        requestLayout();
    }

    private void setParentInterceptDisallowed(boolean disallowed) {
        if (parentInterceptDisallowed == disallowed) {
            return;
        }
        parentInterceptDisallowed = disallowed;
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowed);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                // Claim the gesture up front. Not just when this list can scroll: with a handful
                // of accounts the card does not scroll at all, and then the drawer's own list was
                // free to steal the drag the instant the finger moved - which is exactly when a
                // reorder is starting, so the row was let go every time.
                setParentInterceptDisallowed(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (parentInterceptDisallowed) {
                    float dx = Math.abs(event.getX() - downX);
                    float dy = Math.abs(event.getY() - downY);
                    if (dx > touchSlop && dx > dy * 1.5f) {
                        // A clearly horizontal gesture belongs to the drawer (swipe to close).
                        setParentInterceptDisallowed(false);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setParentInterceptDisallowed(false);
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    private class ReorderCallback extends ItemTouchHelper.Callback {
        @Override
        public boolean isLongPressDragEnabled() {
            // ItemTouchHelper detects the long press itself, from the RecyclerView's touch
            // stream. Starting the drag by hand from a postDelayed runnable meant the helper
            // never owned the gesture, so the row was dropped the moment the finger moved.
            return true;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
            int fromPosition = from.getAdapterPosition();
            int toPosition = to.getAdapterPosition();
            if (fromPosition < 0 || toPosition < 0
                    || fromPosition >= accounts.size() || toPosition >= accounts.size()) {
                return false;
            }
            accounts.add(toPosition, accounts.remove(fromPosition));
            adapter.notifyItemMoved(fromPosition, toPosition);
            orderChanged = true;
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                View view = viewHolder.itemView;
                setParentInterceptDisallowed(true);
                int account = view instanceof AccountRow ? ((AccountRow) view).boundAccount : -1;
                pendingPreviewAccount = account == UserConfig.selectedAccount ? -1 : account;
                try {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } catch (Exception ignore) {
                }
                view.setTranslationZ(AndroidUtilities.dp(8));
                // Deliberately no scaling: it made the row look a different size from the slot it
                // occupies, so the neighbours appeared to jump around while it was being moved.
                view.setBackground(new ColorDrawable(
                        Theme.multAlpha(Theme.getColor(Theme.key_chats_menuItemText), 0.10f)));
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            View view = viewHolder.itemView;
            view.setTranslationZ(0);
            view.setBackground(null);
            int previewAccount = pendingPreviewAccount;
            pendingPreviewAccount = -1;
            if (orderChanged) {
                orderChanged = false;
                if (listener != null) {
                    listener.onAccountsReordered(new ArrayList<>(accounts));
                }
            } else if (previewAccount >= 0 && listener != null) {
                // The hold never moved anything, so it was a request to peek at that account.
                listener.onAccountPreview(previewAccount);
            }
        }
    }

    private class AccountsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new AccountRow(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AccountRow row = (AccountRow) holder.itemView;
            int account = accounts.get(position);
            row.userCell.setAccount(account);
            row.bind(account);
        }

        @Override
        public int getItemCount() {
            return accounts.size();
        }
    }

    private class AccountRow extends LinearLayout {
        final DrawerUserCell userCell;
        private int boundAccount = -1;

        AccountRow(Context context) {
            super(context);
            setOrientation(VERTICAL);
            userCell = new DrawerUserCell(context);
            userCell.setReorderHandleVisible(false);
            addView(userCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, ROW_HEIGHT_DP));
            // The highlight has to sit on the view that receives the touch, otherwise the press
            // state never reaches the row and the feedback looks smaller than the row really is.
            userCell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            userCell.setOnClickListener(v -> {
                if (listener != null && boundAccount >= 0) {
                    listener.onAccountClick(boundAccount);
                }
            });
        }

        void bind(int account) {
            boundAccount = account;
        }


        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(ROW_HEIGHT_DP), MeasureSpec.EXACTLY));
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
            info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
        }
    }
}
