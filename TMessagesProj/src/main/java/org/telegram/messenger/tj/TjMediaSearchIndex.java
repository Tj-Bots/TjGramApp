package org.telegram.messenger.tj;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rebuildable local FTS4 index. Media and user state remain authoritative in media. */
final class TjMediaSearchIndex {
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private TjMediaSearchIndex() { }

    static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS media_fts USING fts4(search_text,title_text,tokenize=unicode61)");
        db.execSQL("CREATE TABLE IF NOT EXISTS media_search_progress (id INTEGER PRIMARY KEY CHECK(id=1), last_row INTEGER NOT NULL)");
        db.execSQL("INSERT OR IGNORE INTO media_search_progress(id,last_row) VALUES(1,0)");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS media_fts_insert AFTER INSERT ON media BEGIN INSERT INTO media_fts(docid,search_text,title_text) VALUES(new.rowid,new.search_text,new.title_text); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS media_fts_delete AFTER DELETE ON media BEGIN DELETE FROM media_fts WHERE docid=old.rowid; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS media_fts_update AFTER UPDATE OF search_text,title_text ON media BEGIN DELETE FROM media_fts WHERE docid=old.rowid; INSERT INTO media_fts(docid,search_text,title_text) VALUES(new.rowid,new.search_text,new.title_text); END");
    }

    /** At most 256 existing rows per transaction; retries never skip uncommitted rows. */
    static boolean backfill(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            long after = 0, last = 0;
            try (Cursor cursor = db.rawQuery("SELECT last_row FROM media_search_progress WHERE id=1", null)) {
                if (cursor.moveToFirst()) after = cursor.getLong(0);
            }
            int count = 0;
            try (Cursor cursor = db.rawQuery("SELECT rowid,search_text,title_text FROM media WHERE rowid>? ORDER BY rowid LIMIT 256", new String[]{Long.toString(after)})) {
                while (cursor.moveToNext()) {
                    last = cursor.getLong(0); count++;
                    db.execSQL("INSERT OR REPLACE INTO media_fts(docid,search_text,title_text) VALUES(?,?,?)",
                            new Object[]{last, cursor.getString(1), cursor.getString(2)});
                }
            }
            if (count > 0) db.execSQL("UPDATE media_search_progress SET last_row=? WHERE id=1", new Object[]{last});
            db.setTransactionSuccessful();
            return count < 256;
        } finally { db.endTransaction(); }
    }

    /** Each word is a quoted prefix, never user-supplied FTS operators or column syntax. */
    static String query(String value) {
        Matcher words = WORD.matcher(TjMediaTitle.normalizeSearch(value));
        StringBuilder result = new StringBuilder();
        while (words.find()) {
            if (result.length() > 0) result.append(' ');
            result.append('"').append(words.group()).append("*\"");
        }
        return result.toString();
    }
}
