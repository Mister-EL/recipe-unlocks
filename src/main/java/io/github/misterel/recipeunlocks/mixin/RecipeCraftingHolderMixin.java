package io.github.misterel.recipeunlocks.mixin;

import io.github.misterel.recipeunlocks.Hooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The check every crafting grid makes before showing a result (the vanilla "limited crafting" hook). */
@Mixin(RecipeCraftingHolder.class)
public interface RecipeCraftingHolderMixin {
    @Inject(
            method = "setRecipeUsed(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/crafting/RecipeHolder;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void recipeUnlocks$denyLocked(ServerPlayer player, RecipeHolder<?> recipe, CallbackInfoReturnable<Boolean> cir) {
        if (Hooks.locked(player, recipe)) {
            cir.setReturnValue(false);
        }
    }
}
