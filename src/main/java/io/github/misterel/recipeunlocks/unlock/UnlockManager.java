package io.github.misterel.recipeunlocks.unlock;

import com.mojang.logging.LogUtils;
import io.github.misterel.recipeunlocks.config.ConfigLoader;
import io.github.misterel.recipeunlocks.config.ModConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;

/** The mod's state and rules: which recipes are locked for whom, and how conditions unlock them. */
public final class UnlockManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile ModConfig config = ModConfig.DISABLED;
    private static UnlockIndex index;
    private static ModConfig indexedConfig;
    private static Object indexedRecipes;
    private static Object indexedAdvancements;

    private UnlockManager() {}

    public static ModConfig config() {
        return config;
    }

    public static void setConfig(ModConfig newConfig) {
        config = newConfig;
    }

    public static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve(ConfigLoader.FILE_NAME);
    }

    /**
     * What reading the config file led to.
     *
     * @param applied  false when the file could not be read at all and was ignored
     * @param problems what to tell the admin; empty when everything is fine
     */
    public record LoadResult(boolean applied, List<String> problems) {}

    /**
     * Reads the config file. A file that cannot be read keeps the config that is in effect; on
     * server start that is the default one: everything locked, no rules.
     */
    public static LoadResult loadConfig(MinecraftServer server, boolean serverStart) {
        try {
            config = ConfigLoader.load(configFile());
        } catch (ConfigLoader.UnreadableException e) {
            LOGGER.error("[Recipe Unlocks] {}", e.getMessage());
            if (serverStart) {
                LOGGER.error("[Recipe Unlocks] the config file is ignored: all recipes are locked and no rules are active until it is fixed");
                config = ConfigLoader.DEFAULTS;
            }
            return new LoadResult(false, List.of(e.getMessage()));
        }
        List<String> problems = new ArrayList<>(config.problems());
        if (!serverStart) {
            // On server start the world is not loaded yet; the rules are checked when it is.
            problems.addAll(index(server).problems());
        }
        return new LoadResult(true, problems);
    }

    /** Forgets everything tied to a server that has stopped. */
    public static void clear() {
        config = ModConfig.DISABLED;
        index = null;
        indexedConfig = null;
        indexedRecipes = null;
        indexedAdvancements = null;
    }

    public static UnlockIndex index(MinecraftServer server) {
        ModConfig current = config;
        Object recipes = server.getRecipeManager();
        Object advancements = server.getAdvancements();
        if (index == null || indexedConfig != current || indexedRecipes != recipes || indexedAdvancements != advancements) {
            index = UnlockIndex.build(current, server);
            indexedConfig = current;
            indexedRecipes = recipes;
            indexedAdvancements = advancements;
            report(current, index);
            // Rules or recipes changed: somebody may already have fulfilled a new rule.
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                evaluateAll(player);
            }
        }
        return index;
    }

    private static void report(ModConfig current, UnlockIndex built) {
        current.problems().forEach(problem -> LOGGER.warn("[Recipe Unlocks] {}", problem));
        built.problems().forEach(problem -> LOGGER.warn("[Recipe Unlocks] {}", problem));
        if (current.lockAll()) {
            LOGGER.info("[Recipe Unlocks] recipes are locked; unlock rules: {}; recipes nothing can unlock: {} ({})",
                    built.entries().size(), built.recipesWithoutUnlock(), current.lockWithoutUnlock() ? "locked" : "left free");
        } else {
            LOGGER.info("[Recipe Unlocks] lock_all_recipes is off: crafting is not restricted");
        }
    }

    /** Whether the mod restricts this recipe at all, for anybody. */
    public static boolean isLockable(MinecraftServer server, RecipeHolder<?> recipe) {
        ModConfig current = config;
        if (!current.lockAll() || recipe.value().isSpecial()) {
            return false;
        }
        return current.lockWithoutUnlock() || index(server).unlockable(recipe.id());
    }

    /** Whether this player may not craft with this recipe yet. */
    public static boolean isLocked(ServerPlayer player, RecipeHolder<?> recipe) {
        if (!config.lockAll() || player instanceof FakePlayer || player.isCreative()) {
            return false;
        }
        return !player.getRecipeBook().contains(recipe.id()) && isLockable(player.level().getServer(), recipe);
    }

    private static boolean tracks(ServerPlayer player) {
        return config.templatesActive() && !(player instanceof FakePlayer) && !player.isCreative() && !player.isSpectator();
    }

    /** A player did {@code trigger} on {@code value} (a block, an item, an entity type...). */
    public static void count(ServerPlayer player, Trigger trigger, Object value, int amount) {
        if (tracks(player)) {
            add(player, index(player.level().getServer()).counters(trigger), value, amount);
        }
    }

    /** The game raised one of the player's statistics. */
    public static void countStat(ServerPlayer player, Stat<?> stat, int amount) {
        if (tracks(player)) {
            add(player, index(player.level().getServer()).counters(stat.getType()), stat.getValue(), amount);
        }
    }

    private static void add(ServerPlayer player, List<UnlockIndex.Counter> counters, Object value, int amount) {
        if (counters.isEmpty() || amount <= 0) {
            return;
        }
        ProgressData data = null;
        ProgressData.PlayerProgress progress = null;
        for (UnlockIndex.Counter counter : counters) {
            if (!counter.matches(value)) {
                continue;
            }
            if (progress == null) {
                data = ProgressData.get(player.level().getServer());
                progress = data.of(player.getUUID());
            }
            int before = progress.counters.getInt(counter.key());
            if (before >= counter.cap()) {
                continue;
            }
            progress.counters.put(counter.key(), (int) Math.min((long) before + amount, counter.cap()));
            data.setDirty();
            for (UnlockIndex.Entry entry : counter.entries()) {
                tryUnlock(player, entry, progress, data);
            }
        }
    }

    /** Checks every rule for the player: used on login and after the rules change. */
    public static void evaluateAll(ServerPlayer player) {
        if (!config.templatesActive() || player instanceof FakePlayer) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        List<UnlockIndex.Entry> entries = index(server).entries();
        if (entries.isEmpty()) {
            return;
        }
        ProgressData data = ProgressData.get(server);
        ProgressData.PlayerProgress progress = data.peek(player.getUUID());
        if (progress != null) {
            for (UnlockIndex.Entry entry : entries) {
                tryUnlock(player, entry, progress, data);
            }
        }
    }

    private static void tryUnlock(ServerPlayer player, UnlockIndex.Entry entry, ProgressData.PlayerProgress progress, ProgressData data) {
        List<RecipeHolder<?>> missing = new ArrayList<>();
        for (RecipeHolder<?> recipe : entry.recipes()) {
            if (!player.getRecipeBook().contains(recipe.id())) {
                missing.add(recipe);
            }
        }
        if (missing.isEmpty() || !entry.expr().test(counterOf(progress))) {
            return;
        }
        player.awardRecipes(missing);
        for (RecipeHolder<?> recipe : missing) {
            progress.awarded.add(recipe.id().identifier());
        }
        data.setDirty();
        LOGGER.info("[Recipe Unlocks] {} unlocked '{}' ({} recipes)", player.getGameProfile().name(), entry.output(), missing.size());
    }

    private static ToIntFunction<Condition> counterOf(ProgressData.PlayerProgress progress) {
        return condition -> progress.counters.getInt(condition.counterKey());
    }

    /**
     * Clears the player's counters and takes back the recipes this mod gave. Giving a recipe also
     * ticks the "has the recipe" criterion of its vanilla advancement; that is undone too, or the
     * completed advancement would never unlock the recipe the vanilla way again.
     *
     * @return how many recipes were taken back
     */
    public static int reset(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ProgressData.PlayerProgress removed = ProgressData.get(server).remove(player.getUUID());
        if (removed == null || removed.awarded.isEmpty()) {
            return 0;
        }
        Set<ResourceKey<Recipe<?>>> keys = new HashSet<>();
        List<RecipeHolder<?>> revoke = new ArrayList<>();
        for (Identifier id : removed.awarded) {
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
            keys.add(key);
            server.getRecipeManager().byKey(key).ifPresent(revoke::add);
        }
        player.resetRecipes(revoke);
        for (AdvancementHolder advancement : server.getAdvancements().getAllAdvancements()) {
            if (advancement.value().rewards().recipes().stream().anyMatch(keys::contains)) {
                // Only the "has the recipe" criteria: those are what giving the recipe had ticked off.
                advancement.value().criteria().forEach((name, criterion) -> {
                    if (criterion.trigger() == CriteriaTriggers.RECIPE_UNLOCKED) {
                        player.getAdvancements().revoke(advancement, name);
                    }
                });
            }
        }
        // A result that is already on screen would otherwise be taken and bring the recipe back.
        player.closeContainer();
        player.inventoryMenu.slotsChanged(player.inventoryMenu.getCraftSlots());
        return revoke.size();
    }

    /** One line per rule: unlocked, or the rule with the player's numbers filled in. */
    public static List<String> describeProgress(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ProgressData.PlayerProgress stored = ProgressData.get(server).peek(player.getUUID());
        ProgressData.PlayerProgress progress = stored != null ? stored : new ProgressData.PlayerProgress();
        List<String> lines = new ArrayList<>();
        for (UnlockIndex.Entry entry : index(server).entries()) {
            boolean unlocked = entry.recipes().stream().allMatch(recipe -> player.getRecipeBook().contains(recipe.id()));
            lines.add(entry.output() + ": " + (unlocked ? "открыт" : entry.expr().describe(counterOf(progress))));
        }
        return lines;
    }
}
