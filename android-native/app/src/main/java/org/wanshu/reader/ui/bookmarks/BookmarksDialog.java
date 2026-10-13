package org.wanshu.reader.ui.bookmarks;

import android.app.Dialog;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.repository.PersonalRepository;
import org.wanshu.reader.navigation.AppNavigator;
import org.wanshu.reader.navigation.DialogDismissHandler;
import org.wanshu.reader.ui.reader.ReaderController;
import org.wanshu.reader.ui.theme.ThemeColors;

public class BookmarksDialog extends Dialog implements DialogDismissHandler, BookmarkItemClickListener, View.OnClickListener, DialogInterface.OnClickListener {
    private final String bookId;
    private final PersonalRepository personalRepository;
    private final AppNavigator navigator;
    private final AppExecutors executors;
    private final ThemeColors colors;
    private final ReaderController readerController;

    private TextView titleView;
    private Button addCurrentButton;
    private Button closeButton;
    private Button clearButton;
    private AlertDialog clearConfirmation;
    private boolean saving;
    private ProgressBar progressBar;
    private TextView emptyMsgView;
    private RecyclerView recyclerView;
    private BookmarksAdapter adapter;

    public BookmarksDialog(
            Context context,
            String bookId,
            PersonalRepository personalRepository,
            AppNavigator navigator,
            AppExecutors executors,
            ThemeColors colors,
            ReaderController readerController
    ) {
        super(context);
        this.bookId = bookId != null ? bookId : "";
        this.personalRepository = personalRepository;
        this.navigator = navigator;
        this.executors = executors;
        this.colors = colors;
        this.readerController = readerController;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Context ctx = getContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int bgColor = colors != null ? colors.background : Color.WHITE;
        int barBgColor = colors != null ? colors.barBackground : Color.parseColor("#F5F5F5");
        int textColor = colors != null ? colors.text : Color.BLACK;
        root.setBackgroundColor(bgColor);

        // Header Bar
        LinearLayout headerBar = new LinearLayout(ctx);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding(24, 16, 16, 16);
        headerBar.setBackgroundColor(barBgColor);

        titleView = new TextView(ctx);
        titleView.setText("书签");
        titleView.setTextSize(18f);
        titleView.setTextColor(textColor);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        headerBar.addView(titleView, titleParams);

        addCurrentButton = org.wanshu.reader.ui.Ui.button(ctx, "+ 添加当前书签", null);
        addCurrentButton.setOnClickListener(this);

        closeButton = org.wanshu.reader.ui.Ui.button(ctx, "关闭", null);
        closeButton.setOnClickListener(this);
        headerBar.addView(closeButton);

        root.addView(headerBar);
        org.wanshu.reader.ui.ActionFlowLayout actions = new org.wanshu.reader.ui.ActionFlowLayout(ctx);
        clearButton = org.wanshu.reader.ui.Ui.button(ctx, "清空本书书签", this);
        actions.addView(addCurrentButton); actions.addView(clearButton); root.addView(actions);
        if (colors != null) org.wanshu.reader.ui.Ui.colors(root, colors);

        // Content Area
        FrameLayout contentArea = new FrameLayout(ctx);
        LinearLayout.LayoutParams areaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        contentArea.setLayoutParams(areaParams);

        progressBar = new ProgressBar(ctx);
        FrameLayout.LayoutParams pbParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        progressBar.setLayoutParams(pbParams);
        contentArea.addView(progressBar);

        emptyMsgView = new TextView(ctx);
        emptyMsgView.setText("暂无书签，点击上方添加");
        emptyMsgView.setTextSize(15f);
        emptyMsgView.setTextColor(Color.parseColor("#888888"));
        emptyMsgView.setVisibility(View.GONE);
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        emptyMsgView.setLayoutParams(emptyParams);
        contentArea.addView(emptyMsgView);

        recyclerView = new RecyclerView(ctx);
        recyclerView.setLayoutManager(new LinearLayoutManager(ctx));
        recyclerView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        adapter = new BookmarksAdapter();
        adapter.setItemClickListener(this);
        recyclerView.setAdapter(adapter);
        contentArea.addView(recyclerView);

        root.addView(contentArea);

        setContentView(root);

        if (getWindow() != null) {
            getWindow().setLayout(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            );
        }

        loadData();
    }

