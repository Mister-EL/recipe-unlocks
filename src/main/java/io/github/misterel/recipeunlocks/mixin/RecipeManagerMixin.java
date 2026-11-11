package io.github.misterel.recipeunlocks.mixin;

import io.github.misterel.recipeunlocks.Hooks;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A cached "last used recipe" must not get around {@link RecipeMapMixin}. */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @ModifyVariable(
            method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
            at = @At("HEAD"),
            argsOnly = true)
    private RecipeHolder<?> recipeUnlocks$dropLockedHint(RecipeHolder<?> hint) {
        return hint != null && Hooks.hiddenFromActingPlayer(hint) ? null : hint;
    }
}
