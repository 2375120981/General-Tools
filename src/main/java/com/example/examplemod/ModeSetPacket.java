package com.example.examplemod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ModeSetPacket
{
    private final int mode;

    public ModeSetPacket(int mode)
    {
        this.mode = mode;
    }

    public static void encode(ModeSetPacket msg, FriendlyByteBuf buf)
    {
        buf.writeInt(msg.mode);
    }

    public static ModeSetPacket decode(FriendlyByteBuf buf)
    {
        return new ModeSetPacket(buf.readInt());
    }

    public static void handle(ModeSetPacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null)
            {
                if (player.containerMenu instanceof SwissKnifeMenu menu)
                {
                    menu.modeData.set(msg.mode);
                }
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof SwissKnifeItem)
                {
                    SwissKnifeItem.setModeSetting(stack, msg.mode);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
