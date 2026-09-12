package org.telegram.messenger;
import org.telegram.tgnet.TLRPC;
import java.util.List;
public final class MessagesController {
    private static final MessagesController instance = new MessagesController();
    public static MessagesController getInstance(int account) { return instance; }
    public TLRPC.InputPeer getInputPeer(long dialog) {
        if (dialog == 0) return null;
        TLRPC.InputPeer peer = new TLRPC.InputPeer();
        if (dialog > 0) peer.user_id = dialog; else peer.channel_id = -dialog;
        return peer;
    }
    public void putUsers(List<?> users, boolean cache) {}
    public void putChats(List<?> chats, boolean cache) {}
}
