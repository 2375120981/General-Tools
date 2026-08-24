package com.c09nat.generaltools;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class SwissKnifeScreen extends AbstractContainerScreen<SwissKnifeMenu>
{
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("generaltools", "textures/gui/swiss_knife_gui.png");
    private static final ResourceLocation MODE_BUTTONS = ResourceLocation.fromNamespaceAndPath("generaltools", "textures/gui/swiss_knife_mode_buttons.png");
    private static final ResourceLocation WRENCH_BUTTONS = ResourceLocation.fromNamespaceAndPath("generaltools", "textures/gui/swiss_knife_wrench_button.png");

    public SwissKnifeScreen(SwissKnifeMenu menu, Inventory playerInventory, Component title)
    {
        super(menu, playerInventory, title);
        this.imageHeight = 168;
        this.inventoryLabelY = 73;
    }

    @Override
    public void init()
    {
        super.init();
        addModeButton(0, 6, 54, 18, 50);   // 自动（3 格宽，1 格高）
        addModeButton(1, 6, 18, 9, 37);    // 剑
        addModeButton(2, 24, 18, 9, 37);   // 镐
        addModeButton(3, 42, 18, 9, 37);   // 斧
        addModeButton(4, 60, 18, 9, 37);   // 铲
        addModeButton(5, 78, 18, 9, 37);   // 锄
        addModeButton(6, 96, 18, 9, 37);   // 剪刀
        addModeButton(7, 114, 18, 9, 37);  // 打火石
        addModeButton(8, 66, 18, 18, 50);  // 扳手（18×18 正方形，与 AUTO 同行，单独一列）
    }

    private void addModeButton(int mode, int x, int width, int height, int y)
    {
        Button button = new ModeButton(this.leftPos + x, this.topPos + y, width, height, mode, (b) ->
                GeneralTools.CHANNEL.sendToServer(new ModeSetPacket(mode)));
        this.addRenderableWidget(button);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY)
    {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(BACKGROUND, x - 1, y - 1, 0, 0, this.imageWidth, this.imageHeight, 176, 168);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY)
    {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // 1.20.1 的 AbstractContainerScreen.render 不调用 renderTooltip，手动渲染 hoveredSlot 物品提示
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private class ModeButton extends Button
    {
        private final int mode;

        ModeButton(int x, int y, int width, int height, int mode, OnPress onPress)
        {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            this.mode = mode;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
        {
            boolean selected = SwissKnifeScreen.this.menu.modeData.get() == mode;
            if (mode == 8)
            {
                // 扳手按钮：独立图集（54×18，三帧 18×18：普通/悬停/选中）
                int u = selected ? 36 : (this.isHoveredOrFocused() ? 18 : 0);
                guiGraphics.blit(WRENCH_BUTTONS, this.getX(), this.getY(), u, 0, this.width, this.height, 54, 18);
                return;
            }
            int u = selected ? 108 : (this.isHoveredOrFocused() ? 54 : 0);
            int v = this.mode == 0 ? 9 : 0;
            guiGraphics.blit(MODE_BUTTONS, this.getX(), this.getY(), u, v, this.width, this.height, 162, 27);
        }
    }
}
