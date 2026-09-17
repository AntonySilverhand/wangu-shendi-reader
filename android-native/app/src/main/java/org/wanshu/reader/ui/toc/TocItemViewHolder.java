package org.wanshu.reader.ui.toc;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.wanshu.reader.ui.theme.ThemeColors;

public class TocItemViewHolder extends RecyclerView.ViewHolder {
    private final TextView titleView;
    private final TextView statusView;
    private final LinearLayout container;

    public TocItemViewHolder(LinearLayout container, TextView titleView, TextView statusView) {
        super(container);
        this.container = container;
        this.titleView = titleView;
        this.statusView = statusView;
    }

    public static TocItemViewHolder create(ViewGroup parent) {
        Context context = parent.getContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        layout.setPadding(32, 24, 32, 24);

        TextView title = new TextView(context);
        title.setTextSize(15f);
        title.setSingleLine(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        layout.addView(title, titleParams);

        TextView status = new TextView(context);
        status.setTextSize(12f);
        status.setPadding(16, 0, 0, 0);
        layout.addView(status);

        return new TocItemViewHolder(layout, title, status);
    }

    public void bind(TocItemModel model, ThemeColors colors, TocDialog dialog) {
        if (model == null) return;

        titleView.setText(model.getTitle());

        if (model.isCurrent()) {
            titleView.setTypeface(null, Typeface.BOLD);
            titleView.setTextColor(Color.parseColor("#1976D2")); // Active blue
        } else {
            titleView.setTypeface(null, Typeface.NORMAL);
            if (colors != null) {
                titleView.setTextColor(colors.text);
            }
        }

        if (model.isComplete()) {
            statusView.setText("已下载");
            statusView.setTextColor(Color.parseColor("#388E3C")); // Green
        } else if (model.isPartial()) {
            statusView.setText("部分");
            statusView.setTextColor(Color.parseColor("#F57C00")); // Orange
        } else {
            statusView.setText("");
        }

        container.setOnClickListener(new TocItemClickListener(dialog, model));
    }
}
