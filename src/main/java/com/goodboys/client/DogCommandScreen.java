package com.goodboys.client;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.goodboys.GoodBoys;
import com.goodboys.entity.Dog;
import com.goodboys.entity.DogCommand;
import com.goodboys.entity.DogLevels;
import com.goodboys.entity.DogMode;
import com.goodboys.network.DogCommandPayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Radial command wheel. Hold the key, point at a command, let go; a quick tap leaves it open for clicking,
 * and 1-8 pick a command directly. The player can keep walking, jumping and sprinting while it is open.
 * Commands go to the dog under the crosshair, or to every owned dog nearby.
 */
public class DogCommandScreen extends Screen {
    private static final ResourceLocation RING = GoodBoys.id("textures/gui/command_wheel.png");
    private static final ResourceLocation WEDGE = GoodBoys.id("textures/gui/command_wheel_hover.png");
    private static final DogCommand[] COMMANDS = DogCommand.values();
    private static final int DEAD_ZONE = 22;
    private static final float OPEN_TICKS = 4.0F;

    private final int dogId;
    @Nullable
    private final Dog dog;
    private final Component header;
    @Nullable
    private DogCommand hovered;
    private float age;
    private float hoverAnim;

    public DogCommandScreen(@Nullable Dog dog, int packSize) {
        super(Component.translatable("gui.goodboys.command_menu"));
        this.dog = dog;
        this.dogId = dog != null ? dog.getId() : DogCommandPayload.WHOLE_PACK;
        this.header = dog != null ? dog.getDisplayName() : Component.translatable("gui.goodboys.pack", packSize);
    }

    // ------------------------------------------------------------------ keep the player moving

    private KeyMapping[] movementKeys() {
        var o = minecraft.options;
        return new KeyMapping[] {o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keySprint, o.keyShift};
    }

    @Override
    protected void init() {
        super.init();
        // opening a screen releases every key; press the movement keys that are still physically held
        long window = minecraft.getWindow().getWindow();
        for (KeyMapping key : movementKeys()) {
            InputConstants.Key bound = key.getKey();
            if (bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() != InputConstants.UNKNOWN.getValue()) {
                key.setDown(InputConstants.isKeyDown(window, bound.getValue()));
            }
        }
    }

