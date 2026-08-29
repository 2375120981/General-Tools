package com.c09nat.generaltools;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = GeneralTools.MODID, value = Dist.CLIENT)
public class ClientEvents
{
    private static int lastAutoMode = -1;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
        {
            lastAutoMode = -1;
            return;
        }
        ItemStack mainHand = mc.player.getMainHandItem();
        if (!(mainHand.getItem() instanceof SwissKnifeItem))
        {
            lastAutoMode = -1;
            return;
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
