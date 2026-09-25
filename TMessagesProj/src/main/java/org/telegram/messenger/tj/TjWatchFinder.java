package org.telegram.messenger.tj;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/**
 * Finds the copies of one film or one episode among the chats: what this device already indexed,
 * and Telegram's own search. Best copy first - the closest match, then the better picture, then
 * the larger file of that picture.
 *
 * The title screen uses it for the first episode someone picks, and the Watch player for every
 * one after that: another source for what is playing, the next episode, an episode from the list.
 */
public final class TjWatchFinder {

    /** One copy of the thing, from wherever it was found. */
    public static final class Copy {
        public final MessageObject message;
        public final long position;
        public final int score;

        public Copy(MessageObject message, long position, int score) {
            this.message = message;
            this.position = position;
            this.score = score;
        }

        String identity() {
            return TjWatchSearch.identity(message);
        }

        public long size() {
            return message.getDocument() == null ? 0 : message.getDocument().size;
        }

        public String quality() {
            return TjMediaTitle.parse(message.getDocumentName(),
                    message.messageOwner == null ? "" : message.messageOwner.message).quality;
        }

        public String described() {
            return TjTitleMatch.describe(message.getDocumentName(),
                    message.messageOwner == null ? "" : message.messageOwner.message);
        }

        public String sourceName() {
            return TjWatchFinder.sourceName(message);
        }

        int qualityRank() {
            switch (quality().toUpperCase(Locale.ROOT)) {
                case "2160P": case "4K": return 4;
                case "1080P": return 3;
                case "720P": return 2;
                case "480P": return 1;
                default: return 0;
            }
        }
    }

    private final ArrayList<Integer> accounts = new ArrayList<>();
    private final ArrayList<String> targets;
    private final String name;
    private final int year;

    public TjWatchFinder(String name, ArrayList<String> targets, int year) {
        this.name = name == null ? "" : name;
        this.targets = targets;
        this.year = year;
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) accounts.add(account);
        }
    }

    /** Every copy of this season and episode (both -1 for a film), best first. */
    public void find(int season, int episode, Utilities.Callback<ArrayList<Copy>> done) {
        final ArrayList<Copy> found = new ArrayList<>();
        final HashSet<String> seen = new HashSet<>();
        final int[] pending = {accounts.size() + 1};
        final Runnable finished = () -> {
            if (--pending[0] > 0) return;
            found.sort((a, b) -> {
                if (a.score != b.score) return Integer.compare(b.score, a.score);
                int quality = Integer.compare(b.qualityRank(), a.qualityRank());
                return quality != 0 ? quality : Long.compare(b.size(), a.size());
            });
            done.run(found);
        };
        for (int account : accounts) {
            TjMediaStore.getInstance().load(account, 0, name, "", null, new HashSet<>(), 0, 0, page -> {
                if (page != null) {
                    for (TjMediaStore.Record record : page) {
                        if (TjMediaKind.of(record.message) != TjMediaKind.VIDEO) continue;
                        int score = TjTitleMatch.score(record.message.getDocumentName(),
                                record.message.messageOwner == null ? "" : record.message.messageOwner.message,
                                targets, year, season, episode);
                        if (score == TjTitleMatch.REJECT) continue;
                        Copy copy = new Copy(record.message, record.position, score);
                        if (seen.add(copy.identity())) found.add(copy);
                    }
                }
                finished.run();
            });
        }
        TjWatchSearch.search(accounts, targets, year, season, episode, results -> {
            for (TjWatchSearch.Result result : results) {
                Copy copy = new Copy(result.message, 0, result.score);
                if (seen.add(copy.identity())) found.add(copy);
            }
            finished.run();
        });
    }

    /** Which chat a copy came from, because that is how people tell two copies apart. */
    public static String sourceName(MessageObject message) {
        long dialogId = message.getDialogId();
        MessagesController controller = MessagesController.getInstance(message.currentAccount);
        if (dialogId < 0) {
            TLRPC.Chat chat = controller.getChat(-dialogId);
            return chat == null ? "" : chat.title;
        }
        TLRPC.User user = controller.getUser(dialogId);
        return user == null ? "" : UserObject.getUserName(user);
    }
}