    private boolean passMovement(int keyCode, int scanCode, boolean down) {
        for (KeyMapping key : movementKeys()) {
            if (key.matches(keyCode, scanCode)) {
                key.setDown(down);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + COMMANDS.length) {
            choose(COMMANDS[keyCode - GLFW.GLFW_KEY_1]);
            return true;
        }
        if (passMovement(keyCode, scanCode, true)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (passMovement(keyCode, scanCode, false)) {
            return true;
        }
        if (ClientEvents.COMMAND_KEY.matches(keyCode, scanCode)) {
            if (hovered != null) {
                choose(hovered);
            }
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        // don't leave the player running when the wheel closes on its own
        for (KeyMapping key : movementKeys()) {
            InputConstants.Key bound = key.getKey();
            if (bound.getType() == InputConstants.Type.KEYSYM) {
                key.setDown(InputConstants.isKeyDown(minecraft.getWindow().getWindow(), bound.getValue()));
            }
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ layout

    private int radius() {
        return Mth.clamp(Math.min(width, height) / 4, 62, 100);
    }

    private double angleOf(int index) {
        return -Math.PI / 2 + index * (Math.PI * 2 / COMMANDS.length);
    }

    @Nullable
    private DogCommand commandAt(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
            return null;
        }
        double step = Math.PI * 2 / COMMANDS.length;
        int index = Math.floorMod((int) Math.round((Math.atan2(dy, dx) + Math.PI / 2) / step), COMMANDS.length);
        return COMMANDS[index];
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void tick() {
        age++;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float open = Mth.clamp((age + partialTick) / OPEN_TICKS, 0, 1);
        graphics.fill(0, 0, width, height, ((int) (0x50 * open)) << 24); // keep the world visible, no blur
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        DogCommand now = commandAt(mouseX, mouseY);
        if (now != hovered) {
            hovered = now;
            hoverAnim = 0;
            if (now != null && now.available()) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.15F));
            }
        }
        hoverAnim = Math.min(1, hoverAnim + partialTick * 0.25F);

        float open = easeOutBack(Mth.clamp((age + partialTick) / OPEN_TICKS, 0, 1));
        int cx = width / 2;
        int cy = height / 2;
        int r = radius();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(open, open, 1);

        int ringSize = (int) (r * 2.45F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(RING, -ringSize / 2, -ringSize / 2, ringSize, ringSize, 0, 0, 256, 256, 256, 256);
        if (hovered != null && hovered.available()) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(hovered.ordinal() * 360.0F / COMMANDS.length));
            graphics.blit(WEDGE, -ringSize / 2, -ringSize / 2, ringSize, ringSize, 0, 0, 256, 256, 256, 256);
            pose.popPose();
        }
        RenderSystem.disableBlend();

        DogMode mode = dog != null ? dog.getMode() : null;
        for (int i = 0; i < COMMANDS.length; i++) {
            DogCommand command = COMMANDS[i];
            int x = (int) Math.round(Math.cos(angleOf(i)) * r);
            int y = (int) Math.round(Math.sin(angleOf(i)) * r);
            boolean isHovered = command == hovered;
            float s = isHovered && command.available() ? 1.0F + 0.45F * easeOutBack(hoverAnim) : 1.0F;
            if (mode != null && isCurrent(command, mode)) {
                graphics.fill(x - 11, y - 11, x + 11, y + 11, 0x506FCF4F);
                graphics.renderOutline(x - 11, y - 11, 22, 22, 0xFF6FCF4F);
            }
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.scale(s, s, 1);
            graphics.renderItem(new ItemStack(command.icon()), -8, -8);
            pose.popPose();
            int color = command.available() ? (isHovered ? 0xFFFFE08A : 0xFFFFFFFF) : 0xFF808080;
            graphics.drawCenteredString(font, Component.translatable(command.translationKey()), x, y + 13, color);
            graphics.drawString(font, String.valueOf(i + 1), x + 9, y - 13, 0x80FFFFFF, false);
        }

        // centre: who listens, which command, what it does
        graphics.drawCenteredString(font, header, 0, -22, 0xFFFFD27F);
        if (dog != null) {
            int level = dog.getDogLevel();
            graphics.drawCenteredString(font, Component.translatable(level >= DogLevels.MAX_LEVEL ? "gui.goodboys.level_max" : "gui.goodboys.level", level), 0, -11, 0xFFDDDDDD);
        }
        if (hovered != null) {
            graphics.drawCenteredString(font, Component.translatable(hovered.translationKey()), 0, 2, hovered.available() ? 0xFFFFFFFF : 0xFF808080);
            Component info = hovered.available() ? Component.translatable(hovered.translationKey() + ".info") : Component.translatable("gui.goodboys.soon");
            pose.pushPose();
            pose.scale(0.75F, 0.75F, 1);
            graphics.drawCenteredString(font, info, 0, 20, 0xFFB0B0B0);
            pose.popPose();
        } else {
            pose.pushPose();
            pose.scale(0.75F, 0.75F, 1);
            graphics.drawCenteredString(font, Component.translatable("gui.goodboys.wheel_hint"), 0, 4, 0xFF9A9A9A);
            pose.popPose();
        }
        pose.popPose();
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    private static boolean isCurrent(DogCommand command, DogMode mode) {
        return switch (command) {
            case FOLLOW -> mode == DogMode.FOLLOW;
            case STAY -> mode == DogMode.STAY;
            case GUARD -> mode == DogMode.GUARD;
            case HUNT -> mode == DogMode.HUNT;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ choosing

    private void choose(@Nullable DogCommand command) {
        if (command != null && command.available()) {
            PacketDistributor.sendToServer(new DogCommandPayload(command.ordinal(), dogId));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.1F, 0.35F));
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            choose(commandAt(mouseX, mouseY));
            return true;
        }
        if (button == 1) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
