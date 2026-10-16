package com.goodboys.entity;

import javax.annotation.Nullable;

import com.goodboys.item.DogAccessoryItem;
import com.goodboys.item.DogArmorItem;
import com.goodboys.item.DogUpgrade;
import com.goodboys.item.DogUpgradeItem;
import com.goodboys.registry.ModMenus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The dog's own screen: armour and head slot, the armour's three upgrade slots, and the saddlebag.
 * Layout follows the vanilla horse inventory texture.
 */
public class DogMenu extends AbstractContainerMenu {
    public static final int HEAD = 0;
    public static final int ARMOR = 1;
    public static final int UPGRADE_START = 2;
    public static final int BAG_START = UPGRADE_START + DogArmorItem.UPGRADE_SLOTS;
    public static final int BAG_SIZE = 9;
    private static final int DOG_END = BAG_START + BAG_SIZE;
    private static final int INV_END = DOG_END + 36;

    @Nullable
    private final Dog dog;
    private final Container gear;
    private final Container upgrades;
    private final Container bag;

    /** Client side: the containers are mirrors filled in by menu sync. */
    public DogMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(id, inventory, inventory.player.level().getEntity(extra.readVarInt()) instanceof Dog d ? d : null,
                new SimpleContainer(2), new SimpleContainer(DogArmorItem.UPGRADE_SLOTS), new SimpleContainer(BAG_SIZE));
    }

    /** Server side. */
    public DogMenu(int id, Inventory inventory, Dog dog) {
        this(id, inventory, dog, dog.getGear(), dog.upgradeContainer(), dog.getBag());
    }

    private DogMenu(int id, Inventory inventory, @Nullable Dog dog, Container gear, Container upgrades, Container bag) {
        super(ModMenus.DOG.get(), id);
        this.dog = dog;
        this.gear = gear;
        this.upgrades = upgrades;
        this.bag = bag;

        addSlot(new Slot(gear, Dog.GEAR_HEAD, 8, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DogAccessoryItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(gear, Dog.GEAR_ARMOR, 8, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DogArmorItem;
            }

            @Override
            public boolean mayPickup(Player player) {
                return !(bagInUse() && DogArmorItem.hasUpgrade(getItem(), DogUpgrade.BAG)); // empty the bag first
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int i = 0; i < DogArmorItem.UPGRADE_SLOTS; i++) {
            addSlot(new Slot(upgrades, i, 80 + i * 18, 18) {
                @Override
                public boolean isActive() {
                    return hasArmor();
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return hasArmor() && stack.getItem() instanceof DogUpgradeItem item
                            && !DogArmorItem.hasUpgrade(armor(), item.upgrade()); // one of each
                }

                @Override
                public boolean mayPickup(Player player) {
                    return !(getItem().getItem() instanceof DogUpgradeItem item && item.upgrade() == DogUpgrade.BAG && bagInUse());
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int i = 0; i < BAG_SIZE; i++) {
            int col = i < 5 ? i : i - 5;
            int row = i < 5 ? 0 : 1;
            addSlot(new Slot(bag, i, 80 + col * 18, 36 + row * 18) {
                @Override
                public boolean isActive() {
                    return hasBag();
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return hasBag();
                }
            });
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

    @Nullable
    public Dog getDog() {
        return dog;
    }

    private ItemStack armor() {
        return gear.getItem(Dog.GEAR_ARMOR);
    }

    public boolean hasArmor() {
        return armor().getItem() instanceof DogArmorItem;
    }

    public boolean hasBag() {
        return DogArmorItem.hasUpgrade(armor(), DogUpgrade.BAG);
    }

    private boolean bagInUse() {
        return !bag.isEmpty();
    }

    @Override
    public boolean stillValid(Player player) {
        return dog != null && dog.isAlive() && dog.isOwnedBy(player) && dog.distanceToSqr(player) < 8 * 8;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < DOG_END) {
            if (!moveItemStackTo(stack, DOG_END, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // from the player: armour, hat and upgrades to their slots, anything else into the bag
            boolean moved = false;
            if (stack.getItem() instanceof DogAccessoryItem) {
                moved = moveItemStackTo(stack, HEAD, HEAD + 1, false);
            } else if (stack.getItem() instanceof DogArmorItem) {
                moved = moveItemStackTo(stack, ARMOR, ARMOR + 1, false);
            } else if (stack.getItem() instanceof DogUpgradeItem) {
                for (int i = UPGRADE_START; i < BAG_START && !moved && !stack.isEmpty(); i++) {
                    if (slots.get(i).mayPlace(stack) && !slots.get(i).hasItem()) {
                        moved = moveItemStackTo(stack, i, i + 1, false);
                    }
                }
            }
            if (!moved && hasBag()) {
                moved = moveItemStackTo(stack, BAG_START, DOG_END, false);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
