package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Показывает количество тотемов в инвентаре игрока.
 *
 * Счётчик только читает данные клиента:
 * он не использует и не перемещает предметы.
 */
@Mod.EventBusSubscriber(
        modid = ExampleMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class TotemHud {
    private static final int PANEL = 0xB0181028;
    private static final int ACCENT = 0xFFAA66FF;
    private static final int TEXT = 0xFFF2EBF8;

    private static final int WIDTH = 100;
    private static final int HEIGHT = 22;

    // Временный компактный размер до добавления настройки в меню.
    private static final float SCALE = 0.70f;

    private TotemHud() {
    }

    @SubscribeEvent
    public static void onAddGuiLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(
                ResourceLocation.fromNamespaceAndPath(
                        ExampleMod.MOD_ID,
                        "totem_hud"
                ),
                (graphics, deltaTracker) -> render(graphics)
        );
    }

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        int count = countTotems(mc.player.getInventory());

        // Слева снизу, над обычным HUD.
        int x = 8;
        int y = Math.max(8, graphics.guiHeight() - 45);

        graphics.pose().pushPose();

        try {
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(SCALE, SCALE, 1.0f);

            graphics.fill(0, 0, WIDTH, HEIGHT, PANEL);
            graphics.fill(0, 0, 2, HEIGHT, ACCENT);

            graphics.renderItem(
                    new ItemStack(Items.TOTEM_OF_UNDYING),
                    5,
                    3
            );

            graphics.drawString(
                    mc.font,
                    "Totems: " + count,
                    25,
                    7,
                    TEXT,
                    false
            );
        } finally {
            graphics.pose().popPose();
        }
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
}