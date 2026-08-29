package com.c09nat.generaltools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = GeneralTools.MODID, value = Dist.CLIENT)
public class ClientEvents
{
    public static final KeyMapping MODE_WHEEL_KEY = new KeyMapping(
            "key.generaltools.mode_wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
            "key.categories.generaltools");
    private static final long HOLD_DELAY_MS = 50L;
    private static int lastAutoMode = -1;
    private static long keyPressedAt = -1L;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
        {
            lastAutoMode = -1;
            return;
        }
        if (mc.screen instanceof RadialModeScreen)
        {
            return;
        }
        ItemStack mainHand = mc.player.getMainHandItem();
        if (!(mainHand.getItem() instanceof SwissKnifeItem))
        {
            lastAutoMode = -1;
            keyPressedAt = -1L;
            return;
        }
        if (mc.screen == null && MODE_WHEEL_KEY.isDown())
        {
            if (keyPressedAt < 0L)
            {
                keyPressedAt = net.minecraft.Util.getMillis();
            }
            else if (net.minecraft.Util.getMillis() - keyPressedAt >= HOLD_DELAY_MS)
            {
                mc.setScreen(new RadialModeScreen(mainHand,
                        mode -> GeneralTools.CHANNEL.sendToServer(new ModeSetPacket(mode))));
                keyPressedAt = -1L;
                return;
            }
        }
        else
        {
            keyPressedAt = -1L;
        }
        if (SwissKnifeItem.getModeSetting(mainHand) != SwissKnifeItem.MODE_AUTO)
        {
            lastAutoMode = -1;
            return;
        }
        // 自动模式：按目视方块即时切换贴图（创造模式挖掘不走 getDestroySpeed，故用射线检测兜底）
        HitResult hit = mc.player.pick(5.0D, 1.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK)
        {
            BlockState state = mc.level.getBlockState(((BlockHitResult) hit).getBlockPos());
            // 统一由有效模式解析器决定并写入外观，避免贴图模式与实际槽位工具不一致。
            SwissKnifeItem.SwissKnifeMode mode = SwissKnifeItem.getEffectiveMode(mainHand, state);
            // 仅模式变化时写 NBT，减少重复写入
            if (mode.id != lastAutoMode)
            {
                lastAutoMode = mode.id;
                SwissKnifeItem.setMode(mainHand, mode);
            }
        }
    }
}
