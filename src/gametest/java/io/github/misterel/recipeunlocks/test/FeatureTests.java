package io.github.misterel.recipeunlocks.test;

import static io.github.misterel.recipeunlocks.test.Sim.check;

import io.github.misterel.recipeunlocks.unlock.PlaceTracker;
import io.github.misterel.recipeunlocks.unlock.Trigger;
import io.github.misterel.recipeunlocks.unlock.UnlockManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Operator commands, place templates and the mod's advancements. */
final class FeatureTests {
    private static final BlockPos CELL = new BlockPos(1, 1, 0);
    private static final String STICK_RULE = """
            lock_all_recipes = true
            template_unlocks = true
            [unlocks]
            stick = break stone 2
            """;

    private FeatureTests() {}

    static void giveAndTake(GameTestHelper helper) {
        Sim.run(helper, () -> {
            Sim.config(STICK_RULE);
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);
            String name = player.getGameProfile().name();

            // give: by item id it opens every recipe of the item, rule or no rule
            UnlockManager.CommandResult given = UnlockManager.give(player, "stick");
            check(helper, given.problems().isEmpty() && given.recipes() == 2, "both stick recipes are given: " + given);
            check(helper, Sim.knows(helper, player, "stick") && Sim.knows(helper, player, "stick_from_bamboo_item"), "and are in the recipe book");
            check(helper, UnlockManager.give(player, "minecraft:stick").recipes() == 0, "giving again changes nothing");

            // take: the recipe goes, the rule's counters go, and it does not come back by itself
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            UnlockManager.CommandResult taken = UnlockManager.take(player, "stick");
            check(helper, taken.problems().isEmpty() && taken.recipes() == 2, "both stick recipes are taken: " + taken);
            check(helper, !Sim.knows(helper, player, "stick"), "the recipe is closed");
            check(helper, Sim.progressLine(player, "stick").equals("stick: break minecraft:stone 0/2"), "its rule starts over: " + Sim.progressLine(player, "stick"));
            UnlockManager.evaluateAll(player);
            check(helper, !Sim.knows(helper, player, "stick"), "and it stays closed");
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            check(helper, Sim.knows(helper, player, "stick"), "until the rule is fulfilled again");

            // what can be named: a tag, a recipe id; a typo is reported
            check(helper, UnlockManager.give(player, "#minecraft:planks").recipes() >= 10, "a tag gives the recipes of all its items");
            check(helper, UnlockManager.give(player, "iron_ingot_from_blasting_raw_iron").recipes() == 1, "a recipe id gives that recipe");
            UnlockManager.CommandResult typo = UnlockManager.give(player, "minecraft:no_such_thing");
            check(helper, typo.recipes() == 0 && typo.problems().size() == 1, "an unknown name is a problem: " + typo);
            check(helper, UnlockManager.take(player, "dirt").problems().size() == 1, "so is an item nothing crafts");

            // the same through the commands, as the console types them
            Sim.console(helper, "unlocks give " + name + " torch");
            check(helper, Sim.knows(helper, player, "torch"), "/unlocks give");
            Sim.console(helper, "unlocks take " + name + " minecraft:torch");
            check(helper, !Sim.knows(helper, player, "torch"), "/unlocks take");

            // everything at once
            Sim.console(helper, "unlocks give " + name + " *");
            check(helper, Sim.knownRecipes(player) > 1000, "give * opens every recipe, got " + Sim.knownRecipes(player));
            Sim.console(helper, "unlocks take " + name + " *");
            check(helper, Sim.knownRecipes(player) == 0, "take * closes every recipe, left " + Sim.knownRecipes(player));

            // a recipe without a rule can be earned again the vanilla way after a take
            Sim.config("lock_all_recipes = true\ntemplate_unlocks = false\n");
            ServerPlayer vanilla = Sim.join(helper, GameType.SURVIVAL);
            Sim.obtainPlanks(vanilla);
            check(helper, Sim.knows(helper, vanilla, "stick"), "planks unlock sticks the vanilla way");
            UnlockManager.take(vanilla, "stick");
            check(helper, !Sim.knows(helper, vanilla, "stick") && Sim.sticksFromInventoryGrid(vanilla).isEmpty(), "taken and locked");
            Sim.obtainPlanks(vanilla);
            check(helper, Sim.knows(helper, vanilla, "stick"), "and earned again by getting planks");
            helper.getLevel().removeBlock(helper.absolutePos(CELL), false);
        });
    }

    static void placesCountOnEntering(GameTestHelper helper) {
        Sim.run(helper, () -> {
            ServerLevel level = helper.getLevel();
            String here = level.getBiome(helper.absolutePos(new BlockPos(0, 1, 0))).unwrapKey().orElseThrow().identifier().toString();
            Sim.config("""
                    [unlocks]
                    stick = dimension the_nether
                    torch = dimension overworld 2
                    ladder = biome %s
                    chest = structure ancient_city
                    bow = structure #minecraft:village 2
                    """.formatted(here));
            Identifier overworld = Identifier.withDefaultNamespace("overworld");
            Identifier nether = Identifier.withDefaultNamespace("the_nether");
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);

            // A real look at where the player stands: the overworld, in the biome of the test area.
            PlaceTracker.look(player);
            check(helper, Sim.knows(helper, player, "ladder"), "the biome the player stands in is counted: " + Sim.progressLine(player, "ladder"));
            check(helper, Sim.progressLine(player, "torch").equals("torch: dimension minecraft:overworld 1/2"), Sim.progressLine(player, "torch"));
            PlaceTracker.look(player);
            check(helper, !Sim.knows(helper, player, "torch"), "staying in a place is not entering it again");

            PlaceTracker.arrive(player, places(level, nether));
            check(helper, Sim.knows(helper, player, "stick"), "dimension: entering the Nether");
            PlaceTracker.arrive(player, places(level, overworld));
            check(helper, Sim.knows(helper, player, "torch"), "coming back is the second entry into the overworld");

            PlaceTracker.arrive(player, places(level, overworld, "ancient_city", "village_plains"));
            check(helper, Sim.knows(helper, player, "chest"), "structure: by id");
            check(helper, !Sim.knows(helper, player, "bow"), "one village of two");
            PlaceTracker.arrive(player, places(level, overworld));
            PlaceTracker.arrive(player, places(level, overworld, "village_desert"));
            check(helper, Sim.knows(helper, player, "bow"), "structure: by tag, any village counts");

            // Logging in again must not add up: the first look after a login only counts what is new.
            ServerPlayer regular = Sim.join(helper, GameType.SURVIVAL);
            PlaceTracker.look(regular);
            PlaceTracker.forget(regular);
            PlaceTracker.look(regular);
            check(helper, Sim.progressLine(regular, "torch").equals("torch: dimension minecraft:overworld 1/2"),
                    "a login is not a new entry: " + Sim.progressLine(regular, "torch"));

            // A cleared counter counts the place the player is still standing in, once.
            UnlockManager.take(regular, "torch");
            PlaceTracker.look(regular);
            PlaceTracker.look(regular);
            check(helper, Sim.progressLine(regular, "torch").equals("torch: dimension minecraft:overworld 1/2"),
                    "after a take the current place counts once: " + Sim.progressLine(regular, "torch"));

            ServerPlayer creative = Sim.join(helper, GameType.CREATIVE);
            PlaceTracker.look(creative);
            check(helper, !Sim.knows(helper, creative, "ladder"), "creative mode is not counted");
        });
    }

    static void achievementsForKnownRecipes(GameTestHelper helper) {
        Sim.run(helper, () -> {
            check(helper, advancement(helper, "hundred_recipes").value().display().orElseThrow().isHidden(), "the advancements are hidden until earned");

            // Recipes without a rule never count, however many.
            Sim.config("lock_all_recipes = true\ntemplate_unlocks = false\n");
            ServerPlayer vanilla = Sim.join(helper, GameType.SURVIVAL);
            Sim.unlock(helper, vanilla, "crafting_table");
            UnlockManager.give(vanilla, "*");
            check(helper, Sim.knownRecipes(vanilla) > 1000 && !done(helper, vanilla, "first_recipe"), "recipes without a rule do not count");

            // One rule counts once, and only when all of its recipes are known.
            Sim.config("""
                    [unlocks]
                    iron_ingot = stat jump 1000000
                    stick = break stone
                    """);
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);
            UnlockManager.give(player, "#minecraft:planks");
            check(helper, !done(helper, player, "first_recipe"), "recipes of items without a rule do not count");
            UnlockManager.give(player, "iron_ingot_from_nuggets");
            check(helper, UnlockManager.rulesDone(player) == 0 && !done(helper, player, "first_recipe"), "one of the 8 iron ingot recipes is not the whole rule");
            UnlockManager.give(player, "iron_ingot");
            check(helper, UnlockManager.rulesDone(player) == 1 && done(helper, player, "first_recipe"), "all 8 recipes given by an operator: the rule counts once");
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            check(helper, UnlockManager.rulesDone(player) == 2, "a fulfilled rule counts: " + UnlockManager.rulesDone(player));
            helper.getLevel().removeBlock(helper.absolutePos(CELL), false);

            // The thresholds, on a config of 100 rules nobody can fulfil by playing.
            List<String> items = UnlockManager.index(helper.getLevel().getServer()).craftableItems().stream()
                    .map(Identifier::toString).sorted().limit(100).toList();
            check(helper, items.size() == 100, "100 craftable items expected, got " + items.size());
            StringBuilder rules = new StringBuilder("[unlocks]\n");
            items.forEach(item -> rules.append(item).append(" = stat jump 1000000\n"));
            Sim.config(rules.toString());
            ServerPlayer collector = Sim.join(helper, GameType.SURVIVAL);
            check(helper, UnlockManager.index(helper.getLevel().getServer()).entries().size() == 100, "all 100 rules are in force");
            giveRules(helper, collector, items, 0, 1);
            check(helper, done(helper, collector, "first_recipe") && !done(helper, collector, "ten_recipes"), "1 rule: the first advancement");
            giveRules(helper, collector, items, 1, 9);
            check(helper, !done(helper, collector, "ten_recipes"), "9 rules are not 10");
            giveRules(helper, collector, items, 9, 10);
            check(helper, done(helper, collector, "ten_recipes") && !done(helper, collector, "hundred_recipes"), "10 rules: the second advancement");
            giveRules(helper, collector, items, 10, 99);
            check(helper, !done(helper, collector, "hundred_recipes"), "99 rules are not 100");
            giveRules(helper, collector, items, 99, 100);
            check(helper, done(helper, collector, "hundred_recipes"), "100 rules: the third advancement");

            UnlockManager.take(collector, "*");
            check(helper, UnlockManager.rulesDone(collector) == 0 && done(helper, collector, "hundred_recipes"), "an earned advancement stays after take");

            Sim.config("achievements = false\n[unlocks]\nstick = break stone\n");
            ServerPlayer other = Sim.join(helper, GameType.SURVIVAL);
            UnlockManager.give(other, "stick");
            check(helper, !done(helper, other, "first_recipe"), "achievements = false switches them off");
        });
    }

    /** Gives the player the recipes of rules {@code from} to {@code to} (exclusive) and checks the count. */
    private static void giveRules(GameTestHelper helper, ServerPlayer player, List<String> items, int from, int to) {
        for (String item : items.subList(from, to)) {
            UnlockManager.give(player, item);
        }
        check(helper, UnlockManager.rulesDone(player) == to, to + " rules should be done, are " + UnlockManager.rulesDone(player));
    }

    private static AdvancementHolder advancement(GameTestHelper helper, String name) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("recipe_unlocks", name));
        if (holder == null) {
            throw new IllegalStateException("the advancement recipe_unlocks:" + name + " is not loaded");
        }
        return holder;
    }

    private static boolean done(GameTestHelper helper, ServerPlayer player, String name) {
        return player.getAdvancements().getOrStartProgress(advancement(helper, name)).isDone();
    }

    /** A made-up position: the given dimension and, inside it, the given structures. */
    private static Map<String, PlaceTracker.Place> places(ServerLevel level, Identifier dimension, String... structures) {
        Map<String, PlaceTracker.Place> places = new HashMap<>();
        places.put("dimension " + dimension, new PlaceTracker.Place(Trigger.DIMENSION, dimension));
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (String name : structures) {
            Identifier id = Identifier.withDefaultNamespace(name);
            Structure structure = registry.getOptional(id).orElseThrow(() -> new IllegalStateException("no structure " + id));
            places.put("structure " + id, new PlaceTracker.Place(Trigger.STRUCTURE, registry.wrapAsHolder(structure)));
        }
        return places;
    }
}
