package com.mertokan.omnilogistics.compat;

import com.mertokan.omnilogistics.OmniLogistics;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Layout: gear (+ donor below) -> arrow -> result + up to 4 extras. Note line under it names the needed module. */
public class ExtractionCategory implements IRecipeCategory<OmniJeiPlugin.ExtractionRecipe> {
    private static final int W = 160, H = 64;
    private final IDrawable icon;

    public ExtractionCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(OmniLogistics.EXTRACTOR_ITEM.get());
    }

    @Override public RecipeType<OmniJeiPlugin.ExtractionRecipe> getRecipeType() { return OmniJeiPlugin.EXTRACTION; }
    @Override public Component getTitle() { return Component.translatable("jei.omnilogistics.category"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return W; }
    @Override public int getHeight() { return H; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, OmniJeiPlugin.ExtractionRecipe r, IFocusGroup focuses) {
        b.addSlot(RecipeIngredientRole.INPUT, 4, 4).addItemStack(r.gear()).setStandardSlotBackground();
        if (!r.donor().isEmpty()) b.addSlot(RecipeIngredientRole.INPUT, 4, 26).addItemStack(r.donor()).setStandardSlotBackground();
        b.addSlot(RecipeIngredientRole.OUTPUT, 62, 4).addItemStack(r.result()).setStandardSlotBackground();
        for (int i = 0; i < r.extras().size() && i < 4; i++)
            b.addSlot(RecipeIngredientRole.OUTPUT, 88 + (i % 2) * 20, 4 + (i / 2) * 20).addItemStack(r.extras().get(i)).setStandardSlotBackground();
    }

    @Override
    public void draw(OmniJeiPlugin.ExtractionRecipe r, IRecipeSlotsView view, GuiGraphicsExtractor g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.text(font, r.fuse() ? "+  >>" : "-  >>", 30, 9, r.fuse() ? 0xFFFF9F1C : 0xFF5CFF7A, false);
        g.text(font, Component.translatable(r.fuse() ? "gui.omnilogistics.mode_fuse" : "gui.omnilogistics.mode_extract"), 28, 30, 0xFF808080, false);
        g.text(font, Component.translatable(r.noteKey()), 4, 52, 0xFF808080, false);
    }
}
