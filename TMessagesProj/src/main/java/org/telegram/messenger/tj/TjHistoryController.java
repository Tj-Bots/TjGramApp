package org.telegram.messenger.tj;

import android.util.SparseArray;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

/** Merges locally archived messages into a normal Telegram history page. */
public final class TjHistoryController {
    private static final int QUERY_LIMIT = 500;

    private TjHistoryController() {
    }

    public static void mergeDeletedMessages(int accountId, ArrayList<MessageObject> messages,
                                            SparseArray<MessageObject>[] messageDictionaries,
                                            long dialogId, int topicId) {
        if (!TjConfig.saveDeletedMessages() || messages == null || dialogId == 0) {
            return;
        }
        // This runs while the chat is being built, on the main thread. Asking the archive is a
        // database read, so it is only worth asking about a chat the archive has something from.
        if (!TjMessageArchive.getInstance().mayHaveDeleted(accountId, dialogId)) {
            return;
        }
        boolean secret = DialogObject.isEncryptedDialog(dialogId);
        int minId = Integer.MAX_VALUE;
        int maxId = Integer.MIN_VALUE;
        HashSet<Integer> existingIds = new HashSet<>();
        for (MessageObject message : messages) {
            if (message == null || message.isSending()) {
                continue;
            }
            int id = message.getId();
            existingIds.add(id);
            minId = Math.min(minId, id);
            maxId = Math.max(maxId, id);
        }

        if (minId == Integer.MAX_VALUE) {
            minId = secret ? Integer.MIN_VALUE : 1;
            maxId = Integer.MAX_VALUE;
        } else if (!secret) {
            TLRPC.Dialog dialog = MessagesController.getInstance(accountId).getDialog(dialogId);
            boolean atTop = dialog != null && dialog.top_message == maxId;
            if (topicId != 0) {
                TLRPC.TL_forumTopic topic = MessagesController.getInstance(accountId)
                        .getTopicsController().findTopic(-dialogId, topicId);
                atTop |= topic != null && topic.top_message == maxId;
            }
            if (atTop) {
                maxId = Integer.MAX_VALUE;
            }
        }

        ArrayList<TjMessageArchive.Snapshot> archived = TjMessageArchive.getInstance()
                .getDeletedSync(accountId, dialogId, topicId, minId, maxId, QUERY_LIMIT);
        if (archived.isEmpty()) {
            return;
        }
        for (TjMessageArchive.Snapshot snapshot : archived) {
            TLRPC.Message message = snapshot.message;
            if (message == null) {
                continue;
            }
            MessageObject existing = find(messages, messageDictionaries, message.id);
            if (existing != null) {
                if (message.media != null) {
                    existing.messageOwner.media = message.media;
                    existing.messageOwner.attachPath = message.attachPath;
                    existing.messageOwner.ttl = message.ttl;
                    existing.messageOwner.destroyTime = 0;
                    existing.messageOwner.destroyTimeMillis = 0;
                    existing.forceExpired = false;
                    existing.setType();
                    existing.generateThumbs(false);
                }
                existing.messageOwner.tjDeleted = true;
                existing.deleted = false;
                continue;
            }
            message.tjDeleted = true;
            messages.add(new MessageObject(accountId, message, false, true));
            existingIds.add(message.id);
        }
        linkReplies(messages, messageDictionaries);
        Comparator<MessageObject> comparator = TjHistoryController::compareMessages;
        if (secret) {
            comparator = comparator.reversed();
        }
        Collections.sort(messages, comparator);
    }

    private static MessageObject find(ArrayList<MessageObject> messages,
                                      SparseArray<MessageObject>[] dictionaries, int messageId) {
        if (dictionaries != null) {
            for (SparseArray<MessageObject> dictionary : dictionaries) {
                if (dictionary != null) {
                    MessageObject message = dictionary.get(messageId);
                    if (message != null) {
                        return message;
                    }
                }
            }
        }
        for (MessageObject message : messages) {
            if (message != null && message.getId() == messageId) {
                return message;
            }
        }
        return null;
    }

    private static boolean contains(SparseArray<MessageObject>[] dictionaries, int messageId) {
        if (dictionaries == null) {
            return false;
        }
        for (SparseArray<MessageObject> dictionary : dictionaries) {
            if (dictionary != null && dictionary.indexOfKey(messageId) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static void linkReplies(ArrayList<MessageObject> messages,
                                    SparseArray<MessageObject>[] dictionaries) {
        for (MessageObject message : messages) {
            if (message.messageOwner.reply_to == null || message.replyMessageObject != null) {
                continue;
            }
            int replyId = message.messageOwner.reply_to.reply_to_msg_id;
            MessageObject reply = null;
            if (dictionaries != null) {
                for (SparseArray<MessageObject> dictionary : dictionaries) {
                    if (dictionary != null && (reply = dictionary.get(replyId)) != null) {
                        break;
                    }
                }
            }
            if (reply == null) {
                for (MessageObject candidate : messages) {
                    if (candidate.getId() == replyId) {
                        reply = candidate;
                        break;
                    }
                }
            }
            if (reply != null) {
                message.messageOwner.replyMessage = reply.messageOwner;
                message.replyMessageObject = reply;
            }
        }
    }

    private static int compareMessages(MessageObject first, MessageObject second) {
        int firstId = first.getId();
        int secondId = second.getId();
        if (firstId > 0 && secondId > 0) {
            return Integer.compare(secondId, firstId);
        }
        if (firstId >= 0 || secondId >= 0) {
            return Integer.compare(second.messageOwner.date, first.messageOwner.date);
        }
        return Integer.compare(firstId, secondId);
    }
}
