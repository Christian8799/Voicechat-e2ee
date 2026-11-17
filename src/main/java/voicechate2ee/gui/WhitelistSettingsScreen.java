package voicechate2ee.gui;

import de.maxhenkel.voicechat.gui.VoiceChatScreenBase;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import voicechate2ee.config.E2EEState;
import voicechate2ee.whitelist.ReceivedKeys;
import voicechate2ee.whitelist.WhitelistService;
import voicechate2ee.whitelist.WhitelistState;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Full in-game management for whitelist mode. No commands or manual config-file editing are needed:
 * users can switch modes, add online players, remove players, inspect key-exchange state and resend grants here.
 */
public final class WhitelistSettingsScreen extends VoiceChatScreenBase {

    private static final Component TITLE = Component.literal("Voice E2EE - Whitelist");
    private static final int WIDTH = 300;
    private static final int HEIGHT = 246;
    private static final int MARGIN = 10;
    private static final int ROWS_PER_PAGE = 5;
    private static final int ROW_HEIGHT = 17;

    private static final int COLOR_GOOD = 0xFF1E7B1E;
    private static final int COLOR_WARN = 0xFFAA2222;
    private static final int COLOR_NORMAL = 0xFF202020;

    @Nullable
    private final Screen parent;

    private EditBox playerName;
    private Button mode;
    private Button add;
    private Button previous;
    private Button next;
    private Button resend;
    private Button done;
    private final Button[] removeButtons = new Button[ROWS_PER_PAGE];

    private int page;
    private String status = "Add an online player by name.";
    private int statusColor = COLOR_NORMAL;

    public WhitelistSettingsScreen(@Nullable Screen parent) {
        super(TITLE, WIDTH, HEIGHT);
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        hoverAreas.clear();

        int left = guiLeft + MARGIN;
        int fullWidth = xSize - MARGIN * 2;

        mode = Button.builder(Component.empty(), button -> toggleMode())
                .bounds(left, guiTop + 22, fullWidth, 20).build();
        addRenderableWidget(mode);

        playerName = new EditBox(font, left, guiTop + 65, fullWidth - 84, 20, Component.literal("Player name"));
        playerName.setMaxLength(16);
        playerName.setHint(Component.literal("Online player name"));
        addRenderableWidget(playerName);

        add = Button.builder(Component.literal("Add player"), button -> addPlayer())
                .bounds(left + fullWidth - 80, guiTop + 65, 80, 20).build();
        addRenderableWidget(add);

        List<WhitelistState.Entry> entries = WhitelistState.entries();
        int pages = pageCount(entries.size());
        page = Math.max(0, Math.min(page, pages - 1));

        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            final int index = page * ROWS_PER_PAGE + i;
            if (index < entries.size()) {
                UUID uuid = entries.get(index).uuid();
                Button remove = Button.builder(Component.literal("Remove"), button -> removePlayer(uuid))
                        .bounds(left + fullWidth - 62, guiTop + 101 + i * ROW_HEIGHT, 62, 16).build();
                removeButtons[i] = remove;
                addRenderableWidget(remove);
            }
        }

        previous = Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(left, guiTop + 190, 30, 18).build();
        addRenderableWidget(previous);

        next = Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(left + fullWidth - 30, guiTop + 190, 30, 18).build();
        addRenderableWidget(next);

        int half = (fullWidth - 4) / 2;
        resend = Button.builder(Component.literal("Resend keys"), button -> resendKeys())
                .bounds(left, guiTop + 215, half, 20).build();
        addRenderableWidget(resend);

        done = Button.builder(Component.literal("Done"), button -> close())
                .bounds(left + half + 4, guiTop + 215, fullWidth - half - 4, 20).build();
        addRenderableWidget(done);

