package com.goodboys.client;

import java.util.Locale;

import com.goodboys.entity.Dog;
import com.goodboys.entity.DogLevels;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Looking at one of your dogs shows a small card under the crosshair: name, level, health bar and its order. */
final class DogLookHud {
    private static float fade;

    private DogLookHud() {
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) {
            return;
        }
        Dog dog = mc.crosshairPickEntity instanceof Dog d && d.isOwnedBy(mc.player) ? d : null;
        fade = Mth.clamp(fade + (dog != null ? 0.15F : -0.1F) * delta.getRealtimeDeltaTicks() * 2, 0, 1);
        if (dog == null || fade <= 0.02F) {
            return;
        }
        int alpha = (int) (fade * 255) << 24;
        int cx = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 14;
        int level = dog.getDogLevel();
        Component line1 = dog.getDisplayName().copy().append(Component.literal(" · ")).append(Component.translatable(
                level >= DogLevels.MAX_LEVEL ? "gui.goodboys.level_max" : "gui.goodboys.level", level));
        Component order = dog.isDowned() ? Component.translatable("gui.goodboys.tracker.downed")
                : dog.isOrderedToSit() ? Component.translatable("gui.goodboys.command.sit")
                : Component.translatable("gui.goodboys.mode." + dog.getMode().name().toLowerCase(Locale.ROOT));
        int w = Math.max(mc.font.width(line1), mc.font.width(order)) + 10;
        graphics.fill(cx - w / 2, y - 3, cx + w / 2, y + 27, ((int) (fade * 0x90)) << 24);
        graphics.drawCenteredString(mc.font, line1, cx, y, 0xFFFFD27F & 0x00FFFFFF | alpha);
        // health bar
        int bw = w - 10;
        float hp = dog.getHealth() / dog.getMaxHealth();
        graphics.fill(cx - bw / 2, y + 11, cx + bw / 2, y + 14, alpha | 0x401010);
        graphics.fill(cx - bw / 2, y + 11, cx - bw / 2 + Math.round(bw * hp), y + 14, alpha | (hp > 0.5F ? 0x55DD55 : hp > 0.25F ? 0xE8C040 : 0xE04040));
        graphics.drawCenteredString(mc.font, order, cx, y + 16, 0xFFCCCCCC & 0x00FFFFFF | alpha);
    }
}
