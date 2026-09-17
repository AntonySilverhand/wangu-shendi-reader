package org.wanshu.reader.ui.bookmarks;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
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

public class BookmarkItemViewHolder extends RecyclerView.ViewHolder {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private final LinearLayout container;
    private final TextView snippetView;
    private final TextView metaView;
    private final Button deleteButton;

    public BookmarkItemViewHolder(
            LinearLayout container,
            TextView snippetView,
            TextView metaView,
            Button deleteButton
    ) {
        super(container);
        this.container = container;
        this.snippetView = snippetView;
        this.metaView = metaView;
        this.deleteButton = deleteButton;
    }

    public static BookmarkItemViewHolder create(ViewGroup parent) {
        Context context = parent.getContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        layout.setPadding(32, 20, 32, 20);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);

        TextView snippet = new TextView(context);
        snippet.setTextSize(15f);
        snippet.setMaxLines(2);
        textCol.addView(snippet);

        TextView meta = new TextView(context);
        meta.setTextSize(12f);
        meta.setPadding(0, 8, 0, 0);
        textCol.addView(meta);

        layout.addView(textCol, colParams);

        Button deleteBtn = new Button(context);
        deleteBtn.setText("删除");
        deleteBtn.setTextSize(12f);
        layout.addView(deleteBtn);

        return new BookmarkItemViewHolder(layout, snippet, meta, deleteBtn);
    }

    public void bind(BookmarkEntity bookmark, ThemeColors colors, BookmarksDialog dialog) {
        if (bookmark == null) return;

        String snip = bookmark.snippet != null && !bookmark.snippet.isEmpty()
                ? bookmark.snippet
                : "第 " + bookmark.paragraphIndex + " 段";
        snippetView.setText(snip);

        String dateStr = bookmark.createdAt > 0 ? DATE_FORMAT.format(new Date(bookmark.createdAt)) : "";
        metaView.setText("第 " + bookmark.chapterId + " 章 · 段落 " + bookmark.paragraphIndex + "  " + dateStr);

        if (colors != null) {
            snippetView.setTextColor(colors.text);
            metaView.setTextColor(colors.secondaryText);
        }

        container.setOnClickListener(new BookmarkItemClickListener(dialog, bookmark));
        deleteButton.setOnClickListener(new BookmarkDeleteClickListener(dialog, bookmark));
    }
}
