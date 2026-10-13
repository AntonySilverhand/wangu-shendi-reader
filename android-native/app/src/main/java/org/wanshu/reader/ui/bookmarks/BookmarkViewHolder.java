package org.wanshu.reader.ui.bookmarks;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.ui.theme.ThemeColors;
import org.wanshu.reader.ui.Ui;

public class BookmarkViewHolder extends RecyclerView.ViewHolder {
    private final TextView snippetView;
    private final TextView infoView;
    private final Button deleteButton;

    public BookmarkViewHolder(LinearLayout itemView, TextView snippetView, TextView infoView, Button deleteButton) {
        super(itemView);
        this.snippetView = snippetView;
        this.infoView = infoView;
        this.deleteButton = deleteButton;
    }

    public static BookmarkViewHolder create(ViewGroup parent) {
        Context context = parent.getContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 8));
        layout.setMinimumHeight(Ui.dp(context, 64));
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textCol.setLayoutParams(colParams);

        TextView tvSnippet = new TextView(context);
        tvSnippet.setTextSize(15f);
        tvSnippet.setMaxLines(2);
        tvSnippet.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(tvSnippet);

        TextView tvInfo = new TextView(context);
        tvInfo.setTextSize(12f);
        tvInfo.setPadding(0, Ui.dp(context, 4), 0, 0);
        tvInfo.setMaxLines(2);
        tvInfo.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(tvInfo);

        layout.addView(textCol);

        Button btnDelete = Ui.button(context, "删除", null);
        layout.addView(btnDelete);

        return new BookmarkViewHolder(layout, tvSnippet, tvInfo, btnDelete);
    }

    public void bind(BookmarkEntity bm, ThemeColors colors, BookmarkItemClickListener listener) {
        if (bm == null) return;

        int textColor = colors != null ? colors.text : Color.BLACK;
        snippetView.setText(bm.snippet != null && !bm.snippet.isEmpty() ? bm.snippet : "[无内容]");
        snippetView.setTextColor(textColor);

        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(bm.createdAt));
        String info = "章节 " + bm.chapterId + " · 段落 " + (bm.paragraphIndex + 1L) + " · " + dateStr;
        infoView.setText(info);
        infoView.setTextColor(colors != null ? colors.secondaryText : Color.parseColor("#666666"));
        if (colors != null) Ui.colors(deleteButton, colors);

        itemView.setOnClickListener(new BookmarkItemViewClickListener(listener, bm));
        deleteButton.setOnClickListener(new BookmarkDeleteClickListener(listener, bm));
    }
}
