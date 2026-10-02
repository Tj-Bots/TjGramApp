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
import org.telegram.messenger.Utilities;
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
        if (msg.tjDeleted) {
            // When it went: the moment this device saw it deleted, from the archive it is kept in.
            TjMessageArchive.getInstance().getDeletedAt(getCurrentAccount(), messageObject.getDialogId(),
                    messageObject.getId(), deletedAt -> {
                        if (fragmentView == null || getContext() == null) return;
                        final String text = deletedAt > 0 ? formatExactTime(deletedAt) : TjLocale.getString(R.string.TjDeletedAtUnknown);
                        final int before = container.getChildCount();
                        addRow(container, TjLocale.getString(R.string.TjDeletedAt), text, deletedAt > 0 ? text : null);
                        for (int i = before; i < container.getChildCount(); i++) {
                            View added = container.getChildAt(i);
                            container.removeViewAt(i);
                            container.addView(added, Math.min(editedRowIndex + (i - before), container.getChildCount()));
                        }
                    });
        }
        if (msg.views != 0 || msg.forwards != 0) {
            // How many times a channel post was viewed and forwarded - the counters Telegram keeps on
            // the post itself, asked for again below so they are today's and not the cached ones.
            TextView views = addRow(container, "Views", String.valueOf(msg.views), String.valueOf(msg.views));
            TextView forwards = addRow(container, "Forwards", String.valueOf(msg.forwards), String.valueOf(msg.forwards));
            tjRefreshCounters(msg, views, forwards);
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
            String quality = tjVideoQuality(document, filePath);
            if (quality == null) {
                quality = tjAudioQuality(document, filePath);
            }
            if (quality != null) {
                addRow(container, "Quality", quality, quality);
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
            TLRPC.PhotoSize biggest = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
            if (biggest != null && biggest.w > 0 && biggest.h > 0) {
                String resolution = biggest.w + "×" + biggest.h;
                addRow(container, "Resolution", resolution, resolution);
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

    /**
     * "1080p · 1920×1080 · 30 fps" for a video - sent as a video or as a plain file. What the
     * sender's app wrote on it comes first; a file without it is read from the copy on the device.
     */
    private static String tjVideoQuality(TLRPC.Document document, String filePath) {
        int w = 0, h = 0;
        for (TLRPC.DocumentAttribute attribute : document.attributes) {
            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                w = attribute.w;
                h = attribute.h;
                break;
            }
        }
        String fps = null;
        boolean video = w > 0 || document.mime_type != null && document.mime_type.startsWith("video/");
        if (!video) return null;
        if (filePath != null && new java.io.File(filePath).exists()) {
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            try {
                retriever.setDataSource(filePath);
                if (w <= 0 || h <= 0) {
                    w = Utilities.parseInt(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                    h = Utilities.parseInt(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                    int rotation = Utilities.parseInt(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
                    if (rotation == 90 || rotation == 270) {
                        int t = w; w = h; h = t;
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    String rate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
                    if (!TextUtils.isEmpty(rate)) {
                        float value = Float.parseFloat(rate);
                        if (value > 0 && value < 1000) fps = Math.round(value) + " fps";
                    }
                }
            } catch (Throwable ignore) {
            } finally {
                try {
                    retriever.release();
                } catch (Throwable ignore) {
                }
            }
        }
        if (w <= 0 || h <= 0) return null;
        int shortSide = Math.min(w, h);
        String label;
        if (shortSide >= 2160) label = "4K";
        else if (shortSide >= 1440) label = "1440p";
        else if (shortSide >= 1080) label = "1080p";
        else if (shortSide >= 720) label = "720p";
        else if (shortSide >= 480) label = "480p";
        else if (shortSide >= 360) label = "360p";
        else label = shortSide + "p";
        return label + " · " + w + "×" + h + (fps != null ? " · " + fps : "");
    }

    /**
     * "320 kbps · 44.1 kHz · stereo" for music and voice. The file on the device says it exactly;
     * before it is downloaded the rate is worked out from its size and length - an average.
     */
    private static String tjAudioQuality(TLRPC.Document document, String filePath) {
        int duration = 0;
        boolean audio = document.mime_type != null && document.mime_type.startsWith("audio/");
        for (TLRPC.DocumentAttribute attribute : document.attributes) {
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                audio = true;
                duration = (int) attribute.duration;
            }
        }
        if (!audio) return null;
        long bitrate = 0;
        int sampleRate = 0, channels = 0;
        boolean exact = false;
        if (filePath != null && new java.io.File(filePath).exists()) {
            android.media.MediaExtractor extractor = new android.media.MediaExtractor();
            try {
                extractor.setDataSource(filePath);
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    android.media.MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                    if (mime == null || !mime.startsWith("audio/")) continue;
                    if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) sampleRate = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE);
                    if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) channels = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT);
                    if (format.containsKey(android.media.MediaFormat.KEY_BIT_RATE)) {
                        bitrate = format.getInteger(android.media.MediaFormat.KEY_BIT_RATE);
                        exact = bitrate > 0;
                    }
                    if (duration <= 0 && format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                        duration = (int) (format.getLong(android.media.MediaFormat.KEY_DURATION) / 1_000_000L);
                    }
                    break;
                }
            } catch (Throwable ignore) {
            } finally {
                extractor.release();
            }
            if (bitrate <= 0) {
                android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                try {
                    retriever.setDataSource(filePath);
                    Integer value = Utilities.parseInt(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE));
                    if (value != null && value > 0) {
                        bitrate = value;
                        exact = true;
                    }
                } catch (Throwable ignore) {
                } finally {
                    try {
                        retriever.release();
                    } catch (Throwable ignore) {
                    }
                }
            }
        }
        if (bitrate <= 0 && duration > 0 && document.size > 0) {
            bitrate = document.size * 8L / duration;
        }
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        if (bitrate > 0) parts.add((exact ? "" : "~") + Math.round(bitrate / 1000.0) + " kbps");
        if (sampleRate > 0) {
            parts.add(sampleRate % 1000 == 0 ? sampleRate / 1000 + " kHz"
                    : String.format(java.util.Locale.US, "%.1f kHz", sampleRate / 1000f));
        }
        if (channels == 1) parts.add("mono");
        else if (channels == 2) parts.add("stereo");
        else if (channels > 2) parts.add(channels + " ch");
        return parts.isEmpty() ? null : TextUtils.join(" · ", parts);
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

    private void tjRefreshCounters(TLRPC.Message msg, TextView views, TextView forwards) {
        if (msg.id <= 0 || messageObject.getDialogId() >= 0) return;
        TLRPC.TL_messages_getMessagesViews req = new TLRPC.TL_messages_getMessagesViews();
        req.peer = getMessagesController().getInputPeer(messageObject.getDialogId());
        req.id.add(msg.id);
        req.increment = false;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TLRPC.TL_messages_messageViews)) return;
            TLRPC.TL_messages_messageViews res = (TLRPC.TL_messages_messageViews) response;
            if (res.views.isEmpty()) return;
            TLRPC.TL_messageViews counters = res.views.get(0);
            if ((counters.flags & 1) != 0 && counters.views > 0) {
                msg.views = Math.max(msg.views, counters.views);
                setRowValue(views, String.valueOf(msg.views));
            }
            if ((counters.flags & 2) != 0) {
                msg.forwards = counters.forwards;
                setRowValue(forwards, String.valueOf(msg.forwards));
            }
        }));
    }

    private void setRowValue(TextView valueView, String value) {
        if (valueView == null) return;
        valueView.setText(value);
        ((View) valueView.getParent()).setOnClickListener(v -> copyValue(value));
    }

    private TextView addRow(LinearLayout container, String label, CharSequence value, String copyText) {
        if (TextUtils.isEmpty(value)) {
            return null;
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
        return valueView;
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
