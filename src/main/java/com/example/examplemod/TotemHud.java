package com.example.examplemod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Дополнительные клиентские HUD-элементы:
 * Totem Counter, Target HUD и Reach Display.
 *
 * Редактор открывается в мире на клавишу O.
 * Мод не отправляет собственных пакетов и не управляет игроком.
 */
public final class TotemHud {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final int PANEL = 0xD0181028;
    private static final int ACCENT = 0xFFAA66FF;
    private static final int TITLE = 0xFFE6D3FF;
    private static final int TEXT = 0xFFF2EBF8;
    private static final int MUTED = 0xFFB9A9C8;
    private static final int GREEN = 0xFF55FF88;

    private static final int TOTEM = 0;
    private static final int TARGET = 1;
    private static final int REACH = 2;
    private static final int COUNT = 3;

    private static final String[] NAMES = {
            "Totem Counter",
            "Target HUD",
            "Reach Display"
    };

    private static final int[] BASE_WIDTHS = {
            100, 150, 125
    };

    private static final int[] BASE_HEIGHTS = {
            22, 40, 22
    };

    private static final ItemStack TOTEM_ICON =
            new ItemStack(Items.TOTEM_OF_UNDYING);

    private static Settings settings;
    private static boolean settingsLoaded;

    private TotemHud() {
    }

    /**
     * JSON-сериализуемые настройки.
     *
     * Координаты задаются в GUI-пикселях от верхнего левого угла.
     */
    public static final class Settings {
        public boolean totemEnabled = true;
        public boolean targetEnabled = true;
        public boolean reachEnabled = true;

        public int totemScale = 70;
        public int targetScale = 70;
        public int reachScale = 70;

        public int totemX = 8;
        public int totemY = 150;

        public int targetX = 8;
        public int targetY = 175;

        public int reachX = 8;
        public int reachY = 210;

        public void normalize() {
            totemScale = clamp(totemScale, 50, 150);
            targetScale = clamp(targetScale, 50, 150);
            reachScale = clamp(reachScale, 50, 150);
        }

        public boolean enabled(int id) {
            return switch (id) {
                case TOTEM -> totemEnabled;
                case TARGET -> targetEnabled;
                case REACH -> reachEnabled;
                default -> false;
            };
        }

        public void setEnabled(int id, boolean value) {
            switch (id) {
                case TOTEM -> totemEnabled = value;
                case TARGET -> targetEnabled = value;
                case REACH -> reachEnabled = value;
                default -> {
                }
            }
        }

        public int scale(int id) {
            return switch (id) {
                case TOTEM -> totemScale;
                case TARGET -> targetScale;
                case REACH -> reachScale;
                default -> 70;
            };
        }

        public void setScale(int id, int value) {
            value = clamp(value, 50, 150);

            switch (id) {
                case TOTEM -> totemScale = value;
                case TARGET -> targetScale = value;
                case REACH -> reachScale = value;
                default -> {
                }
            }
        }

        public int x(int id) {
            return switch (id) {
                case TOTEM -> totemX;
                case TARGET -> targetX;
                case REACH -> reachX;
                default -> 0;
            };
        }

        public int y(int id) {
            return switch (id) {
                case TOTEM -> totemY;
                case TARGET -> targetY;
                case REACH -> reachY;
                default -> 0;
            };
        }

        public void setPosition(int id, int x, int y) {
            switch (id) {
                case TOTEM -> {
                    totemX = x;
                    totemY = y;
                }
                case TARGET -> {
                    targetX = x;
                    targetY = y;
                }
                case REACH -> {
                    reachX = x;
                    reachY = y;
                }
                default -> {
                }
            }
        }
    }

    private static Settings settings() {
        if (!settingsLoaded) {
            settings = loadSettings();
            settingsLoaded = true;
        }

        return settings;
    }

    private static Path configPath() {
        return Minecraft.getInstance()
                .gameDirectory
                .toPath()
                .resolve("config")
                .resolve("endvisuals-extra.json");
    }

