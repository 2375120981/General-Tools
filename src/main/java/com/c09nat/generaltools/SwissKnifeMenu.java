package com.c09nat.generaltools;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;

public class SwissKnifeMenu extends AbstractContainerMenu
{
    private static final int KNIFE_SLOTS = 7;

    private static final ResourceLocation[] SLOT_BACKGROUNDS = {
            ResourceLocation.fromNamespaceAndPath(GeneralTools.MODID, "item/empty_slot_sword"),
            ResourceLocation.fromNamespaceAndPath(GeneralTools.MODID, "item/empty_slot_pickaxe"),
            ResourceLocation.fromNamespaceAndPath(GeneralTools.MODID, "item/empty_slot_axe"),
            ResourceLocation.fromNamespaceAndPath(GeneralTools.MODID, "item/empty_slot_shovel"),
            ResourceLocation.fromNamespaceAndPath(GeneralTools.MODID, "item/empty_slot_hoe"),
            ResourceLocation.fromNamespaceAndPath(GeneralTools.MODID, "item/empty_slot_scissors"),
            ResourceLocation.fromNamespaceAndPath(GeneralTools.MODID, "item/empty_slot_flint_and_steel"),
    };

    private final ItemStack knifeStack;
    private final Player owner;
    private final SimpleContainer container = new SimpleContainer(KNIFE_SLOTS);
    public final DataSlot modeData = DataSlot.standalone();

    public SwissKnifeMenu(int id, Inventory playerInventory)
    {
        this(id, playerInventory, ItemStack.EMPTY, null);
    }

    public SwissKnifeMenu(int id, Inventory playerInventory, ItemStack knife, Level level)
    {
        super(GeneralTools.SWISS_KNIFE_MENU.get(), id);
        this.owner = playerInventory.player;
        this.knifeStack = knife;
        this.modeData.set(SwissKnifeItem.getModeSetting(knife));
        this.addDataSlot(this.modeData);
        if (!knife.isEmpty())
        {
            CompoundTag root = knife.getOrCreateTag().getCompound(SwissKnifeItem.TAG_SWISS);
            for (int i = 0; i < KNIFE_SLOTS; i++)
            {
                container.setItem(i, ItemStack.of(root.getCompound("slot_" + (i + 1))));
            }
        }
        // 瑞士刀 7 个工具槽（剑/镐/斧/铲/锄/剪刀/打火石）
        for (int i = 0; i < KNIFE_SLOTS; i++)
        {
            SwissKnifeSlot slot = new SwissKnifeSlot(container, i, 7 + i * 18, 17);
            slot.setBackground(InventoryMenu.BLOCK_ATLAS, SLOT_BACKGROUNDS[i]);
            addSlot(slot);
        }
        // 玩家背包 27 格
        for (int row = 0; row < 3; row++)
        {
            for (int col = 0; col < 9; col++)
            {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 7 + col * 18, 85 + row * 18));
            }
        }
        // 玩家热栏 9 格
        for (int col = 0; col < 9; col++)
        {
            addSlot(new Slot(playerInventory, col, 7 + col * 18, 143));
        }
    }

    @Override
    public boolean stillValid(Player player)
    {
        return player == this.owner && player.isAlive() && isKnifeStillOwned();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem())
        {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();
            if (index < KNIFE_SLOTS)
            {
                if (!this.moveItemStackTo(slotStack, KNIFE_SLOTS, this.slots.size(), true))
                {
                    return ItemStack.EMPTY;
                }
            }
            else
            {
                if (!this.moveItemStackTo(slotStack, 0, KNIFE_SLOTS, false))
                {
                    return ItemStack.EMPTY;
                }
            }
            if (slotStack.isEmpty())
            {
                slot.set(ItemStack.EMPTY);
            }
            else
            {
                slot.setChanged();
            }
            if (slotStack.getCount() == itemstack.getCount())
            {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, slotStack);
        }
        return itemstack;
    }

    @Override
    public void slotsChanged(Container changedContainer)
    {
        super.slotsChanged(changedContainer);
        saveToNbt();
    }

    @Override
    public void broadcastChanges()
    {
        super.broadcastChanges();
        saveToNbt();
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        saveToNbt();
    }

    private void saveToNbt()
    {
        if (!knifeStack.isEmpty() && isKnifeStillOwned())
        {
            CompoundTag root = SwissKnifeItem.getSwissData(knifeStack);
            root.putInt(SwissKnifeItem.TAG_MODE, this.modeData.get());
            for (int i = 0; i < KNIFE_SLOTS; i++)
            {
                ItemStack s = container.getItem(i);
                if (!s.isEmpty())
                {
                    root.put("slot_" + (i + 1), s.save(new CompoundTag()));
                }
                else
                {
                    root.remove("slot_" + (i + 1));
                }
            }
            SwissKnifeItem.setSwissData(knifeStack, root);
        }
    }

    private boolean isKnifeStillOwned()
    {
        if (knifeStack.isEmpty())
        {
            return false;
        }
        if (owner.getMainHandItem() == knifeStack || owner.getOffhandItem() == knifeStack)
        {
            return true;
        }
        for (ItemStack stack : owner.getInventory().items)
        {
            if (stack == knifeStack)
            {
                return true;
            }
        }
        return false;
    }

    private static class SwissKnifeSlot extends Slot
    {
        SwissKnifeSlot(Container container, int index, int x, int y)
        {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack)
        {
            if (Config.isBlacklisted(stack))
            {
                return false;
            }
            Item item = stack.getItem();
            return switch (this.getSlotIndex())
            {
                case 0 -> item instanceof SwordItem;
                case 1 -> item instanceof PickaxeItem;
                case 2 -> item instanceof AxeItem;
                case 3 -> item instanceof ShovelItem;
                case 4 -> item instanceof HoeItem;
                case 5 -> item instanceof ShearsItem;
                case 6 -> item instanceof FlintAndSteelItem;
                default -> false;
            };
        }

        @Override
        public int getMaxStackSize()
        {
            return 1;
        }
    }
}
