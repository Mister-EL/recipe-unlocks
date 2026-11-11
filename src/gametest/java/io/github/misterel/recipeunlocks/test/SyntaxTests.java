package io.github.misterel.recipeunlocks.test;

import static io.github.misterel.recipeunlocks.test.Sim.check;

import io.github.misterel.recipeunlocks.config.ConfigLoader;
import io.github.misterel.recipeunlocks.config.ModConfig;
import io.github.misterel.recipeunlocks.unlock.ExprParser;
import io.github.misterel.recipeunlocks.unlock.UnlockIndex;
import io.github.misterel.recipeunlocks.unlock.UnlockManager;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/** The config file and the rule language. */
final class SyntaxTests {
    private SyntaxTests() {}

    static void ruleSyntax(GameTestHelper helper) {
        Sim.run(helper, () -> {
            parsesTo(helper, "break stone 3 & (eat apple | drop torch)",
                    "break minecraft:stone 3 & (eat minecraft:apple 1 | dropped minecraft:torch 1)");
            // & binds tighter than |
            parsesTo(helper, "jump * | hit zombie & kill zombie 2", "jump * 1 | hit minecraft:zombie 1 & kill minecraft:zombie 2");
            parsesTo(helper, "(jump * | hit zombie) & kill zombie 2", "(jump * 1 | hit minecraft:zombie 1) & kill minecraft:zombie 2");
            // Russian and word operators, doubled symbols, upper case
            parsesTo(helper, "break stone и (eat apple или drop torch)",
                    "break minecraft:stone 1 & (eat minecraft:apple 1 | dropped minecraft:torch 1)");
            parsesTo(helper, "BREAK Stone AND eat apple && place #minecraft:logs + use bow, craft stick 2",
                    "break minecraft:stone 1 & eat minecraft:apple 1 & place #minecraft:logs 1 & used minecraft:bow 1 & crafted minecraft:stick 2");
            parsesTo(helper, "pickup string || mine dirt", "picked_up minecraft:string 1 | mined minecraft:dirt 1");
            // any statistic type of the game is a template
            parsesTo(helper, "stat jump 5 & killed_by zombie & minecraft:broken wooden_pickaxe",
                    "custom minecraft:jump 5 & killed_by minecraft:zombie 1 & broken minecraft:wooden_pickaxe 1");
            parsesTo(helper, "break create:cogwheel", "break create:cogwheel 1");

            failsWith(helper, "", "пустое");
            failsWith(helper, "fly stone", "неизвестный шаблон 'fly'");
            failsWith(helper, "break", "нужна цель");
            failsWith(helper, "break &", "нужна цель");
            failsWith(helper, "(break stone", "не закрыта скобка");
            failsWith(helper, "break stone)", "лишнее ')'");
            failsWith(helper, "break stone eat apple", "нужен & или |");
            failsWith(helper, "break stone 0", "количество");
            failsWith(helper, "break stone 99999999999", "количество");
            failsWith(helper, "break stone &", "ожидалось условие");
            failsWith(helper, "break st!one", "не похоже на id");
        });
    }

    static void configFile(GameTestHelper helper) {
        Sim.run(helper, () -> {
            ModConfig empty = parse("");
            check(helper, empty.lockAll() && empty.templates() && !empty.lockWithoutUnlock() && empty.lockCrafter() && empty.blockModdedStations(),
                    "defaults of a file without settings");
            check(helper, empty.rules().isEmpty() && empty.problems().isEmpty(), "an empty file has no rules and no problems");

            ModConfig config = parse("""
                    lock_all_recipes = false
                    template_unlocks = false
                    lock_recipes_without_unlock = true
                    lock_crafter = false
                    block_modded_stations = false

                    [unlocks]
                    "minecraft:torch" = "break stone 3"
                    stick = ["jump * 2", "hit zombie | kill zombie"]
                    "create:cogwheel" = "place #minecraft:logs 10"
                    """);
            check(helper, !config.lockAll() && !config.templates() && config.lockWithoutUnlock() && !config.lockCrafter() && !config.blockModdedStations(),
                    "every setting is read");
            check(helper, config.problems().isEmpty(), "no problems expected, got " + config.problems());
            check(helper, config.rules().size() == 3, "three rules expected");
            check(helper, config.rules().get(0).output().equals("minecraft:torch") && config.rules().get(1).output().equals("stick"),
                    "rules keep the order of the file");
            check(helper, config.rules().get(1).expr().describe(null).equals("jump * 2 & (hit minecraft:zombie 1 | kill minecraft:zombie 1)"),
                    "a list of lines means all of them: " + config.rules().get(1).expr().describe(null));

            ModConfig shipped = parse(defaultFile());
            check(helper, shipped.problems().isEmpty() && shipped.rules().isEmpty() && shipped.lockAll() && shipped.templates(),
                    "the shipped default file is valid and has no active rules: " + shipped.problems());
            // Every commented example of the shipped file must be a valid rule.
            String withExamples = defaultFile().lines()
                    .dropWhile(line -> !line.equals("[unlocks]"))
                    .map(line -> line.startsWith("# ") ? line.substring(2) : line)
                    .collect(java.util.stream.Collectors.joining("\n"));
            ModConfig examples = parse(withExamples);
            check(helper, examples.problems().isEmpty() && examples.rules().size() == 5, "shipped examples: " + examples.problems() + examples.rules());

            // Quotes are optional below [unlocks].
            ModConfig bare = parse("""
                    lock_crafter = false
                    [unlocks]
                    minecraft:torch = break stone 3 & (eat apple | drop stick)   # comment
                    stick=jump #minecraft:logs 2
                    "minecraft:bow" = hit skeleton
                      'minecraft:bread'   =   "eat apple"
                    "#minecraft:planks" = [
                      "jump * 2",
                      "hit zombie"
                    ]
                    some.mod:thing.v2 = stat jump
                    # minecraft:ladder = break stone
                    """);
            check(helper, bare.problems().isEmpty(), "unquoted rules: " + bare.problems());
            check(helper, bare.rules().stream().map(ModConfig.Rule::output).toList()
                            .equals(List.of("minecraft:torch", "stick", "minecraft:bow", "minecraft:bread", "#minecraft:planks", "some.mod:thing.v2")),
                    "unquoted rules: " + bare.rules());
            check(helper, bare.rules().get(0).expr().describe(null).equals("break minecraft:stone 3 & (eat minecraft:apple 1 | dropped minecraft:stick 1)"),
                    bare.rules().get(0).expr().describe(null));
            check(helper, bare.rules().get(1).expr().describe(null).equals("jump #minecraft:logs 2"), bare.rules().get(1).expr().describe(null));
            check(helper, !bare.lockCrafter(), "settings above [unlocks] are untouched");
            check(helper, parse("[unlocks]\ntorch =\n").problems().size() == 1, "an empty rule is a problem, not a crash");

            ModConfig broken = parse("""
                    lock_all_recipes = "yes"
                    surprise = 1
                    [unlocks]
                    "minecraft:torch" = "fly stone"
                    "minecraft:stick" = 5
                    "minecraft:bow" = "kill skeleton"
                    """);
            check(helper, broken.lockAll(), "a wrong value falls back to the default");
            check(helper, broken.rules().size() == 1 && broken.rules().getFirst().output().equals("minecraft:bow"), "good rules survive bad neighbours");
            check(helper, broken.problems().size() == 4, "four problems expected: " + broken.problems());

            try {
                ConfigLoader.parse("lock_all_recipes = = true");
                helper.fail("a file that is not TOML must be rejected");
            } catch (ConfigLoader.UnreadableException expected) {
                check(helper, expected.getMessage().contains("TOML"), expected.getMessage());
            }
        });
    }

