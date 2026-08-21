package com.example.examplemod;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

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

@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
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

    private static final ImmutableMultimap<Attribute, AttributeModifier> KNIFE_ATTRIBUTES = ImmutableMultimap.<Attribute, AttributeModifier>builder()
            .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_UUID, "Swiss knife damage", 0.0, AttributeModifier.Operation.ADDITION))
            .put(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_UUID, "Swiss knife speed", -2.4, AttributeModifier.Operation.ADDITION))
            .build();

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

    // ---------- 对方块右键：扳手（旋转/拆除）或临时伪装成槽位工具执行其 useOn（完整继承功能/附魔/充能） ----------
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

        boolean sneaking = player.isShiftKeyDown();
        SwissKnifeMode mode = getEffectiveMode(knife, state);

        // 锁定扳手模式：右键全部由扳手接管（普通=旋转，潜行=拆除），禁用其他工具
        if (mode == SwissKnifeMode.WRENCH)
        {
            if (sneaking)
            {
                // 拆除不生效时返回 FAIL，避免原版回退到对空气右键（打开 GUI）
                InteractionResult dismantle = wrenchDismantle(context);
                return dismantle.consumesAction() ? dismantle : InteractionResult.FAIL;
            }
            return wrenchRotate(context);
        }

        // 非锁定模式：潜行右键优先尝试拆除，拆除不生效则托管当前工具
        if (sneaking)
        {
            InteractionResult dismantle = wrenchDismantle(context);
            if (dismantle.consumesAction())
            {
                return dismantle;
            }
        }

        ItemStack tool = activeTool(knife, state);
        if (tool.isEmpty())
        {
            return player.isCrouching() ? InteractionResult.FAIL : InteractionResult.PASS;
        }

        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        UseOnContext toolContext = new UseOnContext(context.getLevel(), player, context.getHand(), tool, hit);

        InteractionResult result = withToolInHand(player, tool, () -> tool.getItem().useOn(toolContext), knife, mode);

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
            setMode(knife, SwissKnifeMode.NONE);
            applyToolEnchantments(knife, SwissKnifeMode.NONE);
            return ItemStack.EMPTY;
        }
        setMode(knife, mode);
        applyToolEnchantments(knife, mode);
        return tool;
    }

    // ---------- 扳手：普通右键旋转可旋转方块（复刻 GT 扳手通用逻辑） ----------
    private static InteractionResult wrenchRotate(UseOnContext context)
    {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown())
        {
            return InteractionResult.PASS;
        }
        BlockState state = level.getBlockState(pos);
        // 轨道不旋转（留给撬棍）
        if (state.getBlock() instanceof BaseRailBlock)
        {
            return InteractionResult.FAIL;
        }
        // 围绕点击面法线旋转（Create 扳手风格）：朝向绕点击面轴向顺时针旋转 90°
        Direction side = context.getClickedFace();
        BlockState rotated = null;
        if (state.hasProperty(BlockStateProperties.FACING))
        {
            Direction f = state.getValue(BlockStateProperties.FACING);
            Direction nf = f.getClockWise(side.getAxis());
            if (nf != f)
            {
                rotated = state.setValue(BlockStateProperties.FACING, nf);
            }
        }
        else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
        {
            Direction f = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            Direction nf = f.getClockWise(side.getAxis());
            if (nf != f)
            {
                rotated = state.setValue(BlockStateProperties.HORIZONTAL_FACING, nf);
            }
        }
        else if (state.hasProperty(BlockStateProperties.AXIS))
        {
            Direction.Axis a = state.getValue(BlockStateProperties.AXIS);
            Direction.Axis na = side.getAxis();
            if (na != a)
            {
                rotated = state.setValue(BlockStateProperties.AXIS, na);
            }
        }
        if (rotated != null && rotated != state)
        {
            level.setBlock(pos, rotated, 3);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // ---------- 扳手：潜行右键拆除（等效普通破坏掉落，掉落物吸入物品栏，背包满掉落；掉落物堆数超阈值拦截） ----------
    private static InteractionResult wrenchDismantle(UseOnContext context)
    {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null || !(level instanceof ServerLevel serverLevel))
        {
            return InteractionResult.PASS;
        }
        BlockState state = level.getBlockState(pos);
        if (!isDismantleable(level, pos, state))
        {
            return InteractionResult.PASS;
        }
        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos,
                level.getBlockEntity(pos), player, player.getMainHandItem());
        // 掉落物保护：统计掉落堆数（含容器内容物非空槽数），超阈值拦截
        int dropCount = drops.size() + countContainerContents(level.getBlockEntity(pos));
        if (Config.dismantleDropThreshold > 0 && dropCount >= Config.dismantleDropThreshold)
        {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
            player.sendSystemMessage(Component.translatable("message.generaltools.swiss_knife.dismantle_blocked"));
            return InteractionResult.FAIL;
        }
        level.removeBlock(pos, false);
        for (ItemStack drop : drops)
        {
            if (!player.getInventory().add(drop))
            {
                Block.popResource(level, pos, drop);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // 容器内容物非空槽数（ITEM_HANDLER capability）
    private static int countContainerContents(BlockEntity be)
    {
        if (be == null)
        {
            return 0;
        }
        LazyOptional<IItemHandler> opt = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        if (!opt.isPresent())
        {
            return 0;
        }
        IItemHandler handler = opt.orElse(null);
        if (handler == null)
        {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++)
        {
            if (!handler.getStackInSlot(i).isEmpty())
            {
                count++;
            }
        }
        return count;
    }

    // 可拆除判定：带方块实体（视为机器）或红石元件
    private static boolean isDismantleable(Level level, BlockPos pos, BlockState state)
    {
        Block block = state.getBlock();
        if (level.getBlockEntity(pos) != null)
        {
            return true;
        }
        // 红石元件：通用信号源（红石块/火把/中继器/比较器/红石线/按钮/压力板/拉杆等）+ 轨道 + 接受端（红石灯/活塞）
        return state.isSignalSource()
                || state.is(BlockTags.RAILS)
                || block instanceof RedstoneLampBlock
                || block instanceof PistonBaseBlock;
    }

    // 临时把玩家主手替换为槽位工具执行 action，恢复主手并写回工具状态（耐久/充能正常消耗）
    private static InteractionResult withToolInHand(Player player, ItemStack tool, Supplier<InteractionResult> action, ItemStack knife, SwissKnifeMode mode)
    {
        int selected = player.getInventory().selected;
        ItemStack original = player.getInventory().getItem(selected);
        player.getInventory().setItem(selected, tool);
        try
        {
            return action.get();
        }
        finally
        {
            player.getInventory().setItem(selected, original);
            setSlotStack(knife, mode, tool);
        }
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
        if (!(event.getTarget() instanceof Sheep sheep) || sheep.isSheared())
        {
            return;
        }
        ItemStack scissors = getSlotStack(knife, SwissKnifeMode.SCISSORS);
        if (scissors.isEmpty())
        {
            return;
        }
        event.setCanceled(true);
        withToolInHand(player, scissors, () -> {
            sheep.shear(SoundSource.PLAYERS);
            scissors.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(event.getHand()));
            return InteractionResult.SUCCESS;
        }, knife, SwissKnifeMode.SCISSORS);
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
            ItemStack tool = getSlotStack(knife, mode);
            if (tool.isEmpty() || !tool.isDamaged())
            {
                continue;
            }
            if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, tool) <= 0)
            {
                continue;
            }
            int repair = Math.min((int) (amount * tool.getXpRepairRatio()), tool.getDamageValue());
            if (repair <= 0)
            {
                continue;
            }
            tool.setDamageValue(tool.getDamageValue() - repair);
            setSlotStack(knife, mode, tool);
            amount -= repair / 2;
            if (amount <= 0)
            {
                break;
            }
        }
        event.setAmount(Math.max(0, amount));
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
        ItemStack tool = activeTool(stack, state);
        return tool.isEmpty() ? super.mineBlock(stack, level, state, pos, miner)
                : tool.getItem().mineBlock(tool, level, state, pos, miner);
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

    // ---------- 攻击：无剑为空手伤害，有剑时继承剑伤害（锋利/火焰附加等附魔由合并到瑞士刀的附魔自动生效） ----------
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker)
    {
        setMode(stack, SwissKnifeMode.SWORD);
        if (!(attacker instanceof Player player))
        {
            return true;
        }
        ItemStack sword = getSlotStack(stack, SwissKnifeMode.SWORD);
        if (sword.isEmpty())
        {
            return true;
        }
        float extra = 0.0F;
        if (sword.getItem() instanceof SwordItem swordItem)
        {
            extra = swordItem.getDamage();
        }
        if (extra > 0.0F)
        {
            target.hurt(player.damageSources().playerAttack(player), extra);
        }
        // 扣剑槽工具耐久并写回（与挖掘等工具消耗一致；创造模式不扣；剩余 1 由保护机制禁用）
        if (!player.getAbilities().instabuild)
        {
            sword.hurtAndBreak(1, attacker, (p) -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
            setSlotStack(stack, SwissKnifeMode.SWORD, sword);
        }
        return true;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot)
    {
        if (slot == EquipmentSlot.MAINHAND)
        {
            return KNIFE_ATTRIBUTES;
        }
        return super.getDefaultAttributeModifiers(slot);
    }

    // 动态属性：装了剑后，工具提示与实际属性显示剑的伤害（与实际攻击保持一致）；攻击时合并剑附魔
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack)
    {
        if (slot == EquipmentSlot.MAINHAND)
        {
            applyToolEnchantments(stack, SwissKnifeMode.SWORD);
            ItemStack sword = getSlotStack(stack, SwissKnifeMode.SWORD);
            if (!sword.isEmpty() && sword.getItem() instanceof SwordItem swordItem)
            {
                return ImmutableMultimap.<Attribute, AttributeModifier>builder()
                        .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_UUID, "Swiss knife damage", swordItem.getDamage(), AttributeModifier.Operation.ADDITION))
                        .put(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_UUID, "Swiss knife speed", -2.4, AttributeModifier.Operation.ADDITION))
                        .build();
            }
        }
        return super.getAttributeModifiers(slot, stack);
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
        if (knife.isEmpty() || mode == SwissKnifeMode.NONE)
        {
            return ItemStack.EMPTY;
        }
        ItemStack tool = ItemStack.of(getSwissData(knife).getCompound("slot_" + mode.ordinal()));
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
        if (setting == MODE_AUTO)
        {
            return modeFor(state);
        }
        SwissKnifeMode[] modes = SwissKnifeMode.values();
        if (setting >= 0 && setting < modes.length)
        {
            return modes[setting];
        }
        return SwissKnifeMode.NONE;
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
}
