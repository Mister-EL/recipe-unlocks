package io.github.misterel.recipeunlocks.unlock;

import java.util.Locale;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

/** What a condition counts: one registry entry, a tag, or anything ({@code *}). */
public record Target(Kind kind, @Nullable Identifier id) {
    public enum Kind { ANY, ID, TAG }

    public static final Target ANY = new Target(Kind.ANY, null);

    /** Parses {@code *}, {@code #namespace:tag} or {@code namespace:id}; the namespace defaults to minecraft. */
    public static @Nullable Target parse(String word) {
        if (word.equals("*")) {
            return ANY;
        }
        boolean tag = word.startsWith("#");
        String raw = (tag ? word.substring(1) : word).toLowerCase(Locale.ROOT);
        Identifier id = raw.isEmpty() ? null : Identifier.tryParse(raw);
        return id == null ? null : new Target(tag ? Kind.TAG : Kind.ID, id);
    }

    public <T> boolean matches(Registry<T> registry, T value) {
        return switch (this.kind) {
            case ANY -> true;
            case ID -> this.id.equals(registry.getKey(value));
            case TAG -> registry.wrapAsHolder(value).is(TagKey.create(registry.key(), this.id));
        };
    }

    public <T> boolean existsIn(Registry<T> registry) {
        return switch (this.kind) {
            case ANY -> true;
            case ID -> registry.containsKey(this.id);
            case TAG -> registry.get(TagKey.create(registry.key(), this.id)).isPresent();
        };
    }

    @Override
    public String toString() {
        return switch (this.kind) {
            case ANY -> "*";
            case ID -> this.id.toString();
            case TAG -> "#" + this.id;
        };
    }
}
