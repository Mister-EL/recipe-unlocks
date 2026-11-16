package io.github.misterel.recipeunlocks.unlock;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A condition template: the player action that is counted.
 *
 * <p>A few templates are driven by game events, three by where the player is. Every other template
 * is a statistic type taken from the game registry, so statistics added by other mods work without
 * any code here.
 */
public final class Trigger {
    public static final Trigger BREAK = new Trigger("break", BuiltInRegistries.BLOCK, null, null);
    public static final Trigger PLACE = new Trigger("place", BuiltInRegistries.BLOCK, null, null);
    public static final Trigger JUMP = new Trigger("jump", BuiltInRegistries.BLOCK, null, null);
    public static final Trigger HIT = new Trigger("hit", BuiltInRegistries.ENTITY_TYPE, null, null);
    public static final Trigger KILL = new Trigger("kill", BuiltInRegistries.ENTITY_TYPE, null, null);
    public static final Trigger EAT = new Trigger("eat", BuiltInRegistries.ITEM, null, null);
    /** Entering a dimension; the value is the dimension's id. */
    public static final Trigger DIMENSION = new Trigger("dimension", null, Registries.DIMENSION, null);
    /** Entering a biome; the value is the biome's holder. */
    public static final Trigger BIOME = new Trigger("biome", null, Registries.BIOME, null);
    /** Stepping inside a generated structure; the value is the structure's holder. */
    public static final Trigger STRUCTURE = new Trigger("structure", null, Registries.STRUCTURE, null);

    private static final Map<String, Trigger> EVENTS = Map.of(
            BREAK.name, BREAK, PLACE.name, PLACE, JUMP.name, JUMP, HIT.name, HIT, KILL.name, KILL, EAT.name, EAT,
            DIMENSION.name, DIMENSION, BIOME.name, BIOME, STRUCTURE.name, STRUCTURE);

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
    /** The game registry of the targets, for templates whose targets exist in every world. */
    private final @Nullable Registry<?> registry;
    /** The key of the targets' registry, for templates whose targets come with the world's data. */
    private final @Nullable ResourceKey<? extends Registry<?>> worldRegistry;
    private final @Nullable StatType<?> statType;

    private Trigger(String name, @Nullable Registry<?> registry, @Nullable ResourceKey<? extends Registry<?>> worldRegistry, @Nullable StatType<?> statType) {
        this.name = name;
        this.registry = registry;
        this.worldRegistry = worldRegistry;
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
            return new Trigger(name, t.getRegistry(), null, t);
        });
    }

    /** Every template name that can be written in the config. */
    public static String knownNames() {
        StringBuilder names = new StringBuilder(
                "break, place, jump, hit, kill, eat, dimension, biome, structure, pickup, drop, use, craft, mine, stat");
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

    /** Where the target is looked up, worded for the "not found among ..." message. */
    public String targetKind(MinecraftServer server) {
        if (this == DIMENSION) {
            return "измерений (есть: " + server.levelKeys().stream().map(key -> key.identifier().toString()).sorted().collect(Collectors.joining(", ")) + ")";
        } else if (this == BIOME) {
            return "биомов";
        } else if (this == STRUCTURE) {
            return "структур";
        } else if (this.registry == BuiltInRegistries.BLOCK) {
            return "блоков и предметов";
        } else if (this.registry == BuiltInRegistries.ITEM) {
            return "предметов";
        } else if (this.registry == BuiltInRegistries.ENTITY_TYPE) {
            return "существ";
        } else if (this.registry == BuiltInRegistries.CUSTOM_STAT) {
            return "счётчиков статистики";
        }
        return String.valueOf(this.registry == null ? this.worldRegistry : this.registry.key().identifier());
    }

    public boolean targetExists(Target target, MinecraftServer server) {
        if (this == DIMENSION) {
            // Dimensions have no tags of their own.
            return target.kind() == Target.Kind.ANY
                    || target.kind() == Target.Kind.ID && server.levelKeys().stream().anyMatch(key -> key.identifier().equals(target.id()));
        }
        if (this.worldRegistry != null) {
            return server.registryAccess().lookup(this.worldRegistry).map(target::existsIn).orElse(false);
        }
        if (target.existsIn(this.registry)) {
            return true;
        }
        // Blocks may be written by their item id: "torch" also covers the wall torch.
        return this.registry == BuiltInRegistries.BLOCK && target.existsIn(BuiltInRegistries.ITEM);
    }

    @SuppressWarnings("unchecked")
    public boolean matches(Target target, Object value) {
        if (this == DIMENSION) {
            return target.kind() == Target.Kind.ANY || target.kind() == Target.Kind.ID && target.id().equals(value);
        }
        if (value instanceof Holder<?> holder && this.worldRegistry != null) {
            return target.matches((Holder<Object>) holder, (ResourceKey<Registry<Object>>) this.worldRegistry);
        }
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
