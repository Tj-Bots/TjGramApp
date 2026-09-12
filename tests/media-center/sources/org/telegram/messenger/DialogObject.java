package org.telegram.messenger;
public class DialogObject {
    public static boolean isEncryptedDialog(long id) { return id == Long.MAX_VALUE; }
}
