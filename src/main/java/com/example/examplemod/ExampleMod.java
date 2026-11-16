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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

@Mod(ExampleMod.MOD_ID)
public final class ExampleMod {
    public static final String MOD_ID = "examplemod";

    private static final Logger LOGGER = LogUtils.getLogger();

    public ExampleMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Client client = new Client();

            MinecraftForge.EVENT_BUS.register(client);

            FMLJavaModLoadingContext.get()
                    .getModEventBus()
                    .addListener(client::onAddGuiLayers);
        }
    }

    /*
     * Все перемещаемые элементы.
     */
    private static final int FPS = 0;
    private static final int CPS = 1;
    private static final int KEYS = 2;
    private static final int LOCATION = 3;
    private static final int ARMOR = 4;
    private static final int POTIONS = 5;
    private static final int TOTEM = 6;
    private static final int TARGET = 7;
    private static final int REACH = 8;

    private static final int ELEMENT_COUNT = 9;

    private static final String[] NAMES = {
            "FPS / Ping",
            "CPS Counter",
            "Keystrokes",
            "Coordinates",
            "Armor HUD",
            "Potion HUD",
            "Totem Counter",
            "Target HUD",
            "Reach Display"
    };

    private static final int[] WIDTHS = {
            150, 150, 86, 150, 150, 150, 100, 150, 125
    };

    /*
     * Высота панели брони и эффектов рассчитывается по содержимому.
     * Эти значения используются для предпросмотра и ограничений редактора.
     */
    private static final int[] HEIGHTS = {
            20, 20, 86, 57, 127, 70, 22, 40, 22
    };

    private static final int[] DEFAULT_X = {
            8, 8, 8, 190, 190, 190, 8, 100, 100
    };

    private static final int[] DEFAULT_Y = {
            8, 26, 45, 8, 55, 150, 125, 150, 185
    };

    private static final int PANEL = 0xC0181028;
    private static final int ACCENT = 0xFFAA66FF;
    private static final int TITLE = 0xFFE5CFFF;
    private static final int WHITE = 0xFFF2EBF8;
    private static final int MUTED = 0xFFB8A9C7;
    private static final int GREEN = 0xFF55FF88;
    private static final int YELLOW = 0xFFFFDD55;
    private static final int RED = 0xFFFF5555;

    private static final int[] CROSSHAIR_COLORS = {
            0xFFD5A6FF,
            0xFFFFFFFF,
            0xFF55FF88,
            0xFFFF7777,
            0xFF55DDFF
    };

    private static final String[] COLOR_NAMES = {
            "Purple",
            "White",
            "Green",
            "Red",
            "Cyan"
    };

    private static final String[] SHAPE_NAMES = {
            "Cross",
            "Dot",
            "Plus"
    };

    /**
     * Один элемент HUD.
     * Координаты задаются в GUI-пикселях от левого верхнего угла.
     */
    public static final class HudEntry {
        public boolean enabled = true;
        public int scale = 70;
        public int x;
        public int y;

        public HudEntry() {
        }

        public HudEntry(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Настройки клиента.
     *
     * Старые поля сохранены для переноса переключателей и размеров
     * из предыдущего конфига, если массив entries ещё отсутствует.
     */
    public static final class Settings {
        public boolean fpsPing = true;
        public boolean cps = true;
        public boolean keystrokes = true;
        public boolean coordinates = true;
        public boolean armor = true;
        public boolean potions = true;

        public int fpsPingScale = 70;
        public int cpsScale = 70;
        public int keystrokesScale = 70;
        public int coordinatesScale = 70;
        public int armorScale = 70;
        public int potionsScale = 70;

        public boolean crosshair = false;
        public int crosshairScale = 100;

        public int crosshairShape = 0;
        public int crosshairColor = 0;
        public boolean crosshairDot = true;

        public HudEntry[] entries;

        public void normalize() {
            if (entries == null || entries.length != ELEMENT_COUNT) {
                HudEntry[] old = entries;
                entries = new HudEntry[ELEMENT_COUNT];

                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    if (old != null && i < old.length && old[i] != null) {
                        entries[i] = old[i];
                    } else {
                        entries[i] = new HudEntry(
                                DEFAULT_X[i],
                                DEFAULT_Y[i]
                        );
                    }
                }

                /*
                 * Перенос старых настроек только при первом создании
                 * массива элементов.
                 */
                if (old == null) {
                    entries[FPS].enabled = fpsPing;
                    entries[CPS].enabled = cps;
                    entries[KEYS].enabled = keystrokes;
                    entries[LOCATION].enabled = coordinates;
                    entries[ARMOR].enabled = armor;
                    entries[POTIONS].enabled = potions;

                    entries[FPS].scale = fpsPingScale;
                    entries[CPS].scale = cpsScale;
                    entries[KEYS].scale = keystrokesScale;
                    entries[LOCATION].scale = coordinatesScale;
                    entries[ARMOR].scale = armorScale;
                    entries[POTIONS].scale = potionsScale;
                }
            }

            for (int i = 0; i < ELEMENT_COUNT; i++) {
                if (entries[i] == null) {
                    entries[i] = new HudEntry(DEFAULT_X[i], DEFAULT_Y[i]);
                }

                entries[i].scale = clamp(entries[i].scale, 50, 150);
            }

            crosshairScale = clamp(crosshairScale, 50, 150);
            crosshairShape = clamp(
                    crosshairShape,
                    0,
                    SHAPE_NAMES.length - 1
            );
            crosshairColor = clamp(
                    crosshairColor,
                    0,
                    COLOR_NAMES.length - 1
            );
        }
    }

    public static final class Config {
        private static final Gson GSON = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        private static Path path() {
            return Minecraft.getInstance()
                    .gameDirectory
                    .toPath()
                    .resolve("config")
                    .resolve("endvisuals.json");
        }

        public static Settings load() {
            Path path = path();

            if (!Files.exists(path)) {
                Settings settings = new Settings();
                settings.normalize();
                save(settings);
                return settings;
            }

            try {
                String json = Files.readString(
                        path,
                        StandardCharsets.UTF_8
                );

                Settings settings = GSON.fromJson(
                        json,
                        Settings.class
                );

                if (settings == null) {
                    LOGGER.warn("End Visuals config is empty.");
                    settings = new Settings();
                }

                settings.normalize();
                return settings;
            } catch (Exception exception) {
                LOGGER.error(
                        "Could not load End Visuals config; using defaults.",
                        exception
                );

                Settings defaults = new Settings();
                defaults.normalize();
                return defaults;
            }
        }

        public static void save(Settings settings) {
            try {
                settings.normalize();

                Path path = path();
                Files.createDirectories(path.getParent());

                Files.writeString(
                        path,
                        GSON.toJson(settings),
                        StandardCharsets.UTF_8
                );
            } catch (Exception exception) {
                LOGGER.error(
                        "Could not save End Visuals config.",
                        exception
                );
            }
        }
    }

    /**
     * Клиентский обработчик ввода и рендера.
     */
    public static final class Client {
        private static final long CPS_WINDOW_MS = 1_000L;

        private final Settings settings;
        private final Deque<Long> leftClicks = new ArrayDeque<>();
        private final Deque<Long> rightClicks = new ArrayDeque<>();

        private final ItemStack totemIcon =
                new ItemStack(Items.TOTEM_OF_UNDYING);

        public Client() {
            settings = Config.load();
        }

        public void onAddGuiLayers(AddGuiOverlayLayersEvent event) {
            ForgeLayeredDraw draw = event.getLayeredDraw();

            /*
             * Когда кастомный прицел выключен, ванильный остаётся без изменений.
             */
            draw.addConditionTo(
                    ForgeLayeredDraw.PRE_SLEEP_STACK,
                    ForgeLayeredDraw.CROSSHAIR,
                    () -> !settings.crosshair
            );

            draw.add(
                    ResourceLocation.fromNamespaceAndPath(
                            MOD_ID,
                            "unified_hud"
                    ),
                    (graphics, deltaTracker) -> renderHud(graphics)
            );
        }

        @SubscribeEvent
        public void onKey(InputEvent.Key event) {
            if (event.getKey() != GLFW.GLFW_KEY_RIGHT_SHIFT
                    || event.getAction() != GLFW.GLFW_PRESS) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null || mc.screen != null) {
                return;
            }

            mc.setScreen(new MenuScreen(this));
        }

        @SubscribeEvent
        public void onMouseButton(InputEvent.MouseButton.Pre event) {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null
                    || mc.screen != null
                    || event.getAction() != GLFW.GLFW_PRESS) {
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
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            long now = System.currentTimeMillis();

            prune(leftClicks, now);
            prune(rightClicks, now);

            if (Minecraft.getInstance().player == null) {
                leftClicks.clear();
                rightClicks.clear();
            }
        }

        private void prune(Deque<Long> clicks, long now) {
            while (!clicks.isEmpty()
                    && now - clicks.peekFirst() >= CPS_WINDOW_MS) {
                clicks.removeFirst();
            }
        }

        private void renderHud(GuiGraphics graphics) {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null || mc.options.hideGui) {
                return;
            }

            /*
             * В редакторе элементы рисуются отдельно, чтобы не было дублей.
             */
            if (mc.screen instanceof HudEditorScreen) {
                return;
            }

            for (int id = 0; id < ELEMENT_COUNT; id++) {
                if (!settings.entries[id].enabled) {
                    continue;
                }

                if ((id == TARGET || id == REACH) && targetHit(mc) == null) {
                    continue;
                }

                if (id == POTIONS && mc.player.getActiveEffects().isEmpty()) {
                    continue;
                }

                drawElement(graphics, mc, id, false);
            }

            if (settings.crosshair
                    && mc.options.getCameraType().isFirstPerson()) {
                drawCrosshair(graphics);
            }
        }

        private void drawElement(
                GuiGraphics graphics,
                Minecraft mc,
                int id,
                boolean preview
        ) {
            HudEntry entry = settings.entries[id];

            int x = visibleX(id, graphics.guiWidth());
            int y = visibleY(id, graphics.guiHeight());

            float scale = entry.scale / 100.0f;

            graphics.pose().pushPose();

            try {
                graphics.pose().translate(x, y, 0);
                graphics.pose().scale(scale, scale, 1.0f);

                switch (id) {
                    case FPS -> drawFps(graphics, mc);
                    case CPS -> drawCps(graphics, mc);
                    case KEYS -> drawKeys(graphics, mc);
                    case LOCATION -> drawLocation(graphics, mc);
                    case ARMOR -> drawArmor(graphics, mc);
                    case POTIONS -> drawPotions(graphics, mc, preview);
                    case TOTEM -> drawTotem(graphics, mc);
                    case TARGET -> drawTarget(graphics, mc, preview);
                    case REACH -> drawReach(graphics, mc, preview);
                    default -> {
                    }
                }
            } finally {
                graphics.pose().popPose();
            }
        }

        private void drawFps(GuiGraphics graphics, Minecraft mc) {
            panel(graphics, 150, 20);

            int fps = mc.getFps();
            int ping = ping(mc);

            graphics.drawString(
                    mc.font,
                    "FPS: " + fps,
                    6,
                    6,
                    fps >= 100 ? GREEN : fps >= 50 ? YELLOW : RED,
                    false
            );

            graphics.drawString(
                    mc.font,
                    "Ping: " + (ping < 0 ? "N/A" : ping + " ms"),
                    76,
                    6,
                    ping < 0 ? WHITE
                            : ping <= 80 ? GREEN
                            : ping <= 150 ? YELLOW
                            : RED,
                    false
            );
        }

        private int ping(Minecraft mc) {
            if (mc.getConnection() == null || mc.player == null) {
                return -1;
            }

            PlayerInfo info = mc.getConnection()
                    .getPlayerInfo(mc.player.getUUID());

            return info == null ? -1 : info.getLatency();
        }

        private void drawCps(GuiGraphics graphics, Minecraft mc) {
            panel(graphics, 150, 20);

            int left = leftClicks.size();
            int right = rightClicks.size();

            graphics.drawString(
                    mc.font,
                    "CPS L/R: " + left + "/" + right
                            + " | Total: " + (left + right),
                    6,
                    6,
                    WHITE,
                    false
            );
        }

        private void drawKeys(GuiGraphics graphics, Minecraft mc) {
            int size = 20;
            int gap = 2;

            key(graphics, mc, "W", 22, 0, size, size,
                    mc.options.keyUp.isDown());

            key(graphics, mc, "A", 0, 22, size, size,
                    mc.options.keyLeft.isDown());

            key(graphics, mc, "S", 22, 22, size, size,
                    mc.options.keyDown.isDown());

            key(graphics, mc, "D", 44, 22, size, size,
                    mc.options.keyRight.isDown());

            key(graphics, mc, "LMB", 0, 44, 31, size,
                    mc.options.keyAttack.isDown());

            key(graphics, mc, "RMB", 33, 44, 31, size,
                    mc.options.keyUse.isDown());

            key(graphics, mc, "SPACE", 0, 66, 42, size,
                    mc.options.keyJump.isDown());

            key(graphics, mc, "SHIFT", 44, 66, 42, size,
                    mc.options.keyShift.isDown());
        }

        private void key(
                GuiGraphics graphics,
                Minecraft mc,
                String label,
                int x,
                int y,
                int w,
                int h,
                boolean pressed
        ) {
            graphics.fill(
                    x,
                    y,
                    x + w,
                    y + h,
                    pressed ? 0xD0C18AFF : 0xB0332049
            );

            int textX = x + (w - mc.font.width(label)) / 2;
            int textY = y + (h - mc.font.lineHeight) / 2;

            graphics.drawString(
                    mc.font,
                    label,
                    textX,
                    textY,
                    pressed ? 0xFF1A0D29 : WHITE,
                    false
            );
        }

        private void drawLocation(GuiGraphics graphics, Minecraft mc) {
            panel(graphics, 150, 57);

            if (mc.player == null || mc.level == null) {
                return;
            }

            String coordinates = String.format(
                    Locale.ROOT,
                    "XYZ: %.1f / %.1f / %.1f",
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ()
            );

            String direction = mc.player.getDirection()
                    .getName()
                    .toUpperCase(Locale.ROOT);

            String biome = mc.level.getBiome(mc.player.blockPosition())
                    .unwrapKey()
                    .map(key -> key.location().getPath())
                    .orElse("unknown")
                    .replace('_', ' ');

            graphics.drawString(mc.font, "LOCATION", 8, 6, TITLE, false);
            text(graphics, mc, coordinates, 8, 20, 134, WHITE);
            text(graphics, mc, "Facing: " + direction, 8, 31, 134, WHITE);
            text(graphics, mc, "Biome: " + biome, 8, 42, 134, MUTED);
        }

        private List<ItemStack> equipment(Minecraft mc) {
            List<ItemStack> items = new ArrayList<>();

            if (mc.player == null) {
                return items;
            }

            for (ItemStack stack : mc.player.getArmorSlots()) {
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }

            ItemStack hand = mc.player.getMainHandItem();

            if (!hand.isEmpty() && hand.isDamageableItem()) {
                items.add(hand);
            }

            return items;
        }

        private void drawArmor(GuiGraphics graphics, Minecraft mc) {
            List<ItemStack> items = equipment(mc);

            int height = 22 + Math.max(1, items.size()) * 21;

            panel(graphics, 150, height);

            graphics.drawString(
                    mc.font,
                    "ARMOR / TOOL",
                    8,
                    6,
                    TITLE,
                    false
            );

            if (items.isEmpty()) {
                graphics.drawString(
                        mc.font,
                        "No equipment",
                        8,
                        23,
                        MUTED,
                        false
                );
                return;
            }

            int y = 20;

            for (ItemStack stack : items) {
                graphics.renderItem(stack, 7, y);

                if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                    int max = stack.getMaxDamage();
                    int remaining = Math.max(0, max - stack.getDamageValue());

                    float fraction = Math.max(
                            0.0f,
                            Math.min(1.0f, (float) remaining / max)
                    );

                    String label = remaining + " / " + max
                            + " (" + Math.round(fraction * 100) + "%)";

                    text(graphics, mc, label, 28, y + 1, 115, WHITE);

                    int color = fraction > 0.5f
                            ? GREEN
                            : fraction > 0.2f ? YELLOW : RED;

                    graphics.fill(28, y + 12, 140, y + 15, 0xFF393040);
                    graphics.fill(
                            28,
                            y + 12,
                            28 + Math.round(112 * fraction),
                            y + 15,
                            color
                    );
                } else {
                    text(
                            graphics,
                            mc,
                            stack.getHoverName().getString(),
                            28,
                            y + 4,
                            115,
                            WHITE
                    );
                }

                y += 21;
            }
        }

        private void drawPotions(
                GuiGraphics graphics,
                Minecraft mc,
                boolean preview
        ) {
            if (mc.player == null) {
                return;
            }

            int count = Math.min(4, mc.player.getActiveEffects().size());

            if (count == 0 && preview) {
                panel(graphics, 150, 34);
                graphics.drawString(
                        mc.font,
                        "POTION EFFECTS",
                        8,
                        6,
                        TITLE,
                        false
                );
                graphics.drawString(
                        mc.font,
                        "Effect preview",
                        8,
                        20,
                        MUTED,
                        false
                );
                return;
            }

            if (count == 0) {
                return;
            }

            panel(graphics, 150, 22 + count * 12);

            graphics.drawString(
                    mc.font,
                    "POTION EFFECTS",
                    8,
                    6,
                    TITLE,
                    false
            );

            int y = 20;
            int rendered = 0;

            for (MobEffectInstance effect : mc.player.getActiveEffects()) {
                if (rendered >= count) {
                    break;
                }

                String name = Component.translatable(
                        effect.getDescriptionId()
                ).getString();

                int level = effect.getAmplifier() + 1;

                if (level > 1) {
                    name += " " + level;
                }

                String time = duration(effect.getDuration());
                int timeWidth = mc.font.width(time);

                text(
                        graphics,
                        mc,
                        name,
                        8,
                        y,
                        Math.max(0, 126 - timeWidth),
                        WHITE
                );

                graphics.drawString(
                        mc.font,
                        time,
                        142 - timeWidth,
                        y,
                        ACCENT,
                        false
                );

                y += 12;
                rendered++;
            }
        }

        private String duration(int ticks) {
            if (ticks < 0) {
                return "inf";
            }

            int seconds = ticks / 20;

            return String.format(
                    Locale.ROOT,
                    "%d:%02d",
                    seconds / 60,
                    seconds % 60
            );
        }

        private void drawTotem(GuiGraphics graphics, Minecraft mc) {
            panel(graphics, 100, 22);

            graphics.renderItem(totemIcon, 5, 3);

            int count = 0;

            if (mc.player != null) {
                Inventory inventory = mc.player.getInventory();

                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    ItemStack stack = inventory.getItem(slot);

                    if (stack.is(Items.TOTEM_OF_UNDYING)) {
                        count += stack.getCount();
                    }
                }
            }

            graphics.drawString(
                    mc.font,
                    "Totems: " + count,
                    25,
                    7,
                    WHITE,
                    false
            );
        }

        private EntityHitResult targetHit(Minecraft mc) {
            if (mc.hitResult instanceof EntityHitResult hit
                    && hit.getEntity() instanceof LivingEntity) {
                return hit;
            }

            return null;
        }

        private void drawTarget(
                GuiGraphics graphics,
                Minecraft mc,
                boolean preview
        ) {
            EntityHitResult hit = targetHit(mc);

            if (hit == null && !preview) {
                return;
            }

            panel(graphics, 150, 40);

            String name = "Target preview";
            String health = "HP: 20.0 / 20.0";
            String armor = "Armor: 0";

            if (hit != null
                    && hit.getEntity() instanceof LivingEntity entity) {
                name = entity.getDisplayName().getString();

                health = String.format(
                        Locale.ROOT,
                        "HP: %.1f / %.1f",
                        entity.getHealth(),
                        entity.getMaxHealth()
                );

                armor = "Armor: " + entity.getArmorValue();
            }

            text(graphics, mc, name, 7, 5, 135, TITLE);
            text(graphics, mc, health, 7, 16, 135, GREEN);
            text(graphics, mc, armor, 7, 27, 135, MUTED);
        }

        private void drawReach(
                GuiGraphics graphics,
                Minecraft mc,
                boolean preview
        ) {
            EntityHitResult hit = targetHit(mc);

            if (hit == null && !preview) {
                return;
            }

            panel(graphics, 125, 22);

            String value = "Distance: 2.50 m";

            if (hit != null && mc.player != null) {
                double distance = mc.player.getEyePosition()
                        .distanceTo(hit.getLocation());

                value = String.format(
                        Locale.ROOT,
                        "Distance: %.2f m",
                        distance
                );
            }

            graphics.drawString(mc.font, value, 7, 7, WHITE, false);
        }

        private void drawCrosshair(GuiGraphics graphics) {
            int centerX = graphics.guiWidth() / 2;
            int centerY = graphics.guiHeight() / 2;
            int color = CROSSHAIR_COLORS[settings.crosshairColor];

            float scale = settings.crosshairScale / 100.0f;

            graphics.pose().pushPose();

            try {
                graphics.pose().translate(centerX, centerY, 0);
                graphics.pose().scale(scale, scale, 1.0f);

                if (settings.crosshairShape == 0) {
                    // Разделённый крест.
                    graphics.fill(-1, -8, 1, -3, color);
                    graphics.fill(-1, 3, 1, 8, color);
                    graphics.fill(-8, -1, -3, 1, color);
                    graphics.fill(3, -1, 8, 1, color);
                } else if (settings.crosshairShape == 1) {
                    // Точка.
                    graphics.fill(-1, -1, 2, 2, color);
                } else {
                    // Сплошной плюс.
                    graphics.fill(-1, -7, 1, 8, color);
                    graphics.fill(-7, -1, 8, 1, color);
                }

                if (settings.crosshairDot
                        && settings.crosshairShape == 0) {
                    graphics.fill(0, 0, 1, 1, WHITE);
                }
            } finally {
                graphics.pose().popPose();
            }
        }

        private void panel(GuiGraphics graphics, int w, int h) {
            graphics.fill(0, 0, w, h, PANEL);
            graphics.fill(0, 0, 2, h, ACCENT);
        }

        private void text(
                GuiGraphics graphics,
                Minecraft mc,
                String value,
                int x,
                int y,
                int maxWidth,
                int color
        ) {
            if (maxWidth <= 0) {
                return;
            }

            String result = value;

            if (mc.font.width(result) > maxWidth) {
                String suffix = "...";
                StringBuilder builder = new StringBuilder();

                for (int i = 0; i < value.length(); i++) {
                    String candidate = builder.toString() + value.charAt(i);

                    if (mc.font.width(candidate + suffix) > maxWidth) {
                        break;
                    }

                    builder.append(value.charAt(i));
                }

                result = builder + suffix;
            }

            graphics.drawString(mc.font, result, x, y, color, false);
        }

        private int scaledWidth(int id) {
            return (int) Math.ceil(
                    WIDTHS[id] * settings.entries[id].scale / 100.0
            );
        }

        private int scaledHeight(int id) {
            return (int) Math.ceil(
                    HEIGHTS[id] * settings.entries[id].scale / 100.0
            );
        }

        private int visibleX(int id, int screenWidth) {
            return clamp(
                    settings.entries[id].x,
                    0,
                    Math.max(0, screenWidth - scaledWidth(id))
            );
        }

        private int visibleY(int id, int screenHeight) {
            return clamp(
                    settings.entries[id].y,
                    0,
                    Math.max(0, screenHeight - scaledHeight(id))
            );
        }
    }

    /**
     * Компактное меню включения и настройки модулей.
     */
    public static final class MenuScreen extends Screen {
        private static final int MENU_WIDTH = 330;
        private static final int MENU_HEIGHT = 214;
        private static final int CARD_WIDTH = 148;
        private static final int CARD_HEIGHT = 24;

        private final Client client;
        private int tab;

        public MenuScreen(Client client) {
            super(Component.literal("End Visuals"));
            this.client = client;
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
                    0xF2100E17
            );

            graphics.renderItem(
                    client.totemIcon,
                    x + 10,
                    y + 8
            );

            graphics.drawString(
                    font,
                    "End Visuals",
                    x + 31,
                    y + 12,
                    TITLE,
                    false
            );

            graphics.drawString(
                    font,
                    tab == 0 ? "[HUD]" : "HUD",
                    x + 126,
                    y + 12,
                    tab == 0 ? WHITE : MUTED,
                    false
            );

            graphics.drawString(
                    font,
                    tab == 1 ? "[Visuals]" : "Visuals",
                    x + 180,
                    y + 12,
                    tab == 1 ? WHITE : MUTED,
                    false
            );

            graphics.fill(
                    x + 10,
                    y + 30,
                    x + MENU_WIDTH - 10,
                    y + 31,
                    0xFF392C49
            );

            if (tab == 0) {
                drawHudTab(graphics, mouseX, mouseY);
            } else {
                drawVisualsTab(graphics);
            }

            graphics.drawString(
                    font,
                    "RSHIFT / ESC: close",
                    x + 10,
                    y + MENU_HEIGHT - 12,
                    MUTED,
                    false
            );
        }

        private void drawHudTab(
                GuiGraphics graphics,
                int mouseX,
                int mouseY
        ) {
            int x = left();
            int y = top();

            for (int id = 0; id < ELEMENT_COUNT; id++) {
                int column = id % 2;
                int row = id / 2;

                int cardX = x + 10 + column * 156;
                int cardY = y + 38 + row * 27;

                boolean hovered = inside(
                        mouseX,
                        mouseY,
                        cardX,
                        cardY,
                        CARD_WIDTH,
                        CARD_HEIGHT
                );

                rounded(
                        graphics,
                        cardX,
                        cardY,
                        CARD_WIDTH,
                        CARD_HEIGHT,
                        hovered ? 0xFF292234 : 0xFF1D1925
                );

                HudEntry entry = client.settings.entries[id];

                graphics.drawString(
                        font,
                        NAMES[id],
                        cardX + 6,
                        cardY + 4,
                        WHITE,
                        false
                );

                graphics.drawString(
                        font,
                        entry.enabled ? "ON" : "OFF",
                        cardX + 6,
                        cardY + 14,
                        entry.enabled ? ACCENT : MUTED,
                        false
                );

                graphics.drawString(
                        font,
                        "-",
                        cardX + 95,
                        cardY + 13,
                        WHITE,
                        false
                );

                graphics.drawString(
                        font,
                        entry.scale + "%",
                        cardX + 106,
                        cardY + 13,
                        TITLE,
                        false
                );

                graphics.drawString(
                        font,
                        "+",
                        cardX + 137,
                        cardY + 13,
                        WHITE,
                        false
                );
            }

            rounded(
                    graphics,
                    x + 166,
                    y + 146,
                    CARD_WIDTH,
                    CARD_HEIGHT,
                    0xFF593A88
            );

            graphics.drawString(
                    font,
                    "OPEN HUD EDITOR",
                    x + 183,
                    y + 154,
                    WHITE,
                    false
            );
        }

        private void drawVisualsTab(GuiGraphics graphics) {
            int x = left();
            int y = top();
            Settings settings = client.settings;

            graphics.drawString(
                    font,
                    "CUSTOM CROSSHAIR",
                    x + 14,
                    y + 42,
                    TITLE,
                    false
            );

            graphics.drawString(
                    font,
                    "Enabled: " + (settings.crosshair ? "ON" : "OFF"),
                    x + 14,
                    y + 60,
                    settings.crosshair ? ACCENT : MUTED,
                    false
            );

            graphics.drawString(
                    font,
                    "Shape: " + SHAPE_NAMES[settings.crosshairShape],
                    x + 14,
                    y + 80,
                    WHITE,
                    false
            );

            graphics.drawString(
                    font,
                    "Color: " + COLOR_NAMES[settings.crosshairColor],
                    x + 14,
                    y + 100,
                    WHITE,
                    false
            );

            graphics.drawString(
                    font,
                    "Center dot: " + (settings.crosshairDot ? "ON" : "OFF"),
                    x + 14,
                    y + 120,
                    WHITE,
                    false
            );

            graphics.drawString(
                    font,
                    "Size: -  " + settings.crosshairScale + "%  +",
                    x + 14,
                    y + 140,
                    WHITE,
                    false
            );

            graphics.drawString(
                    font,
                    "Click a setting to change it.",
                    x + 14,
                    y + 170,
                    MUTED,
                    false
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

            if (inside(mouseX, mouseY, x + 120, y + 5, 52, 22)) {
                tab = 0;
                return true;
            }

            if (inside(mouseX, mouseY, x + 175, y + 5, 70, 22)) {
                tab = 1;
                return true;
            }

            if (tab == 0) {
                if (inside(
                        mouseX,
                        mouseY,
                        x + 166,
                        y + 146,
                        CARD_WIDTH,
                        CARD_HEIGHT
                )) {
                    Minecraft.getInstance().setScreen(
                            new HudEditorScreen(client)
                    );
                    return true;
                }

                for (int id = 0; id < ELEMENT_COUNT; id++) {
                    int column = id % 2;
                    int row = id / 2;

                    int cardX = x + 10 + column * 156;
                    int cardY = y + 38 + row * 27;

                    HudEntry entry = client.settings.entries[id];

                    if (inside(mouseX, mouseY, cardX + 90, cardY + 10, 14, 14)) {
                        entry.scale = clamp(entry.scale - 10, 50, 150);
                        Config.save(client.settings);
                        return true;
                    }

                    if (inside(mouseX, mouseY, cardX + 132, cardY + 10, 16, 14)) {
                        entry.scale = clamp(entry.scale + 10, 50, 150);
                        Config.save(client.settings);
                        return true;
                    }

                    if (inside(
                            mouseX,
                            mouseY,
                            cardX,
                            cardY,
                            CARD_WIDTH,
                            CARD_HEIGHT
                    )) {
                        entry.enabled = !entry.enabled;
                        Config.save(client.settings);
                        return true;
                    }
                }
            } else {
                Settings settings = client.settings;

                if (inside(mouseX, mouseY, x + 10, y + 55, 170, 16)) {
                    settings.crosshair = !settings.crosshair;
                } else if (inside(mouseX, mouseY, x + 10, y + 75, 170, 16)) {
                    settings.crosshairShape =
                            (settings.crosshairShape + 1) % SHAPE_NAMES.length;
                } else if (inside(mouseX, mouseY, x + 10, y + 95, 170, 16)) {
                    settings.crosshairColor =
                            (settings.crosshairColor + 1) % COLOR_NAMES.length;
                } else if (inside(mouseX, mouseY, x + 10, y + 115, 170, 16)) {
                    settings.crosshairDot = !settings.crosshairDot;
                } else if (inside(mouseX, mouseY, x + 40, y + 135, 20, 16)) {
                    settings.crosshairScale = clamp(
                            settings.crosshairScale - 10,
                            50,
                            150
                    );
                } else if (inside(mouseX, mouseY, x + 95, y + 135, 20, 16)) {
                    settings.crosshairScale = clamp(
                            settings.crosshairScale + 10,
                            50,
                            150
                    );
                } else {
                    return super.mouseClicked(mouseX, mouseY, button);
                }

                Config.save(settings);
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

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    /**
     * Единый редактор всех девяти HUD-элементов.
     */
    public static final class HudEditorScreen extends Screen {
        private final Client client;

        private int dragging = -1;
        private int offsetX;
        private int offsetY;

        public HudEditorScreen(Client client) {
            super(Component.literal("End Visuals HUD Editor"));
            this.client = client;
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            Minecraft mc = Minecraft.getInstance();

            graphics.fill(0, 0, width, height, 0x50000000);

            for (int id = 0; id < ELEMENT_COUNT; id++) {
                /*
                 * Выключенные элементы тоже видны в редакторе,
                 * чтобы их можно было расположить заранее.
                 */
                client.drawElement(graphics, mc, id, true);

                int x = client.visibleX(id, width);
                int y = client.visibleY(id, height);
                int w = client.scaledWidth(id);
                int h = client.scaledHeight(id);

                int border = client.settings.entries[id].enabled
                        ? 0xFFB99AFF
                        : 0xFF77717E;

                graphics.fill(x, y, x + w, y + 1, border);
                graphics.fill(x, y + h - 1, x + w, y + h, border);
                graphics.fill(x, y, x + 1, y + h, border);
                graphics.fill(x + w - 1, y, x + w, y + h, border);
            }

            rounded(
                    graphics,
                    Math.max(2, width / 2 - 105),
                    3,
                    210,
                    20,
                    0xEF100E17
            );

            graphics.drawString(
                    font,
                    "Drag HUD | ESC: back",
                    Math.max(8, width / 2 - 90),
                    9,
                    TITLE,
                    false
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

            /*
             * Последний нарисованный элемент находится сверху.
             */
            for (int id = ELEMENT_COUNT - 1; id >= 0; id--) {
                int x = client.visibleX(id, width);
                int y = client.visibleY(id, height);
                int w = client.scaledWidth(id);
                int h = client.scaledHeight(id);

                if (inside(mouseX, mouseY, x, y, w, h)) {
                    dragging = id;
                    offsetX = (int) mouseX - x;
                    offsetY = (int) mouseY - y;
                    return true;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(
                double mouseX,
                double mouseY,
                int button,
                double dragX,
                double dragY
        ) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragging >= 0) {
                HudEntry entry = client.settings.entries[dragging];

                entry.x = clamp(
                        (int) mouseX - offsetX,
                        0,
                        Math.max(0, width - client.scaledWidth(dragging))
                );

                entry.y = clamp(
                        (int) mouseY - offsetY,
                        0,
                        Math.max(0, height - client.scaledHeight(dragging))
                );

                return true;
            }

            return super.mouseDragged(
                    mouseX,
                    mouseY,
                    button,
                    dragX,
                    dragY
            );
        }

        @Override
        public boolean mouseReleased(
                double mouseX,
                double mouseY,
                int button
        ) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragging >= 0) {
                dragging = -1;
                Config.save(client.settings);
                return true;
            }

            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public void onClose() {
            Config.save(client.settings);
            Minecraft.getInstance().setScreen(new MenuScreen(client));
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
            int w,
            int h,
            int color
    ) {
        graphics.fill(x + 3, y, x + w - 3, y + h, color);
        graphics.fill(x, y + 3, x + w, y + h - 3, color);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
    }

    private static boolean inside(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int w,
            int h
    ) {
        return mouseX >= x
                && mouseX < x + w
                && mouseY >= y
                && mouseY < y + h;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}