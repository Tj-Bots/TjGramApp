package org.telegram.messenger;

import android.content.Context;
import android.content.res.Configuration;
import android.text.TextUtils;

import androidx.annotation.StringRes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Translations for the strings this fork adds.
 *
 * Telegram resolves UI text through its own cloud language packs and only falls back to Android
 * string resources for keys the server does not know about - which is every key we add. That
 * fallback goes through the application context's resource configuration, which is not a reliable
 * way to pick a values-xx folder here: the in-app language is chosen independently of the device
 * locale, and Java still reports Hebrew as the obsolete code "iw", so values-he was never matched
 * and every string we added rendered in English.
 *
 * So we do the lookup ourselves, keyed off the language the user actually picked inside the app.
 */
public class TjLocale {

    private static final Map<String, Map<String, String>> TRANSLATIONS = new HashMap<>();

    static {
        Map<String, String> m;

        m = new HashMap<>();
        m.put("TjLoginStart", "נא להזין את מספר הטלפון שלך\nאו **לבחור דרך נוספת להתחבר**");
        m.put("TjLoginOptions", "דרכים נוספות להתחבר");
        m.put("TjLoginQr", "התחברות באמצעות QR");
        m.put("TjLoginBot", "התחברות לחשבון בוט");
        m.put("TjLoginPasskey", "על התחברות במפתח גישה");
        m.put("TjLoginPasskeyInfo", "טלגרם מגבילה כרגע את מפתחות הגישה לדומיין telegram.org ולזהויות האפליקציות הרשמיות שלה. TjGram אינה יכולה לאמת את השיוך הזה. זו אינה תקלה בטביעת האצבע או בטלפון שלך. אפשר להתחבר באמצעות מספר טלפון או QR.");
        m.put("TjLoginBotInfo", "יש להזין את הטוקן מ־BotFather של בוט שבשליטתך. הטוקן הוא סיסמה לכל דבר. לבוט יש הרשאות שונות: לא ניתן לטעון היסטוריית שיחות כמו בחשבון רגיל. הודעות שמתקבלות כאן נשמרות במכשיר. סנכרון אנשי קשר כבוי.");
        m.put("TjLoginBotToken", "טוקן הבוט");
        m.put("TjLoginConnect", "התחבר");
        m.put("TjLoginConnecting", "מתחבר…");
        m.put("TjLoginInvalidBotToken", "טוקן הבוט אינו תקין או בוטל. יש לבדוק אותו ב־BotFather ולנסות שוב.");
        m.put("TjLoginAlreadyConnected", "החשבון כבר מחובר בסביבה זו. אפשר לבחור אותו בתפריט החשבונות.");
        m.put("TjLoginQrInfo", "במכשיר אחר שבו החשבון מחובר, יש לפתוח את טלגרם ← הגדרות ← מכשירים ← קישור מכשיר, לסרוק את הקוד ולאשר את ההתחברות. אין לאשר התחברות שלא ביקשת.");
        m.put("TjLoginQrWaiting", "ממתין לאישור. הקוד מתחלף אוטומטית.");
        m.put("TjLoginFailed", "לא ניתן להשלים את ההתחברות. יש לבדוק את החיבור ולנסות שוב.");
        m.put("TjLoginRetry", "לחיצה לניסיון נוסף");
        m.put("TjArchivedChats", "\u05e6\u05f3\u05d0\u05d8\u05d9\u05dd \u05d1\u05d0\u05e8\u05db\u05d9\u05d5\u05df");
        m.put("TjAudioTrack", "\u05e8\u05e6\u05d5\u05e2\u05ea \u05e9\u05de\u05e2");
        m.put("TjBotApiIds", "\u05d4\u05e6\u05d2 \u05de\u05d6\u05d4\u05d9 \u05e6\u05f3\u05d0\u05d8 \u05d1\u05e4\u05d5\u05e8\u05de\u05d8 bot API");
        m.put("TjBotApiIdsInfo", "\u05de\u05e6\u05d9\u05d2 \u200e-100\u2026\u200e \u05dc\u05e1\u05d5\u05e4\u05e8-\u05d2\u05e8\u05d5\u05e4\u05d9\u05dd \u05d5\u05e2\u05e8\u05d5\u05e6\u05d9\u05dd \u05d1\u05de\u05e7\u05d5\u05dd \u05d4\u05de\u05d6\u05d4\u05d4 \u05d4\u05e4\u05e0\u05d9\u05de\u05d9 \u05d4\u05d2\u05d5\u05dc\u05de\u05d9.");
        m.put("TjBots", "\u05d1\u05d5\u05d8\u05d9\u05dd");
        m.put("TjChannels", "\u05e2\u05e8\u05d5\u05e6\u05d9\u05dd");
        m.put("TjChatCounters", "\u05de\u05d5\u05e0\u05d9 \u05e6\u05f3\u05d0\u05d8\u05d9\u05dd");
        m.put("TjChatCountersInfo", "\u05d4\u05de\u05d5\u05e0\u05d9\u05dd \u05de\u05d7\u05d5\u05e9\u05d1\u05d9\u05dd \u05de\u05d4\u05e6'\u05d0\u05d8\u05d9\u05dd \u05e9\u05db\u05d1\u05e8 \u05e0\u05d8\u05e2\u05e0\u05d5 \u05d1\u05de\u05db\u05e9\u05d9\u05e8 \u05d4\u05d6\u05d4.");
        m.put("TjChatCountersLoading", "\u05e1\u05d5\u05e4\u05e8 \u05d0\u05ea \u05d4\u05e6'\u05d0\u05d8\u05d9\u05dd \u05e9\u05dc\u05da\u2026");
        m.put("TjChatCountersUpdated", "\u05e2\u05d5\u05d3\u05db\u05df \u05dc\u05d0\u05d7\u05e8\u05d5\u05e0\u05d4: %1$s");
        m.put("TjChatIdHeader", "\u05de\u05d6\u05d4\u05d9 \u05e6'\u05d0\u05d8");
        m.put("TjChatsHeader", "\u05e6'\u05d0\u05d8\u05d9\u05dd");
        m.put("TjContactsCount", "\u05d0\u05e0\u05e9\u05d9 \u05e7\u05e9\u05e8");
        m.put("TjCopyImage", "\u05d4\u05e2\u05ea\u05e7 \u05ea\u05de\u05d5\u05e0\u05d4");
        m.put("TjCopyMessageLink", "\u05d4\u05e2\u05ea\u05e7 \u05e7\u05d9\u05e9\u05d5\u05e8 \u05dc\u05d4\u05d5\u05d3\u05e2\u05d4");
        m.put("TjCopyThumbnail", "\u05d4\u05e2\u05ea\u05e7 \u05ea\u05de\u05d5\u05e0\u05d4 \u05de\u05de\u05d5\u05d6\u05e2\u05e8\u05ea");
        m.put("TjDeleteForBoth", "\u05de\u05d7\u05e7 \u05dc\u05e9\u05e0\u05d9 \u05d4\u05e6\u05d3\u05d3\u05d9\u05dd \u05db\u05d1\u05e8\u05d9\u05e8\u05ea \u05de\u05d7\u05d3\u05dc");
        m.put("TjFilterAdmins", "\u05de\u05e0\u05d4\u05dc\u05d9\u05dd \u05d1\u05dc\u05d1\u05d3");
        m.put("TjFilterAll", "\u05db\u05dc \u05d4\u05d7\u05d1\u05e8\u05d9\u05dd");
        m.put("TjFilterBots", "\u05d1\u05d5\u05d8\u05d9\u05dd \u05d1\u05dc\u05d1\u05d3");
        m.put("TjFilterContacts", "\u05d0\u05e0\u05e9\u05d9 \u05e7\u05e9\u05e8 \u05d1\u05dc\u05d1\u05d3");
        m.put("TjFilterMembers", "\u05e1\u05d9\u05e0\u05d5\u05df \u05d7\u05d1\u05e8\u05d9\u05dd");
        m.put("TjFilterMembersOnly", "\u05d7\u05d1\u05e8\u05d9\u05dd \u05d1\u05dc\u05d1\u05d3");
        m.put("TjFolderIcon", "\u05e1\u05de\u05dc \u05d4\u05ea\u05d9\u05e7\u05d9\u05d9\u05d4");
        m.put("TjFolderManaging", "\u05e0\u05d9\u05d4\u05d5\u05dc");
        m.put("TjLocalFolders", "תיקיות מקומיות של TjGram");
        m.put("TjLocalFoldersInfo", "בחר תיקיות לחשבון הזה. הן נשמרות רק במכשיר הזה, בנפרד מתיקיות הענן שלך. אפשר לשנות את הבחירה בכל עת בהגדרות התיקיות.");
        m.put("TjLocalFoldersReset", "אפס תיקיות מקומיות");
        m.put("TjLocalFoldersResetInfo", "לאפס את בחירת התיקיות המקומיות, הסדר והמועדפים בחשבון הזה? הצ׳אטים ותיקיות הענן לא יימחקו. לאחר האיפוס אפשר לבחור תיקיות מחדש.");
        m.put("TjLocalFolderInfo", "תיקייה מקומית שמתעדכנת אוטומטית");
        m.put("TjLocalManagingInfo", "קבוצות וערוצים שבבעלותך או בניהולך. מתעדכן לפי ההרשאות הידועות לאפליקציה.");
        m.put("TjLocalFavoritesInfo", "תיקייה מקומית עם צ׳אטים שתבחר בעריכת התיקייה");
        m.put("TjLocalPrivate", "צ׳אטים פרטיים");
        m.put("TjLocalUnread", "לא נקראו");
        m.put("TjLocalUnmuted", "לא מושתקים");
        m.put("TjLocalFavorites", "מועדפים");
        m.put("TjFolderTabIconAndName", "\u05e1\u05de\u05dc \u05d5\u05e9\u05dd");
        m.put("TjFolderTabIconOnly", "\u05e1\u05de\u05dc \u05d1\u05dc\u05d1\u05d3");
        m.put("TjFolderTabNameOnly", "\u05e9\u05dd \u05d1\u05dc\u05d1\u05d3");
        m.put("TjFolderTabStyle", "\u05dc\u05e9\u05d5\u05e0\u05d9\u05d5\u05ea \u05ea\u05d9\u05e7\u05d9\u05d5\u05ea");
        m.put("TjFolderTabStyleInfo", "\u05d1\u05d7\u05e8 \u05d0\u05dd \u05d4\u05dc\u05e9\u05d5\u05e0\u05d9\u05d5\u05ea \u05d9\u05e6\u05d9\u05d2\u05d5 \u05e1\u05de\u05dc, \u05e9\u05dd, \u05d0\u05d5 \u05d0\u05ea \u05e9\u05e0\u05d9\u05d4\u05dd.");
        m.put("TjFoldersCount", "\u05ea\u05d9\u05e7\u05d9\u05d5\u05ea");
        m.put("TjForwardWithoutTag", "העבר ללא קרדיט");
        m.put("TjGeneralHeader", "\u05db\u05dc\u05dc\u05d9");
        m.put("TjGhostDontWarnAgain", "\u05d0\u05dc \u05ea\u05e6\u05d9\u05d2 \u05d0\u05ea \u05d6\u05d4 \u05e9\u05d5\u05d1");
        m.put("TjGhostMode", "\u05de\u05e6\u05d1 \u05e8\u05e4\u05d0\u05d9\u05dd");
        m.put("TjGhostModeInfo", "\u05de\u05e6\u05d1 \u05e8\u05e4\u05d0\u05d9\u05dd \u05de\u05e1\u05ea\u05d9\u05e8 \u05d0\u05ea \u05d4\u05e4\u05e2\u05d9\u05dc\u05d5\u05ea \u05e9\u05dc\u05da \u05de\u05d0\u05d7\u05e8\u05d9\u05dd. \u05d1\u05d7\u05e8 \u05de\u05d4 \u05d4\u05d5\u05d0 \u05db\u05d5\u05dc\u05dc.");
        m.put("TjGhostModeOff", "\u05db\u05d1\u05d4 \u05de\u05e6\u05d1 \u05e8\u05e4\u05d0\u05d9\u05dd");
        m.put("TjGhostModeOn", "\u05d4\u05e4\u05e2\u05dc \u05de\u05e6\u05d1 \u05e8\u05e4\u05d0\u05d9\u05dd");
        m.put("TjGhostSettings", "\u05d4\u05d2\u05d3\u05e8\u05d5\u05ea \u05de\u05e6\u05d1 \u05e8\u05e4\u05d0\u05d9\u05dd");
        m.put("TjGhostOnline", "\u05d4\u05d9\u05e9\u05d0\u05e8 \u05dc\u05d0 \u05de\u05d7\u05d5\u05d1\u05e8");
        m.put("TjGhostRead", "\u05d0\u05dc \u05ea\u05e9\u05dc\u05d7 \u05d0\u05d9\u05e9\u05d5\u05e8\u05d9 \u05e7\u05e8\u05d9\u05d0\u05d4");
        m.put("TjGhostStoryRead", "\u05d0\u05dc \u05ea\u05e9\u05dc\u05d7 \u05e6\u05e4\u05d9\u05d5\u05ea \u05d1\u05e1\u05d8\u05d5\u05e8\u05d9\u05d6");
        m.put("TjGhostTyping", "\u05d0\u05dc \u05ea\u05e6\u05d9\u05d2 \u05e9\u05d0\u05e0\u05d9 \u05de\u05e7\u05dc\u05d9\u05d3");
        m.put("TjGhostForceOffline", "\u05e9\u05dc\u05d7 \u05de\u05d9\u05d3 \u05e1\u05d8\u05d8\u05d5\u05e1 \u05dc\u05d0 \u05de\u05d7\u05d5\u05d1\u05e8");
        m.put("TjGhostReadAfterReply", "\u05e1\u05de\u05df \u05db\u05e0\u05e7\u05e8\u05d0 \u05dc\u05d0\u05d7\u05e8 \u05e9\u05d0\u05e0\u05d9 \u05de\u05e9\u05d9\u05d1");
        m.put("TjGhostReadAfterReplyInfo", "\u05de\u05e1\u05de\u05df \u05d4\u05d5\u05d3\u05e2\u05d4 \u05db\u05e0\u05e7\u05e8\u05d0\u05d4 \u05db\u05e9\u05de\u05e9\u05d9\u05d1\u05d9\u05dd \u05dc\u05d4 \u05d0\u05d5 \u05de\u05d2\u05d9\u05d1\u05d9\u05dd \u05e2\u05dc\u05d9\u05d4.");
        m.put("TjGhostScheduleMessages", "\u05e9\u05dc\u05d7 \u05d1\u05d0\u05de\u05e6\u05e2\u05d5\u05ea \u05d4\u05d5\u05d3\u05e2\u05d5\u05ea \u05de\u05ea\u05d5\u05d6\u05de\u05e0\u05d5\u05ea");
        m.put("TjGhostScheduleMessagesInfo", "\u05de\u05ea\u05d6\u05de\u05df \u05d4\u05d5\u05d3\u05e2\u05d5\u05ea \u05d9\u05d5\u05e6\u05d0\u05d5\u05ea \u05dc\u05d6\u05de\u05df \u05e7\u05e6\u05e8 \u05db\u05d3\u05d9 \u05e9\u05dc\u05d0 \u05ea\u05d9\u05e8\u05d0\u05d4 \u05de\u05d7\u05d5\u05d1\u05e8. \u05d1\u05e8\u05e9\u05ea \u05dc\u05d0 \u05d9\u05e6\u05d9\u05d1\u05d4 \u05d4\u05e9\u05dc\u05d9\u05d7\u05d4 \u05e2\u05dc\u05d5\u05dc\u05d4 \u05dc\u05d4\u05ea\u05e2\u05db\u05d1.");
        m.put("TjGhostWarning", "\u05de\u05e6\u05d1 \u05e8\u05e4\u05d0\u05d9\u05dd \u05d0\u05d9\u05e0\u05d5 \u05ea\u05db\u05d5\u05e0\u05d4 \u05e8\u05e9\u05de\u05d9\u05ea \u05e9\u05dc \u05d8\u05dc\u05d2\u05e8\u05dd. \u05d9\u05d9\u05ea\u05db\u05df \u05e9\u05d8\u05dc\u05d2\u05e8\u05dd \u05ea\u05ea\u05d9\u05d9\u05d7\u05e1 \u05dc\u05d6\u05d4 \u05db\u05d4\u05ea\u05e0\u05d4\u05d2\u05d5\u05ea \u05d7\u05e8\u05d9\u05d2\u05d4, \u05d0\u05d6 \u05d4\u05e9\u05d9\u05de\u05d5\u05e9 \u05e2\u05dc \u05d0\u05d7\u05e8\u05d9\u05d5\u05ea\u05da.");
        m.put("TjGoToFirstMessage", "\u05e2\u05d1\u05d5\u05e8 \u05dc\u05d4\u05d5\u05d3\u05e2\u05d4 \u05d4\u05e8\u05d0\u05e9\u05d5\u05e0\u05d4");
        m.put("TjGroups", "\u05e7\u05d1\u05d5\u05e6\u05d5\u05ea");
        m.put("TjSupergroups", "סופר־קבוצות");
        m.put("TjSecretChats", "צ׳אטים סודיים");
        m.put("TjForums", "פורומים");
        m.put("TjCreatorHeader", "יוצר");
        m.put("TjAdministratorHeader", "מנהל");
        m.put("TjHidePhoneNumber", "\u05d4\u05e1\u05ea\u05e8 \u05d0\u05ea \u05de\u05e1\u05e4\u05e8 \u05d4\u05d8\u05dc\u05e4\u05d5\u05df \u05e9\u05dc\u05d9");
        m.put("TjHidePhoneNumberInfo", "\u05de\u05e1\u05ea\u05d9\u05e8 \u05d0\u05ea \u05de\u05e1\u05e4\u05e8 \u05d4\u05d8\u05dc\u05e4\u05d5\u05df \u05e9\u05dc\u05da \u05d1\u05ea\u05e4\u05e8\u05d9\u05d8 \u05d4\u05e6\u05d3 \u05d5\u05d1\u05e4\u05e8\u05d5\u05e4\u05d9\u05dc \u05e9\u05dc\u05da.");
        m.put("TjHidePinnedMessage", "\u05d4\u05e1\u05ea\u05e8 \u05d4\u05d5\u05d3\u05e2\u05d4 \u05de\u05d5\u05e6\u05de\u05d3\u05ea");
        m.put("TjMessageInfo", "\u05e4\u05e8\u05d8\u05d9 \u05d4\u05d5\u05d3\u05e2\u05d4");
        m.put("TjMessageMenuHeader", "\u05ea\u05e4\u05e8\u05d9\u05d8 \u05d4\u05d5\u05d3\u05e2\u05d4");
        m.put("TjMessageMenuInfo", "\u05d1\u05d7\u05e8 \u05d0\u05d9\u05dc\u05d5 \u05de\u05db\u05e4\u05ea\u05d5\u05e8\u05d9 TjGram \u05d9\u05d5\u05e6\u05d2\u05d5 \u05d1\u05dc\u05d7\u05d9\u05e6\u05d4 \u05d0\u05e8\u05d5\u05db\u05d4 \u05e2\u05dc \u05d4\u05d5\u05d3\u05e2\u05d4.");
        m.put("TjMutedChats", "\u05e6\u05f3\u05d0\u05d8\u05d9\u05dd \u05de\u05d5\u05e9\u05ea\u05e7\u05d9\u05dd");
        m.put("TjMyProfile", "\u05d4\u05e4\u05e8\u05d5\u05e4\u05d9\u05dc \u05e9\u05dc\u05d9");
        m.put("TjPrivateChats", "\u05e6\u05f3\u05d0\u05d8\u05d9\u05dd \u05e4\u05e8\u05d8\u05d9\u05d9\u05dd");
        m.put("TjReplyPrivately", "\u05d4\u05e9\u05d1 \u05d1\u05e4\u05e8\u05d8\u05d9");
        m.put("TjSaveToSaved", "\u05e9\u05de\u05d5\u05e8 \u05d1\u05d4\u05d5\u05d3\u05e2\u05d5\u05ea \u05e9\u05de\u05d5\u05e8\u05d5\u05ea");
        m.put("TjSettings", "\u05d4\u05d2\u05d3\u05e8\u05d5\u05ea TjGram");
        m.put("TjShowCallButton", "\u05d4\u05e6\u05d2 \u05db\u05e4\u05ea\u05d5\u05e8 \u05e9\u05d9\u05d7\u05d4 \u05d1\u05e6\u05f3\u05d0\u05d8\u05d9\u05dd \u05e4\u05e8\u05d8\u05d9\u05d9\u05dd");
        m.put("TjShowCallButtonInfo", "\u05db\u05d1\u05e8\u05d9\u05e8\u05ea \u05de\u05d7\u05d3\u05dc \u05de\u05d5\u05e6\u05d2 \u05e1\u05de\u05dc \u05d7\u05d9\u05e4\u05d5\u05e9 \u05d1\u05de\u05e7\u05d5\u05dd \u05e1\u05de\u05dc \u05d4\u05e9\u05d9\u05d7\u05d4 \u05d1\u05e6\u05f3\u05d0\u05d8\u05d9\u05dd \u05e4\u05e8\u05d8\u05d9\u05d9\u05dd.");
        m.put("TjShowPinnedMessage", "\u05d4\u05e6\u05d2 \u05d4\u05d5\u05d3\u05e2\u05d4 \u05de\u05d5\u05e6\u05de\u05d3\u05ea");
        m.put("TjSubtitleAuto", "\u05d4\u05e4\u05e2\u05dc \u05db\u05ea\u05d5\u05d1\u05d9\u05d5\u05ea \u05d0\u05d5\u05d8\u05d5\u05de\u05d8\u05d9\u05ea");
        m.put("TjSubtitleAutoInfo", "\u05d1\u05d5\u05d7\u05e8 \u05e8\u05e6\u05d5\u05e2\u05ea \u05db\u05ea\u05d5\u05d1\u05d9\u05d5\u05ea \u05d1\u05e9\u05e4\u05d4 \u05e9\u05dc\u05da \u05db\u05e9\u05d9\u05e9 \u05db\u05d6\u05d5 \u05d1\u05d5\u05d9\u05d3\u05d0\u05d5, \u05d5\u05d0\u05d7\u05e8\u05ea \u05d1\u05d0\u05e0\u05d2\u05dc\u05d9\u05ea.");
        m.put("TjSubtitleFontSize", "\u05d2\u05d5\u05d3\u05dc \u05d2\u05d5\u05e4\u05df");
        m.put("TjSubtitlePosition", "\u05de\u05d9\u05e7\u05d5\u05dd");
        m.put("TjSubtitleSettings", "\u05d4\u05d2\u05d3\u05e8\u05d5\u05ea \u05db\u05ea\u05d5\u05d1\u05d9\u05d5\u05ea");
        m.put("TjSubtitleSizeHuge", "\u05d2\u05d3\u05d5\u05dc \u05de\u05d0\u05d5\u05d3");
        m.put("TjSubtitleSizeLarge", "\u05d2\u05d3\u05d5\u05dc");
        m.put("TjSubtitleSizeMedium", "\u05d1\u05d9\u05e0\u05d5\u05e0\u05d9");
        m.put("TjSubtitleSizeSmall", "\u05e7\u05d8\u05df");
        m.put("TjSubtitleStyle", "\u05e1\u05d2\u05e0\u05d5\u05df");
        m.put("TjSubtitleStyleBox", "\u05e8\u05e7\u05e2 \u05de\u05dc\u05d0");
        m.put("TjSubtitleStyleOutline", "\u05de\u05e1\u05d2\u05e8\u05ea");
        m.put("TjSubtitleStyleShadow", "\u05e6\u05dc");
        m.put("TjSubtitles", "\u05db\u05ea\u05d5\u05d1\u05d9\u05d5\u05ea");
        m.put("TjSubtitlesOff", "\u05db\u05d1\u05d5\u05d9\u05d5\u05ea");
        m.put("TjTotalChats", "\u05e1\u05d4\u05f4\u05db \u05e6\u05f3\u05d0\u05d8\u05d9\u05dd");
        m.put("TjUnreadChats", "\u05e6\u05f3\u05d0\u05d8\u05d9\u05dd \u05e9\u05dc\u05d0 \u05e0\u05e7\u05e8\u05d0\u05d5");
        m.put("TjPrivacyArchive", "פרטיות וארכיון הודעות");
        m.put("TjPrivacyArchiveInfo", "שליטה בהודעות שנשמרות מקומית, העברה מוגנת ומסנני הודעות.");
        m.put("TjArchiveHeader", "ארכיון הודעות מקומי");
        m.put("TjSaveDeletedMessages", "שמור הודעות שנמחקו");
        m.put("TjSaveEditedMessages", "שמור היסטוריית עריכות");
        m.put("TjSaveFormatting", "שמור עיצוב טקסט");
        m.put("TjSaveReactions", "שמור תגובות");
        m.put("TjSaveBotMessages", "שמור הודעות של בוטים");
        m.put("TjArchiveInfo", "העותקים נשמרים רק במכשיר הזה. אי אפשר לשחזר הודעות שנמחקו לפני הפעלת האפשרות.");
        m.put("TjArchiveMediaHeader", "קבצים מצורפים שמורים");
        m.put("TjSaveMedia", "העתק מדיה שכבר הורדה");
        m.put("TjSavePrivateMedia", "צ׳אטים פרטיים");
        m.put("TjSavePublicGroupMedia", "קבוצות ציבוריות");
        m.put("TjSavePrivateGroupMedia", "קבוצות פרטיות");
        m.put("TjSavePublicChannelMedia", "ערוצים ציבוריים");
        m.put("TjSavePrivateChannelMedia", "ערוצים פרטיים");
        m.put("TjArchiveMediaInfo", "אפשר להעתיק רק מדיה שכבר הורדה למכשיר. הקבצים נשמרים בתיקייה הפרטית של TjGram.");
        m.put("TjDeletedMarker", "סימון הודעה שנמחקה");
        m.put("TjEditedMarker", "סימון הודעה שנערכה");
        m.put("TjClearArchive", "נקה את הארכיון המקומי");
        m.put("TjClearArchiveTitle", "לנקות את ארכיון ההודעות?");
        m.put("TjClearArchiveText", "כל ההודעות המחוקות והגרסאות שנשמרו מקומית יימחקו. אי אפשר לבטל את הפעולה.");
        m.put("TjApproveAllRequestsTitle", "לאשר את כל הבקשות?");
        m.put("TjApproveAllRequestsGroup", "כל מי שממתין להצטרף לקבוצה הזו יתווסף.");
        m.put("TjApproveAllRequestsChannel", "כל מי שממתין להצטרף לערוץ הזה יתווסף.");
        m.put("TjApproveAllRequests", "אשר את כולם");
        m.put("TjApproveAllRequestsProgress", "מאשר בקשות…");
        m.put("TjApproveAllRequestsDone", "אושרו %1$d בקשות");
        m.put("TjApproveAllRequestsFailed", "לא הצלחנו לאשר את כל הבקשות. אושרו %1$d.");
        m.put("TjOneTimeSaveFailed", "המדיה החד־פעמית כבר לא שמורה במכשיר הזה.");
        m.put("TjBackgroundConnection", "חיבור ברקע");
        m.put("TjBackgroundConnectionInfo", "שומר על TjGram מחובר גם כשהאפליקציה סגורה כדי שההודעות יגיעו מיד. מציג התראה שקטה שאנדרואיד דורש בשביל זה.");
        m.put("TjBackgroundConnectionRunning", "מחובר ברקע");
        m.put("TjDisableBatteryOptimization", "בטל אופטימיזציית סוללה");
        m.put("TjOnlineIndicator", "הצג מצב מחובר");
        m.put("TjOnlineIndicatorInfo", "מוסיף נקודה ליד אנשים ברשימת הצ׳אטים וברשימות חברי הקבוצה: צבעונית כשהם מחוברים, ניטרלית כשלא.");
        m.put("TjDirectStreaming", "נגן קובצי וידאו באפליקציה");
        m.put("TjShowUserMessages", "חפש הודעות");
        m.put("TjViewAdminRights", "הרשאות מנהל");
        m.put("TjChatInfo", "מידע");
        m.put("TjChatInfoDescription", "כל מה שהצ׳אט הזה מאפשר לך לראות. פעולות ניהול מופיעות רק אם יש לך הרשאה.");
        m.put("TjChatPermissions", "הרשאות");
        m.put("TjChatPermissionsReadOnly", "מה מותר לחברים רגילים בקבוצה הזו. רק מנהלים יכולים לשנות את זה.");
        m.put("TjMemberJoinedDate", "הצטרף/ה ב-%1$s");
        m.put("TjDirectStreamingInfo", "מנגן בתוך האפליקציה סרטונים שנשלחו כקובץ רגיל, ומתחיל אותם ברגע שהגיע מספיק במקום לחכות להורדה מלאה. תומך ב-mp4, mkv, webm, mov, avi, ts ו-flv; פורמטים אחרים ימשיכו להיפתח בנגן חיצוני. הניגון עדיין תלוי במה שהמכשיר יודע לפענח.");
        m.put("TjProtectedScreenshots", "אפשר צילומי מסך");
        m.put("TjProtectedScreenshotsInfo", "מאפשר לצלם מסך בקבוצות ובערוצים שמבקשים לחסום את זה. צ׳אטים סודיים נשארים מוגנים.");
        m.put("TjArchiveCleared", "ארכיון ההודעות המקומי נוקה");
        m.put("TjProtectedForwarding", "העלה מחדש הודעות מוגנות");
        m.put("TjProtectedForwardingInfo", "כש־Telegram חוסם העברה רגילה, יישלח עותק חדש של הטקסט או המדיה שהורדה. פרטי המקור לא יישמרו.");
        m.put("TjMessageFilters", "מסנני הודעות Regex");
        m.put("TjFiltersInChats", "החל מסננים בתוך צ׳אטים");
        m.put("TjFiltersCaseInsensitive", "התעלם מהבדל בין אותיות");
        m.put("TjFilterExpressions", "ביטויי סינון");
        m.put("TjFiltersInfo", "ביטויים רגולריים מסתירים הודעות תואמות רק במכשיר. ביטוי לא תקין יידחה ויוצג בעורך.");
        m.put("TjSettingsInfo", "מצב רפאים, פרטיות, מראה ואפשרויות מתקדמות");
        m.put("TjGhostSendWithoutSound", "שלח ללא צליל");
        m.put("TjGhostSendWithoutSoundInfo", "שולח הודעות יוצאות ללא צליל כברירת מחדל כל עוד מצב רפאים פעיל.");
        m.put("TjGhostOptionLocked", "האפשרות הזו לא תשתנה יחד עם מתג מצב הרפאים.");
        m.put("TjGhostOptionUnlocked", "האפשרות הזו תשתנה יחד עם מתג מצב הרפאים.");
        m.put("TjLikelyOffline", "כנראה לא מחובר");
        m.put("TjDimDeletedMessages", "עמעם הודעות שנמחקו");
        m.put("TjArchiveLimit", "מגבלת אחסון לארכיון");
        m.put("TjDeleteKeepLocally", "השאר את ההודעה המחוקה במכשיר");
        m.put("TjEditHistory", "היסטוריית עריכות");
        m.put("TjEditHistoryCount", "היסטוריית עריכות (%1$d)");
        m.put("TjReadUntil", "סמן כנקרא עד כאן");
        m.put("TjMarkMediaViewed", "סמן את המדיה כנצפתה");
        m.put("TjViewOnceSaveFailed", "לא ניתן היה לשמור את המדיה החד־פעמית. היא לא סומנה כנצפתה.");
        m.put("TjViewOnceSaveFailedTitle", "המדיה לא נשמרה");
        m.put("TjViewOnceSaveFailedConfirm", "TjGram לא הצליח לשמור עותק פרטי של המדיה החד־פעמית. לסמן אותה כנצפתה בכל זאת? ייתכן שהיא תיעלם לצמיתות.");
        m.put("TjMarkViewedAnyway", "סמן בכל זאת");
        m.put("TjClearFromCache", "נקה מהמטמון");
        m.put("TjEnableChatGhost", "הפעל מצב רפאים");
        m.put("TjDisableChatGhost", "כבה מצב רפאים");
        m.put("TjChatGhostEnabled", "מצב רפאים הופעל בצ׳אט הזה");
        m.put("TjChatGhostDisabled", "מצב רפאים כובה בצ׳אט הזה");
        m.put("TjChatGhostInfo", "אישורי קריאה והקלדה מוסתרים בצ׳אט הזה. מצב מקוון חל על החשבון כולו.");
        m.put("TjChatMenu", "TjGram");
        m.put("TjChatGhostSettings", "הגדרות רפאים");
        m.put("TjChatGhostBehavior", "התנהגות הצ׳אט");
        m.put("TjChatGhostMode", "מצב רפאים בצ׳אט הזה");
        m.put("TjChatGhostReadReceipts", "אישורי קריאה");
        m.put("TjChatGhostTypingStatus", "מצב הקלדה");
        m.put("TjChatGhostInherit", "פעל לפי ההגדרות הכלליות");
        m.put("TjChatGhostOn", "מופעל בצ׳אט הזה");
        m.put("TjChatGhostOff", "כבוי בצ׳אט הזה");
        m.put("TjChatGhostHide", "הסתר בצ׳אט הזה");
        m.put("TjChatGhostSend", "שלח כרגיל בצ׳אט הזה");
        m.put("TjChatGhostEffectiveOn", "מוסתר כרגע");
        m.put("TjChatGhostEffectiveOff", "נשלח כרגיל כרגע");
        m.put("TjChatGhostModeInfo", "בחירה נקודתית לצ׳אט גוברת על מתג מצב הרפאים הכללי. האפשרות לפעול לפי ההגדרות הכלליות משתנה אוטומטית יחד עם המתג הכללי.");
        m.put("TjChatGhostOnlineInfo", "מצב מקוון חל על החשבון כולו ואי אפשר לשנות אותו עבור צ׳אט אחד בלבד.");
        m.put("TjChatGhostReset", "אפס חריגות");
        m.put("TjChatGhostResetDone", "הצ׳אט פועל כעת לפי הגדרות מצב הרפאים הכלליות");
        m.put("TjChatGhostResetTitle", "לאפס את הגדרות מצב הרפאים בצ׳אט הזה?");
        m.put("TjChatGhostResetText", "המצב הנקודתי והבחירות עבור אישורי קריאה והקלדה יוסרו.");
        m.put("TjChatGhostInherited", "פועל לפי הגדרות מצב הרפאים הכלליות");
        m.put("TjDeletedMarkerTrash", "פח אפור");
        m.put("TjDeletedMarkerRedX", "איקס אדום");
        m.put("TjDeletedMarkerDarkX", "איקס כהה");
        m.put("TjDeletedMarkerBroom", "מטאטא");
        m.put("TjDeletedTime", "%1$s %2$s");
        m.put("TjDeletedEditedTime", "%1$s (%2$s) %3$s");
        m.put("TjSecretMediaTime", "%1$s · %2$s");
        m.put("TjSecretMediaViewedTime", "%1$s · %2$s ✓✓");
        m.put("TjAccessibilityDeletedMessage", "הודעה שנמחקה ונשמרה במכשיר");
        m.put("TjAccessibilityViewedOnce", "מדיה חד־פעמית שסומנה כנצפתה");
        m.put("TjNoEditHistory", "אין עריכות שנשמרו מקומית");
        m.put("TjEditRevision", "גרסה %1$d");
        m.put("TjExtras", "תוספות");
        m.put("TjLocalPremium", "פרימיום מקומי");
        m.put("TjLocalPremiumInfo", "גורם ללקוח להתנהג מקומית כחשבון פרימיום. יכולות פרימיום בצד השרת עדיין עשויות להידחות על ידי טלגרם.");
        m.put("TjHideSponsored", "הסתר פרסומות");
        m.put("TjCrashReports", "שלח דוחות קריסה");
        m.put("TjCrashReportsInfo", "כשהאפשרות פעילה, אבחון טכני של קריסות נשלח אל Firebase Crashlytics. האפשרות כבויה כברירת מחדל.");
        m.put("TjShowGhostInDrawer", "הצג מצב רפאים במגירה");
        m.put("TjShowKillInDrawer", "הצג סגירת אפליקציה במגירה");
        m.put("TjKillApp", "סגור את האפליקציה");
        m.put("TjKeepAlive", "שירות שמירה ברקע");
        m.put("TjKeepAliveInfo", "מפעיל מחדש את שירות ההתראות ברקע אם Android עוצר אותו, לקבלת התראות אמינות יותר.");
        m.put("TjSync", "סנכרון מצב קריאה");
        m.put("TjSyncEnabled", "הפעל סנכרון");
        m.put("TjSyncSecure", "השתמש בחיבור מאובטח");
        m.put("TjSyncServer", "כתובת שרת");
        m.put("TjSyncToken", "אסימון גישה");
        m.put("TjSyncStatus", "מצב חיבור");
        m.put("TjSyncDeviceId", "מזהה מכשיר");
        m.put("TjSyncLastSent", "אירוע אחרון שנשלח");
        m.put("TjSyncLastReceived", "אירוע אחרון שהתקבל");
        m.put("TjSyncRegisterStatus", "קוד מצב רישום");
        m.put("TjSyncNever", "אף פעם");
        m.put("TjSyncForce", "כפה סנכרון מלא");
        m.put("TjSyncInfo", "מסנכרן את מיקום הקריאה בין מכשירים דרך השרת והאסימון שסיפקת. לא מוגדר שירות חיצוני כברירת מחדל.");
        m.put("TjSyncConnected", "מחובר");
        m.put("TjSyncConnecting", "מתחבר…");
        m.put("TjSyncError", "שגיאת חיבור");
        m.put("TjSyncMissingConfig", "חסרים שרת או אסימון");
        m.put("TjSyncDisabled", "כבוי");
        m.put("TjXiaomiSuccess", "יש לך טלפון טוב.");
        m.put("TjXiaomiFailure", "כדאי להתקין ROM מותאם אישית.");
        m.put("TjJoinChannelTitle", "נשארים מעודכנים עם TjGram");
        m.put("TjJoinChannelMessage", "אפשר להצטרף לערוץ הרשמי של TjGram לקבלת עדכונים, גרסאות חדשות והודעות חשובות. ההודעה הזו תוצג פעם אחת בלבד.");
        m.put("TjJoinChannelAction", "הצטרפות לערוץ");
        m.put("TjNotNow", "לא עכשיו");
        m.put("TjCategories", "קטגוריות");
        m.put("TjMenuShortcuts", "שורת קיצורים");
        m.put("TjMenuShortcutsChoose", "בחירת הקיצורים");
        m.put("TjMenuShortcutsInfo", "הפעולות שתבחר יוצאות מהרשימה ויושבות כשורת סמלים בתחתית תפריט ההודעה, לפי הסדר שבחרת אותן.");
        m.put("TjMenuShortcutsLimit", "עד %1$d קיצורים");
        m.put("TjLinks", "קישורים");
        m.put("TjCustomization", "התאמה אישית");
        m.put("TjChannel", "ערוץ");
        m.put("TjDiscussions", "דיונים");
        m.put("TjArchiveInsights", "ארכיון ומעקב");
        m.put("TjDeletedAppearance", "מראה הודעות שנמחקו");
        m.put("TjDeletedAppearanceInfo", "כאן אפשר לבחור כיצד יוצגו בצ׳אטים הודעות שנמחקו והיסטוריית עריכות שנשמרו.");
        m.put("TjAdvancedSettings", "מתקדם");
        m.put("TjGhostModeDesc", "לקרוא ולהקליד בלי שיראו");
        m.put("TjGhostModeOnValue", "פעיל");
        m.put("TjArchiveInsightsDesc", "שמירת הודעות שנמחקו ונערכו במכשיר");
        m.put("TjMessageFiltersDesc", "הסתרת הודעות שמכילות מילים שתבחר");
        m.put("TjCustomizationDesc", "סימונים, תוספות, תפריט צד וסנכרון");
        m.put("TjAdvancedSettingsDesc", "צ׳אטים, תפריטים, רקע וצפייה ישירה");
        m.put("TjSettingsFooter", "TjGram הוא לקוח לא רשמי של טלגרם. כל מה שנשמר נשאר במכשיר הזה.");
        m.put("TjProtectedForwardFailed", "לא ניתן היה להכין %1$d הודעות מוגנות. שאר ההודעות נשלחו.");
        m.put("TjReorderAccount", "גרירה לשינוי סדר החשבון");
        TRANSLATIONS.put("he", m);

        m = new HashMap<>();
        m.put("TjArchivedChats", "\u0627\u0644\u062f\u0631\u062f\u0634\u0627\u062a \u0627\u0644\u0645\u0624\u0631\u0634\u0641\u0629");
        m.put("TjAudioTrack", "\u0627\u0644\u0645\u0633\u0627\u0631 \u0627\u0644\u0635\u0648\u062a\u064a");
        m.put("TjBotApiIds", "\u0625\u0638\u0647\u0627\u0631 \u0645\u0639\u0631\u0641\u0627\u062a \u0627\u0644\u062f\u0631\u062f\u0634\u0629 \u0628\u062a\u0646\u0633\u064a\u0642 bot API");
        m.put("TjBotApiIdsInfo", "\u064a\u0639\u0631\u0636 \u200e-100\u2026\u200e \u0644\u0644\u0645\u062c\u0645\u0648\u0639\u0627\u062a \u0627\u0644\u0643\u0628\u064a\u0631\u0629 \u0648\u0627\u0644\u0642\u0646\u0648\u0627\u062a \u0628\u062f\u0644\u0627\u064b \u0645\u0646 \u0627\u0644\u0645\u0639\u0631\u0641 \u0627\u0644\u062f\u0627\u062e\u0644\u064a.");
        m.put("TjBots", "\u0627\u0644\u0628\u0648\u062a\u0627\u062a");
        m.put("TjChannels", "\u0627\u0644\u0642\u0646\u0648\u0627\u062a");
        m.put("TjChatCounters", "\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u062f\u0631\u062f\u0634\u0629");
        m.put("TjChatCountersInfo", "\u064a\u062a\u0645 \u062d\u0633\u0627\u0628 \u0627\u0644\u0623\u0631\u0642\u0627\u0645 \u0645\u0646 \u0627\u0644\u0645\u062d\u0627\u062f\u062b\u0627\u062a \u0627\u0644\u0645\u062d\u0645\u0651\u0644\u0629 \u0639\u0644\u0649 \u0647\u0630\u0627 \u0627\u0644\u062c\u0647\u0627\u0632.");
        m.put("TjChatCountersLoading", "\u062c\u0627\u0631\u064d \u062d\u0633\u0627\u0628 \u0645\u062d\u0627\u062f\u062b\u0627\u062a\u0643\u2026");
        m.put("TjChatCountersUpdated", "\u0622\u062e\u0631 \u062a\u062d\u062f\u064a\u062b: %1$s");
        m.put("TjChatIdHeader", "\u0645\u0639\u0631\u0651\u0641\u0627\u062a \u0627\u0644\u0645\u062d\u0627\u062f\u062b\u0627\u062a");
        m.put("TjChatsHeader", "\u0627\u0644\u0645\u062d\u0627\u062f\u062b\u0627\u062a");
        m.put("TjContactsCount", "\u062c\u0647\u0627\u062a \u0627\u0644\u0627\u062a\u0635\u0627\u0644");
        m.put("TjCopyImage", "\u0646\u0633\u062e \u0627\u0644\u0635\u0648\u0631\u0629");
        m.put("TjCopyMessageLink", "\u0646\u0633\u062e \u0631\u0627\u0628\u0637 \u0627\u0644\u0631\u0633\u0627\u0644\u0629");
        m.put("TjCopyThumbnail", "\u0646\u0633\u062e \u0627\u0644\u0635\u0648\u0631\u0629 \u0627\u0644\u0645\u0635\u063a\u0631\u0629");
        m.put("TjDeleteForBoth", "\u0627\u0644\u062d\u0630\u0641 \u0644\u0644\u0637\u0631\u0641\u064a\u0646 \u0627\u0641\u062a\u0631\u0627\u0636\u064a\u064b\u0627");
        m.put("TjFilterAdmins", "\u0627\u0644\u0645\u0634\u0631\u0641\u0648\u0646 \u0641\u0642\u0637");
        m.put("TjFilterAll", "\u0643\u0644 \u0627\u0644\u0623\u0639\u0636\u0627\u0621");
        m.put("TjFilterBots", "\u0627\u0644\u0628\u0648\u062a\u0627\u062a \u0641\u0642\u0637");
        m.put("TjFilterContacts", "\u062c\u0647\u0627\u062a \u0627\u0644\u0627\u062a\u0635\u0627\u0644 \u0641\u0642\u0637");
        m.put("TjFilterMembers", "\u062a\u0635\u0641\u064a\u0629 \u0627\u0644\u0623\u0639\u0636\u0627\u0621");
        m.put("TjFilterMembersOnly", "\u0627\u0644\u0623\u0639\u0636\u0627\u0621 \u0641\u0642\u0637");
        m.put("TjFolderIcon", "\u0623\u064a\u0642\u0648\u0646\u0629 \u0627\u0644\u0645\u062c\u0644\u062f");
        m.put("TjFolderManaging", "\u0627\u0644\u0625\u062f\u0627\u0631\u0629");
        m.put("TjFolderTabIconAndName", "\u0623\u064a\u0642\u0648\u0646\u0629 \u0648\u0627\u0633\u0645");
        m.put("TjFolderTabIconOnly", "\u0623\u064a\u0642\u0648\u0646\u0629 \u0641\u0642\u0637");
        m.put("TjFolderTabNameOnly", "\u0627\u0633\u0645 \u0641\u0642\u0637");
        m.put("TjFolderTabStyle", "\u062a\u0628\u0648\u064a\u0628\u0627\u062a \u0627\u0644\u0645\u062c\u0644\u062f\u0627\u062a");
        m.put("TjFolderTabStyleInfo", "\u0627\u062e\u062a\u0631 \u0645\u0627 \u0625\u0630\u0627 \u0643\u0627\u0646\u062a \u0627\u0644\u062a\u0628\u0648\u064a\u0628\u0627\u062a \u062a\u0639\u0631\u0636 \u0623\u064a\u0642\u0648\u0646\u0629 \u0623\u0648 \u0627\u0633\u0645\u064b\u0627 \u0623\u0648 \u0643\u0644\u064a\u0647\u0645\u0627.");
        m.put("TjFoldersCount", "\u0627\u0644\u0645\u062c\u0644\u062f\u0627\u062a");
        m.put("TjForwardWithoutTag", "\u0625\u0639\u0627\u062f\u0629 \u062a\u0648\u062c\u064a\u0647 \u0628\u062f\u0648\u0646 \u0639\u0644\u0627\u0645\u0629");
        m.put("TjGeneralHeader", "\u0639\u0627\u0645");
        m.put("TjGhostDontWarnAgain", "\u0644\u0627 \u062a\u0639\u0631\u0636 \u0647\u0630\u0627 \u0645\u0631\u0629 \u0623\u062e\u0631\u0649");
        m.put("TjGhostMode", "\u0627\u0644\u0648\u0636\u0639 \u0627\u0644\u062e\u0641\u064a");
        m.put("TjGhostModeInfo", "\u064a\u062e\u0641\u064a \u0627\u0644\u0648\u0636\u0639 \u0627\u0644\u062e\u0641\u064a \u0646\u0634\u0627\u0637\u0643 \u0639\u0646 \u0627\u0644\u0622\u062e\u0631\u064a\u0646. \u0627\u062e\u062a\u0631 \u0645\u0627 \u064a\u0634\u0645\u0644\u0647.");
        m.put("TjGhostModeOff", "\u0625\u064a\u0642\u0627\u0641 \u0627\u0644\u0648\u0636\u0639 \u0627\u0644\u062e\u0641\u064a");
        m.put("TjGhostModeOn", "\u062a\u0641\u0639\u064a\u0644 \u0627\u0644\u0648\u0636\u0639 \u0627\u0644\u062e\u0641\u064a");
        m.put("TjGhostOnline", "\u0627\u0628\u0642\u064e \u063a\u064a\u0631 \u0645\u062a\u0635\u0644");
        m.put("TjGhostRead", "\u0644\u0627 \u062a\u0631\u0633\u0644 \u0625\u0634\u0639\u0627\u0631\u0627\u062a \u0627\u0644\u0642\u0631\u0627\u0621\u0629");
        m.put("TjGhostTyping", "\u0644\u0627 \u062a\u064f\u0638\u0647\u0631 \u0623\u0646\u0646\u064a \u0623\u0643\u062a\u0628");
        m.put("TjGhostWarning", "\u0627\u0644\u0648\u0636\u0639 \u0627\u0644\u062e\u0641\u064a \u0644\u064a\u0633 \u0645\u064a\u0632\u0629 \u0631\u0633\u0645\u064a\u0629 \u0641\u064a \u062a\u064a\u0644\u064a\u062c\u0631\u0627\u0645. \u0642\u062f \u064a\u0639\u062a\u0628\u0631\u0647 \u062a\u064a\u0644\u064a\u062c\u0631\u0627\u0645 \u0633\u0644\u0648\u0643\u064b\u0627 \u063a\u064a\u0631 \u0645\u0639\u062a\u0627\u062f\u060c \u0641\u0627\u0633\u062a\u062e\u062f\u0645\u0647 \u0639\u0644\u0649 \u0645\u0633\u0624\u0648\u0644\u064a\u062a\u0643.");
        m.put("TjGoToFirstMessage", "\u0627\u0644\u0627\u0646\u062a\u0642\u0627\u0644 \u0625\u0644\u0649 \u0623\u0648\u0644 \u0631\u0633\u0627\u0644\u0629");
        m.put("TjGroups", "\u0627\u0644\u0645\u062c\u0645\u0648\u0639\u0627\u062a");
        m.put("TjHidePhoneNumber", "\u0625\u062e\u0641\u0627\u0621 \u0631\u0642\u0645 \u0647\u0627\u062a\u0641\u064a");
        m.put("TjHidePhoneNumberInfo", "\u064a\u062e\u0641\u064a \u0631\u0642\u0645 \u0647\u0627\u062a\u0641\u0643 \u0641\u064a \u0627\u0644\u0642\u0627\u0626\u0645\u0629 \u0627\u0644\u062c\u0627\u0646\u0628\u064a\u0629 \u0648\u0641\u064a \u0645\u0644\u0641\u0643 \u0627\u0644\u0634\u062e\u0635\u064a.");
        m.put("TjHidePinnedMessage", "\u0625\u062e\u0641\u0627\u0621 \u0627\u0644\u0631\u0633\u0627\u0644\u0629 \u0627\u0644\u0645\u062b\u0628\u062a\u0629");
        m.put("TjMessageInfo", "\u0645\u0639\u0644\u0648\u0645\u0627\u062a \u0627\u0644\u0631\u0633\u0627\u0644\u0629");
        m.put("TjMessageMenuHeader", "\u0642\u0627\u0626\u0645\u0629 \u0627\u0644\u0631\u0633\u0627\u0644\u0629");
        m.put("TjMessageMenuInfo", "\u0627\u062e\u062a\u0631 \u0639\u0646\u0627\u0635\u0631 TJ \u0627\u0644\u062a\u064a \u062a\u0638\u0647\u0631 \u0639\u0646\u062f \u0627\u0644\u0636\u063a\u0637 \u0645\u0637\u0648\u0644\u0627\u064b \u0639\u0644\u0649 \u0631\u0633\u0627\u0644\u0629.");
        m.put("TjMutedChats", "\u0627\u0644\u062f\u0631\u062f\u0634\u0627\u062a \u0627\u0644\u0645\u0643\u062a\u0648\u0645\u0629");
        m.put("TjMyProfile", "\u0645\u0644\u0641\u064a \u0627\u0644\u0634\u062e\u0635\u064a");
        m.put("TjPrivateChats", "\u0627\u0644\u062f\u0631\u062f\u0634\u0627\u062a \u0627\u0644\u062e\u0627\u0635\u0629");
        m.put("TjReplyPrivately", "\u0627\u0644\u0631\u062f \u0628\u0634\u0643\u0644 \u062e\u0627\u0635");
        m.put("TjSaveToSaved", "\u062d\u0641\u0638 \u0641\u064a \u0627\u0644\u0631\u0633\u0627\u0626\u0644 \u0627\u0644\u0645\u062d\u0641\u0648\u0638\u0629");
        m.put("TjSettings", "\u0625\u0639\u062f\u0627\u062f\u0627\u062a TjGram");
        m.put("TjShowCallButton", "\u0625\u0638\u0647\u0627\u0631 \u0632\u0631 \u0627\u0644\u0627\u062a\u0635\u0627\u0644 \u0641\u064a \u0627\u0644\u0645\u062d\u0627\u062f\u062b\u0627\u062a \u0627\u0644\u062e\u0627\u0635\u0629");
        m.put("TjShowCallButtonInfo", "\u0627\u0641\u062a\u0631\u0627\u0636\u064a\u064b\u0627 \u064a\u0638\u0647\u0631 \u0631\u0645\u0632 \u0627\u0644\u0628\u062d\u062b \u0628\u062f\u0644\u0627\u064b \u0645\u0646 \u0631\u0645\u0632 \u0627\u0644\u0627\u062a\u0635\u0627\u0644 \u0641\u064a \u0627\u0644\u0645\u062d\u0627\u062f\u062b\u0627\u062a \u0627\u0644\u062e\u0627\u0635\u0629.");
        m.put("TjShowPinnedMessage", "\u0625\u0638\u0647\u0627\u0631 \u0627\u0644\u0631\u0633\u0627\u0644\u0629 \u0627\u0644\u0645\u062b\u0628\u062a\u0629");
        m.put("TjSubtitleAuto", "\u062a\u0634\u063a\u064a\u0644 \u0627\u0644\u062a\u0631\u062c\u0645\u0629 \u062a\u0644\u0642\u0627\u0626\u064a\u064b\u0627");
        m.put("TjSubtitleAutoInfo", "\u064a\u062e\u062a\u0627\u0631 \u0645\u0633\u0627\u0631 \u062a\u0631\u062c\u0645\u0629 \u0628\u0644\u063a\u062a\u0643 \u0639\u0646\u062f \u062a\u0648\u0641\u0631\u0647 \u0641\u064a \u0627\u0644\u0641\u064a\u062f\u064a\u0648\u060c \u0648\u0625\u0644\u0627 \u0641\u0628\u0627\u0644\u0625\u0646\u062c\u0644\u064a\u0632\u064a\u0629.");
        m.put("TjSubtitleFontSize", "\u062d\u062c\u0645 \u0627\u0644\u062e\u0637");
        m.put("TjSubtitlePosition", "\u0627\u0644\u0645\u0648\u0636\u0639");
        m.put("TjSubtitleSettings", "\u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u062a\u0631\u062c\u0645\u0629");
        m.put("TjSubtitleSizeHuge", "\u0643\u0628\u064a\u0631 \u062c\u062f\u064b\u0627");
        m.put("TjSubtitleSizeLarge", "\u0643\u0628\u064a\u0631");
        m.put("TjSubtitleSizeMedium", "\u0645\u062a\u0648\u0633\u0637");
        m.put("TjSubtitleSizeSmall", "\u0635\u063a\u064a\u0631");
        m.put("TjSubtitleStyle", "\u0627\u0644\u0646\u0645\u0637");
        m.put("TjSubtitleStyleBox", "\u062e\u0644\u0641\u064a\u0629 \u0645\u0644\u0648\u0646\u0629");
        m.put("TjSubtitleStyleOutline", "\u0625\u0637\u0627\u0631");
        m.put("TjSubtitleStyleShadow", "\u0638\u0644");
        m.put("TjSubtitles", "\u0627\u0644\u062a\u0631\u062c\u0645\u0629");
        m.put("TjSubtitlesOff", "\u0625\u064a\u0642\u0627\u0641");
        m.put("TjTotalChats", "\u0625\u062c\u0645\u0627\u0644\u064a \u0627\u0644\u062f\u0631\u062f\u0634\u0627\u062a");
        m.put("TjUnreadChats", "\u0627\u0644\u062f\u0631\u062f\u0634\u0627\u062a \u063a\u064a\u0631 \u0627\u0644\u0645\u0642\u0631\u0648\u0621\u0629");
        TRANSLATIONS.put("ar", m);

        m = new HashMap<>();
        m.put("TjArchivedChats", "\u0410\u0440\u0445\u0438\u0432\u043d\u044b\u0435 \u0447\u0430\u0442\u044b");
        m.put("TjAudioTrack", "\u0410\u0443\u0434\u0438\u043e\u0434\u043e\u0440\u043e\u0436\u043a\u0430");
        m.put("TjBotApiIds", "\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c ID \u0447\u0430\u0442\u043e\u0432 \u0432 \u0444\u043e\u0440\u043c\u0430\u0442\u0435 bot API");
        m.put("TjBotApiIdsInfo", "\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442 \u200e-100\u2026\u200e \u0434\u043b\u044f \u0441\u0443\u043f\u0435\u0440\u0433\u0440\u0443\u043f\u043f \u0438 \u043a\u0430\u043d\u0430\u043b\u043e\u0432 \u0432\u043c\u0435\u0441\u0442\u043e \u0432\u043d\u0443\u0442\u0440\u0435\u043d\u043d\u0435\u0433\u043e ID.");
        m.put("TjBots", "\u0411\u043e\u0442\u044b");
        m.put("TjChannels", "\u041a\u0430\u043d\u0430\u043b\u044b");
        m.put("TjChatCounters", "\u0421\u0447\u0451\u0442\u0447\u0438\u043a\u0438 \u0447\u0430\u0442\u043e\u0432");
        m.put("TjChatCountersInfo", "\u0421\u0447\u0451\u0442\u0447\u0438\u043a\u0438 \u0441\u0447\u0438\u0442\u0430\u044e\u0442\u0441\u044f \u043f\u043e \u0447\u0430\u0442\u0430\u043c, \u0443\u0436\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d\u043d\u044b\u043c \u043d\u0430 \u044d\u0442\u043e\u043c \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u0435.");
        m.put("TjChatCountersLoading", "\u0421\u0447\u0438\u0442\u0430\u0435\u043c \u0432\u0430\u0448\u0438 \u0447\u0430\u0442\u044b\u2026");
        m.put("TjChatCountersUpdated", "\u041e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u043e: %1$s");
        m.put("TjChatIdHeader", "ID \u0447\u0430\u0442\u043e\u0432");
        m.put("TjChatsHeader", "\u0427\u0430\u0442\u044b");
        m.put("TjContactsCount", "\u041a\u043e\u043d\u0442\u0430\u043a\u0442\u044b");
        m.put("TjCopyImage", "\u041a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u0442\u044c \u0438\u0437\u043e\u0431\u0440\u0430\u0436\u0435\u043d\u0438\u0435");
        m.put("TjCopyMessageLink", "\u0421\u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u0442\u044c \u0441\u0441\u044b\u043b\u043a\u0443 \u043d\u0430 \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435");
        m.put("TjCopyThumbnail", "\u041a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u0442\u044c \u043c\u0438\u043d\u0438\u0430\u0442\u044e\u0440\u0443");
        m.put("TjDeleteForBoth", "\u0423\u0434\u0430\u043b\u044f\u0442\u044c \u0443 \u043e\u0431\u043e\u0438\u0445 \u043f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e");
        m.put("TjFilterAdmins", "\u0422\u043e\u043b\u044c\u043a\u043e \u0430\u0434\u043c\u0438\u043d\u0438\u0441\u0442\u0440\u0430\u0442\u043e\u0440\u044b");
        m.put("TjFilterAll", "\u0412\u0441\u0435 \u0443\u0447\u0430\u0441\u0442\u043d\u0438\u043a\u0438");
        m.put("TjFilterBots", "\u0422\u043e\u043b\u044c\u043a\u043e \u0431\u043e\u0442\u044b");
        m.put("TjFilterContacts", "\u0422\u043e\u043b\u044c\u043a\u043e \u043a\u043e\u043d\u0442\u0430\u043a\u0442\u044b");
        m.put("TjFilterMembers", "\u0424\u0438\u043b\u044c\u0442\u0440 \u0443\u0447\u0430\u0441\u0442\u043d\u0438\u043a\u043e\u0432");
        m.put("TjFilterMembersOnly", "\u0422\u043e\u043b\u044c\u043a\u043e \u0443\u0447\u0430\u0441\u0442\u043d\u0438\u043a\u0438");
        m.put("TjFolderIcon", "\u0417\u043d\u0430\u0447\u043e\u043a \u043f\u0430\u043f\u043a\u0438");
        m.put("TjFolderManaging", "\u0423\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435");
        m.put("TjFolderTabIconAndName", "\u0417\u043d\u0430\u0447\u043e\u043a \u0438 \u043d\u0430\u0437\u0432\u0430\u043d\u0438\u0435");
        m.put("TjFolderTabIconOnly", "\u0422\u043e\u043b\u044c\u043a\u043e \u0437\u043d\u0430\u0447\u043e\u043a");
        m.put("TjFolderTabNameOnly", "\u0422\u043e\u043b\u044c\u043a\u043e \u043d\u0430\u0437\u0432\u0430\u043d\u0438\u0435");
        m.put("TjFolderTabStyle", "\u0412\u043a\u043b\u0430\u0434\u043a\u0438 \u043f\u0430\u043f\u043e\u043a");
        m.put("TjFolderTabStyleInfo", "\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435, \u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c \u043d\u0430 \u0432\u043a\u043b\u0430\u0434\u043a\u0430\u0445 \u0437\u043d\u0430\u0447\u043e\u043a, \u043d\u0430\u0437\u0432\u0430\u043d\u0438\u0435 \u0438\u043b\u0438 \u0438 \u0442\u043e \u0438 \u0434\u0440\u0443\u0433\u043e\u0435.");
        m.put("TjFoldersCount", "\u041f\u0430\u043f\u043a\u0438");
        m.put("TjForwardWithoutTag", "\u041f\u0435\u0440\u0435\u0441\u043b\u0430\u0442\u044c \u0431\u0435\u0437 \u043c\u0435\u0442\u043a\u0438");
        m.put("TjGeneralHeader", "\u041e\u0441\u043d\u043e\u0432\u043d\u044b\u0435");
        m.put("TjGhostDontWarnAgain", "\u0411\u043e\u043b\u044c\u0448\u0435 \u043d\u0435 \u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c");
        m.put("TjGhostMode", "\u0420\u0435\u0436\u0438\u043c \u043f\u0440\u0438\u0437\u0440\u0430\u043a\u0430");
        m.put("TjGhostModeInfo", "\u0420\u0435\u0436\u0438\u043c \u043f\u0440\u0438\u0437\u0440\u0430\u043a\u0430 \u0441\u043a\u0440\u044b\u0432\u0430\u0435\u0442 \u0432\u0430\u0448\u0443 \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c \u043e\u0442 \u0434\u0440\u0443\u0433\u0438\u0445. \u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435, \u0447\u0442\u043e \u043e\u043d \u043e\u0445\u0432\u0430\u0442\u044b\u0432\u0430\u0435\u0442.");
        m.put("TjGhostModeOff", "\u0412\u044b\u043a\u043b\u044e\u0447\u0438\u0442\u044c \u0440\u0435\u0436\u0438\u043c \u043f\u0440\u0438\u0437\u0440\u0430\u043a\u0430");
        m.put("TjGhostModeOn", "\u0412\u043a\u043b\u044e\u0447\u0438\u0442\u044c \u0440\u0435\u0436\u0438\u043c \u043f\u0440\u0438\u0437\u0440\u0430\u043a\u0430");
        m.put("TjGhostOnline", "\u041e\u0441\u0442\u0430\u0432\u0430\u0442\u044c\u0441\u044f \u043d\u0435 \u0432 \u0441\u0435\u0442\u0438");
        m.put("TjGhostRead", "\u041d\u0435 \u043e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u0442\u044c \u043e\u0442\u043c\u0435\u0442\u043a\u0438 \u043e \u043f\u0440\u043e\u0447\u0442\u0435\u043d\u0438\u0438");
        m.put("TjGhostTyping", "\u041d\u0435 \u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c, \u0447\u0442\u043e \u044f \u043f\u0435\u0447\u0430\u0442\u0430\u044e");
        m.put("TjGhostWarning", "\u0420\u0435\u0436\u0438\u043c \u043f\u0440\u0438\u0437\u0440\u0430\u043a\u0430 \u2014 \u043d\u0435\u043e\u0444\u0438\u0446\u0438\u0430\u043b\u044c\u043d\u0430\u044f \u0444\u0443\u043d\u043a\u0446\u0438\u044f. Telegram \u043c\u043e\u0436\u0435\u0442 \u0441\u0447\u0435\u0441\u0442\u044c \u044d\u0442\u043e \u043d\u0435\u043e\u0431\u044b\u0447\u043d\u044b\u043c \u043f\u043e\u0432\u0435\u0434\u0435\u043d\u0438\u0435\u043c, \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439\u0442\u0435 \u043d\u0430 \u0441\u0432\u043e\u0439 \u0440\u0438\u0441\u043a.");
        m.put("TjGoToFirstMessage", "\u041a \u043f\u0435\u0440\u0432\u043e\u043c\u0443 \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u044e");
        m.put("TjGroups", "\u0413\u0440\u0443\u043f\u043f\u044b");
        m.put("TjHidePhoneNumber", "\u0421\u043a\u0440\u044b\u0442\u044c \u043c\u043e\u0439 \u043d\u043e\u043c\u0435\u0440 \u0442\u0435\u043b\u0435\u0444\u043e\u043d\u0430");
        m.put("TjHidePhoneNumberInfo", "\u0421\u043a\u0440\u044b\u0432\u0430\u0435\u0442 \u0432\u0430\u0448 \u043d\u043e\u043c\u0435\u0440 \u0432 \u0431\u043e\u043a\u043e\u0432\u043e\u043c \u043c\u0435\u043d\u044e \u0438 \u0432 \u0432\u0430\u0448\u0435\u043c \u043f\u0440\u043e\u0444\u0438\u043b\u0435.");
        m.put("TjHidePinnedMessage", "\u0421\u043a\u0440\u044b\u0442\u044c \u0437\u0430\u043a\u0440\u0435\u043f\u043b\u0451\u043d\u043d\u043e\u0435 \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435");
        m.put("TjMessageInfo", "\u0418\u043d\u0444\u043e\u0440\u043c\u0430\u0446\u0438\u044f \u043e \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0438");
        m.put("TjMessageMenuHeader", "\u041c\u0435\u043d\u044e \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u044f");
        m.put("TjMessageMenuInfo", "\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435, \u043a\u0430\u043a\u0438\u0435 \u043f\u0443\u043d\u043a\u0442\u044b TJ \u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c \u043f\u0440\u0438 \u0434\u043e\u043b\u0433\u043e\u043c \u043d\u0430\u0436\u0430\u0442\u0438\u0438 \u043d\u0430 \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435.");
        m.put("TjMutedChats", "\u041e\u0442\u043a\u043b\u044e\u0447\u0451\u043d\u043d\u044b\u0435 \u0447\u0430\u0442\u044b");
        m.put("TjMyProfile", "\u041c\u043e\u0439 \u043f\u0440\u043e\u0444\u0438\u043b\u044c");
        m.put("TjPrivateChats", "\u041b\u0438\u0447\u043d\u044b\u0435 \u0447\u0430\u0442\u044b");
        m.put("TjReplyPrivately", "\u041e\u0442\u0432\u0435\u0442\u0438\u0442\u044c \u043b\u0438\u0447\u043d\u043e");
        m.put("TjSaveToSaved", "\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u0432 \u0418\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u0435");
        m.put("TjSettings", "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 TjGram");
        m.put("TjShowCallButton", "\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c \u043a\u043d\u043e\u043f\u043a\u0443 \u0437\u0432\u043e\u043d\u043a\u0430 \u0432 \u043b\u0438\u0447\u043d\u044b\u0445 \u0447\u0430\u0442\u0430\u0445");
        m.put("TjShowCallButtonInfo", "\u041f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e \u0432 \u043b\u0438\u0447\u043d\u044b\u0445 \u0447\u0430\u0442\u0430\u0445 \u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442\u0441\u044f \u0437\u043d\u0430\u0447\u043e\u043a \u043f\u043e\u0438\u0441\u043a\u0430 \u0432\u043c\u0435\u0441\u0442\u043e \u0437\u043d\u0430\u0447\u043a\u0430 \u0437\u0432\u043e\u043d\u043a\u0430.");
        m.put("TjShowPinnedMessage", "\u041f\u043e\u043a\u0430\u0437\u0430\u0442\u044c \u0437\u0430\u043a\u0440\u0435\u043f\u043b\u0451\u043d\u043d\u043e\u0435 \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435");
        m.put("TjSubtitleAuto", "\u0412\u043a\u043b\u044e\u0447\u0430\u0442\u044c \u0441\u0443\u0431\u0442\u0438\u0442\u0440\u044b \u0430\u0432\u0442\u043e\u043c\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u0438");
        m.put("TjSubtitleAutoInfo", "\u0412\u044b\u0431\u0438\u0440\u0430\u0435\u0442 \u0434\u043e\u0440\u043e\u0436\u043a\u0443 \u0441\u0443\u0431\u0442\u0438\u0442\u0440\u043e\u0432 \u043d\u0430 \u0432\u0430\u0448\u0435\u043c \u044f\u0437\u044b\u043a\u0435, \u0435\u0441\u043b\u0438 \u043e\u043d\u0430 \u0435\u0441\u0442\u044c \u0432 \u0432\u0438\u0434\u0435\u043e, \u0438\u043d\u0430\u0447\u0435 \u0430\u043d\u0433\u043b\u0438\u0439\u0441\u043a\u0443\u044e.");
        m.put("TjSubtitleFontSize", "\u0420\u0430\u0437\u043c\u0435\u0440 \u0448\u0440\u0438\u0444\u0442\u0430");
        m.put("TjSubtitlePosition", "\u041f\u043e\u043b\u043e\u0436\u0435\u043d\u0438\u0435");
        m.put("TjSubtitleSettings", "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0441\u0443\u0431\u0442\u0438\u0442\u0440\u043e\u0432");
        m.put("TjSubtitleSizeHuge", "\u041e\u0447\u0435\u043d\u044c \u0431\u043e\u043b\u044c\u0448\u043e\u0439");
        m.put("TjSubtitleSizeLarge", "\u0411\u043e\u043b\u044c\u0448\u043e\u0439");
        m.put("TjSubtitleSizeMedium", "\u0421\u0440\u0435\u0434\u043d\u0438\u0439");
        m.put("TjSubtitleSizeSmall", "\u041c\u0430\u043b\u0435\u043d\u044c\u043a\u0438\u0439");
        m.put("TjSubtitleStyle", "\u0421\u0442\u0438\u043b\u044c");
        m.put("TjSubtitleStyleBox", "\u0424\u043e\u043d");
        m.put("TjSubtitleStyleOutline", "\u041e\u0431\u0432\u043e\u0434\u043a\u0430");
        m.put("TjSubtitleStyleShadow", "\u0422\u0435\u043d\u044c");
        m.put("TjSubtitles", "\u0421\u0443\u0431\u0442\u0438\u0442\u0440\u044b");
        m.put("TjSubtitlesOff", "\u0412\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u044b");
        m.put("TjTotalChats", "\u0412\u0441\u0435\u0433\u043e \u0447\u0430\u0442\u043e\u0432");
        m.put("TjUnreadChats", "\u041d\u0435\u043f\u0440\u043e\u0447\u0438\u0442\u0430\u043d\u043d\u044b\u0435 \u0447\u0430\u0442\u044b");
        TRANSLATIONS.put("ru", m);

        m = new HashMap<>();
        m.put("TjArchivedChats", "Discussions archiv\u00e9es");
        m.put("TjAudioTrack", "Piste audio");
        m.put("TjBotApiIds", "Afficher les ID au format bot API");
        m.put("TjBotApiIdsInfo", "Affiche \u200e-100\u2026\u200e pour les supergroupes et canaux au lieu de l'ID interne.");
        m.put("TjBots", "Bots");
        m.put("TjChannels", "Canaux");
        m.put("TjChatCounters", "Compteurs de discussions");
        m.put("TjChatCountersInfo", "Les compteurs sont calcul\u00e9s \u00e0 partir des discussions d\u00e9j\u00e0 charg\u00e9es sur cet appareil.");
        m.put("TjChatCountersLoading", "Comptage de vos discussions\u2026");
        m.put("TjChatCountersUpdated", "Derni\u00e8re mise \u00e0 jour : %1$s");
        m.put("TjChatIdHeader", "Identifiants de discussion");
        m.put("TjChatsHeader", "Discussions");
        m.put("TjContactsCount", "Contacts");
        m.put("TjCopyImage", "Copier l'image");
        m.put("TjCopyMessageLink", "Copier le lien du message");
        m.put("TjCopyThumbnail", "Copier la miniature");
        m.put("TjDeleteForBoth", "Supprimer des deux c\u00f4t\u00e9s par d\u00e9faut");
        m.put("TjFilterAdmins", "Administrateurs uniquement");
        m.put("TjFilterAll", "Tous les membres");
        m.put("TjFilterBots", "Bots uniquement");
        m.put("TjFilterContacts", "Contacts uniquement");
        m.put("TjFilterMembers", "Filtrer les membres");
        m.put("TjFilterMembersOnly", "Membres uniquement");
        m.put("TjFolderIcon", "Ic\u00f4ne du dossier");
        m.put("TjFolderManaging", "Gestion");
        m.put("TjFolderTabIconAndName", "Ic\u00f4ne et nom");
        m.put("TjFolderTabIconOnly", "Ic\u00f4ne seule");
        m.put("TjFolderTabNameOnly", "Nom seul");
        m.put("TjFolderTabStyle", "Onglets de dossiers");
        m.put("TjFolderTabStyleInfo", "Choisissez si les onglets affichent une ic\u00f4ne, un nom, ou les deux.");
        m.put("TjFoldersCount", "Dossiers");
        m.put("TjForwardWithoutTag", "Transf\u00e9rer sans mention");
        m.put("TjGeneralHeader", "G\u00e9n\u00e9ral");
        m.put("TjGhostDontWarnAgain", "Ne plus afficher");
        m.put("TjGhostMode", "Mode fant\u00f4me");
        m.put("TjGhostModeInfo", "Le mode fant\u00f4me masque votre activit\u00e9 aux autres. Choisissez ce qu\u2019il couvre.");
        m.put("TjGhostModeOff", "D\u00e9sactiver le mode fant\u00f4me");
        m.put("TjGhostModeOn", "Activer le mode fant\u00f4me");
        m.put("TjGhostOnline", "Rester hors ligne");
        m.put("TjGhostRead", "Ne pas envoyer d\u2019accus\u00e9s de lecture");
        m.put("TjGhostTyping", "Ne pas montrer que j\u2019\u00e9cris");
        m.put("TjGhostWarning", "Le mode fant\u00f4me n\u2019est pas une fonctionnalit\u00e9 officielle de Telegram. Telegram pourrait le consid\u00e9rer comme un comportement inhabituel : \u00e0 utiliser \u00e0 vos risques.");
        m.put("TjGoToFirstMessage", "Aller au premier message");
        m.put("TjGroups", "Groupes");
        m.put("TjHidePhoneNumber", "Masquer mon num\u00e9ro de t\u00e9l\u00e9phone");
        m.put("TjHidePhoneNumberInfo", "Masque votre num\u00e9ro dans le menu lat\u00e9ral et sur votre profil.");
        m.put("TjHidePinnedMessage", "Masquer le message \u00e9pingl\u00e9");
        m.put("TjMessageInfo", "Infos du message");
        m.put("TjMessageMenuHeader", "Menu du message");
        m.put("TjMessageMenuInfo", "Choisissez les \u00e9l\u00e9ments TJ qui apparaissent lors d\u2019un appui long sur un message.");
        m.put("TjMutedChats", "Discussions en sourdine");
        m.put("TjMyProfile", "Mon profil");
        m.put("TjPrivateChats", "Discussions priv\u00e9es");
        m.put("TjReplyPrivately", "R\u00e9pondre en priv\u00e9");
        m.put("TjSaveToSaved", "Enregistrer dans les messages sauvegard\u00e9s");
        m.put("TjSettings", "Param\u00e8tres TjGram");
        m.put("TjShowCallButton", "Afficher le bouton d'appel dans les discussions priv\u00e9es");
        m.put("TjShowCallButtonInfo", "Par d\u00e9faut, l'ic\u00f4ne de recherche remplace l'ic\u00f4ne d'appel dans les discussions priv\u00e9es.");
        m.put("TjShowPinnedMessage", "Afficher le message \u00e9pingl\u00e9");
        m.put("TjSubtitleAuto", "Activer les sous-titres automatiquement");
        m.put("TjSubtitleAutoInfo", "Choisit une piste de sous-titres dans votre langue si la vid\u00e9o en a une, sinon en anglais.");
        m.put("TjSubtitleFontSize", "Taille du texte");
        m.put("TjSubtitlePosition", "Position");
        m.put("TjSubtitleSettings", "R\u00e9glages des sous-titres");
        m.put("TjSubtitleSizeHuge", "Tr\u00e8s grande");
        m.put("TjSubtitleSizeLarge", "Grande");
        m.put("TjSubtitleSizeMedium", "Moyenne");
        m.put("TjSubtitleSizeSmall", "Petite");
        m.put("TjSubtitleStyle", "Style");
        m.put("TjSubtitleStyleBox", "Fond color\u00e9");
        m.put("TjSubtitleStyleOutline", "Contour");
        m.put("TjSubtitleStyleShadow", "Ombre");
        m.put("TjSubtitles", "Sous-titres");
        m.put("TjSubtitlesOff", "D\u00e9sactiv\u00e9s");
        m.put("TjTotalChats", "Total des discussions");
        m.put("TjUnreadChats", "Discussions non lues");
        TRANSLATIONS.put("fr", m);

        m = new HashMap<>();
        m.put("TjArchivedChats", "\u5df2\u5f52\u6863\u804a\u5929");
        m.put("TjAudioTrack", "\u97f3\u8f68");
        m.put("TjBotApiIds", "\u4ee5 bot API \u683c\u5f0f\u663e\u793a\u804a\u5929 ID");
        m.put("TjBotApiIdsInfo", "\u4e3a\u8d85\u7ea7\u7fa4\u7ec4\u548c\u9891\u9053\u663e\u793a \u200e-100\u2026\u200e \u800c\u4e0d\u662f\u5185\u90e8 ID\u3002");
        m.put("TjBots", "\u673a\u5668\u4eba");
        m.put("TjChannels", "\u9891\u9053");
        m.put("TjChatCounters", "\u804a\u5929\u7edf\u8ba1");
        m.put("TjChatCountersInfo", "\u8ba1\u6570\u57fa\u4e8e\u672c\u8bbe\u5907\u4e0a\u5df2\u52a0\u8f7d\u7684\u804a\u5929\u3002");
        m.put("TjChatCountersLoading", "\u6b63\u5728\u7edf\u8ba1\u4f60\u7684\u804a\u5929\u2026");
        m.put("TjChatCountersUpdated", "\u6700\u540e\u66f4\u65b0\uff1a%1$s");
        m.put("TjChatIdHeader", "\u804a\u5929 ID");
        m.put("TjChatsHeader", "\u804a\u5929");
        m.put("TjContactsCount", "\u8054\u7cfb\u4eba");
        m.put("TjCopyImage", "\u590d\u5236\u56fe\u7247");
        m.put("TjCopyMessageLink", "\u590d\u5236\u6d88\u606f\u94fe\u63a5");
        m.put("TjCopyThumbnail", "\u590d\u5236\u7f29\u7565\u56fe");
        m.put("TjDeleteForBoth", "\u9ed8\u8ba4\u4e3a\u53cc\u65b9\u5220\u9664");
        m.put("TjFilterAdmins", "\u4ec5\u7ba1\u7406\u5458");
        m.put("TjFilterAll", "\u6240\u6709\u6210\u5458");
        m.put("TjFilterBots", "\u4ec5\u673a\u5668\u4eba");
        m.put("TjFilterContacts", "\u4ec5\u8054\u7cfb\u4eba");
        m.put("TjFilterMembers", "\u7b5b\u9009\u6210\u5458");
        m.put("TjFilterMembersOnly", "\u4ec5\u6210\u5458");
        m.put("TjFolderIcon", "\u6587\u4ef6\u5939\u56fe\u6807");
        m.put("TjFolderManaging", "\u7ba1\u7406");
        m.put("TjFolderTabIconAndName", "\u56fe\u6807\u548c\u540d\u79f0");
        m.put("TjFolderTabIconOnly", "\u4ec5\u56fe\u6807");
        m.put("TjFolderTabNameOnly", "\u4ec5\u540d\u79f0");
        m.put("TjFolderTabStyle", "\u6587\u4ef6\u5939\u6807\u7b7e");
        m.put("TjFolderTabStyleInfo", "\u9009\u62e9\u6807\u7b7e\u663e\u793a\u56fe\u6807\u3001\u540d\u79f0\uff0c\u8fd8\u662f\u4e24\u8005\u90fd\u663e\u793a\u3002");
        m.put("TjFoldersCount", "\u6587\u4ef6\u5939");
        m.put("TjForwardWithoutTag", "\u65e0\u6807\u8bb0\u8f6c\u53d1");
        m.put("TjGeneralHeader", "\u5e38\u89c4");
        m.put("TjGhostDontWarnAgain", "\u4e0d\u518d\u663e\u793a");
        m.put("TjGhostMode", "\u9690\u8eab\u6a21\u5f0f");
        m.put("TjGhostModeInfo", "\u9690\u8eab\u6a21\u5f0f\u4f1a\u5bf9\u4ed6\u4eba\u9690\u85cf\u4f60\u7684\u6d3b\u52a8\u3002\u9009\u62e9\u5b83\u5305\u542b\u7684\u5185\u5bb9\u3002");
        m.put("TjGhostModeOff", "\u5173\u95ed\u9690\u8eab\u6a21\u5f0f");
        m.put("TjGhostModeOn", "\u5f00\u542f\u9690\u8eab\u6a21\u5f0f");
        m.put("TjGhostOnline", "\u4fdd\u6301\u79bb\u7ebf");
        m.put("TjGhostRead", "\u4e0d\u53d1\u9001\u5df2\u8bfb\u56de\u6267");
        m.put("TjGhostTyping", "\u4e0d\u663e\u793a\u6211\u6b63\u5728\u8f93\u5165");
        m.put("TjGhostWarning", "\u9690\u8eab\u6a21\u5f0f\u5e76\u975e Telegram \u5b98\u65b9\u529f\u80fd\u3002Telegram \u53ef\u80fd\u5c06\u5176\u89c6\u4e3a\u5f02\u5e38\u884c\u4e3a\uff0c\u8bf7\u81ea\u884c\u627f\u62c5\u98ce\u9669\u3002");
        m.put("TjGoToFirstMessage", "\u8df3\u5230\u7b2c\u4e00\u6761\u6d88\u606f");
        m.put("TjGroups", "\u7fa4\u7ec4");
        m.put("TjHidePhoneNumber", "\u9690\u85cf\u6211\u7684\u624b\u673a\u53f7");
        m.put("TjHidePhoneNumberInfo", "\u5728\u4fa7\u8fb9\u83dc\u5355\u548c\u4e2a\u4eba\u8d44\u6599\u4e2d\u9690\u85cf\u4f60\u7684\u624b\u673a\u53f7\u3002");
        m.put("TjHidePinnedMessage", "\u9690\u85cf\u7f6e\u9876\u6d88\u606f");
        m.put("TjMessageInfo", "\u6d88\u606f\u4fe1\u606f");
        m.put("TjMessageMenuHeader", "\u6d88\u606f\u83dc\u5355");
        m.put("TjMessageMenuInfo", "\u9009\u62e9\u957f\u6309\u6d88\u606f\u65f6\u663e\u793a\u54ea\u4e9b TJ \u9009\u9879\u3002");
        m.put("TjMutedChats", "\u9759\u97f3\u804a\u5929");
        m.put("TjMyProfile", "\u6211\u7684\u8d44\u6599");
        m.put("TjPrivateChats", "\u79c1\u804a");
        m.put("TjReplyPrivately", "\u79c1\u804a\u56de\u590d");
        m.put("TjSaveToSaved", "\u4fdd\u5b58\u5230\u6536\u85cf\u5939");
        m.put("TjSettings", "TjGram \u8bbe\u7f6e");
        m.put("TjShowCallButton", "\u5728\u79c1\u804a\u4e2d\u663e\u793a\u901a\u8bdd\u6309\u94ae");
        m.put("TjShowCallButtonInfo", "\u9ed8\u8ba4\u5728\u79c1\u804a\u4e2d\u663e\u793a\u641c\u7d22\u56fe\u6807\u800c\u975e\u901a\u8bdd\u56fe\u6807\u3002");
        m.put("TjShowPinnedMessage", "\u663e\u793a\u7f6e\u9876\u6d88\u606f");
        m.put("TjSubtitleAuto", "\u81ea\u52a8\u5f00\u542f\u5b57\u5e55");
        m.put("TjSubtitleAutoInfo", "\u89c6\u9891\u4e2d\u6709\u4f60\u7684\u8bed\u8a00\u7684\u5b57\u5e55\u8f68\u65f6\u4f18\u5148\u9009\u62e9\uff0c\u5426\u5219\u9009\u62e9\u82f1\u6587\u3002");
        m.put("TjSubtitleFontSize", "\u5b57\u4f53\u5927\u5c0f");
        m.put("TjSubtitlePosition", "\u4f4d\u7f6e");
        m.put("TjSubtitleSettings", "\u5b57\u5e55\u8bbe\u7f6e");
        m.put("TjSubtitleSizeHuge", "\u7279\u5927");
        m.put("TjSubtitleSizeLarge", "\u5927");
        m.put("TjSubtitleSizeMedium", "\u4e2d");
        m.put("TjSubtitleSizeSmall", "\u5c0f");
        m.put("TjSubtitleStyle", "\u6837\u5f0f");
        m.put("TjSubtitleStyleBox", "\u80cc\u666f\u8272\u5757");
        m.put("TjSubtitleStyleOutline", "\u63cf\u8fb9");
        m.put("TjSubtitleStyleShadow", "\u9634\u5f71");
        m.put("TjSubtitles", "\u5b57\u5e55");
        m.put("TjSubtitlesOff", "\u5173\u95ed");
        m.put("TjTotalChats", "\u804a\u5929\u603b\u6570");
        m.put("TjUnreadChats", "\u672a\u8bfb\u804a\u5929");
        TRANSLATIONS.put("zh", m);

        m = new HashMap<>();
        m.put("TjArchivedChats", "\u0938\u0902\u0917\u094d\u0930\u0939\u093f\u0924 \u091a\u0948\u091f");
        m.put("TjAudioTrack", "\u0911\u0921\u093f\u092f\u094b \u091f\u094d\u0930\u0948\u0915");
        m.put("TjBotApiIds", "\u091a\u0948\u091f \u0906\u0908\u0921\u0940 bot API \u092a\u094d\u0930\u093e\u0930\u0942\u092a \u092e\u0947\u0902 \u0926\u093f\u0916\u093e\u090f\u0901");
        m.put("TjBotApiIdsInfo", "\u0938\u0941\u092a\u0930\u0917\u094d\u0930\u0941\u092a \u0914\u0930 \u091a\u0948\u0928\u0932\u094b\u0902 \u0915\u0947 \u0932\u093f\u090f \u0906\u0902\u0924\u0930\u093f\u0915 \u0906\u0908\u0921\u0940 \u0915\u0947 \u092c\u091c\u093e\u092f \u200e-100\u2026\u200e \u0926\u093f\u0916\u093e\u0924\u093e \u0939\u0948\u0964");
        m.put("TjBots", "\u092c\u0949\u091f");
        m.put("TjChannels", "\u091a\u0948\u0928\u0932");
        m.put("TjChatCounters", "\u091a\u0948\u091f \u0915\u093e\u0909\u0902\u091f\u0930");
        m.put("TjChatCountersInfo", "\u0917\u0923\u0928\u093e \u0907\u0938 \u0921\u093f\u0935\u093e\u0907\u0938 \u092a\u0930 \u092a\u0939\u0932\u0947 \u0938\u0947 \u0932\u094b\u0921 \u091a\u0948\u091f \u0938\u0947 \u0915\u0940 \u091c\u093e\u0924\u0940 \u0939\u0948\u0964");
        m.put("TjChatCountersLoading", "\u0906\u092a\u0915\u0947 \u091a\u0948\u091f \u0917\u093f\u0928\u0947 \u091c\u093e \u0930\u0939\u0947 \u0939\u0948\u0902\u2026");
        m.put("TjChatCountersUpdated", "\u0905\u0902\u0924\u093f\u092e \u0905\u092a\u0921\u0947\u091f: %1$s");
        m.put("TjChatIdHeader", "\u091a\u0948\u091f ID");
        m.put("TjChatsHeader", "\u091a\u0948\u091f");
        m.put("TjContactsCount", "\u0938\u0902\u092a\u0930\u094d\u0915");
        m.put("TjCopyImage", "\u091a\u093f\u0924\u094d\u0930 \u0915\u0949\u092a\u0940 \u0915\u0930\u0947\u0902");
        m.put("TjCopyMessageLink", "\u0938\u0902\u0926\u0947\u0936 \u0932\u093f\u0902\u0915 \u0915\u0949\u092a\u0940 \u0915\u0930\u0947\u0902");
        m.put("TjCopyThumbnail", "\u0925\u0902\u092c\u0928\u0947\u0932 \u0915\u0949\u092a\u0940 \u0915\u0930\u0947\u0902");
        m.put("TjDeleteForBoth", "\u0921\u093f\u092b\u093c\u0949\u0932\u094d\u091f \u0930\u0942\u092a \u0938\u0947 \u0926\u094b\u0928\u094b\u0902 \u0915\u0947 \u0932\u093f\u090f \u0939\u091f\u093e\u090f\u0901");
        m.put("TjFilterAdmins", "\u0915\u0947\u0935\u0932 \u090f\u0921\u092e\u093f\u0928");
        m.put("TjFilterAll", "\u0938\u092d\u0940 \u0938\u0926\u0938\u094d\u092f");
        m.put("TjFilterBots", "\u0915\u0947\u0935\u0932 \u092c\u0949\u091f");
        m.put("TjFilterContacts", "\u0915\u0947\u0935\u0932 \u0938\u0902\u092a\u0930\u094d\u0915");
        m.put("TjFilterMembers", "\u0938\u0926\u0938\u094d\u092f \u092b\u093c\u093f\u0932\u094d\u091f\u0930 \u0915\u0930\u0947\u0902");
        m.put("TjFilterMembersOnly", "\u0915\u0947\u0935\u0932 \u0938\u0926\u0938\u094d\u092f");
        m.put("TjFolderIcon", "\u092b\u093c\u094b\u0932\u094d\u0921\u0930 \u0906\u0907\u0915\u0928");
        m.put("TjFolderManaging", "\u092a\u094d\u0930\u092c\u0902\u0927\u0928");
        m.put("TjFolderTabIconAndName", "\u0906\u0907\u0915\u0928 \u0914\u0930 \u0928\u093e\u092e");
        m.put("TjFolderTabIconOnly", "\u0915\u0947\u0935\u0932 \u0906\u0907\u0915\u0928");
        m.put("TjFolderTabNameOnly", "\u0915\u0947\u0935\u0932 \u0928\u093e\u092e");
        m.put("TjFolderTabStyle", "\u092b\u093c\u094b\u0932\u094d\u0921\u0930 \u091f\u0948\u092c");
        m.put("TjFolderTabStyleInfo", "\u091a\u0941\u0928\u0947\u0902 \u0915\u093f \u091f\u0948\u092c \u092a\u0930 \u0906\u0907\u0915\u0928, \u0928\u093e\u092e, \u092f\u093e \u0926\u094b\u0928\u094b\u0902 \u0926\u093f\u0916\u0947\u0902\u0964");
        m.put("TjFoldersCount", "\u092b\u093c\u094b\u0932\u094d\u0921\u0930");
        m.put("TjForwardWithoutTag", "\u092c\u093f\u0928\u093e \u091f\u0948\u0917 \u092b\u093c\u0949\u0930\u0935\u0930\u094d\u0921 \u0915\u0930\u0947\u0902");
        m.put("TjGeneralHeader", "\u0938\u093e\u092e\u093e\u0928\u094d\u092f");
        m.put("TjGhostDontWarnAgain", "\u0907\u0938\u0947 \u0926\u094b\u092c\u093e\u0930\u093e \u0928 \u0926\u093f\u0916\u093e\u090f\u0901");
        m.put("TjGhostMode", "\u0918\u094b\u0938\u094d\u091f \u092e\u094b\u0921");
        m.put("TjGhostModeInfo", "\u0918\u094b\u0938\u094d\u091f \u092e\u094b\u0921 \u0906\u092a\u0915\u0940 \u0917\u0924\u093f\u0935\u093f\u0927\u093f \u0926\u0942\u0938\u0930\u094b\u0902 \u0938\u0947 \u091b\u093f\u092a\u093e\u0924\u093e \u0939\u0948\u0964 \u091a\u0941\u0928\u0947\u0902 \u0915\u093f \u0907\u0938\u092e\u0947\u0902 \u0915\u094d\u092f\u093e \u0936\u093e\u092e\u093f\u0932 \u0939\u094b\u0964");
        m.put("TjGhostModeOff", "\u0918\u094b\u0938\u094d\u091f \u092e\u094b\u0921 \u092c\u0902\u0926 \u0915\u0930\u0947\u0902");
        m.put("TjGhostModeOn", "\u0918\u094b\u0938\u094d\u091f \u092e\u094b\u0921 \u091a\u093e\u0932\u0942 \u0915\u0930\u0947\u0902");
        m.put("TjGhostOnline", "\u0911\u092b\u093c\u0932\u093e\u0907\u0928 \u0930\u0939\u0947\u0902");
        m.put("TjGhostRead", "\u092a\u0922\u093c\u0928\u0947 \u0915\u0940 \u0930\u0938\u0940\u0926\u0947\u0902 \u0928 \u092d\u0947\u091c\u0947\u0902");
        m.put("TjGhostTyping", "\u092f\u0939 \u0928 \u0926\u093f\u0916\u093e\u090f\u0901 \u0915\u093f \u092e\u0948\u0902 \u091f\u093e\u0907\u092a \u0915\u0930 \u0930\u0939\u093e \u0939\u0942\u0901");
        m.put("TjGhostWarning", "\u0918\u094b\u0938\u094d\u091f \u092e\u094b\u0921 Telegram \u0915\u0940 \u0906\u0927\u093f\u0915\u093e\u0930\u093f\u0915 \u0938\u0941\u0935\u093f\u0927\u093e \u0928\u0939\u0940\u0902 \u0939\u0948\u0964 Telegram \u0907\u0938\u0947 \u0905\u0938\u093e\u092e\u093e\u0928\u094d\u092f \u0935\u094d\u092f\u0935\u0939\u093e\u0930 \u092e\u093e\u0928 \u0938\u0915\u0924\u093e \u0939\u0948, \u0907\u0938\u0932\u093f\u090f \u0905\u092a\u0928\u0947 \u091c\u094b\u0916\u093f\u092e \u092a\u0930 \u0909\u092a\u092f\u094b\u0917 \u0915\u0930\u0947\u0902\u0964");
        m.put("TjGoToFirstMessage", "\u092a\u0939\u0932\u0947 \u0938\u0902\u0926\u0947\u0936 \u092a\u0930 \u091c\u093e\u090f\u0901");
        m.put("TjGroups", "\u0938\u092e\u0942\u0939");
        m.put("TjHidePhoneNumber", "\u092e\u0947\u0930\u093e \u092b\u093c\u094b\u0928 \u0928\u0902\u092c\u0930 \u091b\u093f\u092a\u093e\u090f\u0901");
        m.put("TjHidePhoneNumberInfo", "\u0938\u093e\u0907\u0921 \u092e\u0947\u0928\u094d\u092f\u0942 \u0914\u0930 \u0906\u092a\u0915\u0940 \u092a\u094d\u0930\u094b\u092b\u093c\u093e\u0907\u0932 \u092e\u0947\u0902 \u0906\u092a\u0915\u093e \u0928\u0902\u092c\u0930 \u091b\u093f\u092a\u093e\u0924\u093e \u0939\u0948\u0964");
        m.put("TjHidePinnedMessage", "\u092a\u093f\u0928 \u0915\u093f\u092f\u093e \u0938\u0902\u0926\u0947\u0936 \u091b\u093f\u092a\u093e\u090f\u0901");
        m.put("TjMessageInfo", "\u0938\u0902\u0926\u0947\u0936 \u091c\u093e\u0928\u0915\u093e\u0930\u0940");
        m.put("TjMessageMenuHeader", "\u0938\u0902\u0926\u0947\u0936 \u092e\u0947\u0928\u094d\u092f\u0942");
        m.put("TjMessageMenuInfo", "\u091a\u0941\u0928\u0947\u0902 \u0915\u093f \u0938\u0902\u0926\u0947\u0936 \u092a\u0930 \u0926\u0947\u0930 \u0924\u0915 \u0926\u092c\u093e\u0928\u0947 \u092a\u0930 \u0915\u094c\u0928 \u0938\u0947 TJ \u0935\u093f\u0915\u0932\u094d\u092a \u0926\u093f\u0916\u0947\u0902\u0964");
        m.put("TjMutedChats", "\u092e\u094d\u092f\u0942\u091f \u091a\u0948\u091f");
        m.put("TjMyProfile", "\u092e\u0947\u0930\u0940 \u092a\u094d\u0930\u094b\u092b\u093c\u093e\u0907\u0932");
        m.put("TjPrivateChats", "\u0928\u093f\u091c\u0940 \u091a\u0948\u091f");
        m.put("TjReplyPrivately", "\u0928\u093f\u091c\u0940 \u092e\u0947\u0902 \u0909\u0924\u094d\u0924\u0930 \u0926\u0947\u0902");
        m.put("TjSaveToSaved", "\u0938\u0939\u0947\u091c\u0947 \u0917\u090f \u0938\u0902\u0926\u0947\u0936\u094b\u0902 \u092e\u0947\u0902 \u0938\u0939\u0947\u091c\u0947\u0902");
        m.put("TjSettings", "TjGram \u0938\u0947\u091f\u093f\u0902\u0917\u094d\u0938");
        m.put("TjShowCallButton", "\u0928\u093f\u091c\u0940 \u091a\u0948\u091f \u092e\u0947\u0902 \u0915\u0949\u0932 \u092c\u091f\u0928 \u0926\u093f\u0916\u093e\u090f\u0901");
        m.put("TjShowCallButtonInfo", "\u0921\u093f\u092b\u093c\u0949\u0932\u094d\u091f \u0930\u0942\u092a \u0938\u0947 \u0928\u093f\u091c\u0940 \u091a\u0948\u091f \u092e\u0947\u0902 \u0915\u0949\u0932 \u0906\u0907\u0915\u0928 \u0915\u0947 \u092c\u091c\u093e\u092f \u0916\u094b\u091c \u0906\u0907\u0915\u0928 \u0926\u093f\u0916\u0924\u093e \u0939\u0948\u0964");
        m.put("TjShowPinnedMessage", "\u092a\u093f\u0928 \u0915\u093f\u092f\u093e \u0938\u0902\u0926\u0947\u0936 \u0926\u093f\u0916\u093e\u090f\u0901");
        m.put("TjSubtitleAuto", "\u0938\u092c\u091f\u093e\u0907\u091f\u0932 \u0905\u092a\u0928\u0947 \u0906\u092a \u091a\u093e\u0932\u0942 \u0915\u0930\u0947\u0902");
        m.put("TjSubtitleAutoInfo", "\u0935\u0940\u0921\u093f\u092f\u094b \u092e\u0947\u0902 \u0906\u092a\u0915\u0940 \u092d\u093e\u0937\u093e \u0915\u093e \u0938\u092c\u091f\u093e\u0907\u091f\u0932 \u091f\u094d\u0930\u0948\u0915 \u0939\u094b\u0928\u0947 \u092a\u0930 \u0909\u0938\u0947 \u091a\u0941\u0928\u0924\u093e \u0939\u0948, \u0905\u0928\u094d\u092f\u0925\u093e \u0905\u0902\u0917\u094d\u0930\u0947\u091c\u093c\u0940\u0964");
        m.put("TjSubtitleFontSize", "\u092b\u093c\u0949\u0928\u094d\u091f \u0906\u0915\u093e\u0930");
        m.put("TjSubtitlePosition", "\u0938\u094d\u0925\u093f\u0924\u093f");
        m.put("TjSubtitleSettings", "\u0938\u092c\u091f\u093e\u0907\u091f\u0932 \u0938\u0947\u091f\u093f\u0902\u0917\u094d\u0938");
        m.put("TjSubtitleSizeHuge", "\u092c\u0939\u0941\u0924 \u092c\u0921\u093c\u093e");
        m.put("TjSubtitleSizeLarge", "\u092c\u0921\u093c\u093e");
        m.put("TjSubtitleSizeMedium", "\u092e\u0927\u094d\u092f\u092e");
        m.put("TjSubtitleSizeSmall", "\u091b\u094b\u091f\u093e");
        m.put("TjSubtitleStyle", "\u0936\u0948\u0932\u0940");
        m.put("TjSubtitleStyleBox", "\u092a\u0943\u0937\u094d\u0920\u092d\u0942\u092e\u093f \u092c\u0949\u0915\u094d\u0938");
        m.put("TjSubtitleStyleOutline", "\u0906\u0909\u091f\u0932\u093e\u0907\u0928");
        m.put("TjSubtitleStyleShadow", "\u091b\u093e\u092f\u093e");
        m.put("TjSubtitles", "\u0938\u092c\u091f\u093e\u0907\u091f\u0932");
        m.put("TjSubtitlesOff", "\u092c\u0902\u0926");
        m.put("TjTotalChats", "\u0915\u0941\u0932 \u091a\u0948\u091f");
        m.put("TjUnreadChats", "\u0905\u092a\u0920\u093f\u0924 \u091a\u0948\u091f");
        TRANSLATIONS.put("hi", m);
    }

