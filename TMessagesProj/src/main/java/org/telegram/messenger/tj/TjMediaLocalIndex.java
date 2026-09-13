package org.telegram.messenger.tj;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Rebuildable naming index; manual assignments survive metadata changes. Store queue only. */
final class TjMediaLocalIndex {
    static void ensure(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            ensureSchema(db);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static void ensureSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS media_local_catalog (owner INTEGER NOT NULL, dialog INTEGER NOT NULL, mid INTEGER NOT NULL, document INTEGER NOT NULL, title_key TEXT NOT NULL, title TEXT NOT NULL, series INTEGER NOT NULL, season INTEGER NOT NULL, episode INTEGER NOT NULL, uncertain INTEGER NOT NULL, manual INTEGER NOT NULL DEFAULT 0, year INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(owner,dialog,mid))");
        ensureColumn(db, "year", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "catalog_key", "TEXT NOT NULL DEFAULT ''");
        db.execSQL("CREATE INDEX IF NOT EXISTS media_local_title ON media_local_catalog(owner,title_key,season,episode,dialog,mid)");
        db.execSQL("CREATE INDEX IF NOT EXISTS media_catalog_sources ON media_local_catalog(catalog_key,owner,dialog,mid)");
        db.execSQL("CREATE TABLE IF NOT EXISTS media_catalog_titles (title_key TEXT PRIMARY KEY, title TEXT NOT NULL, series INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS media_catalog_order ON media_catalog_titles(series,title,title_key)");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS media_catalog_delete AFTER DELETE ON media_local_catalog BEGIN DELETE FROM media_catalog_titles WHERE title_key=old.catalog_key AND NOT EXISTS (SELECT 1 FROM media_local_catalog WHERE catalog_key=old.catalog_key); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS media_catalog_move AFTER UPDATE OF catalog_key ON media_local_catalog WHEN old.catalog_key<>new.catalog_key BEGIN DELETE FROM media_catalog_titles WHERE title_key=old.catalog_key AND NOT EXISTS (SELECT 1 FROM media_local_catalog WHERE catalog_key=old.catalog_key); END");
        db.execSQL("CREATE TABLE IF NOT EXISTS media_local_progress (id INTEGER PRIMARY KEY CHECK(id=1), last_row INTEGER NOT NULL)");
        db.execSQL("INSERT OR IGNORE INTO media_local_progress VALUES(1,0)");
        // Version only the rebuildable naming data, not media, manual corrections or watch state.
        // Advance the revision atomically with resetting the cursor: reopening resumes the same pass.
        db.execSQL("CREATE TABLE IF NOT EXISTS media_local_revision (id INTEGER PRIMARY KEY CHECK(id=1), revision INTEGER NOT NULL)");
        db.execSQL("INSERT OR IGNORE INTO media_local_revision VALUES(1,0)");
        db.execSQL("UPDATE media_local_progress SET last_row=0 WHERE id=1 AND EXISTS (SELECT 1 FROM media_local_revision WHERE id=1 AND revision<2)");
        db.execSQL("UPDATE media_local_revision SET revision=2 WHERE id=1 AND revision<2");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS media_local_delete AFTER DELETE ON media BEGIN DELETE FROM media_local_catalog WHERE owner=old.owner AND dialog=old.dialog AND mid=old.mid; END");
    }

    private static void ensureColumn(SQLiteDatabase db, String name, String type) {
        try (Cursor columns = db.rawQuery("PRAGMA table_info(media_local_catalog)", null)) {
            while (columns.moveToNext()) if (name.equals(columns.getString(1))) return;
        }
        db.execSQL("ALTER TABLE media_local_catalog ADD COLUMN " + name + " " + type);
        // An older development index needs its derived directory populated too.
        db.execSQL("DROP TABLE IF EXISTS media_local_progress");
    }

