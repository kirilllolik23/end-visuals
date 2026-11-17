package com.example.examplemod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Клиентские визуальные эффекты:
 *
 * - отключение или замена огня поверх экрана;
 * - локальные частицы при попытке атаки живой сущности.
 *
 * Класс не отправляет собственных пакетов и не выполняет атаки.
 */
public final class CombatVisuals {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final String[] FIRE_MODES = {
            "NORMAL",
            "LOW",
            "OFF"
    };

    private static final String[] PARTICLE_NAMES = {
            "CRIT",
            "ENCHANTED HIT",
            "ELECTRIC SPARK"
    };

    private static Settings settings;

    private CombatVisuals() {
    }

    /**
     * Настройки сохраняются отдельно от основного HUD-конфига.
     */
    public static final class Settings {
        // 0 = обычный, 1 = компактный, 2 = скрытый.
        public int fireMode = 0;

        // Прозрачность декоративного LOW FIRE.
        public int fireOpacity = 50;

        public boolean hitParticles = true;

        // 0 = CRIT, 1 = ENCHANTED_HIT, 2 = ELECTRIC_SPARK.
        public int particleType = 0;

        public int particleCount = 8;

        public void normalize() {
            fireMode = clamp(fireMode, 0, FIRE_MODES.length - 1);

            if (fireOpacity != 25
                    && fireOpacity != 50
                    && fireOpacity != 75
                    && fireOpacity != 100) {
                fireOpacity = 50;
            }

            particleType = clamp(
                    particleType,
                    0,
                    PARTICLE_NAMES.length - 1
            );

            particleCount = clamp(particleCount, 1, 30);
        }
    }

    private static Settings settings() {
        if (settings == null) {
            settings = load();
        }

        return settings;
    }

    private static Path configPath() {
        return Minecraft.getInstance()
                .gameDirectory
                .toPath()
                .resolve("config")
                .resolve("endvisuals-combat.json");
    }

    private static Settings load() {
        Path path = configPath();

        if (!Files.exists(path)) {
            Settings defaults = new Settings();
            defaults.normalize();
            return defaults;
        }

        try {
            String json = Files.readString(
                    path,
                    StandardCharsets.UTF_8
            );

            Settings loaded = GSON.fromJson(json, Settings.class);

            if (loaded == null) {
                LOGGER.warn("End Visuals combat config is empty.");
                return new Settings();
            }

            loaded.normalize();
            return loaded;
        } catch (Exception exception) {
            LOGGER.error(
                    "Could not load End Visuals combat settings.",
                    exception
            );

            return new Settings();
        }
    }

    private static void save() {
        try {
            Settings current = settings();
            current.normalize();

            Path path = configPath();
            Files.createDirectories(path.getParent());

            Files.writeString(
                    path,
                    GSON.toJson(current),
                    StandardCharsets.UTF_8
            );
        } catch (Exception exception) {
            LOGGER.error(
                    "Could not save End Visuals combat settings.",
                    exception
            );
        }
    }

