package org.wanshu.reader.navigation;

public class TestDialogDismissHandler implements DialogDismissHandler {
    private boolean active = false;
    private int dismissCount = 0;

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean dismissActiveDialog() {
        if (active) {
            active = false;
            dismissCount++;
            return true;
        }
        return false;
    }

    public int getDismissCount() {
        return dismissCount;
    }
}
