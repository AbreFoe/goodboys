package com.goodboys.client;

import javax.annotation.Nullable;

import com.goodboys.entity.Dog;
import com.goodboys.entity.DogCommand;
import com.goodboys.entity.DogMode;
import com.goodboys.network.DogCommandPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Radial command wheel. Hold the key, point at a command, let go. A quick tap leaves it open for clicking instead.
 * Commands go to the dog under the crosshair, or to every owned dog nearby.
 */
public class DogCommandScreen extends Screen {
    private static final DogCommand[] COMMANDS = DogCommand.values();
    private static final int DEAD_ZONE = 18;
    private static final int SLOT = 22;
    private static final int COLOR_SLOT = 0xB0201008;
    private static final int COLOR_HOVER = 0xE07B3800;
    private static final int COLOR_ACTIVE = 0xFF6FCF4F;
    private static final int COLOR_DISABLED_TEXT = 0xFF808080;

    private final int dogId;
    @Nullable
    private final Dog dog;
    private final Component header;
    @Nullable
    private DogCommand hovered;

    public DogCommandScreen(@Nullable Dog dog, int packSize) {
        super(Component.translatable("gui.goodboys.command_menu"));
        this.dog = dog;
        this.dogId = dog != null ? dog.getId() : DogCommandPayload.WHOLE_PACK;
        this.header = dog != null ? dog.getDisplayName() : Component.translatable("gui.goodboys.pack", packSize);
    }

    private int ringRadius() {
        return Mth.clamp(Math.min(width, height) / 4, 60, 100);
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
        double angle = Math.atan2(dy, dx) + Math.PI / 2;
        int index = Math.floorMod((int) Math.round(angle / step), COMMANDS.length);
        return COMMANDS[index];
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x60000000); // keep the world visible, no blur
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        hovered = commandAt(mouseX, mouseY);
        int cx = width / 2;
        int cy = height / 2;
        int r = ringRadius();
        DogMode activeMode = dog != null ? dog.getMode() : null;

        for (int i = 0; i < COMMANDS.length; i++) {
            DogCommand command = COMMANDS[i];
            int x = cx + (int) Math.round(Math.cos(angleOf(i)) * r);
            int y = cy + (int) Math.round(Math.sin(angleOf(i)) * r);
            boolean isHovered = command == hovered;
            boolean isActive = activeMode != null && isCurrent(command, activeMode);

            int half = SLOT / 2 + (isHovered ? 2 : 0);
            if (isActive) {
                graphics.fill(x - half - 1, y - half - 1, x + half + 1, y + half + 1, COLOR_ACTIVE);
            }
            graphics.fill(x - half, y - half, x + half, y + half, isHovered && command.available() ? COLOR_HOVER : COLOR_SLOT);
            graphics.renderItem(new ItemStack(command.icon()), x - 8, y - 8);

            Component label = Component.translatable(command.translationKey());
            int color = command.available() ? 0xFFFFFFFF : COLOR_DISABLED_TEXT;
            graphics.drawCenteredString(font, label, x, y + half + 3, color);
            if (!command.available()) {
                graphics.drawCenteredString(font, Component.translatable("gui.goodboys.soon"), x, y + half + 13, COLOR_DISABLED_TEXT);
            }
        }

        graphics.drawCenteredString(font, header, cx, cy - 10, 0xFFFFD27F);
        if (hovered != null) {
            graphics.drawCenteredString(font, Component.translatable(hovered.translationKey()), cx, cy + 2,
                    hovered.available() ? 0xFFFFFFFF : COLOR_DISABLED_TEXT);
        }
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

    private void choose(@Nullable DogCommand command) {
        if (command != null && command.available()) {
            PacketDistributor.sendToServer(new DogCommandPayload(command.ordinal(), dogId));
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

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ClientEvents.COMMAND_KEY.matches(keyCode, scanCode)) {
            DogCommand command = commandAt(minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth(),
                    minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight());
            if (command != null) {
                choose(command);
            }
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
