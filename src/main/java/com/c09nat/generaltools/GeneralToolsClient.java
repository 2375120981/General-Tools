package com.c09nat.generaltools;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = GeneralTools.MODID, value = Dist.CLIENT)
public final class GeneralToolsClient {
    private GeneralToolsClient() {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(GeneralTools.SWISS_KNIFE_MENU.get(), SwissKnifeScreen::new);
    }

    @SubscribeEvent
    static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ClientEvents.MODE_WHEEL_KEY);
    }
}
