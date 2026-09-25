package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.tj.TjMessageArchive;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;

public class MessageInfoActivity extends BaseFragment {

    private final MessageObject messageObject;

    public MessageInfoActivity(MessageObject messageObject) {
        super();
        this.messageObject = messageObject;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Message Info");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout rootLayout = new FrameLayout(context);
        rootLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = rootLayout;

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        scrollView.addView(container, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        rootLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fillContent(container);

        return fragmentView;
    }

    private void fillContent(LinearLayout container) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return;
        }
        final TLRPC.Message msg = messageObject.messageOwner;
        final long fromId = msg.from_id != null ? MessageObject.getPeerId(msg.from_id) : 0;
        final long peerId = msg.peer_id != null ? MessageObject.getPeerId(msg.peer_id) : 0;

        TLRPC.User fromUser = fromId > 0 ? getMessagesController().getUser(fromId) : null;
        TLRPC.Chat fromChat = fromId < 0 ? getMessagesController().getChat(-fromId) : null;
        String fromName = fromUser != null ? UserObject.getUserName(fromUser) : (fromChat != null ? fromChat.title : null);
        String fromUsername = fromUser != null ? fromUser.username : (fromChat != null ? fromChat.username : null);

        String replyName = null;
        String replyUsername = null;
        long replyFromId = 0;
        boolean hasReply = false;
        if (messageObject.replyMessageObject != null && messageObject.replyMessageObject.messageOwner != null) {
            hasReply = true;
            TLRPC.Message replyMsg = messageObject.replyMessageObject.messageOwner;
            replyFromId = replyMsg.from_id != null ? MessageObject.getPeerId(replyMsg.from_id) : 0;
            TLRPC.User replyUser = replyFromId > 0 ? getMessagesController().getUser(replyFromId) : null;
            TLRPC.Chat replyChat = replyFromId < 0 ? getMessagesController().getChat(-replyFromId) : null;
            replyName = replyUser != null ? UserObject.getUserName(replyUser) : (replyChat != null ? replyChat.title : null);
            replyUsername = replyUser != null ? replyUser.username : (replyChat != null ? replyChat.username : null);
        } else if (msg.reply_to != null && msg.reply_to.reply_to_msg_id != 0) {
            hasReply = true;
        }

        TLRPC.Document document = messageObject.getDocument();
        TLRPC.Photo photo = (msg.media instanceof TLRPC.TL_messageMediaPhoto) ? msg.media.photo : null;

        String mediaTypeLabel = null;
        if (messageObject.isRoundVideo()) {
            mediaTypeLabel = LocaleController.getString(R.string.AttachRound);
        } else if (messageObject.isVoice()) {
            mediaTypeLabel = LocaleController.getString(R.string.AttachAudio);
        } else if (document != null && MessageObject.isGifDocument(document)) {
            mediaTypeLabel = LocaleController.getString(R.string.AttachGif);
        } else if (messageObject.isVideo()) {
            mediaTypeLabel = LocaleController.getString(R.string.AttachVideo);
        } else if (messageObject.isMusic()) {
            mediaTypeLabel = LocaleController.getString(R.string.AttachMusic);
        } else if (messageObject.isSticker() || messageObject.isAnimatedSticker()) {
            mediaTypeLabel = LocaleController.getString(R.string.AttachSticker);
        } else if (messageObject.isPhoto()) {
            mediaTypeLabel = LocaleController.getString(R.string.AttachPhoto);
        } else if (document != null) {
            mediaTypeLabel = LocaleController.getString(R.string.AttachDocument);
        }

        String filePath = null;
        try {
            String attach = msg.attachPath;
            if (!TextUtils.isEmpty(attach) && new File(attach).exists()) {
                filePath = attach;
            } else {
                File f = getFileLoader().getPathToMessage(msg);
                if (f != null && f.exists()) {
                    filePath = f.getPath();
                }
            }
        } catch (Exception ignored) {
        }

