package voicechate2ee.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws the gray panel that Simple Voice Chat uses for its windows (black outline, white highlight on the
 * top left, dark shadow on the bottom right, rounded corners), at any size and without a texture.
 *
 * <p>The pixels are the same as in Simple Voice Chat's own {@code gui_voicechat.png}; the harness compares
 * them one by one.
 */
public final class McPanel {

    /** Smallest size that still looks right (two corners next to each other). */
    public static final int MIN_SIZE = 12;

    private static final int K = 0xFF000000; // outline
    private static final int W = 0xFFFFFFFF; // highlight
    private static final int G = 0xFFC6C6C6; // surface
    private static final int D = 0xFF555555; // shadow

    private static final int CORNER = 6;

    // '.' = transparent
    private static final String[] TOP_LEFT = {
            "..KKKK",
            ".KWWWW",
            "KWWWWW",
            "KWWWGG",
            "KWWGGG",
            "KWWGGG",
    };
    private static final String[] TOP_RIGHT = {
            "KKK...",
            "WWWK..",
            "WWWGK.",
            "GGGDDK",
            "GGGDDK",
            "GGGDDK",
    };
    private static final String[] BOTTOM_LEFT = {
            "KWWGGG",
            "KWWGGG",
            "KWWGGG",
            ".KGDDD",
            "..KDDD",
            "...KKK",
    };
    private static final String[] BOTTOM_RIGHT = {
            "GGGDDK",
            "GGGDDK",
            "GGDDDK",
            "DDDDDK",
            "DDDDK.",
            "KKKK..",
    };

    private McPanel() {
    }

    /** Draws a panel of {@code width} x {@code height} with its top left corner at {@code (x, y)}. */
    public static void draw(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        if (width < MIN_SIZE || height < MIN_SIZE) {
            g.fill(x, y, x + width, y + height, G);
            return;
        }
        int right = x + width;
        int bottom = y + height;

        // surface
        g.fill(x + 3, y + 3, right - 3, bottom - 3, G);

        // edges between the corners
        g.fill(x + CORNER, y, right - CORNER, y + 1, K);
        g.fill(x + CORNER, y + 1, right - CORNER, y + 3, W);
        g.fill(x, y + CORNER, x + 1, bottom - CORNER, K);
        g.fill(x + 1, y + CORNER, x + 3, bottom - CORNER, W);
        g.fill(right - 3, y + CORNER, right - 1, bottom - CORNER, D);
        g.fill(right - 1, y + CORNER, right, bottom - CORNER, K);
        g.fill(x + CORNER, bottom - 3, right - CORNER, bottom - 1, D);
        g.fill(x + CORNER, bottom - 1, right - CORNER, bottom, K);

        // corners
        corner(g, TOP_LEFT, x, y);
        corner(g, TOP_RIGHT, right - CORNER, y);
        corner(g, BOTTOM_LEFT, x, bottom - CORNER);
        corner(g, BOTTOM_RIGHT, right - CORNER, bottom - CORNER);
    }

    private static void corner(GuiGraphicsExtractor g, String[] bitmap, int x, int y) {
        for (int row = 0; row < CORNER; row++) {
            String line = bitmap[row];
            int col = 0;
            while (col < CORNER) {
                char c = line.charAt(col);
                if (c == '.') {
                    col++;
                    continue;
                }
                int start = col;
                while (col < CORNER && line.charAt(col) == c) {
                    col++;
                }
                g.fill(x + start, y + row, x + col, y + row + 1, color(c));
            }
        }
    }

    private static int color(char c) {
        return switch (c) {
            case 'K' -> K;
            case 'W' -> W;
            case 'D' -> D;
            default -> G;
        };
    }
}
