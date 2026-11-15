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
 * Дополнительные визуальные HUD-элементы End Visuals.
 *
 * Всё отображается только на клиенте:
 * - координаты, направление и биом;
 * - прочность брони и предмета в основной руке;
 * - активные эффекты и их время.
 *
 * Класс не отправляет пакеты и не управляет игроком.
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

    /**
     * Регистрирует отдельный слой HUD на шине мода.
     */
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

        // На очень маленьком экране не рисуем панели за его границами.
        if (screenWidth < PANEL_WIDTH + MARGIN * 2 || screenHeight < 80) {
            return;
        }

        int x = screenWidth - PANEL_WIDTH - MARGIN;
        int y = MARGIN;

        y = drawCoordinates(graphics, mc, x, y);
        y += 6;

        y = drawArmor(graphics, mc, x, y);
        y += 6;

        drawPotions(graphics, mc, x, y, screenHeight - MARGIN);
    }

    /**
     * Координаты, сторона света и биом текущего блока игрока.
     */
    private int drawCoordinates(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int y
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
                .orElse("unknown");

        biome = biome.replace('_', ' ');

        int height = 57;

        drawPanel(graphics, x, y, PANEL_WIDTH, height);

        graphics.drawString(
                mc.font,
                "LOCATION",
                x + 8,
                y + 6,
                TITLE,
                false
        );

        drawTrimmed(
                graphics,
                mc,
                coordinates,
                x + 8,
                y + 20,
                PANEL_WIDTH - 16,
                TEXT
        );

        graphics.drawString(
                mc.font,
                "Facing: " + direction,
                x + 8,
                y + 31,
                TEXT,
                false
        );

        drawTrimmed(
                graphics,
                mc,
                "Biome: " + biome,
                x + 8,
                y + 42,
                PANEL_WIDTH - 16,
                MUTED
        );

        return y + height;
    }

    /**
     * Показывает прочность надетой брони и предмета в основной руке.
     */
    private int drawArmor(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int y
    ) {
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

        int rowHeight = 21;
        int height = 22 + Math.max(1, items.size()) * rowHeight;

        drawPanel(graphics, x, y, PANEL_WIDTH, height);

        graphics.drawString(
                mc.font,
                "ARMOR / TOOL",
                x + 8,
                y + 6,
                TITLE,
                false
        );

        if (items.isEmpty()) {
            graphics.drawString(
                    mc.font,
                    "No equipment",
                    x + 8,
                    y + 23,
                    MUTED,
                    false
            );

            return y + height;
        }

        int rowY = y + 20;

        for (ItemStack stack : items) {
            drawEquipmentRow(graphics, mc, stack, x + 7, rowY);
            rowY += rowHeight;
        }

        return y + height;
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

        int color = durabilityColor(fraction);

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
                color
        );
    }

    /**
     * Показывает эффекты, которые помещаются на экране.
     */
    private void drawPotions(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int y,
            int bottomLimit
    ) {
        if (mc.player.getActiveEffects().isEmpty()) {
            return;
        }

        int rowHeight = 12;
        int availableHeight = bottomLimit - y;

        if (availableHeight < 34) {
            return;
        }

        int maxRows = (availableHeight - 22) / rowHeight;
        int totalEffects = mc.player.getActiveEffects().size();
        int visibleRows = Math.min(totalEffects, maxRows);

        int height = 22 + visibleRows * rowHeight;

        drawPanel(graphics, x, y, PANEL_WIDTH, height);

        graphics.drawString(
                mc.font,
                "POTION EFFECTS",
                x + 8,
                y + 6,
                TITLE,
                false
        );

        int rowY = y + 20;
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
                    x + 8,
                    rowY,
                    Math.max(0, nameWidth),
                    TEXT
            );

            graphics.drawString(
                    mc.font,
                    time,
                    x + PANEL_WIDTH - 8 - timeWidth,
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
        if (fraction > 0.5f) {
            return GREEN;
        }

        if (fraction > 0.2f) {
            return YELLOW;
        }

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