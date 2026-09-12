package org.telegram.messenger;
public final class MessagesController {
    public static String saved = "";
    public static final class Preferences {
        public String getString(String key,String fallback) { return saved; }
        public Preferences edit() { return this; }
        public Preferences putString(String key,String value) { saved = value; return this; }
        public void apply() { }
    }
    public static Preferences getGlobalMainSettings() { return new Preferences(); }
}
