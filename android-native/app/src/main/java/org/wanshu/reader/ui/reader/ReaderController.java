package org.wanshu.reader.ui.reader;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.core.search.ChapterSearchMatch;
import org.wanshu.reader.core.search.ChapterSearchPage;
import org.wanshu.reader.core.search.ChapterSearchEngine;
import org.wanshu.reader.core.net.CancellationToken;
import org.wanshu.reader.core.text.TextBlock;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.personal.entity.LastRouteEntity;
import org.wanshu.reader.data.personal.entity.SettingsEntity;
import org.wanshu.reader.data.repository.ContentRepository;
import org.wanshu.reader.data.repository.PersonalRepository;
import org.wanshu.reader.data.repository.ReaderRepository;
import org.wanshu.reader.navigation.AppNavigator;
import org.wanshu.reader.navigation.BookRoute;
import org.wanshu.reader.navigation.DialogDismissHandler;
import org.wanshu.reader.navigation.ReaderAnchorSaver;
import org.wanshu.reader.ui.bookmarks.BookmarksDialog;
import org.wanshu.reader.ui.settings.SettingsDialog;
import org.wanshu.reader.ui.toc.TocDialog;
import org.wanshu.reader.ui.theme.ReaderThemeConfig;
import org.wanshu.reader.ui.theme.ThemeColors;

public class ReaderController implements ReaderAnchorSaver, DialogDismissHandler, TextView.OnEditorActionListener, View.OnKeyListener, DialogInterface.OnKeyListener {
    private final ReaderView view;
    private final ReaderRepository readerRepository;
    private final PersonalRepository personalRepository;
    private final AppNavigator navigator;
    private final AppExecutors executors;
    private final TextBlockBuilder textBlockBuilder;
    private ContentRepository contentRepository;
    private Dialog activeDialog;
    private SettingsEntity currentSettings = new SettingsEntity();
    private boolean resumed;
    private boolean progressReady;
    private volatile boolean disposed;
    private String currentBookId = "", currentChapterId = "";
    private volatile long currentGeneration;
    private String currentPrevChapterId, currentNextChapterId;
    private int targetParagraph = -1, targetOffset;
    private boolean hasShownContent, completeShown;
    private int shownCharCount;
    private ReadingAnchor currentAnchor, beforeSearch;
    private List<String> currentParagraphs = new ArrayList<String>();
    private List<ChapterSearchMatch> currentMatches = new ArrayList<ChapterSearchMatch>();
    private int currentMatchIndex = -1, matchWindowStart, totalMatches;
    private String searchQuery = "";
    private CancellationToken searchToken;
    private long searchGeneration;
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private ReaderSearchDebounceRunnable activeSearchRunnable;

    public ReaderController(ReaderView view, ReaderRepository readerRepository, PersonalRepository personalRepository,
            AppNavigator navigator, AppExecutors executors, TextBlockBuilder textBlockBuilder) {
        this.view = view; this.readerRepository = readerRepository; this.personalRepository = personalRepository;
        this.navigator = navigator; this.executors = executors;
        this.textBlockBuilder = textBlockBuilder != null ? textBlockBuilder : new TextBlockBuilder(4000);
        view.setBackClickListener(new ReaderBackClickListener(this));
        view.setNavigationListeners(new ReaderPrevClickListener(this), new ReaderBackClickListener(this), new ReaderNextClickListener(this));
        view.setRetryClickListener(new ReaderRetryClickListener(this));
        view.setAnchorCallback(new ReaderAnchorCallback(this));
        view.setSearchListeners(new ReaderSearchToggleClickListener(this), new ReaderSearchPrevClickListener(this),
                new ReaderSearchNextClickListener(this), new ReaderSearchCloseClickListener(this), new ReaderSearchTextWatcher(this));
        view.setTopBarActionListeners(new ReaderTocClickListener(this), new ReaderBookmarkClickListener(this), new ReaderSettingsClickListener(this));
        view.getSearchEditText().setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        view.getSearchEditText().setOnEditorActionListener(this);
        view.getSearchEditText().setOnKeyListener(this);
    }
    public void setContentRepository(ContentRepository content) { contentRepository = content; }
    public void postToUi(Runnable task) { executors.getMainThreadExecutor().execute(task); }
    public List<TextBlock> buildBlocks(ChapterResult result) { return textBlockBuilder.buildBlocks(result.getParagraphs()); }

