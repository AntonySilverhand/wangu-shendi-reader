package org.wanshu.reader.ui.settings;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.content.StorageStats;
import org.wanshu.reader.data.personal.entity.SettingsEntity;
import org.wanshu.reader.data.repository.ContentRepository;
import org.wanshu.reader.data.repository.PersonalRepository;
import org.wanshu.reader.ui.theme.ReaderThemeConfig;
import org.wanshu.reader.ui.theme.ThemeColors;

public class SettingsDialog extends Dialog {
    private final String bookId;
    private final String currentChapterId;
    private final PersonalRepository personalRepository;
    private final ContentRepository contentRepository;
    private final org.wanshu.reader.download.DownloadCoordinator coordinator;
    private final AppExecutors executors;
    private final SettingsChangeListener changeListener;
    private final ThemeColors colors;

    private SettingsEntity currentSettings = new SettingsEntity();
    private boolean isUpdatingUI = false;

    private TextView fontSizeDisplay;
    private TextView lineHeightDisplay;
    private TextView marginDisplay;
    private CheckBox keepAwakeCheckBox;
    private CheckBox immersiveCheckBox;
    private CheckBox prefetchCheckBox;
    private CheckBox autoCacheCheckBox;
    private CheckBox autoCacheMeteredCheckBox;
    private Button scopeBtnAll;
    private Button scopeBtn50;
    private Button scopeBtn200;
    private TextView storageStatsDisplay;

    public SettingsDialog(
            Context context,
            String bookId,
            String currentChapterId,
            PersonalRepository personalRepository,
            ContentRepository contentRepository,
            org.wanshu.reader.download.DownloadCoordinator coordinator,
            AppExecutors executors,
            SettingsChangeListener changeListener,
            ThemeColors colors
    ) {
        super(context);
        this.bookId = bookId != null ? bookId : "";
        this.currentChapterId = currentChapterId != null ? currentChapterId : "";
        this.personalRepository = personalRepository;
        this.contentRepository = contentRepository;
        this.coordinator = coordinator;
        this.executors = executors;
        this.changeListener = changeListener;
        this.colors = colors;
    }

