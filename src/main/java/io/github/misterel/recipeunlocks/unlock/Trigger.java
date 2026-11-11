package io.github.misterel.recipeunlocks.unlock;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.StatType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A condition template: the player action that is counted.
 *
 * <p>A few templates are driven by game events. Every other template is a statistic type taken
 * from the game registry, so statistics added by other mods work without any code here.
 */
public final class Trigger {
    public static final Trigger BREAK = new Trigger("break", BuiltInRegistries.BLOCK, null);
    public static final Trigger PLACE = new Trigger("place", BuiltInRegistries.BLOCK, null);
    public static final Trigger JUMP = new Trigger("jump", BuiltInRegistries.BLOCK, null);
    public static final Trigger HIT = new Trigger("hit", BuiltInRegistries.ENTITY_TYPE, null);
    public static final Trigger KILL = new Trigger("kill", BuiltInRegistries.ENTITY_TYPE, null);
    public static final Trigger EAT = new Trigger("eat", BuiltInRegistries.ITEM, null);

    private static final Map<String, Trigger> EVENTS = Map.of(
            BREAK.name, BREAK, PLACE.name, PLACE, JUMP.name, JUMP, HIT.name, HIT, KILL.name, KILL, EAT.name, EAT);

    /** Short names for the statistic types people need most often. */
    private static final Map<String, String> STAT_ALIASES = Map.of(
            "pickup", "picked_up",
            "drop", "dropped",
            "use", "used",
            "craft", "crafted",
            "mine", "mined",
            "stat", "custom");

    private static final Map<StatType<?>, Trigger> STATS = new ConcurrentHashMap<>();

    private final String name;
    private final Registry<?> registry;
    private final @Nullable StatType<?> statType;

    private Trigger(String name, Registry<?> registry, @Nullable StatType<?> statType) {
        this.name = name;
        this.registry = registry;
        this.statType = statType;
    }

    public static @Nullable Trigger resolve(String word) {
        String key = word.toLowerCase(Locale.ROOT);
        Trigger event = EVENTS.get(key);
        if (event != null) {
            return event;
        }
        Identifier id = Identifier.tryParse(STAT_ALIASES.getOrDefault(key, key));
        StatType<?> type = id == null ? null : BuiltInRegistries.STAT_TYPE.getOptional(id).orElse(null);
        return type == null ? null : forStat(type);
    }

    public static Trigger forStat(StatType<?> type) {
        return STATS.computeIfAbsent(type, t -> {
            Identifier id = BuiltInRegistries.STAT_TYPE.getKey(t);
            String name = id == null ? "?" : id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? id.getPath() : id.toString();
            return new Trigger(name, t.getRegistry(), t);
        });
    }

    /** Every template name that can be written in the config. */
    public static String knownNames() {
        StringBuilder names = new StringBuilder("break, place, jump, hit, kill, eat, pickup, drop, use, craft, mine, stat");
        for (Identifier id : BuiltInRegistries.STAT_TYPE.keySet()) {
            if (!id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
                names.append(", ").append(id);
            }
        }
        return names.toString();
    }

    public String name() {
        return this.name;
    }

    public @Nullable StatType<?> statType() {
        return this.statType;
    }

    /** Human-readable name of the registry the target is looked up in. */
    public String targetKind() {
        return this.registry.key().identifier().getPath();
    }

    public boolean targetExists(Target target) {
        if (target.existsIn(this.registry)) {
            return true;
        }
        // Blocks may be written by their item id: "torch" also covers the wall torch.
        return this.registry == BuiltInRegistries.BLOCK && target.existsIn(BuiltInRegistries.ITEM);
    }

    @SuppressWarnings("unchecked")
    public boolean matches(Target target, Object value) {
        if (value instanceof BlockState state) {
            value = state.getBlock();
        }
        if (value instanceof Block block) {
            return target.matches(BuiltInRegistries.BLOCK, block)
                    || block.asItem() != Items.AIR && target.matches(BuiltInRegistries.ITEM, block.asItem());
        }
        return target.matches((Registry<Object>) this.registry, value);
    }

    @Override
    public String toString() {
        return this.name;
    }
}
