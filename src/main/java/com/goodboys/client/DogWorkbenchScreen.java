package com.goodboys.client;

import java.util.List;

import javax.annotation.Nullable;

import com.goodboys.crafting.DogWorkbenchMenu;
import com.goodboys.crafting.DogWorkbenchRecipe;
import com.goodboys.registry.ModRecipes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Crafting-table look plus a recipe list on the left. Picking a recipe shows its ingredients as
 * faded "ghost" items in the grid, so nobody has to guess the patterns.
 */
public class DogWorkbenchScreen extends AbstractContainerScreen<DogWorkbenchMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int COLUMNS = 4;
    private static final int CELL = 20;
    private static final int PANEL_W = COLUMNS * CELL + 8;

    private List<RecipeHolder<DogWorkbenchRecipe>> recipes = List.of();
    @Nullable
    private DogWorkbenchRecipe selected;

    public DogWorkbenchScreen(DogWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft != null && minecraft.level != null) {
            recipes = minecraft.level.getRecipeManager().getAllRecipesFor(ModRecipes.DOG_WORKBENCH.get());
        }
        titleLabelX = 29;
    }

    private int panelX() {
        return leftPos - PANEL_W - 4;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int rows = Math.max(1, Mth.positiveCeilDiv(recipes.size(), COLUMNS));
        int px = panelX();
        int py = topPos;
        graphics.fill(px, py, px + PANEL_W, py + 14 + rows * CELL + 4, 0xE0302010);
        graphics.drawString(font, Component.translatable("gui.goodboys.recipes"), px + 4, py + 4, 0xFFFFD27F, false);
        for (int i = 0; i < recipes.size(); i++) {
            int cx = px + 4 + (i % COLUMNS) * CELL;
            int cy = py + 14 + (i / COLUMNS) * CELL;
            DogWorkbenchRecipe recipe = recipes.get(i).value();
            boolean hovered = mouseX >= cx && mouseX < cx + 18 && mouseY >= cy && mouseY < cy + 18;
            graphics.fill(cx, cy, cx + 18, cy + 18, recipe == selected ? 0xFF7B3800 : hovered ? 0xFF5A3A20 : 0xFF1E140A);
            graphics.renderItem(recipe.result(), cx + 1, cy + 1);
        }
        renderGhost(graphics);
    }

    /** Faded ingredients of the selected recipe in empty grid slots. */
    private void renderGhost(GuiGraphics graphics) {
        if (selected == null) {
            return;
        }
        int w = selected.pattern().width();
        List<Ingredient> ingredients = selected.pattern().ingredients();
        long tick = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack[] options = ingredients.get(i).getItems();
            if (options.length == 0) {
                continue;
            }
            int col = i % w;
            int row = i / w;
            if (menu.slots.get(1 + col + row * 3).hasItem()) {
                continue;
            }
            int sx = leftPos + 30 + col * 18;
            int sy = topPos + 17 + row * 18;
            graphics.renderFakeItem(options[(int) (tick / 20 % options.length)], sx, sy);
            graphics.fill(sx, sy, sx + 16, sy + 16, 0x90C6C6C6); // fade it out
        }
        int rx = leftPos + 124;
        int ry = topPos + 35;
        if (!menu.slots.get(0).hasItem()) {
            graphics.renderFakeItem(selected.result(), rx, ry);
            graphics.fill(rx, ry, rx + 16, ry + 16, 0x90C6C6C6);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int index = recipeAt(mouseX, mouseY);
        if (index >= 0) {
            graphics.renderTooltip(font, recipes.get(index).value().result(), mouseX, mouseY);
        }
    }

    private int recipeAt(double mouseX, double mouseY) {
        int px = panelX() + 4;
        int py = topPos + 14;
        if (mouseX < px || mouseY < py) {
            return -1;
        }
        int col = (int) ((mouseX - px) / CELL);
        int row = (int) ((mouseY - py) / CELL);
        int index = row * COLUMNS + col;
        return col < COLUMNS && index < recipes.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = recipeAt(mouseX, mouseY);
        if (index >= 0) {
            DogWorkbenchRecipe recipe = recipes.get(index).value();
            selected = selected == recipe ? null : recipe;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        return recipeAt(mouseX, mouseY) < 0 && super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }
}
