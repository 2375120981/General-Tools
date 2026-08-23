package com.example.examplemod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ExampleMod.MODID)
public class ExampleMod {
    public static final String MODID = "generaltools";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<Item, SwissKnifeItem> SWISS_KNIFE =
            ITEMS.registerItem("swiss_knife", SwissKnifeItem::new);
    public static final DeferredHolder<MenuType<?>, MenuType<SwissKnifeMenu>> SWISS_KNIFE_MENU =
            MENUS.register("swiss_knife", () -> new MenuType<>(SwissKnifeMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GENERAL_TOOLS_TAB =
            TABS.register("general_tools", () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("itemGroup.generaltools.example_tab"))
                    .icon(() -> SWISS_KNIFE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(SWISS_KNIFE.get()))
                    .build());

    public ExampleMod(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "generaltools_blacklist.toml");
    }
}
