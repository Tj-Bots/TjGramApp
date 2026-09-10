package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

/**
 * The look of TjGram's own settings screens.
 *
 * Every TJ screen builds rows out of the stock Telegram cells; this is what turns a flat list of
 * them into grouped cards - rounded at the ends of a group, ripple clipped to those corners, and
 * headers that line up with the text inside the card rather than with the screen edge. Keeping it
 * in one place is what stops the four screens from drifting apart.
 */
public final class TjSettingsStyle {

    /** Side inset of a card from the screen edge. */
    public static final int SIDE_MARGIN = 12;
    /** Corner radius at the ends of a group. */
    public static final int RADIUS = 14;
    /** Where text starts inside a stock cell, so headers can line up with it. */
    private static final int CELL_TEXT_INSET = 21;

    /** Badge colours for the category rows, in the order the home screen lists them. */
    public static final int GHOST_COLOR = 0xFF8E7CFF;
    public static final int ARCHIVE_COLOR = 0xFFF0A03C;
    public static final int FILTERS_COLOR = 0xFF3CA5F0;
    public static final int APPEARANCE_COLOR = 0xFFE8618C;
    public static final int ADVANCED_COLOR = 0xFF5FB868;
    public static final int CHANNEL_COLOR = 0xFF4EA4F6;
    public static final int DISCUSSION_COLOR = 0xFF37C0A8;

    private TjSettingsStyle() {
    }

    /**
     * Makes a row part of a card. {@code first} and {@code last} say where it sits in its group;
     * a row that is both gets a fully rounded card of its own.
     */
    public static void card(View view, boolean first, boolean last) {
        params(view, SIDE_MARGIN);
        int radius = dp(RADIUS);
        int top = first ? radius : 0;
        int bottom = last ? radius : 0;
        view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                top, top, bottom, bottom,
                Theme.getColor(Theme.key_windowBackgroundWhite),
                Theme.getColor(Theme.key_listSelector),
                Theme.getColor(Theme.key_listSelector)));
    }

    /** Strips the card off a row that is not one - a header or an explanatory paragraph. */
    public static void plain(View view) {
        params(view, 0);
        view.setBackgroundColor(Color.TRANSPARENT);
    }

    private static void params(View view, int sideMargin) {
        ViewGroup.LayoutParams current = view.getLayoutParams();
        RecyclerView.LayoutParams params;
        if (current instanceof RecyclerView.LayoutParams) {
            params = (RecyclerView.LayoutParams) current;
        } else {
            params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    current != null ? current.height : ViewGroup.LayoutParams.WRAP_CONTENT);
            view.setLayoutParams(params);
        }
        // The layout manager hands out wrap-content by default, which quietly breaks right-to-left
        // rows: they measure to the text and end up hugging the wrong edge.
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.leftMargin = params.rightMargin = dp(sideMargin);
    }

    /** The header above a group of rows. */
    public static TextView header(Context context) {
        TextView textView = new TextView(context);
        textView.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setTextSize(15);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM);
        int inset = dp(SIDE_MARGIN + CELL_TEXT_INSET);
        textView.setPadding(LocaleController.isRTL ? dp(SIDE_MARGIN) : inset, dp(16),
                LocaleController.isRTL ? inset : dp(SIDE_MARGIN), dp(7));
        return textView;
    }

    /** A rounded square of colour behind a category icon. */
    public static Drawable badge(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(10));
        return drawable;
    }
}
