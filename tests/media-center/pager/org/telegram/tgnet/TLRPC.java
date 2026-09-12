package org.telegram.tgnet;
import java.util.ArrayList;
/** Test protocol shapes only; no serialization or network implementation. */
public final class TLRPC {
    public static class MessagesFilter {}
    public static class TL_inputMessagesFilterPhotoVideo extends MessagesFilter {}
    public static class TL_inputMessagesFilterDocument extends MessagesFilter {}
    public static class TL_inputMessagesFilterMusic extends MessagesFilter {}
    public static class TL_inputMessagesFilterRoundVoice extends MessagesFilter {}
    public static class TL_inputMessagesFilterGif extends MessagesFilter {}
    public static class Peer { public long user_id, chat_id, channel_id; }
    public static class InputPeer extends Peer {}
    public static class TL_inputPeerEmpty extends InputPeer {}
    public static class TL_messages_searchGlobal extends TLObject {
        public String q;
        public int limit, offset_id, offset_rate;
        public MessagesFilter filter;
        public boolean users_only, groups_only, broadcasts_only;
        public InputPeer offset_peer;
    }
    public static class TL_messages_search extends TLObject {
        public String q;
        public int limit, offset_id;
        public MessagesFilter filter;
        public InputPeer peer;
    }
    public static class MessageMedia { public int ttl_seconds; }
    public static class TL_messageMediaPhoto extends MessageMedia {}
    public static class TL_messageMediaDocument extends MessageMedia {}
    public static class Message {
        public int id, date, ttl_period;
        public Peer peer_id = new Peer();
        public MessageMedia media;
    }
    public static class TL_message extends Message {}
    public static class messages_Messages extends TLObject {
        public int next_rate;
        public ArrayList<Message> messages = new ArrayList<>();
        public ArrayList<Object> users = new ArrayList<>(), chats = new ArrayList<>();
    }
}
