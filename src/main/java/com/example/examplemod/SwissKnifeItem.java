package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@EventBusSubscriber(modid = ExampleMod.MODID)
public class SwissKnifeItem extends Item {
    public static final String TAG_MODE = "mode";
    public static final int MODE_AUTO = 0;

    public enum SwissKnifeMode {
        NONE(0), SWORD(1), PICKAXE(2), AXE(3), SHOVEL(4), HOE(5), SCISSORS(6), FLINT_AND_STEEL(7), WRENCH(8);
        public final int id;
        SwissKnifeMode(int id) { this.id = id; }
    }

    public SwissKnifeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player.isCrouching()) {
            ItemStack knife = player.getItemInHand(hand);
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, owner) -> new SwissKnifeMenu(id, inventory, knife, level),
                    Component.translatable("gui.generaltools.swiss_knife")));
            return InteractionResultHolder.consume(knife);
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack knife = context.getItemInHand();
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        SwissKnifeMode mode = getEffectiveMode(knife, state);
        if (mode == SwissKnifeMode.WRENCH) return InteractionResult.PASS;
        ItemStack tool = activeTool(knife, state);
        if (tool.isEmpty()) return player.isCrouching() ? InteractionResult.FAIL : InteractionResult.PASS;
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                context.getClickedPos(), context.isInside());
        UseOnContext toolContext = new UseOnContext(context.getLevel(), player, context.getHand(), tool, hit);
        InteractionResult result = withToolInHand(player, context.getHand(), tool,
                () -> tool.getItem().useOn(toolContext), knife, mode);
        return result == InteractionResult.PASS && player.isCrouching() ? InteractionResult.FAIL : result;
    }

    private static InteractionResult withToolInHand(Player player, InteractionHand hand, ItemStack tool,
                                                     Supplier<InteractionResult> action,
                                                     ItemStack knife, SwissKnifeMode mode) {
        ItemStack original = player.getItemInHand(hand);
        ItemStack backup = tool.copy();
        player.setItemInHand(hand, tool);
        try { return action.get(); }
        finally {
            ItemStack resultingTool = protectDurability(backup, player.getItemInHand(hand));
            player.setItemInHand(hand, original);
            setSlotStack(knife, mode, resultingTool);
        }
    }

    /** 标准耐久工具若在一次代理操作中损坏，则恢复为剩余 1 点耐久。 */
    private static ItemStack protectDurability(ItemStack before, ItemStack after) {
        if (before.isEmpty() || !before.isDamageableItem() || before.getMaxDamage() <= 1) return after;
        int protectedDamage = before.getMaxDamage() - 1;
        if (after.isEmpty()) {
            ItemStack restored = before.copy();
            restored.setDamageValue(protectedDamage);
            return restored;
        }
        if (after.getItem() == before.getItem() && after.getDamageValue() >= protectedDamage) {
            after.setDamageValue(protectedDamage);
        }
        return after;
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack knife = player.getItemInHand(event.getHand());
        if (player.level().isClientSide || !(knife.getItem() instanceof SwissKnifeItem)
                || getModeSetting(knife) != SwissKnifeMode.SCISSORS.id
                || !(event.getTarget() instanceof LivingEntity target)) return;
        ItemStack shears = getSlotStack(knife, SwissKnifeMode.SCISSORS);
        if (shears.isEmpty()) return;
        InteractionResult result = withToolInHand(player, event.getHand(), shears, () -> {
            InteractionResult delegated = shears.interactLivingEntity(player, target, event.getHand());
            if (delegated.consumesAction()) return delegated;
            if (target instanceof Sheep sheep && !sheep.isSheared()) {
                sheep.shear(SoundSource.PLAYERS);
                if (!player.getAbilities().instabuild) {
                    shears.hurtAndBreak(1, player,
                            event.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }, knife, SwissKnifeMode.SCISSORS);
        if (result.consumesAction()) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    // 经验修补不会复制到瑞士刀本体；玩家获得经验时，单独修复主手瑞士刀内带经验修补的受损工具。
    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        int amount = event.getAmount();
        Player player = event.getEntity();
        if (amount <= 0 || !(player.level() instanceof ServerLevel serverLevel)
                || !(player.getMainHandItem().getItem() instanceof SwissKnifeItem)) return;

        ItemStack knife = player.getMainHandItem();
        for (SwissKnifeMode mode : SwissKnifeMode.values()) {
            if (mode == SwissKnifeMode.NONE || mode == SwissKnifeMode.WRENCH) continue;
            ItemStack tool = getStoredSlotStack(knife, mode);
            if (tool.isEmpty() || !tool.isDamaged() || !hasMending(tool)) continue;

            int repairCapacity = EnchantmentHelper.modifyDurabilityToRepairFromXp(
                    serverLevel, tool, (int) (amount * tool.getXpRepairRatio()));
            int repaired = Math.min(repairCapacity, tool.getDamageValue());
            if (repaired <= 0 || repairCapacity <= 0) continue;

            tool.setDamageValue(tool.getDamageValue() - repaired);
            setSlotStack(knife, mode, tool);
            amount -= repaired * amount / repairCapacity;
            if (amount <= 0) break;
        }
        event.setAmount(amount);
    }

    private static boolean hasMending(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .keySet().stream().anyMatch(enchantment -> enchantment.is(Enchantments.MENDING));
    }

    /**
     * 在原版开始计算攻击伤害之前，把剑槽内可继承的附魔同步到瑞士刀。
     * 这样锋利、亡灵杀手、火焰附加、击退、抢夺以及模组的非持续型战斗附魔
     * 都会参与本次攻击；经验修补仍由拾取经验事件单独处理。
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        ItemStack knife = event.getEntity().getMainHandItem();
        if (!(knife.getItem() instanceof SwissKnifeItem)) return;

        ItemStack sword = getSlotStack(knife, SwissKnifeMode.SWORD);
        setMode(knife, sword.isEmpty() ? SwissKnifeMode.NONE : SwissKnifeMode.SWORD);
        applyToolEnchantments(knife, sword);
    }

    /**
     * 瑞士刀被查询主手属性时，直接采用剑槽内物品经过 NeoForge 事件修正后的有效属性。
     * 复制完整属性列表而不是硬编码攻击伤害和攻速，以兼容模组剑的额外属性。
     */
    @SubscribeEvent
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack knife = event.getItemStack();
        if (!(knife.getItem() instanceof SwissKnifeItem)) return;

        event.clearModifiers();
        ItemStack sword = getSlotStack(knife, SwissKnifeMode.SWORD);
        if (sword.isEmpty()) return;

        ItemAttributeModifiers swordModifiers = sword.getAttributeModifiers();
        for (ItemAttributeModifiers.Entry entry : swordModifiers.modifiers()) {
            event.addModifier(entry.attribute(), entry.modifier(), entry.slot());
        }
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        String name = ability.name().toLowerCase(Locale.ROOT);
        if (name.contains("shovel") || name.contains("flatten")) return isActive(stack, SwissKnifeMode.SHOVEL);
        if (name.contains("pickaxe")) return isActive(stack, SwissKnifeMode.PICKAXE);
        if (name.contains("sword")) return isActive(stack, SwissKnifeMode.SWORD);
        if (name.contains("axe")) return isActive(stack, SwissKnifeMode.AXE);
        if (name.contains("hoe") || name.contains("till")) return isActive(stack, SwissKnifeMode.HOE);
        if (name.contains("shears") || name.contains("scissors")) return isActive(stack, SwissKnifeMode.SCISSORS);
        return false;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        ItemStack tool = activeTool(stack, state);
        return tool.isEmpty() ? 1.0F : tool.getDestroySpeed(state);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        SwissKnifeMode mode = getEffectiveMode(stack, state);
        ItemStack tool = activeTool(stack, state);
        if (tool.isEmpty()) return super.mineBlock(stack, level, state, pos, miner);
        ItemStack backup = tool.copy();
        boolean result = tool.getItem().mineBlock(tool, level, state, pos, miner);
        setSlotStack(stack, mode, protectDurability(backup, tool));
        return result;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        ItemStack tool = getSlotStack(stack, getEffectiveMode(stack, state));
        return !tool.isEmpty() && tool.isCorrectToolForDrops(state);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        setMode(stack, SwissKnifeMode.SWORD);
        ItemStack sword = getSlotStack(stack, SwissKnifeMode.SWORD);
        if (sword.isEmpty()) return true;
        ItemStack backup = sword.copy();
        boolean result = sword.getItem().hurtEnemy(sword, target, attacker);
        setSlotStack(stack, SwissKnifeMode.SWORD, protectDurability(backup, sword));
        return result;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        ItemStack sword = getSlotStack(stack, SwissKnifeMode.SWORD);
        if (sword.isEmpty()) return;
        ItemStack backup = sword.copy();
        sword.getItem().postHurtEnemy(sword, target, attacker);
        setSlotStack(stack, SwissKnifeMode.SWORD, protectDurability(backup, sword));
    }

    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource damageSource) {
        ItemStack knife = damageSource.getEntity() instanceof LivingEntity attacker
                ? attacker.getMainHandItem()
                : ItemStack.EMPTY;
        if (!(knife.getItem() instanceof SwissKnifeItem)) return 0.0F;

        ItemStack sword = getSlotStack(knife, SwissKnifeMode.SWORD);
        return sword.isEmpty() ? 0.0F : sword.getItem().getAttackDamageBonus(target, damage, damageSource);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("///").withStyle(ChatFormatting.BOLD)
                .append(Component.translatable("tooltip.generaltools.swiss_knife.title_text")
                        .withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA))
                .append(Component.literal("\\\\\\").withStyle(ChatFormatting.BOLD)));
        List<Component> equipped = new ArrayList<>();
        for (SwissKnifeMode mode : SwissKnifeMode.values()) {
            if (mode == SwissKnifeMode.NONE || mode == SwissKnifeMode.WRENCH) continue;
            ItemStack tool = getSlotStack(stack, mode);
            if (!tool.isEmpty()) equipped.add(tool.getHoverName());
        }
        if (equipped.isEmpty()) tooltip.add(Component.translatable("tooltip.generaltools.swiss_knife.empty").withStyle(ChatFormatting.RED));
        else tooltip.addAll(equipped);
        tooltip.add(Component.literal("\\\\\\＝＝＝///").withStyle(ChatFormatting.BOLD));
        tooltip.add(Component.translatable("tooltip.generaltools.swiss_knife.mode",
                Component.translatable(modeNameKey(getModeSetting(stack))))
                .withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private static String modeNameKey(int setting) {
        if (setting == MODE_AUTO) return "tooltip.generaltools.swiss_knife.auto";
        SwissKnifeMode[] modes = SwissKnifeMode.values();
        return setting > 0 && setting < modes.length
                ? "tooltip.generaltools.swiss_knife." + modes[setting].name().toLowerCase(Locale.ROOT)
                : "tooltip.generaltools.swiss_knife.auto";
    }

    public static ItemStack getSlotStack(ItemStack knife, SwissKnifeMode mode) {
        ItemStack tool = getStoredSlotStack(knife, mode);
        if (tool.isEmpty()) return ItemStack.EMPTY;
        if (tool.getMaxDamage() > 0 && tool.getDamageValue() >= tool.getMaxDamage() - 1) return ItemStack.EMPTY;
        return tool;
    }

    public static ItemStack getStoredSlotStack(ItemStack knife, SwissKnifeMode mode) {
        if (knife.isEmpty() || mode == SwissKnifeMode.NONE || mode == SwissKnifeMode.WRENCH) return ItemStack.EMPTY;
        ItemContainerContents contents = knife.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (mode.ordinal() - 1 >= contents.getSlots()) return ItemStack.EMPTY;
        return contents.getStackInSlot(mode.ordinal() - 1);
    }

    public static void setSlotStack(ItemStack knife, SwissKnifeMode mode, ItemStack tool) {
        if (knife.isEmpty() || mode == SwissKnifeMode.NONE || mode == SwissKnifeMode.WRENCH) return;
        NonNullList<ItemStack> items = NonNullList.withSize(7, ItemStack.EMPTY);
        knife.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        items.set(mode.ordinal() - 1, tool.copy());
        knife.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    public static boolean isActive(ItemStack knife, SwissKnifeMode mode) {
        return !getSlotStack(knife, mode).isEmpty();
    }

    private static ItemStack activeTool(ItemStack knife, BlockState state) {
        SwissKnifeMode mode = getEffectiveMode(knife, state);
        ItemStack tool = getSlotStack(knife, mode);
        setMode(knife, tool.isEmpty() && mode != SwissKnifeMode.WRENCH ? SwissKnifeMode.NONE : mode);
        applyToolEnchantments(knife, tool);
        return tool;
    }

    private static void applyToolEnchantments(ItemStack knife, ItemStack tool) {
        ItemEnchantments source = tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable inherited = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : source.keySet()) {
            if (isInheritableToolEnchantment(enchantment)) inherited.set(enchantment, source.getLevel(enchantment));
        }
        knife.set(DataComponents.ENCHANTMENTS, inherited.toImmutable());
    }

    private static boolean isInheritableToolEnchantment(Holder<Enchantment> enchantment) {
        // 经验修补由 onXpChange 直接作用于槽内工具，不能复制到无耐久的瑞士刀本体。
        if (enchantment.is(Enchantments.MENDING)) return false;

        // 持续 tick / 随位置变化的附魔会把瑞士刀当作真正装备持续执行，明确不继承。
        // 其余附魔来自槽内真实工具，保留其数据驱动效果组件，因此自动兼容模组的
        // 挖掘效率、方块掉落、耐久、击中方块及其它非持续型工具附魔。
        return !enchantment.value().effects().has(EnchantmentEffectComponents.TICK)
                && !enchantment.value().effects().has(EnchantmentEffectComponents.LOCATION_CHANGED);
    }

    public static void setModeSetting(ItemStack stack, int mode) {
        if (stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG_MODE, mode));
        if (mode > MODE_AUTO && mode < SwissKnifeMode.values().length) setMode(stack, SwissKnifeMode.values()[mode]);
    }

    public static int getModeSetting(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(TAG_MODE);
    }

    public static SwissKnifeMode getEffectiveMode(ItemStack stack, BlockState state) {
        int setting = getModeSetting(stack);
        return setting == MODE_AUTO ? modeFor(state)
                : setting > 0 && setting < SwissKnifeMode.values().length ? SwissKnifeMode.values()[setting] : SwissKnifeMode.NONE;
    }

    public static void setMode(ItemStack stack, SwissKnifeMode mode) {
        if (!stack.isEmpty()) stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(mode.id));
    }

    public static SwissKnifeMode modeFor(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return SwissKnifeMode.PICKAXE;
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return SwissKnifeMode.AXE;
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return SwissKnifeMode.SHOVEL;
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) return SwissKnifeMode.HOE;
        return SwissKnifeMode.NONE;
    }
}
