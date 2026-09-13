package org.telegram.messenger.tj;

import android.text.TextUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessageSuggestionParams;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Re-uploads protected messages as new local messages instead of forwarding them. */
public final class TjProtectedForwarder {
    private static final long DOWNLOAD_TIMEOUT_SECONDS = 120;

    private TjProtectedForwarder() {
    }

    public static boolean shouldReupload(int account, ArrayList<MessageObject> messages) {
        if (!TjConfig.protectedForwarding() || messages == null) {
            return false;
        }
        MessagesController controller = MessagesController.getInstance(account);
        for (MessageObject message : messages) {
            if (canReupload(account, message, controller)) {
                return true;
            }
        }
        return false;
    }

    public static boolean canReupload(int account, MessageObject message) {
        return canReupload(account, message, MessagesController.getInstance(account));
    }

    private static boolean canReupload(int account, MessageObject message, MessagesController controller) {
        if (!TjConfig.protectedForwarding() || message == null || message.messageOwner == null ||
                message.messageOwner instanceof TLRPC.TL_messageService ||
                message.type == MessageObject.TYPE_PAID_MEDIA || message.isVoiceOnce() || message.isRoundOnce()
                || org.telegram.messenger.DialogObject.isEncryptedDialog(message.getDialogId())
                || message.getDialogId() == org.telegram.messenger.UserObject.VERIFY
                || message.messageOwner.media != null && message.messageOwner.media.ttl_seconds != 0) {
            return false;
        }
        boolean protectedSource = message.messageOwner.tjDeleted || message.messageOwner.noforwards ||
                controller.isPeerNoForwards(message.getDialogId());
        boolean supportedContent = !TextUtils.isEmpty(message.messageOwner.message) ||
                message.getDocument() != null || message.isPhoto();
        return protectedSource && supportedContent;
    }

    public static void reupload(
            int account,
            ArrayList<MessageObject> source,
            long peer,
            boolean forwardFromMyName,
            boolean hideCaption,
            boolean notify,
            int scheduleDate,
            int scheduleRepeatPeriod,
            MessageObject replyToTopMsg,
            int videoTimestamp,
            long payStars,
            long monoForumPeerId,
            MessageSuggestionParams suggestionParams) {
        ArrayList<MessageObject> messages = new ArrayList<>(source);
        Utilities.globalQueue.postRunnable(() -> prepareAndSend(account, messages, peer,
                forwardFromMyName, hideCaption, notify, scheduleDate, scheduleRepeatPeriod,
                replyToTopMsg, videoTimestamp, payStars,
                monoForumPeerId, suggestionParams));
    }

    private static void prepareAndSend(
            int account,
            ArrayList<MessageObject> messages,
            long peer,
            boolean forwardFromMyName,
            boolean hideCaption,
            boolean notify,
            int scheduleDate,
            int scheduleRepeatPeriod,
            MessageObject replyToTopMsg,
            int videoTimestamp,
            long payStars,
            long monoForumPeerId,
            MessageSuggestionParams suggestionParams) {
        ArrayList<PreparedMessage> prepared = new ArrayList<>();
        Map<MessageObject, PreparedMessage> preparedBySource = new IdentityHashMap<>();
        for (MessageObject message : messages) {
            if (!canReupload(account, message)) {
                continue;
            }
            PreparedMessage item = prepare(account, message, hideCaption);
            if (item != null) {
                prepared.add(item);
                preparedBySource.put(message, item);
            }
        }
        assignGroups(prepared);
        AndroidUtilities.runOnUIThread(() -> {
            SendMessagesHelper helper = SendMessagesHelper.getInstance(account);
            ArrayList<MessageObject> regularBatch = new ArrayList<>();
            int failed = 0;
            for (MessageObject source : messages) {
                if (!canReupload(account, source)) {
                    regularBatch.add(source);
                    continue;
                }
                sendRegularBatch(helper, regularBatch, peer, forwardFromMyName, hideCaption, notify,
                        scheduleDate, scheduleRepeatPeriod, replyToTopMsg, videoTimestamp, payStars,
                        monoForumPeerId, suggestionParams);
                PreparedMessage item = preparedBySource.get(source);
                SendMessagesHelper.SendMessageParams params = item == null ? null
                        : createParams(helper, item, peer, notify, scheduleDate,
                        scheduleRepeatPeriod, replyToTopMsg);
                if (params != null) {
                    params.payStars = payStars;
                    params.monoForumPeer = monoForumPeerId;
                    params.suggestionParams = suggestionParams;
                    helper.sendMessage(params);
                } else {
                    failed++;
                }
            }
            sendRegularBatch(helper, regularBatch, peer, forwardFromMyName, hideCaption, notify,
                    scheduleDate, scheduleRepeatPeriod, replyToTopMsg, videoTimestamp, payStars,
                    monoForumPeerId, suggestionParams);
            if (failed > 0) {
                NotificationCenter.getGlobalInstance().postNotificationName(
                        NotificationCenter.showBulletin,
                        org.telegram.ui.Components.Bulletin.TYPE_ERROR,
                        LocaleController.formatString(R.string.TjProtectedForwardFailed, failed));
            }
        });
    }

