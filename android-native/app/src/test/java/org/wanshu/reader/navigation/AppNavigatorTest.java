package org.wanshu.reader.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppNavigatorTest {

    @Test
    public void testDefaultRouteIsShelfWithInitialGeneration() {
        AppNavigator navigator = new AppNavigator();
        BookRoute route = navigator.getCurrentRoute();
        assertNotNull(route);
        assertTrue(route.isShelf());
        assertEquals(1L, route.getGeneration());
        assertEquals(1L, navigator.getCurrentGeneration());
    }

    @Test
    public void testNavigationIncrementsGeneration() {
        AppNavigator navigator = new AppNavigator();
        TestNavigationListener listener = new TestNavigationListener();
        navigator.addListener(listener);

        BookRoute readerRoute = navigator.openReader("36780", "101");
        assertEquals(2L, readerRoute.getGeneration());
        assertEquals("36780", readerRoute.getBookId());
        assertEquals("101", readerRoute.getChapterId());
        assertTrue(readerRoute.isReader());
        assertTrue(navigator.isCurrentGeneration(2L));
        assertFalse(navigator.isCurrentGeneration(1L));

        BookRoute shelfRoute = navigator.openShelf();
        assertEquals(3L, shelfRoute.getGeneration());
        assertTrue(shelfRoute.isShelf());
        assertTrue(navigator.isCurrentGeneration(3L));

        assertEquals(2, listener.getRoutes().size());
    }

    @Test
    public void testOpenReaderWithAnchorPreservesOffsets() {
        AppNavigator navigator = new AppNavigator();
        BookRoute route = navigator.openReaderWithAnchor("local-1", "5", 12, 34);
        assertEquals(2L, route.getGeneration());
        assertEquals("local-1", route.getBookId());
        assertEquals("5", route.getChapterId());
        assertEquals(12, route.getTargetParagraph());
        assertEquals(34, route.getTargetOffset());
    }

    @Test
    public void testBackControllerPrecedence() {
        AppNavigator navigator = new AppNavigator();
        BackController backController = new BackController(navigator);
        TestDialogDismissHandler dialogHandler = new TestDialogDismissHandler();
        TestReaderAnchorSaver anchorSaver = new TestReaderAnchorSaver();

        backController.setDialogDismissHandler(dialogHandler);
        backController.setReaderAnchorSaver(anchorSaver);

        // 1. In Shelf: back should return false
        assertFalse(backController.handleBack());
        assertEquals(0, anchorSaver.getSaveCount());

        // 2. Open Reader
        navigator.openReader("36780", "1");
        assertTrue(navigator.getCurrentRoute().isReader());

        // 3. Dialog appears in Reader: back should dismiss dialog first, without switching route or saving anchor
        dialogHandler.setActive(true);
        assertTrue(backController.handleBack());
        assertEquals(1, dialogHandler.getDismissCount());
        assertTrue(navigator.getCurrentRoute().isReader());
        assertEquals(0, anchorSaver.getSaveCount());

        // 4. Back again: should save anchor and return to Shelf
        assertTrue(backController.handleBack());
        assertEquals(1, anchorSaver.getSaveCount());
        assertTrue(navigator.getCurrentRoute().isShelf());

        // 5. Back on Shelf: returns false
        assertFalse(backController.handleBack());
    }
}
