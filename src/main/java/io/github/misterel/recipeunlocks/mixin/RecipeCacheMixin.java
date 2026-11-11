package io.github.misterel.recipeunlocks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.misterel.recipeunlocks.PlayerContext;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The crafter's recipe cache is shared by all crafters and remembers "no recipe" too. A lookup
 * made while a player has the crafter screen open must not store that player's filtered view in it.
 */
@Mixin(RecipeCache.class)
public abstract class RecipeCacheMixin {
    @WrapMethod(method = "get")
    private Optional<RecipeHolder<CraftingRecipe>> recipeUnlocks$unfiltered(
            ServerLevel level, CraftingInput input, Operation<Optional<RecipeHolder<CraftingRecipe>>> original) {
        return PlayerContext.callWithoutPlayer(() -> original.call(level, input));
    }
}
