package org.telegram.messenger;
import org.telegram.tgnet.TLRPC;
public final class MessageObject {
    public final int currentAccount;
    public final TLRPC.Message messageOwner;
    public MessageObject(int account, TLRPC.Message message, boolean layout, boolean checkFiles) {
        currentAccount = account; messageOwner = message;
    }
    public long getDialogId() { return getPeerId(messageOwner.peer_id); }
    public int getId() { return messageOwner.id; }
    public static long getPeerId(TLRPC.Peer peer) {
        return peer.user_id != 0 ? peer.user_id : peer.channel_id != 0 ? -peer.channel_id : -peer.chat_id;
    }
    public static TLRPC.MessageMedia getMedia(TLRPC.Message message) { return message.media; }
}
