package org.wanshu.reader.ui.toc;

import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.ui.theme.ThemeColors;

public class TocAdapter extends RecyclerView.Adapter<TocItemViewHolder> {
    public static final int FILTER_MAIN = 0;
    public static final int FILTER_EXTRA = 1;
    public static final int FILTER_SEARCH = 2;

    private final TocDialog dialog;
    private final List<TocItemModel> allItems = new ArrayList<TocItemModel>();
    private final List<TocItemModel> displayedItems = new ArrayList<TocItemModel>();
    private int currentFilter = FILTER_MAIN;
    private ThemeColors colors;

    public TocAdapter(TocDialog dialog) {
        this.dialog = dialog;
    }

    public void setColors(ThemeColors colors) {
        this.colors = colors;
        notifyDataSetChanged();
    }

    public void setData(List<TocItemModel> items) {
        allItems.clear();
        if (items != null) {
            allItems.addAll(items);
        }
        applyCurrentFilter();
    }

    public void setFilter(int filterMode) {
        this.currentFilter = filterMode;
        applyCurrentFilter();
    }

    public void setSearchResults(List<TocItemModel> searchResults) {
        this.currentFilter = FILTER_SEARCH;
        displayedItems.clear();
        if (searchResults != null) {
            displayedItems.addAll(searchResults);
        }
        notifyDataSetChanged();
    }

    public void applyCurrentFilter() {
        displayedItems.clear();
        if (currentFilter == FILTER_MAIN) {
            for (int i = 0; i < allItems.size(); i++) {
                TocItemModel item = allItems.get(i);
                if (!item.isExtra()) {
                    displayedItems.add(item);
                }
            }
        } else if (currentFilter == FILTER_EXTRA) {
            for (int i = 0; i < allItems.size(); i++) {
                TocItemModel item = allItems.get(i);
                if (item.isExtra()) {
                    displayedItems.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public int findCurrentPosition() {
        for (int i = 0; i < displayedItems.size(); i++) {
            if (displayedItems.get(i).isCurrent()) {
                return i;
            }
        }
        return -1;
    }

    public int getMainCount() {
        int count = 0;
        for (int i = 0; i < allItems.size(); i++) {
            if (!allItems.get(i).isExtra()) {
                count++;
            }
        }
        return count;
    }

    public int getExtraCount() {
        int count = 0;
        for (int i = 0; i < allItems.size(); i++) {
            if (allItems.get(i).isExtra()) {
                count++;
            }
        }
        return count;
    }

    public List<TocItemModel> getAllItems() {
        return allItems;
    }

    @Override
    public TocItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return TocItemViewHolder.create(parent);
    }

    @Override
    public void onBindViewHolder(TocItemViewHolder holder, int position) {
        if (position >= 0 && position < displayedItems.size()) {
            holder.bind(displayedItems.get(position), colors, dialog);
        }
    }

    @Override
    public int getItemCount() {
        return displayedItems.size();
    }
}
