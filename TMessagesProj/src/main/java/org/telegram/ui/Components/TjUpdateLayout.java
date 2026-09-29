package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjUpdates;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.IUpdateLayout;

/**
 * The "update" strip at the bottom of the chats list, as the beta and Plus builds have it, fed
 * from {@link TjUpdates}: tap to download - with the percentage, loaded / total and the speed on
 * it - and tap again to install.
 */
public class TjUpdateLayout extends IUpdateLayout {

    private FrameLayout updateLayout;
    private RadialProgress2 updateLayoutIcon;
    private AnimatedTextView updateTextView;
    private AnimatedTextView.AnimatedTextDrawable updateSizeTextView;
    private AnimatedTextView.AnimatedTextDrawable speedTextView;

    private final Activity activity;
    private final ViewGroup container;

    private long speedSampleBytes = -1;
    private long speedSampleTime;
    private double speed;

    public TjUpdateLayout(Activity activity, ViewGroup container) {
        super(activity, container);
        this.activity = activity;
        this.container = container;
    }

    @Override
    public void updateFileProgress(Object[] args) {
        if (updateTextView == null || args == null || args.length < 3 || !TjUpdates.available()) return;
        if (!TjUpdates.isUpdateFile((String) args[0])) return;
        long loaded = (Long) args[1];
        long total = (Long) args[2];
        if (total <= 0) total = TjUpdates.document().size;
        float progress = total > 0 ? loaded / (float) total : 0;
        updateLayoutIcon.setProgress(progress, true);
        updateTextView.setText(TjLocale.formatString(R.string.TjUpdateDownloading, (int) (progress * 100)));
        // Loaded / total on one side and the speed on the other, around the percentage.
        updateSizeTextView.setText(sizes(loaded, total), true);
        speedTextView.setText(speedText(loaded), true);
    }

    private static String sizes(long loaded, long total) {
        if (total >= 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f/%.1f MB", loaded / 1048576f, total / 1048576f);
        }
        return AndroidUtilities.formatFileSize(loaded) + "/" + AndroidUtilities.formatFileSize(total);
    }

    private String speedText(long loaded) {
        long now = SystemClock.elapsedRealtime();
        if (speedSampleBytes < 0 || loaded < speedSampleBytes) {
            speedSampleBytes = loaded;
            speedSampleTime = now;
        } else if (now - speedSampleTime >= 500) {
            double current = (loaded - speedSampleBytes) * 1000.0 / (now - speedSampleTime);
            speed = speed <= 0 ? current : speed * 0.6 + current * 0.4;
            speedSampleBytes = loaded;
            speedSampleTime = now;
        }
        return speed > 0 ? AndroidUtilities.formatFileSize((long) speed) + "/s" : "";
    }

    @Override
    public void createUpdateUI(int currentAccount) {
        if (container == null || updateLayout != null) {
            return;
        }
        updateLayout = new FrameLayout(activity);
        updateLayout.setVisibility(View.INVISIBLE);
        updateLayout.setTranslationY(dp(44));
        updateLayout.setBackground(Theme.getSelectorDrawable(0x40ffffff, false));
        container.addView(updateLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));
        updateLayout.setOnClickListener(v -> {
            if (!TjUpdates.available()) {
                return;
            }
            if (TjUpdates.downloaded()) {
                AndroidUtilities.openForView(TjUpdates.document(), true, activity);
            } else if (TjUpdates.downloading()) {
                TjUpdates.cancelDownload();
            } else {
                speedSampleBytes = -1;
                speed = 0;
                TjUpdates.download();
            }
            updateAppUpdateViews(currentAccount, true);
        });

