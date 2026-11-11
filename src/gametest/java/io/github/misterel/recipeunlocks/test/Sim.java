package io.github.misterel.recipeunlocks.test;

import com.mojang.authlib.GameProfile;
import io.github.misterel.recipeunlocks.config.ConfigLoader;
import io.github.misterel.recipeunlocks.unlock.UnlockManager;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

/** Helpers that drive a real server the way a connected player would. */
final class Sim {
    private static final AtomicInteger PLAYER_NUMBER = new AtomicInteger();
    private static final Map<ServerPlayer, EmbeddedChannel> CHANNELS = new IdentityHashMap<>();
    private static final List<ServerPlayer> JOINED = new ArrayList<>();

    private Sim() {}

    /** Runs a single-tick test and always logs its players out again. */
    static void run(GameTestHelper helper, Runnable body) {
        try {
            body.run();
        } finally {
            leaveAll();
        }
        helper.succeed();
    }

    static void config(String toml) {
        UnlockManager.setConfig(ConfigLoader.parse(toml));
    }

    static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    /** A real server-side player with a loopback connection, standing at the test's origin. */
    static ServerPlayer join(GameTestHelper helper, GameType mode) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "ru_test_" + PLAYER_NUMBER.incrementAndGet());
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        player.snapTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        CHANNELS.put(player, channel);
        JOINED.add(player);
        return player;
    }

    static void leave(ServerPlayer player) {
        JOINED.remove(player);
        CHANNELS.remove(player);
        player.level().getServer().getPlayerList().remove(player);
    }

    static void leaveAll() {
        new ArrayList<>(JOINED).forEach(Sim::leave);
    }

    /** True if the server has told this player's client about newly unlocked recipes since the last call. */
    static boolean clientWasToldAboutNewRecipes(ServerPlayer player) {
        boolean told = false;
        for (Object message; (message = CHANNELS.get(player).readOutbound()) != null; ) {
            if (message instanceof ClientboundRecipeBookAddPacket add && !add.replace()) {
                told = true;
            } else if (message instanceof ClientboundBundlePacket bundle) {
                for (Object packet : bundle.subPackets()) {
                    told |= packet instanceof ClientboundRecipeBookAddPacket add && !add.replace();
                }
            }
        }
        return told;
    }

    static RecipeHolder<?> recipe(GameTestHelper helper, String id) {
        ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(id));
        return helper.getLevel().getServer().getRecipeManager().byKey(key).orElseThrow(() -> new IllegalStateException("no recipe " + id));
    }

    static boolean knows(GameTestHelper helper, ServerPlayer player, String recipeId) {
        return player.getRecipeBook().contains(recipe(helper, recipeId).id());
    }

    static void unlock(GameTestHelper helper, ServerPlayer player, String recipeId) {
        player.awardRecipes(List.of(recipe(helper, recipeId)));
    }

    /** Puts two planks into the player's own 2x2 grid and returns what the result slot shows. */
    static ItemStack sticksFromInventoryGrid(ServerPlayer player) {
        InventoryMenu menu = player.inventoryMenu;
        menu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS));
        menu.getSlot(3).set(new ItemStack(Items.OAK_PLANKS));
        ItemStack result = menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().copy();
        menu.getSlot(1).set(ItemStack.EMPTY);
        menu.getSlot(3).set(ItemStack.EMPTY);
        return result;
    }

    /** Sends the same packet a client sends for a left click on a slot of the open menu. */
    static void clickSlot(ServerPlayer player, int slot) {
        AbstractContainerMenu menu = player.containerMenu;
        player.connection.handleContainerClick(new ServerboundContainerClickPacket(
                menu.containerId, menu.getStateId(), (short) slot, (byte) 0, ContainerInput.PICKUP, Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
    }

    /** Places {@code block} at the given cell of the test and lets the player break it, as mining does. */
    static void breakBlock(GameTestHelper helper, ServerPlayer player, Block block, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        helper.getLevel().setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        player.gameMode.destroyBlock(pos);
    }

    /** One line of {@code /unlocks progress} for the rule that unlocks {@code output}. */
    static String progressLine(ServerPlayer player, String output) {
        return UnlockManager.describeProgress(player).stream()
                .filter(line -> line.startsWith(output + ": "))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no rule for " + output));
    }
}
