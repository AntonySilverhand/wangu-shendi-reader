package org.wanshu.reader.ui.reader;

import java.util.List;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.core.text.TextBlock;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.personal.entity.LastRouteEntity;
import org.wanshu.reader.data.personal.entity.SettingsEntity;
import org.wanshu.reader.data.repository.PersonalRepository;
import org.wanshu.reader.data.repository.ReaderRepository;
import org.wanshu.reader.navigation.AppNavigator;
import org.wanshu.reader.navigation.BookRoute;
import org.wanshu.reader.navigation.ReaderAnchorSaver;
import org.wanshu.reader.ui.settings.SettingsChangeListener;
import org.wanshu.reader.ui.theme.ReaderThemeConfig;
import org.wanshu.reader.ui.theme.ThemeColors;

public class ReaderController implements ReaderAnchorSaver, SettingsChangeListener {
    private final ReaderView view;
    private final ReaderRepository readerRepository;
    private final PersonalRepository personalRepository;
    private final AppNavigator navigator;
    private final AppExecutors executors;
    private final TextBlockBuilder textBlockBuilder;
    private final org.wanshu.reader.download.DownloadCoordinator downloadCoordinator;

    private String currentBookId = "";
    private String currentChapterId = "";
    private long currentGeneration = 0L;
    private String currentPrevChapterId = null;
    private String currentNextChapterId = null;
    private int targetParagraph = -1;
    private boolean hasShownContent = false;
    private ReadingAnchor currentAnchor = null;

    private List<String> currentParagraphs = new java.util.ArrayList<String>();
    private List<TextBlock> currentBlocks = new java.util.ArrayList<TextBlock>();
    private List<org.wanshu.reader.core.search.ChapterSearchMatch> searchMatches =
            new java.util.ArrayList<org.wanshu.reader.core.search.ChapterSearchMatch>();
    private int currentMatchIndex = -1;
    private String currentSearchQuery = "";
    private ThemeColors currentThemeColors = ReaderThemeConfig.getThemeColors(ReaderThemeConfig.THEME_LIGHT);

    public ReaderController(
            ReaderView view,
            ReaderRepository readerRepository,
            PersonalRepository personalRepository,
            AppNavigator navigator,
            AppExecutors executors,
            TextBlockBuilder textBlockBuilder,
            org.wanshu.reader.download.DownloadCoordinator downloadCoordinator
    ) {
        this.view = view;
        this.readerRepository = readerRepository;
        this.personalRepository = personalRepository;
        this.navigator = navigator;
        this.executors = executors;
        this.textBlockBuilder = textBlockBuilder != null ? textBlockBuilder : new TextBlockBuilder(4000);
        this.downloadCoordinator = downloadCoordinator;

        this.view.setBackClickListener(new ReaderBackClickListener(this));
        this.view.setNavigationListeners(
                new ReaderPrevClickListener(this),
                new ReaderBackClickListener(this),
                new ReaderNextClickListener(this)
        );
        this.view.setRetryClickListener(new ReaderRetryClickListener(this));
        this.view.setAnchorCallback(new ReaderAnchorCallback(this));

        this.view.setSearchToggleClickListener(new ReaderSearchToggleClickListener(this));
        this.view.setSearchListeners(
                new ReaderSearchPrevClickListener(this),
                new ReaderSearchNextClickListener(this),
                new ReaderSearchCloseClickListener(this)
        );
        this.view.getSearchEditText().addTextChangedListener(new ReaderSearchTextWatcher(this));
        this.view.getSearchEditText().setOnEditorActionListener(new ReaderSearchActionListener(this));
        this.view.getSearchEditText().setOnKeyListener(new ReaderSearchKeyListener(this));
        this.view.setTocClickListener(new ReaderTocClickListener(this));
        this.view.setBookmarkClickListener(new ReaderBookmarkClickListener(this));
        this.view.setSettingsClickListener(new ReaderSettingsClickListener(this));
        this.view.setDownloadStatusClickListener(new ReaderDownloadStatusClickListener(this));
        if (this.downloadCoordinator != null) {
            this.downloadCoordinator.addListener(new ReaderDownloadProgressListener(this));
        }
    }

    public ReaderController(
            ReaderView view,
            ReaderRepository readerRepository,
            PersonalRepository personalRepository,
            AppNavigator navigator,
            AppExecutors executors,
            TextBlockBuilder textBlockBuilder
    ) {
        this(view, readerRepository, personalRepository, navigator, executors, textBlockBuilder, null);
    }

    public void open(BookRoute route) {
        if (route == null) return;

        this.currentBookId = route.getBookId();
        this.currentChapterId = route.getChapterId();
        this.currentGeneration = route.getGeneration();
        this.targetParagraph = route.getTargetParagraph();
        this.currentPrevChapterId = null;
        this.currentNextChapterId = null;
        this.hasShownContent = false;
        this.currentAnchor = null;

        closeSearch();

        view.setChapterIdentity(currentBookId, currentChapterId);
        executors.getMainThreadExecutor().execute(new ReaderShowLoadingRunnable(view));

        // Load personal typography settings
        personalRepository.getSettings(new ReaderSettingsLoadedCallback(this));

        if (downloadCoordinator != null) {
            downloadCoordinator.onReadingChapterChanged(currentBookId, currentChapterId, "");
        }

        readerRepository.observe(
                currentBookId,
                currentChapterId,
                "reader-ui-" + currentGeneration,
                new ReaderChapterObserver(this, currentGeneration)
        );
    }

