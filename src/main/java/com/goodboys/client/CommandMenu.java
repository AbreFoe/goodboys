package com.goodboys.client;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.goodboys.entity.Dog;
import com.goodboys.entity.DogCommand;
import com.goodboys.entity.DogLevels;
import com.goodboys.entity.DogMode;
import com.goodboys.network.DogCommandPayload;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The command menu: a scrolling list drawn over the game, not a screen, so walking and looking round carry on as
 * normal. G opens it, the mouse wheel picks a command, left click gives it, right click (or G again) closes it.
 * Orders go to the dog that was under the crosshair when the menu opened, or else to the whole pack nearby.
 */
public final class CommandMenu {
    private static final DogCommand[] COMMANDS = DogCommand.values();
    private static final int ROW = 20;
    /** Closes by itself after this long without scrolling. */
    private static final float IDLE_CLOSE_TICKS = 20 * 10;

    private static boolean open;
    /** Where the selection is going (not wrapped, so the list can spin smoothly past the end). */
    private static int selected;
    /** Where the list is drawn right now; eases towards {@link #selected} every frame. */
    private static float scroll;
    /** 0..1 fade/slide in and out. */
    private static float shown;
    private static float idle;
    private static int dogId = DogCommandPayload.WHOLE_PACK;
    @Nullable
    private static Dog dog;
    private static Component header = Component.empty();

    private CommandMenu() {
    }

    public static boolean isOpen() {
        return open;
    }

    static void toggle(Minecraft mc, @Nullable Dog looked, int packSize) {
        if (open) {
            close();
            return;
        }
        open = true;
        idle = 0;
        dog = looked;
        dogId = looked != null ? looked.getId() : DogCommandPayload.WHOLE_PACK;
        header = looked != null ? looked.getDisplayName() : Component.translatable("gui.goodboys.pack", packSize);
        // start on the order the dog is already following, if it's one dog
        if (looked != null) {
            int current = indexOf(looked.isOrderedToSit() ? DogCommand.SIT : commandOf(looked.getMode()));
            selected = Math.floorDiv(selected, COMMANDS.length) * COMMANDS.length + current;
            scroll = selected;
        }
        click(1.5F, 0.25F);
    }

    static void close() {
        open = false;
        dog = null;
    }

    // ------------------------------------------------------------------ input (only while open)

