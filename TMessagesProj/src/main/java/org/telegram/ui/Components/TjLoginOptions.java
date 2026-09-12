package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegramQRCodeWriter;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.tj.TjLoginRules;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.TwoStepVerificationActivity;

/** A single, cancellable alternative-login attempt. Credentials are never persisted here. */
public final class TjLoginOptions implements NotificationCenter.NotificationCenterDelegate {
    public interface Delegate {
        void authorized(TLRPC.TL_auth_authorization authorization);
        void passwordRequired(Bundle params);
    }

    private final BaseFragment fragment;
    private final Delegate delegate;
    private final int account;
    private final ConnectionsManager connections;
    private AlertDialog dialog;
    private ImageView qr;
    private TextView status;
    private boolean closed;
    private boolean qrMode;
    private boolean tokenUpdated;
    private int requestId;
    private int generation;
    private int migrations;
    private final Runnable refresh = this::exportQr;

    public TjLoginOptions(BaseFragment fragment, Delegate delegate) {
        this.fragment = fragment;
        this.delegate = delegate;
        account = fragment.getCurrentAccount();
        connections = ConnectionsManager.getInstance(account);
    }

    private String text(int id) { return TjLocale.getString(id); }

    public void show(int option) {
        if (fragment.getParentActivity() == null || UserConfig.getInstance(account).isClientActivated()) return;
        if (option == R.string.TjLoginQr) showQr();
        else if (option == R.string.TjLoginBot) showBot();
        else if (option == R.string.TjLoginPasskey) showPasskeyInfo();
    }

    private void showPasskeyInfo() {
        dialog = new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle(text(R.string.TjLoginPasskey))
                .setMessage(text(R.string.TjLoginPasskeyInfo))
                .setPositiveButton(LocaleController.getString(R.string.OK), null).create();
        if (fragment.showDialog(dialog, d -> close()) == null) close();
    }

    private TextView label(String value) {
        TextView view = new TextView(fragment.getParentActivity());
        view.setText(value);
        view.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        view.setTextSize(16);
        view.setGravity(Gravity.CENTER);
        view.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        return view;
    }

