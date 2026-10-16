package com.goodboys.crafting;

import java.util.Optional;

import com.goodboys.registry.ModBlocks;
import com.goodboys.registry.ModMenus;
import com.goodboys.registry.ModRecipes;

import net.minecraft.core.NonNullList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/** Crafting-table style menu, but for {@link DogWorkbenchRecipe}s. Layout matches the vanilla crafting table texture. */
public class DogWorkbenchMenu extends AbstractContainerMenu {
    private static final int RESULT = 0;
    private static final int GRID_START = 1;
    private static final int GRID_END = 10;
    private static final int INV_START = 10;
    private static final int INV_END = 46;

    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;

    public DogWorkbenchMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public DogWorkbenchMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.DOG_WORKBENCH.get(), id);
        this.access = access;
        this.player = inventory.player;
        addSlot(new ResultSlot(124, 35));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(craftSlots, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    private Optional<RecipeHolder<DogWorkbenchRecipe>> currentRecipe(Level level) {
        return level.getRecipeManager().getRecipeFor(ModRecipes.DOG_WORKBENCH.get(), craftSlots.asCraftInput(), level);
    }

    @Override
    public void slotsChanged(Container container) {
        access.execute((level, pos) -> {
            if (!level.isClientSide) {
                ItemStack result = currentRecipe(level)
                        .map(holder -> holder.value().assemble(craftSlots.asCraftInput(), level.registryAccess()))
                        .orElse(ItemStack.EMPTY);
                resultSlots.setItem(0, result);
                broadcastChanges();
            }
        });
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, craftSlots));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.DOG_WORKBENCH.get());
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == RESULT) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
        } else if (index >= INV_START) {
            if (!moveItemStackTo(stack, GRID_START, GRID_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, INV_START, INV_END, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }

    /** Taking the result uses up one of each ingredient and leaves container items (buckets, bottles) behind. */
    private class ResultSlot extends Slot {
        ResultSlot(int x, int y) {
            super(resultSlots, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player taker, ItemStack stack) {
            stack.onCraftedBy(taker.level(), taker, stack.getCount());
            access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.VILLAGER_WORK_LEATHERWORKER, SoundSource.BLOCKS, 1.0F, 1.0F));
            CraftingInput.Positioned positioned = craftSlots.asPositionedCraftInput();
            CraftingInput input = positioned.input();
            NonNullList<ItemStack> remaining = currentRecipe(taker.level())
                    .map(holder -> holder.value().getRemainingItems(input))
                    .orElseGet(() -> NonNullList.withSize(input.size(), ItemStack.EMPTY));
            for (int y = 0; y < input.height(); y++) {
                for (int x = 0; x < input.width(); x++) {
                    int slot = x + positioned.left() + (y + positioned.top()) * craftSlots.getWidth();
                    craftSlots.removeItem(slot, 1);
                    ItemStack leftover = remaining.get(x + y * input.width());
                    if (!leftover.isEmpty()) {
                        if (craftSlots.getItem(slot).isEmpty()) {
                            craftSlots.setItem(slot, leftover);
                        } else if (!taker.getInventory().add(leftover)) {
                            taker.drop(leftover, false);
                        }
                    }
                }
            }
            slotsChanged(craftSlots);
        }
    }
}
