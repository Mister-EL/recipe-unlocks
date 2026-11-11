package io.github.misterel.recipeunlocks.config;

import io.github.misterel.recipeunlocks.unlock.Expr;
import java.util.List;

/**
 * Parsed config file.
 *
 * @param lockAll             recipes are locked until the player unlocks them
 * @param templates           the [unlocks] rules are active
 * @param lockWithoutUnlock   also lock recipes that nothing can ever unlock
 * @param lockCrafter         the crafter block only crafts what its owner has unlocked
 * @param blockModdedStations hide locked recipes from recipe lookups made on behalf of a player
 * @param rules               the [unlocks] rules, in file order
 * @param problems            human-readable problems found while reading the file
 */
public record ModConfig(
        boolean lockAll,
        boolean templates,
        boolean lockWithoutUnlock,
        boolean lockCrafter,
        boolean blockModdedStations,
        List<Rule> rules,
        List<String> problems) {

    /** One [unlocks] line: what to unlock and what the player has to do for it. */
    public record Rule(String output, Expr expr) {}

    /** Used until the server reads the real config: the mod does nothing. */
    public static final ModConfig DISABLED = new ModConfig(false, false, false, false, false, List.of(), List.of());

    public boolean templatesActive() {
        return this.lockAll && this.templates;
    }
}
