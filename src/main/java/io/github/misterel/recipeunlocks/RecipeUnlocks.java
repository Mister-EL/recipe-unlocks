package io.github.misterel.recipeunlocks;

import io.github.misterel.recipeunlocks.command.UnlocksCommand;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Recipes stay locked until the player unlocks them: the vanilla way (recipe book) or by
 * fulfilling a rule from {@code config/recipe-unlocks.toml}.
 *
 * <p>The mod is server-side only: it registers nothing that a client would have to know about.
 */
@Mod(RecipeUnlocks.MOD_ID)
public final class RecipeUnlocks {
    public static final String MOD_ID = "recipe_unlocks";

    public RecipeUnlocks() {
        NeoForge.EVENT_BUS.register(GameEvents.class);
        NeoForge.EVENT_BUS.addListener(UnlocksCommand::register);
    }
}
