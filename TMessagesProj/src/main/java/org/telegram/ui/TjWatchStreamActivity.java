package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjWatchStreams;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Watch's network stream screen, laid out like the one in VLC: a field for the link at the top,
 * and under it everything opened this way before - each with how far into it playback got - to
 * open again with a tap.
 */
public class TjWatchStreamActivity extends BaseFragment {

    private static final int MENU_CLEAR = 1;

    private EditTextBoldCursor field;
    private ImageView clearButton;
    private TextView pasteChip;
    private LinearLayout historyList;
    private TextView historyHeader;
    private TextView emptyView;
    private ActionBarMenuItem otherItem;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(TjLocale.getString(R.string.TjWatchStream));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == MENU_CLEAR) askToClear();
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        otherItem = menu.addItem(0, R.drawable.ic_ab_other);
        otherItem.addSubItem(MENU_CLEAR, R.drawable.msg_delete, TjLocale.getString(R.string.TjWatchStreamClear));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = scroll;

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));

        body.addView(inputCard(context), LayoutHelper.createLinear(-1, -2));

        historyHeader = new TextView(context);
        historyHeader.setText(TjLocale.getString(R.string.TjWatchStreamHistory));
        historyHeader.setTextSize(15);
        historyHeader.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        historyHeader.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        historyHeader.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        body.addView(historyHeader, LayoutHelper.createLinear(-1, -2, 20, 18, 20, 6));

        historyList = new LinearLayout(context);
        historyList.setOrientation(LinearLayout.VERTICAL);
        historyList.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        body.addView(historyList, LayoutHelper.createLinear(-1, -2));

        emptyView = new TextView(context);
        emptyView.setText(TjLocale.getString(R.string.TjWatchStreamEmpty));
        emptyView.setTextSize(14);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setPadding(dp(24), dp(32), dp(24), dp(32));
        body.addView(emptyView, LayoutHelper.createLinear(-1, -2));

        refreshHistory();
        return fragmentView;
    }

    private View inputCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        card.setPadding(dp(16), dp(14), dp(16), dp(16));

        // The link runs left to right in every language, and a cross at its end empties it.
        FrameLayout box = new FrameLayout(context);
        box.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.getColor(Theme.key_windowBackgroundGray)));
        field = new EditTextBoldCursor(context);
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        field.setHint(TjLocale.getString(R.string.TjWatchStreamHint));
        field.setBackground(null);
        field.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setCursorSize(dp(20));
        field.setCursorWidth(1.5f);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setImeOptions(EditorInfo.IME_ACTION_GO);
        field.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        field.setTextDirection(View.TEXT_DIRECTION_LTR);
        field.setPadding(dp(14), 0, dp(44), 0);
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                play(field.getText().toString());
                return true;
            }
            return false;
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                clearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                updatePasteChip();
            }
        });
        box.addView(field, LayoutHelper.createFrame(-1, 50));

        clearButton = new ImageView(context);
        clearButton.setImageResource(R.drawable.msg_clear_input);
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
        clearButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
        clearButton.setVisibility(View.GONE);
        clearButton.setOnClickListener(v -> {
            field.setText("");
            field.requestFocus();
            AndroidUtilities.showKeyboard(field);
        });
        box.addView(clearButton, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 5, 0));
        card.addView(box, LayoutHelper.createLinear(-1, 50));

        pasteChip = new TextView(context);
        pasteChip.setText(TjLocale.getString(R.string.TjWatchStreamPaste));
        pasteChip.setTextSize(13);
        pasteChip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        pasteChip.setPadding(dp(12), dp(6), dp(12), dp(6));
        pasteChip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(14),
                Theme.getColor(Theme.key_windowBackgroundGray), Theme.getColor(Theme.key_listSelector)));
        pasteChip.setVisibility(View.GONE);
        pasteChip.setOnClickListener(v -> {
            String link = clipboardLink();
            if (link != null) {
                field.setText(link);
                field.setSelection(field.length());
            }
        });
        card.addView(pasteChip, LayoutHelper.createLinear(-2, -2,
                LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 10, 0, 0));

        TextView playButton = new TextView(context);
        playButton.setText(TjLocale.getString(R.string.TjWatchStreamPlay));
        playButton.setTextSize(16);
        playButton.setGravity(Gravity.CENTER);
        playButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        playButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        playButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        playButton.setOnClickListener(v -> play(field.getText().toString()));
        ScaleStateListAnimator.apply(playButton, 0.03f, 1.2f);
        card.addView(playButton, LayoutHelper.createLinear(-1, 48, 0, 12, 0, 0));
        return card;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshHistory();
        updatePasteChip();
    }

    /** A link on the clipboard, when it is one and is not already what the field holds. */
    private String clipboardLink() {
        try {
            ClipboardManager clipboard = (ClipboardManager) ApplicationContextHolder.get().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip().getItemCount() == 0) return null;
            CharSequence clip = clipboard.getPrimaryClip().getItemAt(0).coerceToText(ApplicationContextHolder.get());
            if (clip == null) return null;
            String text = clip.toString().trim();
            return TjWatchStreams.parse(text) != null ? text : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void updatePasteChip() {
        if (pasteChip == null || field == null) return;
        String link = clipboardLink();
        pasteChip.setVisibility(link != null && !link.equals(field.getText().toString().trim()) ? View.VISIBLE : View.GONE);
    }

    private void play(String text) {
        Uri uri = TjWatchStreams.parse(text);
        if (uri == null) {
            BulletinFactory.of(this).createErrorBulletin(TjLocale.getString(R.string.TjWatchStreamInvalid)).show();
            return;
        }
        AndroidUtilities.hideKeyboard(field);
        TjWatchPlayerActivity.openStream(this, uri);
    }

    private void refreshHistory() {
        if (historyList == null) return;
        historyList.removeAllViews();
        ArrayList<TjWatchStreams.Stream> streams = TjWatchStreams.recent();
        for (int i = 0; i < streams.size(); i++) {
            if (i > 0) {
                View divider = new View(getParentActivity());
                divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
                historyList.addView(divider, LayoutHelper.createLinear(-1, 1,
                        LocaleController.isRTL ? 0 : 72, 0, LocaleController.isRTL ? 72 : 0, 0));
            }
            historyList.addView(streamRow(streams.get(i)));
        }
        boolean empty = streams.isEmpty();
        historyList.setVisibility(empty ? View.GONE : View.VISIBLE);
        historyHeader.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (otherItem != null) otherItem.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private View streamRow(TjWatchStreams.Stream stream) {
        Context context = getParentActivity();
        FrameLayout row = new FrameLayout(context);
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setOnClickListener(v -> play(stream.url));
        row.setOnLongClickListener(v -> {
            showOptions(row, stream);
            return true;
        });

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_link);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_buttonText), PorterDuff.Mode.SRC_IN));
        icon.setBackground(Theme.createCircleDrawable(dp(40), Theme.getColor(Theme.key_featuredStickers_addButton)));
        row.addView(icon, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(stream.title.isEmpty() ? stream.url : stream.title);
        title.setTextSize(16);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        texts.addView(title, LayoutHelper.createLinear(-1, -2));

        TextView address = new TextView(context);
        address.setText(stream.url);
        address.setTextSize(12);
        address.setSingleLine(true);
        address.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        address.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        address.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        address.setTextDirection(View.TEXT_DIRECTION_LTR);
        texts.addView(address, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0));

        if (stream.duration > 0) {
            float progress = Math.min(1f, stream.position / (float) stream.duration);
            TextView time = new TextView(context);
            time.setText(progress >= 0.97f ? TjLocale.getString(R.string.TjWatchStreamWatched)
                    : formatTime(stream.position) + " / " + formatTime(stream.duration));
            time.setTextSize(12);
            time.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            time.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            texts.addView(time, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0));
            texts.addView(new ProgressLine(context, progress >= 0.97f ? 1f : progress), LayoutHelper.createLinear(-1, 3, 0, 6, 0, 0));
        }
        row.addView(texts, LayoutHelper.createFrame(-1, -2, Gravity.CENTER_VERTICAL,
                LocaleController.isRTL ? 16 : 72, 10, LocaleController.isRTL ? 72 : 16, 10));
        return row;
    }

    private void showOptions(View anchor, TjWatchStreams.Stream stream) {
        ItemOptions.makeOptions(this, anchor)
                .add(R.drawable.msg_copy, LocaleController.getString(R.string.CopyLink), () -> {
                    AndroidUtilities.addToClipboard(stream.url);
                    BulletinFactory.of(this).createCopyLinkBulletin().show();
                })
                .add(R.drawable.msg_edit, TjLocale.getString(R.string.TjWatchStreamEdit), () -> {
                    field.setText(stream.url);
                    field.setSelection(field.length());
                    field.requestFocus();
                    AndroidUtilities.showKeyboard(field);
                })
                .add(R.drawable.msg_delete, TjLocale.getString(R.string.TjWatchStreamRemove), true, () -> {
                    TjWatchStreams.remove(stream.url);
                    refreshHistory();
                })
                .setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)
                .show();
    }

    private void askToClear() {
        if (getParentActivity() == null) return;
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity())
                .setTitle(TjLocale.getString(R.string.TjWatchStreamClear))
                .setMessage(TjLocale.getString(R.string.TjWatchStreamClearText))
                .setPositiveButton(LocaleController.getString(R.string.Delete), (d, which) -> {
                    TjWatchStreams.clear();
                    refreshHistory();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (button != null) button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
    }

    private static String formatTime(long millis) {
        long seconds = millis / 1000, hours = seconds / 3600, minutes = seconds % 3600 / 60;
        seconds %= 60;
        return hours > 0 ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    /** The watched part in red over a grey track. */
    private static final class ProgressLine extends View {
        private final float progress;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);

        ProgressLine(Context context, float progress) {
            super(context);
            this.progress = progress;
            track.setColor(0x33808080);
            red.setColor(0xFFE50914);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            canvas.drawRect(0, 0, w, h, track);
            if (LocaleController.isRTL) canvas.drawRect(w - w * progress, 0, w, h, red);
            else canvas.drawRect(0, 0, w * progress, h, red);
        }
    }

    private static final class ApplicationContextHolder {
        static Context get() {
            return org.telegram.messenger.ApplicationLoader.applicationContext;
        }
    }
}
