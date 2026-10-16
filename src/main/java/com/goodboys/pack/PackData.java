package com.goodboys.pack;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which dogs belong to whose pack, for the whole world. Kept in the overworld's saved data so dogs in
 * unloaded chunks or other dimensions still count towards the limit.
 */
public class PackData extends SavedData {
    public static final int MAX_PACK_SIZE = 5;
    private static final String NAME = "goodboys_packs";

    private final Map<UUID, Set<UUID>> packs = new HashMap<>();

    public static PackData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(PackData::new, PackData::load), NAME);
    }

    public int size(UUID owner) {
        Set<UUID> pack = packs.get(owner);
        return pack == null ? 0 : pack.size();
    }

    public boolean contains(UUID owner, UUID dog) {
        Set<UUID> pack = packs.get(owner);
        return pack != null && pack.contains(dog);
    }

    /** Room for one more dog, unless it is already a member. */
    public boolean canJoin(UUID owner, UUID dog) {
        return contains(owner, dog) || size(owner) < MAX_PACK_SIZE;
    }

    public void add(UUID owner, UUID dog) {
        if (packs.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(dog)) {
            setDirty();
        }
    }

    public void remove(UUID owner, UUID dog) {
        Set<UUID> pack = packs.get(owner);
        if (pack != null && pack.remove(dog)) {
            if (pack.isEmpty()) {
                packs.remove(owner);
            }
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        packs.forEach((owner, dogs) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            ListTag members = new ListTag();
            dogs.forEach(dog -> members.add(NbtUtils.createUUID(dog)));
            entry.put("Dogs", members);
            list.add(entry);
        });
        tag.put("Packs", list);
        return tag;
    }

    private static PackData load(CompoundTag tag, HolderLookup.Provider registries) {
        PackData data = new PackData();
        for (Tag t : tag.getList("Packs", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            Set<UUID> dogs = new LinkedHashSet<>();
            for (Tag member : entry.getList("Dogs", Tag.TAG_INT_ARRAY)) {
                dogs.add(NbtUtils.loadUUID(member));
            }
            if (!dogs.isEmpty()) {
                data.packs.put(entry.getUUID("Owner"), dogs);
            }
        }
        return data;
    }
}
