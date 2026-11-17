package voicechate2ee.gui;

import de.maxhenkel.voicechat.gui.VoiceChatScreenBase;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import voicechate2ee.config.E2EEState;
import voicechate2ee.config.SettingsModel;

/**
 * The settings window of the mod. Everything that used to need a command or the config file can be done here:
 * encryption on/off, "only hear encrypted players", and the key (show, enter, apply, generate).
 *
 * <p>Changes of the two switches are saved immediately. The key is only used after "Apply key", so a
 * half-typed key can never replace a working one.
 */
public class E2EESettingsScreen extends VoiceChatScreenBase {

    private static final Component TITLE = Component.literal("Voice Chat E2EE");

    public static final int WIDTH = 256;
    public static final int HEIGHT = 238;

    private static final int MARGIN = 10;
    private static final int GENERATE_CONFIRM_TICKS = 60;

    private static final int COLOR_GOOD = 0xFF1E7B1E;
    private static final int COLOR_WARN = 0xFFAA2222;

    @Nullable
    private final Screen parent;

    private Button encryption;
    private Button strict;
    private Button reveal;
    private Button apply;
    private Button generate;
    private Button whitelist;
    private EditBox keyBox;

    /** What is typed in the key field. Kept here so it survives resizing the window. */
    private String editText;
    private boolean keyVisible;
    private int generateConfirmTicks;

    private static final int KEY_BOX_Y = 92;
    private static final int KEY_BOX_HEIGHT = 20;
    private static final int REVEAL_WIDTH = 40;

    private int keyBoxWidth;

    public E2EESettingsScreen(@Nullable Screen parent) {
        super(TITLE, WIDTH, HEIGHT);
        this.parent = parent;
        this.editText = E2EEState.snapshot().keyText();
    }

    @Override
    protected void init() {
        super.init();
        hoverAreas.clear();

        int left = guiLeft + MARGIN;
        int fullWidth = xSize - MARGIN * 2;

        encryption = Button.builder(Component.empty(), button -> {
            SettingsModel.toggleEncryption();
            refresh();
        }).bounds(left, guiTop + 20, fullWidth, 20).build();
        encryption.setTooltip(Tooltip.create(Component.literal(SettingsModel.encryptionTooltip())));
        addRenderableWidget(encryption);

        strict = Button.builder(Component.empty(), button -> {
            SettingsModel.setStrict(!E2EEState.snapshot().requireEncryption());
            refresh();
        }).bounds(left, guiTop + 42, fullWidth, 20).build();
        strict.setTooltip(Tooltip.create(Component.literal(SettingsModel.strictTooltip())));
        addRenderableWidget(strict);

        keyBoxWidth = fullWidth - REVEAL_WIDTH - 2;
        keyBox = new EditBox(font, left, guiTop + KEY_BOX_Y, keyBoxWidth, KEY_BOX_HEIGHT, Component.empty());
        keyBox.setMaxLength(SettingsModel.MAX_KEY_LENGTH);
        keyBox.setValue(editText);
        keyBox.setResponder(text -> editText = text);
        addRenderableWidget(keyBox);

        reveal = Button.builder(Component.empty(), button -> {
            keyVisible = !keyVisible;
            refresh();
        }).bounds(left + fullWidth - REVEAL_WIDTH, guiTop + KEY_BOX_Y, REVEAL_WIDTH, KEY_BOX_HEIGHT).build();
        addRenderableWidget(reveal);

        int half = (fullWidth - 2) / 2;
        apply = Button.builder(Component.literal(SettingsModel.APPLY_LABEL), button -> applyKey())
                .bounds(left, guiTop + 128, half, 20).build();
        addRenderableWidget(apply);

        generate = Button.builder(Component.empty(), button -> onGenerate())
                .bounds(left + half + 2, guiTop + 128, fullWidth - half - 2, 20).build();
        addRenderableWidget(generate);

        whitelist = Button.builder(Component.literal("Configure whitelist..."), button ->
                minecraft.gui.setScreen(new WhitelistSettingsScreen(this)))
                .bounds(left, guiTop + 178, fullWidth, 20).build();
        addRenderableWidget(whitelist);

        Button done = Button.builder(Component.literal(SettingsModel.DONE_LABEL), button -> close())
                .bounds(left, guiTop + ySize - 20 - 8, fullWidth, 20).build();
        addRenderableWidget(done);

        refresh();
    }