    public void applySettings(SettingsEntity settings) {
        if (settings == null) return;

        ThemeColors colors = ReaderThemeConfig.getThemeColors(settings.theme);
        this.currentThemeColors = colors;
        executors.getMainThreadExecutor().execute(new ReaderApplyTypographyRunnable(
                view,
                colors,
                settings.fontSize,
                settings.lineHeight,
                settings.pageMargin
        ));

        if (view.getContext() instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) view.getContext();
            if (settings.keepScreenAwake) {
                activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    }

    public void onChapterLoaded(ChapterResult result, long generation, boolean isComplete) {
        // Generation protection: discard late responses if generation changed
        if (generation != currentGeneration || !navigator.isCurrentGeneration(generation)) {
            return;
        }

        if (result != null) {
            hasShownContent = true;
            currentPrevChapterId = result.getPrevChapterId();
            currentNextChapterId = result.getNextChapterId();

            this.currentParagraphs = result.getParagraphs() != null ? result.getParagraphs() : new java.util.ArrayList<String>();
            List<TextBlock> blocks = textBlockBuilder.buildBlocks(result.getParagraphs());
            this.currentBlocks = blocks != null ? blocks : new java.util.ArrayList<TextBlock>();

            executors.getMainThreadExecutor().execute(new ReaderShowContentRunnable(
                    view,
                    result.getTitle(),
                    blocks,
                    currentPrevChapterId != null,
                    currentNextChapterId != null,
                    targetParagraph
            ));

            if (!currentSearchQuery.isEmpty()) {
                onSearchQueryChanged(currentSearchQuery);
            }

            if (isComplete) {
                personalRepository.recordReadingHistory(
                        currentBookId,
                        currentChapterId,
                        result.getTitle(),
                        null
                );
            }

            if (downloadCoordinator != null) {
                downloadCoordinator.onReadingChapterChanged(currentBookId, currentChapterId, result.getTitle());
            }
        }
    }

    public void onChapterLoadFailed(Throwable error, long generation) {
        // Generation protection
        if (generation != currentGeneration || !navigator.isCurrentGeneration(generation)) {
            return;
        }

        if (!hasShownContent) {
            String msg = error != null ? error.getMessage() : "加载失败";
            executors.getMainThreadExecutor().execute(new ReaderShowErrorRunnable(view, msg));
        }
    }

    public void onAnchorSampled(ReadingAnchor anchor) {
        if (anchor != null && anchor.getBookId().equals(currentBookId) && anchor.getChapterId().equals(currentChapterId)) {
            this.currentAnchor = anchor;
            personalRepository.saveReadingProgress(anchor, null);
        }
    }

    @Override
    public void saveCurrentAnchor() {
        if (currentBookId != null && !currentBookId.isEmpty() && currentChapterId != null && !currentChapterId.isEmpty()) {
            ReadingAnchor anchor = currentAnchor;
            if (anchor == null || !anchor.getBookId().equals(currentBookId) || !anchor.getChapterId().equals(currentChapterId)) {
                ReadingAnchor sample = view.getAnchorSampler().sampleAnchor();
                if (sample != null) {
                    anchor = sample;
                } else {
                    anchor = new ReadingAnchor(currentBookId, currentChapterId, 0, 0, System.currentTimeMillis());
                }
            }

            personalRepository.saveReadingProgress(anchor, null);

            LastRouteEntity lr = new LastRouteEntity();
            lr.routeType = "READER";
            lr.bookId = currentBookId;
            lr.chapterId = currentChapterId;
            lr.updatedAt = System.currentTimeMillis();
            personalRepository.saveLastRoute(lr, null);
        }
    }

    public void onBackToShelfClicked() {
        saveCurrentAnchor();

        LastRouteEntity lr = new LastRouteEntity();
        lr.routeType = "SHELF";
        lr.bookId = "";
        lr.chapterId = "";
        lr.updatedAt = System.currentTimeMillis();
        personalRepository.saveLastRoute(lr, null);

        navigator.openShelf();
    }

    public void onPrevChapterClicked() {
        if (currentPrevChapterId != null) {
            saveCurrentAnchor();
            navigator.openReader(currentBookId, currentPrevChapterId);
        }
    }

    public void onNextChapterClicked() {
        if (currentNextChapterId != null) {
            saveCurrentAnchor();
            navigator.openReader(currentBookId, currentNextChapterId);
        }
    }

    public void onRetryClicked() {
        navigator.openReader(currentBookId, currentChapterId);
    }

    public void toggleSearch() {
        if (view.isSearchBarVisible()) {
            closeSearch();
        } else {
            view.showSearchBar();
        }
    }

    public void closeSearch() {
        currentSearchQuery = "";
        searchMatches.clear();
        currentMatchIndex = -1;
        view.hideSearchBar();
        view.getAdapter().clearHighlights();
    }

    public void onSearchQueryChanged(String query) {
        this.currentSearchQuery = query != null ? query : "";
        if (currentSearchQuery.trim().isEmpty()) {
            searchMatches.clear();
            currentMatchIndex = -1;
            view.setSearchCount(0, 0);
            view.getAdapter().clearHighlights();
            return;
        }

        this.searchMatches = org.wanshu.reader.core.search.ChapterSearchEngine.search(currentParagraphs, currentSearchQuery);
        if (searchMatches.isEmpty()) {
            currentMatchIndex = -1;
            view.setSearchCount(0, 0);
            view.getAdapter().clearHighlights();
        } else {
            currentMatchIndex = 0;
            view.setSearchCount(1, searchMatches.size());
            jumpToMatch(0);
        }
    }

    public void nextSearchMatch() {
        if (searchMatches.isEmpty()) return;
        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size();
        view.setSearchCount(currentMatchIndex + 1, searchMatches.size());
        jumpToMatch(currentMatchIndex);
    }

    public void prevSearchMatch() {
        if (searchMatches.isEmpty()) return;
        currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size()) % searchMatches.size();
        view.setSearchCount(currentMatchIndex + 1, searchMatches.size());
        jumpToMatch(currentMatchIndex);
    }

