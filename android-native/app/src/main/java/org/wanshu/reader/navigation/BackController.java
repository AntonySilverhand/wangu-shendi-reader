package org.wanshu.reader.navigation;

public class BackController {
    private final AppNavigator navigator;
    private DialogDismissHandler dialogDismissHandler;
    private ReaderAnchorSaver readerAnchorSaver;

    public BackController(AppNavigator navigator) {
        this.navigator = navigator;
    }

    public void setDialogDismissHandler(DialogDismissHandler handler) {
        this.dialogDismissHandler = handler;
    }

    public void setReaderAnchorSaver(ReaderAnchorSaver saver) {
        this.readerAnchorSaver = saver;
    }

    public boolean handleBack() {
        if (dialogDismissHandler != null && dialogDismissHandler.dismissActiveDialog()) {
            return true;
        }

        BookRoute route = navigator.getCurrentRoute();
        if (route != null && route.isReader()) {
            if (readerAnchorSaver != null) {
                readerAnchorSaver.saveCurrentAnchor();
            }
            navigator.openShelf();
            return true;
        }

        return false;
    }
}
