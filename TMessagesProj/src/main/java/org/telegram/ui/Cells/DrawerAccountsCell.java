package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

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

    /**
     * A plain RecyclerView on purpose. RecyclerListView installs its own item-touch listener that
     * runs ahead of everything added later, including the drag helper, and it also swallows the
     * press state of a clickable child - which is what made a hold here behave differently
     * depending on where in the row it started.
     */
    private final RecyclerView listView;
    private final DrawerAddCell addCell;
    private final AccountsAdapter adapter = new AccountsAdapter();
    private final ArrayList<Integer> accounts = new ArrayList<>();
    private final ItemTouchHelper itemTouchHelper;
    private final int touchSlop;

    private Listener listener;
    private boolean orderChanged;
    private boolean dragging;
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

        listView = new RecyclerView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int rows = Math.max(1, Math.min(MAX_VISIBLE_ROWS, accounts.size()));
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        AndroidUtilities.dp(ROW_HEIGHT_DP * rows), MeasureSpec.EXACTLY));
            }
        };
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter);
        // Visual only: never intercept a hold, drag, preview, or scroll gesture.
        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int range = parent.computeVerticalScrollRange();
                int extent = parent.computeVerticalScrollExtent();
                if (!canScrollList() || range <= extent || extent <= 0) return;
                float inset = AndroidUtilities.dp(8);
                float track = parent.getHeight() - inset * 2;
                if (track <= 0) return;
                float thumb = Math.min(track, Math.max(AndroidUtilities.dp(24), track * extent / range));
                float progress = Math.max(0f, Math.min(1f, (float) parent.computeVerticalScrollOffset() / (range - extent)));
                float top = inset + (track - thumb) * progress;
                float width = AndroidUtilities.dp(3);
                float left = LocaleController.isRTL ? AndroidUtilities.dp(3) : parent.getWidth() - AndroidUtilities.dp(6);
                paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chats_menuItemText), 0.12f));
                canvas.drawRoundRect(left, inset, left + width, inset + track, width, width, paint);
                paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chats_menuItemText), 0.5f));
                canvas.drawRoundRect(left, top, left + width, top + thumb, width, width, paint);
            }
        });
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

    private boolean canScrollList() {
        return accounts.size() > MAX_VISIBLE_ROWS;
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
                // Claimed only when there is something here to scroll; otherwise the drawer keeps
                // the gesture and can still be scrolled or swiped shut from on top of this card.
                setParentInterceptDisallowed(canScrollList());
                break;
            case MotionEvent.ACTION_MOVE:
                if (parentInterceptDisallowed && !dragging) {
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
            // The drag is started by hand from a hold on the avatar. Letting the helper detect the
            // long press as well would put two detectors on the same gesture, and the row's own
            // long press - the one that peeks at an account - would race them both.
            return false;
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
                dragging = true;
                setParentInterceptDisallowed(true);
                View view = viewHolder.itemView;
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
            dragging = false;
            View view = viewHolder.itemView;
            view.setTranslationZ(0);
            view.setBackground(null);
            if (orderChanged) {
                orderChanged = false;
                if (listener != null) {
                    listener.onAccountsReordered(new ArrayList<>(accounts));
                }
            }
        }
    }

    private class AccountsAdapter extends RecyclerView.Adapter<AccountsAdapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            Holder(View itemView) {
                super(itemView);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(new AccountRow(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
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
        private float pressX;
        private float pressY;

        @SuppressWarnings("ClickableViewAccessibility")
        AccountRow(Context context) {
            super(context);
            setOrientation(VERTICAL);
            userCell = new DrawerUserCell(context);
            addView(userCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, ROW_HEIGHT_DP));
            // The highlight has to sit on the view that receives the touch, otherwise the press
            // state never reaches the row and the feedback looks smaller than the row really is.
            userCell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            userCell.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    pressX = event.getX();
                    pressY = event.getY();
                }
                return false;
            });
            userCell.setOnClickListener(v -> {
                if (listener != null && boundAccount >= 0) {
                    listener.onAccountClick(boundAccount);
                }
            });
            userCell.setOnLongClickListener(v -> {
                if (listener == null || boundAccount < 0) {
                    return false;
                }
                if (userCell.isOnAvatar(pressX, pressY)) {
                    // Hold the picture to move the account.
                    RecyclerView.ViewHolder holder = listView.findContainingViewHolder(AccountRow.this);
                    if (holder == null) {
                        return false;
                    }
                    try {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    } catch (Exception ignore) {
                    }
                    itemTouchHelper.startDrag(holder);
                    return true;
                }
                if (boundAccount == UserConfig.selectedAccount) {
                    return false;
                }
                // Hold anywhere else to peek at that account.
                listener.onAccountPreview(boundAccount);
                return true;
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
