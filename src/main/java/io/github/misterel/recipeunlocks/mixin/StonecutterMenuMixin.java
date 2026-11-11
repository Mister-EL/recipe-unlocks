package io.github.misterel.recipeunlocks.mixin;

import io.github.misterel.recipeunlocks.Hooks;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stonecutter: clicking a recipe the player has not unlocked does nothing. */
@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuMixin {
    @Shadow
    private SelectableRecipe.SingleInputSet<StonecutterRecipe> recipesForInput;

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void recipeUnlocks$denyLocked(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        if (buttonId < 0 || buttonId >= this.recipesForInput.size()) {
            return;
        }
        Optional<RecipeHolder<StonecutterRecipe>> recipe = this.recipesForInput.entries().get(buttonId).recipe().recipe();
        if (recipe.isPresent() && Hooks.locked(player, recipe.get())) {
            // The client already drew the result on its own; send it the real, empty state.
            ((AbstractContainerMenu) (Object) this).sendAllDataToRemote();
            cir.setReturnValue(false);
        }
    }
}
