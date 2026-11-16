package voicechate2ee.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A small gear for the settings button. Like {@link IncognitoIcon} it is drawn from rectangles, so it needs
 * no texture file. One image pixel equals one GUI pixel.
 */
public final class GearIcon {

    /** Edge length of the image in pixels. */
    public static final int SIZE = 12;

    private static final int LIGHT = 0xFFF2F2F2;
    private static final int SHADOW = 0xFF2B2B2B;

    /** {@code #} = gear, {@code .} = transparent. */
    private static final String[] BITMAP = {
            "....####....",
            ".##.####.##.",
            ".##########.",
            "..###..###..",
            "####....####",
            "####....####",
            "####....####",
            "####....####",
            "..###..###..",
            ".##########.",
            ".##.####.##.",
            "....####....",
    };

    private GearIcon() {
    }

    /** Draws the gear with its top left corner at {@code (x, y)}. A dark copy one pixel lower right acts as shadow. */
    public static void draw(GuiGraphicsExtractor guiGraphics, int x, int y) {
        draw(guiGraphics, x + 1, y + 1, SHADOW);
        draw(guiGraphics, x, y, LIGHT);
    }

    private static void draw(GuiGraphicsExtractor guiGraphics, int x, int y, int color) {
        for (int row = 0; row < SIZE; row++) {
            String line = BITMAP[row];
            int col = 0;
            while (col < SIZE) {
                if (line.charAt(col) != '#') {
                    col++;
                    continue;
                }
                int start = col;
                while (col < SIZE && line.charAt(col) == '#') {
                    col++;
                }
                guiGraphics.fill(x + start, y + row, x + col, y + row + 1, color);
            }
        }
    }
}
