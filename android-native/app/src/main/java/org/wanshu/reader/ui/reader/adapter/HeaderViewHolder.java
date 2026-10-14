package org.wanshu.reader.ui.reader.adapter;

import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.wanshu.reader.ui.theme.ThemeColors;

public class HeaderViewHolder extends RecyclerView.ViewHolder {
    /** Shown under the title when the source only publishes part of the chapter (member-only). */
    public static final String RESTRICTED_NOTICE = "源站仅公开本章部分内容（需源站会员），本阅读器不绕过限制";

    private final TextView titleView;
    private final TextView noticeView;

    public HeaderViewHolder(View itemView, TextView titleView, TextView noticeView) {
        super(itemView);
        this.titleView = titleView;
        this.noticeView = noticeView;
    }

    public static HeaderViewHolder create(ViewGroup parent) {
        LinearLayout layout = new LinearLayout(parent.getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        layout.setPadding(0, 24, 0, 36);

        TextView tv = new TextView(parent.getContext());
        tv.setTextSize(24f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(tv);

        TextView notice = new TextView(parent.getContext());
        notice.setTextSize(13f);
        notice.setText(RESTRICTED_NOTICE);
        notice.setPadding(0, 16, 0, 0);
        notice.setVisibility(View.GONE);
        notice.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(notice);

        return new HeaderViewHolder(layout, tv, notice);
    }

    public void bind(String title, boolean restricted, ThemeColors colors) {
        titleView.setText(title != null ? title : "");
        noticeView.setVisibility(restricted ? View.VISIBLE : View.GONE);
        if (colors != null) {
            titleView.setTextColor(colors.text);
            noticeView.setTextColor(colors.secondaryText);
        }
    }
}
