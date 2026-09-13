package com.mertokan.omnilogistics.compat;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.miner.MinerTier;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** One page per miner tier: the tier block on the left, every loot entry in a 6x3 grid, its share of the table in the slot tooltip. */
public class MinerCategory extends AbstractRecipeCategory<OmniJeiPlugin.MinerDrops> {
    private static final int COLS = 6, GX = 22, GY = 14;   // 130 x 68: 6 columns x 3 rows fits the biggest tier (13 entries)

    public MinerCategory(IGuiHelper gui) {
        super(OmniJeiPlugin.MINER, Component.translatable("jei.omnilogistics.miner_category"),
            gui.createDrawableItemLike(OmniLogistics.MINER_ITEMS.get(MinerTier.ULTIMATE).get()), 130, 68);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, OmniJeiPlugin.MinerDrops r, IFocusGroup focuses) {
        b.addSlot(RecipeIngredientRole.CATALYST, 1, GY)
            .addItemStack(new ItemStack(OmniLogistics.MINER_ITEMS.get(r.tier()).get()))
            .setStandardSlotBackground()
            .addRichTooltipCallback((v, t) -> t.add(Component.translatable("jei.omnilogistics.miner_default").withStyle(ChatFormatting.DARK_GRAY)));
        for (int i = 0; i < r.items().size(); i++) {
            String pct = r.chance().get(i);                 // effectively final for the lambda
            b.addSlot(RecipeIngredientRole.OUTPUT, GX + (i % COLS) * 18, GY + (i / COLS) * 18)
                .addItemStack(r.items().get(i))
                .setOutputSlotBackground()
                .addRichTooltipCallback((v, t) -> t.add(Component.translatable("jei.omnilogistics.miner_chance", pct).withStyle(ChatFormatting.GRAY)));
        }
    }

    @Override
    public void draw(OmniJeiPlugin.MinerDrops r, IRecipeSlotsView view, GuiGraphics g, double mouseX, double mouseY) {
        g.drawString(Minecraft.getInstance().font, OmniLogistics.MINERS.get(r.tier()).get().getName(), 1, 2, r.tier().color, false);
    }
}
