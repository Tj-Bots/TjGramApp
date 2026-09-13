package org.telegram.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.tj.TjMediaStore;
import org.telegram.ui.Cells.TjMediaCollectionCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import java.io.File;
import java.io.FileOutputStream;

/** Opt-in emulator-only layout smoke test. Never logs in, saves settings or sends messages. */
public final class TjMediaUiSmoke extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        boolean rtl = LocaleController.isRTL;
        try {
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
                if (UserConfig.getInstance(i).isClientActivated()) throw new IllegalStateException("Use a signed-out test emulator only");
            }
            ActivityMonitor monitor = addMonitor("org.telegram.ui.LaunchActivity", null, false);
            getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName(getTargetContext().getPackageName(), "org.telegram.ui.LaunchActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity activity = monitor.waitForActivityWithTimeout(10000);
            removeMonitor(monitor);
            if (activity == null) throw new IllegalStateException("LaunchActivity did not open");
            Thread.sleep(1000);
            for (boolean direction : new boolean[]{false, true}) {
                TjMediaCenterActivity center = new TjMediaCenterActivity();
                runOnMainSync(() -> {
                    LocaleController.isRTL = direction;
                    present(activity, center);
                });
                capture(direction ? "media-header-rtl" : "media-header-ltr");
                runOnMainSync(() -> {
                    try {
                        java.lang.reflect.Field searchField = TjMediaCenterActivity.class.getDeclaredField("search");
                        searchField.setAccessible(true);
                        android.widget.EditText search = (android.widget.EditText) searchField.get(center);
                        search.setText("test");
                        if (center.onBackPressed(false) || !"test".contentEquals(search.getText()))
                            throw new AssertionError("Back preview must not clear or leave search");
                        if (center.onBackPressed(true) || search.length() != 0)
                            throw new AssertionError("First Back must clear search");
                        if (!center.onBackPressed(true)) throw new AssertionError("Second Back must allow navigation");
                        java.lang.reflect.Method open = TjMediaCenterActivity.class.getDeclaredMethod("openMediaPage", int.class, int.class, String.class);
                        open.setAccessible(true); open.invoke(center, 3, -1, "");
                    } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                });
                capture(direction ? "media-child-rtl" : "media-child-ltr");
                runOnMainSync(() -> {
                    org.telegram.ui.ActionBar.BaseFragment child = center.getParentLayout().getLastFragment();
                    if (child == center || !(child instanceof TjMediaCenterActivity)) throw new AssertionError("Missing child screen");
                    try {
                        java.lang.reflect.Field tabs = TjMediaCenterActivity.class.getDeclaredField("mainTabs");
                        tabs.setAccessible(true);
                        if (((View) tabs.get(child)).getVisibility() != View.GONE) throw new AssertionError("Duplicate bottom navigation");
                    } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                    child.finishFragment(false);
                    if (center.getParentLayout().getLastFragment() != center) throw new AssertionError("Back lost media parent");
                });
                TjMediaCollectionEditActivity editor = new TjMediaCollectionEditActivity(0, null, "", null);
                runOnMainSync(() -> present(activity, editor));
                capture(direction ? "media-editor-rtl" : "media-editor-ltr");
                runOnMainSync(() -> {
                    ViewGroup root = (ViewGroup) editor.getFragmentView();
                    ViewGroup body = (ViewGroup) root.getChildAt(0);
                    ViewGroup card = (ViewGroup) body.getChildAt(0);
                    for (int i = 0; i < card.getChildCount(); i++) {
                        if (card.getChildAt(i) instanceof TextSettingsCell) card.getChildAt(i).performClick();
                    }
                });
                capture(direction ? "media-icons-rtl" : "media-icons-ltr");
                runOnMainSync(() -> {
                    android.widget.GridLayout icons = findIcons(editor.getVisibleDialog().getWindow().getDecorView());
                    if (icons == null || icons.getChildCount() == 0) throw new AssertionError("Empty icon picker");
                    String chosen = icons.getChildAt(0).getContentDescription().toString();
                    icons.getChildAt(0).performClick();
                    try {
                        java.lang.reflect.Field symbol = TjMediaCollectionEditActivity.class.getDeclaredField("symbol");
                        symbol.setAccessible(true);
                        if (!chosen.equals(symbol.get(editor))) throw new AssertionError("Icon selection did not update editor");
                    } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                });
                runOnMainSync(() -> {
                    try {
                        ViewGroup root = (ViewGroup) editor.getFragmentView();
                        LinearLayout body = (LinearLayout) root.getChildAt(0);
                        TjMediaCollectionCell row = new TjMediaCollectionCell(activity);
                        java.lang.reflect.Constructor<TjMediaStore.CollectionSummary> constructor =
                                TjMediaStore.CollectionSummary.class.getDeclaredConstructor(String.class, long.class, String.class, String.class);
                        constructor.setAccessible(true);
                        row.bind(constructor.newInstance(direction ? "הסרטים שלי" : "My movies", 12L,
                                direction ? "הפריט האחרון ברשימה" : "Latest item in this list", "⭐"), "");
                        row.setOptionsAction(() -> { });
                        body.addView(row, LayoutHelper.createLinear(-1, -2, 0, 20, 0, 0));
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
                capture(direction ? "media-list-row-rtl" : "media-list-row-ltr");
                runOnMainSync(() -> { editor.finishFragment(false); center.finishFragment(false); });
            }
            result.putString("result", "Layout screenshots captured; no live-account playback tested");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("error", android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        } finally { LocaleController.isRTL = rtl; }
    }
    private void present(Activity activity, org.telegram.ui.ActionBar.BaseFragment fragment) {
        try {
            Object opened = activity.getClass().getMethod("presentFragment", org.telegram.ui.ActionBar.BaseFragment.class, boolean.class, boolean.class)
                    .invoke(activity, fragment, false, true);
            if (Boolean.FALSE.equals(opened)) throw new IllegalStateException("Screen transition rejected");
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private android.widget.GridLayout findIcons(View view) {
        if (view instanceof android.widget.GridLayout) return (android.widget.GridLayout) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.GridLayout found = findIcons(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    private void capture(String name) throws Exception {
        Thread.sleep(800);
        Bitmap image = getUiAutomation().takeScreenshot();
        if (image == null) throw new IllegalStateException("Screenshot unavailable");
        File file = new File(getTargetContext().getExternalFilesDir(null), name + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
        image.recycle();
    }
}
