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
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
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
            HudSettings settings = ConfigManager.load();

            ClientHud hud = new ClientHud(settings);
            ExtraHud extraHud = new ExtraHud(settings);

            MinecraftForge.EVENT_BUS.register(hud);

            FMLJavaModLoadingContext.get()
                    .getModEventBus()
                    .addListener(hud::onAddGuiLayers);

            FMLJavaModLoadingContext.get()
                    .getModEventBus()
                    .addListener(extraHud::onAddGuiLayers);
        }
    }

    /**
     * Настройки визуальных элементов.
     *
     * Старые настройки fpsPing, cps и keystrokes сохраняют свои имена,
     * поэтому их значения будут прочитаны из предыдущего конфига.
     */
    public static final class HudSettings {
        public boolean fpsPing = true;
        public boolean cps = true;
        public boolean keystrokes = true;

        public boolean coordinates = true;
        public boolean armor = true;
        public boolean potions = true;

        public boolean crosshair = false;

        public int fpsPingScale = 70;
        public int cpsScale = 70;
        public int keystrokesScale = 70;

        public int coordinatesScale = 70;
        public int armorScale = 70;
        public int potionsScale = 70;

        public int crosshairScale = 100;

        public void normalize() {
            fpsPingScale = clampScale(fpsPingScale);
            cpsScale = clampScale(cpsScale);
            keystrokesScale = clampScale(keystrokesScale);

            coordinatesScale = clampScale(coordinatesScale);
            armorScale = clampScale(armorScale);
            potionsScale = clampScale(potionsScale);

            crosshairScale = clampScale(crosshairScale);
        }

        private static int clampScale(int value) {
            return Math.max(50, Math.min(150, value));
        }

        public boolean isEnabled(int id) {
            return switch (id) {
                case 0 -> fpsPing;
                case 1 -> cps;
                case 2 -> keystrokes;
                case 3 -> coordinates;
                case 4 -> armor;
                case 5 -> potions;
                case 6 -> crosshair;
                default -> false;
            };
        }

        public void setEnabled(int id, boolean enabled) {
            switch (id) {
                case 0 -> fpsPing = enabled;
                case 1 -> cps = enabled;
                case 2 -> keystrokes = enabled;
                case 3 -> coordinates = enabled;
                case 4 -> armor = enabled;
                case 5 -> potions = enabled;
                case 6 -> crosshair = enabled;
                default -> {
                }
            }
        }

        public int getScale(int id) {
            return switch (id) {
                case 0 -> fpsPingScale;
                case 1 -> cpsScale;
                case 2 -> keystrokesScale;
                case 3 -> coordinatesScale;
                case 4 -> armorScale;
                case 5 -> potionsScale;
                case 6 -> crosshairScale;
                default -> 100;
            };
        }

        public void setScale(int id, int value) {
            int scale = clampScale(value);

            switch (id) {
                case 0 -> fpsPingScale = scale;
                case 1 -> cpsScale = scale;
                case 2 -> keystrokesScale = scale;
                case 3 -> coordinatesScale = scale;
                case 4 -> armorScale = scale;
                case 5 -> potionsScale = scale;
                case 6 -> crosshairScale = scale;
                default -> {
                }
            }
        }
    }

    /**
     * Сохранение клиентских настроек в config/endvisuals.json.
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
                String json = Files.readString(
                        path,
                        StandardCharsets.UTF_8
                );

                HudSettings settings = GSON.fromJson(
                        json,
                        HudSettings.class
                );

                if (settings == null) {
                    LOGGER.warn(
                            "End Visuals config is empty; using defaults."
                    );
                    return new HudSettings();
                }

                settings.normalize();
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
                settings.normalize();
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
     * Счётчики и левый столбец HUD.
     *
     * CPS считает физические нажатия мыши, а не подтверждённые удары.
     */
    public static final class ClientHud {
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

        private final Deque<Long> leftClicks = new ArrayDeque<>();
        private final Deque<Long> rightClicks = new ArrayDeque<>();

        private final HudSettings settings;

        public ClientHud(HudSettings settings) {
            this.settings = settings;
        }

        /**
         * Регистрируем HUD и условие отображения ванильного прицела.
         */
        public void onAddGuiLayers(AddGuiOverlayLayersEvent event) {
            ForgeLayeredDraw layeredDraw = event.getLayeredDraw();

            layeredDraw.addConditionTo(
                    ForgeLayeredDraw.PRE_SLEEP_STACK,
                    ForgeLayeredDraw.CROSSHAIR,
                    () -> !settings.crosshair
            );

            layeredDraw.add(
                    ResourceLocation.fromNamespaceAndPath(
                            ExampleMod.MOD_ID,
                            "client_hud"
                    ),
                    (graphics, deltaTracker) -> renderHud(graphics)
            );
        }

        @SubscribeEvent
        public void onKey(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();

            if (event.getKey() != GLFW.GLFW_KEY_RIGHT_SHIFT
                    || event.getAction() != GLFW.GLFW_PRESS) {
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

            int y = 8;

            if (settings.fpsPing) {
                int scale = settings.fpsPingScale;

                pushScaled(graphics, 8, y, scale);
                try {
                    drawFpsPing(graphics, mc);
                } finally {
                    graphics.pose().popPose();
                }

                y += scaledHeight(20, scale) + 4;
            }

            if (settings.cps) {
                int scale = settings.cpsScale;

                pushScaled(graphics, 8, y, scale);
                try {
                    drawCps(graphics, mc);
                } finally {
                    graphics.pose().popPose();
                }

                y += scaledHeight(20, scale) + 4;
            }

            if (settings.keystrokes) {
                int scale = settings.keystrokesScale;

                pushScaled(graphics, 8, y, scale);
                try {
                    drawKeystrokes(graphics, mc);
                } finally {
                    graphics.pose().popPose();
                }
            }

            if (settings.crosshair
                    && mc.options.getCameraType().isFirstPerson()) {
                drawCrosshair(graphics);
            }
        }

        private void drawFpsPing(GuiGraphics graphics, Minecraft mc) {
            int fps = mc.getFps();
            int ping = getPing(mc);

            drawPanel(graphics, 150, 20);

            graphics.drawString(
                    mc.font,
                    "FPS: " + fps,
                    6,
                    6,
                    fpsColor(fps),
                    true
            );

            graphics.drawString(
                    mc.font,
                    "Ping: " + (ping < 0 ? "N/A" : ping + " ms"),
                    76,
                    6,
                    ping < 0 ? WHITE : pingColor(ping),
                    true
            );
        }

        private void drawCps(GuiGraphics graphics, Minecraft mc) {
            int left = leftClicks.size();
            int right = rightClicks.size();

            drawPanel(graphics, 150, 20);

            graphics.drawString(
                    mc.font,
                    "CPS L/R: " + left + "/" + right
                            + " | Total: " + (left + right),
                    6,
                    6,
                    WHITE,
                    true
            );
        }

        private void drawPanel(
                GuiGraphics graphics,
                int width,
                int height
        ) {
            graphics.fill(0, 0, width, height, BACKGROUND);
            graphics.fill(0, 0, 2, height, ACCENT);
        }

        private void drawKeystrokes(GuiGraphics graphics, Minecraft mc) {
            int size = 20;
            int gap = 2;

            drawKey(
                    graphics, mc, "W",
                    size + gap, 0,
                    size, size,
                    mc.options.keyUp.isDown()
            );

            drawKey(
                    graphics, mc, "A",
                    0, size + gap,
                    size, size,
                    mc.options.keyLeft.isDown()
            );

            drawKey(
                    graphics, mc, "S",
                    size + gap, size + gap,
                    size, size,
                    mc.options.keyDown.isDown()
            );

            drawKey(
                    graphics, mc, "D",
                    2 * (size + gap), size + gap,
                    size, size,
                    mc.options.keyRight.isDown()
            );

            int mouseY = 2 * (size + gap);

            drawKey(
                    graphics, mc, "LMB",
                    0, mouseY,
                    31, size,
                    mc.options.keyAttack.isDown()
            );

            drawKey(
                    graphics, mc, "RMB",
                    33, mouseY,
                    31, size,
                    mc.options.keyUse.isDown()
            );

            int bottomY = mouseY + size + gap;

            drawKey(
                    graphics, mc, "SPACE",
                    0, bottomY,
                    42, size,
                    mc.options.keyJump.isDown()
            );

            drawKey(
                    graphics, mc, "SHIFT",
                    44, bottomY,
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

        /**
         * Простой фиолетовый прицел. Это только отрисовка:
         * дальность взаимодействия и попадания не изменяются.
         */
        private void drawCrosshair(GuiGraphics graphics) {
            int centerX = graphics.guiWidth() / 2;
            int centerY = graphics.guiHeight() / 2;

            float scale = settings.crosshairScale / 100.0f;

            graphics.pose().pushPose();
            try {
                graphics.pose().translate(centerX, centerY, 0);
                graphics.pose().scale(scale, scale, 1.0f);

                int color = 0xFFD5A6FF;

                // Четыре линии и точка в центре.
                graphics.fill(-1, -7, 1, -2, color);
                graphics.fill(-1, 3, 1, 8, color);
                graphics.fill(-7, -1, -2, 1, color);
                graphics.fill(3, -1, 8, 1, color);
                graphics.fill(0, 0, 1, 1, WHITE);
            } finally {
                graphics.pose().popPose();
            }
        }

        private void pushScaled(
                GuiGraphics graphics,
                int x,
                int y,
                int percent
        ) {
            float scale = percent / 100.0f;

            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1.0f);
        }

        private int scaledHeight(int height, int percent) {
            return (int) Math.ceil(height * percent / 100.0);
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
            if (fps >= 100) return GREEN;
            if (fps >= 50) return YELLOW;
            return RED;
        }

        private int pingColor(int ping) {
            if (ping <= 80) return GREEN;
            if (ping <= 150) return YELLOW;
            return RED;
        }
    }

    /**
     * Меню End Visuals.
     *
     * Левая кнопка по названию или переключателю включает модуль.
     * Кнопки «-» и «+» меняют его размер.
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

        private static final String[] NAMES = {
                "FPS / Ping",
                "CPS Counter",
                "Keystrokes",
                "Coordinates",
                "Armor HUD",
                "Potion HUD",
                "Crosshair"
        };

        private final HudSettings settings;
        private final ItemStack star = new ItemStack(Items.NETHER_STAR);

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

            roundedRect(
                    graphics,
                    x - 1,
                    y - 1,
                    w + 2,
                    PANEL_HEIGHT + 2,
                    7,
                    PANEL_BORDER
            );

            roundedRect(
                    graphics,
                    x,
                    y,
                    w,
                    PANEL_HEIGHT,
                    6,
                    PANEL
            );

            roundedRect(
                    graphics,
                    x,
                    y,
                    w,
                    HEADER_HEIGHT,
                    6,
                    TOP_BAR
            );

            // Закрываем нижние скругления верхней полосы.
            graphics.fill(
                    x,
                    y + HEADER_HEIGHT - 6,
                    x + w,
                    y + HEADER_HEIGHT,
                    TOP_BAR
            );

            graphics.renderItem(star, x + 12, y + 12);

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
                    graphics, "Visuals",
                    tabStartX, y + 14,
                    TAB_VISUALS, mouseX, mouseY
            );

            drawTab(
                    graphics, "HUD",
                    tabStartX + 58, y + 14,
                    TAB_HUD, mouseX, mouseY
            );

            drawTab(
                    graphics, "Utilities",
                    tabStartX + 102, y + 14,
                    TAB_UTILITIES, mouseX, mouseY
            );

            graphics.fill(
                    x + 12,
                    y + HEADER_HEIGHT,
                    x + w - 12,
                    y + HEADER_HEIGHT + 1,
                    PANEL_BORDER
            );

            if (selectedTab == TAB_HUD) {
                drawCategory(graphics, mouseX, mouseY, 0, 6, "HUD MODULES");
            } else if (selectedTab == TAB_VISUALS) {
                drawCategory(graphics, mouseX, mouseY, 6, 7, "VISUALS");
            } else {
                drawEmptyCategory(graphics);
            }

            graphics.drawString(
                    font,
                    "RSHIFT / ESC: close",
                    x + 14,
                    y + PANEL_HEIGHT - 18,
                    TEXT_MUTED,
                    false
            );

            graphics.drawString(
                    font,
                    "- / +: size",
                    x + w - 78,
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

        private void drawCategory(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                int start,
                int end,
                String title
        ) {
            int x = panelX();
            int y = panelY();

            graphics.drawString(
                    font,
                    title,
                    x + 14,
                    y + 54,
                    TEXT_MUTED,
                    false
            );

            int availableWidth = panelWidth() - 28;
            boolean twoColumns = availableWidth >= 300;
            int gap = 8;

            int cardWidth = twoColumns
                    ? (availableWidth - gap) / 2
                    : availableWidth;

            for (int id = start; id < end; id++) {
                int index = id - start;
                int column = twoColumns ? index % 2 : 0;
                int row = twoColumns ? index / 2 : index;

                int cardX = x + 14 + column * (cardWidth + gap);
                int cardY = y + 72 + row * 48;

                drawCard(
                        graphics,
                        id,
                        cardX,
                        cardY,
                        cardWidth,
                        mouseX,
                        mouseY
                );
            }
        }

        private void drawCard(
                GuiGraphics graphics,
                int id,
                int x,
                int y,
                int cardWidth,
                int mouseX,
                int mouseY
        ) {
            int cardHeight = 40;

            boolean hovered = isInside(
                    mouseX,
                    mouseY,
                    x,
                    y,
                    cardWidth,
                    cardHeight
            );

            roundedRect(
                    graphics,
                    x,
                    y,
                    cardWidth,
                    cardHeight,
                    4,
                    hovered ? CARD_HOVER : CARD
            );

            graphics.drawString(
                    font,
                    NAMES[id],
                    x + 8,
                    y + 6,
                    TEXT,
                    false
            );

            drawSwitch(
                    graphics,
                    x + cardWidth - 29,
                    y + 5,
                    settings.isEnabled(id)
            );

            graphics.drawString(
                    font,
                    "Size",
                    x + 8,
                    y + 25,
                    TEXT_MUTED,
                    false
            );

            int minusX = x + cardWidth - 78;
            int plusX = x + cardWidth - 22;

            drawSmallButton(graphics, "-", minusX, y + 22);
            drawSmallButton(graphics, "+", plusX, y + 22);

            String value = settings.getScale(id) + "%";

            graphics.drawString(
                    font,
                    value,
                    minusX + 16,
                    y + 25,
                    ACCENT_LIGHT,
                    false
            );
        }

        private void drawSmallButton(
                GuiGraphics graphics,
                String text,
                int x,
                int y
        ) {
            roundedRect(
                    graphics,
                    x,
                    y,
                    14,
                    14,
                    3,
                    0xFF393044
            );

            graphics.drawString(
                    font,
                    text,
                    x + (14 - font.width(text)) / 2,
                    y + 3,
                    WHITE,
                    false
            );
        }

        private void drawSwitch(
                GuiGraphics graphics,
                int x,
                int y,
                boolean enabled
        ) {
            int trackColor = enabled ? ACCENT : SWITCH_OFF;
            int knobColor = enabled ? WHITE : SWITCH_KNOB_OFF;

            roundedRect(graphics, x, y, 21, 12, 5, trackColor);

            int knobX = enabled ? x + 11 : x + 2;

            roundedRect(
                    graphics,
                    knobX,
                    y + 2,
                    8,
                    8,
                    3,
                    knobColor
            );
        }

        private void drawEmptyCategory(GuiGraphics graphics) {
            int x = panelX();
            int y = panelY();

            graphics.drawString(
                    font,
                    "UTILITIES",
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
        }

        /**
         * Рисует скруглённый прямоугольник горизонтальными полосами.
         * Используются только GuiGraphics.fill, без отдельных текстур.
         */
        private void roundedRect(
                GuiGraphics graphics,
                int x,
                int y,
                int rectWidth,
                int rectHeight,
                int radius,
                int color
        ) {
            if (rectWidth <= 0 || rectHeight <= 0) {
                return;
            }

            int r = Math.min(
                    radius,
                    Math.min(rectWidth / 2, rectHeight / 2)
            );

            if (r <= 0) {
                graphics.fill(
                        x, y,
                        x + rectWidth,
                        y + rectHeight,
                        color
                );
                return;
            }

            for (int row = 0; row < rectHeight; row++) {
                int inset = 0;

                if (row < r) {
                    double dy = r - row - 0.5;
                    inset = (int) Math.ceil(
                            r - Math.sqrt(r * r - dy * dy)
                    );
                } else if (row >= rectHeight - r) {
                    double dy = row - (rectHeight - r) + 0.5;
                    inset = (int) Math.ceil(
                            r - Math.sqrt(r * r - dy * dy)
                    );
                }

                graphics.fill(
                        x + inset,
                        y + row,
                        x + rectWidth - inset,
                        y + row + 1,
                        color
                );
            }
        }

        @Override
        public boolean mouseClicked(
                double mouseX,
                double mouseY,
                int button
        ) {
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

            int start;
            int end;

            if (selectedTab == TAB_HUD) {
                start = 0;
                end = 6;
            } else if (selectedTab == TAB_VISUALS) {
                start = 6;
                end = 7;
            } else {
                return super.mouseClicked(mouseX, mouseY, button);
            }

            int availableWidth = panelWidth() - 28;
            boolean twoColumns = availableWidth >= 300;
            int gap = 8;

            int cardWidth = twoColumns
                    ? (availableWidth - gap) / 2
                    : availableWidth;

            for (int id = start; id < end; id++) {
                int index = id - start;
                int column = twoColumns ? index % 2 : 0;
                int row = twoColumns ? index / 2 : index;

                int cardX = x + 14 + column * (cardWidth + gap);
                int cardY = y + 72 + row * 48;

                int minusX = cardX + cardWidth - 78;
                int plusX = cardX + cardWidth - 22;

                if (isInside(mouseX, mouseY, minusX, cardY + 22, 14, 14)) {
                    settings.setScale(id, settings.getScale(id) - 10);
                    ConfigManager.save(settings);
                    return true;
                }

                if (isInside(mouseX, mouseY, plusX, cardY + 22, 14, 14)) {
                    settings.setScale(id, settings.getScale(id) + 10);
                    ConfigManager.save(settings);
                    return true;
                }

                if (isInside(mouseX, mouseY, cardX, cardY, cardWidth, 20)) {
                    settings.setEnabled(id, !settings.isEnabled(id));
                    ConfigManager.save(settings);
                    return true;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(
                int keyCode,
                int scanCode,
                int modifiers
        ) {
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