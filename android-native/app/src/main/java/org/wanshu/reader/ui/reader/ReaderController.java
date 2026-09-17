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
import org.wanshu.reader.ui.theme.ReaderThemeConfig;
import org.wanshu.reader.ui.theme.ThemeColors;

public class ReaderController implements ReaderAnchorSaver {
    private final ReaderView view;
    private final ReaderRepository readerRepository;
    private final PersonalRepository personalRepository;
    private final AppNavigator navigator;
    private final AppExecutors executors;
    private final TextBlockBuilder textBlockBuilder;

    private String currentBookId = "";
    private String currentChapterId = "";
    private long currentGeneration = 0L;
    private String currentPrevChapterId = null;
    private String currentNextChapterId = null;
    private int targetParagraph = -1;
    private boolean hasShownContent = false;
    private ReadingAnchor currentAnchor = null;

    public ReaderController(
            ReaderView view,
            ReaderRepository readerRepository,
            PersonalRepository personalRepository,
            AppNavigator navigator,
            AppExecutors executors,
            TextBlockBuilder textBlockBuilder
    ) {
        this.view = view;
        this.readerRepository = readerRepository;
        this.personalRepository = personalRepository;
        this.navigator = navigator;
        this.executors = executors;
        this.textBlockBuilder = textBlockBuilder != null ? textBlockBuilder : new TextBlockBuilder(4000);

        this.view.setBackClickListener(new ReaderBackClickListener(this));
        this.view.setNavigationListeners(
                new ReaderPrevClickListener(this),
                new ReaderBackClickListener(this),
                new ReaderNextClickListener(this)
        );
        this.view.setRetryClickListener(new ReaderRetryClickListener(this));
        this.view.setAnchorCallback(new ReaderAnchorCallback(this));
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

        view.setChapterIdentity(currentBookId, currentChapterId);
        executors.getMainThreadExecutor().execute(new ReaderShowLoadingRunnable(view));

        // Load personal typography settings
        personalRepository.getSettings(new ReaderSettingsLoadedCallback(this));

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
        executors.getMainThreadExecutor().execute(new ReaderApplyTypographyRunnable(
                view,
                colors,
                settings.fontSize,
                settings.lineHeight,
                settings.pageMargin
        ));
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

            List<TextBlock> blocks = textBlockBuilder.buildBlocks(result.getParagraphs());

            executors.getMainThreadExecutor().execute(new ReaderShowContentRunnable(
                    view,
                    result.getTitle(),
                    blocks,
                    currentPrevChapterId != null,
                    currentNextChapterId != null,
                    targetParagraph
            ));

            if (isComplete) {
                personalRepository.recordReadingHistory(
                        currentBookId,
                        currentChapterId,
                        result.getTitle(),
                        null
                );
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
    }

    public void nextSearchMatch() {
    }

    public void prevSearchMatch() {
    }

    public void closeSearch() {
    }
}
