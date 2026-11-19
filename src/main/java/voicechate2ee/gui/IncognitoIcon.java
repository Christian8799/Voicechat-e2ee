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
    // look while encryption is switched off
    private static final int CHIP_OFF = 0xFF454545;
    private static final int WHITE_OFF = 0xFFAFAFAF;
    private static final int SLASH = 0xFFE5322D;
    private static final int DETECTED = 0xFFFFC04A;\n    private static final int DETECTED_BG = 0xFF352819;\n    private static final int SLASH_OUTLINE = 0xFF3A0A0A;

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

    /** Distinct amber variant for E2EE envelopes detected but not locally decryptable/authenticated. */
    public static void drawDetected(GuiGraphicsExtractor guiGraphics, float x, float y, float size) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(x, y);
        guiGraphics.pose().scale(size / GRID, size / GRID);
        for (int row = 0; row < GRID; row++) {
            String line = BITMAP[row];
            for (int col = 0; col < GRID; col++) {
                char c = line.charAt(col);
                if (c != 'T') {
                    guiGraphics.fill(col, row, col + 1, row + 1, c == 'W' ? DETECTED : DETECTED_BG);
                }
            }
        }
        // Amber question mark-like pixel mark differentiates detected/unverified state.
        guiGraphics.fill(5, 0, 6, 2, DETECTED);
        guiGraphics.fill(4, 2, 6, 3, DETECTED);
        guiGraphics.fill(5, 3, 6, 5, DETECTED);
        guiGraphics.fill(5, 6, 6, 7, DETECTED);
        guiGraphics.pose().popMatrix();
    }

    private IncognitoIcon() {
    }

    /**
     * Draws the icon with its top left corner at {@code (x, y)} in the current coordinate system of
     * {@code guiGraphics}, {@code size} units wide.
     */
    public static void draw(GuiGraphicsExtractor guiGraphics, float x, float y, float size) {
        draw(guiGraphics, x, y, size, true);
    }

    /**
     * Like {@link #draw(GuiGraphicsExtractor, float, float, float)}. With {@code active == false} the icon is
     * grayed out and crossed out (encryption is switched off).
     */
    public static void draw(GuiGraphicsExtractor guiGraphics, float x, float y, float size, boolean active) {
        int chip = active ? CHIP : CHIP_OFF;
        int white = active ? WHITE : WHITE_OFF;
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
                guiGraphics.fill(start, row, col, row + 1, c == 'W' ? white : chip);
            }
        }
        if (!active) {
            // diagonal from the bottom left to the top right, with a dark outline
            for (int i = 0; i < GRID; i++) {
                int row = GRID - 1 - i;
                guiGraphics.fill(Math.max(0, i - 1), row, i, row + 1, SLASH_OUTLINE);
                guiGraphics.fill(i + 1, row, Math.min(GRID, i + 2), row + 1, SLASH_OUTLINE);
            }
            for (int i = 0; i < GRID; i++) {
                guiGraphics.fill(i, GRID - 1 - i, i + 1, GRID - i, SLASH);
            }
        }
        guiGraphics.pose().popMatrix();
    }
}
