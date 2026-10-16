package com.goodboys.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.goodboys.GoodBoys;
import com.goodboys.entity.Dog;
import com.goodboys.item.DogUpgrade;
import com.goodboys.registry.ModEntities;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Once a second, tells every owner how their tracked dogs are doing (loaded dogs, any dimension). */
@EventBusSubscriber(modid = GoodBoys.MODID)
public final class TrackerEvents {
    private static final int INTERVAL = 20;
    /** Players that got a non-empty list last time, so they get one empty list to clear their HUD. */
    private static final Set<UUID> SHOWING = new HashSet<>();

    private TrackerEvents() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL != 0) {
            return;
        }
        Map<UUID, List<TrackerPayload.Entry>> byOwner = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Dog dog : level.getEntities(ModEntities.DOG.get(), d -> d.isTame() && d.getOwnerUUID() != null && d.hasUpgrade(DogUpgrade.TRACKER))) {
                byOwner.computeIfAbsent(dog.getOwnerUUID(), k -> new ArrayList<>()).add(new TrackerPayload.Entry(
                        dog.getDisplayName(), dog.getDogLevel(), dog.getHealth(), dog.getMaxHealth(),
                        dog.getX(), dog.getY(), dog.getZ(), level.dimension(), dog.isDowned()));
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<TrackerPayload.Entry> dogs = byOwner.getOrDefault(player.getUUID(), List.of());
            if (!dogs.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new TrackerPayload(dogs));
                SHOWING.add(player.getUUID());
            } else if (SHOWING.remove(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new TrackerPayload(List.of()));
            }
        }
    }
}
