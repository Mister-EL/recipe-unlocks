package io.github.misterel.recipeunlocks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.misterel.recipeunlocks.Hooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Furnace screen: the two ways of moving items that do not ask the slots, see {@link SlotMixin}. */
@Mixin(AbstractFurnaceMenu.class)
public abstract class AbstractFurnaceMenuMixin {
    @Shadow
    @Final
    private Container container;

    /**
     * Shift-click: an item the player has no unlocked recipe for is treated as "cannot be smelted".
     * Topping up a stack that already lies in the input slot does not ask the slot.
     */
    @WrapOperation(
            method = "quickMoveStack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractFurnaceMenu;canSmelt(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean recipeUnlocks$lockedIsNotSmeltable(
            AbstractFurnaceMenu menu, ItemStack stack, Operation<Boolean> original, @Local(argsOnly = true) Player player) {
        return original.call(menu, stack) && !Hooks.furnaceInputLocked(player, this.container, stack);
    }

    /** A click in the recipe book first moves the input and the output into the inventory, without asking the output slot. */
    @Inject(method = "handlePlacement", at = @At("HEAD"), cancellable = true)
    private void recipeUnlocks$keepLockedOutput(
            boolean useMaxItems, boolean allowDroppingItemsToClear, RecipeHolder<?> recipe, ServerLevel level, Inventory inventory,
            CallbackInfoReturnable<RecipeBookMenu.PostPlaceAction> cir) {
        if (this.container instanceof AbstractFurnaceBlockEntity furnace && Hooks.furnaceOutputLocked(inventory.player, furnace)) {
            cir.setReturnValue(RecipeBookMenu.PostPlaceAction.NOTHING);
        }
    }
}
