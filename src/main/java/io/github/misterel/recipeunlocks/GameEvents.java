package io.github.misterel.recipeunlocks;

import com.mojang.logging.LogUtils;
import io.github.misterel.recipeunlocks.unlock.Trigger;
import io.github.misterel.recipeunlocks.unlock.UnlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.StatAwardEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Turns game events into condition counters.
 *
 * <p>Listeners run last and skip cancelled events, so an action another mod prevented is not counted.
 */
public final class GameEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GameEvents() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        // Problems are written to the log by UnlockManager.
        UnlockManager.loadConfig(event.getServer(), true);
        LOGGER.info("[Recipe Unlocks] config: {}", UnlockManager.configFile());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        UnlockManager.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UnlockManager.evaluateAll(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            UnlockManager.count(player, Trigger.BREAK, event.getState(), 1);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Hooks.rememberCrafterOwner(event.getLevel().getBlockEntity(event.getPos()), player);
            UnlockManager.count(player, Trigger.PLACE, event.getPlacedBlock(), 1);
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // The server learns about a jump before it moves the player, so this is still the block jumped from.
            BlockPos standingOn = player.mainSupportingBlockPos.orElseGet(player::getOnPos);
            UnlockManager.count(player, Trigger.JUMP, player.level().getBlockState(standingOn), 1);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHit(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(event.getTarget() instanceof Player)) {
            UnlockManager.count(player, Trigger.HIT, event.getTarget().getType(), 1);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && !(event.getEntity() instanceof Player)) {
            UnlockManager.count(player, Trigger.KILL, event.getEntity().getType(), 1);
        }
    }

    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UnlockManager.count(player, Trigger.EAT, event.getItem().getItem(), 1);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onStat(StatAwardEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UnlockManager.countStat(player, event.getStat(), event.getValue());
        }
    }
}
