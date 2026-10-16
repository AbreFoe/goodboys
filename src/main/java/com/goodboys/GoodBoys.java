package com.goodboys;

import org.slf4j.Logger;

import com.goodboys.network.ModNetwork;
import com.goodboys.registry.ModEntities;
import com.goodboys.registry.ModGameRules;
import com.goodboys.registry.ModItems;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(GoodBoys.MODID)
public class GoodBoys {
    public static final String MODID = "goodboys";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GoodBoys(IEventBus modEventBus, ModContainer modContainer) {
        ModGameRules.init();
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModNetwork.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
