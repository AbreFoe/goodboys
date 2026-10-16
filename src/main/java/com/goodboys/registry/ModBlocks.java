package com.goodboys.registry;

import com.goodboys.GoodBoys;
import com.goodboys.block.DogWorkbenchBlock;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GoodBoys.MODID);

    public static final DeferredBlock<DogWorkbenchBlock> DOG_WORKBENCH = BLOCKS.register("dog_workbench",
            () -> new DogWorkbenchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
