package io.github.misterel.recipeunlocks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.misterel.recipeunlocks.Hooks;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Crafting table and inventory grid: when the first matching recipe is locked, try the next one. */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @ModifyExpressionValue(
            method = "slotChangedCraftingGrid",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;"))
    private static Optional<RecipeHolder<CraftingRecipe>> recipeUnlocks$skipLocked(
            Optional<RecipeHolder<CraftingRecipe>> found,
            @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) Player player,
            @Local(argsOnly = true) CraftingContainer container) {
        return Hooks.skipLocked(found, player, RecipeType.CRAFTING, container.asCraftInput(), level);
    }
}