    private static void sendRegularBatch(
            SendMessagesHelper helper,
            ArrayList<MessageObject> batch,
            long peer,
            boolean forwardFromMyName,
            boolean hideCaption,
            boolean notify,
            int scheduleDate,
            int scheduleRepeatPeriod,
            MessageObject replyToTopMsg,
            int videoTimestamp,
            long payStars,
            long monoForumPeerId,
            MessageSuggestionParams suggestionParams) {
        if (batch.isEmpty()) {
            return;
        }
        ArrayList<MessageObject> sending = new ArrayList<>(batch);
        batch.clear();
        helper.sendMessage(sending, peer, forwardFromMyName, hideCaption, notify, scheduleDate,
                scheduleRepeatPeriod, replyToTopMsg, videoTimestamp, payStars,
                monoForumPeerId, suggestionParams);
    }

    private static PreparedMessage prepare(int account, MessageObject message, boolean hideCaption) {
        String text = hideCaption && (message.getDocument() != null || message.isPhoto())
                ? "" : message.messageOwner.message;
        ArrayList<TLRPC.MessageEntity> entities = TextUtils.isEmpty(text)
                ? null : message.messageOwner.entities;
        if (message.getDocument() != null || message.isPhoto()) {
            File path = ensureMediaDownloaded(account, message);
            if (path == null || !path.exists() || path.length() == 0) {
                if (TextUtils.isEmpty(text)) {
                    return null;
                }
                return PreparedMessage.text(message, text, entities);
            }
            return PreparedMessage.media(message, text, entities, path);
        }
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        return PreparedMessage.text(message, text, entities);
    }

