package voicechate2ee.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The incognito icon (hat with glasses on a dark badge). It is drawn from rectangles and therefore needs
 * no texture file: it cannot show up as a "missing texture" and adapts to any GUI scale.
 *
 * <p>The image is {@value #GRID} x {@value #GRID} pixels. At {@code size == GRID} one image pixel equals
 * exactly one GUI pixel; whole multiples (14, 21, ...) stay sharp.
 */
public final class IncognitoIcon {

    /** Edge length of the image in image pixels. */
    public static final int GRID = 7;

    private static final int CHIP = 0xFF1F1F1F;
    private static final int WHITE = 0xFFFFFFFF;

    /** {@code T} = transparent, {@code #} = dark background, {@code W} = white (hat and glasses lenses). */
    private static final String[] BITMAP = {
            "T#####T",
            "##WWW##",
            "#WWWWW#",
            "#######",
            "#WW#WW#",
            "#WW#WW#",
            "T#####T",
    };

    private IncognitoIcon() {
    }

    /**
     * Draws the icon with its top left corner at {@code (x, y)} in the current coordinate system of
     * {@code guiGraphics}, {@code size} units wide.
     */
    public static void draw(GuiGraphicsExtractor guiGraphics, float x, float y, float size) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(x, y);
        float scale = size / GRID;
        guiGraphics.pose().scale(scale, scale);
        for (int row = 0; row < GRID; row++) {
            String line = BITMAP[row];
            int col = 0;
            while (col < GRID) {
                char c = line.charAt(col);
                if (c == 'T') {
                    col++;
                    continue;
                }
                int start = col;
                while (col < GRID && line.charAt(col) == c) {
                    col++;
                }
                guiGraphics.fill(start, row, col, row + 1, c == 'W' ? WHITE : CHIP);
            }
        }
        guiGraphics.pose().popMatrix();
    }
}
