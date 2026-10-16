package com.goodboys.network;

import java.util.List;

import javax.annotation.Nullable;

import com.goodboys.entity.Dog;
import com.goodboys.entity.DogCommand;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ModNetwork {
    /** Dogs further away than this don't hear the command. */
    public static final double COMMAND_RADIUS = 32.0;
    /** How far the owner can point with GO_TO / ATTACK. */
    private static final double POINT_RANGE = 64.0;

    private ModNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(DogCommandPayload.TYPE, DogCommandPayload.STREAM_CODEC, ModNetwork::handleCommand);
    }

    private static void handleCommand(DogCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DogCommand command = DogCommand.byId(payload.command());
                if (command != null && command.available()) {
                    execute(player, command, payload.dogId());
                }
            }
        });
    }

    public static void execute(ServerPlayer player, DogCommand command, int dogId) {
        List<Dog> dogs = listeners(player, dogId);
        if (dogs.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.goodboys.no_dogs"), true);
            return;
        }
        BlockPos pos = null;
        LivingEntity target = null;
        if (command == DogCommand.GO_TO || command == DogCommand.ATTACK) {
            HitResult hit = pointedAt(player);
            if (command == DogCommand.GO_TO) {
                if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                    pos = blockHit.getBlockPos().relative(blockHit.getDirection());
                } else if (hit instanceof EntityHitResult entityHit) {
                    pos = entityHit.getEntity().blockPosition();
                } else {
                    player.displayClientMessage(Component.translatable("message.goodboys.no_spot"), true);
                    return;
                }
            } else if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
                target = living;
            } else {
                player.displayClientMessage(Component.translatable("message.goodboys.no_target"), true);
                return;
            }
        }
        for (Dog dog : dogs) {
            dog.command(command, pos, target);
        }
        Component who = dogs.size() == 1 ? dogs.get(0).getDisplayName() : Component.translatable("gui.goodboys.pack", dogs.size());
        player.displayClientMessage(Component.translatable("message.goodboys.commanded", who, Component.translatable(command.translationKey())), true);
    }

    /** The one dog the owner looked at, or all of the owner's dogs in earshot. */
    private static List<Dog> listeners(ServerPlayer player, int dogId) {
        if (dogId != DogCommandPayload.WHOLE_PACK) {
            Entity entity = player.level().getEntity(dogId);
            if (entity instanceof Dog dog && dog.isOwnedBy(player) && dog.distanceToSqr(player) <= COMMAND_RADIUS * COMMAND_RADIUS) {
                return List.of(dog);
            }
            return List.of();
        }
        return player.level().getEntitiesOfClass(Dog.class, player.getBoundingBox().inflate(COMMAND_RADIUS),
                dog -> dog.isOwnedBy(player) && dog.isAlive());
    }

    /** Server-side look ray: the nearest living thing or block the player is pointing at. */
    @Nullable
    private static HitResult pointedAt(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(POINT_RANGE));
        BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double maxDist = block.getType() == HitResult.Type.MISS ? POINT_RANGE * POINT_RANGE : block.getLocation().distanceToSqr(eye);
        AABB sweep = player.getBoundingBox().expandTowards(look.scale(POINT_RANGE)).inflate(1.0);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
                e -> e instanceof LivingEntity && e.isPickable() && !e.isSpectator() && !(e instanceof Dog dog && dog.isOwnedBy(player)),
                maxDist);
        if (entity != null) {
            return entity;
        }
        return block.getType() == HitResult.Type.MISS ? null : block;
    }
}
