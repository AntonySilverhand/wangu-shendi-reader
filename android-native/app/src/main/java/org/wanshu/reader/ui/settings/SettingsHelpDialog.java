package org.wanshu.reader.ui.settings;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.wanshu.reader.ui.theme.ThemeColors;

public class SettingsHelpDialog extends Dialog {
    private final ThemeColors colors;

    public SettingsHelpDialog(Context context, ThemeColors colors) {
        super(context);
        this.colors = colors;
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

        // Header
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(24, 16, 16, 16);
        header.setBackgroundColor(barBg);

        TextView titleView = new TextView(ctx);
        titleView.setText("快捷键与说明");
        titleView.setTextSize(18f);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(textCol);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(titleView, titleParams);

        Button closeBtn = new Button(ctx);
        closeBtn.setText("✕");
        closeBtn.setOnClickListener(new SettingsHelpCloseClickListener(this));
        header.addView(closeBtn);
        root.addView(header);

        // Content
        ScrollView scroll = new ScrollView(ctx);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(32, 24, 32, 32);

        TextView h1 = new TextView(ctx);
        h1.setText("一、按键与手势快捷操作");
        h1.setTextSize(16f);
        h1.setTypeface(null, Typeface.BOLD);
        h1.setTextColor(textCol);
        h1.setPadding(0, 8, 0, 8);
        body.addView(h1);

        TextView p1 = new TextView(ctx);
        p1.setText("• 方向键 ← / →：切换上一章 / 下一章\n"
                + "• S 键：开启 / 收起本章搜索\n"
                + "• B 键：打开书签面板\n"
                + "• T 键：打开目录与全书搜索\n"
                + "• Esc 键：关闭当前打开的搜索或弹窗\n"
                + "• 点击正文区域：显示或隐藏顶部操作栏\n"
                + "• 长按正文文字：唤出系统原生文字选择与复制");
        p1.setTextSize(14f);
        p1.setLineSpacing(0, 1.4f);
        p1.setTextColor(secTextCol);
        p1.setPadding(0, 0, 0, 24);
        body.addView(p1);

        TextView h2 = new TextView(ctx);
        h2.setText("二、离线阅读与缓存机制");
        h2.setTextSize(16f);
        h2.setTypeface(null, Typeface.BOLD);
        h2.setTextColor(textCol);
        h2.setPadding(0, 8, 0, 8);
        body.addView(h2);

        TextView p2 = new TextView(ctx);
        p2.setText("• 零后台消耗：所有正文预取与自动缓存仅在应用前台阅读时推进；锁屏、切后台或返回书架时即刻暂停，不驻留任何后台服务。\n"
                + "• 缓存独立安全：清理远程正文缓存仅释放网络章节数据，本地导入的 TXT 书籍、阅读进度与书签记录绝不会被清除。\n"
                + "• 备份互通：支持与网页版完全兼容的 JSON 个人数据导出与导入，可在多端自由迁移书签与进度。");
        p2.setTextSize(14f);
        p2.setLineSpacing(0, 1.4f);
        p2.setTextColor(secTextCol);
        p2.setPadding(0, 0, 0, 24);
        body.addView(p2);

        TextView h3 = new TextView(ctx);
        h3.setText("三、版本信息");
        h3.setTextSize(16f);
        h3.setTypeface(null, Typeface.BOLD);
        h3.setTextColor(textCol);
        h3.setPadding(0, 8, 0, 8);
        body.addView(h3);

        TextView p3 = new TextView(ctx);
        p3.setText("《万古神帝》个人原生阅读器 v0.0.9\n"
                + "纯原生 Android View + Room 架构，无网页容器开销。");
        p3.setTextSize(13f);
        p3.setTextColor(secTextCol);
        p3.setPadding(0, 0, 0, 16);
        body.addView(p3);

        scroll.addView(body);
        root.addView(scroll);

        setContentView(root);
    }
}