        refresh();
    }

    private static int pageCount(int count) {
        return Math.max(1, (count + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    private void refresh() {
        if (mode == null) {
            return;
        }
        mode.setMessage(Component.literal("Mode: " + (WhitelistState.isWhitelistMode()
                ? "WHITELIST - only listed players" : "SHARED KEY - same key")));
        ClientPacketListener connection = minecraft == null ? null : minecraft.getConnection();
        String name = playerName == null ? "" : playerName.getValue().trim();
        add.active = connection != null && !name.isEmpty() && connection.getPlayerInfo(name) != null;
        List<WhitelistState.Entry> entries = WhitelistState.entries();
        previous.active = page > 0;
        next.active = page + 1 < pageCount(entries.size());
        resend.active = !entries.isEmpty() && WhitelistState.isWhitelistMode() && E2EEState.snapshot().enabled();
    }

    private void toggleMode() {
        WhitelistState.Mode nextMode = WhitelistState.isWhitelistMode()
                ? WhitelistState.Mode.KEY : WhitelistState.Mode.WHITELIST;
        try {
            WhitelistState.setMode(nextMode);
            WhitelistService.getInstance().onModeChanged();
            setStatus(nextMode == WhitelistState.Mode.WHITELIST
                    ? "Whitelist mode enabled. Turn encryption ON to send your voice securely."
                    : "Shared-key mode enabled.", nextMode == WhitelistState.Mode.WHITELIST ? COLOR_GOOD : COLOR_NORMAL);
        } catch (IOException e) {
            setStatus("Could not save mode: " + e.getMessage(), COLOR_WARN);
        }
        refresh();
    }

    private void addPlayer() {
        ClientPacketListener connection = minecraft.getConnection();
        String name = playerName.getValue().trim();
        PlayerInfo info = connection == null || name.isEmpty() ? null : connection.getPlayerInfo(name);
        if (info == null) {
            setStatus("Player is not online. Only online players can be added.", COLOR_WARN);
            return;
        }
        UUID uuid = info.getProfile().id();
        if (uuid.equals(minecraft.getUser().getProfileId())) {
            setStatus("You cannot add yourself.", COLOR_WARN);
            return;
        }
        String realName = info.getProfile().name();
        try {
            boolean added = WhitelistState.add(uuid, realName);
            WhitelistService.getInstance().onAdded(uuid);
            page = Math.max(0, pageCount(WhitelistState.entries().size()) - 1);
            playerName.setValue("");
            setStatus(added ? realName + " added. Key exchange is queued automatically."
                    : realName + " is already listed; their key will be refreshed.", COLOR_GOOD);
            reopen();
        } catch (IOException e) {
            setStatus("Could not save whitelist: " + e.getMessage(), COLOR_WARN);
        }
        refresh();
    }

    private void removePlayer(UUID uuid) {
        WhitelistState.Entry entry = WhitelistState.entries().stream()
                .filter(item -> item.uuid().equals(uuid)).findFirst().orElse(null);
        if (entry == null) {
            reopen();
            return;
        }
        try {
            WhitelistState.remove(uuid);
            WhitelistService.getInstance().onRemoved(uuid);
            page = Math.min(page, pageCount(WhitelistState.entries().size()) - 1);
            status = entry.name() + " removed. Your voice key was rotated.";
            statusColor = COLOR_GOOD;
            reopen();
        } catch (IOException e) {
            setStatus("Could not save whitelist: " + e.getMessage(), COLOR_WARN);
        }
    }

    private void resendKeys() {
        WhitelistService.getInstance().resend();
        setStatus("Current key grants queued for all online players on your whitelist.", COLOR_GOOD);
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(page + delta, pageCount(WhitelistState.entries().size()) - 1));
        reopen();
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
    }

    private void reopen() {
        minecraft.gui.setScreen(new WhitelistSettingsScreen(parent, page, status, statusColor));
    }

    private WhitelistSettingsScreen(@Nullable Screen parent, int page, String status, int statusColor) {
        this(parent);
        this.page = page;
        this.status = status;
        this.statusColor = statusColor;
    }

    private void close() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void tick() {
        super.tick();
        refresh();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        if (keyEvent.isEscape()) {
            close();
            return true;
        }
        if (super.keyPressed(keyEvent)) {
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
        guiGraphics.text(font, TITLE, guiLeft + MARGIN, guiTop + 7, FONT_COLOR, false);
        guiGraphics.text(font, Component.literal("Select who may hear your encrypted voice."), guiLeft + MARGIN,
                guiTop + 46, FONT_COLOR, false);
        guiGraphics.text(font, Component.literal("WHITELIST"), guiLeft + MARGIN, guiTop + 91, FONT_COLOR, false);

        List<WhitelistState.Entry> entries = WhitelistState.entries();
        int start = page * ROWS_PER_PAGE;
        for (int i = 0; i < ROWS_PER_PAGE && start + i < entries.size(); i++) {
            WhitelistState.Entry entry = entries.get(start + i);
            int y = guiTop + 104 + i * ROW_HEIGHT;
            boolean online = minecraft.getConnection() != null && minecraft.getConnection().getPlayerInfo(entry.uuid()) != null;
            String outgoing = WhitelistService.getInstance().isGranted(entry.uuid()) ? "grant queued" : "grant pending";
            String incoming = ReceivedKeys.has(entry.uuid()) ? "key received" : "no key yet";
            String line = entry.name() + (online ? " (online)" : " (offline)") + " - " + outgoing + " / " + incoming;
            guiGraphics.text(font, Component.literal(font.plainSubstrByWidth(line, xSize - 92)),
                    guiLeft + MARGIN, y, online ? COLOR_NORMAL : 0xFF777777, false);
        }

        guiGraphics.text(font, Component.literal("Page " + (page + 1) + " / " + pageCount(entries.size())
                + "   " + entries.size() + " player(s)"), guiLeft + 48, guiTop + 194, FONT_COLOR, false);
        String visibleStatus = !E2EEState.snapshot().enabled() && WhitelistState.isWhitelistMode()
                ? "Encryption is OFF - no voice key grants are sent."
                : status;
        int visibleStatusColor = !E2EEState.snapshot().enabled() && WhitelistState.isWhitelistMode()
                ? COLOR_WARN : statusColor;
        guiGraphics.text(font, Component.literal(font.plainSubstrByWidth(visibleStatus, xSize - MARGIN * 2)),
                guiLeft + MARGIN, guiTop + 202, visibleStatusColor, false);
    }
}