    /** Brings labels, visibility and enabled state of the widgets in line with the saved settings. */
    private void refresh() {
        E2EEState.Snapshot s = E2EEState.snapshot();

        encryption.setMessage(Component.literal(SettingsModel.encryptionLabel(s)));
        encryption.active = SettingsModel.canToggleEncryption(s);
        strict.setMessage(Component.literal(SettingsModel.strictLabel(s)));

        keyBox.visible = keyVisible;
        reveal.setMessage(Component.literal(SettingsModel.revealLabel(keyVisible)));

        apply.active = SettingsModel.canApply(s, editText);
        generate.setMessage(Component.literal(generateConfirmTicks > 0 ? SettingsModel.CONFIRM_LABEL : SettingsModel.GENERATE_LABEL));
    }

    private void applyKey() {
        if (SettingsModel.applyKey(editText)) {
            editText = SettingsModel.normalizeKey(editText);
            keyBox.setValue(editText);
        }
        refresh();
    }

    /** Replacing the key cannot be undone, so an existing key needs a second click. */
    private void onGenerate() {
        boolean hasKey = E2EEState.snapshot().key() != null;
        if (hasKey && generateConfirmTicks == 0) {
            generateConfirmTicks = GENERATE_CONFIRM_TICKS;
            refresh();
            return;
        }
        generateConfirmTicks = 0;
        String key = SettingsModel.generateKey();
        if (key != null) {
            editText = key;
            keyBox.setValue(key);
            keyVisible = true; // so the new key can be copied right away
        }
        refresh();
    }

    private void close() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void tick() {
        super.tick();
        if (generateConfirmTicks > 0) {
            generateConfirmTicks--;
        }
        refresh();
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.isEscape()) {
            close();
            return true;
        }
        if (super.keyPressed(keyEvent)) {
            return true;
        }
        if (keyEvent.isConfirmation() && keyBox.visible && keyBox.isFocused()) {
            applyKey();
            return true;
        }
        return false;
    }

    @Override
    public void extractBackgroundRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        McPanel.draw(guiGraphics, guiLeft, guiTop, xSize, ySize);
    }

    @Override
    public void extractForegroundRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        int titleWidth = font.width(TITLE);
        int titleX = guiLeft + (xSize - titleWidth) / 2;
        guiGraphics.text(font, TITLE, titleX, guiTop + 7, FONT_COLOR, false);
        IncognitoIcon.draw(guiGraphics, titleX - IncognitoIcon.GRID - 3, guiTop + 7, IncognitoIcon.GRID,
                SettingsModel.isSendingEncrypted(E2EEState.snapshot()));

        E2EEState.Snapshot s = E2EEState.snapshot();
        int x = guiLeft + MARGIN;
        line(guiGraphics, SettingsModel.sendLine(s), x, guiTop + 65);
        line(guiGraphics, SettingsModel.hearLine(s), x, guiTop + 75);

        if (!keyVisible) {
            drawHiddenKeyBox(guiGraphics, x, guiTop + KEY_BOX_Y, keyBoxWidth, KEY_BOX_HEIGHT);
        }
        line(guiGraphics, SettingsModel.keyLine(s, editText), x, guiTop + 116);

        String[] hints = SettingsModel.hintLines();
        for (int i = 0; i < hints.length; i++) {
            guiGraphics.text(font, Component.literal(hints[i]), x, guiTop + 153 + i * 10, FONT_COLOR, false);
        }
    }

    private void line(GuiGraphicsExtractor guiGraphics, SettingsModel.Line line, int x, int y) {
        int color = switch (line.tone()) {
            case GOOD -> COLOR_GOOD;
            case WARN -> COLOR_WARN;
            case NORMAL -> FONT_COLOR;
        };
        guiGraphics.text(font, Component.literal(line.text()), x, y, color, false);
    }

    /** Stand-in for the text field while the key is hidden (looks like a disabled text field). */
    private void drawHiddenKeyBox(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, 0xFF555555);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF000000);
        guiGraphics.text(font, Component.literal(editText.isEmpty() ? "No key" : "Key hidden. Click Show."),
                x + 4, y + (height - font.lineHeight) / 2, 0xFF808080, false);
    }
}
