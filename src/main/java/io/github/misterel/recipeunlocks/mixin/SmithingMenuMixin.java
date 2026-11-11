package io.github.misterel.recipeunlocks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.misterel.recipeunlocks.Hooks;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Smithing table: no result for a recipe the player has not unlocked. */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {
    @Shadow
    @Final
    private Level level;

    @ModifyExpressionValue(
            method = "createResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private Optional<RecipeHolder<SmithingRecipe>> recipeUnlocks$skipLocked(
            Optional<RecipeHolder<SmithingRecipe>> found, @Local SmithingRecipeInput input) {
        Player player = ((ItemCombinerMenuAccessor) this).recipeUnlocks$player();
        return Hooks.skipLocked(found, player, RecipeType.SMITHING, input, this.level);
    }
}