    static void refreshTitle(SQLiteDatabase db, String[] args) {
        try (Cursor row = db.rawQuery("SELECT c.title_key,c.title,c.series,m.metadata,m.metadata_id,m.series FROM media_local_catalog c JOIN media m ON m.owner=c.owner AND m.dialog=c.dialog AND m.mid=c.mid WHERE c.owner=? AND c.dialog=? AND c.mid=?", args)) {
            if (!row.moveToFirst()) return;
            String key = row.getString(0), title = row.getString(1);
            boolean series = row.getInt(2) != 0;
            if (key.isEmpty() && row.getLong(4) > 0) {
                try {
                    series = row.getInt(5) != 0;
                    TjMediaMetadata.Title metadata = new TjMediaMetadata.Title(new org.json.JSONObject(row.getString(3)), series);
                    key = TjMediaCatalog.key(metadata.id, series); title = metadata.name;
                } catch (org.json.JSONException ignored) { }
            }
            ContentValues values = new ContentValues(); values.put("catalog_key", key);
            db.update("media_local_catalog", values, "owner=? AND dialog=? AND mid=?", args);
            if (!key.isEmpty()) {
                ContentValues directory = new ContentValues(); directory.put("title_key", key);
                directory.put("title", TjMediaTitle.normalizeSearch(title)); directory.put("series", series ? 1 : 0);
                db.insertWithOnConflict("media_catalog_titles", null, directory, SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }

    static void index(SQLiteDatabase db, ContentValues media) {
        String[] args = {media.getAsString("owner"), media.getAsString("dialog"), media.getAsString("mid")};
        int kind = media.getAsInteger("media_type");
        if (kind != TjMediaKind.VIDEO && !(kind == TjMediaKind.DOCUMENT && TjVideoFormat.isSupportedContainer(null, media.getAsString("filename")))) {
            db.delete("media_local_catalog", "owner=? AND dialog=? AND mid=?", args); return;
        }
        TjMediaLocalIdentity hint = TjMediaLocalIdentity.parse(media.getAsString("filename"), media.getAsString("caption"));
        try (Cursor current = db.query("media_local_catalog", new String[]{"document", "manual"},
                "owner=? AND dialog=? AND mid=?", args, null, null, null)) {
            if (current.moveToFirst() && current.getInt(1) != 0 && current.getLong(0) == media.getAsLong("document")) {
                ContentValues numbering = new ContentValues();
                numbering.put("season", hint.season); numbering.put("episode", hint.episode);
                db.update("media_local_catalog", numbering, "owner=? AND dialog=? AND mid=?", args);
                refreshTitle(db, args);
                return;
            }
        }
        ContentValues values = new ContentValues();
        values.put("owner", media.getAsLong("owner")); values.put("dialog", media.getAsLong("dialog"));
        values.put("mid", media.getAsInteger("mid")); values.put("document", media.getAsLong("document"));
        values.put("title_key", hint.key); values.put("title", hint.name); values.put("series", hint.series ? 1 : 0);
        values.put("season", hint.season); values.put("episode", hint.episode); values.put("uncertain", hint.uncertain ? 1 : 0);
        values.put("manual", 0);
        values.put("year", hint.year);
        if (db.update("media_local_catalog", values, "owner=? AND dialog=? AND mid=?", args) == 0)
            db.insertOrThrow("media_local_catalog", null, values);
        refreshTitle(db, args);
    }

    static boolean backfill(SQLiteDatabase db) {
        long last;
        try (Cursor cursor = db.rawQuery("SELECT last_row FROM media_local_progress WHERE id=1", null)) {
            cursor.moveToFirst(); last = cursor.getLong(0);
        }
        int count = 0;
        db.beginTransaction();
        try (Cursor rows = db.rawQuery("SELECT rowid,owner,dialog,mid,document,filename,caption,media_type FROM media WHERE rowid>? ORDER BY rowid LIMIT 128", new String[]{Long.toString(last)})) {
            while (rows.moveToNext()) {
                ContentValues values = new ContentValues();
                last = rows.getLong(0); count++;
                values.put("owner", rows.getLong(1)); values.put("dialog", rows.getLong(2));
                values.put("mid", rows.getInt(3)); values.put("document", rows.getLong(4));
                values.put("filename", rows.getString(5)); values.put("caption", rows.getString(6));
                values.put("media_type", rows.getInt(7)); index(db, values);
            }
            db.execSQL("UPDATE media_local_progress SET last_row=? WHERE id=1", new Object[]{last});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return count < 128;
    }
}
