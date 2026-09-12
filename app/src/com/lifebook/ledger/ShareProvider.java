package com.lifebook.ledger;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** 简单的文件提供者：把 files/share 目录下的文件分享给其他应用 */
public class ShareProvider extends ContentProvider {

    public static final String AUTHORITY = "com.lifebook.ledger.share";

    @Override
    public boolean onCreate() {
        return true;
    }

    public static File shareDir(Context c) {
        File d = new File(c.getFilesDir(), "share");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static Uri uriForFile(Context c, File f) {
        return Uri.parse("content://" + AUTHORITY + "/" + Uri.encode(f.getName()));
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        File dir = shareDir(getContext());
        String name = Uri.decode(uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment());
        File f = new File(dir, name);
        try {
            if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath())) throw new FileNotFoundException("非法路径");
        } catch (Exception e) {
            throw new FileNotFoundException("非法路径");
        }
        if (!f.exists()) throw new FileNotFoundException("文件不存在");
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("只支持读取");
        File f = fileFor(uri);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment().toLowerCase();
        if (name.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (name.endsWith(".zip")) return "application/zip";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f;
        try {
            f = fileFor(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        if (projection == null || projection.length == 0) {
            projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        }
        MatrixCursor cursor = new MatrixCursor(projection, 1);
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(projection[i])) row[i] = f.length();
            else row[i] = null;
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
