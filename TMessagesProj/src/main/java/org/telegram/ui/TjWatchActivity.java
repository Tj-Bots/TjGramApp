package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.messenger.tj.TjMediaTitle;
import org.telegram.messenger.tj.TjTmdb;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;

/**
 * Browsing what there is to watch, which starts with the catalogue of everything rather than with
 * the files in a chat. A name is searched against TMDB, the answers come back as the titles a
 * person would recognise, and only after one is chosen does anything go looking for a file.
 */
public class TjWatchActivity extends BaseFragment {

    /** One row of results. */
    public static final class Item {
        public final long id;
        public final boolean series;
        public final String name, poster, date;
        public final double rating;

        Item(JSONObject object, boolean series) {
            this.series = series;
            id = object.optLong("id");
            name = object.optString(series ? "name" : "title", "");
            poster = object.optString("poster_path", "");
            date = object.optString(series ? "first_air_date" : "release_date", "");
            rating = object.optDouble("vote_average", 0);
        }

        String year() { return date != null && date.length() >= 4 ? date.substring(0, 4) : ""; }
    }

    private final ArrayList<Item> items = new ArrayList<>();
    private final TjTmdb series = new TjTmdb();
    private final TjTmdb movies = new TjTmdb();

    private FrameLayout root;
    private RecyclerListView listView;
    private Adapter adapter;
    private EditTextBoldCursor search;
    private TextView status;
    private View gate;
    private String query = "";
    private int pending;
    private final Runnable searchRunnable = this::reload;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(TjLocale.getString(R.string.TjWatchTitle));
        actionBar.setCastShadows(false);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, LayoutHelper.createFrame(-1, -1));

        content.addView(searchField(context), LayoutHelper.createLinear(-1, 44, 12, 10, 12, 0));

        status = new TextView(context);
        status.setTextSize(14);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(28), dp(28), dp(28), dp(12));
        content.addView(status, LayoutHelper.createLinear(-1, -2));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new GridLayoutManager(context, 3));
        listView.setClipToPadding(false);
        listView.setPadding(dp(6), dp(6), dp(6), dp(24));
        listView.setVerticalScrollBarEnabled(false);
        adapter = new Adapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) return;
            Item item = items.get(position);
            presentFragment(new TjTitleActivity(item.id, item.series, item.name));
        });
        content.addView(listView, LayoutHelper.createLinear(-1, -1));

        if (!TjTmdb.available(currentAccount)) {
            showGate(context);
        } else {
            trending();
        }
        return fragmentView;
    }

    /**
     * Without a key of their own there is no catalogue to browse, so the screen says so and offers
     * the one thing that would change it rather than opening on an empty grid.
     */
    private void showGate(Context context) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(32), dp(24), dp(32), dp(24));

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_played);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
        panel.addView(icon, LayoutHelper.createLinear(56, 56, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        TextView text = new TextView(context);
        text.setTextSize(15);
        text.setGravity(Gravity.CENTER);
        text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        text.setText(TjLocale.getString(R.string.TjWatchNeedsKey));
        panel.addView(text, LayoutHelper.createLinear(-1, -2));

        TextView button = new TextView(context);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(20),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        button.setText(TjLocale.getString(R.string.TjWatchAddKey));
        button.setOnClickListener(v -> askForKey());
        ScaleStateListAnimator.apply(button, 0.04f, 1.2f);
        panel.addView(button, LayoutHelper.createLinear(-2, 40, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));

        gate = panel;
        root.addView(panel, LayoutHelper.createFrame(-1, -2, Gravity.CENTER));
        listView.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        if (search != null) search.setEnabled(false);
    }

    private void askForKey() {
        Context context = getParentActivity();
        if (context == null) return;
        final int account = currentAccount;
        final long owner = UserConfig.getInstance(account).getClientUserId();
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setSingleLine(true);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        input.setHint(TjLocale.getString(R.string.TjMediaKeyHint));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setPadding(dp(20), dp(12), dp(20), dp(12));
        org.telegram.ui.Components.TjMediaInputStyle.apply(input, TjLocale.getString(R.string.TjMediaKeyHint));
        showDialog(new AlertDialog.Builder(context)
                .setTitle(TjLocale.getString(R.string.TjMediaMetadata))
                .setMessage(TjLocale.getString(R.string.TjMediaMetadataInfo) + "\n\n"
                        + TjLocale.getString(R.string.TjMediaAttribution))
                .setView(input)
                .setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    input.setText("");
                    Utilities.globalQueue.postRunnable(() -> {
                        boolean saved = UserConfig.getInstance(account).getClientUserId() == owner
                                && TjConfig.setMediaMetadataCredential(account, owner, value);
                        AndroidUtilities.runOnUIThread(() -> {
                            if (getParentActivity() == null || !saved) return;
                            if (gate != null) { root.removeView(gate); gate = null; }
                            listView.setVisibility(View.VISIBLE);
                            status.setVisibility(View.VISIBLE);
                            if (search != null) search.setEnabled(true);
                            trending();
                        });
                    });
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    private View searchField(Context context) {
        FrameLayout field = new FrameLayout(context);
        field.setBackground(Theme.createRoundRectDrawable(dp(22), Theme.getColor(Theme.key_windowBackgroundWhite)));
        search = new EditTextBoldCursor(context);
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        search.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        search.setBackground(null);
        search.setSingleLine(true);
        search.setHint(TjLocale.getString(R.string.TjWatchSearchHint));
        search.setCursorSize(dp(20));
        search.setCursorWidth(1.5f);
        search.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                query = s.toString();
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                AndroidUtilities.runOnUIThread(searchRunnable, 400);
            }
        });
        field.addView(search, LayoutHelper.createFrame(-1, -1, Gravity.CENTER, 44, 0, 16, 0));
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_search);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteHintText), PorterDuff.Mode.SRC_IN));
        field.addView(icon, LayoutHelper.createFrame(44, 44,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
        return field;
    }

    private void trending() {
        items.clear();
        adapter.notifyDataSetChanged();
        status.setText(TjLocale.getString(R.string.TjMediaLoading));
        status.setVisibility(View.VISIBLE);
        pending = 2;
        series.trending(currentAccount, true, (body, error) -> collect(body, true, error));
        movies.trending(currentAccount, false, (body, error) -> collect(body, false, error));
    }

    private void reload() {
        if (!TjTmdb.available(currentAccount)) return;
        String typed = query.trim();
        if (typed.isEmpty()) { trending(); return; }
        // A person types the episode into the search box as readily as the name. The name is what
        // the catalogue is asked about; the numbers are carried into the title screen.
        String name = TjMediaTitle.parse("", typed).title;
        if (name == null || name.trim().isEmpty()) name = typed;
        items.clear();
        adapter.notifyDataSetChanged();
        status.setText(TjLocale.getString(R.string.TjMediaLoading));
        status.setVisibility(View.VISIBLE);
        pending = 2;
        series.search(currentAccount, name, true, (body, error) -> collect(body, true, error));
        movies.search(currentAccount, name, false, (body, error) -> collect(body, false, error));
    }

    private void collect(JSONObject body, boolean isSeries, int error) {
        if (body != null) {
            JSONArray results = body.optJSONArray("results");
            for (int i = 0; results != null && i < results.length(); i++) {
                JSONObject object = results.optJSONObject(i);
                if (object == null) continue;
                Item item = new Item(object, isSeries);
                if (item.id > 0 && !item.name.isEmpty()) items.add(item);
            }
        }
        if (--pending > 0) return;
        // Best known first, so the thing being looked for is rarely below the fold.
        items.sort((a, b) -> Double.compare(b.rating, a.rating));
        adapter.notifyDataSetChanged();
        if (!items.isEmpty()) {
            status.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.VISIBLE);
            status.setText(TjLocale.getString(error == TjTmdb.CREDENTIAL ? R.string.TjWatchNeedsKey
                    : error == TjTmdb.NETWORK ? R.string.TjWatchOffline : R.string.TjWatchNothing));
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public int getItemCount() { return items.size(); }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            PosterCell cell = new PosterCell(parent.getContext());
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerListView.Holder(cell);
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((PosterCell) holder.itemView).bind(items.get(position));
        }
    }

    /** A poster with its score in the corner and its name underneath. */
    static class PosterCell extends LinearLayout {
        private final BackupImageView image;
        private final TextView name;
        private final TextView rating;

        PosterCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(6), dp(6), dp(6), dp(10));
            FrameLayout art = new FrameLayout(context);
            art.setClipToOutline(true);
            art.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_windowBackgroundGray)));
            addView(art, LayoutHelper.createLinear(-1, -2));
            image = new BackupImageView(context) {
                @Override protected void onMeasure(int widthSpec, int heightSpec) {
                    int width = MeasureSpec.getSize(widthSpec);
                    super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(width * 3 / 2, MeasureSpec.EXACTLY));
                }
            };
            art.addView(image, LayoutHelper.createFrame(-1, -2));
            rating = new TextView(context);
            rating.setTextSize(11);
            rating.setTextColor(0xffffffff);
            rating.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            rating.setPadding(dp(6), dp(2), dp(6), dp(3));
            rating.setBackground(Theme.createRoundRectDrawable(dp(6), 0xcc000000));
            art.addView(rating, LayoutHelper.createFrame(-2, -2, Gravity.BOTTOM | Gravity.RIGHT, 6, 6, 6, 6));
            name = new TextView(context);
            name.setTextSize(13);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(name, LayoutHelper.createLinear(-1, -2, 0, 8, 0, 0));
            ScaleStateListAnimator.apply(this, 0.03f, 1.2f);
        }

        void bind(Item item) {
            String poster = TjTmdb.posterUrl(item.poster);
            image.setImage(poster.isEmpty() ? null : poster, "320_480", (android.graphics.drawable.Drawable) null);
            String year = item.year();
            name.setText(year.isEmpty() ? item.name : item.name + " (" + year + ")");
            if (item.rating > 0) {
                rating.setVisibility(VISIBLE);
                rating.setText(String.format(java.util.Locale.US, "★ %.1f", item.rating));
            } else {
                rating.setVisibility(GONE);
            }
            setContentDescription(item.name);
        }
    }
}
