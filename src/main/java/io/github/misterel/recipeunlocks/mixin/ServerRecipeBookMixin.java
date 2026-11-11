package io.github.misterel.recipeunlocks.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.misterel.recipeunlocks.unlock.UnlockManager;
import java.util.Collection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every way the game gives a player a recipe ends here: advancement rewards, "you crafted it",
 * knowledge books. Recipes that have a rule in the config are held back: only the rule opens them.
 */
@Mixin(ServerRecipeBook.class)
public abstract class ServerRecipeBookMixin {
    @ModifyVariable(method = "addRecipes", at = @At("HEAD"), argsOnly = true)
    private Collection<RecipeHolder<?>> recipeUnlocks$holdBackRuledRecipes(Collection<RecipeHolder<?>> recipes, @Local(argsOnly = true) ServerPlayer player) {
        return UnlockManager.withoutRuledRecipes(player, recipes);
    }
}
