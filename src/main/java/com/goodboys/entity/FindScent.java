package com.goodboys.entity;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * What the FIND command looks for, from the item the owner holds: a block item means that block, raw ore, ingots,
 * gems and dusts mean their ore ({@code c:ores/<name>}, so modded ores count too), anything else only items lying about.
 */
public final class FindScent {
    /** Item tags whose last part names an ore: {@code c:ingots/iron} means {@code c:ores/iron}. */
    private static final String[] MATERIAL_TAGS = {"raw_materials/", "ingots/", "gems/", "dusts/", "nuggets/"};

    private FindScent() {
    }

    /** Which blocks the scent leads to, or null if it only matches dropped items. */
    @Nullable
    public static Predicate<BlockState> blocks(ItemStack scent) {
        if (scent.isEmpty()) {
            return null;
        }
        Set<TagKey<Block>> ores = new LinkedHashSet<>();
        Block exact = scent.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
        if (exact != null) {
            // iron ore also finds deepslate iron ore
            exact.builtInRegistryHolder().tags()
                    .filter(t -> t.location().getNamespace().equals("c") && t.location().getPath().startsWith("ores/"))
                    .forEach(ores::add);
        } else {
            Set<String> names = new LinkedHashSet<>();
            scent.getTags().forEach(tag -> {
                ResourceLocation id = tag.location();
                for (String prefix : MATERIAL_TAGS) {
                    if (id.getNamespace().equals("c") && id.getPath().startsWith(prefix)) {
                        names.add(id.getPath().substring(prefix.length()));
                    }
                }
            });
            String path = BuiltInRegistries.ITEM.getKey(scent.getItem()).getPath();
            names.add(path.replaceFirst("^raw_", "").replaceFirst("_(ingot|nugget|dust)$", "").replace("_lazuli", ""));
            if (scent.is(Items.CHARCOAL)) {
                names.add("coal");
            }
            for (String name : names) {
                TagKey<Block> ore = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores/" + name.toLowerCase(Locale.ROOT)));
                if (BuiltInRegistries.BLOCK.getTag(ore).map(set -> set.size() > 0).orElse(false)) {
                    ores.add(ore);
                }
            }
            if (ores.isEmpty()) {
                return null;
            }
        }
        return state -> exact != null && state.is(exact) || ores.stream().anyMatch(state::is);
    }

    /**
     * The nearest matching block in a box round {@code center}. Skips chunk sections that can't contain it,
     * so a big box stays cheap.
     */
    @Nullable
    public static BlockPos nearest(Level level, BlockPos center, Predicate<BlockState> wanted, int radius, int down, int up) {
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - down);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + up);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(wanted)) {
                        continue;
                    }
                    int x0 = Math.max(cx << 4, center.getX() - radius), x1 = Math.min((cx << 4) + 15, center.getX() + radius);
                    int z0 = Math.max(cz << 4, center.getZ() - radius), z1 = Math.min((cz << 4) + 15, center.getZ() + radius);
                    int y0 = Math.max(sy << 4, minY), y1 = Math.min((sy << 4) + 15, maxY);
                    for (int x = x0; x <= x1; x++) {
                        for (int y = y0; y <= y1; y++) {
                            for (int z = z0; z <= z1; z++) {
                                if (!wanted.test(section.getBlockState(x & 15, y & 15, z & 15))) {
                                    continue;
                                }
                                double dist = pos.set(x, y, z).distSqr(center);
                                if (dist < bestDist) {
                                    bestDist = dist;
                                    best = pos.immutable();
                                }
                            }
                        }
                    }
                }
            }
        }
        return best;
    }
}
