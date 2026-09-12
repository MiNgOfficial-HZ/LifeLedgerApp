package com.lifebook.ledger.util;

import android.content.Context;

import com.lifebook.ledger.ShareProvider;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Book;
import com.lifebook.ledger.model.Record;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 无第三方依赖的最小 xlsx 生成器（含全部账本） */
public class ExcelExporter {

    public static File export(Context ctx, DbHelper db, String month) throws Exception {
        List<Record> exp = new ArrayList<>(db.records(month, DbHelper.TYPE_EXPENSE, null));
        List<Record> inc = new ArrayList<>(db.records(month, DbHelper.TYPE_INCOME, null));
        exp.sort(Comparator.comparing((Record r) -> r.date).thenComparingLong(r -> r.id));
        inc.sort(Comparator.comparing((Record r) -> r.date).thenComparingLong(r -> r.id));

        File out = new File(ShareProvider.shareDir(ctx), "生活记账_" + month + ".xlsx");
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out));
        put(zos, "[Content_Types].xml", contentTypes());
        put(zos, "_rels/.rels", rootRels());
        put(zos, "xl/workbook.xml", workbook());
        put(zos, "xl/_rels/workbook.xml.rels", workbookRels());
        put(zos, "xl/styles.xml", styles());
        put(zos, "xl/worksheets/sheet1.xml", detailSheet(exp, db));
        put(zos, "xl/worksheets/sheet2.xml", detailSheet(inc, db));
        put(zos, "xl/worksheets/sheet3.xml", summarySheet(db, month));
        put(zos, "docProps/core.xml", coreProps(month));
        put(zos, "docProps/app.xml", appProps());
        zos.close();
        return out;
    }

    private static void put(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes("UTF-8"));
        zos.closeEntry();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String bookLabel(Record r, DbHelper db) {
        return db.bookName(r.book);
    }

    private static String detailSheet(List<Record> list, DbHelper db) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sb.append("<cols>");
        sb.append("<col min=\"1\" max=\"1\" width=\"13\" customWidth=\"1\"/>");
        sb.append("<col min=\"2\" max=\"2\" width=\"10\" customWidth=\"1\"/>");
        sb.append("<col min=\"3\" max=\"3\" width=\"16\" customWidth=\"1\"/>");
        sb.append("<col min=\"4\" max=\"4\" width=\"16\" customWidth=\"1\"/>");
        sb.append("<col min=\"5\" max=\"5\" width=\"13\" customWidth=\"1\"/>");
        sb.append("<col min=\"6\" max=\"6\" width=\"46\" customWidth=\"1\"/>");
        sb.append("</cols><sheetData>");
        sb.append("<row r=\"1\">");
        sb.append(cellStr("A1", "日期", 1));
        sb.append(cellStr("B1", "账本", 1));
        sb.append(cellStr("C1", "一级分类", 1));
        sb.append(cellStr("D1", "二级分类", 1));
        sb.append(cellStr("E1", "金额(元)", 1));
        sb.append(cellStr("F1", "备注", 1));
        sb.append("</row>");
        long total = 0;
        int r = 2;
        for (Record rec : list) {
            sb.append("<row r=\"" + r + "\">");
            sb.append(cellStr("A" + r, rec.date == null ? "" : rec.date, 0));
            sb.append(cellStr("B" + r, bookLabel(rec, db), 0));
            sb.append(cellStr("C" + r, rec.cat1Name == null || rec.cat1Name.isEmpty() ? "未分类" : rec.cat1Name, 0));
            sb.append(cellStr("D" + r, rec.cat2Name == null ? "" : rec.cat2Name, 0));
            sb.append(cellNum("E" + r, U.money(rec.amountCents), 2));
            sb.append(cellStr("F" + r, rec.note, 0));
            sb.append("</row>");
            total += rec.amountCents;
            r++;
        }
        if (list.isEmpty()) {
            sb.append("<row r=\"" + r + "\">");
            sb.append(cellStr("A" + r, "本月暂无记录", 0));
            sb.append("</row>");
            r++;
        }
        sb.append("<row r=\"" + r + "\">");
        sb.append(cellStr("A" + r, "共 " + list.size() + " 笔", 3));
        sb.append(cellStr("B" + r, "", 3));
        sb.append(cellStr("C" + r, "", 3));
        sb.append(cellStr("D" + r, "合计", 3));
        sb.append(cellNum("E" + r, U.money(total), 3));
        sb.append(cellStr("F" + r, "", 3));
        sb.append("</row>");
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static String summarySheet(DbHelper db, String month) throws Exception {
        List<Book> books = db.books();
        long[] tall = db.monthTotals(month, null);
        int allInc = db.recordCount(month, DbHelper.TYPE_INCOME, null);
        int allExp = db.recordCount(month, DbHelper.TYPE_EXPENSE, null);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sb.append("<cols>");
        sb.append("<col min=\"1\" max=\"1\" width=\"22\" customWidth=\"1\"/>");
        sb.append("<col min=\"2\" max=\"2\" width=\"14\" customWidth=\"1\"/>");
        sb.append("<col min=\"3\" max=\"3\" width=\"22\" customWidth=\"1\"/>");
        sb.append("<col min=\"4\" max=\"4\" width=\"14\" customWidth=\"1\"/>");
        sb.append("</cols><sheetData>");
        int r = 1;
        sb.append("<row r=\"1\">" + cellStr("A1", U.monthLabel(month) + " 月度汇总", 3) + "</row>");
        r += 2;
        for (Book b : books) {
            long[] t = db.monthTotals(month, b.key);
            int inc = db.recordCount(month, DbHelper.TYPE_INCOME, b.key);
            int exp = db.recordCount(month, DbHelper.TYPE_EXPENSE, b.key);
            sb.append(kvMoney(r, b.name + " · 总收入", U.money(t[0])));
            r++;
            sb.append(kvMoney(r, b.name + " · 总支出", U.money(t[1])));
            r++;
            sb.append(kvMoney(r, b.name + " · 结余", (t[0] - t[1] < 0 ? "-" : "") + U.money(Math.abs(t[0] - t[1]))));
            r++;
            sb.append(kvText(r, b.name + " · 收/支笔数", inc + " 笔 / " + exp + " 笔"));
            r += 2;
        }
        sb.append(kvMoney(r, "全部账本 · 总收入", U.money(tall[0])));
        r++;
        sb.append(kvMoney(r, "全部账本 · 总支出", U.money(tall[1])));
        r++;
        sb.append(kvMoney(r, "全部账本 · 总结余", (tall[0] - tall[1] < 0 ? "-" : "") + U.money(Math.abs(tall[0] - tall[1]))));
        r++;
        sb.append(kvText(r, "全部账本 · 收/支笔数", allInc + " 笔 / " + allExp + " 笔"));
        r += 2;
        for (Book b : books) {
            Map<String, Long> expCats = db.sumByCat(month, DbHelper.TYPE_EXPENSE, 1, b.key);
            Map<String, Long> incCats = db.sumByCat(month, DbHelper.TYPE_INCOME, 1, b.key);
            r = appendCatSection(sb, r, "支出分类（" + b.name + "）", expCats, "收入分类（" + b.name + "）", incCats);
            r++;
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static int appendCatSection(StringBuilder sb, int r, String leftTitle, Map<String, Long> left,
                                        String rightTitle, Map<String, Long> right) {
        sb.append("<row r=\"" + r + "\">");
        sb.append(cellStr("A" + r, leftTitle, 1));
        sb.append(cellStr("C" + r, rightTitle, 1));
        sb.append("</row>");
        r++;
        sb.append("<row r=\"" + r + "\">");
        sb.append(cellStr("A" + r, "分类", 1));
        sb.append(cellStr("B" + r, "金额", 1));
        sb.append(cellStr("C" + r, "分类", 1));
        sb.append(cellStr("D" + r, "金额", 1));
        sb.append("</row>");
        r++;
        List<Map.Entry<String, Long>> e1 = new ArrayList<>(left.entrySet());
        List<Map.Entry<String, Long>> e2 = new ArrayList<>(right.entrySet());
        int max = Math.max(e1.size(), e2.size());
        for (int i = 0; i < max; i++) {
            sb.append("<row r=\"" + r + "\">");
            if (i < e1.size()) {
                sb.append(cellStr("A" + r, e1.get(i).getKey(), 0));
                sb.append(cellNum("B" + r, U.money(e1.get(i).getValue()), 2));
            }
            if (i < e2.size()) {
                sb.append(cellStr("C" + r, e2.get(i).getKey(), 0));
                sb.append(cellNum("D" + r, U.money(e2.get(i).getValue()), 2));
            }
            sb.append("</row>");
            r++;
        }
        return r;
    }

    private static String kvMoney(int r, String label, String value) {
        return "<row r=\"" + r + "\">" + cellStr("A" + r, label, 0) + cellNum("B" + r, value, 2) + "</row>";
    }

    private static String kvText(int r, String label, String value) {
        return "<row r=\"" + r + "\">" + cellStr("A" + r, label, 0) + cellStr("B" + r, value, 0) + "</row>";
    }

    private static String cellStr(String ref, String v, int style) {
        return "<c r=\"" + ref + "\" t=\"inlineStr\"" + (style > 0 ? " s=\"" + style + "\"" : "")
                + "><is><t xml:space=\"preserve\">" + esc(v) + "</t></is></c>";
    }

    private static String cellNum(String ref, String v, int style) {
        return "<c r=\"" + ref + "\"" + (style > 0 ? " s=\"" + style + "\"" : "") + "><v>" + v + "</v></c>";
    }

    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
                + "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>"
                + "</Types>";
    }

    private static String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>"
                + "</Relationships>";
    }

    private static String workbook() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets>"
                + "<sheet name=\"支出明细\" sheetId=\"1\" r:id=\"rId1\"/>"
                + "<sheet name=\"收入明细\" sheetId=\"2\" r:id=\"rId2\"/>"
                + "<sheet name=\"月度汇总\" sheetId=\"3\" r:id=\"rId3\"/>"
                + "</sheets></workbook>";
    }

    private static String workbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/>"
                + "<Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\">"
                + "<font><sz val=\"11\"/><name val=\"Microsoft YaHei\"/></font>"
                + "<font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Microsoft YaHei\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF0066CC\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"4\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                + "<xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "<xf numFmtId=\"2\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\"/>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private static String coreProps(String month) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
                + "<dc:title>生活记账 " + esc(month) + "</dc:title>"
                + "<dc:creator>生活记账本</dc:creator>"
                + "<cp:lastModifiedBy>生活记账本</cp:lastModifiedBy>"
                + "</cp:coreProperties>";
    }

    private static String appProps() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">"
                + "<Application>生活记账本</Application></Properties>";
    }
}