        updateTextView = new AnimatedTextView(activity, true, true, true) {
            @Override
            protected void onDraw(Canvas canvas) {
                updateSizeTextView.setBounds(0, 0, getMeasuredWidth() - dp(16), getMeasuredHeight());
                updateSizeTextView.draw(canvas);
                speedTextView.setBounds(dp(16), 0, getMeasuredWidth(), getMeasuredHeight());
                speedTextView.draw(canvas);

                canvas.save();
                canvas.translate(dp(15), 0);
                super.onDraw(canvas);
                canvas.translate((getMeasuredWidth() - width()) / 2f - dp(30), dp(11));
                updateLayoutIcon.draw(canvas);
                canvas.restore();
            }

            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return super.verifyDrawable(who) || who == updateSizeTextView || who == speedTextView;
            }
        };
        updateTextView.setTextSize(dp(15));
        updateTextView.setTypeface(AndroidUtilities.bold());
        updateTextView.setTextColor(0xffffffff);
        updateTextView.setGravity(Gravity.CENTER);
        updateLayout.addView(updateTextView, LayoutHelper.createFrameMatchParent());
        updateTextView.setText(TjLocale.getString(R.string.TjUpdateAvailable), false);

        updateLayoutIcon = new RadialProgress2(updateTextView);
        updateLayoutIcon.setColors(0xffffffff, 0xffffffff, Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButton));
        updateLayoutIcon.setProgressRect(0, 0, dp(22), dp(22));
        updateLayoutIcon.setCircleRadius(dp(11));
        updateLayoutIcon.setAsMini();

        updateSizeTextView = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
        updateSizeTextView.setCallback(updateTextView);
        updateSizeTextView.setTextSize(dp(12));
        updateSizeTextView.setTypeface(AndroidUtilities.bold());
        updateSizeTextView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        updateSizeTextView.setTextColor(0xccffffff);

        speedTextView = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
        speedTextView.setCallback(updateTextView);
        speedTextView.setTextSize(dp(12));
        speedTextView.setTypeface(AndroidUtilities.bold());
        speedTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        speedTextView.setTextColor(0xccffffff);
    }

    @Override
    public void updateAppUpdateViews(int currentAccount, boolean animated) {
        if (container == null) {
            return;
        }
        if (TjUpdates.available()) {
            createUpdateUI(currentAccount);

            if (TjUpdates.downloaded()) {
                updateLayoutIcon.setIcon(MediaActionDrawable.ICON_UPDATE, true, animated);
                updateTextView.setText(TjLocale.getString(R.string.TjUpdateInstall), animated);
                updateSizeTextView.setText(null, animated);
                speedTextView.setText(null, animated);
            } else if (TjUpdates.downloading()) {
                updateLayoutIcon.setIcon(MediaActionDrawable.ICON_CANCEL, true, animated);
                Float p = ImageLoader.getInstance().getFileProgress(TjUpdates.fileName());
                float progress = p != null ? p : 0f;
                updateLayoutIcon.setProgress(progress, false);
                updateTextView.setText(TjLocale.formatString(R.string.TjUpdateDownloading, (int) (progress * 100)), animated);
            } else {
                updateLayoutIcon.setIcon(MediaActionDrawable.ICON_DOWNLOAD, true, animated);
                updateTextView.setText(TjLocale.getString(R.string.TjUpdateAvailable), animated);
                updateSizeTextView.setText(AndroidUtilities.formatFileSize(TjUpdates.document().size), animated);
                speedTextView.setText(null, animated);
            }
            if (updateLayout.getTag() != null) {
                return;
            }
            updateLayout.setVisibility(View.VISIBLE);
            updateLayout.setTag(1);
            if (animated) {
                updateLayout.animate().translationY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(null).setDuration(180).start();
            } else {
                updateLayout.setTranslationY(0);
            }
        } else {
            if (updateLayout == null || updateLayout.getTag() == null) {
                return;
            }
            updateLayout.setTag(null);
            if (animated) {
                updateLayout.animate().translationY(dp(44)).setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (updateLayout.getTag() == null) {
                            updateLayout.setVisibility(View.INVISIBLE);
                        }
                    }
                }).setDuration(180).start();
            } else {
                updateLayout.setTranslationY(dp(44));
                updateLayout.setVisibility(View.INVISIBLE);
            }
        }
    }
}
