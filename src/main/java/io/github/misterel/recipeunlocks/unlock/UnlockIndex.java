package io.github.misterel.recipeunlocks.unlock;

import io.github.misterel.recipeunlocks.config.ModConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatType;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * The config rules resolved against the recipes and advancements the server has loaded right now.
 * Rebuilt whenever the config, the recipes or the advancements change.
 */
public final class UnlockIndex {
    /** One rule with the recipes it unlocks. */
    public record Entry(String output, Expr expr, List<RecipeHolder<?>> recipes) {}

    /** One per-player number: how many times a player did {@code trigger} on {@code target}. */
    public static final class Counter {
        private final String key;
        private final Trigger trigger;
        private final Target target;
        private final List<Entry> entries = new ArrayList<>();
        private int cap;

        private Counter(Condition condition) {
            this.key = condition.counterKey();
            this.trigger = condition.trigger();
            this.target = condition.target();
        }

        public String key() {
            return this.key;
        }

        /** Counting stops here: no rule needs more. */
        public int cap() {
            return this.cap;
        }

        public List<Entry> entries() {
            return this.entries;
        }

        public boolean matches(Object value) {
            return this.trigger.matches(this.target, value);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<Trigger, List<Counter>> countersByTrigger = new IdentityHashMap<>();
    private final Map<StatType<?>, List<Counter>> countersByStat = new IdentityHashMap<>();
    private final Set<ResourceKey<Recipe<?>>> unlockedByAdvancements = new HashSet<>();
    private final Set<ResourceKey<Recipe<?>>> ruled = new HashSet<>();
    private final List<String> problems = new ArrayList<>();
    private int recipesWithoutUnlock;

    private UnlockIndex() {}

    public static UnlockIndex build(ModConfig config, MinecraftServer server) {
        UnlockIndex index = new UnlockIndex();
        RecipeManager recipes = server.getRecipeManager();

        for (AdvancementHolder advancement : server.getAdvancements().getAllAdvancements()) {
            index.unlockedByAdvancements.addAll(advancement.value().rewards().recipes());
        }

        if (config.rulesActive()) {
            Map<Item, Set<RecipeHolder<?>>> byResult = recipesByResult(server);
            Map<String, Counter> counters = new LinkedHashMap<>();
            for (ModConfig.Rule rule : config.rules()) {
                List<RecipeHolder<?>> unlocked = index.resolveOutput(rule.output(), byResult, recipes);
                if (unlocked.isEmpty()) {
                    continue;
                }
                Entry entry = new Entry(rule.output(), rule.expr(), unlocked);
                index.entries.add(entry);
                unlocked.forEach(recipe -> index.ruled.add(recipe.id()));
                rule.expr().forEachCondition(condition -> {
                    Counter counter = counters.computeIfAbsent(condition.counterKey(), key -> index.newCounter(rule.output(), condition));
                    counter.cap = Math.max(counter.cap, condition.count());
                    if (!counter.entries.contains(entry)) {
                        counter.entries.add(entry);
                    }
                });
            }
        }

        for (RecipeHolder<?> recipe : recipes.getRecipes()) {
            if (!recipe.value().isSpecial() && !index.ruled.contains(recipe.id()) && !index.unlockedByAdvancements.contains(recipe.id())) {
                index.recipesWithoutUnlock++;
            }
        }
        return index;
    }

    /** Result item -> recipes, taken from what every recipe shows in the recipe book. */
    private static Map<Item, Set<RecipeHolder<?>>> recipesByResult(MinecraftServer server) {
        Map<Item, Set<RecipeHolder<?>>> byResult = new HashMap<>();
        // Built from the server, not from a level: the rules may be resolved before the world is loaded.
        ContextMap context = new ContextMap.Builder()
                .withParameter(SlotDisplayContext.FUEL_VALUES, server.fuelValues())
                .withParameter(SlotDisplayContext.REGISTRIES, server.registryAccess())
                .create(SlotDisplayContext.CONTEXT);
        for (RecipeHolder<?> recipe : server.getRecipeManager().getRecipes()) {
            if (recipe.value().isSpecial()) {
                continue;
            }
            try {
                for (RecipeDisplay display : recipe.value().display()) {
                    for (ItemStack result : display.result().resolveForStacks(context)) {
                        if (!result.isEmpty()) {
                            byResult.computeIfAbsent(result.getItem(), item -> new LinkedHashSet<>()).add(recipe);
                        }
                    }
                }
            } catch (RuntimeException e) {
                // A recipe from another mod that cannot describe itself; it can still be listed by recipe id.
            }
        }
        return byResult;
    }

    private Counter newCounter(String output, Condition condition) {
        Counter counter = new Counter(condition);
        Trigger trigger = condition.trigger();
        if (trigger.statType() != null) {
            this.countersByStat.computeIfAbsent(trigger.statType(), type -> new ArrayList<>()).add(counter);
        } else {
            this.countersByTrigger.computeIfAbsent(trigger, t -> new ArrayList<>()).add(counter);
        }
        if (!trigger.targetExists(condition.target())) {
            this.problems.add("'" + output + "': цель '" + condition.target() + "' не найдена среди " + trigger.targetKind()
                    + ", условие '" + condition.counterKey() + "' никогда не выполнится");
        }
        return counter;
    }

    private List<RecipeHolder<?>> resolveOutput(String output, Map<Item, Set<RecipeHolder<?>>> byResult, RecipeManager recipes) {
        Target target = Target.parse(output);
        if (target == null || target.kind() == Target.Kind.ANY) {
            this.problems.add("'" + output + "': слева нужен id предмета, #тег предметов или id рецепта");
            return List.of();
        }

        Set<RecipeHolder<?>> found = new LinkedHashSet<>();
        if (target.kind() == Target.Kind.TAG) {
            Optional<HolderSet.Named<Item>> tag = BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, target.id()));
            if (tag.isEmpty()) {
                this.problems.add("'" + output + "': нет такого тега предметов");
                return List.of();
            }
            tag.get().forEach(item -> found.addAll(byResult.getOrDefault(item.value(), Set.of())));
        } else {
            boolean isItem = BuiltInRegistries.ITEM.containsKey(target.id());
            if (isItem) {
                found.addAll(byResult.getOrDefault(BuiltInRegistries.ITEM.getValue(target.id()), Set.of()));
            }
            if (found.isEmpty()) {
                Optional<RecipeHolder<?>> byId = recipes.byKey(ResourceKey.create(Registries.RECIPE, target.id()));
                if (byId.isPresent() && !byId.get().value().isSpecial()) {
                    found.add(byId.get());
                } else if (!isItem) {
                    this.problems.add("'" + output + "': нет ни предмета, ни рецепта с таким id");
                    return List.of();
                }
            }
        }

        if (found.isEmpty()) {
            this.problems.add("'" + output + "': не найдено рецептов с таким результатом (для рецептов из модов можно указать id рецепта)");
        }
        return List.copyOf(found);
    }

    public List<Entry> entries() {
        return this.entries;
    }

    public List<Counter> counters(Trigger trigger) {
        return this.countersByTrigger.getOrDefault(trigger, List.of());
    }

    public List<Counter> counters(StatType<?> statType) {
        return this.countersByStat.getOrDefault(statType, List.of());
    }

    /** True when some advancement of the game gives this recipe. */
    public boolean unlockedByAdvancements(ResourceKey<Recipe<?>> recipe) {
        return this.unlockedByAdvancements.contains(recipe);
    }

    /** True when a config rule unlocks this recipe; then the rule is the only way to get it. */
    public boolean ruled(ResourceKey<Recipe<?>> recipe) {
        return this.ruled.contains(recipe);
    }

    public List<String> problems() {
        return this.problems;
    }

    public int recipesWithoutUnlock() {
        return this.recipesWithoutUnlock;
    }
}
