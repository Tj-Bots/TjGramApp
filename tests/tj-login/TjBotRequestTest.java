import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

/** Exercises the actual MTProto request encoder, without networking or real credentials. */
public final class TjBotRequestTest {
    private static int assertions;
    private static void check(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("Bot authorization request encoding");
    }
    public static void main(String[] args) {
        TLRPC.TL_auth_importBotAuthorization request = new TLRPC.TL_auth_importBotAuthorization();
        request.flags = 0;
        request.api_id = 12345;
        request.api_hash = "synthetic-api-hash";
        request.bot_auth_token = "123456:synthetic_token_never_used_for_login";
        SerializedData output = new SerializedData();
        request.serializeToStream(output);
        SerializedData input = new SerializedData(output.toByteArray());
        check(input.readInt32(true) == 0x67a3ff2c);
        check(input.readInt32(true) == 0);
        check(input.readInt32(true) == 12345);
        check(input.readString(true).equals(request.api_hash));
        check(input.readString(true).equals(request.bot_auth_token));
        check(input.remaining() == 0);
        input.cleanup();
        output.cleanup();
        TLRPC.TL_auth_exportLoginToken qr = new TLRPC.TL_auth_exportLoginToken();
        qr.api_id = 54321;
        qr.api_hash = "synthetic-qr-hash";
        qr.except_ids.add(5_000_000_000L);
        output = new SerializedData();
        qr.serializeToStream(output);
        input = new SerializedData(output.toByteArray());
        check(input.readInt32(true) == 0xb7e085fe);
        check(input.readInt32(true) == 54321);
        check(input.readString(true).equals(qr.api_hash));
        check(input.readInt32(true) == 0x1cb5c415);
        check(input.readInt32(true) == 1);
        check(input.readInt64(true) == 5_000_000_000L);
        check(input.remaining() == 0);
        input.cleanup();
        output.cleanup();

        TLRPC.TL_auth_importLoginToken migration = new TLRPC.TL_auth_importLoginToken();
        migration.token = new byte[]{0, 1, -1, 127, -128};
        output = new SerializedData();
        migration.serializeToStream(output);
        input = new SerializedData(output.toByteArray());
        check(input.readInt32(true) == 0x95ac5ce4);
        check(java.util.Arrays.equals(input.readByteArray(true), migration.token));
        check(input.remaining() == 0);
        input.cleanup();
        output.cleanup();
        System.out.println("PASS: " + assertions + " actual bot/QR request encoding assertions (offline)");
    }
}
