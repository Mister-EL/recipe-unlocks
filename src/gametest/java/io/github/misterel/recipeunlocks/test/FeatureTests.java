package io.github.misterel.recipeunlocks.test;

import static io.github.misterel.recipeunlocks.test.Sim.check;

import io.github.misterel.recipeunlocks.unlock.PlaceTracker;
import io.github.misterel.recipeunlocks.unlock.Trigger;
import io.github.misterel.recipeunlocks.unlock.UnlockManager;
import java.util.ArrayList;
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
import net.minecraft.world.item.crafting.RecipeHolder;
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
            Sim.config("lock_all_recipes = true\ntemplate_unlocks = false\n");
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);
            check(helper, Sim.knownRecipes(player) == 0, "a new player knows no recipes, got " + Sim.knownRecipes(player));
            check(helper, !done(helper, player, "first_recipe"), "nothing is given for nothing");
            // The game gives the crafting table recipe to everybody on their first tick: not an achievement.
            Sim.unlock(helper, player, "crafting_table");
            check(helper, Sim.knownRecipes(player) == 1 && !done(helper, player, "first_recipe"), "a recipe everybody gets for free does not count");
            check(helper, advancement(helper, "hundred_recipes").value().display().orElseThrow().isHidden(), "the advancements are hidden until earned");

            learn(helper, player, 1);
            check(helper, done(helper, player, "first_recipe") && !done(helper, player, "ten_recipes"), "1 recipe: the first advancement");
            learn(helper, player, 9);
            check(helper, !done(helper, player, "ten_recipes"), "9 recipes are not 10");
            learn(helper, player, 10);
            check(helper, done(helper, player, "ten_recipes") && !done(helper, player, "hundred_recipes"), "10 recipes: the second advancement");
            learn(helper, player, 99);
            check(helper, !done(helper, player, "hundred_recipes"), "99 recipes are not 100");
            learn(helper, player, 100);
            check(helper, done(helper, player, "hundred_recipes"), "100 recipes: the third advancement");

            UnlockManager.take(player, "*");
            check(helper, done(helper, player, "hundred_recipes"), "an earned advancement stays");

            // With a rule the crafting table is no longer free: opening it by the rule is an achievement.
            Sim.config("[unlocks]\ncrafting_table = break stone\n");
            ServerPlayer ruled = Sim.join(helper, GameType.SURVIVAL);
            Sim.unlock(helper, ruled, "crafting_table");
            check(helper, !Sim.knows(helper, ruled, "crafting_table") && !done(helper, ruled, "first_recipe"), "the rule holds the crafting table back");
            Sim.breakBlock(helper, ruled, Blocks.STONE, CELL);
            check(helper, Sim.knows(helper, ruled, "crafting_table") && done(helper, ruled, "first_recipe"), "earned by the rule, it counts");
            helper.getLevel().removeBlock(helper.absolutePos(CELL), false);

            Sim.config("lock_all_recipes = true\ntemplate_unlocks = false\nachievements = false\n");
            ServerPlayer other = Sim.join(helper, GameType.SURVIVAL);
            learn(helper, other, 10);
            check(helper, !done(helper, other, "first_recipe"), "achievements = false switches them off");
        });
    }

    /** Opens recipes until the player has earned exactly {@code total}, not counting the free ones. */
    private static void learn(GameTestHelper helper, ServerPlayer player, int total) {
        var server = helper.getLevel().getServer();
        var free = UnlockManager.index(server).givenToEverybody();
        java.util.function.IntSupplier earned = () -> Sim.knownRecipes(player) - (int) free.stream().filter(player.getRecipeBook()::contains).count();
        List<RecipeHolder<?>> more = new ArrayList<>();
        for (RecipeHolder<?> recipe : server.getRecipeManager().getRecipes()) {
            if (more.size() < total && !recipe.value().isSpecial() && !free.contains(recipe.id()) && !player.getRecipeBook().contains(recipe.id())) {
                more.add(recipe);
            }
        }
        // One at a time: no recipe advancement of the game may add a second recipe on the way.
        more.forEach(recipe -> {
            if (earned.getAsInt() < total) {
                player.awardRecipes(List.of(recipe));
            }
        });
        check(helper, earned.getAsInt() == total, "the player should have earned " + total + " recipes, has " + earned.getAsInt());
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
