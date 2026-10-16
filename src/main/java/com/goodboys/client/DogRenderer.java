package com.goodboys.client;

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
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Uses geo/entity/dog.geo.json, animations/entity/dog.animation.json and textures/entity/dog.png.
 * Gear lives in the same model as hidden bones: {@code armor_*} (tinted per armour tier), {@code bag_*}, {@code acc_*}.
 */
public class DogRenderer extends GeoEntityRenderer<Dog> {
    private static final String[] ARMOR_BONES = {"armor_chest", "armor_belly"};
    private static final String[] BAG_BONES = {"bag_left", "bag_right"};

    public DogRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(GoodBoys.id("dog"), "head"));
        shadowRadius = 0.5F;
    }

    @Override
    public void preRender(PoseStack poseStack, Dog dog, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, dog, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        ItemStack armor = dog.getArmor();
        boolean hasArmor = armor.getItem() instanceof DogArmorItem;
        boolean hasBag = hasArmor && DogArmorItem.hasUpgrade(armor, DogUpgrade.BAG);
        for (String bone : ARMOR_BONES) {
            setHidden(model, bone, !hasArmor);
        }
        for (String bone : BAG_BONES) {
            setHidden(model, bone, !hasBag);
        }
        Accessory worn = dog.getHeadAccessory().getItem() instanceof DogAccessoryItem item ? item.accessory() : null;
        for (Accessory accessory : Accessory.values()) {
            setHidden(model, accessory.bone(), accessory != worn);
        }
    }

    private static void setHidden(BakedGeoModel model, String bone, boolean hidden) {
        model.getBone(bone).ifPresent(b -> b.setHidden(hidden));
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