    @Override protected void onStart() {
        super.onStart();
        if (getWindow() != null) getWindow().setLayout(Math.min(getContext().getResources().getDisplayMetrics().widthPixels, org.wanshu.reader.ui.Ui.dp(getContext(), 720)), LinearLayout.LayoutParams.MATCH_PARENT);
    }

    @Override public void dismiss() {
        if (clearConfirmation != null) clearConfirmation.dismiss();
        super.dismiss();
    }

    @Override public void onClick(View view) {
        if (view == closeButton) dismiss();
        else if (!saving && view == addCurrentButton) onAddBookmarkClicked();
        else if (!saving && view == clearButton) {
            clearConfirmation = new AlertDialog.Builder(getContext()).setTitle("清空本书书签？")
                    .setMessage("仅删除这本书的书签，其他书籍、进度和正文不受影响。")
                    .setNegativeButton("取消", null).setPositiveButton("清空", this).show();
        }
    }

    @Override public void onClick(DialogInterface dialog, int which) {
        if (which != DialogInterface.BUTTON_POSITIVE || saving) return;
        setSaving(true);
        personalRepository.clearBookmarks(bookId, new BookmarkDeleteCompletedCallback(this));
    }

    private void setSaving(boolean value) {
        saving = value;
        addCurrentButton.setEnabled(!value);
        clearButton.setEnabled(!value && adapter.getItemCount() > 0);
    }

    public void loadData() {
        setSaving(true);
        progressBar.setVisibility(View.VISIBLE);
        emptyMsgView.setVisibility(View.GONE);
        personalRepository.getBookmarks(bookId, new BookmarksLoadedCallback(this));
    }

    public void onBookmarksLoaded(List<BookmarkEntity> list) {
        executors.getMainThreadExecutor().execute(new BookmarksUpdateListRunnable(this, list));
    }

    public void onBookmarksLoadError(String error) {
        executors.getMainThreadExecutor().execute(new BookmarksErrorRunnable(this, error));
    }

    public void applyBookmarksList(List<BookmarkEntity> list) {
        if (!isShowing()) return;
        progressBar.setVisibility(View.GONE);
        adapter.setData(list, colors);
        setSaving(false);
        if (list == null || list.isEmpty()) {
            emptyMsgView.setText("暂无书签，点击上方添加");
            emptyMsgView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyMsgView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    public void applyError(String error) {
        if (!isShowing()) return;
        setSaving(false);
        progressBar.setVisibility(View.GONE);
        emptyMsgView.setText(error != null ? error : "加载书签失败");
        emptyMsgView.setVisibility(View.VISIBLE);
    }

    public void onAddBookmarkClicked() {
        if (saving) return;
        if (readerController != null) {
            ReadingAnchor anchor = readerController.getCurrentAnchorForBookmark();
            String snippet = readerController.getCurrentSnippetForBookmark();
            if (anchor != null) {
                setSaving(true);
                personalRepository.addBookmark(anchor, snippet, new BookmarkAddCompletedCallback(this));
            } else applyError("正文尚未就绪，请加载后再添加书签");
        }
    }

    public void onBookmarkAdded(BookmarkEntity bookmark) {
        executors.getMainThreadExecutor().execute(new BookmarksReloadRunnable(this));
    }

    public void onBookmarkAddError(String error) {
        executors.getMainThreadExecutor().execute(new BookmarksErrorRunnable(this, error));
    }

    public void onBookmarkDeleted() {
        executors.getMainThreadExecutor().execute(new BookmarksReloadRunnable(this));
    }

    public void onBookmarkDeleteError(String error) {
        executors.getMainThreadExecutor().execute(new BookmarksErrorRunnable(this, error));
    }

    @Override
    public void onBookmarkClicked(BookmarkEntity bookmark) {
        if (bookmark != null) {
            navigator.openReaderWithAnchor(
                    bookId,
                    bookmark.chapterId,
                    bookmark.paragraphIndex,
                    bookmark.offsetUtf16
            );
            dismiss();
        }
    }

    @Override
    public void onBookmarkDeleteClicked(BookmarkEntity bookmark) {
        if (bookmark != null && !saving) {
            setSaving(true);
            personalRepository.deleteBookmark(bookmark.id, new BookmarkDeleteCompletedCallback(this));
        }
    }

    @Override
    public boolean dismissActiveDialog() {
        if (isShowing()) {
            dismiss();
            return true;
        }
        return false;
    }
}
