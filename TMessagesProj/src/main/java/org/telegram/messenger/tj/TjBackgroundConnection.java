package org.telegram.messenger.tj;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;

/**
 * Keeps TjGram connected while it is not on screen.
 *
 * Telegram relies on Firebase to wake a backgrounded process. A sideloaded build cannot count on
 * that reaching every device, and Android stops plain background services soon after the last
 * activity goes away, so the connection is dropped and messages only arrive when the app is opened
 * again. Promoting the existing notifications service to the foreground keeps the process - and
 * therefore the MTProto connection - alive, the way a messenger installed from a store behaves.
 */
public final class TjBackgroundConnection {

    public static final int NOTIFICATION_ID = 39;
    private static final String CHANNEL_ID = "tj_background_connection";

    private TjBackgroundConnection() {
    }

    /** The service only runs in the foreground while the user asks it to. */
    public static boolean isEnabled() {
        return TjConfig.backgroundConnection();
    }

    public static Notification createNotification(Context context) {
        ensureChannel(context);
        Intent intent = new Intent(context, org.telegram.ui.LaunchActivity.class);
        intent.setAction("com.tmessages.openchat");
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.notification);
        builder.setContentTitle(LocaleController.getString(R.string.AppName));
        builder.setContentText(TjLocale.getString(R.string.TjBackgroundConnectionRunning));
        builder.setContentIntent(contentIntent);
        builder.setOngoing(true);
        builder.setShowWhen(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setPriority(Notification.PRIORITY_MIN);
            builder.setVisibility(Notification.VISIBILITY_SECRET);
        }
        return builder.build();
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                TjLocale.getString(R.string.TjBackgroundConnection), NotificationManager.IMPORTANCE_MIN);
        channel.setDescription(TjLocale.getString(R.string.TjBackgroundConnectionInfo));
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.enableLights(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }

    public static boolean isBatteryOptimized() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        try {
            Context context = ApplicationLoader.applicationContext;
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return power != null && !power.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Throwable error) {
            FileLog.e("Tj battery optimization check failed", error);
            return false;
        }
    }

    /**
     * Opens the system prompt that exempts TjGram from battery optimization. Without the
     * exemption the OS can still stop the process, foreground service or not.
     */
    @SuppressWarnings("BatteryLife")
    public static void requestIgnoreBatteryOptimizations(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable error) {
            FileLog.e("Tj battery optimization request failed", error);
            try {
                Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Throwable ignored) {
            }
        }
    }
}
