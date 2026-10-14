package org.wanshu.reader;

import androidx.activity.ComponentActivity;
import android.content.Intent;
import android.content.DialogInterface;
import android.app.AlertDialog;
import android.widget.Toast;
import org.wanshu.reader.data.repository.DataCallback;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import org.wanshu.reader.data.personal.entity.LastRouteEntity;
import org.wanshu.reader.navigation.BookRoute;
import org.wanshu.reader.ui.reader.ReaderController;
import org.wanshu.reader.ui.reader.ReaderView;
import org.wanshu.reader.ui.shelf.ShelfController;
import org.wanshu.reader.ui.shelf.ShelfView;

public class MainActivity extends ComponentActivity implements DataCallback<String>, DialogInterface.OnClickListener {
    private static final int IMPORT_PERSONAL = 2001, EXPORT_PERSONAL = 2002;
    private boolean personalPickerOpen;
    private AlertDialog importConfirmation;
    private AppContainer container;
    private ShelfView shelfView;
    private ReaderView readerView;
    private ShelfController shelfController;
    private ReaderController readerController;
    private MainNavigationListener navigationListener;
    private long startupGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        ReaderApplication app = (ReaderApplication) getApplication();
        this.container = app.getContainer();
        personalPickerOpen = savedInstanceState != null && savedInstanceState.getBoolean("personalPickerOpen");
        startupGeneration = container.getNavigator().getCurrentGeneration();
        getOnBackPressedDispatcher().addCallback(this, new MainBackCallback(this, container.getBackController()));

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
                container.getContentDb(),
                container
        );

        readerController = new ReaderController(
                readerView,
                container.getReaderRepository(),
                container.getPersonalRepository(),
                container.getNavigator(),
                container.getExecutors(),
                container.getTextBlockBuilder()
        );

        readerController.setContentRepository(container.getContentRepository());
        container.getBackController().setDialogDismissHandler(readerController);
        container.getBackController().setReaderAnchorSaver(readerController);

        navigationListener = new MainNavigationListener(this);
        container.getNavigator().addListener(navigationListener);

        container.getPersonalRepository().getLastRoute(new MainLastRouteCallback(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (readerController != null) readerController.setResumed(true);
        if (container != null) container.getPersonalDataTransfer().attach(this);
    }

    @Override
    protected void onPause() {
        if (container != null) container.getPersonalDataTransfer().detach(this);
        if (container != null) container.getCoverLoader().cancelPending();
        if (readerController != null) {
            if (container.getNavigator().getCurrentRoute().isReader()) readerController.saveCurrentAnchor();
            readerController.setResumed(false);
            readerController.cancelActiveRequest();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (container != null) container.getPersonalDataTransfer().detach(this);
        if (importConfirmation != null) importConfirmation.dismiss();
        if (readerController != null) readerController.dispose();
        if (container != null && navigationListener != null) {
            container.getNavigator().removeListener(navigationListener);
            container.getBackController().releaseOwner(readerController);
            container.getBackController().releaseOwner(shelfController);
        }
        super.onDestroy();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (readerView != null && container.getNavigator().getCurrentRoute().isReader()
                && readerView.handleReaderKey(keyCode, event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMPORT_PERSONAL || requestCode == EXPORT_PERSONAL) {
            personalPickerOpen = false;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try {
                    if (!container.getPersonalDataTransfer().start(data.getData(), requestCode == IMPORT_PERSONAL))
                        Toast.makeText(this, "个人数据操作仍在进行", Toast.LENGTH_LONG).show();
                } catch (Exception error) { onError(error); }
            }
            return;
        }
        if (requestCode == ShelfController.REQUEST_CODE_IMPORT_TXT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                container.getExecutors().getCpuExecutor().execute(new MainImportMetadataRunnable(this, uri, shelfController));
            }
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("personalPickerOpen", personalPickerOpen);
        super.onSaveInstanceState(state);
    }

    public boolean isPersonalFileBusy() {
        return personalPickerOpen || container.getPersonalDataTransfer().isBusy();
    }

    public boolean pickPersonalData(boolean importing) {
        if (isPersonalFileBusy()) {
            Toast.makeText(this, "请等待当前文件操作完成", Toast.LENGTH_LONG).show(); return false;
        }
        readerController.saveCurrentAnchor();
        if (importing) {
            importConfirmation = new AlertDialog.Builder(this).setTitle("导入原生 JSON 备份？")
                    .setMessage("合并较新的进度、历史和书签，恢复设置，完成后返回书架。不删除现有书籍，不含小说正文；不会自动开启整本缓存。仅支持原生备份（最大4 MiB），旧网页版数据需另行迁移。")
                    .setNegativeButton("取消", null).setPositiveButton("选择备份", this).show();
            return true;
        }
        return launchPersonalPicker(false);
    }

    @Override public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) launchPersonalPicker(true);
    }

    private boolean launchPersonalPicker(boolean importing) {
        Intent intent = new Intent(importing ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(importing ? "*/*" : "application/json");
        if (importing) intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        else intent.putExtra(Intent.EXTRA_TITLE, "wangu-personal-native.json");
        try {
            personalPickerOpen = true;
            startActivityForResult(intent, importing ? IMPORT_PERSONAL : EXPORT_PERSONAL);
            return true;
        } catch (Exception error) {
            personalPickerOpen = false; onError(error); return false;
        }
    }

    @Override public void onSuccess(String message) {
        if (isFinishing() || isDestroyed()) return;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (container.getPersonalDataTransfer().wasImport()) container.getNavigator().openShelf();
        readerController.refreshSettings();
    }

    @Override public void onError(Throwable error) {
        if (isFinishing() || isDestroyed()) return;
        Toast.makeText(this, error.getMessage() == null ? "文件操作失败" : error.getMessage(), Toast.LENGTH_LONG).show();
        readerController.refreshSettings();
    }

    public void handleRouteChanged(BookRoute route) {
        runOnUiThread(new MainRouteRunnable(this, route));
    }

    public void applyRoute(BookRoute route) {
        if (isFinishing() || isDestroyed()) return;
        if (route != null && !container.getNavigator().isCurrentGeneration(route.getGeneration())) return;
        if (route == null || route.isShelf()) {
            readerController.deactivate();
            container.getBackController().setDialogDismissHandler(shelfController);
            LastRouteEntity saved = new LastRouteEntity();
            saved.routeType = "SHELF"; saved.bookId = ""; saved.chapterId = "";
            saved.updatedAt = System.currentTimeMillis();
            container.getPersonalRepository().saveLastRoute(saved, null);
            readerView.setVisibility(View.GONE);
            shelfView.setVisibility(View.VISIBLE);
            shelfController.refresh();
        } else if (route.isReader()) {
            container.getBackController().setDialogDismissHandler(readerController);
            shelfView.setVisibility(View.GONE);
            readerView.setVisibility(View.VISIBLE);
            readerController.open(route);
        }
    }

    public void handleLastRoute(LastRouteEntity route) {
        runOnUiThread(new MainLastRouteRunnable(this, route));
    }

    public void applyLastRoute(LastRouteEntity route) {
        if (isFinishing() || isDestroyed() || container.getNavigator().getCurrentGeneration() != startupGeneration) return;
        if (route != null && "READER".equals(route.routeType) && route.bookId != null && !route.bookId.isEmpty()) {
            container.getNavigator().openReader(route.bookId, route.chapterId);
        } else {
            shelfController.refresh();
        }
    }

}
