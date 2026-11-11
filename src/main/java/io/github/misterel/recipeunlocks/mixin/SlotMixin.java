package io.github.misterel.recipeunlocks.mixin;

import io.github.misterel.recipeunlocks.Hooks;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Furnace, blast furnace, smoker: a player can neither start nor collect smelting they have not unlocked. */
@Mixin(Slot.class)
public abstract class SlotMixin {
    private static final int FURNACE_INPUT_SLOT = 0;

    @Shadow
    @Final
    public Container container;

    @Shadow
    public abstract int getContainerSlot();

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void recipeUnlocks$furnaceOutput(Player player, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof FurnaceResultSlot
                && this.container instanceof AbstractFurnaceBlockEntity furnace
                && Hooks.furnaceOutputLocked(player, furnace)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void recipeUnlocks$furnaceInput(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (this.container instanceof AbstractFurnaceBlockEntity furnace
                && this.getContainerSlot() == FURNACE_INPUT_SLOT
                && Hooks.furnaceInputLocked(furnace, stack)) {
            cir.setReturnValue(false);
        }
    }
}
