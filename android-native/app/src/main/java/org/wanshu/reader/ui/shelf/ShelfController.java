package org.wanshu.reader.ui.shelf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.net.Uri;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.AppContainer;
import org.wanshu.reader.core.model.BookKey;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.core.source.SourceUrls;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.BookEntity;
import org.wanshu.reader.data.content.entity.TocEntryEntity;
import org.wanshu.reader.data.repository.CompletionCallback;
import org.wanshu.reader.data.repository.ContentRepository;
import org.wanshu.reader.data.repository.DataCallback;
import org.wanshu.reader.data.repository.PersonalRepository;
import org.wanshu.reader.data.txt.TxtImportTaskRunnable;
import org.wanshu.reader.navigation.AppNavigator;
import org.wanshu.reader.navigation.DialogDismissHandler;
import org.wanshu.reader.ui.search.BookSearchDialog;
import org.wanshu.reader.ui.search.BookSearchHost;

public class ShelfController implements DialogDismissHandler, BookSearchHost {
    public static final int REQUEST_CODE_IMPORT_TXT = 1001;
    /** Where the default book starts, so it still opens without a loaded TOC (and offline). */
    private static final String DEFAULT_BOOK_FIRST_CHAPTER_ID = "8924760";
    private static final String PREFS_NAME = "shelf";

    private final Activity activity;
    private final ShelfView view;
    private final ContentRepository contentRepository;
    private final PersonalRepository personalRepository;
    private final AppNavigator navigator;
    private final AppExecutors executors;
    private final TextBlockBuilder textBlockBuilder;
    private final ContentDatabase contentDb;
    private final AppContainer container;

    private Dialog activeDialog;
    private List<ShelfItemModel> pendingItems = new ArrayList<ShelfItemModel>();
    private int loadGeneration;
    private int outstandingItems;

    public ShelfController(
            Activity activity,
            ShelfView view,
            ContentRepository contentRepository,
            PersonalRepository personalRepository,
            AppNavigator navigator,
            AppExecutors executors,
            TextBlockBuilder textBlockBuilder,
            ContentDatabase contentDb,
            AppContainer container
    ) {
        this.activity = activity;
        this.view = view;
        this.contentRepository = contentRepository;
        this.personalRepository = personalRepository;
        this.navigator = navigator;
        this.executors = executors;
        this.textBlockBuilder = textBlockBuilder;
        this.contentDb = contentDb;
        this.container = container;

        this.view.setImportClickListener(new ShelfImportClickListener(this));
        this.view.setSearchClickListener(new ShelfSearchClickListener(this));
        this.view.setCoverLoader(container.getCoverLoader());
    }

