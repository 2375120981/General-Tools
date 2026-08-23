package com.example.examplemod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = ExampleMod.MODID)
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_TOOLS = BUILDER
            .comment("禁止放入瑞士刀工具槽的物品 ID，例如 minecraft:bedrock")
            .defineListAllowEmpty("blacklistedTools", List.of(), Config::validId);
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_TAGS = BUILDER
            .comment("禁止放入瑞士刀工具槽的物品标签，例如 minecraft:logs")
            .defineListAllowEmpty("blacklistedTags", List.of(), Config::validId);

    static final ModConfigSpec SPEC = BUILDER.build();
    public static final TagKey<Item> BLACKLIST_TAG = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "blacklist"));
    private static Set<Item> blacklistedTools = Collections.emptySet();
    private static Set<TagKey<Item>> blacklistedTags = Collections.emptySet();

    private Config() {}

    private static boolean validId(Object value) {
        return value instanceof String text && ResourceLocation.tryParse(text) != null;
    }

    public static boolean isBlacklisted(ItemStack stack) {
        if (stack.is(BLACKLIST_TAG) || blacklistedTools.contains(stack.getItem())) return true;
        return blacklistedTags.stream().anyMatch(stack::is);
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        blacklistedTools = BLACKLISTED_TOOLS.get().stream()
                .map(ResourceLocation::tryParse)
                .filter(BuiltInRegistries.ITEM::containsKey)
                .map(BuiltInRegistries.ITEM::get)
                .collect(Collectors.toUnmodifiableSet());
        blacklistedTags = BLACKLISTED_TAGS.get().stream()
                .map(ResourceLocation::tryParse)
                .map(id -> TagKey.create(Registries.ITEM, id))
                .collect(Collectors.toUnmodifiableSet());
    }
}
