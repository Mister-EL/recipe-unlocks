package io.github.misterel.recipeunlocks.mixin;

import io.github.misterel.recipeunlocks.Hooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Campfire: food a player has no unlocked recipe for cannot be put on it. */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireBlockEntityMixin {
    @Inject(method = "placeFood", at = @At("HEAD"), cancellable = true)
    private void recipeUnlocks$denyLocked(ServerLevel level, LivingEntity source, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Hooks.cookingLocked(source, RecipeType.CAMPFIRE_COOKING, stack)) {
            cir.setReturnValue(false);
        }
    }
}
