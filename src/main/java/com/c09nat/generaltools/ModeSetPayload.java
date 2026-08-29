package com.c09nat.generaltools;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ModeSetPayload(int mode) implements CustomPacketPayload {
    public static final Type<ModeSetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(GeneralTools.MODID, "set_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ModeSetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ModeSetPayload::mode, ModeSetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ModeSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.mode < SwissKnifeItem.MODE_AUTO
                    || payload.mode >= SwissKnifeItem.SwissKnifeMode.values().length) return;
            ItemStack stack = context.player().getMainHandItem();
            if (stack.getItem() instanceof SwissKnifeItem) {
                SwissKnifeItem.setModeSetting(stack, payload.mode);
            }
        });
    }
}
