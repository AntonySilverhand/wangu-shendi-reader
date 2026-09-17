package org.wanshu.reader.ui.bookmarks;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.repository.PersonalRepository;
import org.wanshu.reader.navigation.AppNavigator;
import org.wanshu.reader.ui.theme.ThemeColors;

public class BookmarksDialog extends Dialog {
    private final String bookId;
    private final ReadingAnchor currentAnchor;
    private final String currentSnippet;
    private final PersonalRepository personalRepository;
    private final AppNavigator navigator;
    private final AppExecutors executors;
    private final ThemeColors colors;

    private BookmarksAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private RecyclerView recyclerView;

    public BookmarksDialog(
            Context context,
            String bookId,
            ReadingAnchor currentAnchor,
            String currentSnippet,
            PersonalRepository personalRepository,
            AppNavigator navigator,
            AppExecutors executors,
            ThemeColors colors
    ) {
        super(context);
        this.bookId = bookId != null ? bookId : "";
        this.currentAnchor = currentAnchor;
        this.currentSnippet = currentSnippet != null ? currentSnippet : "";
        this.personalRepository = personalRepository;
        this.navigator = navigator;
        this.executors = executors;
        this.colors = colors;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Context ctx = getContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int bg = colors != null ? colors.background : Color.WHITE;
        int barBg = colors != null ? colors.barBackground : Color.parseColor("#F5F5F5");
        int textCol = colors != null ? colors.text : Color.BLACK;
        int secTextCol = colors != null ? colors.secondaryText : Color.GRAY;

        root.setBackgroundColor(bg);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 1. Header Bar
        LinearLayout headerBar = new LinearLayout(ctx);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding(24, 16, 16, 16);
        headerBar.setBackgroundColor(barBg);

        TextView titleView = new TextView(ctx);
        titleView.setText("书签");
        titleView.setTextSize(18f);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(textCol);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        headerBar.addView(titleView, titleParams);

        Button closeButton = new Button(ctx);
        closeButton.setText("✕");
        closeButton.setOnClickListener(new BookmarksCloseClickListener(this));
        headerBar.addView(closeButton);
        root.addView(headerBar);

        // 2. Action Row
        LinearLayout actionRow = new LinearLayout(ctx);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(24, 12, 16, 12);
        actionRow.setBackgroundColor(barBg);

        Button addCurrentBtn = new Button(ctx);
        addCurrentBtn.setText("＋ 添加当前书签");
        addCurrentBtn.setOnClickListener(new BookmarksAddCurrentClickListener(this));
        actionRow.addView(addCurrentBtn);

        Button clearAllBtn = new Button(ctx);
        clearAllBtn.setText("清空全部");
        clearAllBtn.setOnClickListener(new BookmarksClearClickListener(this));
        actionRow.addView(clearAllBtn);
        root.addView(actionRow);

        // 3. Progress Bar
        progressBar = new ProgressBar(ctx);
        root.addView(progressBar);

        // 4. Empty View
        emptyView = new TextView(ctx);
        emptyView.setText("暂无书签\n点击上方按钮添加当前阅读位置");
        emptyView.setTextSize(15f);
        emptyView.setTextColor(secTextCol);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(32, 64, 32, 64);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView);

        // 5. RecyclerView
        recyclerView = new RecyclerView(ctx);
        recyclerView.setLayoutManager(new LinearLayoutManager(ctx));
        recyclerView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));
        adapter = new BookmarksAdapter(this);
        adapter.setColors(colors);
        recyclerView.setAdapter(adapter);
        root.addView(recyclerView);

        setContentView(root);

        loadBookmarks();
    }

    public void loadBookmarks() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        personalRepository.getBookmarks(
                bookId,
                new BookmarksLoadedCallback(this, executors.getMainThreadExecutor())
        );
    }

    public void onBookmarksLoaded(List<BookmarkEntity> list) {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (adapter != null) {
            adapter.setData(list);
            updateEmptyState();
        }
    }

    public void addCurrentBookmark() {
        if (currentAnchor == null) return;
        personalRepository.addBookmark(
                currentAnchor,
                currentSnippet,
                new BookmarkAddedCallback(this, executors.getMainThreadExecutor())
        );
    }

    public void onBookmarkAdded(BookmarkEntity entity) {
        loadBookmarks();
    }

    public void onDeleteBookmarkClicked(BookmarkEntity bookmark) {
        if (bookmark == null) return;
        personalRepository.deleteBookmark(
                bookmark.id,
                new BookmarkDeletedCallback(this, bookmark.id, executors.getMainThreadExecutor())
        );
    }

    public void onBookmarkDeleted(String bookmarkId) {
        if (adapter != null) {
            adapter.removeById(bookmarkId);
            updateEmptyState();
        }
    }

    public void clearAllBookmarks() {
        personalRepository.clearBookmarks(
                bookId,
                new BookmarksClearedCallback(this, executors.getMainThreadExecutor())
        );
    }

    public void onBookmarksCleared() {
        if (adapter != null) {
            adapter.clear();
            updateEmptyState();
        }
    }

    public void onBookmarkClicked(BookmarkEntity bookmark) {
        if (bookmark != null && navigator != null) {
            navigator.openReaderWithAnchor(
                    bookmark.bookId,
                    bookmark.chapterId,
                    bookmark.paragraphIndex,
                    bookmark.offsetUtf16
            );
            dismiss();
        }
    }

    private void updateEmptyState() {
        if (adapter != null && emptyView != null) {
            boolean empty = adapter.getCount() == 0;
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (recyclerView != null) {
                recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
            }
        }
    }
}
