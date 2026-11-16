package io.github.misterel.recipeunlocks.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.misterel.recipeunlocks.config.ModConfig;
import io.github.misterel.recipeunlocks.unlock.UnlockManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /unlocks reload | progress <player> | reset <player> | give <player> <what> | take <player> <what>} for operators. */
public final class UnlocksCommand {
    private static final int MAX_LINES = 40;
    private static final String WHAT = "what";

    private UnlocksCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("unlocks")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("reload").executes(UnlocksCommand::reload))
                .then(Commands.literal("progress")
                        .then(Commands.argument("player", EntityArgument.player()).executes(UnlocksCommand::progress)))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player()).executes(UnlocksCommand::reset)))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(what().executes(context -> giveOrTake(context, true)))))
                .then(Commands.literal("take")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(what().executes(context -> giveOrTake(context, false))))));
    }

    /**
     * What to give or take: an item id, an item #tag, a recipe id or *. A plain text argument, so
     * that clients without the mod understand the command; the server suggests the item ids.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> what() {
        return Commands.argument(WHAT, StringArgumentType.greedyString()).suggests((context, builder) -> {
            if ("*".startsWith(builder.getRemaining())) {
                builder.suggest("*");
            }
            return SharedSuggestionProvider.suggestResource(UnlockManager.index(context.getSource().getServer()).craftableItems(), builder);
        });
    }

    private static int giveOrTake(CommandContext<CommandSourceStack> context, boolean give) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String what = StringArgumentType.getString(context, WHAT).strip();
        UnlockManager.CommandResult result = give ? UnlockManager.give(player, what) : UnlockManager.take(player, what);
        if (!result.problems().isEmpty()) {
            source.sendFailure(Component.literal("Recipe Unlocks: " + String.join("; ", result.problems())));
            return 0;
        }
        String name = player.getGameProfile().name();
        String message;
        if (give) {
            message = result.recipes() == 0 ? name + " уже знает эти рецепты" : name + ": открыто рецептов: " + result.recipes();
        } else {
            message = (result.recipes() == 0 ? name + " и так не знает этих рецептов" : name + ": закрыто рецептов: " + result.recipes())
                    + "; счётчики правил для них обнулены";
        }
        source.sendSuccess(() -> Component.literal("Recipe Unlocks: " + message + " (" + what + ")"), true);
        return Math.max(result.recipes(), 1);
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UnlockManager.LoadResult result = UnlockManager.loadConfig(source.getServer(), false);
        if (!result.applied()) {
            source.sendFailure(Component.literal("Recipe Unlocks: конфиг не применён, действует прежний. " + String.join("; ", result.problems())));
            return 0;
        }
        int rules = UnlockManager.index(source.getServer()).entries().size();
        List<String> problems = new ArrayList<>(result.problems());
        ModConfig config = UnlockManager.config();
        if (!config.templates() && !config.rules().isEmpty()) {
            problems.add("правила из [unlocks] (" + config.rules().size() + ") сейчас выключены: нужен template_unlocks = true");
        }
        source.sendSuccess(() -> Component.literal("Recipe Unlocks: конфиг применён, правил: " + rules + ", замечаний: " + problems.size()), true);
        say(source, problems);
        return 1;
    }

    private static int progress(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        List<String> lines = UnlockManager.describeProgress(player);
        if (lines.isEmpty()) {
            ModConfig config = UnlockManager.config();
            String why = config.rules().isEmpty() || config.templates() ? "действующих правил нет" : "правила из [unlocks] выключены настройками";
            source.sendSuccess(() -> Component.literal("Recipe Unlocks: " + why), false);
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
