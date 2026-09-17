package org.wanshu.reader;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.FrameLayout;
import org.wanshu.reader.data.personal.entity.LastRouteEntity;
import org.wanshu.reader.navigation.BookRoute;
import org.wanshu.reader.ui.reader.ReaderController;
import org.wanshu.reader.ui.reader.ReaderView;
import org.wanshu.reader.ui.shelf.ShelfController;
import org.wanshu.reader.ui.shelf.ShelfView;

public class MainActivity extends Activity {
    private AppContainer container;
    private ShelfView shelfView;
    private ReaderView readerView;
    private ShelfController shelfController;
    private ReaderController readerController;
    private MainNavigationListener navigationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ReaderApplication app = (ReaderApplication) getApplication();
        this.container = app.getContainer();

        FrameLayout root = new FrameLayout(this);
        shelfView = new ShelfView(this);
        readerView = new ReaderView(this);
        readerView.setVisibility(View.GONE);

        root.addView(shelfView);
        root.addView(readerView);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, new ReaderWindowInsetsListener());
        setContentView(root);

        shelfController = new ShelfController(
                this,
                shelfView,
                container.getContentRepository(),
                container.getPersonalRepository(),
                container.getNavigator(),
                container.getExecutors(),
                container.getTextBlockBuilder(),
                container.getContentDb()
        );

        readerController = new ReaderController(
                readerView,
                container.getReaderRepository(),
                container.getPersonalRepository(),
                container.getNavigator(),
                container.getExecutors(),
                container.getTextBlockBuilder(),
                container.getDownloadCoordinator()
        );

        container.getBackController().setDialogDismissHandler(shelfController);
        container.getBackController().setReaderAnchorSaver(readerController);

        navigationListener = new MainNavigationListener(this);
        container.getNavigator().addListener(navigationListener);

        container.getPersonalRepository().getLastRoute(new MainLastRouteCallback(this));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (container != null && container.getDownloadCoordinator() != null) {
            container.getDownloadCoordinator().setAppForeground(true);
        }
    }

    @Override
    protected void onStop() {
        if (container != null && container.getDownloadCoordinator() != null) {
            container.getDownloadCoordinator().setAppForeground(false);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (container != null && navigationListener != null) {
            container.getNavigator().removeListener(navigationListener);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (container != null && container.getBackController().handleBack()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ShelfController.REQUEST_CODE_IMPORT_TXT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                String title = extractFileName(uri);
                if (title != null && title.toLowerCase().endsWith(".txt")) {
                    title = title.substring(0, title.length() - 4);
                }
                shelfController.handleImportUri(uri, title);
            }
        }
    }

    public void handleRouteChanged(BookRoute route) {
        runOnUiThread(new MainRouteRunnable(this, route));
    }

    public void applyRoute(BookRoute route) {
        if (route == null || route.isShelf()) {
            readerView.setVisibility(View.GONE);
            shelfView.setVisibility(View.VISIBLE);
            shelfController.refresh();
        } else if (route.isReader()) {
            shelfView.setVisibility(View.GONE);
            readerView.setVisibility(View.VISIBLE);
            readerController.open(route);
        }
    }

    public void handleLastRoute(LastRouteEntity route) {
        runOnUiThread(new MainLastRouteRunnable(this, route));
    }

    public void applyLastRoute(LastRouteEntity route) {
        if (route != null && "READER".equals(route.routeType) && route.bookId != null && !route.bookId.isEmpty()) {
            container.getNavigator().openReader(route.bookId, route.chapterId);
        } else {
            shelfController.refresh();
        }
    }

    private String extractFileName(Uri uri) {
        String result = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (col != -1) {
                        result = cursor.getString(col);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) {
                    try {
                        cursor.close();
                    } catch (Exception ignored) {}
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }
}