    /**
     * Слой компактного декоративного огня.
     */
    @Mod.EventBusSubscriber(
            modid = ExampleMod.MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.MOD,
            value = Dist.CLIENT
    )
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onAddGuiLayers(AddGuiOverlayLayersEvent event) {
            event.getLayeredDraw().add(
                    ResourceLocation.fromNamespaceAndPath(
                            ExampleMod.MOD_ID,
                            "combat_visuals"
                    ),
                    (graphics, deltaTracker) -> drawLowFire(graphics)
            );
        }
    }

    /**
     * Огонь, локальная атака и открытие настроек.
     */
    @Mod.EventBusSubscriber(
            modid = ExampleMod.MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.FORGE,
            value = Dist.CLIENT
    )
    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        /**
         * Отменяем только огонь поверх экрана.
         * Оверлеи воды и блока не затрагиваются.
         */
        @SubscribeEvent
        public static void onScreenEffect(
                RenderBlockScreenEffectEvent event
        ) {
            if (event.getOverlayType()
                    != RenderBlockScreenEffectEvent.OverlayType.FIRE) {
                return;
            }

            if (settings().fireMode != 0) {
                event.setCanceled(true);
            }
        }

        /**
         * Частицы обозначают локальную попытку атаки.
         * Сервер может не засчитать урон.
         */
        @SubscribeEvent
        public static void onAttack(AttackEntityEvent event) {
            Minecraft mc = Minecraft.getInstance();

            if (!settings().hitParticles
                    || mc.player == null
                    || mc.level == null) {
                return;
            }

            // Не обрабатываем события других игроков.
            if (event.getEntity() != mc.player) {
                return;
            }

            Entity target = event.getTarget();

            if (!(target instanceof LivingEntity)) {
                return;
            }

            ParticleOptions particle = switch (settings().particleType) {
                case 1 -> ParticleTypes.ENCHANTED_HIT;
                case 2 -> ParticleTypes.ELECTRIC_SPARK;
                default -> ParticleTypes.CRIT;
            };

            for (int i = 0; i < settings().particleCount; i++) {
                double x = target.getX()
                        + (mc.level.random.nextDouble() - 0.5)
                        * target.getBbWidth();

                double y = target.getY()
                        + mc.level.random.nextDouble()
                        * target.getBbHeight();

                double z = target.getZ()
                        + (mc.level.random.nextDouble() - 0.5)
                        * target.getBbWidth();

                double velocityX =
                        (mc.level.random.nextDouble() - 0.5) * 0.08;

                double velocityY =
                        mc.level.random.nextDouble() * 0.08;

                double velocityZ =
                        (mc.level.random.nextDouble() - 0.5) * 0.08;

                mc.level.addParticle(
                        particle,
                        x,
                        y,
                        z,
                        velocityX,
                        velocityY,
                        velocityZ
                );
            }
        }

        @SubscribeEvent
        public static void onKey(InputEvent.Key event) {
            if (event.getKey() != GLFW.GLFW_KEY_P
                    || event.getAction() != GLFW.GLFW_PRESS) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null || mc.screen != null) {
                return;
            }

            mc.setScreen(new CombatSettingsScreen());
        }
    }

    /**
     * Рисует компактный декоративный огонь снизу экрана.
     *
     * Это намеренно не ванильная текстура: событие позволяет
     * отменить её, но не предоставляет отдельный безопасный
     * интерфейс для изменения её прозрачности.
     */
    private static void drawLowFire(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();

        if (settings().fireMode != 1
                || mc.player == null
                || mc.options.hideGui
                || !mc.player.isOnFire()) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        if (width < 40 || height < 40) {
            return;
        }

        int alpha = Math.round(
                255.0f * settings().fireOpacity / 100.0f
        );

        int outer = (alpha << 24) | 0xE94C16;
        int middle = (alpha << 24) | 0xFF8A1C;
        int inner = (alpha << 24) | 0xFFD35A;

        int baseY = height;

        // Левая группа пламени.
        flame(graphics, 8, baseY, 28, outer, middle, inner);
        flame(graphics, 29, baseY, 19, outer, middle, inner);

        // Правая группа пламени.
        flame(graphics, width - 38, baseY, 28, outer, middle, inner);
        flame(graphics, width - 58, baseY, 19, outer, middle, inner);
    }

    private static void flame(
            GuiGraphics graphics,
            int x,
            int bottom,
            int flameHeight,
            int outer,
            int middle,
            int inner
    ) {
        graphics.fill(
                x,
                bottom - flameHeight / 2,
                x + 22,
                bottom,
                outer
        );

        graphics.fill(
                x + 5,
                bottom - flameHeight,
                x + 17,
                bottom,
                outer
        );

        graphics.fill(
                x + 5,
                bottom - flameHeight / 2,
                x + 17,
                bottom,
                middle
        );

        graphics.fill(
                x + 9,
                bottom - flameHeight / 3,
                x + 14,
                bottom,
                inner
        );
    }

    /**
     * Компактное меню временно открывается на P.
     */
    public static final class CombatSettingsScreen extends Screen {
        private static final int MENU_WIDTH = 240;
        private static final int MENU_HEIGHT = 164;

        private static final int BACKGROUND = 0xF2100E17;
        private static final int CARD = 0xFF241E30;
        private static final int CARD_HOVER = 0xFF352844;
        private static final int TITLE = 0xFFE5CFFF;
        private static final int TEXT = 0xFFF2EBF8;
        private static final int MUTED = 0xFFB8A9C7;
        private static final int ACCENT = 0xFFAA66FF;

        public CombatSettingsScreen() {
            super(Component.literal("End Visuals Combat"));
        }

        private int left() {
            return (width - MENU_WIDTH) / 2;
        }

        private int top() {
            return Math.max(2, (height - MENU_HEIGHT) / 2);
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            int x = left();
            int y = top();

            graphics.fill(0, 0, width, height, 0x90000000);

            rounded(
                    graphics,
                    x,
                    y,
                    MENU_WIDTH,
                    MENU_HEIGHT,
                    BACKGROUND
            );

            graphics.drawString(
                    font,
                    "END VISUALS / COMBAT",
                    x + 10,
                    y + 9,
                    TITLE,
                    false
            );

            Settings current = settings();

            row(
                    graphics,
                    x + 8,
                    y + 28,
                    "Fire: " + FIRE_MODES[current.fireMode],
                    mouseX,
                    mouseY
            );

            row(
                    graphics,
                    x + 8,
                    y + 50,
                    "Low Fire opacity: " + current.fireOpacity + "%",
                    mouseX,
                    mouseY
            );

            row(
                    graphics,
                    x + 8,
                    y + 72,
                    "Hit Particles: "
                            + (current.hitParticles ? "ON" : "OFF"),
                    mouseX,
                    mouseY
            );

            row(
                    graphics,
                    x + 8,
                    y + 94,
                    "Particle: " + PARTICLE_NAMES[current.particleType],
                    mouseX,
                    mouseY
            );

            row(
                    graphics,
                    x + 8,
                    y + 116,
                    "Count: " + current.particleCount
                            + "  (left - / right +)",
                    mouseX,
                    mouseY
            );

            graphics.drawString(
                    font,
                    "Click a row | P / ESC: close",
                    x + 10,
                    y + 147,
                    MUTED,
                    false
            );
        }

        private void row(
                GuiGraphics graphics,
                int x,
                int y,
                String label,
                int mouseX,
                int mouseY
        ) {
            int rowWidth = MENU_WIDTH - 16;

            boolean hovered = inside(
                    mouseX,
                    mouseY,
                    x,
                    y,
                    rowWidth,
                    19
            );

            rounded(
                    graphics,
                    x,
                    y,
                    rowWidth,
                    19,
                    hovered ? CARD_HOVER : CARD
            );

            graphics.drawString(
                    font,
                    label,
                    x + 7,
                    y + 5,
                    TEXT,
                    false
            );

            graphics.fill(
                    x + rowWidth - 5,
                    y + 5,
                    x + rowWidth - 3,
                    y + 14,
                    ACCENT
            );
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

            int x = left();
            int y = top();
            int rowX = x + 8;
            int rowWidth = MENU_WIDTH - 16;

            Settings current = settings();

            if (inside(mouseX, mouseY, rowX, y + 28, rowWidth, 19)) {
                current.fireMode = (current.fireMode + 1)
                        % FIRE_MODES.length;
            } else if (inside(
                    mouseX, mouseY, rowX, y + 50, rowWidth, 19
            )) {
                current.fireOpacity += 25;

                if (current.fireOpacity > 100) {
                    current.fireOpacity = 25;
                }
            } else if (inside(
                    mouseX, mouseY, rowX, y + 72, rowWidth, 19
            )) {
                current.hitParticles = !current.hitParticles;
            } else if (inside(
                    mouseX, mouseY, rowX, y + 94, rowWidth, 19
            )) {
                current.particleType = (current.particleType + 1)
                        % PARTICLE_NAMES.length;
            } else if (inside(
                    mouseX, mouseY, rowX, y + 116, rowWidth, 19
            )) {
                if (mouseX < x + MENU_WIDTH / 2.0) {
                    current.particleCount = clamp(
                            current.particleCount - 1,
                            1,
                            30
                    );
                } else {
                    current.particleCount = clamp(
                            current.particleCount + 1,
                            1,
                            30
                    );
                }
            } else {
                return super.mouseClicked(mouseX, mouseY, button);
            }

            save();
            return true;
        }

        @Override
        public boolean keyPressed(
                int keyCode,
                int scanCode,
                int modifiers
        ) {
            if (keyCode == GLFW.GLFW_KEY_P) {
                onClose();
                return true;
            }

            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private static void rounded(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        graphics.fill(
                x + 3,
                y,
                x + width - 3,
                y + height,
                color
        );

        graphics.fill(
                x,
                y + 3,
                x + width,
                y + height - 3,
                color
        );

        graphics.fill(
                x + 1,
                y + 1,
                x + width - 1,
                y + height - 1,
                color
        );
    }

    private static boolean inside(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height
    ) {
        return mouseX >= x
                && mouseX < x + width
                && mouseY >= y
                && mouseY < y + height;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}