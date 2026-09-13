import org.wanshu.reader.DisplayGeometry;

public final class DisplayGeometryTest {
  private static void equal(int actual, int expected) {
    if (actual != expected) throw new AssertionError(actual + " != " + expected);
  }
  public static void main(String[] args) {
    equal(DisplayGeometry.legacyIme(48, 48, 48, 1), 0);
    equal(DisplayGeometry.legacyIme(348, 48, 348, 1), 348);
    equal(DisplayGeometry.legacyIme(0, 48, 348, 1), 348);
    equal(DisplayGeometry.legacyIme(144, 144, 180, 3), 0);
    equal(DisplayGeometry.legacyNavigation(348, 48, 348), 48);
    equal(DisplayGeometry.legacyNavigation(0, 48, 0), 0);
    equal(DisplayGeometry.keyboardOverlap(900, 600, true), 300);
    equal(DisplayGeometry.keyboardOverlap(600, 600, true), 0); // already adjustResize
    equal(DisplayGeometry.keyboardOverlap(650, 600, true), 50); // partially resized
    equal(DisplayGeometry.keyboardOverlap(500, 600, true), 0); // split-screen
    equal(DisplayGeometry.keyboardOverlap(900, 600, false), 0); // dismissed
    System.out.println("Android IME geometry: 11 assertions passed");
  }
}