    /** Language the user picked inside Telegram, normalised to a plain lowercase code. */
    private static String currentLanguage() {
        String code = null;
        try {
            LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
            if (info != null) {
                // shortName identifies the language pack the user actually selected. pluralLangCode
                // only selects grammatical plural rules and may point at a different/base language.
                code = !TextUtils.isEmpty(info.shortName) ? info.shortName
                     : !TextUtils.isEmpty(info.baseLangCode) ? info.baseLangCode
                     : info.pluralLangCode;
            }
        } catch (Exception ignore) {
        }
        if (TextUtils.isEmpty(code)) {
            return null;
        }
        code = code.toLowerCase();
        int cut = code.indexOf('_');
        if (cut < 0) {
            cut = code.indexOf('-');
        }
        if (cut > 0) {
            code = code.substring(0, cut);
        }
        // Java keeps the obsolete ISO codes for these three languages.
        if ("iw".equals(code)) return "he";
        if ("in".equals(code)) return "id";
        if ("ji".equals(code)) return "yi";
        return code;
    }

    /** Full language tag, including a region such as pt-BR when Telegram provides one. */
    private static String currentLanguageTag() {
        String code = null;
        try {
            LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
            if (info != null) {
                code = !TextUtils.isEmpty(info.shortName) ? info.shortName
                        : !TextUtils.isEmpty(info.baseLangCode) ? info.baseLangCode
                        : info.pluralLangCode;
            }
        } catch (Exception ignore) {
        }
        if (TextUtils.isEmpty(code)) {
            return currentLanguage();
        }
        code = code.replace('_', '-');
        if (code.equalsIgnoreCase("iw")) return "he";
        if (code.equalsIgnoreCase("in")) return "id";
        if (code.equalsIgnoreCase("ji")) return "yi";
        return code;
    }

