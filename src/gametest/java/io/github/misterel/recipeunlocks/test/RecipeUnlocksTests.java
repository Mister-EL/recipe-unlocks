package io.github.misterel.recipeunlocks.test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the game tests. This mod exists only in development runs, never in the released jar. */
@Mod(RecipeUnlocksTests.MOD_ID)
public final class RecipeUnlocksTests {
    public static final String MOD_ID = "recipe_unlocks_test";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, MOD_ID);

    /** Tests that finish in the tick they start in; they may share a batch although the config is global. */
    private static final Map<String, Consumer<GameTestHelper>> QUICK = new LinkedHashMap<>();
    /** Tests that run over many ticks; each gets its own batch so nobody swaps the config under it. */
    private static final Map<String, Consumer<GameTestHelper>> SLOW = new LinkedHashMap<>();

    public RecipeUnlocksTests(IEventBus modBus) {
        QUICK.put("rule_syntax", SyntaxTests::ruleSyntax);
        QUICK.put("config_file", SyntaxTests::configFile);
        QUICK.put("config_problems", SyntaxTests::configProblems);
        QUICK.put("crafting_locked_until_unlocked", LockTests::craftingLockedUntilUnlocked);
        QUICK.put("smithing_locked", LockTests::smithingLocked);
        QUICK.put("stonecutter_locked", LockTests::stonecutterLocked);
        QUICK.put("furnace_locked", LockTests::furnaceLocked);
        QUICK.put("campfire_locked", LockTests::campfireLocked);
        QUICK.put("lookups_hidden_from_acting_player", LockTests::lookupsHiddenFromActingPlayer);
        QUICK.put("rule_unlocks_recipe", UnlockTests::ruleUnlocksRecipe);
        QUICK.put("rule_is_the_only_way", UnlockTests::ruleIsTheOnlyWay);
        QUICK.put("only_ruled_items_locked_without_lock_all", UnlockTests::onlyRuledItemsLockedWithoutLockAll);
        QUICK.put("every_template_counts", UnlockTests::everyTemplateCounts);
        QUICK.put("creative_is_not_counted", UnlockTests::creativeIsNotCounted);
        QUICK.put("reset_takes_recipes_back", UnlockTests::resetTakesRecipesBack);
        QUICK.put("progress_is_saved", UnlockTests::progressIsSaved);
        SLOW.put("crafter_follows_owner", LockTests::crafterFollowsOwner);

        QUICK.forEach((name, test) -> FUNCTIONS.register(name, () -> test));
        SLOW.forEach((name, test) -> FUNCTIONS.register(name, () -> test));
        FUNCTIONS.register(modBus);
        modBus.addListener(RegisterGameTestsEvent.class, RecipeUnlocksTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> quick = event.registerEnvironment(id("quick"));
        QUICK.keySet().forEach(name -> register(event, name, quick, 20));
        for (String name : SLOW.keySet()) {
            register(event, name, event.registerEnvironment(id("slow_" + name)), 200);
        }
    }

    private static void register(RegisterGameTestsEvent event, String name, Holder<TestEnvironmentDefinition<?>> environment, int maxTicks) {
        // The 1x1x1 "empty" structure plus padding: tests build what they need above their own cell.
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), maxTicks, 0, true, Rotation.NONE, false, 1, 1, false, 4);
        event.registerTest(id(name), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id(name)), data));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
