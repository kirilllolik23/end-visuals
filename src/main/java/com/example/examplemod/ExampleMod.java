package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
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

import java.util.ArrayDeque;
import java.util.Deque;

@Mod(ExampleMod.MOD_ID)
public final class ExampleMod {
    public static final String MOD_ID = "examplemod";

    public ExampleMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientHud hud = new ClientHud();

            // Клики и тики приходят на общую шину Forge.
            MinecraftForge.EVENT_BUS.register(hud);

            // Регистрация слоя HUD происходит на шине мода.
            FMLJavaModLoadingContext.get()
                    .getModEventBus()
                    .addListener(hud::onAddGuiLayers);
        }
    }

    /**
     * Временный HUD для проверки окружения.
     *
     * На следующем этапе этот класс будет разделён на:
     * ModuleManager, HudManager и отдельные HUD-модули.
     */
    public static final class ClientHud {
        private final Deque<Long> leftClicks = new ArrayDeque<>();
        private final Deque<Long> rightClicks = new ArrayDeque<>();

        private static final long CPS_WINDOW_MS = 1_000L;

        private static final int WHITE = 0xFFFFFFFF;
        private static final int GREEN = 0xFF55FF55;
        private static final int YELLOW = 0xFFFFFF55;
        private static final int RED = 0xFFFF5555;
        private static final int BACKGROUND = 0x90000000;
        private static final int KEY_IDLE = 0xA0303030;
        private static final int KEY_PRESSED = 0xB0EEEEEE;
        private static final int KEY_TEXT_PRESSED = 0xFF101010;

        /**
         * Добавляет наш HUD как слой поверх существующих слоёв интерфейса.
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
         * Считаем реальные нажатия мыши, а не количество атак,
         * зарегистрированных сервером.
         */
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

        /**
         * Удаляем старые клики даже тогда, когда HUD временно не рисуется.
         */
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

        /**
         * Рисует временные HUD-элементы.
         */
        private void renderHud(GuiGraphics graphics) {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null || mc.options.hideGui) {
                return;
            }

            drawStats(graphics, mc, 8, 8);
            drawKeystrokes(graphics, mc, 8, 50);
        }

        private void drawStats(
                GuiGraphics graphics,
                Minecraft mc,
                int x,
                int y
        ) {
            int fps = mc.getFps();
            int ping = getPing(mc);

            int left = leftClicks.size();
            int right = rightClicks.size();

            graphics.fill(x - 3, y - 3, x + 120, y + 34, BACKGROUND);

            graphics.drawString(
                    mc.font,
                    "FPS: " + fps,
                    x,
                    y,
                    fpsColor(fps),
                    true
            );

            graphics.drawString(
                    mc.font,
                    "Ping: " + (ping < 0 ? "N/A" : ping + " ms"),
                    x + 55,
                    y,
                    ping < 0 ? WHITE : pingColor(ping),
                    true
            );

            graphics.drawString(
                    mc.font,
                    "CPS L/R: " + left + "/" + right
                            + " | Total: " + (left + right),
                    x,
                    y + 14,
                    WHITE,
                    true
            );
        }

        private void drawKeystrokes(
                GuiGraphics graphics,
                Minecraft mc,
                int x,
                int y
        ) {
            int size = 20;
            int gap = 2;

            drawKey(graphics, mc, "W", x + size + gap, y, size, size,
                    mc.options.keyUp.isDown());

            drawKey(graphics, mc, "A", x, y + size + gap, size, size,
                    mc.options.keyLeft.isDown());

            drawKey(graphics, mc, "S", x + size + gap, y + size + gap,
                    size, size, mc.options.keyDown.isDown());

            drawKey(graphics, mc, "D", x + 2 * (size + gap), y + size + gap,
                    size, size, mc.options.keyRight.isDown());

            int mouseY = y + 2 * (size + gap);
            int mouseWidth = 31;

            drawKey(graphics, mc, "LMB", x, mouseY, mouseWidth, size,
                    mc.options.keyAttack.isDown());

            drawKey(graphics, mc, "RMB", x + mouseWidth + gap, mouseY,
                    mouseWidth, size, mc.options.keyUse.isDown());

            int bottomY = mouseY + size + gap;

            drawKey(graphics, mc, "SPACE", x, bottomY, 42, size,
                    mc.options.keyJump.isDown());

            drawKey(graphics, mc, "SHIFT", x + 44, bottomY, 42, size,
                    mc.options.keyShift.isDown());
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
}