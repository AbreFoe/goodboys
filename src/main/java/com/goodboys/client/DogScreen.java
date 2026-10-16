package com.goodboys.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.goodboys.entity.Dog;
import com.goodboys.entity.DogLevels;
import com.goodboys.entity.DogMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Dog inventory, drawn on the vanilla horse inventory background. */
public class DogScreen extends AbstractContainerScreen<DogMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/horse.png");
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private static final ResourceLocation ARMOR_SLOT = ResourceLocation.withDefaultNamespace("container/horse/armor_slot");

    public DogScreen(DogMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.blit(BACKGROUND, x, y, 0, 0, imageWidth, imageHeight);
        graphics.blitSprite(SLOT, x + 7, y + 17, 18, 18);       // head
        graphics.blitSprite(ARMOR_SLOT, x + 7, y + 35, 18, 18); // armour
        for (int i = DogMenu.UPGRADE_START; i < DogMenu.BAG_START + DogMenu.BAG_SIZE; i++) {
            Slot slot = menu.slots.get(i);
            boolean upgrade = i < DogMenu.BAG_START;
            if (slot.isActive()) {
                graphics.blitSprite(SLOT, x + slot.x - 1, y + slot.y - 1, 18, 18);
            } else if (upgrade) {
                // dim placeholders so you can see the armour has slots to fill
                graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0x40000000);
            }
        }
        Dog dog = menu.getDog();
        if (dog != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + 26, y + 18, x + 78, y + 70, 17, 0.25F, mouseX, mouseY, dog);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Dog dog = menu.getDog();
        Component name = dog != null
                ? title.copy().append(Component.literal(" · ")).append(Component.translatable("gui.goodboys.level", dog.getDogLevel()))
                : title;
        graphics.drawString(font, name, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int lx = mouseX - leftPos;
        int ly = mouseY - topPos;
        Dog dog = menu.getDog();
        if (dog != null && lx >= 26 && lx < 78 && ly >= 18 && ly < 70) {
            graphics.renderComponentTooltip(font, stats(dog), mouseX, mouseY);
        } else if (hoveredSlot != null && !hoveredSlot.hasItem()) {
            Component hint = emptySlotHint(menu.slots.indexOf(hoveredSlot));
            if (hint != null) {
                graphics.renderTooltip(font, hint, mouseX, mouseY);
            }
        } else if (menu.slots.size() > DogMenu.UPGRADE_START && !menu.hasArmor()) {
            // hovering where the upgrade slots would be, without armour on
            for (int i = DogMenu.UPGRADE_START; i < DogMenu.BAG_START; i++) {
                Slot slot = menu.slots.get(i);
                if (lx >= slot.x && lx < slot.x + 16 && ly >= slot.y && ly < slot.y + 16) {
                    graphics.renderTooltip(font, Component.translatable("gui.goodboys.slot.upgrade_locked"), mouseX, mouseY);
                }
            }
        }
    }

    private static Component emptySlotHint(int index) {
        if (index == DogMenu.HEAD) {
            return Component.translatable("gui.goodboys.slot.head");
        }
        if (index == DogMenu.ARMOR) {
            return Component.translatable("gui.goodboys.slot.armor");
        }
        if (index >= DogMenu.UPGRADE_START && index < DogMenu.BAG_START) {
            return Component.translatable("gui.goodboys.slot.upgrade");
        }
        return null;
    }

    private static List<Component> stats(Dog dog) {
        List<Component> lines = new ArrayList<>();
        lines.add(dog.getDisplayName().copy().withStyle(ChatFormatting.GOLD));
        int level = dog.getDogLevel();
        lines.add(level >= DogLevels.MAX_LEVEL
                ? Component.translatable("gui.goodboys.level_max", level)
                : Component.translatable("gui.goodboys.stats.level", level, dog.getXp(), DogLevels.xpToNext(level)));
        lines.add(Component.translatable("gui.goodboys.stats.health", Mth.ceil(dog.getHealth()), Mth.ceil(dog.getMaxHealth())).withStyle(ChatFormatting.RED));
        lines.add(Component.translatable("gui.goodboys.stats.armor", Mth.floor(dog.getAttributeValue(Attributes.ARMOR))).withStyle(ChatFormatting.BLUE));
        lines.add(Component.translatable("gui.goodboys.stats.attack", String.format("%.1f", dog.getAttributeValue(Attributes.ATTACK_DAMAGE))).withStyle(ChatFormatting.DARK_RED));
        lines.add(Component.translatable("gui.goodboys.stats.mode", Component.translatable("gui.goodboys.mode." + dog.getMode().name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GRAY));
        if (level >= DogLevels.HEART_LEVEL) {
            lines.add(Component.translatable("gui.goodboys.stats.heart").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return lines;
    }
}
