package org.wanshu.reader.navigation;

public class TestReaderAnchorSaver implements ReaderAnchorSaver {
    private int saveCount = 0;

    @Override
    public void saveCurrentAnchor() {
        saveCount++;
    }

    public int getSaveCount() {
        return saveCount;
    }
}
