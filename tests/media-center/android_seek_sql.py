"""Emit production-schema seek checks for the Android sqlite3 shell.

Pipe stdout to sqlite3 on an empty, test-owned emulator database. Does not log in,
invoke application components, download media or inspect any user database.
"""
import pathlib
import re

root = pathlib.Path(__file__).resolve().parents[2]
source = (root / "TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaStore.java").read_text()
keys = (root / "TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaPageKey.java").read_text()
creation = source.split("public void onCreate(SQLiteDatabase db)", 1)[1].split("@Override", 1)[0]
print(".bail on\n.timeout 3000\nSELECT 'SQLite version',sqlite_version();")
for statement in re.findall(r"db.execSQL\((.*?)\);", creation, re.S):
    print("".join(re.findall(r'"([^"]*)"', statement)) + ";")
print("""
BEGIN;
WITH RECURSIVE fixture(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM fixture WHERE n<5000)
INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date,played)
SELECT 1, -(n%19+1), n, n, zeroblob(256), 'סרט.mkv', 'שם בעברית caption',
1700000000-(n/16),1700000000-(n/32) FROM fixture;
INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date,played)
SELECT 2,dialog,mid,document,data,filename,caption,date,played FROM media WHERE owner=1;
COMMIT;
CREATE TEMP TABLE assertion(ok INTEGER NOT NULL CHECK(ok=1));
INSERT INTO assertion VALUES((SELECT count(*)=10000 FROM media));
""")
for prefix, column in [("DATE", "date"), ("PLAYED", "played")]:
    order = re.search(prefix + r'_ORDER = "([^"]+)"', keys).group(1)
    predicate = re.search(prefix + r'_AFTER = "([^"]+)"', keys).group(1)
    for expression in ["stamp", "stamp", "dialog", "dialog", "mid"]:
        predicate = predicate.replace("?", "(SELECT " + expression + " FROM boundary)", 1)
    query = "SELECT dialog,mid FROM media WHERE owner=1" + predicate + " ORDER BY " + order + " LIMIT 201"
    print("SAVEPOINT scenario;")
    print("CREATE TEMP TABLE boundary AS SELECT " + column + " AS stamp,dialog,mid FROM media WHERE owner=1 ORDER BY " + order + " LIMIT 1 OFFSET 199;")
    print("CREATE TEMP TABLE expected AS SELECT dialog,mid FROM media WHERE owner=1 ORDER BY " + order + " LIMIT 201 OFFSET 200;")
    print("CREATE TEMP TABLE actual AS " + query + ";")
    print("INSERT INTO assertion VALUES(NOT EXISTS(SELECT * FROM expected EXCEPT SELECT * FROM actual));")
    print("INSERT INTO assertion VALUES(NOT EXISTS(SELECT * FROM actual EXCEPT SELECT * FROM expected));")
    print("DELETE FROM media WHERE owner=1 AND mid IN (SELECT mid FROM media WHERE owner=1 ORDER BY " + order + " LIMIT 100);")
    print("INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date,played) VALUES(1,-99,90000,90000,zeroblob(256),'new','',1900000000,1900000000);")
    print("DELETE FROM actual; INSERT INTO actual " + query + ";")
    print("INSERT INTO assertion VALUES(NOT EXISTS(SELECT * FROM expected EXCEPT SELECT * FROM actual));")
    print("INSERT INTO assertion VALUES(NOT EXISTS(SELECT * FROM actual EXCEPT SELECT * FROM expected));")
    print("INSERT INTO assertion VALUES((SELECT count(*)=5000 FROM media WHERE owner=2));")
    print("EXPLAIN QUERY PLAN " + query + ";")
    print("ROLLBACK TO scenario; RELEASE scenario;")
    print("SELECT 'PASS " + prefix + " seek identities, mutations and account isolation';")
print("PRAGMA integrity_check; SELECT 'PASS Android SQLite seek suite';")
