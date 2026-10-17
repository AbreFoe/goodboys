package com.goodboys.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A place a dog likes to sleep on (pet bed) or in (kennel). Napping there heals faster. */
public interface DogRestBlock {
    /** Where the dog's feet go. */
    Vec3 restPosition(BlockState state, BlockPos pos);

    /** Which way the dog faces when lying there (degrees), or NaN for "any". */
    float restYaw(BlockState state);

    /** Sleeping pose: index into the dog's nap styles. */
    int napStyle();
}
