package io.github.misterel.recipeunlocks.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.misterel.recipeunlocks.unlock.UnlockManager;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /unlocks reload | progress <player> | reset <player>} for operators. */
public final class UnlocksCommand {
    private static final int MAX_LINES = 40;

    private UnlocksCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("unlocks")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("reload").executes(UnlocksCommand::reload))
                .then(Commands.literal("progress")
                        .then(Commands.argument("player", EntityArgument.player()).executes(UnlocksCommand::progress)))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player()).executes(UnlocksCommand::reset))));
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UnlockManager.LoadResult result = UnlockManager.loadConfig(source.getServer(), false);
        if (!result.applied()) {
            source.sendFailure(Component.literal("Recipe Unlocks: конфиг не применён, действует прежний. " + String.join("; ", result.problems())));
            return 0;
        }
        int rules = UnlockManager.index(source.getServer()).entries().size();
        List<String> problems = result.problems();
        source.sendSuccess(() -> Component.literal("Recipe Unlocks: конфиг применён, правил: " + rules + ", замечаний: " + problems.size()), true);
        say(source, problems);
        return 1;
    }

    private static int progress(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        List<String> lines = UnlockManager.describeProgress(player);
        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Recipe Unlocks: правил в конфиге нет"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Recipe Unlocks, " + player.getGameProfile().name() + ":"), false);
            say(source, lines);
        }
        return lines.size();
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int revoked = UnlockManager.reset(player);
        source.sendSuccess(() -> Component.literal("Recipe Unlocks: счётчики " + player.getGameProfile().name()
                + " сброшены, рецептов закрыто обратно: " + revoked), true);
        return 1;
    }

    private static void say(CommandSourceStack source, List<String> lines) {
        lines.stream().limit(MAX_LINES).forEach(line -> source.sendSuccess(() -> Component.literal("  " + line), false));
        if (lines.size() > MAX_LINES) {
            source.sendSuccess(() -> Component.literal("  … и ещё " + (lines.size() - MAX_LINES)), false);
        }
    }
}
