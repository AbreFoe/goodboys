package com.goodboys.client;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.goodboys.entity.Dog;
import com.goodboys.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientEvents {
    public static final KeyMapping COMMAND_KEY = new KeyMapping("key.goodboys.command_menu", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.goodboys");

    private ClientEvents() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener(ClientEvents::registerKeys);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(CommandMenu::onScroll);
        NeoForge.EVENT_BUS.addListener(CommandMenu::onMouseButton);
        NeoForge.EVENT_BUS.addListener(CreateWorldOptions::onScreenInit);
        NeoForge.EVENT_BUS.addListener(CreateWorldOptions::onScreenRender);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(COMMAND_KEY);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (COMMAND_KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                openMenu(mc, mc.player);
            }
        }
        CommandMenu.tick(mc);
    }

    private static void openMenu(Minecraft mc, LocalPlayer player) {
        if (CommandMenu.isOpen()) {
            CommandMenu.close();
            return;
        }
        Dog looked = lookedAtDog(player);
        if (looked != null) {
            CommandMenu.toggle(mc, looked, 1);
            return;
        }
        int pack = player.level().getEntitiesOfClass(Dog.class, player.getBoundingBox().inflate(ModNetwork.COMMAND_RADIUS),
                dog -> dog.isOwnedBy(player)).size();
        if (pack == 0) {
            player.displayClientMessage(Component.translatable("message.goodboys.no_dogs"), true);
            return;
        }
        CommandMenu.toggle(mc, null, pack);
    }

    /** One of the player's dogs under the crosshair, further than the normal reach. */
    @Nullable
    private static Dog lookedAtDog(LocalPlayer player) {
        double range = ModNetwork.COMMAND_RADIUS;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxDist = block.getType() == HitResult.Type.MISS ? range * range : block.getLocation().distanceToSqr(eye);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end,
                player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0),
                e -> e instanceof Dog dog && dog.isOwnedBy(player), maxDist);
        return hit != null ? (Dog) hit.getEntity() : null;
    }
}
