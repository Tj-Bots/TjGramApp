package org.telegram.messenger;
import java.util.List;
public final class MessagesStorage {
    private static final MessagesStorage instance = new MessagesStorage();
    public static MessagesStorage getInstance(int account) { return instance; }
    public void putUsersAndChats(List<?> users, List<?> chats, boolean queue, boolean transaction) {}
}
