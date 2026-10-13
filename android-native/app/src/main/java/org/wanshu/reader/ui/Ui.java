package org.wanshu.reader.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import org.wanshu.reader.ui.theme.ThemeColors;

public final class Ui {
    private Ui() {}
    public static int dp(Context context, float value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
    public static Button button(Context context, String text, View.OnClickListener listener) {
        Button button = new Button(context);
        button.setText(text);
        button.setContentDescription(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setMinWidth(dp(context, 48));
        button.setMinimumWidth(dp(context, 48));
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4));
        button.setOnClickListener(listener);
        return button;
    }
    public static void colors(View root, ThemeColors colors) {
        if (root instanceof TextView) ((TextView) root).setTextColor(colors.text);
        if (root instanceof EditText) ((EditText) root).setHintTextColor(colors.secondaryText);
        if (root instanceof Button) root.setBackgroundTintList(ColorStateList.valueOf(colors.barBackground));
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) colors(group.getChildAt(i), colors);
        }
    }
}
