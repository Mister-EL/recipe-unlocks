package io.github.misterel.recipeunlocks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.misterel.recipeunlocks.unlock.UnlockManager;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.RecipeCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;

/** {@code /recipe give} is an operator's decision: it also gives recipes that have a rule. */
@Mixin(RecipeCommand.class)
public abstract class RecipeCommandMixin {
    @WrapMethod(method = "giveRecipes")
    private static int recipeUnlocks$operatorGrant(
            CommandSourceStack source, Collection<ServerPlayer> players, Collection<RecipeHolder<?>> recipes, Operation<Integer> original) {
        UnlockManager.setOperatorGrant(true);
        try {
            return original.call(source, players, recipes);
        } finally {
            UnlockManager.setOperatorGrant(false);
        }
    }
}
