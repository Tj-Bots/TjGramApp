"""Production seek predicates/indexes against real desktop SQLite, not Android UI."""
import pathlib
import random
import re
import sqlite3

ROOT = pathlib.Path(__file__).resolve().parents[2]
store = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaStore.java").read_text()
keys = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaPageKey.java").read_text()


def constant(name):
    return re.search(r'public static final String ' + name + r' = "([^"]+)";', keys).group(1)


schema = "".join(re.findall(r'"([^"]*)"', re.search(r'db.execSQL\(("CREATE TABLE media .*?)\);', store, re.S).group(1)))
db = sqlite3.connect(":memory:")
db.execute(schema)
creation = store.split("public void onCreate(SQLiteDatabase db)", 1)[1].split("@Override", 1)[0]
for sql in re.findall(r'db.execSQL\("(CREATE INDEX [^"]+)"\)', creation):
    db.execute(sql)
random.seed(42)
for mid in range(1, 1601):
    for owner in [1, 2]:
        db.execute("INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date,played,media_type,favorite) "
                   "VALUES(?,?,?,?,?,'file','caption',?,?,?,?)",
                   (owner, random.randint(-9, 9), mid, mid, b"fixture", random.randint(100, 110),
                    random.randint(1000, 1008), mid % 6 + 1, int(mid % 7 == 0)))
checks = 0
for time_column, prefix in [("date", "DATE"), ("played", "PLAYED")]:
    for filter_sql, filter_args in [("", ()), (" AND media_type=?", (2,)), (" AND favorite=1", ())]:
        select = "SELECT " + time_column + ",dialog,mid FROM media WHERE owner=?" + filter_sql
        order = " ORDER BY " + constant(prefix + "_ORDER")
        expected = db.execute(select + order, (1,) + filter_args).fetchall()
        actual = []
        after = None
        while True:
            sql = select
            args = (1,) + filter_args
            if after is not None:
                time, dialog, mid = after
                sql += constant(prefix + "_AFTER")
                args += (time, time, dialog, dialog, mid)
            rows = db.execute(sql + order + " LIMIT 201", args).fetchall()
            actual.extend(rows[:200])
            if len(rows) <= 200:
                break
            after = rows[199]
        assert actual == expected
        assert len({(r[1], r[2]) for r in actual}) == len(actual)
        checks += 1

# A key is independent of the number of preceding rows. Delete already-seen rows
# and insert newer rows, then verify the exact remaining historical set.
order = " ORDER BY " + constant("DATE_ORDER")
first = db.execute("SELECT date,dialog,mid FROM media WHERE owner=1" + order + " LIMIT 201").fetchall()
time, dialog, mid = first[199]
tail_sql = "SELECT date,dialog,mid FROM media WHERE owner=?" + constant("DATE_AFTER") + order
args = (1, time, time, dialog, dialog, mid)
tail = db.execute(tail_sql, args).fetchall()
for _, d, m in first[:50]:
    db.execute("DELETE FROM media WHERE owner=1 AND dialog=? AND mid=?", (d, m))
db.execute("INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date) VALUES(1,-99,99999,99999,?,'new','',9999)", (b"new",))
assert db.execute(tail_sql, args).fetchall() == tail
db.execute("DELETE FROM media WHERE owner=1 AND dialog=? AND mid=?", (dialog, mid))
assert db.execute(tail_sql, args).fetchall() == tail
checks += 2

# Empty sets and a nonexistent boundary still terminate, without offset arithmetic.
assert not db.execute(tail_sql, (99, time, time, dialog, dialog, mid)).fetchall()
assert not db.execute(tail_sql, (1, 0, 0, 0, 0, 0)).fetchall()
checks += 2
plan = [r[3] for r in db.execute("EXPLAIN QUERY PLAN " + tail_sql + " LIMIT 201", args)]
assert any("media_recent_page" in line and "date<" in line for line in plan), plan
assert not any("TEMP B-TREE" in line for line in plan), plan
checks += 1
assert "after.matches(owner, byPlayed)" in store
assert store.index("result.nextKey = new TjMediaPageKey") < store.index("TLRPC.Message raw = deserialize", store.index("private void loadInternal"))
assert '"201 OFFSET "' not in store
assert "records.nextKey" in (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaAutoMatcher.java").read_text()
checks += 1
db.close()
print(f"{checks} seek pagination sets/mutations/plan checks passed; desktop SQLite, not Android")
