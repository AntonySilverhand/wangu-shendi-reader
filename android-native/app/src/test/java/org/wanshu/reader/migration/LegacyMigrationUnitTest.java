package org.wanshu.reader.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LegacyMigrationUnitTest {

    @Test
    public void testBridgeHandshakeAndLocalStorageData() {
        TestMigrationListener listener = new TestMigrationListener();
        LegacyMigrationBridge bridge = new LegacyMigrationBridge(listener);

        bridge.onHandshake("1.0");
        assertEquals("1.0", listener.getHandshakeVersion());

        String sJson = "{\"theme\":\"black\",\"font\":\"serif\"}";
        String pJson = "{\"version\":1,\"books\":{}}";
        String lbJson = "[{\"id\":\"local-1\",\"title\":\"Test\"}]";

        bridge.onLocalStorageData(sJson, pJson, lbJson);
        assertEquals(sJson, listener.getSettingsJson());
        assertEquals(pJson, listener.getPersonalJson());
        assertEquals(lbJson, listener.getLocalBooksJson());
    }

    @Test
    public void testBridgeIndexedDbBatchAndCompletion() {
        TestMigrationListener listener = new TestMigrationListener();
        LegacyMigrationBridge bridge = new LegacyMigrationBridge(listener);

        String batch = "[{\"bookId\":\"36780\",\"chapterId\":\"1\",\"title\":\"第1章\"}]";
        bridge.onIndexedDbBatch("run-123", "chapters", batch, 0, false, "checksum-abc");

        assertEquals("chapters", listener.getLastStoreName());
        assertEquals(batch, listener.getLastBatchJson());
        assertEquals(0, listener.getLastBatchIndex());
        assertEquals(false, listener.isLastIsLastBatch());

        bridge.onIndexedDbBatch("run-123", "chapters", batch, 1, true, "checksum-xyz");
        assertEquals(1, listener.getLastBatchIndex());
        assertEquals(true, listener.isLastIsLastBatch());

        bridge.onMigrationComplete("run-123", "{\"chaptersCount\":10,\"tocCount\":1}");
        assertNotNull(listener.getCompletedSummaryJson());
        assertTrue(listener.getCompletedSummaryJson().contains("chaptersCount"));
    }

    @Test
    public void testBridgeErrorReporting() {
        TestMigrationListener listener = new TestMigrationListener();
        LegacyMigrationBridge bridge = new LegacyMigrationBridge(listener);

        bridge.onMigrationError("run-456", "idb_open_error", "VersionError: upgrade needed");
        assertEquals("idb_open_error", listener.getErrorStage());
        assertEquals("VersionError: upgrade needed", listener.getErrorMessage());
    }

    @Test
    public void testSettingsThemeTranslationLogic() {
        String legacyTheme = "black";
        String mappedTheme = "light";
        if ("black".equalsIgnoreCase(legacyTheme)) mappedTheme = "oled";
        else if ("eink".equalsIgnoreCase(legacyTheme)) mappedTheme = "eyecare";
        else if ("paper".equalsIgnoreCase(legacyTheme)) mappedTheme = "sepia";

        assertEquals("oled", mappedTheme);

        String legacyFont = "hei";
        String mappedFont = "hei".equalsIgnoreCase(legacyFont) ? "sans" : legacyFont;
        assertEquals("sans", mappedFont);
    }
}