    private static String getLocalizedResource(@StringRes int res) {
        try {
            String tag = currentLanguageTag();
            if (TextUtils.isEmpty(tag)) {
                tag = currentLanguage();
            }
            if (TextUtils.isEmpty(tag)) {
                return null;
            }
            Configuration configuration = new Configuration(
                    ApplicationLoader.applicationContext.getResources().getConfiguration());
            configuration.setLocale(Locale.forLanguageTag(tag));
            Context localized = ApplicationLoader.applicationContext.createConfigurationContext(configuration);
            return localized.getString(res);
        } catch (Exception ignore) {
            return null;
        }
    }

    public static String getString(@StringRes int res) {
        String key = null;
        try {
            key = ApplicationLoader.applicationContext.getResources().getResourceEntryName(res);
        } catch (Exception ignore) {
        }
        if (key != null) {
            String lang = currentLanguage();
            if (lang != null) {
                Map<String, String> map = TRANSLATIONS.get(lang);
                if (map != null) {
                    String value = map.get(key);
                    if (value != null) {
                        return value;
                    }
                }
            }
            String localized = getLocalizedResource(res);
            if (localized != null) {
                return localized;
            }
        }
        return LocaleController.getString(res);
    }

    public static String formatString(@StringRes int res, Object... args) {
        String format = getString(res);
        try {
            return String.format(LocaleController.getInstance().getCurrentLocale(), format, args);
        } catch (Exception e) {
            FileLog.e(e);
            return format;
        }
    }
}
