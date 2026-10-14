package org.wanshu.reader.ui.toc;

import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.ui.theme.ThemeColors;

public class TocAdapter extends RecyclerView.Adapter<TocViewHolder> {
    private final List<TocItemModel> allItems = new ArrayList<TocItemModel>();
    private final List<TocItemModel> displayedItems = new ArrayList<TocItemModel>();
    private TocFilterType currentFilter = TocFilterType.ALL;
    private boolean isSearchActive = false;
    private ThemeColors colors;
    private TocItemClickListener itemClickListener;

    public void setItemClickListener(TocItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setData(List<TocItemModel> items, ThemeColors colors) {
        this.allItems.clear();
        if (items != null) {
            this.allItems.addAll(items);
        }
        this.colors = colors;
        applyFilter();
    }

    public void setFilter(TocFilterType filter) {
        this.currentFilter = filter != null ? filter : TocFilterType.ALL;
        this.isSearchActive = false;
        applyFilter();
    }

    /** Indices refer to the list passed to setData (same order as the saved TOC). */
    public void setSearchResults(List<Integer> matches) {
        this.isSearchActive = true;
        this.displayedItems.clear();
        for (int i = 0; i < matches.size(); i++) {
            int index = matches.get(i);
            if (index >= 0 && index < allItems.size()) displayedItems.add(allItems.get(index));
        }
        notifyDataSetChanged();
    }

    public void clearSearchResults() {
        this.isSearchActive = false;
        applyFilter();
    }

    private void applyFilter() {
        displayedItems.clear();
        for (int i = 0; i < allItems.size(); i++) {
            TocItemModel item = allItems.get(i);
            if (currentFilter == TocFilterType.MAIN && item.isExtra()) {
                continue;
            }
            if (currentFilter == TocFilterType.EXTRA && !item.isExtra()) {
                continue;
            }
            displayedItems.add(item);
        }
        notifyDataSetChanged();
    }

    public int findPositionForChapter(String chapterId) {
        if (chapterId == null) return -1;
        for (int i = 0; i < displayedItems.size(); i++) {
            if (chapterId.equals(displayedItems.get(i).getChapterId())) {
                return i;
            }
        }
        return -1;
    }

    public int getMainCount() {
        int count = 0;
        for (int i = 0; i < allItems.size(); i++) {
            if (!allItems.get(i).isExtra()) count++;
        }
        return count;
    }

    public int getExtraCount() {
        int count = 0;
        for (int i = 0; i < allItems.size(); i++) {
            if (allItems.get(i).isExtra()) count++;
        }
        return count;
    }

    public int getTotalCount() {
        return allItems.size();
    }

    @Override
    public TocViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return TocViewHolder.create(parent);
    }

    @Override
    public void onBindViewHolder(TocViewHolder holder, int position) {
        if (position >= 0 && position < displayedItems.size()) {
            holder.bind(displayedItems.get(position), colors, itemClickListener);
        }
    }

    @Override
    public int getItemCount() {
        return displayedItems.size();
    }
}
