package com.lifebook.ledger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Book;
import com.lifebook.ledger.model.Record;
import com.lifebook.ledger.page.AnalysisPage;
import com.lifebook.ledger.page.HomePage;
import com.lifebook.ledger.page.ListPage;
import com.lifebook.ledger.page.SettingsPage;
import com.lifebook.ledger.page.TravelPage;
import com.lifebook.ledger.ui.AddRecordDialog;
import com.lifebook.ledger.util.BackupManager;
import com.lifebook.ledger.util.Async;
import com.lifebook.ledger.util.LockUtil;
import com.lifebook.ledger.util.NotifyManager;
import com.lifebook.ledger.util.U;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class MainActivity extends Activity {

    public static final int REQ_PICK_BACKUP = 101;
    public static final int REQ_SAVE_EXCEL = 102;
    public static final int REQ_SAVE_ZIP = 103;
    public static final int REQ_NOTIF_PERM = 501;

    public DbHelper db;
    HomePage home;
    ListPage listPage;
    TravelPage travel;
    AnalysisPage analysis;
    SettingsPage settings;

    private View lockOverlay;
    private boolean promptShowing;
    private boolean unlocked;
    private boolean suppressAutoPrompt;

    private static final int[] TAB_IDS = {R.id.tab_home, R.id.tab_list, R.id.tab_travel, R.id.tab_analysis, R.id.tab_settings};
    private static final int[] PAGE_IDS = {R.id.page_home, R.id.page_list, R.id.page_travel, R.id.page_analysis, R.id.page_settings};
    private static final int[] ICON_IDS = {R.id.icon_home, R.id.icon_list, R.id.icon_travel, R.id.icon_analysis, R.id.icon_settings};
    private static final int[] LABEL_IDS = {R.id.label_home, R.id.label_list, R.id.label_travel, R.id.label_analysis, R.id.label_settings};

    private int currentTab = 0;
    private File pendingSaveFile;
    private String currentBook = DbHelper.BOOK_MAIN;

    public String currentBook() {
        return currentBook;
    }

    /** 显式校验：当前账本不存在（如刚被删除）时回退到主账本 */
    public void ensureCurrentBook() {
        if (db == null || db.bookByKey(currentBook) == null) {
            currentBook = DbHelper.BOOK_MAIN;
        }
    }

    public void setCurrentBook(String book) {
        currentBook = book;
    }

    /** 生成账本选择器里的一块标签 */
    public TextView makeBookChip(String name, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(name);
        chip.setTextSize(13);
        chip.setGravity(Gravity.CENTER);
        chip.setMaxLines(1);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setMaxWidth(U.dp(this, 200));
        chip.setPadding(U.dp(this, 12), U.dp(this, 7), U.dp(this, 12), U.dp(this, 7));
        if (selected) {
            chip.setBackgroundResource(R.drawable.bg_seg_item);
            chip.setTextColor(getResources().getColor(R.color.primary));
            chip.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            chip.setBackgroundColor(Color.TRANSPARENT);
            chip.setTextColor(getResources().getColor(R.color.chip_text));
            chip.setTypeface(Typeface.DEFAULT);
        }
        return chip;
    }

    /** 渲染首页 / 明细 / 分析共用的一排账本标签，末尾带一个「＋ 账本」入口 */
    public void renderBookSelector(LinearLayout box, Runnable onChanged) {
        box.removeAllViews();
        ensureCurrentBook();
        String cur = currentBook();
        for (Book b : db.books()) {
            boolean sel = b.key.equals(cur);
            TextView chip = makeBookChip(b.name, sel);
            chip.setOnClickListener(v -> {
                if (!b.key.equals(currentBook())) {
                    setCurrentBook(b.key);
                    if (onChanged != null) onChanged.run();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(U.dp(this, 3));
            box.addView(chip, lp);
        }
        TextView add = makeBookChip("＋ 账本", false);
        add.setTextColor(getResources().getColor(R.color.primary));
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setOnClickListener(v -> promptAddBook(onChanged));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(U.dp(this, 3));
        box.addView(add, lp);
    }

    public void promptAddBook(final Runnable onChanged) {
        FrameLayout frame = new FrameLayout(this);
        final EditText input = new EditText(this);
        input.setHint("账本名称");
        input.setTextColor(getResources().getColor(R.color.text_main));
        input.setHintTextColor(getResources().getColor(R.color.text_sub));
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_input);
        frame.setPadding(U.dp(this, 20), U.dp(this, 8), U.dp(this, 20), 0);
        frame.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("新建账本")
                .setView(frame)
                .setPositiveButton("创建", (d, w) -> {
                    String n = input.getText().toString().trim();
                    if (n.isEmpty()) {
                        U.toast(this, "名称不能为空");
                        return;
                    }
                    Book b = db.addBook(n);
                    setCurrentBook(b.key);
                    U.toast(this, "账本「" + b.name + "」已创建");
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DbHelper(this);
        db.seedIfEmpty();
        db.runRecurring(U.currentMonth());
        new Handler(Looper.getMainLooper()).postDelayed(this::maybeAutoBackup, 2500);
        setContentView(R.layout.activity_main);

        home = new HomePage(this);
        listPage = new ListPage(this);
        travel = new TravelPage(this);
        analysis = new AnalysisPage(this);
        settings = new SettingsPage(this);

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            findViewById(TAB_IDS[i]).setOnClickListener(v -> selectTab(idx));
        }
        selectTab(0);

        NotifyManager.ensureChannel(this);
        NotifyManager.scheduleAll(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF_PERM);
        }
    }

    /** 每周自动备份到应用外部目录，最多保留 5 份（全离线，不上传） */
    private void maybeAutoBackup() {
        final SharedPreferences sp = getSharedPreferences("lifebook_prefs", MODE_PRIVATE);
        long last = sp.getLong("last_auto_backup", 0);
        long now = System.currentTimeMillis();
        if (now - last < 7L * 24 * 3600 * 1000) return;
        Async.run(() -> {
            try {
                BackupManager.exportZipAuto(this, db);
            } catch (Exception ignored) {
            }
            return true;
        }, ok -> {
            sp.edit().putLong("last_auto_backup", now).apply();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (home != null) selectTab(currentTab);
        handleFingerprintLock();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 切到后台即重新上锁，回到前台再验证；若正在弹出指纹框则不打断
        if (lockEnabled() && !promptShowing) {
            unlocked = false;
        }
    }

    private boolean lockEnabled() {
        return LockUtil.enabled(this);
    }

    private void handleFingerprintLock() {
        if (!lockEnabled()) return;
        if (unlocked) return;
        if (promptShowing) return;
        // 指纹条件不满足（无锁屏密码 / 无指纹硬件 / 未录入指纹）时自动关闭，避免把用户锁在外面
        if (!LockUtil.canUse(this)) {
            LockUtil.setEnabled(this, false);
            U.toast(this, "手机指纹不可用，已自动关闭指纹解锁");
            unlocked = true;
            return;
        }
        showLockOverlay();
        if (suppressAutoPrompt) return;
        promptFingerprint();
    }

    private void promptFingerprint() {
        promptShowing = true;
        LockUtil.prompt(this, "解锁生活记账本", "请验证指纹后进入应用", new LockUtil.Result() {
            @Override
            public void onSuccess() {
                promptShowing = false;
                unlocked = true;
                suppressAutoPrompt = false;
                hideLockOverlay();
                U.toast(MainActivity.this, "指纹验证通过");
            }

            @Override
            public void onError(int code, CharSequence msg) {
                promptShowing = false;
                if (code == LockUtil.ERROR_USER_CANCELED
                        || code == LockUtil.ERROR_CANCELED
                        || code == LockUtil.ERROR_NEGATIVE_BUTTON) {
                    U.toast(MainActivity.this, "未验证，请重新触摸指纹");
                    suppressAutoPrompt = true;
                    // 保持锁定，点按覆盖层可重试
                } else if (code == LockUtil.ERROR_LOCKOUT) {
                    U.toast(MainActivity.this, "尝试次数过多，请稍后再试");
                    suppressAutoPrompt = true;
                    // 保持锁定，稍后可重试
                } else {
                    LockUtil.setEnabled(MainActivity.this, false);
                    unlocked = true;
                    suppressAutoPrompt = false;
                    hideLockOverlay();
                    U.toast(MainActivity.this, "指纹不可用 (" + msg + ")，已自动关闭");
                }
            }

            @Override
            public void onUnavailable() {
                promptShowing = false;
                LockUtil.setEnabled(MainActivity.this, false);
                unlocked = true;
                suppressAutoPrompt = false;
                hideLockOverlay();
                U.toast(MainActivity.this, "无法使用指纹，已自动关闭");
            }
        });
    }

    private void showLockOverlay() {
        if (lockOverlay != null) return;
        FrameLayout content = findViewById(android.R.id.content);
        FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(getColor(R.color.page_bg));
        ov.setClickable(true);
        ov.setFocusable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(U.dp(this, 32), U.dp(this, 32), U.dp(this, 32), U.dp(this, 32));

        TextView icon = new TextView(this);
        icon.setText("🔒");
        icon.setTextSize(64);
        icon.setGravity(Gravity.CENTER);
        col.addView(icon, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("生活记账本");
        title.setTextSize(20);
        title.setTextColor(getColor(R.color.text_main));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = U.dp(this, 18);
        col.addView(title, tlp);

        TextView msg = new TextView(this);
        msg.setText("请验证指纹以进入应用");
        msg.setTextSize(14);
        msg.setTextColor(getColor(R.color.text_sub));
        msg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = U.dp(this, 10);
        col.addView(msg, mlp);

        TextView hint = new TextView(this);
        hint.setText("未验证前无法进入；指纹暂时不可用时，请点按任意处重试。");
        hint.setTextSize(12);
        hint.setTextColor(getColor(R.color.icon_gray));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(U.dp(this, 8), U.dp(this, 8), U.dp(this, 8), U.dp(this, 8));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = U.dp(this, 22);
        col.addView(hint, hlp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        ov.addView(col, lp);

        ov.setOnClickListener(v -> {
            if (!promptShowing && lockEnabled() && !unlocked) {
                suppressAutoPrompt = false;
                promptFingerprint();
            }
        });

        FrameLayout.LayoutParams olp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        content.addView(ov, olp);
        lockOverlay = ov;
    }

    private void hideLockOverlay() {
        if (lockOverlay == null) return;
        ViewGroup parent = (ViewGroup) lockOverlay.getParent();
        if (parent != null) parent.removeView(lockOverlay);
        lockOverlay = null;
    }

    public void selectTab(int idx) {
        currentTab = idx;
        for (int i = 0; i < 5; i++) {
            findViewById(PAGE_IDS[i]).setVisibility(i == idx ? View.VISIBLE : View.GONE);
            LinearLayout tab = findViewById(TAB_IDS[i]);
            ImageView icon = findViewById(ICON_IDS[i]);
            TextView label = findViewById(LABEL_IDS[i]);
            if (i == idx) {
                icon.setColorFilter(getColor(R.color.primary));
                label.setTextColor(getColor(R.color.primary));
                label.setTypeface(Typeface.DEFAULT_BOLD);
                tab.setBackgroundResource(R.drawable.bg_tab_sel);
            } else {
                icon.setColorFilter(getColor(R.color.icon_gray));
                label.setTextColor(getColor(R.color.icon_gray));
                label.setTypeface(Typeface.DEFAULT);
                tab.setBackgroundColor(Color.TRANSPARENT);
            }
        }
        if (idx == 0) home.show();
        else if (idx == 1) listPage.show();
        else if (idx == 2) travel.show();
        else if (idx == 3) analysis.show();
        else settings.show();
    }

    public void refreshData() {
        home.show();
        listPage.show();
        travel.show();
        analysis.show();
    }

    // ===== 记账 =====
    public void openAddRecord(String defaultType) {
        ensureCurrentBook();
        AddRecordDialog.show(this, db, null, defaultType, currentBook(), this::refreshData);
    }

    public void openEditRecord(Record r) {
        ensureCurrentBook();
        AddRecordDialog.show(this, db, r, null, r.book == null ? currentBook() : r.book, this::refreshData);
    }

    public void confirmDeleteRecord(Record r) {
        new AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("确定删除这笔记录吗？（可到「管理 → 回收站」恢复）\n"
                        + r.title() + "  " + (DbHelper.TYPE_INCOME.equals(r.type) ? "+" : "-") + U.money(r.amountCents) + " 元")
                .setPositiveButton("删除", (d, w) -> {
                    db.deleteRecord(r.id);
                    U.toast(this, "已删除（可在回收站恢复）");
                    refreshData();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ===== 导入 =====
    public void pickBackup() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        try {
            startActivityForResult(it, REQ_PICK_BACKUP);
        } catch (Exception e) {
            U.toast(this, "无法打开文件选择器：" + e.getMessage());
        }
    }

    // ===== 导出结果（分享 / 保存） =====
    public void showExportResult(File f, String mime) {
        new AlertDialog.Builder(this)
                .setTitle("导出成功")
                .setMessage("文件已生成：\n" + f.getName())
                .setPositiveButton("发送 / 分享", (d, w) -> shareFile(f, mime))
                .setNeutralButton("保存到手机", (d, w) -> saveFileTo(f, mime))
                .setNegativeButton("完成", null)
                .show();
    }

    private void shareFile(File f, String mime) {
        Uri u = ShareProvider.uriForFile(this, f);
        Intent it = new Intent(Intent.ACTION_SEND);
        it.setType(mime);
        it.putExtra(Intent.EXTRA_STREAM, u);
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(it, "发送文件（微信 / QQ / 文件管理等）"));
        } catch (Exception e) {
            U.toast(this, "没有可用的发送方式：" + e.getMessage());
        }
    }

    private void saveFileTo(File f, String mime) {
        pendingSaveFile = f;
        Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType(mime);
        it.putExtra(Intent.EXTRA_TITLE, f.getName());
        try {
            startActivityForResult(it, "application/zip".equals(mime) ? REQ_SAVE_ZIP : REQ_SAVE_EXCEL);
        } catch (Exception e) {
            U.toast(this, "无法打开保存界面：" + e.getMessage());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_PICK_BACKUP) {
            handleBackupImport(data.getData());
        } else if (requestCode == REQ_SAVE_EXCEL || requestCode == REQ_SAVE_ZIP) {
            handleSaveCopy(data.getData());
        }
    }

    private void handleSaveCopy(Uri target) {
        if (pendingSaveFile == null) return;
        try {
            InputStream in = new FileInputStream(pendingSaveFile);
            OutputStream out = getContentResolver().openOutputStream(target);
            if (out == null) throw new Exception("无法写入目标位置");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            U.toast(this, "已保存到所选位置");
        } catch (Exception e) {
            U.toast(this, "保存失败：" + e.getMessage());
        }
    }

    private void handleBackupImport(Uri uri) {
        try {
            BackupManager.ImportResult r = BackupManager.parseImport(this, uri);
            String detail = r.categories.size() + " 个分类、" + r.records.size() + " 条记录、" + r.trips.size()
                    + " 次出游、" + r.transfers.size() + " 笔转账、"
                    + r.budgets.size() + " 项预算、" + r.recurrings.size() + " 条周期记账";
            new AlertDialog.Builder(this)
                    .setTitle("确认导入")
                    .setMessage("备份包含 " + detail + "。\n\n「覆盖导入」会清空本机数据后替换；「合并导入」只新增备份里有、本机还没有的账目，不会删掉现有数据。")
                    .setPositiveButton("覆盖导入", (d, w) -> {
                        db.replaceAll(r.books, r.categories, r.records, r.trips, r.tripRecords, r.transfers, r.budgets, r.recurrings);
                        db.seedIfEmpty();
                        U.toast(this, "覆盖导入成功");
                        refreshData();
                    })
                    .setNeutralButton("合并导入", (d, w) -> {
                        db.mergeImport(r.books, r.records, r.trips, r.tripRecords, r.transfers, r.budgets, r.recurrings);
                        U.toast(this, "合并导入成功");
                        refreshData();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            U.toast(this, "导入失败：" + e.getMessage());
        }
    }
}
