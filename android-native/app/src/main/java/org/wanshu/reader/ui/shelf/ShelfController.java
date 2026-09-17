package org.wanshu.reader.ui.shelf;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.BookEntity;
import org.wanshu.reader.data.repository.ContentRepository;
import org.wanshu.reader.data.repository.PersonalRepository;
import org.wanshu.reader.data.txt.TxtImportTaskRunnable;
import org.wanshu.reader.navigation.AppNavigator;
import org.wanshu.reader.navigation.DialogDismissHandler;

public class ShelfController implements DialogDismissHandler {
    public static final int REQUEST_CODE_IMPORT_TXT = 1001;

    private final Activity activity;
    private final ShelfView view;
    private final ContentRepository contentRepository;
    private final PersonalRepository personalRepository;
    private final AppNavigator navigator;
    private final AppExecutors executors;
    private final TextBlockBuilder textBlockBuilder;
    private final ContentDatabase contentDb;

    private AlertDialog activeDialog;
    private List<ShelfItemModel> pendingItems = new ArrayList<ShelfItemModel>();

    public ShelfController(
            Activity activity,
            ShelfView view,
            ContentRepository contentRepository,
            PersonalRepository personalRepository,
            AppNavigator navigator,
            AppExecutors executors,
            TextBlockBuilder textBlockBuilder,
            ContentDatabase contentDb
    ) {
        this.activity = activity;
        this.view = view;
        this.contentRepository = contentRepository;
        this.personalRepository = personalRepository;
        this.navigator = navigator;
        this.executors = executors;
        this.textBlockBuilder = textBlockBuilder;
        this.contentDb = contentDb;

        this.view.setImportClickListener(new ShelfImportClickListener(this));
    }

    public void refresh() {
        contentRepository.ensureOnlineBook("36780", "万古神帝", "飞天鱼", new ShelfEnsureBookCallback(this));
    }

    public void onDefaultBookEnsured() {
        contentRepository.getAllBooks(new ShelfBooksLoadedCallback(this));
    }

    public void onBooksLoaded(List<BookEntity> books) {
        if (books == null || books.isEmpty()) {
            postDisplayBooks(new ArrayList<ShelfItemModel>());
            return;
        }

        final List<ShelfItemModel> items = new ArrayList<ShelfItemModel>();
        for (int i = 0; i < books.size(); i++) {
            items.add(new ShelfItemModel(books.get(i), null));
        }
        this.pendingItems = items;

        final AtomicInteger remaining = new AtomicInteger(items.size());
        for (int i = 0; i < items.size(); i++) {
            final ShelfItemModel item = items.get(i);
            personalRepository.getReadingProgress(item.getBook().bookId, new ShelfAnchorLoadedCallback(this, item.getBook().bookId));
        }
    }

    public void onAnchorLoaded(String bookId, ReadingAnchor anchor) {
        if (anchor != null && pendingItems != null) {
            for (int i = 0; i < pendingItems.size(); i++) {
                ShelfItemModel item = pendingItems.get(i);
                if (item.getBook().bookId.equals(bookId)) {
                    item.setAnchor(anchor);
                    break;
                }
            }
        }
        postDisplayBooks(pendingItems);
    }

    public void onBooksLoadFailed(Throwable error) {
        postDisplayBooks(new ArrayList<ShelfItemModel>());
    }

    private void postDisplayBooks(List<ShelfItemModel> items) {
        executors.getMainThreadExecutor().execute(new ShelfDisplayBooksRunnable(view, items, this));
    }

    public void onContinueClicked(String bookId, ReadingAnchor anchor) {
        if (anchor != null && anchor.getChapterId() != null && !anchor.getChapterId().isEmpty()) {
            navigator.openReaderWithAnchor(bookId, anchor.getChapterId(), anchor.getParagraphIndex(), anchor.getOffsetUtf16());
        } else {
            navigator.openReader(bookId, "1");
        }
    }

    public void onStartClicked(String bookId) {
        navigator.openReader(bookId, "1");
    }

    public void onDeleteClicked(String bookId, String bookTitle) {
        dismissActiveDialog();

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("删除本地书籍")
                .setMessage("确定删除《" + bookTitle + "》及其缓存吗？个人书签和阅读记录将保留。")
                .setPositiveButton("删除", new ShelfDeleteConfirmClickListener(this, bookId))
                .setNegativeButton("取消", null)
                .create();

        this.activeDialog = dialog;
        dialog.show();
    }

    public void confirmDeleteBook(String bookId) {
        dismissActiveDialog();
        contentRepository.deleteLocalBook(bookId, new ShelfDeleteBookCallback(this));
    }

    public void onBookDeleted() {
        refresh();
    }

    public void onBookDeleteFailed(Throwable error) {
        Toast.makeText(activity, "删除失败: " + (error != null ? error.getMessage() : "未知错误"), Toast.LENGTH_SHORT).show();
    }

    public void onImportClicked() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT_TXT);
    }

    public void handleImportUri(Uri uri, String bookTitle) {
        executors.getMainThreadExecutor().execute(new ShelfImportProgressRunnable(view, "正在导入 TXT..."));
        executors.getCpuExecutor().execute(new TxtImportTaskRunnable(
                activity,
                uri,
                bookTitle,
                contentDb,
                textBlockBuilder,
                new ShelfImportProgressListener(this)
        ));
    }

    public void onImportProgress(int chaptersCount, long totalBytes) {
        executors.getMainThreadExecutor().execute(
                new ShelfImportProgressRunnable(view, "已导入 " + chaptersCount + " 章...")
        );
    }

    public void onImportSuccess(String bookId, int chaptersCount, long totalBytes) {
        executors.getMainThreadExecutor().execute(new ShelfImportProgressRunnable(view, null));
        executors.getMainThreadExecutor().execute(new ShelfImportSuccessRunnable(activity, this, chaptersCount));
    }

    public void onImportError(Throwable error) {
        executors.getMainThreadExecutor().execute(new ShelfImportProgressRunnable(view, null));
        executors.getMainThreadExecutor().execute(new ShelfImportErrorRunnable(activity, error));
    }

    @Override
    public boolean dismissActiveDialog() {
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
            activeDialog = null;
            return true;
        }
        return false;
    }
}
