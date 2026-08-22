package com.example.examplemod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITag;
import net.minecraftforge.registries.tags.ITagManager;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Swiss knife config: items that cannot be placed into the tool slots.
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // Blacklist of items that cannot be placed into the swiss knife (registry names, e.g. "minecraft:bedrock")
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_TOOLS = BUILDER
            .comment(
                    "=================== 模组内置 Tag ===================",
                    "本模组自带物品标签：generaltools:blacklist",
                    "带此标签的物品同样会被禁止放入瑞士刀工具槽。",
                    "可在数据包中为该标签添加物品。",
                    "",
                    "=================== ID 禁用 ===================",
                    "按物品 ID（注册表名）禁用放入瑞士刀工具槽的物品。",
                    "示例：blacklistedTools = [\"<黑名单物品ID>\"]",
                    "留空按上方格式填写，多个用英文逗号分隔。",
                    ""
            )
            .defineListAllowEmpty("blacklistedTools", List.of(), Config::validateItemName);

    // Blacklist of item tags that cannot be placed into the swiss knife (tag ids, e.g. "minecraft:logs")
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_TAGS = BUILDER
            .comment(
                    "=================== Tag 禁用 ===================",
                    "按物品标签禁用，标签下所有物品均不可放入。",
                    "示例：blacklistedTags = [\"<黑名单tag>\"]",
                    "留空按上方格式填写，多个用英文逗号分隔。",
                    ""
            )
            .defineListAllowEmpty("blacklistedTags", List.of(), Config::validateTagName);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static final TagKey<Item> BLACKLIST_TAG = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "blacklist"));

    public static Set<Item> blacklistedTools = Collections.emptySet();
    public static Set<TagKey<Item>> blacklistedTags = Collections.emptySet();

    private static boolean validateItemName(final Object obj)
    {
        if (!(obj instanceof final String itemName))
        {
            return false;
        }
        ResourceLocation rl = ResourceLocation.tryParse(itemName);
        return rl != null && ForgeRegistries.ITEMS.containsKey(rl);
    }

    private static boolean validateTagName(final Object obj)
    {
        if (!(obj instanceof final String tagName))
        {
            return false;
        }
        return ResourceLocation.tryParse(tagName) != null;
    }

    public static boolean isBlacklisted(ItemStack stack)
    {
        if (stack.is(BLACKLIST_TAG))
        {
            return true;
        }
        if (blacklistedTools.contains(stack.getItem()))
        {
            return true;
        }
        if (!blacklistedTags.isEmpty())
        {
            ITagManager<Item> tagManager = ForgeRegistries.ITEMS.tags();
            if (tagManager != null)
            {
                for (TagKey<Item> tag : blacklistedTags)
                {
                    ITag<Item> resolved = tagManager.getTag(tag);
                    if (resolved != null && resolved.contains(stack.getItem()))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        blacklistedTools = BLACKLISTED_TOOLS.get().stream()
                .map(itemName -> ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemName)))
                .collect(Collectors.toSet());
        blacklistedTags = BLACKLISTED_TAGS.get().stream()
                .map(tagName -> TagKey.create(Registries.ITEM, ResourceLocation.tryParse(tagName)))
                .collect(Collectors.toSet());
    }
}