    private static File ensureMediaDownloaded(int account, MessageObject message) {
        FileLoader loader = FileLoader.getInstance(account);
        File existing = loader.getPathToMessage(message.messageOwner);
        if (existing.exists() && existing.length() > 0) {
            return existing;
        }

        TLRPC.Document document = message.getDocument();
        TLRPC.Photo photo = MessageObject.getPhoto(message.messageOwner);
        TLRPC.PhotoSize photoSize = photo == null ? null : FileLoader.getClosestPhotoSizeWithSize(
                photo.sizes, AndroidUtilities.getPhotoSize(true), false, null, true);
        String expectedName = document != null ? FileLoader.getAttachFileName(document)
                : photoSize != null ? FileLoader.getAttachFileName(photoSize) : null;
        if (TextUtils.isEmpty(expectedName)) {
            return null;
        }

        CountDownLatch registered = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<File> loadedPath = new AtomicReference<>();
        NotificationCenter.NotificationCenterDelegate observer = (id, observedAccount, args) -> {
            if (observedAccount != account || args.length == 0 || !(args[0] instanceof String)) {
                return;
            }
            String name = (String) args[0];
            if (!expectedName.equals(name) && !name.contains(expectedName) && !expectedName.contains(name)) {
                return;
            }
            if (id == NotificationCenter.fileLoaded && args.length > 1 && args[1] instanceof File) {
                loadedPath.set((File) args[1]);
            }
            completed.countDown();
        };

        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter center = NotificationCenter.getInstance(account);
            center.addObserver(observer, NotificationCenter.fileLoaded);
            center.addObserver(observer, NotificationCenter.fileLoadFailed);
            registered.countDown();
        });
        try {
            registered.await(10, TimeUnit.SECONDS);
            if (document != null) {
                loader.loadFile(document, message, FileLoader.PRIORITY_HIGH, 0);
            } else if (photoSize != null) {
                loader.loadFile(ImageLocation.getForPhoto(photoSize, photo), message, null,
                        FileLoader.PRIORITY_HIGH, 0);
            }
            completed.await(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter center = NotificationCenter.getInstance(account);
                center.removeObserver(observer, NotificationCenter.fileLoaded);
                center.removeObserver(observer, NotificationCenter.fileLoadFailed);
            });
        }
        File result = loadedPath.get();
        if (result != null && result.exists()) {
            return result;
        }
        existing = loader.getPathToMessage(message.messageOwner);
        return existing.exists() ? existing : null;
    }

    private static void assignGroups(ArrayList<PreparedMessage> messages) {
        HashMap<Long, ArrayList<PreparedMessage>> groups = new HashMap<>();
        for (PreparedMessage message : messages) {
            if (message.media && message.source.getGroupId() != 0) {
                groups.computeIfAbsent(message.source.getGroupId(), key -> new ArrayList<>()).add(message);
            }
        }
        for (ArrayList<PreparedMessage> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            long localGroupId = Utilities.random.nextLong();
            for (int i = 0; i < group.size(); i++) {
                PreparedMessage message = group.get(i);
                message.params = new HashMap<>();
                message.params.put("groupId", String.valueOf(localGroupId));
                if (i == group.size() - 1) {
                    message.params.put("final", "1");
                }
            }
        }
    }

    private static SendMessagesHelper.SendMessageParams createParams(
            SendMessagesHelper helper,
            PreparedMessage item,
            long peer,
            boolean notify,
            int scheduleDate,
            int scheduleRepeatPeriod,
            MessageObject replyToTopMsg) {
        if (!item.media) {
            return SendMessagesHelper.SendMessageParams.of(item.text, peer, null, replyToTopMsg,
                    null, false, item.entities, null, null, notify, scheduleDate,
                    scheduleRepeatPeriod, null, false);
        }
        if (item.source.getDocument() != null) {
            TLRPC.Document original = item.source.getDocument();
            TLRPC.TL_document document = new TLRPC.TL_document();
            document.file_reference = new byte[0];
            document.dc_id = Integer.MIN_VALUE;
            document.date = AccountInstance.getInstance(item.source.currentAccount)
                    .getConnectionsManager().getCurrentTime();
            document.mime_type = original.mime_type;
            document.size = item.path.length();
            document.attributes = original.attributes;
            document.file_name = original.file_name;
            document.file_name_fixed = original.file_name_fixed;
            document.localPath = item.path.getAbsolutePath();
            return SendMessagesHelper.SendMessageParams.of(document, null, item.path.getAbsolutePath(),
                    peer, null, replyToTopMsg, item.text, item.entities, null, item.params,
                    notify, scheduleDate, scheduleRepeatPeriod, 0, item.source, null, false,
                    item.source.hasMediaSpoilers());
        }
        TLRPC.TL_photo photo = helper.generatePhotoSizes(item.path.getAbsolutePath(), null);
        if (photo == null) {
            return null;
        }
        return SendMessagesHelper.SendMessageParams.of(photo, item.path.getAbsolutePath(), peer,
                null, replyToTopMsg, item.text, item.entities, null, item.params, notify,
                scheduleDate, scheduleRepeatPeriod, 0, item.source, false,
                item.source.hasMediaSpoilers());
    }

    private static final class PreparedMessage {
        final MessageObject source;
        final String text;
        final ArrayList<TLRPC.MessageEntity> entities;
        final File path;
        final boolean media;
        HashMap<String, String> params;

        private PreparedMessage(MessageObject source, String text,
                                ArrayList<TLRPC.MessageEntity> entities, File path, boolean media) {
            this.source = source;
            this.text = text == null ? "" : text;
            this.entities = entities;
            this.path = path;
            this.media = media;
        }

        static PreparedMessage text(MessageObject source, String text,
                                    ArrayList<TLRPC.MessageEntity> entities) {
            return new PreparedMessage(source, text, entities, null, false);
        }

        static PreparedMessage media(MessageObject source, String text,
                                     ArrayList<TLRPC.MessageEntity> entities, File path) {
            return new PreparedMessage(source, text, entities, path, true);
        }
    }
}
