package com.example.examplemod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

@Mod(ExampleMod.MOD_ID)
public final class ExampleMod {
    public static final String MOD_ID = "examplemod";

    private static final Logger LOGGER = LogUtils.getLogger();

    public ExampleMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientHud hud = new ClientHud();

            MinecraftForge.EVENT_BUS.register(hud);

            FMLJavaModLoadingContext.get()
                    .getModEventBus()
                    .addListener(hud::onAddGuiLayers);
        }
    }

    /**
     * Настройки первого набора HUD-элементов.
     *
     * Позднее этот класс можно заменить полноценной системой Setting.
     */
    public static final class HudSettings {
        public boolean fpsPing = true;
        public boolean cps = true;
        public boolean keystrokes = true;
    }

    /**
     * Загрузка и сохранение настроек клиента в JSON.
     */
    public static final class ConfigManager {
        private static final Gson GSON = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        private static Path getConfigPath() {
            return Minecraft.getInstance()
                    .gameDirectory
                    .toPath()
                    .resolve("config")
                    .resolve("endvisuals.json");
        }

        public static HudSettings load() {
            Path path = getConfigPath();

            if (!Files.exists(path)) {
                HudSettings defaults = new HudSettings();
                save(defaults);
                return defaults;
            }

            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                HudSettings settings = GSON.fromJson(json, HudSettings.class);

                if (settings == null) {
                    LOGGER.warn(
                            "End Visuals config is empty; using default settings."
                    );
                    return new HudSettings();
                }

                return settings;
            } catch (Exception exception) {
                LOGGER.error(
                        "Could not load End Visuals config; using defaults.",
                        exception
                );
                return new HudSettings();
            }
        }

        public static void save(HudSettings settings) {
            Path path = getConfigPath();

            try {
                Files.createDirectories(path.getParent());
                Files.writeString(
                        path,
                        GSON.toJson(settings),
                        StandardCharsets.UTF_8
                );
            } catch (IOException exception) {
                LOGGER.error(
                        "Could not save End Visuals config to {}",
                        path,
                        exception
                );
            }
        }
    }

    /**
     * Временный менеджер HUD и обработчик клиентского ввода.
     *
     * Счётчик CPS учитывает физические нажатия мыши, а не
     * подтверждённые сервером удары.
     */
    public static final class ClientHud {
        private final Deque<Long> leftClicks = new ArrayDeque<>();
        private final Deque<Long> rightClicks = new ArrayDeque<>();

        private final HudSettings settings;

        private static final long CPS_WINDOW_MS = 1_000L;

        private static final int WHITE = 0xFFFFFFFF;
        private static final int GREEN = 0xFF55FF55;
        private static final int YELLOW = 0xFFFFFF55;
        private static final int RED = 0xFFFF5555;

        private static final int BACKGROUND = 0xB0181028;
        private static final int ACCENT = 0xFFAA66FF;
        private static final int KEY_IDLE = 0xB0332049;
        private static final int KEY_PRESSED = 0xD0C18AFF;
        private static final int KEY_TEXT_PRESSED = 0xFF1A0D29;

        public ClientHud() {
            settings = ConfigManager.load();
        }

        /**
         * Регистрирует слой HUD через API Forge 1.21.4.
         */
        public void onAddGuiLayers(AddGuiOverlayLayersEvent event) {
            event.getLayeredDraw().add(
                    ResourceLocation.fromNamespaceAndPath(
                            ExampleMod.MOD_ID,
                            "client_hud"
                    ),
                    (graphics, deltaTracker) -> renderHud(graphics)
            );
        }

        /**
         * Открывает меню на правый Shift, когда другой экран не открыт.
         *
         * Если правый Shift назначен действию Minecraft или другого мода,
         * возможен конфликт. Редактор привязок добавим отдельно.
         */
        @SubscribeEvent
        public void onKey(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();

            if (event.getKey() != GLFW.GLFW_KEY_RIGHT_SHIFT) {
                return;
            }

            if (event.getAction() != GLFW.GLFW_PRESS) {
                return;
            }

            if (mc.player == null || mc.screen != null) {
                return;
            }

            mc.setScreen(new EndVisualsScreen(settings));
        }

        @SubscribeEvent
        public void onMouseButton(InputEvent.MouseButton.Pre event) {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null || mc.screen != null) {
                return;
            }

            if (event.getAction() != GLFW.GLFW_PRESS) {
                return;
            }

            long now = System.currentTimeMillis();

            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                leftClicks.addLast(now);
            } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                rightClicks.addLast(now);
            }
        }

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            long now = System.currentTimeMillis();

            prune(leftClicks, now);
            prune(rightClicks, now);

            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null) {
                leftClicks.clear();
                rightClicks.clear();
            }
        }

        private void renderHud(GuiGraphics graphics) {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null || mc.options.hideGui) {
                return;
            }

            drawStats(graphics, mc, 8, 8);

            if (settings.keystrokes) {
                drawKeystrokes(graphics, mc, 8, 50);
            }
        }

        private void drawStats(
                GuiGraphics graphics,
                Minecraft mc,
                int x,
                int y
        ) {
            if (!settings.fpsPing && !settings.cps) {
                return;
            }

            int height = settings.fpsPing && settings.cps ? 32 : 18;
            int panelWidth = 150;

            graphics.fill(
                    x - 3,
                    y - 3,
                    x + panelWidth,
                    y + height,
                    BACKGROUND
            );

            graphics.fill(
                    x - 3,
                    y - 3,
                    x - 1,
                    y + height,
                    ACCENT
            );

            int currentY = y;

            if (settings.fpsPing) {
                int fps = mc.getFps();
                int ping = getPing(mc);

                graphics.drawString(
                        mc.font,
                        "FPS: " + fps,
                        x + 2,
                        currentY,
                        fpsColor(fps),
                        true
                );

                graphics.drawString(
                        mc.font,
                        "Ping: " + (ping < 0 ? "N/A" : ping + " ms"),
                        x + 70,
                        currentY,
                        ping < 0 ? WHITE : pingColor(ping),
                        true
                );

                currentY += 14;
            }

            if (settings.cps) {
                int left = leftClicks.size();
                int right = rightClicks.size();

                graphics.drawString(
                        mc.font,
                        "CPS L/R: " + left + "/" + right
                                + " | Total: " + (left + right),
                        x + 2,
                        currentY,
                        WHITE,
                        true
                );
            }
        }

        private void drawKeystrokes(
                GuiGraphics graphics,
                Minecraft mc,
                int x,
                int y
        ) {
            int size = 20;
            int gap = 2;

            drawKey(
                    graphics, mc, "W",
                    x + size + gap, y,
                    size, size,
                    mc.options.keyUp.isDown()
            );

            drawKey(
                    graphics, mc, "A",
                    x, y + size + gap,
                    size, size,
                    mc.options.keyLeft.isDown()
            );

            drawKey(
                    graphics, mc, "S",
                    x + size + gap, y + size + gap,
                    size, size,
                    mc.options.keyDown.isDown()
            );

            drawKey(
                    graphics, mc, "D",
                    x + 2 * (size + gap), y + size + gap,
                    size, size,
                    mc.options.keyRight.isDown()
            );

            int mouseY = y + 2 * (size + gap);
            int mouseWidth = 31;

            drawKey(
                    graphics, mc, "LMB",
                    x, mouseY,
                    mouseWidth, size,
                    mc.options.keyAttack.isDown()
            );

            drawKey(
                    graphics, mc, "RMB",
                    x + mouseWidth + gap, mouseY,
                    mouseWidth, size,
                    mc.options.keyUse.isDown()
            );

            int bottomY = mouseY + size + gap;

            drawKey(
                    graphics, mc, "SPACE",
                    x, bottomY,
                    42, size,
                    mc.options.keyJump.isDown()
            );

            drawKey(
                    graphics, mc, "SHIFT",
                    x + 44, bottomY,
                    42, size,
                    mc.options.keyShift.isDown()
            );
        }

        private void drawKey(
                GuiGraphics graphics,
                Minecraft mc,
                String label,
                int x,
                int y,
                int width,
                int height,
                boolean pressed
        ) {
            graphics.fill(
                    x,
                    y,
                    x + width,
                    y + height,
                    pressed ? KEY_PRESSED : KEY_IDLE
            );

            int textX = x + (width - mc.font.width(label)) / 2;
            int textY = y + (height - mc.font.lineHeight) / 2;

            graphics.drawString(
                    mc.font,
                    label,
                    textX,
                    textY,
                    pressed ? KEY_TEXT_PRESSED : WHITE,
                    false
            );
        }

        private int getPing(Minecraft mc) {
            if (mc.getConnection() == null || mc.player == null) {
                return -1;
            }

            PlayerInfo info =
                    mc.getConnection().getPlayerInfo(mc.player.getUUID());

            return info == null ? -1 : info.getLatency();
        }

        private void prune(Deque<Long> clicks, long now) {
            while (!clicks.isEmpty()
                    && now - clicks.peekFirst() >= CPS_WINDOW_MS) {
                clicks.removeFirst();
            }
        }

        private int fpsColor(int fps) {
            if (fps >= 100) {
                return GREEN;
            }

            if (fps >= 50) {
                return YELLOW;
            }

            return RED;
        }

        private int pingColor(int ping) {
            if (ping <= 80) {
                return GREEN;
            }

            if (ping <= 150) {
                return YELLOW;
            }

            return RED;
        }
    }

    /**
     * Тёмное меню End Visuals с фиолетовыми переключателями.
     *
     * Открывается на правый Shift. Пока управляет только уже
     * реализованными HUD-функциями.
     */
    public static final class EndVisualsScreen extends Screen {
        private static final int DIM = 0xA0000000;

        private static final int PANEL = 0xF2100E17;
        private static final int PANEL_BORDER = 0xFF292131;
        private static final int TOP_BAR = 0xFF15121E;

        private static final int CARD = 0xFF1C1925;
        private static final int CARD_HOVER = 0xFF262132;

        private static final int ACCENT = 0xFF8B5CF6;
        private static final int ACCENT_LIGHT = 0xFFB99AFF;

        private static final int TEXT = 0xFFEDE9F5;
        private static final int TEXT_MUTED = 0xFF817A8D;
        private static final int WHITE = 0xFFFFFFFF;

        private static final int SWITCH_OFF = 0xFF393443;
        private static final int SWITCH_KNOB_OFF = 0xFF77717E;

        private static final int MAX_PANEL_WIDTH = 450;
        private static final int PANEL_HEIGHT = 270;
        private static final int HEADER_HEIGHT = 42;

        private static final int TAB_VISUALS = 0;
        private static final int TAB_HUD = 1;
        private static final int TAB_UTILITIES = 2;

        private final HudSettings settings;

        // Сначала открываем HUD, потому что существующие функции находятся там.
        private int selectedTab = TAB_HUD;

        public EndVisualsScreen(HudSettings settings) {
            super(Component.literal("End Visuals"));
            this.settings = settings;
        }

        private int panelWidth() {
            return Math.max(1, Math.min(MAX_PANEL_WIDTH, width - 16));
        }

        private int panelX() {
            return (width - panelWidth()) / 2;
        }

        private int panelY() {
            return Math.max(4, (height - PANEL_HEIGHT) / 2);
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            int x = panelX();
            int y = panelY();
            int w = panelWidth();

            graphics.fill(0, 0, width, height, DIM);

            graphics.fill(
                    x - 1,
                    y - 1,
                    x + w + 1,
                    y + PANEL_HEIGHT + 1,
                    PANEL_BORDER
            );

            graphics.fill(
                    x,
                    y,
                    x + w,
                    y + PANEL_HEIGHT,
                    PANEL
            );

            graphics.fill(
                    x,
                    y,
                    x + w,
                    y + HEADER_HEIGHT,
                    TOP_BAR
            );

            graphics.renderItem(
                    new ItemStack(Items.NETHER_STAR),
                    x + 12,
                    y + 12
            );

            graphics.drawString(
                    font,
                    "End Visuals",
                    x + 34,
                    y + 10,
                    TEXT,
                    false
            );

            graphics.drawString(
                    font,
                    "CLIENT",
                    x + 34,
                    y + 23,
                    ACCENT_LIGHT,
                    false
            );

            int tabStartX = x + 130;

            drawTab(
                    graphics,
                    "Visuals",
                    tabStartX,
                    y + 14,
                    TAB_VISUALS,
                    mouseX,
                    mouseY
            );

            drawTab(
                    graphics,
                    "HUD",
                    tabStartX + 58,
                    y + 14,
                    TAB_HUD,
                    mouseX,
                    mouseY
            );

            drawTab(
                    graphics,
                    "Utilities",
                    tabStartX + 102,
                    y + 14,
                    TAB_UTILITIES,
                    mouseX,
                    mouseY
            );

            graphics.fill(
                    x + 12,
                    y + HEADER_HEIGHT,
                    x + w - 12,
                    y + HEADER_HEIGHT + 1,
                    PANEL_BORDER
            );

            if (selectedTab == TAB_HUD) {
                drawHudCards(graphics, mouseX, mouseY);
            } else {
                drawEmptyCategory(graphics);
            }

            graphics.drawString(
                    font,
                    "RIGHT SHIFT / ESC  -  Close",
                    x + 14,
                    y + PANEL_HEIGHT - 18,
                    TEXT_MUTED,
                    false
            );
        }

        private void drawTab(
                GuiGraphics graphics,
                String label,
                int x,
                int y,
                int tab,
                int mouseX,
                int mouseY
        ) {
            boolean selected = selectedTab == tab;
            boolean hovered = isInside(
                    mouseX,
                    mouseY,
                    x - 4,
                    y - 5,
                    font.width(label) + 8,
                    20
            );

            int color = selected
                    ? TEXT
                    : hovered ? ACCENT_LIGHT : TEXT_MUTED;

            graphics.drawString(font, label, x, y, color, false);

            if (selected) {
                graphics.fill(
                        x,
                        y + 13,
                        x + font.width(label),
                        y + 15,
                        ACCENT
                );
            }
        }

        private void drawHudCards(
                GuiGraphics graphics,
                int mouseX,
                int mouseY
        ) {
            int x = panelX();
            int y = panelY();
            int w = panelWidth();

            graphics.drawString(
                    font,
                    "HUD MODULES",
                    x + 14,
                    y + 54,
                    TEXT_MUTED,
                    false
            );

            int gap = 8;
            int cardX = x + 14;
            int cardY = y + 72;
            int availableWidth = w - 28;

            boolean twoColumns = availableWidth >= 300;
            int cardWidth = twoColumns
                    ? (availableWidth - gap) / 2
                    : availableWidth;

            drawCard(
                    graphics,
                    "FPS / Ping",
                    "Frame rate and latency",
                    cardX,
                    cardY,
                    cardWidth,
                    settings.fpsPing,
                    mouseX,
                    mouseY
            );

            int secondX = twoColumns
                    ? cardX + cardWidth + gap
                    : cardX;

            int secondY = twoColumns
                    ? cardY
                    : cardY + 42;

            drawCard(
                    graphics,
                    "CPS Counter",
                    "Mouse clicks per second",
                    secondX,
                    secondY,
                    cardWidth,
                    settings.cps,
                    mouseX,
                    mouseY
            );

            int thirdY = twoColumns
                    ? cardY + 42
                    : cardY + 84;

            drawCard(
                    graphics,
                    "Keystrokes",
                    "Movement and mouse keys",
                    cardX,
                    thirdY,
                    cardWidth,
                    settings.keystrokes,
                    mouseX,
                    mouseY
            );

            graphics.drawString(
                    font,
                    "Click a card to toggle it.",
                    cardX,
                    y + 220,
                    TEXT_MUTED,
                    false
            );
        }

        private void drawCard(
                GuiGraphics graphics,
                String name,
                String description,
                int x,
                int y,
                int cardWidth,
                boolean enabled,
                int mouseX,
                int mouseY
        ) {
            int cardHeight = 34;

            boolean hovered = isInside(
                    mouseX,
                    mouseY,
                    x,
                    y,
                    cardWidth,
                    cardHeight
            );

            graphics.fill(
                    x,
                    y,
                    x + cardWidth,
                    y + cardHeight,
                    hovered ? CARD_HOVER : CARD
            );

            graphics.drawString(
                    font,
                    name,
                    x + 10,
                    y + 6,
                    TEXT,
                    false
            );

            int maxTextWidth = Math.max(0, cardWidth - 42);
            String trimmedDescription = trimToWidth(description, maxTextWidth);

            graphics.drawString(
                    font,
                    trimmedDescription,
                    x + 10,
                    y + 19,
                    TEXT_MUTED,
                    false
            );

            drawSwitch(
                    graphics,
                    x + cardWidth - 29,
                    y + 11,
                    enabled
            );
        }

        private String trimToWidth(String text, int maxWidth) {
            if (font.width(text) <= maxWidth) {
                return text;
            }

            String ellipsis = "...";
            int ellipsisWidth = font.width(ellipsis);

            if (maxWidth <= ellipsisWidth) {
                return "";
            }

            StringBuilder builder = new StringBuilder();

            for (int i = 0; i < text.length(); i++) {
                String next = builder.toString() + text.charAt(i);

                if (font.width(next) + ellipsisWidth > maxWidth) {
                    break;
                }

                builder.append(text.charAt(i));
            }

            return builder + ellipsis;
        }

        private void drawSwitch(
                GuiGraphics graphics,
                int x,
                int y,
                boolean enabled
        ) {
            int trackColor = enabled ? ACCENT : SWITCH_OFF;
            int knobColor = enabled ? WHITE : SWITCH_KNOB_OFF;

            graphics.fill(x + 2, y, x + 19, y + 12, trackColor);
            graphics.fill(x, y + 2, x + 21, y + 10, trackColor);

            int knobX = enabled ? x + 12 : x + 2;

            graphics.fill(
                    knobX + 1,
                    y + 2,
                    knobX + 7,
                    y + 10,
                    knobColor
            );

            graphics.fill(
                    knobX,
                    y + 3,
                    knobX + 8,
                    y + 9,
                    knobColor
            );
        }

        private void drawEmptyCategory(GuiGraphics graphics) {
            int x = panelX();
            int y = panelY();

            String title = selectedTab == TAB_VISUALS
                    ? "VISUALS"
                    : "UTILITIES";

            graphics.drawString(
                    font,
                    title,
                    x + 14,
                    y + 54,
                    TEXT_MUTED,
                    false
            );

            graphics.drawString(
                    font,
                    "Modules coming soon",
                    x + 14,
                    y + 78,
                    TEXT,
                    false
            );

            graphics.drawString(
                    font,
                    "This category is not implemented yet.",
                    x + 14,
                    y + 93,
                    TEXT_MUTED,
                    false
            );
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return super.mouseClicked(mouseX, mouseY, button);
            }

            int x = panelX();
            int y = panelY();
            int tabStartX = x + 130;

            if (isInside(mouseX, mouseY, tabStartX - 4, y + 9, 55, 20)) {
                selectedTab = TAB_VISUALS;
                return true;
            }

            if (isInside(mouseX, mouseY, tabStartX + 54, y + 9, 40, 20)) {
                selectedTab = TAB_HUD;
                return true;
            }

            if (isInside(mouseX, mouseY, tabStartX + 98, y + 9, 64, 20)) {
                selectedTab = TAB_UTILITIES;
                return true;
            }

            if (selectedTab != TAB_HUD) {
                return super.mouseClicked(mouseX, mouseY, button);
            }

            int gap = 8;
            int cardX = x + 14;
            int cardY = y + 72;
            int availableWidth = panelWidth() - 28;

            boolean twoColumns = availableWidth >= 300;
            int cardWidth = twoColumns
                    ? (availableWidth - gap) / 2
                    : availableWidth;

            int secondX = twoColumns
                    ? cardX + cardWidth + gap
                    : cardX;

            int secondY = twoColumns
                    ? cardY
                    : cardY + 42;

            int thirdY = twoColumns
                    ? cardY + 42
                    : cardY + 84;

            if (isInside(mouseX, mouseY, cardX, cardY, cardWidth, 34)) {
                settings.fpsPing = !settings.fpsPing;
                ConfigManager.save(settings);
                return true;
            }

            if (isInside(mouseX, mouseY, secondX, secondY, cardWidth, 34)) {
                settings.cps = !settings.cps;
                ConfigManager.save(settings);
                return true;
            }

            if (isInside(mouseX, mouseY, cardX, thirdY, cardWidth, 34)) {
                settings.keystrokes = !settings.keystrokes;
                ConfigManager.save(settings);
                return true;
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                onClose();
                return true;
            }

            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private boolean isInside(
                double mouseX,
                double mouseY,
                int x,
                int y,
                int boxWidth,
                int boxHeight
        ) {
            return mouseX >= x
                    && mouseX < x + boxWidth
                    && mouseY >= y
                    && mouseY < y + boxHeight;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}