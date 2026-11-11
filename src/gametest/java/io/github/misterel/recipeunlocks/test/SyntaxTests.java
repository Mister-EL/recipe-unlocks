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
            failsWith(helper, "break 3 stone", "сначала цель, потом количество");
            failsWith(helper, "jump 5", "сначала цель, потом количество");
        });
    }

    static void configFile(GameTestHelper helper) {
        Sim.run(helper, () -> {
            ModConfig empty = ConfigLoader.parse("");
            check(helper, empty.lockAll() && empty.templates() && !empty.lockWithoutUnlock() && empty.lockCrafter() && empty.blockModdedStations(),
                    "defaults of a file without settings");
            check(helper, empty.rules().isEmpty() && empty.problems().isEmpty(), "an empty file has no rules and no problems");

            // Windows line endings, a byte order mark, tabs: what a file saved by Notepad looks like.
            ModConfig config = ConfigLoader.parse("\uFEFF" + """
                    lock_all_recipes = false
                    template_unlocks = FALSE   # off for now
                    lock_recipes_without_unlock = true
                    \tlock_crafter=no
                    block_modded_stations = "false"

                    [ Unlocks ]   # rules
                    "minecraft:torch" = "break stone 3"
                    minecraft:torch_2 = break stone 3 & (eat apple | drop stick)   # comment
                    stick=jump #minecraft:logs 2
                      'minecraft:bread'   =   'eat apple'
                    "#minecraft:planks" = jump * 2 и hit zombie
                    some.mod:thing.v2 = stat jump
                    # minecraft:ladder = break stone
                    """.replace("\n", "\r\n"));
            check(helper, config.problems().isEmpty(), "no problems expected, got " + config.problems());
            check(helper, !config.lockAll() && !config.templates() && config.lockWithoutUnlock() && !config.lockCrafter() && !config.blockModdedStations(),
                    "every setting is read");
            check(helper, config.rules().stream().map(ModConfig.Rule::output).toList()
                            .equals(List.of("minecraft:torch", "minecraft:torch_2", "stick", "minecraft:bread", "#minecraft:planks", "some.mod:thing.v2")),
                    "rules keep the order of the file: " + config.rules());
            check(helper, rule(config, 1).equals("break minecraft:stone 3 & (eat minecraft:apple 1 | dropped minecraft:stick 1)"), rule(config, 1));
            check(helper, rule(config, 2).equals("jump #minecraft:logs 2"), "a tag is not a comment: " + rule(config, 2));
            check(helper, rule(config, 4).equals("jump * 2 & hit minecraft:zombie 1"), rule(config, 4));

            ModConfig shipped = ConfigLoader.parse(defaultFile());
            check(helper, shipped.problems().isEmpty() && shipped.rules().isEmpty() && shipped.lockAll() && shipped.templates(),
                    "the shipped default file is valid and has no active rules: " + shipped.problems());
            // Every commented example of the shipped file must be a valid rule.
            String withExamples = defaultFile().lines()
                    .dropWhile(line -> !line.equals("[unlocks]"))
                    .map(line -> line.startsWith("# ") ? line.substring(2) : line)
                    .collect(java.util.stream.Collectors.joining("\n"));
            ModConfig examples = ConfigLoader.parse(withExamples);
            check(helper, examples.problems().isEmpty() && examples.rules().size() == 5, "shipped examples: " + examples.problems() + examples.rules());

            // A mistake costs one line and is reported with its number; the rest of the file works.
            ModConfig broken = ConfigLoader.parse("""
                    lock_all_recipes = maybe
                    surprise = true
                    torch = break stone
                    lock_crafter = false
                    lock_crafter = true
                    [unlocks] trailing
                    minecraft:torch = fly stone
                    minecraft:stick
                    bow = kill skeleton
                    bow = kill zombie
                    "bread = eat apple
                    cake = "eat apple" & jump *
                    chest = [ "jump *", "hit zombie" ]
                    template_unlocks = false
                    ladder =
                    jump 5 = jump 5
                    [other]
                    furnace = break stone
                    [unlocks]
                    barrel = break stone 2
                    """);
            check(helper, broken.lockAll() && broken.templates() && broken.lockCrafter(), "wrong or misplaced settings do not change anything");
            check(helper, broken.rules().stream().map(ModConfig.Rule::output).toList().equals(List.of("bow", "barrel")),
                    "good rules survive bad neighbours: " + broken.rules());
            check(helper, rule(broken, 0).equals("kill minecraft:skeleton 1"), "the first of two rules for one item wins");
            String[] expected = {
                "строка 1: 'lock_all_recipes' должно быть true или false", "строка 2: неизвестная настройка 'surprise'",
                "строка 3: неизвестная настройка 'torch' (правила пишутся ниже", "строка 5: настройка 'lock_crafter' задана повторно",
                "строка 6: заголовок раздела", "строка 7: 'minecraft:torch': символ 1: неизвестный шаблон 'fly'",
                "строка 8: ожидалось 'что = значение'", "строка 10: для 'bow' уже есть правило в строке 9",
                "строка 11: не закрыта кавычка", "строка 12: лишний текст после закрывающей кавычки",
                "строка 13: 'chest': списки не поддерживаются", "строка 14: настройка 'template_unlocks' должна стоять выше",
                "строка 15: 'ladder': пустое", "строка 16: 'jump 5': символ 6: сначала цель, потом количество",
                "строка 17: неизвестный раздел [other]",
            };
            check(helper, broken.problems().size() == expected.length, expected.length + " problems expected: " + broken.problems());
            for (int i = 0; i < Math.min(expected.length, broken.problems().size()); i++) {
                check(helper, broken.problems().get(i).startsWith(expected[i]), "problem " + i + ": '" + broken.problems().get(i) + "', expected '" + expected[i] + "'");
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

    private static String rule(ModConfig config, int index) {
        return config.rules().get(index).expr().describe(null);
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