    private void showBot() {
        LinearLayout content = new LinearLayout(fragment.getParentActivity());
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(label(text(R.string.TjLoginBotInfo)));
        EditTextBoldCursor token = new EditTextBoldCursor(fragment.getParentActivity());
        token.setTextSize(16);
        token.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        token.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        token.setHint(text(R.string.TjLoginBotToken));
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setTextDirection(View.TEXT_DIRECTION_LTR);
        token.setSaveEnabled(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        content.addView(token, LayoutHelper.createLinear(-1, 48, 24, 0, 24, 8));
        status = label("");
        content.addView(status);
        ScrollView scroll = new ScrollView(fragment.getParentActivity());
        scroll.addView(content);
        dialog = new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle(text(R.string.TjLoginBot)).setView(scroll)
                .setPositiveButton(text(R.string.TjLoginConnect), null)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create();
        if (fragment.showDialog(dialog, d -> { token.setText(""); close(); }) == null) {
            close();
            return;
        }
        if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (requestId != 0 || closed) return;
            String value = token.getText().toString().trim();
            long botId = TjLoginRules.botId(value);
            if (botId == 0) {
                status.setText(text(R.string.TjLoginInvalidBotToken));
                return;
            }
            if (alreadyConnected(botId)) { status.setText(text(R.string.TjLoginAlreadyConnected)); return; }
            TLRPC.TL_auth_importBotAuthorization req = new TLRPC.TL_auth_importBotAuthorization();
            req.api_id = BuildVars.APP_ID;
            req.api_hash = BuildVars.APP_HASH;
            req.bot_auth_token = value;
            token.setText("");
            AndroidUtilities.hideKeyboard(token);
            status.setText(text(R.string.TjLoginConnecting));
            send(req, ConnectionsManager.DEFAULT_DATACENTER_ID, false);
        });
    }

    private boolean alreadyConnected(long id) {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            UserConfig config = UserConfig.getInstance(i);
            if (config.isClientActivated() && config.getClientUserId() == id
                    && ConnectionsManager.getInstance(i).isTestBackend() == connections.isTestBackend()) return true;
        }
        return false;
    }

    private void showQr() {
        qrMode = true;
        LinearLayout content = new LinearLayout(fragment.getParentActivity());
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(label(text(R.string.TjLoginQrInfo)));
        qr = new ImageView(fragment.getParentActivity());
        qr.setContentDescription(text(R.string.TjLoginQr));
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(qr, LayoutHelper.createLinear(-1, 240, 24, 8, 24, 8));
        status = label(text(R.string.TjLoginConnecting));
        content.addView(status);
        ScrollView scroll = new ScrollView(fragment.getParentActivity());
        scroll.addView(content);
        dialog = new AlertDialog.Builder(fragment.getParentActivity()).setTitle(text(R.string.TjLoginQr))
                .setView(scroll).setNegativeButton(LocaleController.getString(R.string.Cancel), null).create();
        if (fragment.showDialog(dialog, d -> close()) == null) {
            close();
            return;
        }
        if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.tjLoginTokenUpdated);
        exportQr();
    }

    private void exportQr() {
        if (closed || requestId != 0) return;
        AndroidUtilities.cancelRunOnUIThread(refresh);
        qr.setImageDrawable(null);
        status.setText(text(R.string.TjLoginConnecting));
        TLRPC.TL_auth_exportLoginToken req = new TLRPC.TL_auth_exportLoginToken();
        req.api_id = BuildVars.APP_ID;
        req.api_hash = BuildVars.APP_HASH;
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            if (UserConfig.getInstance(i).isClientActivated()
                    && ConnectionsManager.getInstance(i).isTestBackend() == connections.isTestBackend()) {
                req.except_ids.add(UserConfig.getInstance(i).getClientUserId());
            }
        }
        send(req, ConnectionsManager.DEFAULT_DATACENTER_ID, true);
    }

    private void send(TLObject request, int dc, boolean loginToken) {
        int attempt = ++generation;
        requestId = connections.sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (closed || attempt != generation) return;
            requestId = 0;
            if (error != null) {
                if (loginToken && "SESSION_PASSWORD_NEEDED".equals(error.text)) loadPassword();
                else fail(error.text);
            } else if (response instanceof TLRPC.TL_auth_authorization) {
                authorize((TLRPC.TL_auth_authorization) response);
            } else if (response instanceof TLRPC.TL_auth_loginTokenSuccess) {
                TLRPC.auth_Authorization auth = ((TLRPC.TL_auth_loginTokenSuccess) response).authorization;
                if (auth instanceof TLRPC.TL_auth_authorization) authorize((TLRPC.TL_auth_authorization) auth);
                else fail(null);
            } else if (response instanceof TLRPC.TL_auth_loginTokenMigrateTo) {
                if (++migrations > 3) { fail(null); return; }
                TLRPC.TL_auth_loginTokenMigrateTo migration = (TLRPC.TL_auth_loginTokenMigrateTo) response;
                connections.setDefaultDatacenterId(migration.dc_id);
                TLRPC.TL_auth_importLoginToken req = new TLRPC.TL_auth_importLoginToken();
                req.token = migration.token;
                send(req, migration.dc_id, true);
            } else if (response instanceof TLRPC.TL_auth_loginToken) {
                TLRPC.TL_auth_loginToken token = (TLRPC.TL_auth_loginToken) response;
                if (tokenUpdated) { tokenUpdated = false; exportQr(); return; }
                renderQr(token, attempt);
            } else fail(null);
        }), null, null, ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagEnableUnauthorized,
                dc, ConnectionsManager.ConnectionTypeGeneric, true);
        connections.bindRequestToGuid(requestId, fragment.getClassGuid());
    }

    private void renderQr(TLRPC.TL_auth_loginToken token, int attempt) {
        int now = connections.getCurrentTime();
        long delay = TjLoginRules.qrRefreshDelay(token.expires, now);
        AndroidUtilities.runOnUIThread(refresh, delay);
        if (token.expires <= now) return;
        // Encoding is bounded, but still kept off the UI thread. Never log the login URI.
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap bitmap = null;
            try {
                String uri = "tg://login?token=" + Base64.encodeToString(token.token, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                bitmap = new TelegramQRCodeWriter().encode(uri, 640, 640, null, null);
            } catch (Exception ignored) { }
            Bitmap result = bitmap;
            AndroidUtilities.runOnUIThread(() -> {
                if (closed || attempt != generation) {
                    if (result != null) result.recycle();
                    return;
                }
                if (result == null) fail(null);
                else { qr.setImageBitmap(result); status.setText(text(R.string.TjLoginQrWaiting)); }
            });
        });
    }

    private void loadPassword() {
        AndroidUtilities.cancelRunOnUIThread(refresh);
        int attempt = ++generation;
        requestId = connections.sendRequest(new TL_account.getPassword(), (res, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (closed || attempt != generation) return;
            requestId = 0;
            if (error != null || !(res instanceof TL_account.Password)) { fail(error == null ? null : error.text); return; }
            TL_account.Password password = (TL_account.Password) res;
            if (!TwoStepVerificationActivity.canHandleCurrentPassword(password, true)) { fail(null); return; }
            SerializedData data = new SerializedData(password.getObjectSize());
            password.serializeToStream(data);
            Bundle params = new Bundle();
            params.putString("password", Utilities.bytesToHex(data.toByteArray()));
            data.cleanup();
            close();
            delegate.passwordRequired(params);
        }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
        connections.bindRequestToGuid(requestId, fragment.getClassGuid());
    }

    private void authorize(TLRPC.TL_auth_authorization auth) {
        if (auth.user == null || alreadyConnected(auth.user.id)) { fail("ALREADY_CONNECTED"); return; }
        close();
        delegate.authorized(auth);
    }

    private void fail(String error) {
        AndroidUtilities.cancelRunOnUIThread(refresh);
        tokenUpdated = false;
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.tjLoginTokenUpdated);
        if (qr != null) qr.setImageDrawable(null);
        int message = "ALREADY_CONNECTED".equals(error) ? R.string.TjLoginAlreadyConnected
                : error != null && (error.contains("ACCESS_TOKEN") || error.contains("TOKEN_INVALID")) ? R.string.TjLoginInvalidBotToken
                : error != null && error.startsWith("FLOOD_WAIT") ? R.string.FloodWait : R.string.TjLoginFailed;
        status.setText(text(message));
        // Retry is explicit, avoiding automatic loops after server errors or rate limits.
        if (qrMode) {
            status.setText(text(message) + "\n" + text(R.string.TjLoginRetry));
            status.setOnClickListener(v -> {
                status.setOnClickListener(null);
                migrations = 0;
                NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.tjLoginTokenUpdated);
                exportQr();
            });
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (!closed && qrMode && id == NotificationCenter.tjLoginTokenUpdated) {
            if (requestId != 0) tokenUpdated = true;
            else exportQr();
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        generation++;
        AndroidUtilities.cancelRunOnUIThread(refresh);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.tjLoginTokenUpdated);
        if (requestId != 0) { connections.cancelRequest(requestId, true); requestId = 0; }
        if (qr != null) qr.setImageDrawable(null);
        if (dialog != null) { dialog.dismiss(); dialog = null; }
    }
}
