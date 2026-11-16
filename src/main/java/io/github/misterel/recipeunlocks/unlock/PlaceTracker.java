package io.github.misterel.recipeunlocks.unlock;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Watches where players are for the {@code dimension}, {@code biome} and {@code structure} templates.
 *
 * <p>What counts is entering: a place is counted when the player is in it now and was not at the
 * previous look. A place the player simply is in (after a login, or since before the rule existed)
 * counts once: only while its counter is still zero. So logging in again does not add up.
 */
public final class PlaceTracker {
    /** How often a player's position is looked at, in ticks. */
    public static final int INTERVAL = 20;

    /** One place a player is in: which template sees it and the value that template matches against. */
    public record Place(Trigger trigger, Object value) {}

    /** Per online player: the places they were in at the last look, by a stable name. */
    private static final Map<UUID, Set<String>> LAST = new HashMap<>();

    private PlaceTracker() {}

    public static void tick(ServerPlayer player) {
        if (player.tickCount % INTERVAL == 0) {
            look(player);
        }
    }

    /** Compares where the player is with the previous look and counts what was entered. */
    public static void look(ServerPlayer player) {
        if (!UnlockManager.watchesPlaces(player)) {
            LAST.remove(player.getUUID());
        } else if (player.level().isLoaded(player.blockPosition())) {
            // Never make the server load or generate terrain just to look; try again next second.
            arrive(player, currentPlaces(player));
        }
    }

    /** The player is now in exactly these places; the keys are stable names such as "biome minecraft:desert". */
    public static void arrive(ServerPlayer player, Map<String, Place> now) {
        Set<String> before = LAST.put(player.getUUID(), Set.copyOf(now.keySet()));
        now.forEach((name, place) -> {
            boolean entered = before != null && !before.contains(name);
            UnlockManager.countPlace(player, place, !entered);
        });
    }

    public static Map<String, Place> currentPlaces(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        UnlockIndex index = UnlockManager.index(level.getServer());
        Map<String, Place> places = new HashMap<>();

        Identifier dimension = level.dimension().identifier();
        places.put(Trigger.DIMENSION.name() + " " + dimension, new Place(Trigger.DIMENSION, dimension));

        if (!index.counters(Trigger.BIOME).isEmpty()) {
            Holder<Biome> biome = level.getBiome(pos);
            biome.unwrapKey().ifPresent(key -> places.put(Trigger.BIOME.name() + " " + key.identifier(), new Place(Trigger.BIOME, biome)));
        }

        if (!index.counters(Trigger.STRUCTURE).isEmpty()) {
            Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            // Structures that reach into this chunk; being inside one of their pieces is what counts.
            for (Map.Entry<Structure, LongSet> reaching : level.structureManager().getAllStructuresAt(pos).entrySet()) {
                Structure structure = reaching.getKey();
                Identifier id = structures.getKey(structure);
                Holder<Structure> holder = structures.wrapAsHolder(structure);
                if (id != null
                        && index.counters(Trigger.STRUCTURE).stream().anyMatch(counter -> counter.matches(holder))
                        && insidePiece(level, pos, structure, reaching.getValue())) {
                    places.put(Trigger.STRUCTURE.name() + " " + id, new Place(Trigger.STRUCTURE, holder));
                }
            }
        }
        return places;
    }

    /**
     * Like the game's own "is inside this structure" test, except that a structure whose starting
     * chunk is not loaded is skipped instead of being read from disk.
     */
    private static boolean insidePiece(ServerLevel level, BlockPos pos, Structure structure, LongSet startChunks) {
        for (long packed : startChunks) {
            ChunkPos chunk = ChunkPos.unpack(packed);
            if (level.hasChunk(chunk.x(), chunk.z())) {
                StructureStart start = level.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
                if (start != null && start.isValid() && level.structureManager().structureHasPieceAt(pos, start)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void forget(ServerPlayer player) {
        LAST.remove(player.getUUID());
    }

    public static void clear() {
        LAST.clear();
    }
}
