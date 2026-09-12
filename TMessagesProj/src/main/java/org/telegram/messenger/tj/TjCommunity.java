package org.telegram.messenger.tj;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/** Official TjGram destinations and installation-wide community prompt state. */
public final class TjCommunity {

    public static final String CHANNEL_USERNAME = "@TjGramApp";
    public static final String CHANNEL_URL = "https://t.me/TjGramApp";
    public static final String FAQ_USERNAME = "@TjGramFAQ";
    public static final String FAQ_URL = "https://t.me/TjGramFAQ/3";
    public static final String DISCUSSION_USERNAME = "@TjGramAppChat";
    public static final String DISCUSSION_URL = "https://t.me/TjGramAppChat";

    private static final String PREFERENCES = "tjcommunity";
    // v1 was accidentally shown in English when Telegram used an in-app Hebrew language pack.
    private static final String JOIN_PROMPT_SHOWN = "join_prompt_shown_v2";

    private TjCommunity() {
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public static boolean shouldShowJoinPrompt() {
        return !preferences().getBoolean(JOIN_PROMPT_SHOWN, false);
    }

    /** Mark before opening the URL so process death cannot make the prompt repeat. */
    public static void markJoinPromptShown() {
        preferences().edit().putBoolean(JOIN_PROMPT_SHOWN, true).apply();
    }
}
