package io.github.misterel.recipeunlocks;

import io.github.misterel.recipeunlocks.config.ModConfig;
import io.github.misterel.recipeunlocks.mixin.AbstractFurnaceBlockEntityAccessor;
import io.github.misterel.recipeunlocks.unlock.UnlockManager;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import org.jspecify.annotations.Nullable;

/** Entry points called from the mixins. Everything here is cheap when the mod is switched off. */
public final class Hooks {
    private static final String CRAFTER_OWNER = RecipeUnlocks.MOD_ID + ":owner";

    private Hooks() {}

    public static boolean locked(@Nullable Player player, RecipeHolder<?> recipe) {
        return player instanceof ServerPlayer serverPlayer && UnlockManager.isLocked(serverPlayer, recipe);
    }

    /** Replaces a locked match with the first matching recipe the player may use, if any. */
    public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> skipLocked(
            Optional<RecipeHolder<T>> found, @Nullable Player player, RecipeType<T> type, I input, Level level) {
        if (found.isEmpty() || !(player instanceof ServerPlayer serverPlayer) || !UnlockManager.isLocked(serverPlayer, found.get())) {
            return found;
        }
        for (RecipeHolder<T> recipe : serverPlayer.level().getServer().getRecipeManager().recipeMap().byType(type)) {
            if (recipe.value().matches(input, level) && !UnlockManager.isLocked(serverPlayer, recipe)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    /** True inside a recipe lookup made for a player who has not unlocked {@code recipe}. */
    public static boolean hiddenFromActingPlayer(RecipeHolder<?> recipe) {
        ServerPlayer player = PlayerContext.get();
        return player != null && UnlockManager.config().blockModdedStations() && UnlockManager.isLocked(player, recipe);
    }

    /** The player may not take the furnace output while it was made with a recipe they have not unlocked. */
    public static boolean furnaceOutputLocked(Player player, AbstractFurnaceBlockEntity furnace) {
        if (!(player instanceof ServerPlayer serverPlayer) || !UnlockManager.config().active()) {
            return false;
        }
        MinecraftServer server = serverPlayer.level().getServer();
        for (ResourceKey<Recipe<?>> used : ((AbstractFurnaceBlockEntityAccessor) furnace).recipeUnlocks$recipesUsed().keySet()) {
            Optional<RecipeHolder<?>> recipe = server.getRecipeManager().byKey(used);
            if (recipe.isPresent() && UnlockManager.isLocked(serverPlayer, recipe.get())) {
                return true;
            }
        }
        return false;
    }

    /** The player may not put {@code stack} into the furnace: every recipe for it is locked. */
    public static boolean furnaceInputLocked(@Nullable Player player, Container container, ItemStack stack) {
        return player != null
                && container instanceof AbstractFurnaceBlockEntityAccessor furnace
                && cookingLocked(player, furnace.recipeUnlocks$recipeType(), stack);
    }

    /** True when {@code stack} can be cooked, but only with recipes the player has not unlocked. */
    public static <T extends Recipe<SingleRecipeInput>> boolean cookingLocked(@Nullable Object cook, RecipeType<T> type, ItemStack stack) {
        if (!(cook instanceof ServerPlayer player) || stack.isEmpty() || !UnlockManager.config().active()) {
            return false;
        }
        ServerLevel level = player.level();
        SingleRecipeInput input = new SingleRecipeInput(stack);
        boolean anyRecipe = false;
        for (RecipeHolder<T> recipe : level.getServer().getRecipeManager().recipeMap().byType(type)) {
            if (recipe.value().matches(input, level)) {
                if (!UnlockManager.isLocked(player, recipe)) {
                    return false;
                }
                anyRecipe = true;
            }
        }
        return anyRecipe;
    }

    /** Remembers who placed a crafter, so that it can follow that player's unlocked recipes. */
    public static void rememberCrafterOwner(@Nullable BlockEntity blockEntity, ServerPlayer player) {
        if (blockEntity instanceof CrafterBlockEntity) {
            blockEntity.getPersistentData().putString(CRAFTER_OWNER, player.getUUID().toString());
            blockEntity.setChanged();
        }
    }

    /** The crafter only crafts what its owner, who has to be online, has unlocked. */
    public static Optional<RecipeHolder<CraftingRecipe>> filterCrafter(Optional<RecipeHolder<CraftingRecipe>> found, ServerLevel level, BlockPos pos) {
        ModConfig config = UnlockManager.config();
        if (found.isEmpty() || !config.active() || !config.lockCrafter() || !UnlockManager.isLockable(level.getServer(), found.get())) {
            return found;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        String owner = blockEntity == null ? "" : blockEntity.getPersistentData().getStringOr(CRAFTER_OWNER, "");
        ServerPlayer player = null;
        try {
            player = owner.isEmpty() ? null : level.getServer().getPlayerList().getPlayer(UUID.fromString(owner));
        } catch (IllegalArgumentException e) {
            // Not a UUID: treat the crafter as having no owner.
        }
        return player != null && !UnlockManager.isLocked(player, found.get()) ? found : Optional.empty();
    }
}