    private static Settings loadSettings() {
        Path path = configPath();

        if (!Files.exists(path)) {
            return new Settings();
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Settings loaded = GSON.fromJson(json, Settings.class);

            if (loaded == null) {
                LOGGER.warn("End Visuals extra config is empty.");
                return new Settings();
            }

            loaded.normalize();
            return loaded;
        } catch (Exception exception) {
            LOGGER.error(
                    "Could not load End Visuals extra config; using defaults.",
                    exception
            );
            return new Settings();
        }
    }

    private static void saveSettings() {
        Settings current = settings();

        try {
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
                    "Could not save End Visuals extra config.",
                    exception
            );
        }
    }

    /**
     * Регистрация слоя HUD на шине мода.
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
                            "totem_hud"
                    ),
                    (graphics, deltaTracker) -> renderHud(graphics)
            );
        }
    }

    /**
     * Открытие редактора через общую шину Forge.
     */
    @Mod.EventBusSubscriber(
            modid = ExampleMod.MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.FORGE,
            value = Dist.CLIENT
    )
    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onKey(InputEvent.Key event) {
            if (event.getKey() != GLFW.GLFW_KEY_O
                    || event.getAction() != GLFW.GLFW_PRESS) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null || mc.screen != null) {
                return;
            }

            mc.setScreen(new ExtraHudEditor());
        }
    }

    private static void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        // В редакторе элементы рисуются отдельно как перетаскиваемый предпросмотр.
        if (mc.screen instanceof ExtraHudEditor) {
            return;
        }

        Settings current = settings();

        for (int id = 0; id < COUNT; id++) {
            if (!current.enabled(id)) {
                continue;
            }

            if (id != TOTEM && targetHit(mc) == null) {
                continue;
            }

            int x = visibleX(current, id, graphics.guiWidth());
            int y = visibleY(current, id, graphics.guiHeight());

            drawElement(graphics, mc, id, x, y, false);
        }
    }

    private static EntityHitResult targetHit(Minecraft mc) {
        if (mc.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity) {
            return hit;
        }

        return null;
    }

    private static void drawElement(
            GuiGraphics graphics,
            Minecraft mc,
            int id,
            int x,
            int y,
            boolean preview
    ) {
        float scale = settings().scale(id) / 100.0f;

        graphics.pose().pushPose();

        try {
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1.0f);

            if (id == TOTEM) {
                drawTotem(graphics, mc);
            } else if (id == TARGET) {
                drawTarget(graphics, mc, preview);
            } else if (id == REACH) {
                drawReach(graphics, mc, preview);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void panel(
            GuiGraphics graphics,
            int width,
            int height
    ) {
        graphics.fill(0, 0, width, height, PANEL);
        graphics.fill(0, 0, 2, height, ACCENT);
    }

    private static void drawTotem(
            GuiGraphics graphics,
            Minecraft mc
    ) {
        panel(graphics, BASE_WIDTHS[TOTEM], BASE_HEIGHTS[TOTEM]);

        graphics.renderItem(TOTEM_ICON, 5, 3);

        int count = mc.player == null
                ? 0
                : countTotems(mc.player.getInventory());

        graphics.drawString(
                mc.font,
                "Totems: " + count,
                25,
                7,
                TEXT,
                false
        );
    }

    private static int countTotems(Inventory inventory) {
        int count = 0;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);

            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private static void drawTarget(
            GuiGraphics graphics,
            Minecraft mc,
            boolean preview
    ) {
        panel(graphics, BASE_WIDTHS[TARGET], BASE_HEIGHTS[TARGET]);

        EntityHitResult hit = targetHit(mc);

        String name;
        String health;
        String armor;

        if (hit != null && hit.getEntity() instanceof LivingEntity entity) {
            name = entity.getDisplayName().getString();

            health = String.format(
                    Locale.ROOT,
                    "HP: %.1f / %.1f",
                    entity.getHealth(),
                    entity.getMaxHealth()
            );

            armor = "Armor: " + entity.getArmorValue();
        } else if (preview) {
            name = "Target preview";
            health = "HP: 20.0 / 20.0";
            armor = "Armor: 0";
        } else {
            return;
        }

        graphics.drawString(
                mc.font,
                trim(mc, name, 135),
                7,
                5,
                TITLE,
                false
        );

        graphics.drawString(
                mc.font,
                health,
                7,
                16,
                GREEN,
                false
        );

        graphics.drawString(
                mc.font,
                armor,
                7,
                27,
                MUTED,
                false
        );
    }

    private static void drawReach(
            GuiGraphics graphics,
            Minecraft mc,
            boolean preview
    ) {
        panel(graphics, BASE_WIDTHS[REACH], BASE_HEIGHTS[REACH]);

        EntityHitResult hit = targetHit(mc);

        String value;

        if (hit != null && mc.player != null) {
            double distance = mc.player.getEyePosition()
                    .distanceTo(hit.getLocation());

            value = String.format(
                    Locale.ROOT,
                    "Distance: %.2f m",
                    distance
            );
        } else if (preview) {
            value = "Distance: 2.50 m";
        } else {
            return;
        }

        graphics.drawString(
                mc.font,
                value,
                7,
                7,
                TEXT,
                false
        );
    }

    private static String trim(
            Minecraft mc,
            String text,
            int maxWidth
    ) {
        if (mc.font.width(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            String next = result.toString() + text.charAt(i);

            if (mc.font.width(next + suffix) > maxWidth) {
                break;
            }

            result.append(text.charAt(i));
        }

        return result + suffix;
    }

    private static int scaledWidth(Settings current, int id) {
        return (int) Math.ceil(
                BASE_WIDTHS[id] * current.scale(id) / 100.0
        );
    }

    private static int scaledHeight(Settings current, int id) {
        return (int) Math.ceil(
                BASE_HEIGHTS[id] * current.scale(id) / 100.0
        );
    }

    private static int visibleX(Settings current, int id, int screenWidth) {
        return clamp(
                current.x(id),
                0,
                Math.max(0, screenWidth - scaledWidth(current, id))
        );
    }

    private static int visibleY(Settings current, int id, int screenHeight) {
        return clamp(
                current.y(id),
                0,
                Math.max(0, screenHeight - scaledHeight(current, id))
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Компактный редактор трёх дополнительных элементов.
     *
     * Левая колонка: переключатели и масштаб.
     * Элементы на фоне можно перетаскивать левой кнопкой мыши.
     */
    public static final class ExtraHudEditor extends Screen {
        private static final int MENU_WIDTH = 190;
        private static final int ROW_HEIGHT = 28;
        private static final int MENU_HEIGHT = 125;

        private static final int MENU_BACKGROUND = 0xEF100E17;
        private static final int ROW_BACKGROUND = 0xFF24202D;
        private static final int BUTTON_BACKGROUND = 0xFF453659;
        private static final int OFF = 0xFF77717E;
        private static final int ON = 0xFFB99AFF;
        private static final int PREVIEW_BORDER = 0xFFB99AFF;

        private final Settings current;

        private int dragging = -1;
        private int dragOffsetX;
        private int dragOffsetY;

        public ExtraHudEditor() {
            super(Component.literal("End Visuals HUD Editor"));
            current = settings();
        }

        private int menuX() {
            return Math.max(4, (width - MENU_WIDTH) / 2);
        }

        private int menuY() {
            return 8;
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            Minecraft mc = Minecraft.getInstance();

            graphics.fill(0, 0, width, height, 0x70000000);

            // Рисуем все элементы в редакторе, даже выключенные,
            // чтобы их можно было перемещать.
            for (int id = 0; id < COUNT; id++) {
                int x = visibleX(current, id, width);
                int y = visibleY(current, id, height);
                int w = scaledWidth(current, id);
                int h = scaledHeight(current, id);

                drawElement(graphics, mc, id, x, y, true);

                graphics.fill(x, y, x + w, y + 1, PREVIEW_BORDER);
                graphics.fill(x, y + h - 1, x + w, y + h, PREVIEW_BORDER);
                graphics.fill(x, y, x + 1, y + h, PREVIEW_BORDER);
                graphics.fill(x + w - 1, y, x + w, y + h, PREVIEW_BORDER);
            }

            int x = menuX();
            int y = menuY();

            roundedRect(
                    graphics,
                    x,
                    y,
                    MENU_WIDTH,
                    MENU_HEIGHT,
                    MENU_BACKGROUND
            );

            graphics.drawString(
                    font,
                    "END VISUALS / HUD",
                    x + 8,
                    y + 7,
                    TITLE,
                    false
            );

            for (int id = 0; id < COUNT; id++) {
                int rowY = y + 21 + id * ROW_HEIGHT;

                roundedRect(
                        graphics,
                        x + 5,
                        rowY,
                        MENU_WIDTH - 10,
                        24,
                        ROW_BACKGROUND
                );

                graphics.drawString(
                        font,
                        NAMES[id],
                        x + 10,
                        rowY + 4,
                        TEXT,
                        false
                );

                graphics.drawString(
                        font,
                        current.enabled(id) ? "ON" : "OFF",
                        x + 10,
                        rowY + 14,
                        current.enabled(id) ? ON : OFF,
                        false
                );

                drawButton(graphics, "-", x + 105, rowY + 5);

                String percent = current.scale(id) + "%";

                graphics.drawString(
                        font,
                        percent,
                        x + 122,
                        rowY + 8,
                        TITLE,
                        false
                );

                drawButton(graphics, "+", x + 158, rowY + 5);
            }

            graphics.drawString(
                    font,
                    "Drag panels | Esc: save",
                    x + 8,
                    y + MENU_HEIGHT - 13,
                    MUTED,
                    false
            );
        }

        private void drawButton(
                GuiGraphics graphics,
                String label,
                int x,
                int y
        ) {
            roundedRect(
                    graphics,
                    x,
                    y,
                    14,
                    14,
                    BUTTON_BACKGROUND
            );

            graphics.drawString(
                    font,
                    label,
                    x + (14 - font.width(label)) / 2,
                    y + 3,
                    TEXT,
                    false
            );
        }

        private void roundedRect(
                GuiGraphics graphics,
                int x,
                int y,
                int w,
                int h,
                int color
        ) {
            graphics.fill(x + 2, y, x + w - 2, y + h, color);
            graphics.fill(x, y + 2, x + w, y + h - 2, color);
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

            int menuX = menuX();
            int menuY = menuY();

            if (inside(
                    mouseX,
                    mouseY,
                    menuX,
                    menuY,
                    MENU_WIDTH,
                    MENU_HEIGHT
            )) {
                for (int id = 0; id < COUNT; id++) {
                    int rowY = menuY + 21 + id * ROW_HEIGHT;

                    if (inside(mouseX, mouseY, menuX + 105, rowY + 5, 14, 14)) {
                        current.setScale(id, current.scale(id) - 10);
                        saveSettings();
                        return true;
                    }

                    if (inside(mouseX, mouseY, menuX + 158, rowY + 5, 14, 14)) {
                        current.setScale(id, current.scale(id) + 10);
                        saveSettings();
                        return true;
                    }

                    if (inside(mouseX, mouseY, menuX + 5, rowY, 95, 24)) {
                        current.setEnabled(id, !current.enabled(id));
                        saveSettings();
                        return true;
                    }
                }

                return true;
            }

            // Проверяем в обратном порядке отрисовки:
            // верхний перекрывающий элемент получает клик первым.
            for (int id = COUNT - 1; id >= 0; id--) {
                int x = visibleX(current, id, width);
                int y = visibleY(current, id, height);
                int w = scaledWidth(current, id);
                int h = scaledHeight(current, id);

                if (inside(mouseX, mouseY, x, y, w, h)) {
                    dragging = id;
                    dragOffsetX = (int) mouseX - x;
                    dragOffsetY = (int) mouseY - y;
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
                int maxX = Math.max(
                        0,
                        width - scaledWidth(current, dragging)
                );

                int maxY = Math.max(
                        0,
                        height - scaledHeight(current, dragging)
                );

                int x = clamp((int) mouseX - dragOffsetX, 0, maxX);
                int y = clamp((int) mouseY - dragOffsetY, 0, maxY);

                current.setPosition(dragging, x, y);
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
                saveSettings();
                return true;
            }

            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public void onClose() {
            saveSettings();
            super.onClose();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        private boolean inside(
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
    }
}