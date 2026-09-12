import org.telegram.messenger.MessageObject;
import org.telegram.messenger.tj.TjMediaKind;
import org.telegram.tgnet.TLRPC;

public class TjMediaKindTest {
    private static int checks;
    private static void expect(MessageObject m, int type) {
        checks++;
        if (TjMediaKind.of(m) != type) throw new AssertionError("classification " + checks);
    }
    public static void main(String[] args) {
        MessageObject m = new MessageObject(0, new TLRPC.TL_message(), false, false);
        expect(m, TjMediaKind.DOCUMENT);
        m.document = new TLRPC.Document();
        m.document.mime_type = "application/octet-stream";
        m.filename = "הרצאה.MKV";
        expect(m, TjMediaKind.VIDEO);
        if (!m.document.attributes.isEmpty() || m.video) throw new AssertionError("must not mutate video flags");
        m.voice = true; expect(m, TjMediaKind.VOICE); m.voice = false;
        m.round = true; m.video = true; expect(m, TjMediaKind.VOICE); m.round = false; m.video = false;
        m.music = true; expect(m, TjMediaKind.MUSIC); m.music = false;
        m.photo = true; expect(m, TjMediaKind.PHOTO); m.photo = false;
        for (TLRPC.DocumentAttribute attr : new TLRPC.DocumentAttribute[]{
                new TLRPC.TL_documentAttributeAudio(), new TLRPC.TL_documentAttributeSticker(),
                new TLRPC.TL_documentAttributeImageSize(), new TLRPC.TL_documentAttributeAnimated()}) {
            m.document.attributes.add(attr);
            expect(m, TjMediaKind.DOCUMENT);
            m.document.attributes.clear();
        }
        m.document.mime_type = "video/mp4"; m.filename = "notes.pdf";
        expect(m, TjMediaKind.DOCUMENT);
        m.filename = null; expect(m, TjMediaKind.VIDEO);
        m.gif = true; expect(m, TjMediaKind.GIF); m.gif = false;
        m.document = new TLRPC.TL_documentEncrypted(); m.filename = "private.mkv";
        expect(m, TjMediaKind.DOCUMENT);
        System.out.println(checks + " library classification checks passed without player settings or attribute mutation");
    }
}
