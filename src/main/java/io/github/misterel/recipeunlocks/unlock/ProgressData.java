package io.github.misterel.recipeunlocks.unlock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.misterel.recipeunlocks.RecipeUnlocks;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/** Per-player counters, stored with the world in {@code data/recipe_unlocks/progress.dat}. */
public final class ProgressData extends SavedData {
    public static final class PlayerProgress {
        private static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of()).forGetter(p -> p.counters),
                        Identifier.CODEC.listOf().optionalFieldOf("awarded", List.of()).forGetter(p -> List.copyOf(p.awarded)),
                        Identifier.CODEC.listOf().optionalFieldOf("withheld", List.of()).forGetter(p -> List.copyOf(p.withheld)))
                .apply(i, PlayerProgress::new));

        /** Counter key -> how many times the player did it. */
        public final Object2IntOpenHashMap<String> counters = new Object2IntOpenHashMap<>();
        /** Recipes this mod added to the player's recipe book, so that a reset can take them back. */
        public final Set<Identifier> awarded = new LinkedHashSet<>();
        /** Recipes the game wanted to give while a rule stood in the way; they are handed over if the rule goes away. */
        public final Set<Identifier> withheld = new LinkedHashSet<>();

        public PlayerProgress() {}

        private PlayerProgress(Map<String, Integer> counters, List<Identifier> awarded, List<Identifier> withheld) {
            this.counters.putAll(counters);
            this.awarded.addAll(awarded);
            this.withheld.addAll(withheld);
        }
    }

    public static final Codec<ProgressData> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerProgress.CODEC).fieldOf("players").forGetter(d -> d.players))
            .apply(i, ProgressData::new));

    private static final SavedDataType<ProgressData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(RecipeUnlocks.MOD_ID, "progress"), ProgressData::new, CODEC);

    private final Map<UUID, PlayerProgress> players = new HashMap<>();

    private ProgressData() {}

    private ProgressData(Map<UUID, PlayerProgress> players) {
        this.players.putAll(players);
    }

    public static ProgressData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public PlayerProgress of(UUID player) {
        return this.players.computeIfAbsent(player, id -> new PlayerProgress());
    }

    public @Nullable PlayerProgress peek(UUID player) {
        return this.players.get(player);
    }
}
