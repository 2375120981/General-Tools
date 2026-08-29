package com.c09nat.generaltools;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.function.IntConsumer;

public class RadialModeScreen extends Screen {
    private static final int RADIUS = 90;
    private static final int INNER_RADIUS = 25;
    private static final ResourceLocation WRENCH_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneralTools.MODID, "textures/item/swiss_knife_wrench.png");
    private static final int[] MODES = {1, 2, 3, 7, 8, 6, 5, 4};
    private static final double[] ANGLES = {0, 45, 90, 135, 180, 225, 270, 315};

    private final ItemStack knife;
    private final IntConsumer sender;
    private int centerX;
    private int centerY;
    private int selectedMode = SwissKnifeItem.MODE_AUTO;
    private boolean cancelled;
    private boolean confirmed;

    public RadialModeScreen(ItemStack knife, IntConsumer sender) {
        super(Component.translatable("gui.generaltools.radial_mode"));
        this.knife = knife;
        this.sender = sender;
    }

    @Override
    protected void init() {
        centerX = width / 2;
        centerY = height / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.player == null || !minecraft.player.isAlive()
                || minecraft.player.getMainHandItem() != knife) {
            cancel();
            return;
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ClientEvents.MODE_WHEEL_KEY.matches(keyCode, scanCode)) {
            confirm();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateSelection(mouseX, mouseY);
        graphics.fill(0, 0, width, height, 0x55000000);

        for (int i = 0; i < MODES.length; i++) {
            int color = MODES[i] == selectedMode ? 0xCC45A8FF : 0xB0303030;
            drawSector(graphics, ANGLES[i] - 21.5, ANGLES[i] + 21.5, INNER_RADIUS + 3, RADIUS, color);
        }
        int centerColor = selectedMode == SwissKnifeItem.MODE_AUTO ? 0xDD45A8FF : 0xBB202020;
        drawSector(graphics, 0, 360, 0, INNER_RADIUS, centerColor);
        Component auto = Component.literal("AUTO");
        graphics.drawCenteredString(font, auto, centerX, centerY - font.lineHeight / 2, 0xFFFFFF);

        for (int i = 0; i < MODES.length; i++) {
            int mode = MODES[i];
            double radians = Math.toRadians(ANGLES[i]);
            int x = centerX + (int) Math.round(Math.sin(radians) * 62) - 8;
            int y = centerY - (int) Math.round(Math.cos(radians) * 62) - 8;
            ItemStack tool = mode == SwissKnifeItem.SwissKnifeMode.WRENCH.id
                    ? ItemStack.EMPTY : SwissKnifeItem.getSlotStack(knife, SwissKnifeItem.SwissKnifeMode.values()[mode]);
            boolean enabled = mode == SwissKnifeItem.SwissKnifeMode.WRENCH.id || !tool.isEmpty();
            if (mode == SwissKnifeItem.SwissKnifeMode.WRENCH.id) {
                graphics.blit(WRENCH_TEXTURE, x, y, 0, 0, 16, 16, 16, 16);
            } else if (!tool.isEmpty()) {
                graphics.renderItem(tool, x, y);
            } else {
                graphics.drawCenteredString(font, "×", x + 8, y + 4, enabled ? 0xFFFFFF : 0x777777);
            }
        }

        Component selected = Component.translatable(modeNameKey(selectedMode));
        graphics.drawCenteredString(font, selected, centerX, centerY + RADIUS + 12,
                canSelect(selectedMode) ? 0xFFFFFF : 0x888888);
    }

    private void updateSelection(int mouseX, int mouseY) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        if (dx * dx + dy * dy <= INNER_RADIUS * INNER_RADIUS) {
            selectedMode = SwissKnifeItem.MODE_AUTO;
            return;
        }
        double degree = Math.toDegrees(Math.atan2(dx, -dy));
        if (degree < 0) degree += 360;
        selectedMode = nearestMode(degree);
    }

    private static int nearestMode(double degree) {
        int best = MODES[0];
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < MODES.length; i++) {
            double distance = Math.abs(degree - ANGLES[i]);
            distance = Math.min(distance, 360 - distance);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = MODES[i];
            }
        }
        return best;
    }

    private void drawSector(GuiGraphics graphics, double startDegree, double endDegree,
                            double innerRadius, double outerRadius, int color) {
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (double degree = startDegree; degree < endDegree; degree += 3.0) {
            double next = Math.min(degree + 3.0, endDegree);
            addSectorTriangle(buffer, matrix, degree, next, innerRadius, outerRadius, color);
        }
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        graphics.flush();
    }

    private void addSectorTriangle(BufferBuilder buffer, Matrix4f matrix, double degree, double next,
                                   double innerRadius, double outerRadius, int color) {
        double a = Math.toRadians(degree);
        double b = Math.toRadians(next);
        float ix1 = centerX + (float) Math.sin(a) * (float) innerRadius;
        float iy1 = centerY - (float) Math.cos(a) * (float) innerRadius;
        float ox1 = centerX + (float) Math.sin(a) * (float) outerRadius;
        float oy1 = centerY - (float) Math.cos(a) * (float) outerRadius;
        float ix2 = centerX + (float) Math.sin(b) * (float) innerRadius;
        float iy2 = centerY - (float) Math.cos(b) * (float) innerRadius;
        float ox2 = centerX + (float) Math.sin(b) * (float) outerRadius;
        float oy2 = centerY - (float) Math.cos(b) * (float) outerRadius;
        vertex(buffer, matrix, ix1, iy1, color); vertex(buffer, matrix, ox1, oy1, color); vertex(buffer, matrix, ox2, oy2, color);
        vertex(buffer, matrix, ix1, iy1, color); vertex(buffer, matrix, ox2, oy2, color); vertex(buffer, matrix, ix2, iy2, color);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, int color) {
        buffer.vertex(matrix, x, y, 0).color((color >> 16) & 255, (color >> 8) & 255,
                color & 255, (color >>> 24) & 255).endVertex();
    }

    private boolean canSelect(int mode) {
        return mode == SwissKnifeItem.MODE_AUTO || mode == SwissKnifeItem.SwissKnifeMode.WRENCH.id
                || !SwissKnifeItem.getSlotStack(knife, SwissKnifeItem.SwissKnifeMode.values()[mode]).isEmpty();
    }

    private void confirm() {
        if (confirmed || cancelled) return;
        confirmed = true;
        if (canSelect(selectedMode)) sender.accept(selectedMode);
        if (minecraft != null) minecraft.setScreen(null);
    }

    private void cancel() {
        cancelled = true;
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        cancel();
    }

    private static String modeNameKey(int mode) {
        if (mode == SwissKnifeItem.MODE_AUTO) return "tooltip.generaltools.swiss_knife.auto";
        return "tooltip.generaltools.swiss_knife." + SwissKnifeItem.SwissKnifeMode.values()[mode].name().toLowerCase();
    }
}
