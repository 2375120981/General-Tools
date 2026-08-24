package com.c09nat.generaltools;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = GeneralTools.MODID, value = Dist.CLIENT)
public final class GeneralToolsClient {
    private GeneralToolsClient() {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(GeneralTools.SWISS_KNIFE_MENU.get(), SwissKnifeScreen::new);
    }
}
