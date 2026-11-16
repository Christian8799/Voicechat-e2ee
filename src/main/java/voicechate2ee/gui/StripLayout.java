package voicechate2ee.gui;

/**
 * Position of the small button strip next to the Simple Voice Chat window (the window itself is a fixed
 * texture without any free space, so the two buttons get a little panel of their own).
 *
 * <p>Normally the strip is a column to the right of the window. If there is no room on the right, it goes to
 * the left; on a very narrow screen (huge GUI scale) it becomes a row below or above the window.
 */
public final class StripLayout {

    public static final int BUTTON = 20;
    public static final int PADDING = 4;
    public static final int GAP = 2;
    /** Space between the Simple Voice Chat window and the strip. */
    public static final int SPACING = 2;

    /** Length of the strip (two buttons). */
    public static final int LONG = PADDING * 2 + BUTTON * 2 + GAP;
    /** Thickness of the strip (one button). */
    public static final int SHORT = PADDING * 2 + BUTTON;

    private StripLayout() {
    }

    /** Where the strip is and how its buttons are arranged. */
    public record Placement(int x, int y, int width, int height, boolean vertical) {

        /** Left edge of button {@code index} (0 = encryption toggle, 1 = settings). */
        public int buttonX(int index) {
            return x + PADDING + (vertical ? 0 : index * (BUTTON + GAP));
        }

        /** Top edge of button {@code index}. */
        public int buttonY(int index) {
            return y + PADDING + (vertical ? index * (BUTTON + GAP) : 0);
        }
    }

    public static Placement place(int guiLeft, int guiTop, int xSize, int ySize, int screenWidth, int screenHeight) {
        int centeredY = guiTop + (ySize - LONG) / 2;
        int right = guiLeft + xSize + SPACING;
        if (right + SHORT <= screenWidth) {
            return new Placement(right, centeredY, SHORT, LONG, true);
        }
        int left = guiLeft - SPACING - SHORT;
        if (left >= 0) {
            return new Placement(left, centeredY, SHORT, LONG, true);
        }
        // No room at the sides: a row below the window, or above it
        int x = Math.max(0, Math.min(guiLeft + (xSize - LONG) / 2, screenWidth - LONG));
        int below = guiTop + ySize + SPACING;
        if (below + SHORT <= screenHeight) {
            return new Placement(x, below, LONG, SHORT, false);
        }
        return new Placement(x, Math.max(0, guiTop - SPACING - SHORT), LONG, SHORT, false);
    }
}
