package com.goodboys.registry;

import java.util.EnumMap;
import java.util.Map;

import com.goodboys.GoodBoys;
import com.goodboys.item.DogAccessoryItem;
import com.goodboys.item.DogAccessoryItem.Accessory;
import com.goodboys.item.DogArmorItem;
import com.goodboys.item.DogUpgrade;
import com.goodboys.item.DogUpgradeItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GoodBoys.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GoodBoys.MODID);

    // Egg colours are the dog's base brown and saddle black.
    public static final DeferredItem<DeferredSpawnEggItem> DOG_SPAWN_EGG = ITEMS.register("dog_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.DOG, 0x7B3800, 0x271000, new Item.Properties()));

    public static final DeferredItem<BlockItem> DOG_WORKBENCH = ITEMS.registerSimpleBlockItem(ModBlocks.DOG_WORKBENCH);

    public static final DeferredItem<DogArmorItem> LEATHER_DOG_ARMOR = armor("leather_dog_armor", DogArmorItem.Tier.LEATHER, Rarity.COMMON);
    public static final DeferredItem<DogArmorItem> IRON_DOG_ARMOR = armor("iron_dog_armor", DogArmorItem.Tier.IRON, Rarity.COMMON);
    public static final DeferredItem<DogArmorItem> DIAMOND_DOG_ARMOR = armor("diamond_dog_armor", DogArmorItem.Tier.DIAMOND, Rarity.UNCOMMON);

    public static final Map<DogUpgrade, DeferredItem<DogUpgradeItem>> UPGRADES = new EnumMap<>(DogUpgrade.class);
    public static final Map<Accessory, DeferredItem<DogAccessoryItem>> ACCESSORIES = new EnumMap<>(Accessory.class);

    static {
        for (DogUpgrade upgrade : DogUpgrade.values()) {
            UPGRADES.put(upgrade, ITEMS.register(upgrade.id(), () -> new DogUpgradeItem(upgrade, new Item.Properties().stacksTo(16))));
        }
        for (Accessory accessory : Accessory.values()) {
            ACCESSORIES.put(accessory, ITEMS.register(accessory.id(), () -> new DogAccessoryItem(accessory, new Item.Properties())));
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("goodboys", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.goodboys"))
            .icon(() -> IRON_DOG_ARMOR.get().getDefaultInstance())
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .displayItems((params, output) -> {
                output.accept(DOG_WORKBENCH);
                output.accept(LEATHER_DOG_ARMOR);
                output.accept(IRON_DOG_ARMOR);
                output.accept(DIAMOND_DOG_ARMOR);
                UPGRADES.values().forEach(output::accept);
                ACCESSORIES.values().forEach(output::accept);
                output.accept(DOG_SPAWN_EGG);
            })
            .build());

    private static DeferredItem<DogArmorItem> armor(String name, DogArmorItem.Tier tier, Rarity rarity) {
        return ITEMS.register(name, () -> new DogArmorItem(tier, new Item.Properties().rarity(rarity)));
    }

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(ModItems::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(DOG_SPAWN_EGG);
        }
    }
}
