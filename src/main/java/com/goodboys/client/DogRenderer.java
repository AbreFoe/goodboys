package com.goodboys.client;

import com.goodboys.GoodBoys;
import com.goodboys.entity.Dog;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Uses geo/entity/dog.geo.json, animations/entity/dog.animation.json and textures/entity/dog.png. */
public class DogRenderer extends GeoEntityRenderer<Dog> {
    public DogRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(GoodBoys.id("dog"), "head"));
        shadowRadius = 0.5F;
    }
}
