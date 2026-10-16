package com.goodboys.client;

import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.goodboys.registry.ModGameRules;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Adds the dog survival mode switch to the "Game" tab of the Create World screen.
 * It just edits the {@code goodboysPermadeath} game rule of the world being created.
 */
final class CreateWorldOptions {
    private static final int WIDTH = 210;
    /** Our button per open screen, with the Game tab widget it hangs under. */
    private static final Map<CreateWorldScreen, Placed> PLACED = new WeakHashMap<>();

    private record Placed(CycleButton<Boolean> button, AbstractWidget anchor) {
    }

    private CreateWorldOptions() {
    }

    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen)) {
            return;
        }
        // The Game tab is open when the screen appears; sit under its lowest full-width control.
        AbstractWidget anchor = lowestGameTabWidget(event);
        if (anchor == null) {
            return; // layout changed: the game rule is still available under More > Game Rules
        }
        WorldCreationUiState state = screen.getUiState();
        boolean current = state.getGameRules().getBoolean(ModGameRules.PERMADEATH);
        CycleButton<Boolean> button = CycleButton.booleanBuilder(
                        Component.translatable("gui.goodboys.dog_mode.heaven"),
                        Component.translatable("gui.goodboys.dog_mode.happy"))
                .withInitialValue(current)
                .withTooltip(permadeath -> Tooltip.create(Component.translatable(
                        permadeath ? "gui.goodboys.dog_mode.heaven.info" : "gui.goodboys.dog_mode.happy.info")))
                .create(screen.width / 2 - WIDTH / 2, anchor.getY() + anchor.getHeight() + 4, WIDTH, 20,
                        Component.translatable("gui.goodboys.dog_mode"), (b, permadeath) -> {
                            GameRules rules = state.getGameRules().copy();
                            rules.getRule(ModGameRules.PERMADEATH).set(permadeath, null);
                            state.setGameRules(rules);
                        });
        event.addListener(button);
        // only show it on the Game tab: the anchor leaves the screen when another tab is opened
        PLACED.put(screen, new Placed(button, anchor));
    }

    static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof CreateWorldScreen screen) {
            Placed placed = PLACED.get(screen);
            if (placed != null) {
                boolean onGameTab = screen.children().contains(placed.anchor());
                placed.button().visible = onGameTab;
                placed.button().active = onGameTab;
            }
        }
    }

    @Nullable
    private static AbstractWidget lowestGameTabWidget(ScreenEvent.Init.Post event) {
        AbstractWidget lowest = null;
        int centre = event.getScreen().width / 2;
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof AbstractWidget widget && widget.getWidth() >= WIDTH - 10
                    && Math.abs(widget.getX() + widget.getWidth() / 2 - centre) <= 2
                    && widget.getY() < event.getScreen().height - 40
                    && (lowest == null || widget.getY() > lowest.getY())) {
                lowest = widget;
            }
        }
        return lowest;
    }
}
