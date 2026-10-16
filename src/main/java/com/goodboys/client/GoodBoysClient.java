package com.goodboys.client;

import com.goodboys.GoodBoys;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

// Client-only entry point: renderers, keybinds and screens live under this package.
@Mod(value = GoodBoys.MODID, dist = Dist.CLIENT)
public class GoodBoysClient {
    public GoodBoysClient(ModContainer container) {
    }
}