    private void jumpToMatch(int index) {
        if (index < 0 || index >= searchMatches.size()) return;
        org.wanshu.reader.core.search.ChapterSearchMatch m = searchMatches.get(index);
        view.getAdapter().setSearchHighlights(
                currentSearchQuery,
                m.getParagraphIndex(),
                m.getStartOffset(),
                m.getEndOffset()
        );
        if (currentBlocks != null && !currentBlocks.isEmpty()) {
            int blockIdx = org.wanshu.reader.core.text.AnchorMapper.findBlockIndexForParagraph(currentBlocks, m.getParagraphIndex());
            int targetPos = blockIdx + 1; // +1 for header
            view.getRecyclerView().scrollToPosition(targetPos);
        }
    }

    public void openToc() {
        saveCurrentAnchor();
        org.wanshu.reader.ui.toc.TocDialog dialog = new org.wanshu.reader.ui.toc.TocDialog(
                view.getContext(),
                currentBookId,
                currentChapterId,
                readerRepository.getContentRepository().getDatabase(),
                readerRepository.getHttpClient(),
                navigator,
                executors,
                currentThemeColors
        );
        dialog.show();
    }

    public void openBookmarks() {
        saveCurrentAnchor();
        ReadingAnchor anchor = currentAnchor;
        if (anchor == null || !anchor.getBookId().equals(currentBookId) || !anchor.getChapterId().equals(currentChapterId)) {
            ReadingAnchor sample = view.getAnchorSampler().sampleAnchor();
            if (sample != null) {
                anchor = sample;
            } else {
                anchor = new ReadingAnchor(currentBookId, currentChapterId, 0, 0, System.currentTimeMillis());
            }
        }

        String snippet = "";
        if (currentParagraphs != null && anchor.getParagraphIndex() >= 0 && anchor.getParagraphIndex() < currentParagraphs.size()) {
            String p = currentParagraphs.get(anchor.getParagraphIndex());
            if (p != null) {
                snippet = p.length() > 60 ? p.substring(0, 60) + "..." : p;
            }
        }

        org.wanshu.reader.ui.bookmarks.BookmarksDialog dialog = new org.wanshu.reader.ui.bookmarks.BookmarksDialog(
                view.getContext(),
                currentBookId,
                anchor,
                snippet,
                personalRepository,
                navigator,
                executors,
                currentThemeColors
        );
        dialog.show();
    }

    public void openSettings() {
        saveCurrentAnchor();
        org.wanshu.reader.ui.settings.SettingsDialog dialog = new org.wanshu.reader.ui.settings.SettingsDialog(
                view.getContext(),
                currentBookId,
                currentChapterId,
                personalRepository,
                readerRepository.getContentRepository(),
                downloadCoordinator,
                executors,
                this,
                currentThemeColors
        );
        dialog.show();
    }

    public void openDownloads() {
        saveCurrentAnchor();
        if (downloadCoordinator != null) {
            org.wanshu.reader.ui.download.DownloadsDialog dialog = new org.wanshu.reader.ui.download.DownloadsDialog(
                    view.getContext(),
                    currentBookId,
                    currentChapterId,
                    downloadCoordinator,
                    currentThemeColors
            );
            dialog.show();
        }
    }

    public void onDownloadProgress(org.wanshu.reader.download.DownloadProgress progress) {
        if (progress == null) return;
        if (!progress.getBookId().equals(currentBookId)) return;
        executors.getMainThreadExecutor().execute(new ReaderDownloadStatusRunnable(view, progress.getSummaryText()));
    }

    @Override
    public void onSettingsChanged(SettingsEntity settings) {
        applySettings(settings);
    }
}
