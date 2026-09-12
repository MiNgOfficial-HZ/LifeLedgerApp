package com.lifebook.ledger.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.lifebook.ledger.model.Category;
import com.lifebook.ledger.model.Book;
import com.lifebook.ledger.model.Budget;
import com.lifebook.ledger.model.Recurring;
import com.lifebook.ledger.model.Record;
import com.lifebook.ledger.model.Transfer;
import com.lifebook.ledger.model.Trip;
import com.lifebook.ledger.model.TripRecord;
import com.lifebook.ledger.util.U;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "ledger.db";
    public static final int DB_VERSION = 5;
    public static final String TYPE_EXPENSE = "expense";
    public static final String TYPE_INCOME = "income";
    public static final String BOOK_MAIN = "main";
    public static final String BOOK_VAULT = "vault";

    public DbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTables(db);
        createIndexes(db);
        seedBooks(db);
        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createTripTables(db);
        }
        if (oldVersion < 3) {
            if (!hasColumn(db, "record", "book")) {
                db.execSQL("ALTER TABLE record ADD COLUMN book TEXT NOT NULL DEFAULT 'main'");
            }
            if (hasColumn(db, "trip", "added_book") == false) {
                db.execSQL("ALTER TABLE trip ADD COLUMN added_book TEXT NOT NULL DEFAULT 'main'");
            }
        }
        if (oldVersion < 4) {
            createBookTable(db);
            seedBooks(db);
        }
        if (oldVersion < 5) {
            createBudgetTable(db);
            createTransferTable(db);
            createRecurringTable(db);
            if (!hasColumn(db, "record", "deleted")) {
                db.execSQL("ALTER TABLE record ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");
            }
            createIndexes(db);
        }
    }

    private boolean hasColumn(SQLiteDatabase db, String table, String column) {
        try {
            Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
            while (c.moveToNext()) {
                String name = c.getString(1);
                if (column.equals(name)) {
                    c.close();
                    return true;
                }
            }
            c.close();
        } catch (Exception ignored) {
        }
        return false;
    }

    private void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE category (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, parent_id INTEGER NOT NULL DEFAULT 0, name TEXT NOT NULL, sort INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE record (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, amount_cents INTEGER NOT NULL, cat1_id INTEGER NOT NULL DEFAULT 0, cat1_name TEXT NOT NULL DEFAULT '', cat2_id INTEGER NOT NULL DEFAULT 0, cat2_name TEXT NOT NULL DEFAULT '', date TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', book TEXT NOT NULL DEFAULT 'main', deleted INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        createTripTables(db);
        createBookTable(db);
        createBudgetTable(db);
        createTransferTable(db);
        createRecurringTable(db);
    }

    private void createBudgetTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS budget (id INTEGER PRIMARY KEY AUTOINCREMENT, book TEXT NOT NULL, type TEXT NOT NULL, cat1_id INTEGER NOT NULL DEFAULT 0, cat1_name TEXT NOT NULL DEFAULT '', amount_cents INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)");
    }

    private void createTransferTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS transfer (id INTEGER PRIMARY KEY AUTOINCREMENT, from_book TEXT NOT NULL, to_book TEXT NOT NULL, amount_cents INTEGER NOT NULL, date TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', deleted INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
    }

    private void createRecurringTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS recurring (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, amount_cents INTEGER NOT NULL, cat1_id INTEGER NOT NULL DEFAULT 0, cat1_name TEXT NOT NULL DEFAULT '', cat2_id INTEGER NOT NULL DEFAULT 0, cat2_name TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', book TEXT NOT NULL DEFAULT 'main', day_of_month INTEGER NOT NULL DEFAULT 1, enabled INTEGER NOT NULL DEFAULT 1, last_run TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
    }

    private void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_record_date ON record(date)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_record_book ON record(book)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_record_type_book ON record(type, book)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_record_deleted ON record(deleted)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transfer_date ON transfer(date)");
    }

    private void createTripTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS trip (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, start_date TEXT NOT NULL, end_date TEXT, note TEXT NOT NULL DEFAULT '', finished INTEGER NOT NULL DEFAULT 0, added_to_main INTEGER NOT NULL DEFAULT 0, added_book TEXT NOT NULL DEFAULT 'main', added_record_id INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS trip_record (id INTEGER PRIMARY KEY AUTOINCREMENT, trip_id INTEGER NOT NULL, amount_cents INTEGER NOT NULL, category TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', date TEXT NOT NULL, created_at INTEGER NOT NULL)");
    }

    private void createBookTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS book (id INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT NOT NULL UNIQUE, name TEXT NOT NULL, sort INTEGER NOT NULL DEFAULT 0, builtin INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)");
    }

    /** 确保内置两本账存在（主账本 main / 小金库 vault） */
    private void seedBooks(SQLiteDatabase db) {
        if (!hasBook(db, BOOK_MAIN)) insertBook(db, BOOK_MAIN, "主账本", 0, true);
        if (!hasBook(db, BOOK_VAULT)) insertBook(db, BOOK_VAULT, "小金库", 1, true);
    }

    private boolean hasBook(SQLiteDatabase db, String key) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM book WHERE key=?", new String[]{key});
        boolean has = false;
        if (c.moveToFirst()) has = c.getInt(0) > 0;
        c.close();
        return has;
    }

    private long insertBook(SQLiteDatabase db, String key, String name, int sort, boolean builtin) {
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("name", name);
        cv.put("sort", sort);
        cv.put("builtin", builtin ? 1 : 0);
        cv.put("created_at", System.currentTimeMillis());
        return db.insert("book", null, cv);
    }

    // ===== 预设分类 =====
    private void seed(SQLiteDatabase db) {
        int s = 0;
        s = seedCat(db, "income", 0, "工资收入", new String[]{"实习工资", "家教收入", "校内兼职"}, s);
        s = seedCat(db, "income", 0, "生活费", new String[]{"每月固定生活费", "额外补助"}, s);
        s = seedCat(db, "income", 0, "奖学金", new String[]{"校级奖学金", "国家级奖学金", "竞赛奖金"}, s);
        s = seedCat(db, "income", 0, "红包收入", new String[]{"压岁钱", "节日红包", "生日红包"}, s);
        s = seedCat(db, "income", 0, "二手出售", new String[]{"闲置数码", "书籍教材"}, s);
        seedCat(db, "income", 0, "其他收入", null, s);

        s = 0;
        s = seedCat(db, "expense", 0, "吃喝", new String[]{"一日三餐", "去食堂", "奶茶", "外卖", "零食水果", "聚餐"}, s);
        s = seedCat(db, "expense", 0, "存旅行经费", new String[]{"吃喝", "交通", "住宿", "门票"}, s);
        s = seedCat(db, "expense", 0, "交通出行", new String[]{"公交地铁", "打车", "共享单车", "火车票"}, s);
        s = seedCat(db, "expense", 0, "购物消费", new String[]{"衣物鞋帽", "日用品", "数码产品"}, s);
        s = seedCat(db, "expense", 0, "学习用品", new String[]{"书籍", "文具", "网课资料"}, s);
        s = seedCat(db, "expense", 0, "娱乐休闲", new String[]{"电影", "游戏", "KTV", "运动健身"}, s);
        s = seedCat(db, "expense", 0, "医疗健康", new String[]{"药品", "门诊"}, s);
        s = seedCat(db, "expense", 0, "人情往来", new String[]{"礼物", "请客"}, s);
        seedCat(db, "expense", 0, "其他支出", null, s);
    }

    private int seedCat(SQLiteDatabase db, String type, long parentId, String name, String[] children, int sort) {
        ContentValues cv = new ContentValues();
        cv.put("type", type);
        cv.put("parent_id", parentId);
        cv.put("name", name);
        cv.put("sort", sort);
        long pid = db.insert("category", null, cv);
        sort++;
        if (children != null) {
            int cs = 0;
            for (String child : children) {
                ContentValues cc = new ContentValues();
                cc.put("type", type);
                cc.put("parent_id", pid);
                cc.put("name", child);
                cc.put("sort", cs++);
                db.insert("category", null, cc);
            }
        }
        return sort;
    }

    public void seedIfEmpty() {
        if (categoryCount() == 0) seed(getWritableDatabase());
    }

    private int categoryCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM category", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    // ===== 分类 =====
    public List<Category> topCategories(String type) {
        return queryCategories("SELECT * FROM category WHERE type=? AND parent_id=0 ORDER BY sort, id", new String[]{type});
    }

    public List<Category> childCategories(String type, long parentId) {
        return queryCategories("SELECT * FROM category WHERE type=? AND parent_id=? ORDER BY sort, id", new String[]{type, String.valueOf(parentId)});
    }

    public List<Category> allCategories(String type) {
        return queryCategories("SELECT * FROM category WHERE type=? ORDER BY parent_id, sort, id", new String[]{type});
    }

    private List<Category> queryCategories(String sql, String[] args) {
        List<Category> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) {
            Category cat = new Category();
            cat.id = c.getLong(c.getColumnIndexOrThrow("id"));
            cat.type = c.getString(c.getColumnIndexOrThrow("type"));
            cat.parentId = c.getLong(c.getColumnIndexOrThrow("parent_id"));
            cat.name = c.getString(c.getColumnIndexOrThrow("name"));
            cat.sort = c.getInt(c.getColumnIndexOrThrow("sort"));
            out.add(cat);
        }
        c.close();
        return out;
    }

    public Category categoryById(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM category WHERE id=?", new String[]{String.valueOf(id)});
        Category cat = null;
        if (c.moveToFirst()) {
            cat = new Category();
            cat.id = c.getLong(c.getColumnIndexOrThrow("id"));
            cat.type = c.getString(c.getColumnIndexOrThrow("type"));
            cat.parentId = c.getLong(c.getColumnIndexOrThrow("parent_id"));
            cat.name = c.getString(c.getColumnIndexOrThrow("name"));
            cat.sort = c.getInt(c.getColumnIndexOrThrow("sort"));
        }
        c.close();
        return cat;
    }

    public long addCategory(String type, long parentId, String name) {
        int sort = 0;
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(MAX(sort),-1) FROM category WHERE type=? AND parent_id=?", new String[]{type, String.valueOf(parentId)});
        if (c.moveToFirst()) sort = c.getInt(0) + 1;
        c.close();
        ContentValues cv = new ContentValues();
        cv.put("type", type);
        cv.put("parent_id", parentId);
        cv.put("name", name);
        cv.put("sort", sort);
        return getWritableDatabase().insert("category", null, cv);
    }

    public void renameCategory(long id, String name) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        getWritableDatabase().update("category", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteCategory(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("category", "parent_id=?", new String[]{String.valueOf(id)});
            db.delete("category", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public int countRecordsByCat(long catId) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM record WHERE cat1_id=? OR cat2_id=?",
                new String[]{String.valueOf(catId), String.valueOf(catId)});
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    /** 确保存在支出大分类「出游支出」，返回其 id */
    public long ensureOutingCategory() {
        for (Category c : topCategories(TYPE_EXPENSE)) {
            if ("出游支出".equals(c.name)) return c.id;
        }
        return addCategory(TYPE_EXPENSE, 0, "出游支出");
    }

    // ===== 账本 =====
    public List<Book> books() {
        List<Book> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM book ORDER BY sort, id", null);
        while (c.moveToNext()) out.add(readBook(c));
        c.close();
        return out;
    }

    public Book bookByKey(String key) {
        if (key == null || key.isEmpty()) key = BOOK_MAIN;
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM book WHERE key=?", new String[]{key});
        Book b = null;
        if (c.moveToFirst()) b = readBook(c);
        c.close();
        return b;
    }

    private Book readBook(Cursor c) {
        Book b = new Book();
        b.id = c.getLong(c.getColumnIndexOrThrow("id"));
        b.key = c.getString(c.getColumnIndexOrThrow("key"));
        b.name = c.getString(c.getColumnIndexOrThrow("name"));
        b.sort = c.getInt(c.getColumnIndexOrThrow("sort"));
        b.builtin = c.getInt(c.getColumnIndexOrThrow("builtin")) != 0;
        return b;
    }

    /** 返回账本的显示名称，未知 key 回退到 key 本身 */
    public String bookName(String key) {
        Book b = bookByKey(key);
        if (b != null) return b.name;
        if (BOOK_MAIN.equals(key)) return "主账本";
        if (BOOK_VAULT.equals(key)) return "小金库";
        return key == null || key.isEmpty() ? "账本" : key;
    }

    public String bookLabel(String key) {
        return bookName(key);
    }

    /** 新建自定义账本，返回自动生成的 key */
    public Book addBook(String name) {
        String key;
        int n = 0;
        do {
            key = "book_" + System.currentTimeMillis() + (n == 0 ? "" : "_" + n);
            n++;
        } while (bookByKey(key) != null);
        int sort = nextBookSort();
        long id = insertBook(getWritableDatabase(), key, name, sort, false);
        return new Book(id, key, name, sort, false);
    }

    private int nextBookSort() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(MAX(sort),-1)+1 FROM book", null);
        int s = 0;
        if (c.moveToFirst()) s = c.getInt(0);
        c.close();
        return s;
    }

    public void renameBook(String key, String name) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        getWritableDatabase().update("book", cv, "key=?", new String[]{key});
    }

    /** 删除自定义账本及其下全部记录；内置账本不可删。返回是否成功 */
    public boolean deleteBook(String key) {
        Book b = bookByKey(key);
        if (b == null || b.builtin) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("record", "book=?", new String[]{key});
            db.delete("transfer", "from_book=? OR to_book=?", new String[]{key, key});
            db.delete("budget", "book=?", new String[]{key});
            db.delete("recurring", "book=?", new String[]{key});
            ContentValues tv = new ContentValues();
            tv.put("added_to_main", 0);
            tv.put("added_record_id", 0);
            tv.put("added_book", BOOK_MAIN);
            db.update("trip", tv, "added_book=?", new String[]{key});
            db.delete("book", "key=?", new String[]{key});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return true;
    }

    // ===== 主账本记录 =====
    public long addRecord(String type, long amountCents, long cat1Id, String cat1Name, long cat2Id, String cat2Name, String date, String note, String book) {
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("type", type);
        cv.put("amount_cents", amountCents);
        cv.put("cat1_id", cat1Id);
        cv.put("cat1_name", cat1Name == null ? "" : cat1Name);
        cv.put("cat2_id", cat2Id);
        cv.put("cat2_name", cat2Name == null ? "" : cat2Name);
        cv.put("date", date);
        cv.put("note", note == null ? "" : note);
        cv.put("book", book == null ? BOOK_MAIN : book);
        cv.put("created_at", now);
        cv.put("updated_at", now);
        return getWritableDatabase().insert("record", null, cv);
    }

    public void updateRecord(long id, String type, long amountCents, long cat1Id, String cat1Name, long cat2Id, String cat2Name, String date, String note) {
        ContentValues cv = new ContentValues();
        cv.put("type", type);
        cv.put("amount_cents", amountCents);
        cv.put("cat1_id", cat1Id);
        cv.put("cat1_name", cat1Name == null ? "" : cat1Name);
        cv.put("cat2_id", cat2Id);
        cv.put("cat2_name", cat2Name == null ? "" : cat2Name);
        cv.put("date", date);
        cv.put("note", note == null ? "" : note);
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("record", cv, "id=?", new String[]{String.valueOf(id)});
    }

    /** 软删除：移入回收站，可从回收站恢复 */
    public void deleteRecord(long id) {
        ContentValues cv = new ContentValues();
        cv.put("deleted", 1);
        getWritableDatabase().update("record", cv, "id=?", new String[]{String.valueOf(id)});
    }

    /** 彻底删除（回收站里执行） */
    public void purgeRecord(long id) {
        getWritableDatabase().delete("record", "id=?", new String[]{String.valueOf(id)});
    }

    public void restoreRecord(long id) {
        ContentValues cv = new ContentValues();
        cv.put("deleted", 0);
        getWritableDatabase().update("record", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public List<Record> records(String month, String type, String book) {
        StringBuilder sql = new StringBuilder("SELECT * FROM record WHERE deleted=0");
        List<String> args = new ArrayList<>();
        if (month != null) {
            String[] mr = monthRange(month);
            sql.append(" AND date >= ? AND date < ?");
            args.add(mr[0]);
            args.add(mr[1]);
        }
        if (type != null) {
            sql.append(" AND type=?");
            args.add(type);
        }
        if (book != null) {
            sql.append(" AND book=?");
            args.add(book);
        }
        sql.append(" ORDER BY date DESC, id DESC");
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        List<Record> out = new ArrayList<>();
        while (c.moveToNext()) out.add(readRecord(c));
        c.close();
        return out;
    }

    public List<Record> recordsBetween(String start, String end, String type, String book) {
        StringBuilder sql = new StringBuilder("SELECT * FROM record WHERE deleted=0 AND date >= ? AND date <= ?");
        List<String> args = new ArrayList<>();
        args.add(start);
        args.add(end);
        if (type != null) {
            sql.append(" AND type=?");
            args.add(type);
        }
        if (book != null) {
            sql.append(" AND book=?");
            args.add(book);
        }
        sql.append(" ORDER BY date, id");
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        List<Record> out = new ArrayList<>();
        while (c.moveToNext()) out.add(readRecord(c));
        c.close();
        return out;
    }

    public int recordCount(String month, String type, String book) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM record WHERE deleted=0");
        List<String> args = new ArrayList<>();
        if (month != null) {
            String[] mr = monthRange(month);
            sql.append(" AND date >= ? AND date < ?");
            args.add(mr[0]);
            args.add(mr[1]);
        }
        if (type != null) {
            sql.append(" AND type=?");
            args.add(type);
        }
        if (book != null) {
            sql.append(" AND book=?");
            args.add(book);
        }
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    private Record readRecord(Cursor c) {
        Record r = new Record();
        r.id = c.getLong(c.getColumnIndexOrThrow("id"));
        r.type = c.getString(c.getColumnIndexOrThrow("type"));
        r.amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
        r.cat1Id = c.getLong(c.getColumnIndexOrThrow("cat1_id"));
        r.cat1Name = c.getString(c.getColumnIndexOrThrow("cat1_name"));
        r.cat2Id = c.getLong(c.getColumnIndexOrThrow("cat2_id"));
        r.cat2Name = c.getString(c.getColumnIndexOrThrow("cat2_name"));
        r.date = c.getString(c.getColumnIndexOrThrow("date"));
        r.note = c.getString(c.getColumnIndexOrThrow("note"));
        try {
            r.deleted = c.getInt(c.getColumnIndexOrThrow("deleted")) != 0;
        } catch (Exception ignored) {
            r.deleted = false;
        }
        String book = c.getString(c.getColumnIndexOrThrow("book"));
        r.book = book == null ? BOOK_MAIN : book;
        r.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        r.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return r;
    }

    /** 返回 [收入, 支出]，单位分；book 为 null 时统计全部账本 */
    public long[] monthTotals(String month, String book) {
        long[] out = new long[2];
        String[] mr = monthRange(month);
        StringBuilder sql = new StringBuilder("SELECT type, SUM(amount_cents) FROM record WHERE deleted=0 AND date >= ? AND date < ?");
        List<String> args = new ArrayList<>();
        args.add(mr[0]);
        args.add(mr[1]);
        if (book != null) {
            sql.append(" AND book=?");
            args.add(book);
        }
        sql.append(" GROUP BY type");
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        while (c.moveToNext()) {
            String t = c.getString(0);
            if (TYPE_INCOME.equals(t)) out[0] = c.getLong(1);
            else out[1] = c.getLong(1);
        }
        c.close();
        return out;
    }

    /** 返回账本累计结余（分）：该账本总收入 - 总支出 + 转入 - 转出，即「这张银行卡还剩多少钱」 */
    public long bookBalance(String book) {
        String b = book == null || book.isEmpty() ? BOOK_MAIN : book;
        long net = 0;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(CASE WHEN type=? THEN amount_cents ELSE -amount_cents END),0) FROM record WHERE deleted=0 AND book=?",
                new String[]{TYPE_INCOME, b});
        if (c.moveToFirst()) net += c.getLong(0);
        c.close();
        Cursor t = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(CASE WHEN from_book=? THEN -amount_cents ELSE amount_cents END),0) FROM transfer WHERE deleted=0 AND (from_book=? OR to_book=?)",
                new String[]{b, b, b});
        if (t.moveToFirst()) net += t.getLong(0);
        t.close();
        return net;
    }

    public Map<String, Long> sumByCat(String month, String type, int level, String book) {
        Map<String, Long> out = new LinkedHashMap<>();
        String col = level == 1 ? "cat1_name" : "CASE WHEN cat2_name='' THEN cat1_name ELSE cat2_name END";
        String[] mr = monthRange(month);
        StringBuilder sql = new StringBuilder("SELECT " + col + " AS k, SUM(amount_cents) AS v FROM record WHERE deleted=0 AND date >= ? AND date < ? AND type=?");
        List<String> args = new ArrayList<>();
        args.add(mr[0]);
        args.add(mr[1]);
        args.add(type);
        if (book != null) {
            sql.append(" AND book=?");
            args.add(book);
        }
        sql.append(" GROUP BY k ORDER BY v DESC");
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        while (c.moveToNext()) {
            String k = c.getString(0);
            if (k == null || k.isEmpty()) k = "未分类";
            out.put(k, c.getLong(1));
        }
        c.close();
        return out;
    }

    public void expenseByDay(String month, long[] out, String book) {
        String[] mr = monthRange(month);
        StringBuilder sql = new StringBuilder("SELECT date, SUM(amount_cents) FROM record WHERE deleted=0 AND date >= ? AND date < ? AND type=?");
        List<String> args = new ArrayList<>();
        args.add(mr[0]);
        args.add(mr[1]);
        args.add(TYPE_EXPENSE);
        if (book != null) {
            sql.append(" AND book=?");
            args.add(book);
        }
        sql.append(" GROUP BY date");
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        while (c.moveToNext()) {
            String d = c.getString(0);
            if (d != null && d.length() >= 10) {
                try {
                    int day = Integer.parseInt(d.substring(8, 10));
                    if (day >= 1 && day <= out.length) out[day - 1] = c.getLong(1);
                } catch (Exception ignored) {
                }
            }
        }
        c.close();
    }

    public List<Record> allRecords() {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM record WHERE deleted=0 ORDER BY date, id", null);
        List<Record> out = new ArrayList<>();
        while (c.moveToNext()) out.add(readRecord(c));
        c.close();
        return out;
    }

    public List<Record> trashRecords() {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM record WHERE deleted=1 ORDER BY updated_at DESC, id DESC", null);
        List<Record> out = new ArrayList<>();
        while (c.moveToNext()) out.add(readRecord(c));
        c.close();
        return out;
    }

    /** 搜索 + 精细筛选：keyword 匹配分类/备注；amountMin/Max 为分，<=0 表示不限；cat1Id<=0 表示不限分类 */
    public List<Record> searchRecords(String month, String type, String book, String keyword,
                                      long amountMin, long amountMax, long cat1Id) {
        StringBuilder sql = new StringBuilder("SELECT * FROM record WHERE deleted=0");
        List<String> args = new ArrayList<>();
        if (month != null) {
            String[] mr = monthRange(month);
            sql.append(" AND date >= ? AND date < ?");
            args.add(mr[0]);
            args.add(mr[1]);
        }
        if (type != null) {
            sql.append(" AND type=?");
            args.add(type);
        }
        if (book != null) {
            sql.append(" AND book=?");
            args.add(book);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (cat1_name LIKE ? OR cat2_name LIKE ? OR note LIKE ?)");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
            args.add(k);
        }
        if (amountMin > 0) {
            sql.append(" AND amount_cents >= ?");
            args.add(String.valueOf(amountMin));
        }
        if (amountMax > 0) {
            sql.append(" AND amount_cents <= ?");
            args.add(String.valueOf(amountMax));
        }
        if (cat1Id > 0) {
            sql.append(" AND cat1_id=?");
            args.add(String.valueOf(cat1Id));
        }
        sql.append(" ORDER BY date DESC, id DESC");
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        List<Record> out = new ArrayList<>();
        while (c.moveToNext()) out.add(readRecord(c));
        c.close();
        return out;
    }

    private static String[] monthRange(String month) {
        String start = month + "-01";
        String next = U.shiftMonth(month, 1);
        return new String[]{start, next + "-01"};
    }

    // ===== 出游 =====
    public long addTrip(String name, String startDate) {
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("start_date", startDate);
        cv.put("created_at", now);
        cv.put("updated_at", now);
        return getWritableDatabase().insert("trip", null, cv);
    }

    public void updateTrip(long id, String name, String startDate) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("start_date", startDate);
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("trip", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteTrip(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("trip_record", "trip_id=?", new String[]{String.valueOf(id)});
            db.delete("trip", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Trip> trips() {
        List<Trip> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM trip ORDER BY finished ASC, start_date DESC, id DESC", null);
        while (c.moveToNext()) out.add(readTrip(c));
        c.close();
        return out;
    }

    public Trip tripById(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM trip WHERE id=?", new String[]{String.valueOf(id)});
        Trip t = null;
        if (c.moveToFirst()) t = readTrip(c);
        c.close();
        return t;
    }

    private Trip readTrip(Cursor c) {
        Trip t = new Trip();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.name = c.getString(c.getColumnIndexOrThrow("name"));
        t.startDate = c.getString(c.getColumnIndexOrThrow("start_date"));
        t.endDate = c.getString(c.getColumnIndexOrThrow("end_date"));
        t.note = c.getString(c.getColumnIndexOrThrow("note"));
        t.finished = c.getInt(c.getColumnIndexOrThrow("finished")) != 0;
        t.addedToMain = c.getInt(c.getColumnIndexOrThrow("added_to_main")) != 0;
        String ab = c.getString(c.getColumnIndexOrThrow("added_book"));
        t.addedBook = ab == null ? BOOK_MAIN : ab;
        t.addedRecordId = c.getLong(c.getColumnIndexOrThrow("added_record_id"));
        t.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        t.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return t;
    }

    public long tripTotal(long tripId) {
        long total = 0;
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount_cents),0) FROM trip_record WHERE trip_id=?",
                new String[]{String.valueOf(tripId)});
        if (c.moveToFirst()) total = c.getLong(0);
        c.close();
        return total;
    }

    public int tripCountRecords(long tripId) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM trip_record WHERE trip_id=?",
                new String[]{String.valueOf(tripId)});
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public long addTripRecord(long tripId, long amountCents, String category, String note, String date) {
        ContentValues cv = new ContentValues();
        cv.put("trip_id", tripId);
        cv.put("amount_cents", amountCents);
        cv.put("category", category == null ? "" : category);
        cv.put("note", note == null ? "" : note);
        cv.put("date", date);
        cv.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("trip_record", null, cv);
    }

    public void updateTripRecord(long id, long amountCents, String category, String note, String date) {
        ContentValues cv = new ContentValues();
        cv.put("amount_cents", amountCents);
        cv.put("category", category == null ? "" : category);
        cv.put("note", note == null ? "" : note);
        cv.put("date", date);
        getWritableDatabase().update("trip_record", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteTripRecord(long id) {
        getWritableDatabase().delete("trip_record", "id=?", new String[]{String.valueOf(id)});
    }

    public List<TripRecord> tripRecords(long tripId) {
        List<TripRecord> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM trip_record WHERE trip_id=? ORDER BY date DESC, id DESC",
                new String[]{String.valueOf(tripId)});
        while (c.moveToNext()) {
            TripRecord r = new TripRecord();
            r.id = c.getLong(c.getColumnIndexOrThrow("id"));
            r.tripId = c.getLong(c.getColumnIndexOrThrow("trip_id"));
            r.amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
            r.category = c.getString(c.getColumnIndexOrThrow("category"));
            r.note = c.getString(c.getColumnIndexOrThrow("note"));
            r.date = c.getString(c.getColumnIndexOrThrow("date"));
            r.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
            out.add(r);
        }
        c.close();
        return out;
    }

    /** 结束出游；book 为 null/"" 表示不合并，否则合并成对应账本的单条支出 */
    public void finishTrip(long tripId, String book) {
        Trip t = tripById(tripId);
        if (t == null) return;
        String today = U.today();
        ContentValues cv = new ContentValues();
        if (!t.finished) {
            cv.put("finished", 1);
            cv.put("end_date", today);
        }
        cv.put("updated_at", System.currentTimeMillis());
        boolean merge = book != null && !book.isEmpty() && bookByKey(book) != null;
        if (merge && !t.addedToMain) {
            long total = tripTotal(tripId);
            if (total > 0) {
                long catId = ensureOutingCategory();
                long recId = addRecord(TYPE_EXPENSE, total, catId, "出游支出", 0, t.name, today,
                        "出游 " + tripDays(t) + " 天 · " + tripCountRecords(tripId) + " 笔 · 明细见出游账本", book);
                cv.put("added_to_main", 1);
                cv.put("added_record_id", recId);
                cv.put("added_book", book);
            }
        }
        getWritableDatabase().update("trip", cv, "id=?", new String[]{String.valueOf(tripId)});
    }

    /** 把之前合并进账本的那条支出删掉 */
    public void removeTripFromMain(long tripId) {
        Trip t = tripById(tripId);
        if (t == null || t.addedRecordId <= 0) return;
        purgeRecord(t.addedRecordId);
        ContentValues cv = new ContentValues();
        cv.put("added_to_main", 0);
        cv.put("added_record_id", 0);
        cv.put("added_book", BOOK_MAIN);
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("trip", cv, "id=?", new String[]{String.valueOf(tripId)});
    }

    private int tripDays(Trip t) {
        try {
            LocalDate s = LocalDate.parse(t.startDate);
            LocalDate e = (t.endDate != null && !t.endDate.isEmpty()) ? LocalDate.parse(t.endDate) : LocalDate.now();
            long d = ChronoUnit.DAYS.between(s, e) + 1;
            return (int) Math.max(1, d);
        } catch (Exception ex) {
            return 1;
        }
    }

    // ===== 账本间转账 =====
    public long addTransfer(String fromBook, String toBook, long amountCents, String date, String note) {
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("from_book", fromBook);
        cv.put("to_book", toBook);
        cv.put("amount_cents", amountCents);
        cv.put("date", date);
        cv.put("note", note == null ? "" : note);
        cv.put("created_at", now);
        cv.put("updated_at", now);
        return getWritableDatabase().insert("transfer", null, cv);
    }

    public void deleteTransfer(long id) {
        ContentValues cv = new ContentValues();
        cv.put("deleted", 1);
        getWritableDatabase().update("transfer", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void purgeTransfer(long id) {
        getWritableDatabase().delete("transfer", "id=?", new String[]{String.valueOf(id)});
    }

    public void restoreTransfer(long id) {
        ContentValues cv = new ContentValues();
        cv.put("deleted", 0);
        getWritableDatabase().update("transfer", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public List<Transfer> transfers(String month, String book) {
        StringBuilder sql = new StringBuilder("SELECT * FROM transfer WHERE deleted=0");
        List<String> args = new ArrayList<>();
        if (month != null) {
            String[] mr = monthRange(month);
            sql.append(" AND date >= ? AND date < ?");
            args.add(mr[0]);
            args.add(mr[1]);
        }
        if (book != null) {
            sql.append(" AND (from_book=? OR to_book=?)");
            args.add(book);
            args.add(book);
        }
        sql.append(" ORDER BY date DESC, id DESC");
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        List<Transfer> out = new ArrayList<>();
        while (c.moveToNext()) out.add(readTransfer(c));
        c.close();
        return out;
    }

    public List<Transfer> allTransfers() {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM transfer WHERE deleted=0 ORDER BY date, id", null);
        List<Transfer> out = new ArrayList<>();
        while (c.moveToNext()) out.add(readTransfer(c));
        c.close();
        return out;
    }

    public List<Transfer> trashTransfers() {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM transfer WHERE deleted=1 ORDER BY updated_at DESC, id DESC", null);
        List<Transfer> out = new ArrayList<>();
        while (c.moveToNext()) out.add(readTransfer(c));
        c.close();
        return out;
    }

    private Transfer readTransfer(Cursor c) {
        Transfer t = new Transfer();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.fromBook = c.getString(c.getColumnIndexOrThrow("from_book"));
        t.toBook = c.getString(c.getColumnIndexOrThrow("to_book"));
        t.amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
        t.date = c.getString(c.getColumnIndexOrThrow("date"));
        t.note = c.getString(c.getColumnIndexOrThrow("note"));
        try {
            t.deleted = c.getInt(c.getColumnIndexOrThrow("deleted")) != 0;
        } catch (Exception ignored) {
        }
        t.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        t.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return t;
    }

    // ===== 预算 =====
    public List<Budget> budgets(String book, String type) {
        StringBuilder sql = new StringBuilder("SELECT * FROM budget WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (book != null) {
            sql.append(" AND book=?");
            args.add(book);
        }
        if (type != null) {
            sql.append(" AND type=?");
            args.add(type);
        }
        sql.append(" ORDER BY cat1_id, id");
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        List<Budget> out = new ArrayList<>();
        while (c.moveToNext()) {
            Budget b = new Budget();
            b.id = c.getLong(c.getColumnIndexOrThrow("id"));
            b.book = c.getString(c.getColumnIndexOrThrow("book"));
            b.type = c.getString(c.getColumnIndexOrThrow("type"));
            b.cat1Id = c.getLong(c.getColumnIndexOrThrow("cat1_id"));
            b.cat1Name = c.getString(c.getColumnIndexOrThrow("cat1_name"));
            b.amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
            b.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
            out.add(b);
        }
        c.close();
        return out;
    }

    public long budgetTotal(String book, String type) {
        long total = 0;
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount_cents),0) FROM budget WHERE book=? AND type=?",
                new String[]{book, type});
        if (c.moveToFirst()) total = c.getLong(0);
        c.close();
        return total;
    }

    /** 设置某分类预算；amountCents<=0 表示删除该分类预算 */
    public void setBudget(String book, String type, long cat1Id, String cat1Name, long amountCents) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("budget", "book=? AND type=? AND cat1_id=?", new String[]{book, type, String.valueOf(cat1Id)});
        if (amountCents > 0) {
            ContentValues cv = new ContentValues();
            cv.put("book", book);
            cv.put("type", type);
            cv.put("cat1_id", cat1Id);
            cv.put("cat1_name", cat1Name == null ? "" : cat1Name);
            cv.put("amount_cents", amountCents);
            cv.put("created_at", System.currentTimeMillis());
            db.insert("budget", null, cv);
        }
    }

    public void deleteBudget(long id) {
        getWritableDatabase().delete("budget", "id=?", new String[]{String.valueOf(id)});
    }

    // ===== 周期记账 =====
    public List<Recurring> recurrings() {
        List<Recurring> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM recurring ORDER BY created_at DESC, id DESC", null);
        while (c.moveToNext()) out.add(readRecurring(c));
        c.close();
        return out;
    }

    private Recurring readRecurring(Cursor c) {
        Recurring r = new Recurring();
        r.id = c.getLong(c.getColumnIndexOrThrow("id"));
        r.type = c.getString(c.getColumnIndexOrThrow("type"));
        r.amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
        r.cat1Id = c.getLong(c.getColumnIndexOrThrow("cat1_id"));
        r.cat1Name = c.getString(c.getColumnIndexOrThrow("cat1_name"));
        r.cat2Id = c.getLong(c.getColumnIndexOrThrow("cat2_id"));
        r.cat2Name = c.getString(c.getColumnIndexOrThrow("cat2_name"));
        r.note = c.getString(c.getColumnIndexOrThrow("note"));
        r.book = c.getString(c.getColumnIndexOrThrow("book"));
        r.dayOfMonth = c.getInt(c.getColumnIndexOrThrow("day_of_month"));
        r.enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) != 0;
        r.lastRun = c.getString(c.getColumnIndexOrThrow("last_run"));
        r.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        r.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return r;
    }

    public long addRecurring(String type, long amountCents, long cat1Id, String cat1Name, long cat2Id, String cat2Name,
                             String note, String book, int dayOfMonth) {
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("type", type);
        cv.put("amount_cents", amountCents);
        cv.put("cat1_id", cat1Id);
        cv.put("cat1_name", cat1Name == null ? "" : cat1Name);
        cv.put("cat2_id", cat2Id);
        cv.put("cat2_name", cat2Name == null ? "" : cat2Name);
        cv.put("note", note == null ? "" : note);
        cv.put("book", book == null ? BOOK_MAIN : book);
        cv.put("day_of_month", dayOfMonth);
        cv.put("enabled", 1);
        cv.put("created_at", now);
        cv.put("updated_at", now);
        return getWritableDatabase().insert("recurring", null, cv);
    }

    public void updateRecurring(long id, String type, long amountCents, long cat1Id, String cat1Name, long cat2Id, String cat2Name,
                                String note, String book, int dayOfMonth, boolean enabled) {
        ContentValues cv = new ContentValues();
        cv.put("type", type);
        cv.put("amount_cents", amountCents);
        cv.put("cat1_id", cat1Id);
        cv.put("cat1_name", cat1Name == null ? "" : cat1Name);
        cv.put("cat2_id", cat2Id);
        cv.put("cat2_name", cat2Name == null ? "" : cat2Name);
        cv.put("note", note == null ? "" : note);
        cv.put("book", book == null ? BOOK_MAIN : book);
        cv.put("day_of_month", dayOfMonth);
        cv.put("enabled", enabled ? 1 : 0);
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("recurring", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteRecurring(long id) {
        getWritableDatabase().delete("recurring", "id=?", new String[]{String.valueOf(id)});
    }

    /** 执行到期周期记账：针对指定月份生成尚未生成过的记录。返回生成的记录数 */
    public int runRecurring(String month) {
        return runRecurring(month, false);
    }

    public int runRecurring(String month, boolean force) {
        int made = 0;
        int today = LocalDate.now().getDayOfMonth();
        boolean isCurrent = month.equals(U.currentMonth());
        for (Recurring r : recurrings()) {
            if (!r.enabled) continue;
            if (alreadyRun(r.id, month)) continue;
            int dim = daysInMonth(month);
            int day = Math.min(r.dayOfMonth, dim);
            if (!force && isCurrent && today < r.dayOfMonth) continue;
            String date = month + "-" + String.format("%02d", day);
            addRecord(r.type, r.amountCents, r.cat1Id, r.cat1Name, r.cat2Id, r.cat2Name, date, r.note, r.book);
            markRun(r.id, month);
            made++;
        }
        return made;
    }

    private boolean alreadyRun(long id, String month) {
        for (Recurring x : recurrings()) {
            if (x.id == id && x.lastRun != null) {
                for (String s : x.lastRun.split(",")) {
                    if (month.equals(s)) return true;
                }
            }
        }
        return false;
    }

    private void markRun(long id, String month) {
        Recurring r = null;
        for (Recurring x : recurrings()) {
            if (x.id == id) { r = x; break; }
        }
        String last = r == null ? "" : (r.lastRun == null ? "" : r.lastRun);
        ContentValues cv = new ContentValues();
        cv.put("last_run", (last.isEmpty() ? "" : last + ",") + month);
        getWritableDatabase().update("recurring", cv, "id=?", new String[]{String.valueOf(id)});
    }

    private static int daysInMonth(String month) {
        try {
            return java.time.YearMonth.parse(month).lengthOfMonth();
        } catch (Exception e) {
            return 30;
        }
    }

    // ===== 导入导出 / 清空 =====
    public void replaceAll(List<Book> books, List<Category> cats, List<Record> recs, List<Trip> trips, List<TripRecord> tripRecords,
                           List<Transfer> transfers, List<Budget> budgets, List<Recurring> recurrings) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM record");
            db.execSQL("DELETE FROM category");
            db.execSQL("DELETE FROM trip");
            db.execSQL("DELETE FROM trip_record");
            db.execSQL("DELETE FROM book");
            db.execSQL("DELETE FROM transfer");
            db.execSQL("DELETE FROM budget");
            db.execSQL("DELETE FROM recurring");
            if (books != null) {
                for (Book b : books) {
                    ContentValues bv = new ContentValues();
                    bv.put("id", b.id);
                    bv.put("key", b.key);
                    bv.put("name", b.name);
                    bv.put("sort", b.sort);
                    bv.put("builtin", b.builtin ? 1 : 0);
                    bv.put("created_at", System.currentTimeMillis());
                    db.insert("book", null, bv);
                }
            }
            seedBooks(db);
            for (Category cat : cats) {
                ContentValues cv = new ContentValues();
                cv.put("id", cat.id);
                cv.put("type", cat.type);
                cv.put("parent_id", cat.parentId);
                cv.put("name", cat.name);
                cv.put("sort", cat.sort);
                db.insert("category", null, cv);
            }
            for (Record r : recs) {
                ContentValues cv = new ContentValues();
                cv.put("id", r.id);
                cv.put("type", r.type);
                cv.put("amount_cents", r.amountCents);
                cv.put("cat1_id", r.cat1Id);
                cv.put("cat1_name", r.cat1Name == null ? "" : r.cat1Name);
                cv.put("cat2_id", r.cat2Id);
                cv.put("cat2_name", r.cat2Name == null ? "" : r.cat2Name);
                cv.put("date", r.date);
                cv.put("note", r.note == null ? "" : r.note);
                cv.put("book", r.book == null ? BOOK_MAIN : r.book);
                cv.put("created_at", r.createdAt > 0 ? r.createdAt : System.currentTimeMillis());
                cv.put("updated_at", r.updatedAt > 0 ? r.updatedAt : System.currentTimeMillis());
                db.insert("record", null, cv);
            }
            for (Trip t : trips) {
                ContentValues cv = new ContentValues();
                cv.put("id", t.id);
                cv.put("name", t.name);
                cv.put("start_date", t.startDate);
                cv.put("end_date", t.endDate == null ? "" : t.endDate);
                cv.put("note", t.note == null ? "" : t.note);
                cv.put("finished", t.finished ? 1 : 0);
                cv.put("added_to_main", t.addedToMain ? 1 : 0);
                cv.put("added_book", t.addedBook == null ? BOOK_MAIN : t.addedBook);
                cv.put("added_record_id", t.addedRecordId);
                cv.put("created_at", t.createdAt > 0 ? t.createdAt : System.currentTimeMillis());
                cv.put("updated_at", t.updatedAt > 0 ? t.updatedAt : System.currentTimeMillis());
                db.insert("trip", null, cv);
            }
            for (TripRecord tr : tripRecords) {
                ContentValues cv = new ContentValues();
                cv.put("id", tr.id);
                cv.put("trip_id", tr.tripId);
                cv.put("amount_cents", tr.amountCents);
                cv.put("category", tr.category == null ? "" : tr.category);
                cv.put("note", tr.note == null ? "" : tr.note);
                cv.put("date", tr.date);
                cv.put("created_at", tr.createdAt > 0 ? tr.createdAt : System.currentTimeMillis());
                db.insert("trip_record", null, cv);
            }
            if (transfers != null) {
                for (Transfer tr : transfers) {
                    ContentValues cv = new ContentValues();
                    cv.put("id", tr.id);
                    cv.put("from_book", tr.fromBook);
                    cv.put("to_book", tr.toBook);
                    cv.put("amount_cents", tr.amountCents);
                    cv.put("date", tr.date);
                    cv.put("note", tr.note == null ? "" : tr.note);
                    cv.put("created_at", tr.createdAt > 0 ? tr.createdAt : System.currentTimeMillis());
                    cv.put("updated_at", tr.updatedAt > 0 ? tr.updatedAt : System.currentTimeMillis());
                    db.insert("transfer", null, cv);
                }
            }
            if (budgets != null) {
                for (Budget b : budgets) {
                    ContentValues cv = new ContentValues();
                    cv.put("id", b.id);
                    cv.put("book", b.book);
                    cv.put("type", b.type);
                    cv.put("cat1_id", b.cat1Id);
                    cv.put("cat1_name", b.cat1Name == null ? "" : b.cat1Name);
                    cv.put("amount_cents", b.amountCents);
                    cv.put("created_at", b.createdAt > 0 ? b.createdAt : System.currentTimeMillis());
                    db.insert("budget", null, cv);
                }
            }
            if (recurrings != null) {
                for (Recurring r : recurrings) {
                    ContentValues cv = new ContentValues();
                    cv.put("id", r.id);
                    cv.put("type", r.type);
                    cv.put("amount_cents", r.amountCents);
                    cv.put("cat1_id", r.cat1Id);
                    cv.put("cat1_name", r.cat1Name == null ? "" : r.cat1Name);
                    cv.put("cat2_id", r.cat2Id);
                    cv.put("cat2_name", r.cat2Name == null ? "" : r.cat2Name);
                    cv.put("note", r.note == null ? "" : r.note);
                    cv.put("book", r.book == null ? BOOK_MAIN : r.book);
                    cv.put("day_of_month", r.dayOfMonth);
                    cv.put("enabled", r.enabled ? 1 : 0);
                    cv.put("last_run", r.lastRun == null ? "" : r.lastRun);
                    cv.put("created_at", r.createdAt > 0 ? r.createdAt : System.currentTimeMillis());
                    cv.put("updated_at", r.updatedAt > 0 ? r.updatedAt : System.currentTimeMillis());
                    db.insert("recurring", null, cv);
                }
            }
            ensureBookExtras(db, recs, trips);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** 备份里可能出现未被 book 表收录的账本 key（旧备份 / 未来扩展），补一个同名账本避免记录悬空 */
    private void ensureBookExtras(SQLiteDatabase db, List<Record> recs, List<Trip> trips) {
        Set<String> keys = new HashSet<>();
        if (recs != null) for (Record r : recs) keys.add(r.book == null ? BOOK_MAIN : r.book);
        if (trips != null) for (Trip t : trips) keys.add(t.addedBook == null ? BOOK_MAIN : t.addedBook);
        int sort = 0;
        Cursor c = db.rawQuery("SELECT COALESCE(MAX(sort),-1)+1 FROM book", null);
        if (c.moveToFirst()) sort = c.getInt(0);
        c.close();
        for (String key : keys) {
            if (key == null || key.isEmpty() || hasBook(db, key)) continue;
            String name = BOOK_MAIN.equals(key) ? "主账本" : (BOOK_VAULT.equals(key) ? "小金库" : "自定义账本");
            insertBook(db, key, name, sort, false);
            sort++;
        }
    }

    /** 合并导入：只新增备份里有、本机还没有的账本/记录/转账/周期记账，绝不清空现有数据 */
    public void mergeImport(List<Book> books, List<Record> recs, List<Trip> trips, List<TripRecord> tripRecords,
                            List<Transfer> transfers, List<Budget> budgets, List<Recurring> recurrings) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (books != null) {
                for (Book b : books) {
                    if (b.key == null || b.key.isEmpty()) continue;
                    if (bookByKey(b.key) != null) continue;
                    insertBook(db, b.key, b.name == null ? b.key : b.name, nextBookSort(), b.builtin);
                }
            }
            for (Record r : recs) {
                if (recordExists(db, r)) continue;
                ContentValues cv = new ContentValues();
                cv.put("type", r.type);
                cv.put("amount_cents", r.amountCents);
                cv.put("cat1_id", r.cat1Id);
                cv.put("cat1_name", r.cat1Name == null ? "" : r.cat1Name);
                cv.put("cat2_id", r.cat2Id);
                cv.put("cat2_name", r.cat2Name == null ? "" : r.cat2Name);
                cv.put("date", r.date);
                cv.put("note", r.note == null ? "" : r.note);
                cv.put("book", r.book == null ? BOOK_MAIN : r.book);
                cv.put("deleted", 0);
                cv.put("created_at", r.createdAt > 0 ? r.createdAt : System.currentTimeMillis());
                cv.put("updated_at", r.updatedAt > 0 ? r.updatedAt : System.currentTimeMillis());
                db.insert("record", null, cv);
            }
            if (transfers != null) {
                for (Transfer tr : transfers) {
                    if (transferExists(db, tr)) continue;
                    ContentValues cv = new ContentValues();
                    cv.put("from_book", tr.fromBook);
                    cv.put("to_book", tr.toBook);
                    cv.put("amount_cents", tr.amountCents);
                    cv.put("date", tr.date);
                    cv.put("note", tr.note == null ? "" : tr.note);
                    cv.put("deleted", 0);
                    cv.put("created_at", tr.createdAt > 0 ? tr.createdAt : System.currentTimeMillis());
                    cv.put("updated_at", tr.updatedAt > 0 ? tr.updatedAt : System.currentTimeMillis());
                    db.insert("transfer", null, cv);
                }
            }
            if (recurrings != null) {
                for (Recurring r : recurrings) {
                    if (recurringExists(db, r)) continue;
                    ContentValues cv = new ContentValues();
                    cv.put("type", r.type);
                    cv.put("amount_cents", r.amountCents);
                    cv.put("cat1_id", r.cat1Id);
                    cv.put("cat1_name", r.cat1Name == null ? "" : r.cat1Name);
                    cv.put("cat2_id", r.cat2Id);
                    cv.put("cat2_name", r.cat2Name == null ? "" : r.cat2Name);
                    cv.put("note", r.note == null ? "" : r.note);
                    cv.put("book", r.book == null ? BOOK_MAIN : r.book);
                    cv.put("day_of_month", r.dayOfMonth);
                    cv.put("enabled", r.enabled ? 1 : 0);
                    cv.put("last_run", r.lastRun == null ? "" : r.lastRun);
                    cv.put("created_at", r.createdAt > 0 ? r.createdAt : System.currentTimeMillis());
                    cv.put("updated_at", r.updatedAt > 0 ? r.updatedAt : System.currentTimeMillis());
                    db.insert("recurring", null, cv);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private boolean recordExists(SQLiteDatabase db, Record r) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM record WHERE deleted=0 AND type=? AND amount_cents=? AND date=? AND book=? AND note=? AND cat1_id=? AND cat2_id=?",
                new String[]{r.type, String.valueOf(r.amountCents), r.date, r.book == null ? BOOK_MAIN : r.book,
                        r.note == null ? "" : r.note, String.valueOf(r.cat1Id), String.valueOf(r.cat2Id)});
        boolean exists = false;
        if (c.moveToFirst()) exists = c.getInt(0) > 0;
        c.close();
        return exists;
    }

    private boolean transferExists(SQLiteDatabase db, Transfer tr) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM transfer WHERE deleted=0 AND from_book=? AND to_book=? AND amount_cents=? AND date=? AND note=?",
                new String[]{tr.fromBook, tr.toBook, String.valueOf(tr.amountCents), tr.date, tr.note == null ? "" : tr.note});
        boolean exists = false;
        if (c.moveToFirst()) exists = c.getInt(0) > 0;
        c.close();
        return exists;
    }

    private boolean recurringExists(SQLiteDatabase db, Recurring r) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM recurring WHERE type=? AND amount_cents=? AND cat1_id=? AND cat2_id=? AND note=? AND book=? AND day_of_month=?",
                new String[]{r.type, String.valueOf(r.amountCents), String.valueOf(r.cat1Id), String.valueOf(r.cat2Id),
                        r.note == null ? "" : r.note, r.book == null ? BOOK_MAIN : r.book, String.valueOf(r.dayOfMonth)});
        boolean exists = false;
        if (c.moveToFirst()) exists = c.getInt(0) > 0;
        c.close();
        return exists;
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM record");
            db.execSQL("DELETE FROM category");
            db.execSQL("DELETE FROM trip");
            db.execSQL("DELETE FROM trip_record");
            db.execSQL("DELETE FROM transfer");
            db.execSQL("DELETE FROM budget");
            db.execSQL("DELETE FROM recurring");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        seed(getWritableDatabase());
    }

    /** 清空回收站 */
    public void emptyTrash() {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM record WHERE deleted=1");
        db.execSQL("DELETE FROM transfer WHERE deleted=1");
    }
}
