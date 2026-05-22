package dev.attuned.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code AbstractRecipeBookScreen}'s private recipe-book component so the
 * survival Focus panel can tell when the book is open and step aside for it.
 */
@Mixin(AbstractRecipeBookScreen.class)
public interface AbstractRecipeBookScreenAccessor {
	@Accessor("recipeBookComponent")
	RecipeBookComponent<?> attuned$recipeBookComponent();
}
