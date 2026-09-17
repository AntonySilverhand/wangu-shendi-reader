package org.wanshu.reader.ui.reader.adapter;

import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.wanshu.reader.ui.theme.ThemeColors;

public class HeaderViewHolder extends RecyclerView.ViewHolder {
    private final TextView titleView;

    public HeaderViewHolder(View itemView, TextView titleView) {
        super(itemView);
        this.titleView = titleView;
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

        return new HeaderViewHolder(layout, tv);
    }

    public void bind(String title, ThemeColors colors) {
        titleView.setText(title != null ? title : "");
        if (colors != null) {
            titleView.setTextColor(colors.text);
        }
    }
}
