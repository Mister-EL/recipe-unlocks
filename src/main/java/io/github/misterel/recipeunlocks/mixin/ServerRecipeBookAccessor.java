package io.github.misterel.recipeunlocks.mixin;

import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerRecipeBook.class)
public interface ServerRecipeBookAccessor {
    @Accessor("known")
    Set<ResourceKey<Recipe<?>>> recipeUnlocks$known();
}
