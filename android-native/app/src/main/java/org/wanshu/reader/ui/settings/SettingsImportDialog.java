package org.wanshu.reader.ui.settings;

import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.wanshu.reader.ui.theme.ThemeColors;

public class SettingsImportDialog extends Dialog {
    private final SettingsDialog parentDialog;
    private final ThemeColors colors;
    private EditText editText;

    public SettingsImportDialog(Context context, SettingsDialog parentDialog, ThemeColors colors) {
        super(context);
        this.parentDialog = parentDialog;
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
        root.setPadding(32, 32, 32, 32);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(ctx);
        title.setText("导入个人数据");
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(textCol);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        TextView note = new TextView(ctx);
        note.setText("请粘贴个人备份 JSON 数据（含设置、书签、阅读进度与历史）。\n合并导入保留现有数据并更新；覆盖导入将清空现有书签重新导入。");
        note.setTextSize(13f);
        note.setTextColor(secTextCol);
        note.setPadding(0, 0, 0, 16);
        root.addView(note);

        editText = new EditText(ctx);
        editText.setHint("在此粘贴 JSON 数据...");
        editText.setTextSize(13f);
        editText.setTextColor(textCol);
        editText.setHintTextColor(secTextCol);
        editText.setBackgroundColor(barBg);
        editText.setPadding(16, 16, 16, 16);
        editText.setLines(6);
        editText.setGravity(Gravity.TOP | Gravity.START);

        // Auto paste from clipboard if valid json detected
        try {
            ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence clip = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (clip != null) {
                    String s = clip.toString().trim();
                    if (s.startsWith("{") && s.contains("wangu-shendi-reader")) {
                        editText.setText(s);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        root.addView(editText);

        LinearLayout buttonBar = new LinearLayout(ctx);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.END);
        buttonBar.setPadding(0, 24, 0, 0);

        Button cancelBtn = new Button(ctx);
        cancelBtn.setText("取消");
        cancelBtn.setOnClickListener(new SettingsImportCancelClickListener(this));
        buttonBar.addView(cancelBtn);

        Button mergeBtn = new Button(ctx);
        mergeBtn.setText("合并导入");
        mergeBtn.setOnClickListener(new SettingsImportMergeClickListener(this));
        buttonBar.addView(mergeBtn);

        Button replaceBtn = new Button(ctx);
        replaceBtn.setText("覆盖导入");
        replaceBtn.setOnClickListener(new SettingsImportReplaceClickListener(this));
        buttonBar.addView(replaceBtn);

        root.addView(buttonBar);
        setContentView(root);
    }

    public void doImport(boolean replace) {
        if (editText == null) return;
        String content = editText.getText() != null ? editText.getText().toString().trim() : "";
        if (content.isEmpty()) {
            Toast.makeText(getContext(), "请输入或粘贴备份 JSON", Toast.LENGTH_SHORT).show();
            return;
        }

        if (parentDialog != null) {
            parentDialog.performImport(content, replace);
        }
        dismiss();
    }
}
