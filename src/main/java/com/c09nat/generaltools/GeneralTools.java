package com.c09nat.generaltools;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(GeneralTools.MODID)
public class GeneralTools
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "generaltools";
    // Create a Deferred Register to hold Items which will all be registered under the "generaltools" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // Create a Deferred Register to hold MenuTypes which will all be registered under the "generaltools" namespace
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "generaltools" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Network channel
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(MODID, "main"),
            () -> "1",
            "1"::equals,
            "1"::equals);

    // The swiss knife
    public static final RegistryObject<Item> SWISS_KNIFE = ITEMS.register("swiss_knife", SwissKnifeItem::new);
    // The swiss knife GUI menu
    public static final RegistryObject<MenuType<SwissKnifeMenu>> SWISS_KNIFE_MENU = MENUS.register("swiss_knife", () -> new MenuType<SwissKnifeMenu>((id, inv) -> new SwissKnifeMenu(id, inv), FeatureFlags.VANILLA_SET));

    // Creates a creative tab with the id "generaltools:example_tab" for the swiss knife
    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .title(Component.translatable("itemGroup.generaltools.example_tab"))
            .icon(() -> SWISS_KNIFE.get().getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(SWISS_KNIFE.get()))
            .build());

    public GeneralTools(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // Register network packets
        CHANNEL.registerMessage(0, ModeSetPacket.class, ModeSetPacket::encode, ModeSetPacket::decode, ModeSetPacket::handle);

        // Register the Deferred Registers to the mod event bus
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "generaltools_blacklist.toml");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Register the swiss knife GUI screen
            event.enqueueWork(() -> MenuScreens.<SwissKnifeMenu, SwissKnifeScreen>register(SWISS_KNIFE_MENU.get(), (menu, inv, title) -> new SwissKnifeScreen(menu, inv, title)));
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event)
        {
            event.register(ClientEvents.MODE_WHEEL_KEY);
        }
    }
}
