package com.lifebook.ledger.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.lifebook.ledger.ShareProvider;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Book;
import com.lifebook.ledger.model.Budget;
import com.lifebook.ledger.model.Category;
import com.lifebook.ledger.model.Recurring;
import com.lifebook.ledger.model.Record;
import com.lifebook.ledger.model.Transfer;
import com.lifebook.ledger.model.Trip;
import com.lifebook.ledger.model.TripRecord;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** 全量备份：data.json（含出游账本）+ ledger.db + 恢复说明 */
public class BackupManager {

    public static class ImportResult {
        public final List<Book> books = new ArrayList<>();
        public final List<Category> categories = new ArrayList<>();
        public final List<Record> records = new ArrayList<>();
        public final List<Transfer> transfers = new ArrayList<>();
        public final List<Budget> budgets = new ArrayList<>();
        public final List<Recurring> recurrings = new ArrayList<>();
        public final List<Trip> trips = new ArrayList<>();
        public final List<TripRecord> tripRecords = new ArrayList<>();
    }

    public static File exportZip(Context ctx, DbHelper db) throws Exception {
        cleanup(ctx);
        String name = "生活记账本备份_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".zip";
        File out = new File(ShareProvider.shareDir(ctx), name);
        writeZip(ctx, db, out);
        return out;
    }

