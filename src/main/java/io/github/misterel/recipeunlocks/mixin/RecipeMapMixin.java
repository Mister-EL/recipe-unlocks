package io.github.misterel.recipeunlocks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.misterel.recipeunlocks.Hooks;
import io.github.misterel.recipeunlocks.PlayerContext;
import java.util.stream.Stream;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Recipe lookups made while the server handles a player's click do not see recipes that player
 * has not unlocked. This is what covers crafting stations added by other mods.
 */
@Mixin(RecipeMap.class)
public abstract class RecipeMapMixin {
    @ModifyReturnValue(method = "getRecipesFor", at = @At("RETURN"))
    private Stream<RecipeHolder<?>> recipeUnlocks$hideLocked(Stream<RecipeHolder<?>> matches) {
        return PlayerContext.get() == null ? matches : matches.filter(recipe -> !Hooks.hiddenFromActingPlayer(recipe));
    }
}
