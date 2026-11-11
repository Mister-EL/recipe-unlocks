package io.github.misterel.recipeunlocks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.misterel.recipeunlocks.PlayerContext;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Marks which player the server is acting for while it handles that player's clicks. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @WrapMethod(method = "handleContainerClick")
    private void recipeUnlocks$containerClick(ServerboundContainerClickPacket packet, Operation<Void> original) {
        PlayerContext.run(this.player, () -> original.call(packet));
    }

    @WrapMethod(method = "handleContainerButtonClick")
    private void recipeUnlocks$containerButtonClick(ServerboundContainerButtonClickPacket packet, Operation<Void> original) {
        PlayerContext.run(this.player, () -> original.call(packet));
    }

    @WrapMethod(method = "handlePlaceRecipe")
    private void recipeUnlocks$placeRecipe(ServerboundPlaceRecipePacket packet, Operation<Void> original) {
        PlayerContext.run(this.player, () -> original.call(packet));
    }

    @WrapMethod(method = "handleUseItemOn")
    private void recipeUnlocks$useItemOn(ServerboundUseItemOnPacket packet, Operation<Void> original) {
        PlayerContext.run(this.player, () -> original.call(packet));
    }
}
