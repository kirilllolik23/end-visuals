package com.example.examplemod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

            // Фиолетовая полоска слева.
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
     * Простое меню End Visuals.
     *
     * Открывается на правый Shift и пока управляет тремя HUD-элементами.
     * Кнопки используют стандартные виджеты Minecraft.
     */
    public static final class EndVisualsScreen extends Screen {
        private static final int PANEL_BACKGROUND = 0xE0140B20;
        private static final int PANEL_BORDER = 0xFF9D5CE8;
        private static final int TITLE_COLOR = 0xFFE0C2FF;
        private static final int TEXT_COLOR = 0xFFDDCBEA;
        private static final int DIM_BACKGROUND = 0xB0000000;

        private static final int PANEL_WIDTH = 230;
        private static final int PANEL_HEIGHT = 190;

        private final HudSettings settings;

        public EndVisualsScreen(HudSettings settings) {
            super(Component.literal("End Visuals"));
            this.settings = settings;
        }

        @Override
        protected void init() {
            int panelX = (width - PANEL_WIDTH) / 2;
            int panelY = (height - PANEL_HEIGHT) / 2;

            int buttonX = panelX + 20;
            int buttonWidth = PANEL_WIDTH - 40;
            int buttonHeight = 20;
            int startY = panelY + 52;

            addRenderableWidget(
                    Button.builder(
                            toggleText("FPS / Ping", settings.fpsPing),
                            button -> {
                                settings.fpsPing = !settings.fpsPing;
                                button.setMessage(
                                        toggleText("FPS / Ping", settings.fpsPing)
                                );
                                ConfigManager.save(settings);
                            }
                    ).bounds(
                            buttonX, startY, buttonWidth, buttonHeight
                    ).build()
            );

            addRenderableWidget(
                    Button.builder(
                            toggleText("CPS Counter", settings.cps),
                            button -> {
                                settings.cps = !settings.cps;
                                button.setMessage(
                                        toggleText("CPS Counter", settings.cps)
                                );
                                ConfigManager.save(settings);
                            }
                    ).bounds(
                            buttonX, startY + 24, buttonWidth, buttonHeight
                    ).build()
            );

            addRenderableWidget(
                    Button.builder(
                            toggleText("Keystrokes", settings.keystrokes),
                            button -> {
                                settings.keystrokes = !settings.keystrokes;
                                button.setMessage(
                                        toggleText(
                                                "Keystrokes",
                                                settings.keystrokes
                                        )
                                );
                                ConfigManager.save(settings);
                            }
                    ).bounds(
                            buttonX, startY + 48, buttonWidth, buttonHeight
                    ).build()
            );

            int bottomY = startY + 82;
            int halfWidth = (buttonWidth - 4) / 2;

            addRenderableWidget(
                    Button.builder(
                            Component.literal("Reset"),
                            button -> {
                                settings.fpsPing = true;
                                settings.cps = true;
                                settings.keystrokes = true;

                                ConfigManager.save(settings);

                                // Перестраиваем кнопки, чтобы обновить надписи.
                                clearWidgets();
                                init();
                            }
                    ).bounds(
                            buttonX, bottomY, halfWidth, buttonHeight
                    ).build()
            );

            addRenderableWidget(
                    Button.builder(
                            Component.literal("Done"),
                            button -> onClose()
                    ).bounds(
                            buttonX + halfWidth + 4,
                            bottomY,
                            halfWidth,
                            buttonHeight
                    ).build()
            );
        }

        private static Component toggleText(String name, boolean enabled) {
            return Component.literal(
                    name + ": " + (enabled ? "ON" : "OFF")
            );
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            int panelX = (width - PANEL_WIDTH) / 2;
            int panelY = (height - PANEL_HEIGHT) / 2;

            graphics.fill(0, 0, width, height, DIM_BACKGROUND);

            // Рамка и фон панели.
            graphics.fill(
                    panelX - 1,
                    panelY - 1,
                    panelX + PANEL_WIDTH + 1,
                    panelY + PANEL_HEIGHT + 1,
                    PANEL_BORDER
            );

            graphics.fill(
                    panelX,
                    panelY,
                    panelX + PANEL_WIDTH,
                    panelY + PANEL_HEIGHT,
                    PANEL_BACKGROUND
            );

            // Звезда Незера — предмет Minecraft, отдельная текстура не нужна.
            graphics.renderItem(
                    new ItemStack(Items.NETHER_STAR),
                    panelX + 16,
                    panelY + 13
            );

            graphics.drawString(
                    font,
                    "End Visuals",
                    panelX + 40,
                    panelY + 17,
                    TITLE_COLOR,
                    true
            );

            graphics.drawString(
                    font,
                    "HUD settings",
                    panelX + 20,
                    panelY + 38,
                    TEXT_COLOR,
                    false
            );

            // Отрисовка стандартных кнопок и подсказок Minecraft.
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        /**
         * Меню не ставит одиночную игру на паузу.
         */
        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}