package com.goodboys.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.goodboys.GoodBoys;
import com.goodboys.entity.Dog;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Dog model with its own head-look. GeckoLib's built-in one replaces the head bone's rotation outright, which
 * throws away the animated head pose: a sitting dog (body tilted nose-up) ended up staring at the sky, and a
 * sleeping one lifted its head to watch the player. Here the look is added on top of the animation, and the yaw
 * turns around the true vertical instead of the tilted spine.
 */
public class DogGeoModel extends DefaultedEntityGeoModel<Dog> {
    public DogGeoModel() {
        super(GoodBoys.id("dog")); // no built-in head turning
    }

    @Override
    public ResourceLocation getTextureResource(Dog dog) {
        return dog.getVariant().texture(); // coat colour
    }

    @Override
    public void setCustomAnimations(Dog dog, long instanceId, AnimationState<Dog> state) {
        super.setCustomAnimations(dog, instanceId, state);
        if (dog.isNapping() || dog.isDowned()) {
            return; // asleep or out cold: the head stays where the animation puts it
        }
        GeoBone head = getAnimationProcessor().getBone("head");
        GeoBone body = getAnimationProcessor().getBone("body");
        GeoBone chest = getAnimationProcessor().getBone("chest");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head == null || body == null || chest == null || data == null) {
            return;
        }
        float yaw = Mth.clamp(data.netHeadYaw(), -70, 70) * Mth.DEG_TO_RAD;
        float pitch = Mth.clamp(data.headPitch(), -40, 40) * Mth.DEG_TO_RAD;
        float parentPitch = body.getRotX() + chest.getRotX();

        // head = undo the spine tilt, turn about vertical, re-apply the tilt, then the animated pose, then look up/down
        Quaternionf q = new Quaternionf()
                .rotateX(-parentPitch).rotateY(yaw).rotateX(parentPitch)
                .rotateZ(head.getRotZ()).rotateY(head.getRotY()).rotateX(head.getRotX())
                .rotateX(pitch);
        Vector3f euler = q.getEulerAnglesZYX(new Vector3f());
        head.setRotX(euler.x);
        head.setRotY(euler.y);
        head.setRotZ(euler.z);
    }
}
