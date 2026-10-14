package org.wanshu.reader.ui.toc;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.wanshu.reader.ui.theme.ThemeColors;

public class TocViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    private final TextView titleView;
    private final TextView badgeView;
    private TocItemModel item;
    private TocItemClickListener listener;

    public TocViewHolder(LinearLayout itemView, TextView titleView, TextView badgeView) {
        super(itemView);
        this.titleView = titleView;
        this.badgeView = badgeView;
        itemView.setOnClickListener(this);
    }

    public static TocViewHolder create(ViewGroup parent) {
        Context context = parent.getContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(32, 24, 32, 24);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView tvTitle = new TextView(context);
        tvTitle.setTextSize(15f);
        tvTitle.setSingleLine(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        layout.addView(tvTitle, titleParams);

        TextView tvBadge = new TextView(context);
        tvBadge.setTextSize(12f);
        tvBadge.setPadding(12, 0, 0, 0);
        layout.addView(tvBadge);

        return new TocViewHolder(layout, tvTitle, tvBadge);
    }

    public void bind(TocItemModel item, ThemeColors colors, TocItemClickListener listener) {
        if (item == null) return;

        titleView.setText(item.getTitle());

        int textColor = colors != null ? colors.text : Color.BLACK;
        if (item.isCurrent()) {
            titleView.setTextColor(Color.parseColor("#1976D2")); // Blue highlight for current chapter
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            titleView.setTextColor(textColor);
            titleView.setTypeface(Typeface.DEFAULT);
        }

        if (item.getCacheStatus() == TocCacheStatus.COMPLETE) {
            badgeView.setText("已下载");
            badgeView.setTextColor(Color.parseColor("#388E3C")); // Green
        } else if (item.getCacheStatus() == TocCacheStatus.PARTIAL) {
            badgeView.setText("部分");
            badgeView.setTextColor(Color.parseColor("#F57C00")); // Orange
        } else {
            badgeView.setText("");
        }

        this.item = item;
        this.listener = listener;
    }

    @Override
    public void onClick(View v) {
        if (listener != null && item != null) listener.onTocItemClicked(item);
    }
}
