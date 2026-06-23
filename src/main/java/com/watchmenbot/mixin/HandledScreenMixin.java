package com.watchmenbot.mixin;

import com.watchmenbot.modules.inventory.InventoryTools;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> extends Screen {
    private static final int BUTTON_WIDTH = 64;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int EDGE_PADDING = 4;

    @Shadow protected int backgroundWidth;
    @Shadow protected int x;
    @Shadow protected int y;
    @Final @Shadow protected T handler;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void watchmenbot$addInventoryToolsButtons(CallbackInfo info) {
        if (!InventoryTools.isEnabled() || !InventoryTools.supports(handler)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.interactionManager == null) return;

        ButtonPosition position = buttonPosition();
        addDrawableChild(ButtonWidget.builder(Text.literal("Take All"), button -> InventoryTools.takeAll(mc, handler))
            .dimensions(position.x(), position.y(), BUTTON_WIDTH, BUTTON_HEIGHT)
            .build()
        );
        addDrawableChild(ButtonWidget.builder(Text.literal("Put All"), button -> InventoryTools.putAll(mc, handler))
            .dimensions(position.x(), position.y() + BUTTON_HEIGHT + BUTTON_GAP, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build()
        );
        addDrawableChild(ButtonWidget.builder(Text.literal("Sort Chest"), button -> InventoryTools.sortChest(mc, handler))
            .dimensions(position.x(), position.y() + (BUTTON_HEIGHT + BUTTON_GAP) * 2, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build()
        );
        addDrawableChild(ButtonWidget.builder(Text.literal("Sort Inv"), button -> InventoryTools.sortInventory(mc, handler))
            .dimensions(position.x(), position.y() + (BUTTON_HEIGHT + BUTTON_GAP) * 3, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build()
        );
    }

    private ButtonPosition buttonPosition() {
        int rightSideX = x + backgroundWidth + BUTTON_GAP;
        int rightSideLimit = rightSideX + BUTTON_WIDTH + EDGE_PADDING;
        if (rightSideLimit <= width) return new ButtonPosition(rightSideX, y + EDGE_PADDING);

        return new ButtonPosition(x + backgroundWidth - BUTTON_WIDTH - EDGE_PADDING, y + EDGE_PADDING);
    }

    private record ButtonPosition(int x, int y) {
    }
}
