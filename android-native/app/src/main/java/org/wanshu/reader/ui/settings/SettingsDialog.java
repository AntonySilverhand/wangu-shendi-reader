package org.wanshu.reader.ui.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Activity;
import org.wanshu.reader.MainActivity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.content.StorageStats;
import org.wanshu.reader.data.personal.entity.SettingsEntity;
import org.wanshu.reader.data.repository.ContentRepository;
import org.wanshu.reader.data.repository.PersonalRepository;
import org.wanshu.reader.ui.ActionFlowLayout;
import org.wanshu.reader.ui.Ui;
import org.wanshu.reader.ui.reader.ReaderController;
import org.wanshu.reader.ui.theme.ReaderThemeConfig;
import org.wanshu.reader.ui.theme.ThemeColors;

/** Native, scrollable settings. DB callbacks publish state; only run() touches widgets. */
public final class SettingsDialog extends Dialog implements Runnable, View.OnClickListener, DialogInterface.OnClickListener {
    private final String bookId;
    private final PersonalRepository personal;
    private final ContentRepository content;
    private final AppExecutors executors;
    private final ReaderController reader;
    private LinearLayout body;
    private AlertDialog subDialog;
    private volatile SettingsEntity settings;
    private volatile StorageStats stats;
    private volatile String message = "正在读取设置…";
    private volatile boolean saving;

