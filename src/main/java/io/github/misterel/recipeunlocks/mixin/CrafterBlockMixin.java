package io.github.misterel.recipeunlocks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.misterel.recipeunlocks.Hooks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Crafter block: follows the unlocked recipes of the player who placed it. */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {
    @ModifyExpressionValue(
            method = "dispenseFrom",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/CrafterBlock;getPotentialResults(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/crafting/CraftingInput;)Ljava/util/Optional;"))
    private Optional<RecipeHolder<CraftingRecipe>> recipeUnlocks$ownerOnly(
            Optional<RecipeHolder<CraftingRecipe>> found, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos) {
        return Hooks.filterCrafter(found, level, pos);
    }
}