    /** 自动备份：写入外部存储目录，不依赖分享目录 */
    public static File exportZipAuto(Context ctx, DbHelper db) throws Exception {
        File dir = new File(ctx.getExternalFilesDir(null), "backups");
        if (!dir.exists()) dir.mkdirs();
        String name = "生活记账本_自动备份_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".zip";
        File out = new File(dir, name);
        writeZip(ctx, db, out);
        // 只保留最近 5 份
        File[] files = dir.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < files.length - 5; i++) files[i].delete();
        }
        return out;
    }

    private static void writeZip(Context ctx, DbHelper db, File out) throws Exception {
        ZipOutputStream zos = new ZipOutputStream(new java.io.FileOutputStream(out));
        zos.putNextEntry(new ZipEntry("data.json"));
        zos.write(buildJson(db).getBytes("UTF-8"));
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("恢复说明.txt"));
        zos.write(("【生活记账本 · 全量备份】\n\n"
                + "恢复方法：在新手机安装本应用后，打开「管理」→「导入备份」，选择本 zip 文件即可恢复全部数据（含出游账本）。\n"
                + "说明：data.json 是标准备份数据；ledger.db 是数据库原始副本（供高级用户备用）。\n"
                + "本应用完全离线，数据不会上传到任何服务器。").getBytes("UTF-8"));
        zos.closeEntry();

        db.close();
        try {
            File dbFile = ctx.getDatabasePath(DbHelper.DB_NAME);
            zos.putNextEntry(new ZipEntry("ledger.db"));
            FileInputStream fin = new FileInputStream(dbFile);
            byte[] buf = new byte[8192];
            int n;
            while ((n = fin.read(buf)) > 0) zos.write(buf, 0, n);
            fin.close();
            zos.closeEntry();
        } finally {
            db.getReadableDatabase();
        }
        zos.close();
    }

    /** 清理分享目录里 30 天前的旧文件 */
    private static void cleanup(Context ctx) {
        File dir = ShareProvider.shareDir(ctx);
        File[] files = dir.listFiles();
        if (files == null) return;
        long limit = System.currentTimeMillis() - 30L * 24 * 3600 * 1000;
        for (File f : files) {
            if (f.lastModified() < limit) f.delete();
        }
    }

    private static String buildJson(DbHelper db) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 4);
        root.put("app", "生活记账本");
        root.put("exported_at", LocalDateTime.now().toString());

        JSONArray books = new JSONArray();
        for (Book b : db.books()) {
            JSONObject o = new JSONObject();
            o.put("key", b.key);
            o.put("name", b.name);
            o.put("sort", b.sort);
            o.put("builtin", b.builtin);
            books.put(o);
        }
        root.put("books", books);

        JSONArray cats = new JSONArray();
        for (String type : new String[]{DbHelper.TYPE_INCOME, DbHelper.TYPE_EXPENSE}) {
            for (Category c : db.allCategories(type)) {
                JSONObject o = new JSONObject();
                o.put("id", c.id);
                o.put("type", c.type);
                o.put("parent_id", c.parentId);
                o.put("name", c.name);
                o.put("sort", c.sort);
                cats.put(o);
            }
        }
        root.put("categories", cats);

        JSONArray recs = new JSONArray();
        for (Record r : db.allRecords()) {
            JSONObject o = new JSONObject();
            o.put("type", r.type);
            o.put("amount_cents", r.amountCents);
            o.put("cat1_id", r.cat1Id);
            o.put("cat1_name", r.cat1Name == null ? "" : r.cat1Name);
            o.put("cat2_id", r.cat2Id);
            o.put("cat2_name", r.cat2Name == null ? "" : r.cat2Name);
            o.put("date", r.date);
            o.put("note", r.note == null ? "" : r.note);
            o.put("book", r.book == null ? "main" : r.book);
            o.put("created_at", r.createdAt);
            o.put("updated_at", r.updatedAt);
            recs.put(o);
        }
        root.put("records", recs);

        JSONArray transfers = new JSONArray();
        for (Transfer t : db.allTransfers()) {
            JSONObject o = new JSONObject();
            o.put("from_book", t.fromBook);
            o.put("to_book", t.toBook);
            o.put("amount_cents", t.amountCents);
            o.put("date", t.date);
            o.put("note", t.note == null ? "" : t.note);
            o.put("created_at", t.createdAt);
            o.put("updated_at", t.updatedAt);
            transfers.put(o);
        }
        root.put("transfers", transfers);

        JSONArray budgets = new JSONArray();
        for (Book b : db.books()) {
            for (Budget bd : db.budgets(b.key, null)) {
                JSONObject o = new JSONObject();
                o.put("book", bd.book);
                o.put("type", bd.type);
                o.put("cat1_id", bd.cat1Id);
                o.put("cat1_name", bd.cat1Name == null ? "" : bd.cat1Name);
                o.put("amount_cents", bd.amountCents);
                o.put("created_at", bd.createdAt);
                budgets.put(o);
            }
        }
        root.put("budgets", budgets);

        JSONArray recurrings = new JSONArray();
        for (Recurring r : db.recurrings()) {
            JSONObject o = new JSONObject();
            o.put("type", r.type);
            o.put("amount_cents", r.amountCents);
            o.put("cat1_id", r.cat1Id);
            o.put("cat1_name", r.cat1Name == null ? "" : r.cat1Name);
            o.put("cat2_id", r.cat2Id);
            o.put("cat2_name", r.cat2Name == null ? "" : r.cat2Name);
            o.put("note", r.note == null ? "" : r.note);
            o.put("book", r.book == null ? "main" : r.book);
            o.put("day_of_month", r.dayOfMonth);
            o.put("enabled", r.enabled);
            o.put("last_run", r.lastRun == null ? "" : r.lastRun);
            o.put("created_at", r.createdAt);
            o.put("updated_at", r.updatedAt);
            recurrings.put(o);
        }
        root.put("recurring", recurrings);

        JSONArray trips = new JSONArray();
        for (Trip t : db.trips()) {
            JSONObject o = new JSONObject();
            o.put("id", t.id);
            o.put("name", t.name);
            o.put("start_date", t.startDate);
            o.put("end_date", t.endDate == null ? "" : t.endDate);
            o.put("note", t.note == null ? "" : t.note);
            o.put("finished", t.finished);
            o.put("added_to_main", t.addedToMain);
            o.put("added_book", t.addedBook == null ? "main" : t.addedBook);
            o.put("added_record_id", t.addedRecordId);
            o.put("created_at", t.createdAt);
            o.put("updated_at", t.updatedAt);
            trips.put(o);
        }
        root.put("trips", trips);

        JSONArray triprecs = new JSONArray();
        for (Trip t : db.trips()) {
            for (TripRecord tr : db.tripRecords(t.id)) {
                JSONObject o = new JSONObject();
                o.put("id", tr.id);
                o.put("trip_id", tr.tripId);
                o.put("amount_cents", tr.amountCents);
                o.put("category", tr.category == null ? "" : tr.category);
                o.put("note", tr.note == null ? "" : tr.note);
                o.put("date", tr.date);
                o.put("created_at", tr.createdAt);
                triprecs.put(o);
            }
        }
        root.put("trip_records", triprecs);
        return root.toString(2);
    }

    public static ImportResult parseImport(Context ctx, Uri uri) throws Exception {
        InputStream in = ctx.getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("无法读取所选文件");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        byte[] data = bos.toByteArray();

        String name = guessName(ctx, uri).toLowerCase();
        if (name.endsWith(".json") || looksLikeJson(data)) {
            return parseJson(new String(data, StandardCharsets.UTF_8));
        }
        JSONObject json = null;
        try {
            ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(data));
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if ("data.json".equals(e.getName())) {
                    ByteArrayOutputStream b2 = new ByteArrayOutputStream();
                    byte[] b = new byte[8192];
                    int m;
                    while ((m = zis.read(b)) > 0) b2.write(b, 0, m);
                    json = new JSONObject(b2.toString("UTF-8"));
                    break;
                }
            }
            zis.close();
        } catch (Exception ex) {
            throw new Exception("不是有效的备份文件（zip 已损坏）");
        }
        if (json == null) throw new Exception("压缩包里没有找到 data.json，可能不是本应用的备份文件");
        return parseJson(json.toString());
    }

    private static boolean looksLikeJson(byte[] data) {
        String s = new String(data, 0, Math.min(data.length, 200), StandardCharsets.UTF_8).trim();
        return s.startsWith("{") || s.startsWith("[");
    }

    private static ImportResult parseJson(String text) throws Exception {
        ImportResult res = new ImportResult();
        JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (Exception e) {
            throw new Exception("JSON 解析失败，文件可能已损坏");
        }
        JSONArray bookArr = root.optJSONArray("books");
        if (bookArr != null) {
            for (int i = 0; i < bookArr.length(); i++) {
                JSONObject o = bookArr.optJSONObject(i);
                if (o == null) continue;
                String key = o.optString("key", "");
                if (key.isEmpty()) continue;
                Book b = new Book();
                b.key = key;
                b.name = o.optString("name", key);
                b.sort = o.optInt("sort", i);
                b.builtin = o.optBoolean("builtin", false);
                res.books.add(b);
            }
        }
        JSONArray cats = root.optJSONArray("categories");
        if (cats != null) {
            for (int i = 0; i < cats.length(); i++) {
                JSONObject o = cats.optJSONObject(i);
                if (o == null) continue;
                String type = o.optString("type");
                if (!DbHelper.TYPE_INCOME.equals(type) && !DbHelper.TYPE_EXPENSE.equals(type)) continue;
                Category c = new Category();
                c.id = o.optLong("id", i + 1);
                c.type = type;
                c.parentId = o.optLong("parent_id", 0);
                c.name = o.optString("name", "未命名");
                c.sort = o.optInt("sort", i);
                res.categories.add(c);
            }
        }
        JSONArray recs = root.optJSONArray("records");
        if (recs != null) {
            for (int i = 0; i < recs.length(); i++) {
                JSONObject o = recs.optJSONObject(i);
                if (o == null) continue;
                String type = o.optString("type");
                if (!DbHelper.TYPE_INCOME.equals(type) && !DbHelper.TYPE_EXPENSE.equals(type)) continue;
                long amount = o.optLong("amount_cents", 0);
                if (amount <= 0) continue;
                String date = o.optString("date", "");
                if (!isValidDate(date)) continue;
                Record r = new Record();
                r.type = type;
                r.amountCents = amount;
                r.cat1Id = o.optLong("cat1_id", 0);
                r.cat1Name = o.optString("cat1_name", "");
                r.cat2Id = o.optLong("cat2_id", 0);
                r.cat2Name = o.optString("cat2_name", "");
                r.date = date;
                r.note = o.optString("note", "");
                String book = o.optString("book", DbHelper.BOOK_MAIN);
                if (book == null || book.isEmpty()) book = DbHelper.BOOK_MAIN;
                r.book = book;
                r.createdAt = o.optLong("created_at", 0);
                r.updatedAt = o.optLong("updated_at", 0);
                res.records.add(r);
            }
        }
        JSONArray transferArr = root.optJSONArray("transfers");
        if (transferArr != null) {
            for (int i = 0; i < transferArr.length(); i++) {
                JSONObject o = transferArr.optJSONObject(i);
                if (o == null) continue;
                long amount = o.optLong("amount_cents", 0);
                if (amount <= 0) continue;
                String date = o.optString("date", "");
                if (!isValidDate(date)) continue;
                Transfer tr = new Transfer();
                tr.fromBook = o.optString("from_book", DbHelper.BOOK_MAIN);
                tr.toBook = o.optString("to_book", DbHelper.BOOK_VAULT);
                tr.amountCents = amount;
                tr.date = date;
                tr.note = o.optString("note", "");
                tr.createdAt = o.optLong("created_at", 0);
                tr.updatedAt = o.optLong("updated_at", 0);
                res.transfers.add(tr);
            }
        }
        JSONArray budgetArr = root.optJSONArray("budgets");
        if (budgetArr != null) {
            for (int i = 0; i < budgetArr.length(); i++) {
                JSONObject o = budgetArr.optJSONObject(i);
                if (o == null) continue;
                long amount = o.optLong("amount_cents", 0);
                if (amount <= 0) continue;
                String type = o.optString("type");
                if (!DbHelper.TYPE_INCOME.equals(type) && !DbHelper.TYPE_EXPENSE.equals(type)) continue;
                Budget bd = new Budget();
                bd.book = o.optString("book", DbHelper.BOOK_MAIN);
                bd.type = type;
                bd.cat1Id = o.optLong("cat1_id", 0);
                bd.cat1Name = o.optString("cat1_name", "");
                bd.amountCents = amount;
                bd.createdAt = o.optLong("created_at", 0);
                res.budgets.add(bd);
            }
        }
        JSONArray recArr = root.optJSONArray("recurring");
        if (recArr != null) {
            for (int i = 0; i < recArr.length(); i++) {
                JSONObject o = recArr.optJSONObject(i);
                if (o == null) continue;
                long amount = o.optLong("amount_cents", 0);
                if (amount <= 0) continue;
                String type = o.optString("type");
                if (!DbHelper.TYPE_INCOME.equals(type) && !DbHelper.TYPE_EXPENSE.equals(type)) continue;
                Recurring r = new Recurring();
                r.type = type;
                r.amountCents = amount;
                r.cat1Id = o.optLong("cat1_id", 0);
                r.cat1Name = o.optString("cat1_name", "");
                r.cat2Id = o.optLong("cat2_id", 0);
                r.cat2Name = o.optString("cat2_name", "");
                r.note = o.optString("note", "");
                r.book = o.optString("book", DbHelper.BOOK_MAIN);
                r.dayOfMonth = o.optInt("day_of_month", 1);
                r.enabled = o.optBoolean("enabled", true);
                r.lastRun = o.optString("last_run", "");
                r.createdAt = o.optLong("created_at", 0);
                r.updatedAt = o.optLong("updated_at", 0);
                res.recurrings.add(r);
            }
        }
        JSONArray trips = root.optJSONArray("trips");
        if (trips != null) {
            for (int i = 0; i < trips.length(); i++) {
                JSONObject o = trips.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "");
                if (name.isEmpty()) continue;
                String sd = o.optString("start_date", "");
                if (!isValidDate(sd)) continue;
                Trip t = new Trip();
                t.id = o.optLong("id", i + 1);
                t.name = name;
                t.startDate = sd;
                t.endDate = o.optString("end_date", "");
                t.note = o.optString("note", "");
                t.finished = o.optBoolean("finished", false);
                t.addedToMain = o.optBoolean("added_to_main", false);
                String ab = o.optString("added_book", DbHelper.BOOK_MAIN);
                if (ab == null || ab.isEmpty()) ab = DbHelper.BOOK_MAIN;
                t.addedBook = ab;
                t.addedRecordId = o.optLong("added_record_id", 0);
                t.createdAt = o.optLong("created_at", 0);
                t.updatedAt = o.optLong("updated_at", 0);
                res.trips.add(t);
            }
        }
        JSONArray triprecs = root.optJSONArray("trip_records");
        if (triprecs != null) {
            for (int i = 0; i < triprecs.length(); i++) {
                JSONObject o = triprecs.optJSONObject(i);
                if (o == null) continue;
                long amount = o.optLong("amount_cents", 0);
                if (amount <= 0) continue;
                String date = o.optString("date", "");
                if (!isValidDate(date)) continue;
                TripRecord tr = new TripRecord();
                tr.id = o.optLong("id", i + 1);
                tr.tripId = o.optLong("trip_id", 0);
                tr.amountCents = amount;
                tr.category = o.optString("category", "");
                tr.note = o.optString("note", "");
                tr.date = date;
                tr.createdAt = o.optLong("created_at", 0);
                res.tripRecords.add(tr);
            }
        }
        if (res.categories.isEmpty() && res.records.isEmpty() && res.trips.isEmpty()) {
            throw new Exception("备份文件里没有可导入的数据");
        }
        return res;
    }

    private static boolean isValidDate(String d) {
        if (d == null || d.length() != 10) return false;
        if (d.charAt(4) != '-' || d.charAt(7) != '-') return false;
        for (int i = 0; i < 10; i++) {
            if (i == 4 || i == 7) continue;
            char c = d.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static String guessName(Context ctx, Uri uri) {
        String name = null;
        try {
            Cursor c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                if (c.moveToFirst()) name = c.getString(0);
                c.close();
            }
        } catch (Exception ignored) {
        }
        return name == null ? "backup.zip" : name;
    }
}
