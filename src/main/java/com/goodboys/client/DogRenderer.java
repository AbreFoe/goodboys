package com.goodboys.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.goodboys.GoodBoys;
import com.goodboys.entity.Dog;
import com.goodboys.item.DogAccessoryItem;
import com.goodboys.item.DogAccessoryItem.Accessory;
import com.goodboys.item.DogArmorItem;
import com.goodboys.item.DogUpgrade;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Uses geo/entity/dog.geo.json, animations/entity/dog.animation.json and textures/entity/dog.png.
 * Gear lives in the same model as hidden bones: {@code armor_*} (tinted per armour tier), {@code up_<upgrade>*}, {@code acc_<accessory>}.
 */
public class DogRenderer extends GeoEntityRenderer<Dog> {
    /** Gear bones per model, found once: armor_* (any armour), up_<upgrade>* (that upgrade), acc_<accessory>. */
    private final Map<BakedGeoModel, List<GeoBone>> gearBones = new WeakHashMap<>();

    public DogRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<Dog>(GoodBoys.id("dog"), "head") {
            @Override
            public ResourceLocation getTextureResource(Dog dog) {
                return dog.getVariant().texture(); // coat colour
            }
        });
        shadowRadius = 0.5F;
    }

    @Override
    public void preRender(PoseStack poseStack, Dog dog, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, dog, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        ItemStack armor = dog.getArmor();
        boolean hasArmor = armor.getItem() instanceof DogArmorItem;
        Accessory worn = dog.getHeadAccessory().getItem() instanceof DogAccessoryItem item ? item.accessory() : null;
        for (GeoBone bone : gearBones.computeIfAbsent(model, DogRenderer::findGearBones)) {
            String name = bone.getName();
            boolean visible;
            if (name.startsWith("armor_")) {
                visible = hasArmor;
            } else if (name.startsWith("up_")) {
                visible = hasArmor && upgradeOf(name) != null && DogArmorItem.hasUpgrade(armor, upgradeOf(name));
            } else {
                visible = worn != null && name.equals(worn.bone());
            }
            bone.setHidden(!visible);
        }
    }

    private static List<GeoBone> findGearBones(BakedGeoModel model) {
        List<GeoBone> found = new ArrayList<>();
        Deque<GeoBone> todo = new ArrayDeque<>(model.topLevelBones());
        while (!todo.isEmpty()) {
            GeoBone bone = todo.pop();
            String name = bone.getName();
            if (name.startsWith("armor_") || name.startsWith("up_") || name.startsWith("acc_")) {
                found.add(bone);
            }
            todo.addAll(bone.getChildBones());
        }
        return found;
    }

    /** "up_fire_cloth" -> FIRE_CLOTH, "up_running_shoes_fl" -> RUNNING_SHOES (longest matching id wins). */
    private static DogUpgrade upgradeOf(String bone) {
        DogUpgrade best = null;
        for (DogUpgrade upgrade : DogUpgrade.values()) {
            String prefix = "up_" + upgrade.id();
            if (bone.startsWith(prefix) && (best == null || prefix.length() > ("up_" + best.id()).length())) {
                best = upgrade;
            }
        }
        return best;
    }

    @Override
    public void renderRecursively(PoseStack poseStack, Dog dog, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (bone.getName().startsWith("armor_") && dog.getArmor().getItem() instanceof DogArmorItem item) {
            colour = FastColor.ARGB32.multiply(colour, item.tier().tint());
        }
        super.renderRecursively(poseStack, dog, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }
}
