package org.telegram.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Cells.DrawerAccountsCell;
import org.telegram.ui.Components.RecyclerListView;
import java.util.ArrayList;

/** Signed-out emulator only. No account creation, network requests or preference writes. */
public final class TjDrawerDragSmoke extends Instrumentation {
    private ArrayList<Integer> saved;
    private DrawerAccountsCell card;
    private int clicks;
    private int saves;
    private DrawerAccountsCell.Listener listener;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
                if (UserConfig.getInstance(i).isClientActivated()) throw new AssertionError("Signed-out emulator required");
            }
            ActivityMonitor monitor = addMonitor("org.telegram.ui.LaunchActivity", null, false);
            getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName(getTargetContext().getPackageName(), "org.telegram.ui.LaunchActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Activity activity = monitor.waitForActivityWithTimeout(10000);
            removeMonitor(monitor);
            if (activity == null) throw new AssertionError("No activity");
            for (int count : new int[]{6, 3}) {
                saved = null;
                clicks = 0;
                saves = 0;
                runOnMainSync(() -> {
                    RecyclerListView outer = new RecyclerListView(activity);
                    outer.setLayoutManager(new LinearLayoutManager(activity));
                    card = new DrawerAccountsCell(activity);
                    ArrayList<Integer> accounts = new ArrayList<>();
                    for (int i = 0; i < count; i++) accounts.add(i);
                    listener = new DrawerAccountsCell.Listener() {
                        public void onAccountClick(int account) { clicks++; }
                        public void onAccountPreview(int account) { clicks++; }
                        public void onAddAccount() { clicks++; }
                        public void onAccountsReordered(ArrayList<Integer> order) { saved = order; saves++; }
                    };
                    card.setAccounts(accounts, listener);
                    outer.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        public int getItemCount() { return 1; }
                        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                            card.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                            return new RecyclerView.ViewHolder(card) {};
                        }
                        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {}
                    });
                    activity.setContentView(outer);
                });
                SystemClock.sleep(500);
                int[] xy = new int[2];
                runOnMainSync(() -> card.getLocationOnScreen(xy));
                float x = xy[0] + AndroidUtilities.dp(32);
                float y = xy[1] + AndroidUtilities.dp(24);
                long down = SystemClock.uptimeMillis();
                pointer(down, MotionEvent.ACTION_DOWN, x, y);
                SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 150);
                for (int step = 1; step <= 20; step++) {
                    pointer(down, MotionEvent.ACTION_MOVE, x, y + AndroidUtilities.dp(96) * step / 20f);
                    SystemClock.sleep(25);
                }
                runOnMainSync(() -> {
                    ArrayList<Integer> previousOrder = new ArrayList<>();
                    for (int i = 0; i < count; i++) previousOrder.add(i);
                    card.setAccounts(previousOrder, listener);
                });
                SystemClock.sleep(150);
                pointer(down, MotionEvent.ACTION_UP, x, y + AndroidUtilities.dp(96));
                SystemClock.sleep(700);
                runOnMainSync(() -> {});
                if (saved == null || saved.get(0) == 0) {
                    throw new AssertionError("Drag failed for " + count + " accounts: " + saved);
                }
                if (saved.size() != count || new java.util.HashSet<>(saved).size() != count) {
                    throw new AssertionError("Reorder lost or duplicated an account");
                }
                if (clicks != 0) throw new AssertionError("Drag triggered click/preview");
                if (saves != 1) throw new AssertionError("Expected one order commit, got " + saves);
                if (count > 4) {
                    long scrollDown = SystemClock.uptimeMillis();
                    float scrollX = xy[0] + AndroidUtilities.dp(180);
                    float scrollY = xy[1] + AndroidUtilities.dp(160);
                    pointer(scrollDown, MotionEvent.ACTION_DOWN, scrollX, scrollY);
                    for (int step = 1; step <= 10; step++) {
                        pointer(scrollDown, MotionEvent.ACTION_MOVE, scrollX,
                                scrollY - AndroidUtilities.dp(110) * step / 10f);
                        SystemClock.sleep(15);
                    }
                    pointer(scrollDown, MotionEvent.ACTION_UP, scrollX, scrollY - AndroidUtilities.dp(110));
                    SystemClock.sleep(300);
                    int[] offset = new int[1];
                    runOnMainSync(() -> offset[0] = ((RecyclerView) card.getChildAt(0)).computeVerticalScrollOffset());
                    if (offset[0] <= 0) throw new AssertionError("Account list no longer scrolls after drag");
                    if (saves != 1 || clicks != 0) throw new AssertionError("Scroll triggered reorder/click");
                }
                Bundle stepResult = new Bundle();
                stepResult.putString("stream", "Accounts=" + count + " order=" + saved + "\n");
                sendStatus(0, stepResult);
            }
            result.putString("stream", "Drawer avatar drag passed for short and scrollable lists\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void pointer(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        sendPointerSync(event);
        event.recycle();
    }
}
