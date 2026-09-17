package org.wanshu.reader.ui.bookmarks;

import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.ui.theme.ThemeColors;

public class BookmarksAdapter extends RecyclerView.Adapter<BookmarkItemViewHolder> {
    private final BookmarksDialog dialog;
    private final List<BookmarkEntity> bookmarks = new ArrayList<BookmarkEntity>();
    private ThemeColors colors;

    public BookmarksAdapter(BookmarksDialog dialog) {
        this.dialog = dialog;
    }

    public void setColors(ThemeColors colors) {
        this.colors = colors;
        notifyDataSetChanged();
    }

    public void setData(List<BookmarkEntity> list) {
        bookmarks.clear();
        if (list != null) {
            bookmarks.addAll(list);
        }
        notifyDataSetChanged();
    }

    public void removeById(String id) {
        if (id == null) return;
        for (int i = 0; i < bookmarks.size(); i++) {
            if (id.equals(bookmarks.get(i).id)) {
                bookmarks.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    public void clear() {
        bookmarks.clear();
        notifyDataSetChanged();
    }

    public int getCount() {
        return bookmarks.size();
    }

    @Override
    public BookmarkItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return BookmarkItemViewHolder.create(parent);
    }

    @Override
    public void onBindViewHolder(BookmarkItemViewHolder holder, int position) {
        if (position >= 0 && position < bookmarks.size()) {
            holder.bind(bookmarks.get(position), colors, dialog);
        }
    }

    @Override
    public int getItemCount() {
        return bookmarks.size();
    }
}