    public SettingsDialog(
            Context context,
            String bookId,
            PersonalRepository personalRepository,
            ContentRepository contentRepository,
            AppExecutors executors,
            SettingsChangeListener changeListener,
            ThemeColors colors
    ) {
        this(context, bookId, "", personalRepository, contentRepository, null, executors, changeListener, colors);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Context ctx = getContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int bg = colors != null ? colors.background : Color.WHITE;
        int barBg = colors != null ? colors.barBackground : Color.parseColor("#F5F5F5");
        int textCol = colors != null ? colors.text : Color.BLACK;
        int secTextCol = colors != null ? colors.secondaryText : Color.GRAY;

        root.setBackgroundColor(bg);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 1. Header Bar
        LinearLayout headerBar = new LinearLayout(ctx);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding(24, 16, 16, 16);
        headerBar.setBackgroundColor(barBg);

        TextView titleView = new TextView(ctx);
        titleView.setText("设置与缓存");
        titleView.setTextSize(18f);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(textCol);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        headerBar.addView(titleView, titleParams);

        Button closeBtn = new Button(ctx);
        closeBtn.setText("✕");
        closeBtn.setOnClickListener(new SettingsCloseClickListener(this));
        headerBar.addView(closeBtn);
        root.addView(headerBar);

        // 2. Scrollable Body
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(32, 24, 32, 32);

        // Section A: 主题风格
        TextView sectionTheme = makeSectionHeader(ctx, "阅读主题", textCol);
        body.addView(sectionTheme);

        LinearLayout themeRow = new LinearLayout(ctx);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setPadding(0, 8, 0, 24);

        addThemeButton(ctx, themeRow, "浅色", ReaderThemeConfig.THEME_LIGHT, Color.parseColor("#FAFAFA"), Color.parseColor("#1F2937"));
        addThemeButton(ctx, themeRow, "暗色", ReaderThemeConfig.THEME_DARK, Color.parseColor("#1E293B"), Color.parseColor("#F1F5F9"));
        addThemeButton(ctx, themeRow, "纯黑", ReaderThemeConfig.THEME_BLACK, Color.parseColor("#000000"), Color.parseColor("#E2E8F0"));
        addThemeButton(ctx, themeRow, "水墨", ReaderThemeConfig.THEME_EINK, Color.parseColor("#FFFFFF"), Color.parseColor("#000000"));
        addThemeButton(ctx, themeRow, "羊皮", ReaderThemeConfig.THEME_PAPER, Color.parseColor("#F6F1E7"), Color.parseColor("#2C2723"));
        body.addView(themeRow);

        // Section B: 正文排版
        TextView sectionTypo = makeSectionHeader(ctx, "正文排版", textCol);
        body.addView(sectionTypo);

        // Font size row
        LinearLayout fontRow = new LinearLayout(ctx);
        fontRow.setOrientation(LinearLayout.HORIZONTAL);
        fontRow.setGravity(Gravity.CENTER_VERTICAL);
        fontRow.setPadding(0, 8, 0, 8);

        fontSizeDisplay = new TextView(ctx);
        fontSizeDisplay.setText("字号: 18 sp");
        fontSizeDisplay.setTextSize(15f);
        fontSizeDisplay.setTextColor(textCol);
        fontRow.addView(fontSizeDisplay, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Button fontMinus = new Button(ctx);
        fontMinus.setText("－");
        fontMinus.setOnClickListener(new SettingsFontSizeClickListener(this, -1.0f));
        fontRow.addView(fontMinus);

        Button fontPlus = new Button(ctx);
        fontPlus.setText("＋");
        fontPlus.setOnClickListener(new SettingsFontSizeClickListener(this, 1.0f));
        fontRow.addView(fontPlus);
        body.addView(fontRow);

        // Line height row
        LinearLayout lineRow = new LinearLayout(ctx);
        lineRow.setOrientation(LinearLayout.HORIZONTAL);
        lineRow.setGravity(Gravity.CENTER_VERTICAL);
        lineRow.setPadding(0, 8, 0, 8);

        lineHeightDisplay = new TextView(ctx);
        lineHeightDisplay.setText("行距: 1.8");
        lineHeightDisplay.setTextSize(15f);
        lineHeightDisplay.setTextColor(textCol);
        lineRow.addView(lineHeightDisplay, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Button lineMinus = new Button(ctx);
        lineMinus.setText("－");
        lineMinus.setOnClickListener(new SettingsLineHeightClickListener(this, -0.1f));
        lineRow.addView(lineMinus);

        Button linePlus = new Button(ctx);
        linePlus.setText("＋");
        linePlus.setOnClickListener(new SettingsLineHeightClickListener(this, 0.1f));
        lineRow.addView(linePlus);
        body.addView(lineRow);

        // Margin row
        LinearLayout marginRow = new LinearLayout(ctx);
        marginRow.setOrientation(LinearLayout.HORIZONTAL);
        marginRow.setGravity(Gravity.CENTER_VERTICAL);
        marginRow.setPadding(0, 8, 0, 8);

        marginDisplay = new TextView(ctx);
        marginDisplay.setText("边距: 20 dp");
        marginDisplay.setTextSize(15f);
        marginDisplay.setTextColor(textCol);
        marginRow.addView(marginDisplay, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Button marginMinus = new Button(ctx);
        marginMinus.setText("－");
        marginMinus.setOnClickListener(new SettingsMarginClickListener(this, -4));
        marginRow.addView(marginMinus);

        Button marginPlus = new Button(ctx);
        marginPlus.setText("＋");
        marginPlus.setOnClickListener(new SettingsMarginClickListener(this, 4));
        marginRow.addView(marginPlus);
        body.addView(marginRow);

        Button resetTypoBtn = new Button(ctx);
        resetTypoBtn.setText("恢复默认排版");
        resetTypoBtn.setOnClickListener(new SettingsResetTypographyClickListener(this));
        body.addView(resetTypoBtn);

        // Section C: 阅读体验
        TextView sectionExp = makeSectionHeader(ctx, "阅读体验", textCol);
        sectionExp.setPadding(0, 24, 0, 8);
        body.addView(sectionExp);

        keepAwakeCheckBox = new CheckBox(ctx);
        keepAwakeCheckBox.setText("阅读时屏幕常亮");
        keepAwakeCheckBox.setTextColor(textCol);
        keepAwakeCheckBox.setOnCheckedChangeListener(new SettingsKeepAwakeChangeListener(this));
        body.addView(keepAwakeCheckBox);

        immersiveCheckBox = new CheckBox(ctx);
        immersiveCheckBox.setText("沉浸阅读模式（隐藏状态栏）");
        immersiveCheckBox.setTextColor(textCol);
        immersiveCheckBox.setOnCheckedChangeListener(new SettingsImmersiveChangeListener(this));
        body.addView(immersiveCheckBox);

        prefetchCheckBox = new CheckBox(ctx);
        prefetchCheckBox.setText("自动预取下一章");
        prefetchCheckBox.setTextColor(textCol);
        prefetchCheckBox.setOnCheckedChangeListener(new SettingsPrefetchChangeListener(this));
        body.addView(prefetchCheckBox);

        autoCacheCheckBox = new CheckBox(ctx);
        autoCacheCheckBox.setText("阅读时自动缓存");
        autoCacheCheckBox.setTextColor(textCol);
        autoCacheCheckBox.setOnCheckedChangeListener(new SettingsAutoCacheChangeListener(this));
        body.addView(autoCacheCheckBox);

        TextView autoCacheHint = new TextView(ctx);
        autoCacheHint.setText("阅读时提前保存后续章节，整本模式会再补齐前面的章节。离开应用或锁屏即暂停；下次阅读继续。网站限制或存储不足时可能暂停。");
        autoCacheHint.setTextSize(12f);
        autoCacheHint.setTextColor(secTextCol);
        autoCacheHint.setPadding(32, 0, 0, 8);
        body.addView(autoCacheHint);

        LinearLayout scopeRow = new LinearLayout(ctx);
        scopeRow.setOrientation(LinearLayout.HORIZONTAL);
        scopeRow.setPadding(32, 4, 0, 8);

        scopeBtnAll = createScopeButton(ctx, "整本", "ENTIRE_BOOK");
        scopeBtn50 = createScopeButton(ctx, "后续50章", "NEXT_50");
        scopeBtn200 = createScopeButton(ctx, "后续200章", "NEXT_200");

        scopeRow.addView(scopeBtnAll);
        scopeRow.addView(scopeBtn50);
        scopeRow.addView(scopeBtn200);
        body.addView(scopeRow);

        autoCacheMeteredCheckBox = new CheckBox(ctx);
        autoCacheMeteredCheckBox.setText("允许在计费网络下载");
        autoCacheMeteredCheckBox.setTextColor(textCol);
        autoCacheMeteredCheckBox.setPadding(32, 0, 0, 16);
        autoCacheMeteredCheckBox.setOnCheckedChangeListener(new SettingsAutoCacheMeteredChangeListener(this));
        body.addView(autoCacheMeteredCheckBox);

        // Section D: 缓存与空间
        TextView sectionStorage = makeSectionHeader(ctx, "缓存管理", textCol);
        sectionStorage.setPadding(0, 16, 0, 8);
        body.addView(sectionStorage);

        storageStatsDisplay = new TextView(ctx);
        storageStatsDisplay.setText("正在计算缓存状态...");
        storageStatsDisplay.setTextSize(14f);
        storageStatsDisplay.setTextColor(secTextCol);
        storageStatsDisplay.setPadding(0, 4, 0, 12);
        body.addView(storageStatsDisplay);

        Button clearCacheBtn = new Button(ctx);
        clearCacheBtn.setText("清理本书远程正文缓存");
        clearCacheBtn.setOnClickListener(new SettingsClearCacheClickListener(this));
        body.addView(clearCacheBtn);

        Button downloadsBtn = new Button(ctx);
        downloadsBtn.setText("离线下载管理");
        downloadsBtn.setOnClickListener(new SettingsDownloadsClickListener(this));
        body.addView(downloadsBtn);

        // Section E: 个人数据备份与导入
        TextView sectionBackup = makeSectionHeader(ctx, "个人数据", textCol);
        sectionBackup.setPadding(0, 24, 0, 8);
        body.addView(sectionBackup);

        LinearLayout backupRow = new LinearLayout(ctx);
        backupRow.setOrientation(LinearLayout.HORIZONTAL);
        backupRow.setPadding(0, 8, 0, 16);

        Button exportBtn = new Button(ctx);
        exportBtn.setText("导出备份 (JSON)");
        exportBtn.setOnClickListener(new SettingsExportClickListener(this));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        backupRow.addView(exportBtn, btnParams);

        Button importBtn = new Button(ctx);
        importBtn.setText("导入备份 (JSON)");
        importBtn.setOnClickListener(new SettingsImportClickListener(this));
        backupRow.addView(importBtn, btnParams);
        body.addView(backupRow);

        Button migrateBtn = new Button(ctx);
        migrateBtn.setText("从旧版数据恢复 / 重新迁移");
        migrateBtn.setOnClickListener(new SettingsMigrationClickListener(ctx, this));
        body.addView(migrateBtn);

        // Section F: 帮助与快捷键
        TextView sectionHelp = makeSectionHeader(ctx, "关于与帮助", textCol);
        sectionHelp.setPadding(0, 16, 0, 8);
        body.addView(sectionHelp);

        Button helpBtn = new Button(ctx);
        helpBtn.setText("快捷键与离线说明");
        helpBtn.setOnClickListener(new SettingsHelpClickListener(this));
        body.addView(helpBtn);

        scrollView.addView(body);
        root.addView(scrollView);

        setContentView(root);

        // Initial fetch
        personalRepository.getSettings(new SettingsLoadedCallback(this, executors.getMainThreadExecutor()));
        loadStorageStats();
    }

    private TextView makeSectionHeader(Context ctx, String text, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(color);
        tv.setPadding(0, 12, 0, 8);
        return tv;
    }

    private void addThemeButton(Context ctx, LinearLayout parent, String name, String themeId, int bg, int fg) {
        Button btn = new Button(ctx);
        btn.setText(name);
        btn.setBackgroundColor(bg);
        btn.setTextColor(fg);
        btn.setTextSize(13f);
        btn.setPadding(8, 4, 8, 4);
        btn.setOnClickListener(new SettingsThemeClickListener(this, themeId));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lp.setMargins(4, 0, 4, 0);
        parent.addView(btn, lp);
    }

    public void loadStorageStats() {
        if (contentRepository != null && bookId != null && !bookId.isEmpty()) {
            contentRepository.getStorageStats(bookId, new SettingsStorageStatsCallback(this, executors.getMainThreadExecutor()));
        }
    }

    public void onSettingsLoaded(SettingsEntity settings) {
        if (settings != null) {
            this.currentSettings = settings;
            updateUI();
        }
    }

    public void onStorageStatsLoaded(StorageStats stats) {
        if (storageStatsDisplay != null && stats != null) {
            String text = "已缓存: " + stats.getCompletedCount() + " 章 (部分 " + stats.getPartialCount() + " 章)\n"
                    + "占用空间: " + formatBytes(stats.getTotalBytes());
            storageStatsDisplay.setText(text);
        }
    }

    private void updateUI() {
        isUpdatingUI = true;
        if (fontSizeDisplay != null) {
            fontSizeDisplay.setText(String.format(Locale.getDefault(), "字号: %.0f sp", currentSettings.fontSize));
        }
        if (lineHeightDisplay != null) {
            lineHeightDisplay.setText(String.format(Locale.getDefault(), "行距: %.1f", currentSettings.lineHeight));
        }
        if (marginDisplay != null) {
            marginDisplay.setText("边距: " + currentSettings.pageMargin + " dp");
        }
        if (keepAwakeCheckBox != null) {
            keepAwakeCheckBox.setChecked(currentSettings.keepScreenAwake);
        }
        if (immersiveCheckBox != null) {
            immersiveCheckBox.setChecked(currentSettings.immersiveMode);
        }
        if (prefetchCheckBox != null) {
            prefetchCheckBox.setChecked(currentSettings.prefetchNextChapter);
        }
        if (autoCacheCheckBox != null) {
            autoCacheCheckBox.setChecked(currentSettings.autoCacheEnabled);
        }
        if (autoCacheMeteredCheckBox != null) {
            autoCacheMeteredCheckBox.setChecked(currentSettings.autoCacheMetered);
        }
        updateScopeButtons();
        isUpdatingUI = false;
    }

    public void onThemeSelected(String theme) {
        if (theme == null) return;
        currentSettings.theme = theme;
        saveAndNotify();
        Toast.makeText(getContext(), "主题已切换", Toast.LENGTH_SHORT).show();
    }

    public void adjustFontSize(float delta) {
        float next = Math.max(14.0f, Math.min(28.0f, currentSettings.fontSize + delta));
        currentSettings.fontSize = next;
        if (fontSizeDisplay != null) {
            fontSizeDisplay.setText(String.format(Locale.getDefault(), "字号: %.0f sp", next));
        }
        saveAndNotify();
    }

    public void adjustLineHeight(float delta) {
        float next = Math.max(1.3f, Math.min(2.4f, Math.round((currentSettings.lineHeight + delta) * 10.0f) / 10.0f));
        currentSettings.lineHeight = next;
        if (lineHeightDisplay != null) {
            lineHeightDisplay.setText(String.format(Locale.getDefault(), "行距: %.1f", next));
        }
        saveAndNotify();
    }

    public void adjustMargin(int delta) {
        int next = Math.max(8, Math.min(64, currentSettings.pageMargin + delta));
        currentSettings.pageMargin = next;
        if (marginDisplay != null) {
            marginDisplay.setText("边距: " + next + " dp");
        }
        saveAndNotify();
    }

    public void resetTypography() {
        currentSettings.fontSize = 18.0f;
        currentSettings.lineHeight = 1.8f;
        currentSettings.pageMargin = 20;
        updateUI();
        saveAndNotify();
        Toast.makeText(getContext(), "已恢复默认排版", Toast.LENGTH_SHORT).show();
    }

    public void onKeepAwakeChanged(boolean isChecked) {
        if (isUpdatingUI) return;
        currentSettings.keepScreenAwake = isChecked;
        saveAndNotify();
    }

    public void onImmersiveChanged(boolean isChecked) {
        if (isUpdatingUI) return;
        currentSettings.immersiveMode = isChecked;
        saveAndNotify();
    }

    public void onPrefetchChanged(boolean isChecked) {
        if (isUpdatingUI) return;
        currentSettings.prefetchNextChapter = isChecked;
        saveAndNotify();
    }

    private Button createScopeButton(Context ctx, String text, String scope) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(12f);
        btn.setPadding(8, 4, 8, 4);
        btn.setOnClickListener(new SettingsAutoCacheScopeClickListener(this, scope));
        return btn;
    }

    private void updateScopeButtons() {
        String scope = currentSettings.autoCacheScope != null ? currentSettings.autoCacheScope : "ENTIRE_BOOK";
        highlightScopeButton(scopeBtnAll, "ENTIRE_BOOK".equals(scope));
        highlightScopeButton(scopeBtn50, "NEXT_50".equals(scope));
        highlightScopeButton(scopeBtn200, "NEXT_200".equals(scope));
    }

    private void highlightScopeButton(Button btn, boolean selected) {
        if (btn == null) return;
        btn.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    public void onAutoCacheChanged(boolean isChecked) {
        if (isUpdatingUI) return;
        currentSettings.autoCacheEnabled = isChecked;
        saveAndNotify();
        syncDownloadPolicy();
    }

    public void onAutoCacheScopeSelected(String scope) {
        if (scope == null) return;
        currentSettings.autoCacheScope = scope;
        updateScopeButtons();
        saveAndNotify();
        syncDownloadPolicy();
    }

    public void onAutoCacheMeteredChanged(boolean isChecked) {
        if (isUpdatingUI) return;
        currentSettings.autoCacheMetered = isChecked;
        saveAndNotify();
        syncDownloadPolicy();
    }

    private void syncDownloadPolicy() {
        if (coordinator != null && contentRepository != null && bookId != null && !bookId.startsWith("local-")) {
            org.wanshu.reader.data.content.entity.DownloadPolicyEntity policy = new org.wanshu.reader.data.content.entity.DownloadPolicyEntity();
            policy.bookId = bookId;
            policy.autoCacheEnabled = currentSettings.autoCacheEnabled;
            policy.scope = currentSettings.autoCacheScope != null ? currentSettings.autoCacheScope : "ENTIRE_BOOK";
            policy.allowMetered = currentSettings.autoCacheMetered;
            policy.maxCacheBytes = currentSettings.maxCacheMb * 1024L * 1024L;
            executors.getContentDbExecutor().execute(new org.wanshu.reader.download.DownloadSavePolicyRunnable(
                    coordinator,
                    contentRepository.getDatabase(),
                    policy
            ));
        }
    }

    private void saveAndNotify() {
        personalRepository.saveSettings(currentSettings, null);
        if (changeListener != null) {
            changeListener.onSettingsChanged(currentSettings);
        }
    }

    public void clearRemoteCache() {
        if (bookId.startsWith("local-")) {
            Toast.makeText(getContext(), "本地导入书籍不支持清理", Toast.LENGTH_SHORT).show();
            return;
        }
        contentRepository.clearRemoteCache(bookId, new SettingsClearCacheCallback(this, executors.getMainThreadExecutor()));
    }

    public void onClearCacheComplete() {
        Toast.makeText(getContext(), "远程正文缓存已清理", Toast.LENGTH_SHORT).show();
        loadStorageStats();
    }

    public void exportPersonalData() {
        executors.getContentDbExecutor().execute(new SettingsExportRunnable(
                this,
                personalRepository.getDatabase(),
                executors.getMainThreadExecutor()
        ));
    }

    public void onExportResult(String json) {
        if (json != null && !json.isEmpty()) {
            try {
                ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("wangu_reader_backup", json));
                    Toast.makeText(getContext(), "个人数据已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(getContext(), "导出失败", Toast.LENGTH_SHORT).show();
    }

    public void importPersonalData() {
        SettingsImportDialog importDialog = new SettingsImportDialog(getContext(), this, colors);
        importDialog.show();
    }

    public void performImport(String json, boolean replace) {
        executors.getContentDbExecutor().execute(new SettingsImportRunnable(
                this,
                personalRepository.getDatabase(),
                json,
                replace,
                executors.getMainThreadExecutor()
        ));
    }

    public void onImportResult(boolean success, SettingsEntity updatedSettings) {
        if (success) {
            if (updatedSettings != null) {
                this.currentSettings = updatedSettings;
                updateUI();
                if (changeListener != null) {
                    changeListener.onSettingsChanged(currentSettings);
                }
            }
            loadStorageStats();
            Toast.makeText(getContext(), "个人数据导入成功", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "导入失败：格式错误或内容无效", Toast.LENGTH_SHORT).show();
        }
    }

    public void showHelp() {
        SettingsHelpDialog helpDialog = new SettingsHelpDialog(getContext(), colors);
        helpDialog.show();
    }

    public void openDownloadsDialog() {
        if (coordinator != null) {
            org.wanshu.reader.ui.download.DownloadsDialog dialog = new org.wanshu.reader.ui.download.DownloadsDialog(
                    getContext(),
                    bookId,
                    currentChapterId,
                    coordinator,
                    colors
            );
            dialog.show();
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        } else {
            return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
