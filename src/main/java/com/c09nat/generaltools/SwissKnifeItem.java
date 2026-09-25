package com.c09nat.generaltools;

import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.ToolAction;

import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = GeneralTools.MODID)
public class SwissKnifeItem extends Item
{
    public static final String TAG_SWISS = "swiss_knife";
    public static final String TAG_MODE = "mode";
    public static final int MODE_AUTO = 0;

    public enum SwissKnifeMode
    {
        NONE(0), SWORD(1), PICKAXE(2), AXE(3), SHOVEL(4), HOE(5), SCISSORS(6), FLINT_AND_STEEL(7), WRENCH(8);

        public final int id;

        SwissKnifeMode(int id)
        {
            this.id = id;
        }
    }

    public SwissKnifeItem()
    {
        super(new Item.Properties().stacksTo(1));
    }

    // ---------- 潜行+右键（对空气）打开 GUI ----------
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        if (!level.isClientSide() && player.isCrouching())
        {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> new SwissKnifeMenu(containerId, inventory, p.getMainHandItem(), level),
                    Component.translatable("gui.generaltools.swiss_knife")));
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    // ---------- 对方块右键：扳手模式完全交给 AE2 等 mod 的 wrench 机制，其余模式临时伪装成槽位工具执行其 useOn（完整继承功能/附魔/充能） ----------
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        if (player == null)
        {
            return InteractionResult.PASS;
        }
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        ItemStack knife = context.getItemInHand();

        SwissKnifeMode mode = getEffectiveMode(knife, state);

        // 扳手模式：复刻 AE2 石英扳手，旋转/拆除由 AE2 WrenchHook 等通过 wrench tag 接管；非 AE2 方块返回 FAIL（无效果）
        if (mode == SwissKnifeMode.WRENCH)
        {
            return InteractionResult.FAIL;
        }

        ItemStack tool = activeTool(knife, state);
        if (tool.isEmpty())
        {
            return player.isCrouching() ? InteractionResult.FAIL : InteractionResult.PASS;
        }

        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        UseOnContext toolContext = new UseOnContext(context.getLevel(), player, context.getHand(), tool, hit);

        InteractionResult result = withToolInHand(player, context.getHand(), tool,
                () -> tool.getItem().useOn(toolContext), knife, mode);

        if (result == InteractionResult.PASS && player.isCrouching())
        {
            return InteractionResult.FAIL;
        }
        return result;
    }

    // 解析当前生效模式并应用 setMode + 附魔继承，返回槽位工具；空则复位 NONE 并返回 EMPTY
    private static ItemStack activeTool(ItemStack knife, BlockState state)
    {
        SwissKnifeMode mode = getEffectiveMode(knife, state);
        ItemStack tool = getSlotStack(knife, mode);
        if (tool.isEmpty())
        {
            // 扳手模式无槽位工具：保持扳手贴图；其余模式空工具复位 NONE
            setMode(knife, mode == SwissKnifeMode.WRENCH ? SwissKnifeMode.WRENCH : SwissKnifeMode.NONE);
            applyToolEnchantments(knife, SwissKnifeMode.NONE);
            return ItemStack.EMPTY;
        }
        setMode(knife, mode);
        applyToolEnchantments(knife, mode);
        return tool;
    }

    // 临时把玩家主手替换为槽位工具执行 action，恢复主手并写回工具状态（耐久/充能正常消耗）
    private static InteractionResult withToolInHand(Player player, InteractionHand hand, ItemStack tool,
                                                     Supplier<InteractionResult> action, ItemStack knife,
                                                     SwissKnifeMode mode)
    {
        ItemStack original = player.getItemInHand(hand);
        ItemStack backup = tool.copy();
        player.setItemInHand(hand, tool);
        try
        {
            return action.get();
        }
        finally
        {
            ItemStack resultingTool = protectDurability(backup, player.getItemInHand(hand));
            player.setItemInHand(hand, original);
            setSlotStack(knife, mode, resultingTool);
        }
    }

    /**
     * 方块会先于物品的 useOn 检查玩家手中的物品。打火石模式必须在这一阶段就让方块看到
     * 槽内的真实打火石，否则 TNT 以及采用相同实现方式的模组方块只会看到瑞士刀。
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack knife = player.getItemInHand(hand);
        if (!(knife.getItem() instanceof SwissKnifeItem))
        {
            return;
        }

        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (getEffectiveMode(knife, state) != SwissKnifeMode.FLINT_AND_STEEL)
        {
            return;
        }

        ItemStack flintAndSteel = getSlotStack(knife, SwissKnifeMode.FLINT_AND_STEEL);
        if (flintAndSteel.isEmpty())
        {
            return;
        }

        InteractionResult result = withToolInHand(player, hand, flintAndSteel,
                () -> state.use(level, player, hand, event.getHitVec()),
                knife, SwissKnifeMode.FLINT_AND_STEEL);

        if (result.consumesAction() || result == InteractionResult.FAIL)
        {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    /** 标准耐久工具若在一次代理操作中损坏，则恢复为剩余 1 点耐久。 */
    private static ItemStack protectDurability(ItemStack before, ItemStack after)
    {
        if (before.isEmpty() || !before.isDamageableItem() || before.getMaxDamage() <= 1)
        {
            return after;
        }
        int protectedDamage = before.getMaxDamage() - 1;
        if (after.isEmpty())
        {
            ItemStack restored = before.copy();
            restored.setDamageValue(protectedDamage);
            return restored;
        }
        if (after.getItem() == before.getItem() && after.getDamageValue() >= protectedDamage)
        {
            after.setDamageValue(protectedDamage);
        }
        return after;
    }

    // ---------- 剪羊毛：剪刀模式右键未剪毛的羊时，临时伪装成剪刀并直接调用原版剪毛逻辑 ----------
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event)
    {
        Player player = event.getEntity();
        if (player.level().isClientSide)
        {
            return;
        }
        ItemStack knife = player.getItemInHand(event.getHand());
        if (!(knife.getItem() instanceof SwissKnifeItem))
        {
            return;
        }
        if (getModeSetting(knife) != SwissKnifeMode.SCISSORS.id)
        {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target))
        {
            return;
        }
        ItemStack scissors = getSlotStack(knife, SwissKnifeMode.SCISSORS);
        if (scissors.isEmpty())
        {
            return;
        }
        InteractionResult result = withToolInHand(player, event.getHand(), scissors, () -> {
            InteractionResult delegated = scissors.interactLivingEntity(player, target, event.getHand());
            if (delegated.consumesAction())
            {
                return delegated;
            }
            if (target instanceof Sheep sheep && !sheep.isSheared())
            {
                sheep.shear(SoundSource.PLAYERS);
                if (!player.getAbilities().instabuild)
                {
                    scissors.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(event.getHand()));
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }, knife, SwissKnifeMode.SCISSORS);
        if (result.consumesAction())
        {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    // ---------- 经验修补：玩家获得经验时，用经验修复瑞士刀槽位内带 Mending 且耐久不满的工具（原版只查手持物品自身 NBT，不进入瑞士刀内部） ----------
    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event)
    {
        int amount = event.getAmount();
        if (amount <= 0)
        {
            return;
        }
        Player player = event.getEntity();
        ItemStack knife = player.getMainHandItem();
        if (!(knife.getItem() instanceof SwissKnifeItem))
        {
            return;
        }
        for (SwissKnifeMode mode : SwissKnifeMode.values())
        {
            if (mode == SwissKnifeMode.NONE)
            {
                continue;
            }
            ItemStack tool = getStoredSlotStack(knife, mode);
            if (tool.isEmpty() || !tool.isDamaged())
            {
                continue;
            }
            if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, tool) <= 0)
            {
                continue;
            }
            float repairRatio = tool.getXpRepairRatio();
            if (repairRatio <= 0.0F)
            {
                continue;
            }
            int repair = Math.min((int) (amount * repairRatio), tool.getDamageValue());
            if (repair <= 0)
            {
                continue;
            }
            tool.setDamageValue(tool.getDamageValue() - repair);
            setSlotStack(knife, mode, tool);
            int xpUsed = Math.min(amount, Math.max(1, (int) Math.ceil(repair / repairRatio)));
            amount -= xpUsed;
            if (amount <= 0)
            {
                break;
            }
        }
        event.setAmount(Math.max(0, amount));
    }

    /** 在原版计算本次攻击前同步剑槽内的附魔，避免在属性查询过程中修改物品 NBT。 */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event)
    {
        ItemStack knife = event.getEntity().getMainHandItem();
        if (!(knife.getItem() instanceof SwissKnifeItem))
        {
            return;
        }
        ItemStack sword = getSlotStack(knife, SwissKnifeMode.SWORD);
        setMode(knife, sword.isEmpty() ? SwissKnifeMode.NONE : SwissKnifeMode.SWORD);
        applyToolEnchantments(knife, SwissKnifeMode.SWORD);
    }

    /** 直接采用内部剑经过 Forge 事件修正后的有效属性，兼容模组剑的攻速与额外属性。 */
    @SubscribeEvent
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event)
    {
        ItemStack knife = event.getItemStack();
        if (!(knife.getItem() instanceof SwissKnifeItem))
        {
            return;
        }
        event.clearModifiers();
        ItemStack sword = getSlotStack(knife, SwissKnifeMode.SWORD);
        if (sword.isEmpty())
        {
            return;
        }
        Multimap<Attribute, AttributeModifier> swordModifiers = sword.getAttributeModifiers(event.getSlotType());
        for (Map.Entry<Attribute, AttributeModifier> entry : swordModifiers.entries())
        {
            event.addModifier(entry.getKey(), entry.getValue());
        }
    }

    // ---------- 声明瑞士刀能执行的工具动作：按动作名归类工具类型，按槽位判定（与当前切换状态无关） ----------
    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction)
    {
        String name = toolAction.name().toLowerCase(Locale.ROOT);
        if (name.contains("shovel") || name.contains("flatten"))
        {
            return isActive(stack, SwissKnifeMode.SHOVEL);
        }
        if (name.contains("pickaxe"))
        {
            return isActive(stack, SwissKnifeMode.PICKAXE);
        }
        if (name.contains("sword"))
        {
            return isActive(stack, SwissKnifeMode.SWORD);
        }
        if (name.contains("axe"))
        {
            return isActive(stack, SwissKnifeMode.AXE);
        }
        if (name.contains("hoe") || name.contains("till"))
        {
            return isActive(stack, SwissKnifeMode.HOE);
        }
        if (name.contains("scissors") || name.contains("shears"))
        {
            return isActive(stack, SwissKnifeMode.SCISSORS);
        }
        return false;
    }

    // ---------- 挖掘：委托当前生效模式的槽位工具，并继承其附魔（效率附魔由原版 Player.getDigSpeed 自动结算） ----------
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state)
    {
        ItemStack tool = activeTool(stack, state);
        return tool.isEmpty() ? 1.0F : tool.getDestroySpeed(state);
    }

    // 挖掘完成时触发：委托当前模式槽位工具（范围挖掘、耐久消耗等效果）
    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner)
    {
        SwissKnifeMode mode = getEffectiveMode(stack, state);
        ItemStack tool = activeTool(stack, state);
        if (tool.isEmpty())
        {
            return super.mineBlock(stack, level, state, pos, miner);
        }
        ItemStack backup = tool.copy();
        boolean result = tool.getItem().mineBlock(tool, level, state, pos, miner);
        setSlotStack(stack, mode, protectDurability(backup, tool));
        return result;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state)
    {
        if (state.requiresCorrectToolForDrops())
        {
            SwissKnifeMode mode = getEffectiveMode(stack, state);
            ItemStack tool = getSlotStack(stack, mode);
            return !tool.isEmpty() && tool.isCorrectToolForDrops(state);
        }
        return false;
    }

    // ---------- 攻击：伤害/攻速由动态属性事件继承；此处委托内部剑命中回调并写回耐久及自定义状态 ----------
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker)
    {
        setMode(stack, SwissKnifeMode.SWORD);
        ItemStack sword = getSlotStack(stack, SwissKnifeMode.SWORD);
        if (sword.isEmpty())
        {
            return true;
        }
        ItemStack backup = sword.copy();
        boolean result = sword.getItem().hurtEnemy(sword, target, attacker);
        setSlotStack(stack, SwissKnifeMode.SWORD, protectDurability(backup, sword));
        return result;
    }

    private static final String TITLE_SLASH = "///";
    private static final String TITLE_BSLASH = "\\\\\\";

    // ---------- 工具提示：斜线装饰标题 + 工具列表 + 分割线 + 模式 ----------
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        // 标题行：///当前已装备\\\  （斜线只加粗，文字加粗+青色）
        Component title = Component.literal(TITLE_SLASH).withStyle(ChatFormatting.BOLD)
                .append(Component.translatable("tooltip.generaltools.swiss_knife.title_text").withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA))
                .append(Component.literal(TITLE_BSLASH).withStyle(ChatFormatting.BOLD));
        tooltip.add(title);

        // 工具列表（保持工具原名），未装备时显示【空】
        List<Component> equipped = new ArrayList<>();
        for (SwissKnifeMode mode : SwissKnifeMode.values())
        {
            if (mode == SwissKnifeMode.NONE)
            {
                continue;
            }
            ItemStack tool = getSlotStack(stack, mode);
            if (!tool.isEmpty())
            {
                equipped.add(tool.getHoverName());
            }
        }
        if (equipped.isEmpty())
        {
            tooltip.add(Component.translatable("tooltip.generaltools.swiss_knife.empty").withStyle(ChatFormatting.RED));
        }
        else
        {
            tooltip.addAll(equipped);
        }

        // 分割线：\\\\\\ + =填充 + //////，宽度≈标题行，整行加粗
        tooltip.add(buildDivider(title));

        // 模式行
        tooltip.add(Component.translatable("tooltip.generaltools.swiss_knife.mode",
                Component.translatable(modeNameKey(getModeSetting(stack)))).withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    private Component buildDivider(Component title)
    {
        Font font = Minecraft.getInstance().font;
        if (font == null)
        {
            return Component.literal(TITLE_BSLASH + "＝＝＝" + TITLE_SLASH).withStyle(ChatFormatting.BOLD);
        }
        int titleWidth = font.width(title.getString());
        int baseWidth = font.width(TITLE_BSLASH) + font.width(TITLE_SLASH);
        StringBuilder filler = new StringBuilder();
        while (baseWidth + font.width(filler.toString()) < titleWidth)
        {
            filler.append('＝');
        }
        if (filler.length() > 0 && baseWidth + font.width(filler.toString()) > titleWidth)
        {
            filler.setLength(filler.length() - 1);
        }
        return Component.literal(TITLE_BSLASH + filler + TITLE_SLASH).withStyle(ChatFormatting.BOLD);
    }

    private String modeNameKey(int setting)
    {
        if (setting == MODE_AUTO)
        {
            return "tooltip.generaltools.swiss_knife.auto";
        }
        SwissKnifeMode[] modes = SwissKnifeMode.values();
        if (setting >= 0 && setting < modes.length)
        {
            return "tooltip.generaltools.swiss_knife." + modes[setting].name().toLowerCase(Locale.ROOT);
        }
        return "tooltip.generaltools.swiss_knife.auto";
    }

    // ---------- NBT 数据与模式工具 ----------
    public static CompoundTag getSwissData(ItemStack stack)
    {
        return stack.getOrCreateTag().getCompound(TAG_SWISS);
    }

    public static void setSwissData(ItemStack stack, CompoundTag root)
    {
        if (!stack.isEmpty())
        {
            stack.getOrCreateTag().put(TAG_SWISS, root);
        }
    }

    public static ItemStack getSlotStack(ItemStack knife, SwissKnifeMode mode)
    {
        ItemStack tool = getStoredSlotStack(knife, mode);
        if (tool.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        // 保护：剩余耐久 ≤ 1 时视为空槽（功能禁用，工具保留 1 耐久不损坏）
        if (tool.getMaxDamage() > 0 && tool.getDamageValue() >= tool.getMaxDamage() - 1)
        {
            return ItemStack.EMPTY;
        }
        return tool;
    }

    /** 读取真实槽位内容，不应用剩余 1 点耐久时的功能禁用保护。 */
    public static ItemStack getStoredSlotStack(ItemStack knife, SwissKnifeMode mode)
    {
        if (knife.isEmpty() || mode == SwissKnifeMode.NONE || mode == SwissKnifeMode.WRENCH)
        {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(getSwissData(knife).getCompound("slot_" + mode.ordinal()));
    }

    public static void setSlotStack(ItemStack knife, SwissKnifeMode mode, ItemStack tool)
    {
        if (knife.isEmpty() || mode == SwissKnifeMode.NONE)
        {
            return;
        }
        CompoundTag root = getSwissData(knife);
        if (tool.isEmpty())
        {
            root.remove("slot_" + mode.ordinal());
        }
        else
        {
            root.put("slot_" + mode.ordinal(), tool.save(new CompoundTag()));
        }
        setSwissData(knife, root);
    }

    public static boolean isActive(ItemStack knife, SwissKnifeMode mode)
    {
        return !getSlotStack(knife, mode).isEmpty();
    }

    // ---------- 附魔继承：把当前模式槽位工具的附魔合并到瑞士刀 NBT（使原版机制自动生效） ----------
    public static void applyToolEnchantments(ItemStack knife, SwissKnifeMode mode)
    {
        if (knife.isEmpty())
        {
            return;
        }
        ItemStack tool = getSlotStack(knife, mode);
        ListTag list = new ListTag();
        if (!tool.isEmpty())
        {
            for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(tool).entrySet())
            {
                // 经验修补直接作用于槽内真实工具，不能复制到无耐久的瑞士刀本体。
                if (entry.getKey() == Enchantments.MENDING)
                {
                    continue;
                }
                CompoundTag tag = new CompoundTag();
                tag.putString("id", EnchantmentHelper.getEnchantmentId(entry.getKey()).toString());
                tag.putInt("lvl", entry.getValue());
                list.add(tag);
            }
        }
        if (list.isEmpty())
        {
            CompoundTag tag = knife.getOrCreateTag();
            if (tag.contains("Enchantments"))
            {
                tag.remove("Enchantments");
            }
        }
        else
        {
            knife.getOrCreateTag().put("Enchantments", list);
        }
    }

    // ---------- 模式设置（自动 / 锁定到某工具） ----------
    public static void setModeSetting(ItemStack stack, int mode)
    {
        if (stack.isEmpty())
        {
            return;
        }
        CompoundTag root = getSwissData(stack);
        root.putInt(TAG_MODE, mode);
        setSwissData(stack, root);
        // 手动锁定模式时立即切换贴图（不依赖指向方块）；自动模式保持当前贴图
        if (mode != MODE_AUTO)
        {
            SwissKnifeMode[] modes = SwissKnifeMode.values();
            if (mode >= 0 && mode < modes.length)
            {
                setMode(stack, modes[mode]);
            }
        }
    }

    public static int getModeSetting(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return MODE_AUTO;
        }
        return getSwissData(stack).getInt(TAG_MODE);
    }

    // 当前生效模式：自动按目标方块计算，锁定则固定
    public static SwissKnifeMode getEffectiveMode(ItemStack stack, BlockState state)
    {
        int setting = getModeSetting(stack);
        SwissKnifeMode mode;
        if (setting == MODE_AUTO)
        {
            mode = modeFor(stack, state);
        }
        else
        {
            SwissKnifeMode[] modes = SwissKnifeMode.values();
            mode = setting >= 0 && setting < modes.length ? modes[setting] : SwissKnifeMode.NONE;
        }

        // 外观与实际委托必须使用同一个最终模式；槽位为空/工具已不可用时统一显示 NONE。
        if (mode != SwissKnifeMode.NONE && mode != SwissKnifeMode.WRENCH
                && getSlotStack(stack, mode).isEmpty())
        {
            mode = SwissKnifeMode.NONE;
        }
        setMode(stack, mode);
        return mode;
    }

    public static void setMode(ItemStack stack, SwissKnifeMode mode)
    {
        if (stack.isEmpty())
        {
            return;
        }
        stack.getOrCreateTag().putInt("CustomModelData", mode.id);
    }

    public static SwissKnifeMode modeFor(BlockState state)
    {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE))
        {
            return SwissKnifeMode.PICKAXE;
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE))
        {
            return SwissKnifeMode.AXE;
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL))
        {
            return SwissKnifeMode.SHOVEL;
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE))
        {
            return SwissKnifeMode.HOE;
        }
        return SwissKnifeMode.NONE;
    }

    /**
     * 仿照 Jade 的候选工具测试思路，直接测试瑞士刀槽内的真实工具。
     * 按工具槽位倒序测试，以便蜘蛛网等支持多个工具的方块优先使用靠后的剪刀槽。
     */
    public static SwissKnifeMode modeFor(ItemStack knife, BlockState state)
    {
        SwissKnifeMode[] priority = {
                SwissKnifeMode.SCISSORS, SwissKnifeMode.HOE,
                SwissKnifeMode.SHOVEL, SwissKnifeMode.AXE,
                SwissKnifeMode.PICKAXE, SwissKnifeMode.SWORD
        };
        for (SwissKnifeMode mode : priority)
        {
            ItemStack tool = getSlotStack(knife, mode);
            if (!tool.isEmpty() && (tool.isCorrectToolForDrops(state) || tool.getDestroySpeed(state) > 1.0F))
            {
                return mode;
            }
        }
        return modeFor(state);
    }
}
