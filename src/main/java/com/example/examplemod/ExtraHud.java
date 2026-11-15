package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Дополнительные клиентские HUD-элементы End Visuals.
 *
 * Видимость и масштаб каждого элемента управляются из меню.
 * Позиции автоматически складываются в правый столбец.
 */
public final class ExtraHud {
    private static final int PANEL = 0xB0181028;
    private static final int ACCENT = 0xFFAA66FF;
    private static final int TITLE = 0xFFE5CFFF;
    private static final int TEXT = 0xFFF2EBF8;
    private static final int MUTED = 0xFFB8A9C7;

    private static final int GREEN = 0xFF55FF88;
    private static final int YELLOW = 0xFFFFDD55;
    private static final int RED = 0xFFFF5555;

    private static final int PANEL_WIDTH = 150;
    private static final int MARGIN = 8;
    private static final int GAP = 5;

    private final ExampleMod.HudSettings settings;

    public ExtraHud(ExampleMod.HudSettings settings) {
        this.settings = settings;
    }

    public void onAddGuiLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(
                ResourceLocation.fromNamespaceAndPath(
                        ExampleMod.MOD_ID,
                        "extra_hud"
                ),
                (graphics, deltaTracker) -> render(graphics)
        );
    }

    private void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        int y = MARGIN;

        if (settings.coordinates) {
            int scale = settings.coordinatesScale;
            int x = rightAlignedX(screenWidth, scale);

            pushScaled(graphics, x, y, scale);
            try {
                drawCoordinates(graphics, mc);
            } finally {
                graphics.pose().popPose();
            }

            y += scaledHeight(57, scale) + GAP;
        }

        if (settings.armor) {
            List<ItemStack> items = collectEquipment(mc);
            int height = armorHeight(items.size());
            int scale = settings.armorScale;
            int x = rightAlignedX(screenWidth, scale);

            pushScaled(graphics, x, y, scale);
            try {
                drawArmor(graphics, mc, items);
            } finally {
                graphics.pose().popPose();
            }

            y += scaledHeight(height, scale) + GAP;
        }

        if (settings.potions && !mc.player.getActiveEffects().isEmpty()) {
            int scale = settings.potionsScale;
            int x = rightAlignedX(screenWidth, scale);

            int availablePixels = screenHeight - MARGIN - y;

            if (availablePixels <= 0) {
                return;
            }

            int availableLogicalHeight = (int) Math.floor(
                    availablePixels / (scale / 100.0f)
            );

            pushScaled(graphics, x, y, scale);
            try {
                drawPotions(graphics, mc, availableLogicalHeight);
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    private int rightAlignedX(int screenWidth, int scale) {
        int scaledWidth = (int) Math.ceil(
                PANEL_WIDTH * scale / 100.0
        );

        return Math.max(MARGIN, screenWidth - MARGIN - scaledWidth);
    }

    private int scaledHeight(int height, int scale) {
        return (int) Math.ceil(height * scale / 100.0);
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

    private void drawCoordinates(
            GuiGraphics graphics,
            Minecraft mc
    ) {
        LocalPlayer player = mc.player;
        BlockPos pos = player.blockPosition();

        String coordinates = String.format(
                Locale.ROOT,
                "XYZ: %.1f / %.1f / %.1f",
                player.getX(),
                player.getY(),
                player.getZ()
        );

        String direction = player.getDirection()
                .getName()
                .toUpperCase(Locale.ROOT);

        String biome = mc.level.getBiome(pos)
                .unwrapKey()
                .map(key -> key.location().getPath())
                .orElse("unknown")
                .replace('_', ' ');

        drawPanel(graphics, 0, 0, PANEL_WIDTH, 57);

        graphics.drawString(
                mc.font,
                "LOCATION",
                8,
                6,
                TITLE,
                false
        );

        drawTrimmed(
                graphics,
                mc,
                coordinates,
                8,
                20,
                PANEL_WIDTH - 16,
                TEXT
        );

        graphics.drawString(
                mc.font,
                "Facing: " + direction,
                8,
                31,
                TEXT,
                false
        );

        drawTrimmed(
                graphics,
                mc,
                "Biome: " + biome,
                8,
                42,
                PANEL_WIDTH - 16,
                MUTED
        );
    }

    private List<ItemStack> collectEquipment(Minecraft mc) {
        List<ItemStack> items = new ArrayList<>();

        for (ItemStack stack : mc.player.getArmorSlots()) {
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }

        ItemStack mainHand = mc.player.getMainHandItem();

        if (!mainHand.isEmpty() && mainHand.isDamageableItem()) {
            items.add(mainHand);
        }

        return items;
    }

    private int armorHeight(int itemCount) {
        return 22 + Math.max(1, itemCount) * 21;
    }

    private void drawArmor(
            GuiGraphics graphics,
            Minecraft mc,
            List<ItemStack> items
    ) {
        int height = armorHeight(items.size());

        drawPanel(graphics, 0, 0, PANEL_WIDTH, height);

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

        int rowY = 20;

        for (ItemStack stack : items) {
            drawEquipmentRow(graphics, mc, stack, 7, rowY);
            rowY += 21;
        }
    }

    private void drawEquipmentRow(
            GuiGraphics graphics,
            Minecraft mc,
            ItemStack stack,
            int x,
            int y
    ) {
        graphics.renderItem(stack, x, y);

        int textX = x + 21;
        int availableWidth = PANEL_WIDTH - 35;

        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            drawTrimmed(
                    graphics,
                    mc,
                    stack.getHoverName().getString(),
                    textX,
                    y + 4,
                    availableWidth,
                    TEXT
            );
            return;
        }

        int max = stack.getMaxDamage();
        int remaining = Math.max(0, max - stack.getDamageValue());

        float fraction = Math.max(
                0.0f,
                Math.min(1.0f, (float) remaining / max)
        );

        String label = remaining + " / " + max
                + " (" + Math.round(fraction * 100.0f) + "%)";

        drawTrimmed(
                graphics,
                mc,
                label,
                textX,
                y + 1,
                availableWidth,
                TEXT
        );

        int barWidth = PANEL_WIDTH - 38;
        int barY = y + 12;

        graphics.fill(
                textX,
                barY,
                textX + barWidth,
                barY + 3,
                0xFF393040
        );

        graphics.fill(
                textX,
                barY,
                textX + Math.round(barWidth * fraction),
                barY + 3,
                durabilityColor(fraction)
        );
    }

    private void drawPotions(
            GuiGraphics graphics,
            Minecraft mc,
            int availableHeight
    ) {
        if (availableHeight < 34) {
            return;
        }

        int rowHeight = 12;
        int maxRows = (availableHeight - 22) / rowHeight;
        int totalEffects = mc.player.getActiveEffects().size();
        int visibleRows = Math.min(totalEffects, maxRows);

        if (visibleRows <= 0) {
            return;
        }

        int height = 22 + visibleRows * rowHeight;

        drawPanel(graphics, 0, 0, PANEL_WIDTH, height);

        graphics.drawString(
                mc.font,
                "POTION EFFECTS",
                8,
                6,
                TITLE,
                false
        );

        int rowY = 20;
        int rendered = 0;

        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            if (rendered >= visibleRows) {
                break;
            }

            String effectName = Component.translatable(
                    effect.getDescriptionId()
            ).getString();

            int level = effect.getAmplifier() + 1;

            if (level > 1) {
                effectName += " " + level;
            }

            String time = formatDuration(effect.getDuration());
            int timeWidth = mc.font.width(time);
            int nameWidth = PANEL_WIDTH - 24 - timeWidth;

            drawTrimmed(
                    graphics,
                    mc,
                    effectName,
                    8,
                    rowY,
                    Math.max(0, nameWidth),
                    TEXT
            );

            graphics.drawString(
                    mc.font,
                    time,
                    PANEL_WIDTH - 8 - timeWidth,
                    rowY,
                    ACCENT,
                    false
            );

            rowY += rowHeight;
            rendered++;
        }
    }

    private String formatDuration(int ticks) {
        if (ticks < 0) {
            return "∞";
        }

        int seconds = ticks / 20;
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;

        return String.format(
                Locale.ROOT,
                "%d:%02d",
                minutes,
                remainingSeconds
        );
    }

    private int durabilityColor(float fraction) {
        if (fraction > 0.5f) return GREEN;
        if (fraction > 0.2f) return YELLOW;
        return RED;
    }

    private void drawPanel(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height
    ) {
        graphics.fill(
                x,
                y,
                x + width,
                y + height,
                PANEL
        );

        graphics.fill(
                x,
                y,
                x + 2,
                y + height,
                ACCENT
        );
    }

    private void drawTrimmed(
            GuiGraphics graphics,
            Minecraft mc,
            String text,
            int x,
            int y,
            int maxWidth,
            int color
    ) {
        if (maxWidth <= 0) {
            return;
        }

        String result = text;

        if (mc.font.width(result) > maxWidth) {
            String suffix = "...";
            int suffixWidth = mc.font.width(suffix);

            if (suffixWidth > maxWidth) {
                return;
            }

            StringBuilder builder = new StringBuilder();

            for (int i = 0; i < text.length(); i++) {
                String candidate = builder.toString() + text.charAt(i);

                if (mc.font.width(candidate) + suffixWidth > maxWidth) {
                    break;
                }

                builder.append(text.charAt(i));
            }

            result = builder + suffix;
        }

        graphics.drawString(
                mc.font,
                result,
                x,
                y,
                color,
                false
        );
    }
}