    /** Problems that only show up once the rules meet the real game data. */
    static void configProblems(GameTestHelper helper) {
        Sim.run(helper, () -> {
            Sim.config("""
                    [unlocks]
                    "minecraft:stick" = "break ston 2"
                    "minecraft:no_such_thing" = "break stone"
                    "#minecraft:no_such_tag" = "break stone"
                    "#minecraft:planks" = "break #minecraft:logs"
                    "minecraft:iron_ingot" = "hit zombie"
                    "minecraft:iron_ingot_from_blasting_raw_iron" = "hit zombie 2"
                    "minecraft:dirt" = "break stone"
                    """);
            UnlockIndex index = UnlockManager.index(helper.getLevel().getServer());
            List<String> problems = index.problems();
            check(helper, problems.size() == 4, "four problems expected: " + problems);
            check(helper, problems.stream().anyMatch(p -> p.contains("'minecraft:ston'")), "unknown block is reported: " + problems);
            check(helper, problems.stream().anyMatch(p -> p.contains("no_such_thing")), "unknown output is reported");
            check(helper, problems.stream().anyMatch(p -> p.contains("no_such_tag")), "unknown tag is reported");
            check(helper, problems.stream().anyMatch(p -> p.contains("'minecraft:dirt'")), "an item nothing crafts is reported");

            UnlockIndex.Entry planks = entry(index, "#minecraft:planks");
            check(helper, planks.recipes().size() >= 10, "a tag unlocks the recipes of all its items, got " + planks.recipes().size());
            UnlockIndex.Entry ingot = entry(index, "minecraft:iron_ingot");
            check(helper, ingot.recipes().size() >= 4, "an item unlocks all its recipes (crafting, smelting, blasting), got " + ingot.recipes().size());
            check(helper, entry(index, "minecraft:iron_ingot_from_blasting_raw_iron").recipes().size() == 1, "a recipe id unlocks that recipe");
        });
    }

    private static UnlockIndex.Entry entry(UnlockIndex index, String output) {
        return index.entries().stream().filter(e -> e.output().equals(output)).findFirst().orElseThrow();
    }

    private static ModConfig parse(String toml) {
        try {
            return ConfigLoader.parse(toml);
        } catch (ConfigLoader.UnreadableException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String defaultFile() {
        try (InputStream in = ConfigLoader.class.getResourceAsStream("/recipe-unlocks-default.toml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void parsesTo(GameTestHelper helper, String rule, String expected) {
        try {
            String actual = ExprParser.parse(rule).describe(null);
            check(helper, actual.equals(expected), "'" + rule + "' parsed as '" + actual + "', expected '" + expected + "'");
        } catch (ExprParser.SyntaxException e) {
            helper.fail("'" + rule + "' was rejected: " + e.getMessage());
        }
    }

    private static void failsWith(GameTestHelper helper, String rule, String expectedPart) {
        try {
            String parsed = ExprParser.parse(rule).describe(null);
            helper.fail("'" + rule + "' must be rejected, but parsed as '" + parsed + "'");
        } catch (ExprParser.SyntaxException e) {
            check(helper, e.getMessage().contains(expectedPart), "'" + rule + "' -> '" + e.getMessage() + "', expected to mention '" + expectedPart + "'");
        }
    }
}
