package io.github.misterel.recipeunlocks;

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
            if (previous == null) {
                ACTING.remove();
            } else {
                ACTING.set(previous);
            }
        }
    }
}