    static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!open || Minecraft.getInstance().screen != null || event.getScrollDeltaY() == 0) {
            return;
        }
        event.setCanceled(true); // don't flip through the hotbar as well
        selected += event.getScrollDeltaY() > 0 ? -1 : 1;
        idle = 0;
        click(1.8F, 0.12F);
    }

    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!open || Minecraft.getInstance().screen != null || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            event.setCanceled(true); // no punching or mining with the click that gives the order
            DogCommand command = COMMANDS[Math.floorMod(selected, COMMANDS.length)];
            PacketDistributor.sendToServer(new DogCommandPayload(command.ordinal(), dogId));
            click(1.1F, 0.35F);
            close();
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            event.setCanceled(true);
            close();
        }
    }

    static void tick(Minecraft mc) {
        if (!open) {
            return;
        }
        if (mc.player == null || mc.screen != null || dog != null && (!dog.isAlive() || dog.isRemoved()) || ++idle > IDLE_CLOSE_TICKS) {
            close();
        }
    }

    private static void click(float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
    }

    // ------------------------------------------------------------------ drawing

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        float dt = delta.getRealtimeDeltaTicks(); // per frame, so the animation is as smooth as the frame rate
        shown = Mth.clamp(shown + (open ? 0.22F : -0.3F) * dt, 0, 1);
        scroll += (selected - scroll) * (1 - (float) Math.exp(-0.55F * dt));
        if (Math.abs(selected - scroll) < 0.001F) {
            scroll = selected;
        }
        if (shown <= 0 || mc.player == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        float ease = 1 - (1 - shown) * (1 - shown);
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        int x = cx + 22 + Math.round((1 - ease) * 14);
        int alpha = Math.round(ease * 255);
        int panelW = 128;

        var pose = graphics.pose();
        // backdrop: a soft column, darker in the middle row
        graphics.fill(x - 4, cy - ROW * 2 - 22, x + panelW, cy + ROW * 2 + 24, argb(ease * 0.45F, 0x000000));
        graphics.fill(x - 4, cy - ROW / 2, x + panelW, cy + ROW / 2, argb(ease * 0.5F, 0x3A2A10));
        graphics.fill(x - 4, cy - ROW / 2, x - 2, cy + ROW / 2, argb(ease, 0xFFC860));

        // who listens
        Component title = header;
        if (dog != null) {
            int level = dog.getDogLevel();
            title = header.copy().append(Component.literal(" · ")).append(Component.translatable(
                    level >= DogLevels.MAX_LEVEL ? "gui.goodboys.level_max" : "gui.goodboys.level", level));
        }
        graphics.drawString(font, title, x + 2, cy - ROW * 2 - 18, withAlpha(0xFFD27F, alpha), true);

        // the drum: rows above and below the selection shrink and fade
        graphics.enableScissor(x - 4, cy - ROW * 2 - 8, x + panelW, cy + ROW * 2 + 8);
        int base = Mth.floor(scroll);
        float frac = scroll - base;
        DogMode mode = dog != null ? dog.getMode() : null;
        for (int k = -3; k <= 3; k++) {
            float offset = k - frac;
            float dist = Math.abs(offset);
            if (dist > 2.6F) {
                continue;
            }
            DogCommand command = COMMANDS[Math.floorMod(base + k, COMMANDS.length)];
            float rowFade = ease * Mth.clamp(1 - dist * 0.36F, 0, 1);
            float scale = 1 - Math.min(dist, 2) * 0.13F;
            int y = cy + Math.round(offset * ROW);
            boolean centre = dist < 0.5F;

            pose.pushPose();
            pose.translate(x + 4, y, 0);
            pose.scale(scale, scale, 1);
            RenderSystem.setShaderColor(1, 1, 1, rowFade);
            RenderSystem.enableBlend();
            graphics.renderItem(new ItemStack(command.icon()), 0, -8);
            graphics.flush();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            int textAlpha = Math.max(8, Math.round(rowFade * 255));
            graphics.drawString(font, Component.translatable(command.translationKey()), 21, -4,
                    withAlpha(centre ? 0xFFE08A : 0xFFFFFF, textAlpha), true);
            if (mode != null && isCurrent(command, mode, dog)) {
                graphics.fill(-5, -2, -2, 1, withAlpha(0x6FCF4F, textAlpha)); // what it's doing now
            }
            pose.popPose();
        }
        graphics.disableScissor();

        // what the selected order does
        DogCommand current = COMMANDS[Math.floorMod(selected, COMMANDS.length)];
        Component info = Component.translatable(current.translationKey() + ".info");
        if (current == DogCommand.FIND) {
            ItemStack held = mc.player.getMainHandItem().isEmpty() ? mc.player.getOffhandItem() : mc.player.getMainHandItem();
            info = held.isEmpty() ? Component.translatable("gui.goodboys.command.find.dropped")
                    : Component.translatable("gui.goodboys.command.find.scent", held.getHoverName());
        }
        pose.pushPose();
        pose.translate(x + 2, cy + ROW * 2 + 6, 0);
        pose.scale(0.75F, 0.75F, 1);
        graphics.drawString(font, info, 0, 0, withAlpha(0xC8C8C8, alpha), true);
        graphics.drawString(font, Component.translatable("gui.goodboys.menu_hint"), 0, 11, withAlpha(0x8A8A8A, alpha), true);
        pose.popPose();
    }

    private static int argb(float a, int rgb) {
        return Math.round(Mth.clamp(a, 0, 1) * 255) << 24 | rgb;
    }

    private static int withAlpha(int rgb, int alpha) {
        return Math.max(alpha, 8) << 24 | rgb & 0xFFFFFF;
    }

    private static int indexOf(DogCommand command) {
        return command.ordinal();
    }

    private static DogCommand commandOf(DogMode mode) {
        return switch (mode) {
            case FOLLOW -> DogCommand.FOLLOW;
            case STAY -> DogCommand.STAY;
            case GUARD -> DogCommand.GUARD;
            case HUNT -> DogCommand.HUNT;
        };
    }

    private static boolean isCurrent(DogCommand command, DogMode mode, Dog dog) {
        return dog.isOrderedToSit() ? command == DogCommand.SIT : command == commandOf(mode);
    }
}
