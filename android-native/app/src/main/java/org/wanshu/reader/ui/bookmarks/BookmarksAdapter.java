package org.wanshu.reader.ui.bookmarks;

import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.ui.theme.ThemeColors;

public class BookmarksAdapter extends RecyclerView.Adapter<BookmarkViewHolder> {
    private final List<BookmarkEntity> bookmarks = new ArrayList<BookmarkEntity>();
    private ThemeColors colors;
    private BookmarkItemClickListener listener;

    public void setItemClickListener(BookmarkItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<BookmarkEntity> items, ThemeColors colors) {
        this.bookmarks.clear();
        if (items != null) {
            this.bookmarks.addAll(items);
        }
        this.colors = colors;
        notifyDataSetChanged();
    }

    @Override
    public BookmarkViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return BookmarkViewHolder.create(parent);
    }

    @Override
    public void onBindViewHolder(BookmarkViewHolder holder, int position) {
        if (position >= 0 && position < bookmarks.size()) {
            holder.bind(bookmarks.get(position), colors, listener);
        }
    }

    @Override
    public int getItemCount() {
        return bookmarks.size();
    }
}
