package org.wanshu.reader.ui.toc;

import android.text.Editable;
import android.text.TextWatcher;
import org.wanshu.reader.core.toc.TocSearchController;

public class TocSearchQueryWatcher implements TextWatcher {
    private final TocSearchController searchController;
    private final TocDialog dialog;

    public TocSearchQueryWatcher(TocSearchController searchController, TocDialog dialog) {
        this.searchController = searchController;
        this.dialog = dialog;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        String q = s != null ? s.toString() : "";
        if (q.trim().isEmpty()) {
            dialog.exitSearchMode();
        }
        if (searchController != null) {
            searchController.setQuery(q);
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
    }
}
