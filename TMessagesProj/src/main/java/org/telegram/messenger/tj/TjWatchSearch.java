package org.telegram.messenger.tj;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Finding the file for something the catalogue says exists.
 *
 * The index only holds chats that have been scanned, which is never all of them, so this asks
 * Telegram the same question a person would type into the search field - the name of the thing -
 * and reads the answers. Nothing is stored and nothing is scanned: it is one search, run because
 * somebody pressed watch.
 */
public final class TjWatchSearch {

    public interface Callback {
        void complete(ArrayList<MessageObject> results);
    }

    private static final int LIMIT = 40;

    private TjWatchSearch() {
    }

    /**
     * @param season  the season being looked for, or -1 for a film
     * @param episode the episode being looked for, or -1 for a film
     */
    public static void search(List<Integer> accounts, String name, int season, int episode, Callback callback) {
        final ArrayList<MessageObject> results = new ArrayList<>();
        final HashSet<String> seen = new HashSet<>();
        final ArrayList<String> queries = new ArrayList<>();
        queries.add(name);
        if (season >= 0 && episode >= 0) {
            // The episode label is a word in its own right in most filenames, and asking for it
            // reaches episodes that a search for the name alone would never page down to.
            queries.add(name + " " + String.format(Locale.US, "S%02dE%02d", season, episode));
        }
        final TLRPC.MessagesFilter[] filters = {
                new TLRPC.TL_inputMessagesFilterVideo(),
                // Videos posted as plain files are the whole reason the file player exists.
                new TLRPC.TL_inputMessagesFilterDocument()
        };
        int requests = 0;
        for (int account : accounts) {
            if (UserConfig.getInstance(account).isClientActivated()) requests += queries.size() * filters.length;
        }
        if (requests == 0) {
            callback.complete(results);
            return;
        }
        final int[] pending = {requests};
        for (int account : accounts) {
            if (!UserConfig.getInstance(account).isClientActivated()) continue;
            for (String query : queries) {
                for (TLRPC.MessagesFilter filter : filters) {
                    send(account, query, filter, messages -> {
                        for (MessageObject message : messages) {
                            if (!isPlayable(message)) continue;
                            if (!matches(message, season, episode)) continue;
                            if (seen.add(message.getDialogId() + ":" + message.getId())) results.add(message);
                        }
                        if (--pending[0] == 0) callback.complete(results);
                    });
                }
            }
        }
    }

    private static void send(int account, String query, TLRPC.MessagesFilter filter, Callback callback) {
        AccountInstance instance = AccountInstance.getInstance(account);
        TLRPC.TL_messages_searchGlobal request = new TLRPC.TL_messages_searchGlobal();
        request.q = query;
        request.filter = filter;
        request.limit = LIMIT;
        request.offset_rate = 0;
        request.offset_id = 0;
        request.offset_peer = new TLRPC.TL_inputPeerEmpty();
        instance.getConnectionsManager().sendRequest(request, (response, error) -> {
            ArrayList<MessageObject> messages = new ArrayList<>();
            if (error == null && response instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages result = (TLRPC.messages_Messages) response;
                instance.getMessagesStorage().putUsersAndChats(result.users, result.chats, true, true);
                for (int i = 0; i < result.messages.size(); i++) {
                    messages.add(new MessageObject(account, result.messages.get(i), false, true));
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                AccountInstance current = AccountInstance.getInstance(account);
                current.getMessagesController().putUsers(
                        error == null && response instanceof TLRPC.messages_Messages
                                ? ((TLRPC.messages_Messages) response).users : new ArrayList<>(), false);
                current.getMessagesController().putChats(
                        error == null && response instanceof TLRPC.messages_Messages
                                ? ((TLRPC.messages_Messages) response).chats : new ArrayList<>(), false);
                callback.complete(messages);
            });
        });
    }

    private static boolean isPlayable(MessageObject message) {
        int kind = TjMediaKind.of(message);
        if (kind == TjMediaKind.VIDEO) return true;
        // A document only counts when the file player would accept it - a subtitle or an archive
        // named after the film is not something to hand to a player.
        if (kind != TjMediaKind.DOCUMENT) return false;
        return message.getDocument() != null && TjVideoFiles.isTjMarked(message.getDocument());
    }

    private static boolean matches(MessageObject message, int season, int episode) {
        if (episode < 0) return true;
        TjMediaTitle parsed = TjMediaTitle.parse(message.getDocumentName(),
                message.messageOwner == null ? "" : message.messageOwner.message);
        if (parsed.episode != episode) return false;
        // A file that never named its season still belongs to the episode it names.
        return parsed.season < 0 || season < 0 || parsed.season == season;
    }
}
