package org.telegram.tgnet;
public class TLRPC {
    public static class Dialog { public long id; public Dialog(long id) { this.id = id; } }
    public static class TL_dialogFolder extends Dialog { public TL_dialogFolder(long id) { super(id); } }
}