        addRow(container, LocaleController.getString(R.string.Message), !TextUtils.isEmpty(msg.message) ? msg.message : mediaTypeLabel, !TextUtils.isEmpty(msg.message) ? msg.message : mediaTypeLabel);
        addRow(container, "Id", String.valueOf(msg.id), String.valueOf(msg.id));
        String chatIdText = formatBotApiPeerId(peerId);
        addRow(container, "Chat Id", chatIdText, chatIdText);
        addPersonRow(container, LocaleController.getString(R.string.From), fromName, fromUsername, fromId);
        if (!TextUtils.isEmpty(msg.post_author)) {
            addRow(container, "Author", msg.post_author, msg.post_author);
        }
        final String sentAt = formatExactTime((long) msg.date * 1000);
        addRow(container, "Date", sentAt, sentAt);
        if (msg.edit_date != 0) {
            final String editedAt = formatExactTime((long) msg.edit_date * 1000);
            addRow(container, "Edited", editedAt, editedAt);
        }
        final int editedRowIndex = container.getChildCount();
        if (msg.views != 0) {
            addRow(container, "Views", String.valueOf(msg.views), String.valueOf(msg.views));
        }
        if (hasReply) {
            if (replyName != null || replyUsername != null) {
                addPersonRow(container, "Reply to", replyName, replyUsername, replyFromId);
            } else {
                addRow(container, "Reply to", String.valueOf(msg.reply_to.reply_to_msg_id), String.valueOf(msg.reply_to.reply_to_msg_id));
            }
        }
        if (document != null) {
            String fileName = FileLoader.getDocumentFileName(document);
            if (!TextUtils.isEmpty(fileName)) {
                addRow(container, "Name", fileName, fileName);
            }
            if (filePath != null) {
                addRow(container, "File", filePath, filePath);
            }
            if (document.size > 0) {
                String sizeStr = AndroidUtilities.formatFileSize(document.size);
                addRow(container, "Size", sizeStr, sizeStr);
            }
            if (!TextUtils.isEmpty(document.mime_type)) {
                addRow(container, "MimeType", document.mime_type, document.mime_type);
            }
            if (document.dc_id != 0) {
                addRow(container, "DC", "DC" + document.dc_id, null);
            }
        } else if (photo != null) {
            if (filePath != null) {
                addRow(container, "File", filePath, filePath);
            }
            if (photo.dc_id != 0) {
                addRow(container, "DC", "DC" + photo.dc_id, null);
            }
        }
        TjMessageArchive.getInstance().getRevisions(getCurrentAccount(), messageObject.getDialogId(),
                messageObject.getId(), revisions -> {
                    if (!revisions.isEmpty() && fragmentView != null && getContext() != null) {
                        addHistoryRow(container, revisions.size());
                        // Some edits arrive without the server's edit date on the copy we hold; the
                        // moment the last one was kept is then the closest there is to it.
                        if (msg.edit_date == 0) {
                            long lastEdit = 0;
                            for (TjMessageArchive.Snapshot revision : revisions) {
                                lastEdit = Math.max(lastEdit, revision.editDate != 0 ? revision.editDate * 1000L : revision.capturedAt);
                            }
                            if (lastEdit > 0) {
                                final int before = container.getChildCount();
                                final String editedAt = formatExactTime(lastEdit);
                                addRow(container, "Edited", editedAt, editedAt);
                                for (int i = before; i < container.getChildCount(); i++) {
                                    View added = container.getChildAt(i);
                                    container.removeViewAt(i);
                                    container.addView(added, editedRowIndex + (i - before));
                                }
                            }
                        }
                    }
                });
    }

    /** A date down to the second - what the message info is for is knowing exactly when. */
    private static String formatExactTime(long millis) {
        final String pattern = LocaleController.is24HourFormat ? "dd.MM.yyyy, HH:mm:ss" : "dd.MM.yyyy, h:mm:ss a";
        return org.telegram.messenger.time.FastDateFormat.getInstance(pattern,
                LocaleController.getInstance().getCurrentLocale()).format(millis);
    }

    private void addHistoryRow(LinearLayout container, int count) {
        TextView row = new TextView(getContext());
        row.setText(TjLocale.formatString(R.string.TjEditHistoryCount, count));
        row.setTextSize(16);
        row.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        row.setBackground(Theme.getSelectorDrawable(true));
        row.setOnClickListener(v -> presentFragment(new TjMessageHistoryActivity(messageObject)));
        container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));
    }

    private String formatBotApiPeerId(long peerId) {
        if (peerId >= 0) {
            return String.valueOf(peerId);
        }
        if (!TjSettingsActivity.isBotApiIdsEnabled()) {
            return String.valueOf(-peerId);
        }
        TLRPC.Chat chat = getMessagesController().getChat(-peerId);
        if (ChatObject.isChannel(chat)) {
            return "-100" + (-peerId);
        }
        return String.valueOf(peerId);
    }

    private void copyValue(String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        AndroidUtilities.addToClipboard(text);
        BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
    }

    private TextView makeValueLine(CharSequence text, String copyText) {
        TextView valueView = new TextView(getContext());
        valueView.setText(text);
        valueView.setTextSize(15);
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        valueView.setBackground(Theme.getSelectorDrawable(true));
        valueView.setClickable(true);
        valueView.setFocusable(true);
        valueView.setPadding(0, AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3));
        valueView.setOnClickListener(v -> copyValue(copyText));
        return valueView;
    }

    private void addDivider(LinearLayout container) {
        View divider = new View(getContext());
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        container.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1));
    }

    private void addRow(LinearLayout container, String label, CharSequence value, String copyText) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        if (copyText != null) {
            row.setBackground(Theme.getSelectorDrawable(true));
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> copyValue(copyText));
        }

        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        row.addView(labelView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView valueView = new TextView(getContext());
        valueView.setText(value);
        valueView.setTextSize(15);
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        addDivider(container);
    }

    private void addPersonRow(LinearLayout container, String label, String name, String username, long id) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));

        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        row.addView(labelView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        if (!TextUtils.isEmpty(name)) {
            row.addView(makeValueLine(name, name), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }
        if (!TextUtils.isEmpty(username)) {
            String withAt = "@" + username;
            row.addView(makeValueLine(withAt, withAt), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }
        row.addView(makeValueLine(String.valueOf(id), String.valueOf(id)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        addDivider(container);
    }
}
