package com.goodboys.client;

import java.util.List;

import com.goodboys.network.TrackerPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Top-left list of tracked dogs: name, level, health, and distance with a direction arrow. */
public final class TrackerHud {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static volatile List<TrackerPayload.Entry> dogs = List.of();

    private TrackerHud() {
    }

    public static void update(TrackerPayload payload) {
        dogs = payload.dogs();
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || dogs.isEmpty() || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        int y = 4;
        for (TrackerPayload.Entry dog : dogs) {
            Component where;
            if (!dog.dimension().equals(player.level().dimension())) {
                where = Component.translatable("gui.goodboys.tracker.elsewhere");
            } else {
                double dx = dog.x() - player.getX();
                double dz = dog.z() - player.getZ();
                int distance = Mth.floor(Math.sqrt(dx * dx + dz * dz + Math.pow(dog.y() - player.getY(), 2)));
                // angle of the dog relative to where the player looks, 0 = straight ahead
                double angle = Math.toDegrees(Math.atan2(-dx, dz)) - player.getYRot();
                int arrow = Math.floorMod((int) Math.round(angle / 45.0), 8);
                where = Component.literal(distance + " " + Component.translatable("gui.goodboys.tracker.blocks").getString() + " " + ARROWS[arrow]);
            }
            Component line = Component.empty()
                    .append(dog.name())
                    .append(Component.literal(" · " + Component.translatable("gui.goodboys.level", dog.level()).getString() + " · "))
                    .append(dog.downed()
                            ? Component.translatable("gui.goodboys.tracker.downed").withColor(0xFF5555)
                            : Component.literal("❤ " + Mth.ceil(dog.health()) + "/" + Mth.ceil(dog.maxHealth())).withColor(0xFF7777))
                    .append(Component.literal(" · "))
                    .append(where);
            int width = mc.font.width(line);
            graphics.fill(2, y - 2, 6 + width, y + 10, 0x80000000);
            graphics.drawString(mc.font, line, 4, y, 0xFFFFFFFF);
            y += 13;
        }
    }
}
