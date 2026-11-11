package io.github.misterel.recipeunlocks.config;

import io.github.misterel.recipeunlocks.unlock.Expr;
import java.util.List;

/**
 * Parsed config file.
 *
 * @param lockAll             every recipe is locked until the player unlocks it
 * @param templates           the [unlocks] rules are in force: a recipe with a rule is locked until the rule is fulfilled
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

    /** The [unlocks] rules are switched on and there is at least one. */
    public boolean rulesActive() {
        return this.templates && !this.rules.isEmpty();
    }

    /** The mod restricts something. */
    public boolean active() {
        return this.lockAll || this.rulesActive();
    }
}
