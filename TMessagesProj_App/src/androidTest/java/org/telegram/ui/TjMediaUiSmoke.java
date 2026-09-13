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
    private boolean dark;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args); dark = args != null && "true".equals(args.getString("dark")); start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        boolean rtl = LocaleController.isRTL;
        LocaleController.LocaleInfo originalLocale = LocaleController.getInstance().getCurrentLocaleInfo();
        org.telegram.ui.ActionBar.Theme.ThemeInfo originalTheme = org.telegram.ui.ActionBar.Theme.getCurrentTheme();
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
            if (dark) runOnMainSync(() -> {
                org.telegram.ui.ActionBar.Theme.ThemeInfo builtIn = org.telegram.ui.ActionBar.Theme.getTheme("Dark Blue");
                if (builtIn == null || builtIn.pathToFile != null) throw new AssertionError("Built-in dark theme unavailable");
                org.telegram.ui.ActionBar.Theme.applyTheme(builtIn, false, false);
            });
            for (boolean direction : new boolean[]{false, true}) {
                runOnMainSync(() -> {
                    LocaleController.LocaleInfo locale = new LocaleController.LocaleInfo();
                    locale.shortName = direction ? "he" : "en";
                    locale.name = locale.nameEnglish = direction ? "Hebrew" : "English";
                    // Built-in resources only: no remote pack, persisted preference or network-language update.
                    LocaleController.getInstance().applyLanguage(locale, false, true, false, true, 0, null);
                    LocaleController.isRTL = direction;
                });
                awaitUiQueue();
                TjMediaCenterActivity center = new TjMediaCenterActivity();
                runOnMainSync(() -> present(activity, center));
                capture(direction ? "media-header-rtl" : "media-header-ltr");
                int[] bounds = new int[4];
                runOnMainSync(() -> {
                    try {
                        java.lang.reflect.Field mask = TjMediaCenterActivity.class.getDeclaredField("enabledTypes");
                        mask.setAccessible(true); mask.setInt(center, (1 << 1) | (1 << 2));
                        java.lang.reflect.Method bind = TjMediaCenterActivity.class.getDeclaredMethod("bindTabs");
                        bind.setAccessible(true); bind.invoke(center);
                        java.lang.reflect.Field gridField = TjMediaCenterActivity.class.getDeclaredField("grid");
                        gridField.setAccessible(true);
                        View grid = (View) gridField.get(center);
                        int[] origin = new int[2]; grid.getLocationOnScreen(origin);
                        bounds[0] = origin[0]; bounds[1] = origin[1];
                        bounds[2] = grid.getWidth(); bounds[3] = grid.getHeight();
                    } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                });
                swipe(bounds[0] + bounds[2] * (direction ? .25f : .75f),
                        bounds[0] + bounds[2] * (direction ? .75f : .25f), bounds[1] + bounds[3] * .4f);
                awaitUiQueue();
                runOnMainSync(() -> {
                    try {
                        java.lang.reflect.Field type = TjMediaCenterActivity.class.getDeclaredField("mediaType");
                        type.setAccessible(true);
                        if (type.getInt(center) != 2) throw new AssertionError("Content swipe did not select videos");
                        if (center.getParentLayout().getLastFragment() != center)
                            throw new AssertionError("Content swipe left the media center");
                    } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                });
                capture(direction ? "media-swipe-rtl" : "media-swipe-ltr");
                // Return to All, then drag beyond that boundary: neither gesture may pop the fragment.
                for (int attempt = 0; attempt < 2; attempt++) {
                    swipe(bounds[0] + bounds[2] * (direction ? .75f : .25f),
                            bounds[0] + bounds[2] * (direction ? .25f : .75f), bounds[1] + bounds[3] * .4f);
                    awaitUiQueue();
                    runOnMainSync(() -> {
                        try {
                            java.lang.reflect.Field type = TjMediaCenterActivity.class.getDeclaredField("mediaType");
                            type.setAccessible(true);
                            if (type.getInt(center) != 0) throw new AssertionError("Reverse/boundary swipe changed the wrong type");
                            if (center.getParentLayout().getLastFragment() != center)
                                throw new AssertionError("Boundary swipe left the media center");
                        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                    });
                }
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
                TjMediaCollectionsActivity directory = new TjMediaCollectionsActivity(0, java.util.Collections.emptyList(), (account, name) -> { });
                runOnMainSync(() -> {
                    present(activity, directory);
                    try {
                        java.lang.reflect.Constructor<TjMediaStore.CollectionSummary> summaryConstructor =
                                TjMediaStore.CollectionSummary.class.getDeclaredConstructor(String.class, long.class, String.class, String.class);
                        summaryConstructor.setAccessible(true);
                        Object summary = summaryConstructor.newInstance(direction ? "הסדרות שלי" : "My series", 12L,
                                direction ? "פרקים שהוספתי לצפייה" : "Episodes saved for later", "star");
                        Class<?> rowClass = Class.forName("org.telegram.ui.TjMediaCollectionsActivity$ListRow");
                        java.lang.reflect.Constructor<?> rowConstructor = rowClass.getDeclaredConstructor(int.class, TjMediaStore.CollectionSummary.class);
                        rowConstructor.setAccessible(true);
                        java.lang.reflect.Field rowsField = TjMediaCollectionsActivity.class.getDeclaredField("rows");
                        rowsField.setAccessible(true);
                        ((java.util.List) rowsField.get(directory)).add(rowConstructor.newInstance(0, summary));
                        java.lang.reflect.Method update = TjMediaCollectionsActivity.class.getDeclaredMethod("updateDirectory");
                        update.setAccessible(true); update.invoke(directory);
                    } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                });
                capture(direction ? "media-directory-rtl" : "media-directory-ltr");
                org.telegram.messenger.MessageObject[] sampleVideo = new org.telegram.messenger.MessageObject[1];
                runOnMainSync(() -> {
                    org.telegram.tgnet.TLRPC.TL_message message = new org.telegram.tgnet.TLRPC.TL_message();
                    message.id = 1;
                    message.peer_id = new org.telegram.tgnet.TLRPC.TL_peerUser(); message.peer_id.user_id = 1;
                    message.message = "";
                    message.media = new org.telegram.tgnet.TLRPC.TL_messageMediaDocument();
                    message.media.document = new org.telegram.tgnet.TLRPC.TL_document();
                    message.media.document.id = 1; message.media.document.mime_type = "video/mp4";
                    message.media.document.size = 1500000000L;
                    org.telegram.tgnet.TLRPC.TL_documentAttributeVideo video = new org.telegram.tgnet.TLRPC.TL_documentAttributeVideo();
                    video.w = 1920; video.h = 1080; video.duration = 2640;
                    message.media.document.attributes.add(video);
                    org.telegram.messenger.MessageObject object = new org.telegram.messenger.MessageObject(0, message, false, false);
                    sampleVideo[0] = object;
                    org.telegram.ui.Cells.TjMediaRowCell row = new org.telegram.ui.Cells.TjMediaRowCell(activity);
                    row.bindVersion(object, "Example.Series.S02E16.1080p.WEB-DL.mkv",
                            direction ? "ערוץ הסדרות · משתמש לדוגמה" : "Series channel · Example uploader");
                    directory.showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(activity)
                            .setTitle(org.telegram.messenger.TjLocale.getString(org.telegram.messenger.R.string.TjMediaVersions))
                            .setView(row).setNegativeButton(LocaleController.getString(org.telegram.messenger.R.string.Close), null).create());
                });
                capture(direction ? "media-version-row-rtl" : "media-version-row-ltr");
                runOnMainSync(() -> directory.getVisibleDialog().dismiss());
                org.telegram.ui.ActionBar.BaseFragment detailsFixture = new org.telegram.ui.ActionBar.BaseFragment() {
                    private TjMediaDetailsActivity details;
                    @Override public View createView(android.content.Context context) {
                        TjMediaStore.Record record = new TjMediaStore.Record();
                        record.message = sampleVideo[0]; record.localSeries = true;
                        record.localTitle = direction ? "הסדרה המקומית שלי" : "My local series";
                        record.localKey = "local:smoke-series"; record.indexedSeason = 2; record.indexedEpisode = 16;
                        record.position = 30000; record.duration = 2640000;
                        details = new TjMediaDetailsActivity(new org.telegram.messenger.tj.TjMediaLibrary.Entry(0, 0, record.message),
                                record, () -> { }, java.util.Collections.emptyList(), java.util.Collections.emptyMap(), 0);
                        // Layout only: no synthetic login, no production onResume or network scope.
                        details.setParentLayout(getParentLayout());
                        actionBar.setTitle(org.telegram.messenger.TjLocale.getString(org.telegram.messenger.R.string.TjMediaDetails));
                        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
                        fragmentView = details.createView(context);
                        return fragmentView;
                    }
                    @Override public void onFragmentDestroy() {
                        if (details != null) details.onFragmentDestroy();
                        super.onFragmentDestroy();
                    }
                };
                runOnMainSync(() -> present(activity, detailsFixture));
                capture(direction ? "media-details-no-metadata-rtl" : "media-details-no-metadata-ltr");
                runOnMainSync(() -> detailsFixture.finishFragment(false));
                runOnMainSync(() -> {
                    try {
                        java.lang.reflect.Field listField = TjMediaCollectionsActivity.class.getDeclaredField("list");
                        listField.setAccessible(true);
                        ViewGroup list = (ViewGroup) listField.get(directory);
                        if (list.getChildCount() == 0 || list.getChildAt(0).getWidth() != list.getWidth())
                            throw new AssertionError("Directory item must occupy the real recycler width");
                        directory.finishFragment(false);
                    } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                });
                runOnMainSync(() -> { editor.finishFragment(false); center.finishFragment(false); });
            }
            result.putString("result", "Layout screenshots captured; no live-account playback tested");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("error", android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            runOnMainSync(() -> {
                if (originalLocale != null && originalLocale.getPathToFile() == null)
                    LocaleController.getInstance().applyLanguage(originalLocale, false, true, false, true, 0, null);
                LocaleController.isRTL = rtl;
                if (dark && originalTheme != null) org.telegram.ui.ActionBar.Theme.applyTheme(originalTheme, false, false);
            });
        }
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
        if (dark) name = "dark-" + name;
        Bundle progress = new Bundle(); progress.putString("stage", name); sendStatus(0, progress);
        Thread.sleep(800);
        Bitmap image = getUiAutomation().takeScreenshot();
        if (image == null) throw new IllegalStateException("Screenshot unavailable");
        File file = new File(getTargetContext().getExternalFilesDir(null), name + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
        image.recycle();
    }

    private void awaitUiQueue() throws InterruptedException {
        // Intro and image animations can keep IdleHandlers from running indefinitely.
        // Drain already-posted UI work, but fail explicitly if the main thread is blocked.
        java.util.concurrent.CountDownLatch reached = new java.util.concurrent.CountDownLatch(1);
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(() -> main.post(() -> reached.countDown()));
        if (!reached.await(10, java.util.concurrent.TimeUnit.SECONDS))
            throw new AssertionError("UI queue did not respond within 10 seconds");
    }

    private void swipe(float from, float to, float y) {
        long down = android.os.SystemClock.uptimeMillis();
        for (int step = 0; step <= 12; step++) {
            int action = step == 0 ? android.view.MotionEvent.ACTION_DOWN
                    : step == 12 ? android.view.MotionEvent.ACTION_UP : android.view.MotionEvent.ACTION_MOVE;
            android.view.MotionEvent event = android.view.MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(),
                    action, from + (to - from) * step / 12f, y, 0);
            event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            sendPointerSync(event); event.recycle();
            android.os.SystemClock.sleep(20);
        }
    }
}
