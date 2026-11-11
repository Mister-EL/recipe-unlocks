package io.github.misterel.recipeunlocks;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The player whose action the server is handling right now, if any.
 *
 * <p>Set while a container click or a block interaction of that player is processed. It lets the
 * mod tell "a player is crafting" from "a machine is working", including inside other mods' code.
 */
public final class PlayerContext {
    private static final ThreadLocal<ServerPlayer> ACTING = new ThreadLocal<>();

    private PlayerContext() {}

    public static @Nullable ServerPlayer get() {
        return ACTING.get();
    }

    public static void run(ServerPlayer player, Runnable action) {
        ServerPlayer previous = ACTING.get();
        ACTING.set(player);
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    /** Runs code whose result is shared between players, so it must not depend on who is acting. */
    public static <T> T callWithoutPlayer(Supplier<T> action) {
        ServerPlayer previous = ACTING.get();
        if (previous == null) {
            return action.get();
        }
        ACTING.remove();
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(@Nullable ServerPlayer previous) {
        if (previous == null) {
            ACTING.remove();
        } else {
            ACTING.set(previous);
        }
    }
}