    public void refresh() {
        contentRepository.seedDefaultBook(
                activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE),
                new ShelfEnsureBookCallback(this));
    }

    public void onDefaultBookEnsured() {
        contentRepository.getAllBooks(new ShelfBooksLoadedCallback(this));
    }

    public synchronized void onBooksLoaded(List<BookEntity> books) {
        loadGeneration++;
        if (books == null || books.isEmpty()) {
            pendingItems = new ArrayList<ShelfItemModel>();
            outstandingItems = 0;
            postDisplayBooks(pendingItems);
            return;
        }

        final List<ShelfItemModel> items = new ArrayList<ShelfItemModel>();
        for (int i = 0; i < books.size(); i++) {
            items.add(new ShelfItemModel(books.get(i), null));
        }
        this.pendingItems = items;
        this.outstandingItems = items.size();

        final int generation = loadGeneration;
        for (int i = 0; i < items.size(); i++) {
            final ShelfItemModel item = items.get(i);
            personalRepository.getReadingProgress(item.getBook().bookId, new ShelfAnchorLoadedCallback(this, item.getBook().bookId, generation));
        }
    }

    public void onAnchorLoaded(String bookId, ReadingAnchor anchor, int generation) {
        boolean hasChapter = anchor != null && anchor.getChapterId() != null && !anchor.getChapterId().isEmpty();
        synchronized (this) {
            if (generation != loadGeneration) return;
            ShelfItemModel item = findItem(bookId);
            if (item == null) return;
            if (anchor != null) item.setAnchor(anchor);
        }
        if (hasChapter) {
            contentRepository.getChapterTitle(bookId, anchor.getChapterId(), new ShelfTitleLoadedCallback(this, bookId, generation));
        } else {
            onItemReady(generation);
        }
    }

    public void onChapterTitleLoaded(String bookId, String title, int generation) {
        synchronized (this) {
            if (generation != loadGeneration) return;
            ShelfItemModel item = findItem(bookId);
            if (item != null && title != null) item.setLastChapterTitle(title);
        }
        onItemReady(generation);
    }

    private ShelfItemModel findItem(String bookId) {
        for (int i = 0; i < pendingItems.size(); i++) {
            if (pendingItems.get(i).getBook().bookId.equals(bookId)) return pendingItems.get(i);
        }
        return null;
    }

    private void onItemReady(int generation) {
        List<ShelfItemModel> toShow = null;
        synchronized (this) {
            if (generation != loadGeneration) return;
            outstandingItems--;
            if (outstandingItems == 0) toShow = pendingItems;
        }
        if (toShow != null) postDisplayBooks(toShow);
    }

    public void onBooksLoadFailed(Throwable error) {
        postDisplayBooks(new ArrayList<ShelfItemModel>());
    }

    private void postDisplayBooks(List<ShelfItemModel> items) {
        executors.getMainThreadExecutor().execute(new ShelfDisplayBooksRunnable(view, items, this));
    }

    private void postMessage(String message) {
        executors.getMainThreadExecutor().execute(new ShelfMessageRunnable(activity, message));
    }

    public void onContinueClicked(String bookId, ReadingAnchor anchor) {
        if (anchor != null && anchor.getChapterId() != null && !anchor.getChapterId().isEmpty()) {
            navigator.openReaderWithAnchor(bookId, anchor.getChapterId(), anchor.getParagraphIndex(), anchor.getOffsetUtf16());
        } else {
            onStartClicked(bookId);
        }
    }

    public void onStartClicked(String bookId) {
        if (!BookKey.isOnlineId(bookId)) {
            navigator.openReader(bookId, "1");
            return;
        }
        startOnlineBook(bookId, null);
    }

    /** Opens the first chapter of an online book: the given id, else the first TOC entry (loading TOC page 1 if needed). */
    public void startOnlineBook(String bookId, String knownFirstChapterId) {
        if (knownFirstChapterId != null && !knownFirstChapterId.isEmpty()) {
            navigator.openReader(bookId, knownFirstChapterId);
            return;
        }
        contentRepository.getTocEntries(bookId, new ShelfFirstEntryCallback(this, bookId, false));
    }

    public void onFirstEntriesLoaded(String bookId, List<TocEntryEntity> entries, boolean afterTocLoad) {
        if (entries != null && !entries.isEmpty()) {
            navigator.openReader(bookId, entries.get(0).chapterId);
            return;
        }
        if (SourceUrls.DEFAULT_BOOK_ID.equals(bookId)) {
            navigator.openReader(bookId, DEFAULT_BOOK_FIRST_CHAPTER_ID);
            return;
        }
        if (afterTocLoad || contentRepository.getTocLoader() == null) {
            postMessage("没有取得目录，暂时无法从头阅读，请稍后重试");
            return;
        }
        postMessage("正在获取目录…");
        contentRepository.getTocLoader().loadFirstPage(bookId, "shelf-start:" + bookId, new ShelfStartTocListener(this, bookId));
    }

    public void onStartTocFinished(String bookId, String error) {
        if (error != null) {
            postMessage("获取目录失败：" + error);
            return;
        }
        contentRepository.getTocEntries(bookId, new ShelfFirstEntryCallback(this, bookId, true));
    }

    public void onStartFailed(Throwable error) {
        postMessage("无法开始阅读：" + (error != null && error.getMessage() != null ? error.getMessage() : "未知错误"));
    }

    public void onDeleteClicked(String bookId, String bookTitle) {
        dismissActiveDialog();

        boolean online = BookKey.isOnlineId(bookId);
        String title = online ? "从书架移除" : "删除本地书籍";
        String message = online
                ? "确定从书架移除《" + bookTitle + "》并删除其缓存吗？个人书签和阅读记录将保留，之后可通过搜索再次添加。"
                : "确定删除《" + bookTitle + "》及其缓存吗？个人书签和阅读记录将保留。";
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(online ? "移除" : "删除", new ShelfDeleteConfirmClickListener(this, bookId))
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

    public void onSearchClicked() {
        dismissActiveDialog();
        BookSearchDialog dialog = new BookSearchDialog(
                activity,
                this,
                container.getScheduler(),
                container.getHttpClient(),
                container.getSearchGate(),
                container.getCoverLoader(),
                executors
        );
        this.activeDialog = dialog;
        dialog.show();
    }

    @Override
    public void addBook(String bookId, String title, String author, CompletionCallback callback) {
        contentRepository.ensureOnlineBook(bookId, title, author, new ShelfAddBookCallback(this, callback));
    }

    @Override
    public void startReading(String bookId, String title, String author, String firstChapterId) {
        dismissActiveDialog();
        contentRepository.ensureOnlineBook(bookId, title, author, new ShelfStartAfterAddCallback(this, bookId, firstChapterId));
    }

    @Override
    public void loadShelfBooks(DataCallback<List<BookEntity>> callback) {
        contentRepository.getAllBooks(callback);
    }

    public void onImportClicked() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        try {
            activity.startActivityForResult(intent, REQUEST_CODE_IMPORT_TXT);
        } catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(activity, "没有可用的文件选择器", Toast.LENGTH_LONG).show();
        }
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
