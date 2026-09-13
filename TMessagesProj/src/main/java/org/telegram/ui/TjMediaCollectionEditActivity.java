package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.messenger.tj.TjMediaStore;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.*;

/** Local list editor; validates and commits name and symbol together. */
public final class TjMediaCollectionEditActivity extends BaseFragment {
    private final String oldName;
    private final long owner;
    private final java.util.function.Consumer<String> saved;
    private String symbol;
    private EditTextBoldCursor name;
    private TextView error, saveButton;
    private boolean saving, destroyed;

    public TjMediaCollectionEditActivity(int account, String oldName, String symbol, java.util.function.Consumer<String> saved) {
        setCurrentAccount(account);
        owner = UserConfig.getInstance(account).getClientUserId();
        this.oldName = oldName; this.symbol = symbol == null ? "" : symbol; this.saved = saved;
    }
    private String text(int id) { return TjLocale.getString(id); }
    private boolean active() { return !destroyed && UserConfig.getInstance(currentAccount).isClientActivated()
            && UserConfig.getInstance(currentAccount).getClientUserId() == owner; }

    @Override public View createView(Context context) {
        actionBar.setTitle(text(oldName == null ? R.string.TjMediaNewCollection : R.string.TjMediaEditCollection));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1 && !saving) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context); scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = scroll;
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(24));
        scroll.addView(body);
        LinearLayout card = new LinearLayout(context); card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), Theme.getColor(Theme.key_windowBackgroundWhite)));
        HeaderCell header = new HeaderCell(context); header.setText(text(R.string.TjMediaCollectionName));
        card.addView(header, LayoutHelper.createLinear(-1, -2));
        name = new EditTextBoldCursor(context);
        TjMediaInputStyle.apply(name, text(R.string.TjMediaCollectionName));
        name.setSingleLine(true);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});
        name.setText(oldName == null ? "" : oldName);
        card.addView(name, LayoutHelper.createLinear(-1, 56, 16, 0, 16, 8));
        TextSettingsCell icon = new TextSettingsCell(context);
        bindIcon(icon);
        icon.setBackground(Theme.getSelectorDrawable(false));
        icon.setOnClickListener(v -> { if (!saving) chooseIcon(icon); });
        card.addView(icon, LayoutHelper.createLinear(-1, 56));
        body.addView(card, LayoutHelper.createLinear(-1, -2));
        error = new TextView(context); error.setTextSize(14);
        error.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        error.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        error.setVisibility(View.GONE);
        body.addView(error, LayoutHelper.createLinear(-1, -2, 8, 12, 8, 0));
        saveButton = new TextView(context); saveButton.setText(text(R.string.Save));
        saveButton.setTextSize(16); saveButton.setTypeface(AndroidUtilities.bold()); saveButton.setGravity(Gravity.CENTER);
        saveButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        saveButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        saveButton.setOnClickListener(v -> save());
        body.addView(saveButton, LayoutHelper.createLinear(-1, 50, 0, 20, 0, 0));
        return scroll;
    }
    private void bindIcon(TextSettingsCell cell) {
        cell.setTextAndIcon(text(R.string.TjFolderIcon), TjFolderIcons.getTabIcon(symbol), false);
    }
    private void chooseIcon(TextSettingsCell cell) {
        GridLayout grid = new GridLayout(getContext());
        int columns = Math.max(4, Math.min(8, (int) (AndroidUtilities.displaySize.x / AndroidUtilities.density - 32) / 48));
        grid.setColumnCount(columns); grid.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(20));
        final BottomSheet[] sheet = new BottomSheet[1];
        for (String value : TjFolderIcons.emoticons()) {
            ImageView image = new ImageView(getContext()); image.setImageResource(TjFolderIcons.getTabIcon(value));
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE); image.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            image.setColorFilter(Theme.getColor(value.equals(symbol) ? Theme.key_dialogTextBlue : Theme.key_dialogIcon));
            image.setContentDescription(value); image.setBackground(Theme.getSelectorDrawable(false));
            image.setOnClickListener(v -> { symbol = value; bindIcon(cell); sheet[0].dismiss(); });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = AndroidUtilities.dp(44); params.height = AndroidUtilities.dp(48); grid.addView(image, params);
        }
        ScrollView scroll = new ScrollView(getContext()); scroll.addView(grid);
        sheet[0] = new BottomSheet.Builder(getContext()).setTitle(text(R.string.TjFolderIcon)).setCustomView(scroll).create();
        showDialog(sheet[0]);
    }
    private void save() {
        if (saving || !active()) return;
        String value = name.getText().toString().trim();
        if (value.isEmpty()) { error.setText(text(R.string.TjMediaCollectionNameRequired)); error.setVisibility(View.VISIBLE); name.requestFocus(); return; }
        saving = true; saveButton.setEnabled(false); saveButton.setAlpha(.5f); error.setVisibility(View.GONE);
        TjMediaStore.getInstance().editCollection(currentAccount, oldName, value, symbol, success -> {
            if (!active()) return;
            saving = false; saveButton.setEnabled(true); saveButton.setAlpha(1f);
            if (success) { AndroidUtilities.hideKeyboard(name); if (saved != null) saved.accept(value); finishFragment(); }
            else { error.setText(text(R.string.TjMediaSaveError)); error.setVisibility(View.VISIBLE); }
        });
    }
    @Override public boolean onBackPressed(boolean invoked) { return !saving && super.onBackPressed(invoked); }
    @Override public boolean isSwipeBackEnabled(android.view.MotionEvent event) { return !saving && super.isSwipeBackEnabled(event); }
    @Override public void onFragmentDestroy() { destroyed = true; super.onFragmentDestroy(); }
}