    public void open(BookRoute route) {
        if (route == null) return;
        cancelActiveRequest();
        if (activeDialog != null) activeDialog.dismiss();
        currentBookId = route.getBookId(); currentChapterId = route.getChapterId();
        currentGeneration = route.getGeneration(); targetParagraph = route.getTargetParagraph(); targetOffset = route.getTargetOffset();
        currentPrevChapterId = null; currentNextChapterId = null;
        hasShownContent = false; completeShown = false; shownCharCount = 0; progressReady = false; currentAnchor = null;
        closeSearch(); currentParagraphs = new ArrayList<String>();
        view.setChapterIdentity(currentBookId, currentChapterId);
        view.showLoading();
        personalRepository.getSettings(new ReaderSettingsLoadedCallback(this));
        personalRepository.getReadingProgress(currentBookId, new ReaderOpenProgressCallback(this, currentGeneration));
    }
    public void beginObservation(long generation, ReadingAnchor progress) {
        if (!isCurrent(generation)) return;
        if (targetParagraph < 0 && progress != null && currentChapterId.equals(progress.getChapterId())) {
            targetParagraph = progress.getParagraphIndex(); targetOffset = progress.getOffsetUtf16();
        }
        progressReady = true;
        if (!resumed) return;
        readerRepository.observe(currentBookId, currentChapterId, "reader-ui-" + currentGeneration,
                new ReaderChapterObserver(this, currentGeneration));
    }
    private boolean isCurrent(long generation) { return !disposed && generation == currentGeneration && navigator.isCurrentGeneration(generation); }
    public void refreshSettings() {
        if (disposed) return;
        if (activeDialog instanceof SettingsDialog && activeDialog.isShowing()) ((SettingsDialog) activeDialog).onSettingsSaved();
        else personalRepository.getSettings(new ReaderSettingsLoadedCallback(this));
    }
    public void applySettings(SettingsEntity settings) {
        if (settings != null) postToUi(new ReaderSettingsUiRunnable(this, settings));
    }
    public void applySettingsOnUi(SettingsEntity settings) {
        if (disposed) return;
        currentSettings = settings;
        ThemeColors colors = ReaderThemeConfig.getThemeColors(settings.theme);
        view.setMaxWidthChars(settings.maxWidthChars);
        view.setTypography(colors, settings.fontSize, settings.lineHeight, settings.pageMargin);
        if (view.getContext() instanceof Activity) {
            Activity activity = (Activity) view.getContext();
            activity.getWindow().getDecorView().setBackgroundColor(colors.background);
            androidx.core.view.WindowInsetsControllerCompat bars = androidx.core.view.WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
            boolean light = !"dark".equals(settings.theme) && !"oled".equals(settings.theme) && !"black".equals(settings.theme);
            bars.setAppearanceLightStatusBars(light); bars.setAppearanceLightNavigationBars(light);
        }
        updateAwake();
    }
    public void setResumed(boolean value) {
        boolean returning = value && !resumed;
        resumed = value; updateAwake();
        // A complete chapter on screen needs no reload; otherwise resume the cancelled completion.
        if (returning && progressReady && !completeShown && !currentChapterId.isEmpty() && isCurrent(currentGeneration)) beginObservation(currentGeneration, null);
    }
    public void dispose() {
        deactivate(); disposed = true;
        searchHandler.removeCallbacksAndMessages(null);
    }
    private void updateAwake() {
        if (!(view.getContext() instanceof Activity)) return;
        Activity activity = (Activity) view.getContext();
        if (resumed && navigator.getCurrentRoute().isReader() && currentSettings.keepScreenAwake)
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    public void onChapterLoaded(ChapterResult result, long generation, boolean complete) {
        if (result != null && isCurrent(generation)) executors.getCpuExecutor().execute(new ReaderContentTask(this, result, generation, complete));
    }
    public void applyChapter(ChapterResult result, List<TextBlock> blocks, long generation, boolean complete) {
        if (!isCurrent(generation)) return;
        if (result.getParagraphs().isEmpty()) {
            if (complete && !hasShownContent) view.showError("源站未返回正文");
            return;
        }
        // 宁多勿少: keep what is shown unless the new text is longer or newly complete.
        if (hasShownContent && (result.getCharCount() < shownCharCount
                || (result.getCharCount() == shownCharCount && (completeShown || !result.isComplete())))) return;
        ReadingAnchor anchor = hasShownContent ? view.getAnchorSampler().sampleAnchor() : null;
        currentPrevChapterId = result.getPrevChapterId(); currentNextChapterId = result.getNextChapterId();
        currentParagraphs = new ArrayList<String>(result.getParagraphs());
        hasShownContent = true; completeShown = result.isComplete(); shownCharCount = result.getCharCount();
        view.showContent(result.getTitle(), blocks, currentPrevChapterId != null, currentNextChapterId != null, result.isRestricted());
        if (anchor != null) view.restoreAnchor(anchor.getParagraphIndex(), anchor.getOffsetUtf16());
        else if (targetParagraph >= 0) view.restoreAnchor(targetParagraph, Math.max(0, targetOffset));
        if (view.isSearchBarVisible()) onSearchQueryChanged(view.getSearchEditText().getText().toString());
        if (complete || result.isComplete()) personalRepository.recordReadingHistory(currentBookId, currentChapterId, result.getTitle(), null);
    }
    public void onChapterLoadFailed(Throwable error, long generation) {
        postToUi(new ReaderFailureRunnable(this, error == null ? "加载失败" : error.getMessage(), generation));
    }
    public void applyFailure(String message, long generation) {
        if (isCurrent(generation) && !hasShownContent) view.showError(message);
    }
    public void onAnchorSampled(ReadingAnchor anchor) {
        if (!hasShownContent || view.isSearchBarVisible() || !isCurrent(currentGeneration)) return;
        if (anchor != null && currentBookId.equals(anchor.getBookId()) && currentChapterId.equals(anchor.getChapterId())) {
            currentAnchor = anchor; personalRepository.saveReadingProgress(anchor, null);
        }
    }
    @Override public void saveCurrentAnchor() {
        if (currentBookId.isEmpty() || currentChapterId.isEmpty() || !hasShownContent) return;
        ReadingAnchor anchor = beforeSearch != null ? beforeSearch : view.getAnchorSampler().sampleAnchor();
        if (anchor == null) anchor = currentAnchor;
        if (anchor == null || !currentBookId.equals(anchor.getBookId()) || !currentChapterId.equals(anchor.getChapterId())) return;
        currentAnchor = anchor;
        personalRepository.saveReadingProgress(anchor, null);
        LastRouteEntity route = new LastRouteEntity(); route.routeType = "READER";
        route.bookId = currentBookId; route.chapterId = currentChapterId; route.updatedAt = System.currentTimeMillis();
        personalRepository.saveLastRoute(route, null);
    }
    public void cancelActiveRequest() {
        cancelSearchTask();
        if (activeSearchRunnable != null) { searchHandler.removeCallbacks(activeSearchRunnable); activeSearchRunnable = null; }
        if (activeDialog instanceof TocDialog) ((TocDialog) activeDialog).pauseLoading();
        readerRepository.incrementGeneration();
        readerRepository.cancel("reader-ui-" + currentGeneration);
    }
    public void deactivate() {
        if (navigator.getCurrentRoute().isReader()) saveCurrentAnchor();
        cancelActiveRequest();
        if (activeDialog != null) activeDialog.dismiss();
        closeSearch(); hasShownContent = false; updateAwake();
    }
    public void onBackToShelfClicked() {
        saveCurrentAnchor(); navigator.openShelf();
    }
    public void onPrevChapterClicked() {
        if (currentPrevChapterId != null) { saveCurrentAnchor(); navigator.openReader(currentBookId, currentPrevChapterId); }
    }
    public void onNextChapterClicked() {
        if (currentNextChapterId != null) { saveCurrentAnchor(); navigator.openReader(currentBookId, currentNextChapterId); }
    }
    public void onRetryClicked() { navigator.openReader(currentBookId, currentChapterId); }
    public void toggleSearch() {
        if (view.isSearchBarVisible()) closeSearch();
        else if (hasShownContent) { beforeSearch = view.getAnchorSampler().sampleAnchor(); view.showSearchBar(); }
    }
    @Override public boolean onEditorAction(TextView input, int actionId, KeyEvent event) {
        if (!view.isSearchBarVisible() || event != null) return false;
        if (actionId != EditorInfo.IME_ACTION_SEARCH && actionId != EditorInfo.IME_ACTION_DONE) return false;
        nextSearchMatch(); return true;
    }
    @Override public boolean onKey(View input, int keyCode, KeyEvent event) {
        if (!view.isSearchBarVisible() || event.isCtrlPressed() || event.isAltPressed() || event.isMetaPressed()
                || BaseInputConnection.getComposingSpanStart(view.getSearchEditText().getText()) >= 0) return false;
        if (keyCode != KeyEvent.KEYCODE_ENTER && keyCode != KeyEvent.KEYCODE_ESCAPE) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (keyCode == KeyEvent.KEYCODE_ESCAPE) closeSearch();
            else if (event.isShiftPressed()) prevSearchMatch();
            else nextSearchMatch();
        }
        return true;
    }
    private void cancelSearchTask() {
        searchGeneration++;
        if (searchToken != null) { searchToken.cancel(); searchToken = null; }
    }
    public void onSearchQueryChanged(String query) {
        cancelSearchTask(); view.cancelAnchorRestore();
        searchQuery = query == null ? "" : query.trim();
        currentMatches = new ArrayList<ChapterSearchMatch>(); currentMatchIndex = -1;
        totalMatches = 0; matchWindowStart = 0;
        view.setSearchMatchCount(0, 0); view.getAdapter().setSearchMatches(currentMatches, -1);
        if (activeSearchRunnable != null) searchHandler.removeCallbacks(activeSearchRunnable);
        activeSearchRunnable = null;
        if (!view.isSearchBarVisible() || searchQuery.isEmpty()) return;
        activeSearchRunnable = new ReaderSearchDebounceRunnable(this, searchQuery);
        searchHandler.postDelayed(activeSearchRunnable, 120);
    }
    public void executeSearch(String query) {
        if (!view.isSearchBarVisible() || !searchQuery.equals(query)) return;
        activeSearchRunnable = null;
        requestMatchWindow(0);
    }
    private void requestMatchWindow(int index) {
        cancelSearchTask();
        searchToken = new CancellationToken();
        int start = index / ChapterSearchEngine.MAX_HIGHLIGHTS * ChapterSearchEngine.MAX_HIGHLIGHTS;
        executors.getCpuExecutor().execute(new ReaderSearchTask(this, currentParagraphs, searchQuery, start, index, searchGeneration, searchToken));
    }
    public void applySearchResults(ChapterSearchPage page, int index, long generation) {
        if (generation != searchGeneration || !view.isSearchBarVisible() || !isCurrent(currentGeneration)) return;
        searchToken = null;
        currentMatches = page.getMatches(); matchWindowStart = page.getStartIndex(); totalMatches = page.getTotalMatches();
        currentMatchIndex = currentMatches.isEmpty() ? -1 : index;
        showMatch();
    }
    private void showMatch() {
        view.setSearchMatchCount(currentMatchIndex + 1, totalMatches);
        int localIndex = currentMatchIndex - matchWindowStart;
        view.getAdapter().setSearchMatches(currentMatches, localIndex);
        if (localIndex >= 0 && localIndex < currentMatches.size()) {
            ChapterSearchMatch match = currentMatches.get(localIndex);
            view.restoreAnchor(match.getParagraphIndex(), match.getStartOffset());
        }
    }
    private void moveSearchMatch(int direction) {
        if (totalMatches == 0) return;
        currentMatchIndex = (currentMatchIndex + direction + totalMatches) % totalMatches;
        int localIndex = currentMatchIndex - matchWindowStart;
        if (localIndex >= 0 && localIndex < currentMatches.size()) {
            cancelSearchTask(); showMatch();
        } else {
            view.cancelAnchorRestore();
            view.setSearchMatchCount(currentMatchIndex + 1, totalMatches);
            view.getAdapter().setSearchMatches(null, -1);
            requestMatchWindow(currentMatchIndex);
        }
    }
    public void nextSearchMatch() { moveSearchMatch(1); }
    public void prevSearchMatch() { moveSearchMatch(-1); }
    public void closeSearch() {
        view.hideSearchBar(); cancelSearchTask(); view.cancelAnchorRestore(); searchQuery = "";
        if (activeSearchRunnable != null) { searchHandler.removeCallbacks(activeSearchRunnable); activeSearchRunnable = null; }
        currentMatches = new ArrayList<ChapterSearchMatch>(); currentMatchIndex = -1; totalMatches = 0;
        view.setSearchMatchCount(0, 0); view.getAdapter().setSearchMatches(currentMatches, -1);
        if (beforeSearch != null && hasShownContent) view.restoreAnchor(beforeSearch.getParagraphIndex(), beforeSearch.getOffsetUtf16());
        beforeSearch = null;
    }
    @Override public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (dialog != activeDialog || keyCode != KeyEvent.KEYCODE_ESCAPE
                || event.isCtrlPressed() || event.isAltPressed() || event.isMetaPressed()) return false;
        View focus = activeDialog.getCurrentFocus();
        if (focus instanceof TextView && ((TextView) focus).getEditableText() != null
                && BaseInputConnection.getComposingSpanStart(((TextView) focus).getEditableText()) >= 0) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            activeDialog.dismiss(); activeDialog = null;
        }
        return true;
    }
    private void showDialog(Dialog dialog) {
        if (activeDialog != null) activeDialog.dismiss();
        saveCurrentAnchor(); activeDialog = dialog;
        dialog.setOnKeyListener(this); dialog.show();
    }
    public void openToc() {
        if (contentRepository != null) showDialog(new TocDialog(view.getContext(), currentBookId, currentChapterId,
                contentRepository, navigator, executors, ReaderThemeConfig.getThemeColors(currentSettings.theme)));
    }
    public void openBookmarks() {
        showDialog(new BookmarksDialog(view.getContext(), currentBookId, personalRepository, navigator, executors,
                ReaderThemeConfig.getThemeColors(currentSettings.theme), this));
    }
    public void openSettings() {
        if (contentRepository != null) showDialog(new SettingsDialog(view.getContext(), currentBookId, personalRepository, contentRepository, executors, this));
    }
    public ReadingAnchor getCurrentAnchorForBookmark() { saveCurrentAnchor(); return currentAnchor; }
    public String getCurrentSnippetForBookmark() {
        ReadingAnchor anchor = getCurrentAnchorForBookmark();
        if (anchor == null || anchor.getParagraphIndex() >= currentParagraphs.size()) return "";
        String paragraph = currentParagraphs.get(anchor.getParagraphIndex());
        int start = Math.min(anchor.getOffsetUtf16(), Math.max(0, paragraph.length() - 120));
        if (start > 0 && Character.isLowSurrogate(paragraph.charAt(start))) start--;
        int end = start + Math.min(120, paragraph.length() - start);
        if (end < paragraph.length() && end > start && Character.isHighSurrogate(paragraph.charAt(end - 1))) end--;
        return paragraph.substring(start, end);
    }
    @Override public boolean dismissActiveDialog() {
        if (activeDialog != null && activeDialog.isShowing()) { activeDialog.dismiss(); return true; }
        if (view.isSearchBarVisible()) { closeSearch(); return true; }
        return false;
    }
}