    public SettingsDialog(android.content.Context context, String bookId, PersonalRepository personal,
            ContentRepository content, AppExecutors executors, ReaderController reader) {
        super(context);
        if (context instanceof Activity) setOwnerActivity((Activity) context);
        this.bookId = bookId; this.personal = personal; this.content = content;
        this.executors = executors; this.reader = reader;
    }
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(true);
        body = new LinearLayout(getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = Ui.dp(getContext(), 16);
        body.setPadding(padding, padding, padding, padding);
        scroll.addView(body);
        setContentView(scroll);
        personal.getSettings(new SettingsLoadedCallback(this));
        content.getStorageStats(bookId, new SettingsStorageStatsCallback(this));
        text(message);
        body.addView(button("完成", "close"));
    }
    @Override
    protected void onStart() {
        super.onStart();
        if (getWindow() != null) getWindow().setLayout(
                Math.min(getContext().getResources().getDisplayMetrics().widthPixels, Ui.dp(getContext(), 600)),
                android.view.ViewGroup.LayoutParams.MATCH_PARENT);
    }
    @Override public void dismiss() {
        if (subDialog != null) { subDialog.dismiss(); subDialog = null; }
        super.dismiss();
    }
    private TextView text(String value) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(16);
        view.setPadding(0, Ui.dp(getContext(), 8), 0, Ui.dp(getContext(), 8));
        body.addView(view); return view;
    }
    private Button button(String title, String action) {
        Button button = Ui.button(getContext(), title, this);
        button.setTag(action);
        return button;
    }
    private void action(ActionFlowLayout row, String title, String action) {
        Button button = button(title, action);
        button.setEnabled(editable()); row.addView(button);
    }
    @Override public void onClick(View view) {
        String action = (String) view.getTag();
        if ("close".equals(action)) { dismiss(); return; }
        if (!editable() || action == null) return;
        switch (action) {
            case "light": case "dark": case "sepia": case "eyecare": case "oled": settings.theme = action; break;
            case "font-": settings.fontSize = Math.max(14, settings.fontSize - 1); break;
            case "font+": settings.fontSize = Math.min(28, settings.fontSize + 1); break;
            case "line-": settings.lineHeight = Math.max(1.3f, settings.lineHeight - 0.1f); break;
            case "line+": settings.lineHeight = Math.min(2.4f, settings.lineHeight + 0.1f); break;
            case "margin-": settings.pageMargin = Math.max(8, settings.pageMargin - 2); break;
            case "margin+": settings.pageMargin = Math.min(64, settings.pageMargin + 2); break;
            case "reset":
                settings.fontSize = 18; settings.lineHeight = 1.8f; settings.pageMargin = 20;
                settings.paragraphSpacing = 0.7f; settings.maxWidthChars = 36; break;
            case "awake": settings.keepScreenAwake = !settings.keepScreenAwake; break;
            case "clear": onClearCacheClicked(); return;
            case "help": onHelpClicked(); return;
            case "export": case "import":
                if (getOwnerActivity() instanceof MainActivity && ((MainActivity) getOwnerActivity()).pickPersonalData("import".equals(action))) dismiss();
                return;
            default: return;
        }
        save();
    }
    @Override public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) executeClearRemoteCache();
    }
    private ActionFlowLayout row() {
        ActionFlowLayout row = new ActionFlowLayout(getContext()); body.addView(row); return row;
    }
    @Override
    public void run() {
        if (body == null || !isShowing()) return;
        body.removeAllViews();
        ThemeColors colors = ReaderThemeConfig.getThemeColors(settings == null ? "light" : settings.theme);
        body.setBackgroundColor(colors.background);
        text("主题与排版").setTextSize(22);
        body.addView(button("完成", "close"));
        text(saving ? "正在保存…" : message).setContentDescription(saving ? "正在保存设置" : message);
        if (settings != null) {
            ActionFlowLayout themes = row();
            String[] names = {"light", "dark", "sepia", "eyecare", "oled"};
            String[] labels = {"亮色", "暗色", "纸张", "护眼", "纯黑"};
            for (int i = 0; i < names.length; i++) {
                action(themes, (names[i].equals(settings.theme) ? "✓ " : "") + labels[i], names[i]);
            }
            text(String.format(Locale.ROOT, "字号 %.0fsp · 行距 %.1f · 边距 %ddp", settings.fontSize, settings.lineHeight, settings.pageMargin));
            ActionFlowLayout type = row();
            action(type, "字号 −", "font-");
            action(type, "字号 +", "font+");
            action(type, "行距 −", "line-");
            action(type, "行距 +", "line+");
            action(type, "边距 −", "margin-");
            action(type, "边距 +", "margin+");
            action(type, "恢复默认排版", "reset");
            ActionFlowLayout behavior = row();
            action(behavior, "阅读常亮：" + (settings.keepScreenAwake ? "开" : "关"), "awake");
        }
        text("缓存与数据").setTextSize(20);
        if (stats != null) text(String.format(Locale.ROOT, "完整 %d 章 · 部分 %d 章 · %.1f MiB",
                stats.getCompletedCount(), stats.getPartialCount(), stats.getTotalBytes() / 1048576.0));
        ActionFlowLayout data = row();
        if ("36780".equals(bookId)) action(data, "清理在线正文缓存", "clear");
        else text("本地 TXT 不会被清理正文缓存删除。请保留原 TXT 文件。");
        if (getOwnerActivity() instanceof MainActivity) {
            action(data, "导出个人 JSON", "export");
            action(data, "导入原生 JSON", "import");
        }
        action(data, "帮助与数据说明", "help");
        Ui.colors(body, colors);
    }
    private void render() { executors.getMainThreadExecutor().execute(this); }
    public void onSettingsLoaded(SettingsEntity value) {
        settings = value == null ? new SettingsEntity() : value;
        saving = false; message = "设置保存在本机";
        reader.applySettings(settings); render();
    }
    public void onSettingsError(String error) { saving = false; message = error; render(); }
    public void onStorageStatsLoaded(StorageStats value) { stats = value; render(); }
    public void onStorageStatsError(String error) { message = error; render(); }
    private boolean editable() {
        return settings != null && !saving && !(getOwnerActivity() instanceof MainActivity && ((MainActivity) getOwnerActivity()).isPersonalFileBusy());
    }
    private void save() { saving = true; render(); personal.saveSettings(settings, new SettingsSaveCompletedCallback(this)); }
    public void onSettingsSaved() { personal.getSettings(new SettingsLoadedCallback(this)); }
    private void onClearCacheClicked() {
        subDialog = new AlertDialog.Builder(getContext()).setTitle("清理在线正文缓存？")
                .setMessage("保留书签、进度、设置和本地 TXT。离线时将无法阅读被清理的章节。")
                .setNegativeButton("取消", null).setPositiveButton("清理", this).show();
    }
    private void executeClearRemoteCache() {
        if (!"36780".equals(bookId) || saving) return;
        saving = true; reader.cancelActiveRequest(); render();
        content.clearRemoteCache(bookId, new SettingsClearCacheCallback(this));
    }
    public void onCacheCleared() { saving = false; message = "在线正文缓存已清理"; content.getStorageStats(bookId, new SettingsStorageStatsCallback(this)); render(); }
    public void onClearCacheError(String error) { saving = false; message = error; render(); }
    private void onHelpClicked() {
        subDialog = new AlertDialog.Builder(getContext()).setTitle("阅读帮助")
                .setMessage("左上角「书架」可返回主页换书。目录、搜索、书签、设置位于顶部操作区；搜索时点 × 关闭。\n\n外接键盘：← / → 上下章，T 目录，F 本章搜索，B 书签列表，S / 逗号 设置，Home / End 章首章尾。搜索中 Enter / Shift+Enter 切换命中，Esc 关闭搜索或阅读面板。输入、中文组词、选字时不抢占阅读快捷键；Ctrl / Alt / Meta 组合键交给系统。\n\n设置与个人数据仅保存在本机，不会云同步。原生 JSON 备份只包含设置、进度、书签与历史，不含小说正文。仅合并同一书籍 ID 的记录；重新导入 TXT 会生成新 ID，不能自动关联旧进度。请另外保留原 TXT。原生开发版与旧版数据隔离，旧版迁移尚未接入，请勿卸载旧版。")
                .setPositiveButton("知道了", null).show();
    }
}
