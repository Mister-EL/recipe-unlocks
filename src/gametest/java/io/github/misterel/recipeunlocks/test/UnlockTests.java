package io.github.misterel.recipeunlocks.test;

import static io.github.misterel.recipeunlocks.test.Sim.check;

import io.github.misterel.recipeunlocks.unlock.ProgressData;
import io.github.misterel.recipeunlocks.unlock.UnlockManager;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** Rules from the config unlock recipes. */
final class UnlockTests {
    private static final String STICK_RULE = """
            lock_all_recipes = true
            template_unlocks = true
            [unlocks]
            "minecraft:stick" = "break stone 2 & (jump cobblestone | drop dirt 3)"
            """;
    private static final BlockPos CELL = new BlockPos(1, 1, 0);

    private UnlockTests() {}

    static void ruleUnlocksRecipe(GameTestHelper helper) {
        Sim.run(helper, () -> {
            Sim.config(STICK_RULE);
            ServerLevel level = helper.getLevel();

            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);
            Sim.clientWasToldAboutNewRecipes(player);
            Sim.breakBlock(helper, player, Blocks.DIRT, CELL);
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            check(helper, Sim.progressLine(player, "minecraft:stick")
                            .equals("minecraft:stick: break minecraft:stone 1/2 & (jump minecraft:cobblestone 0/1 | dropped minecraft:dirt 0/3)"),
                    "progress after one stone: " + Sim.progressLine(player, "minecraft:stick"));
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            check(helper, !Sim.knows(helper, player, "stick"), "half of the rule is not enough");
            check(helper, Sim.sticksFromInventoryGrid(player).isEmpty(), "so crafting is still refused");

            jumpOn(helper, player, Blocks.STONE);
            check(helper, !Sim.knows(helper, player, "stick"), "jumping on the wrong block does not count");
            jumpOn(helper, player, Blocks.COBBLESTONE);
            check(helper, Sim.knows(helper, player, "stick"), "the fulfilled rule unlocks the recipe");
            check(helper, Sim.clientWasToldAboutNewRecipes(player), "the client is told, so the recipe book shows the recipe and the toast");
            check(helper, Sim.sticksFromInventoryGrid(player).is(Items.STICK), "and crafting works");
            check(helper, Sim.progressLine(player, "minecraft:stick").equals("minecraft:stick: открыт"), Sim.progressLine(player, "minecraft:stick"));

            // The other branch of the "or", by another player: progress is per player.
            ServerPlayer second = Sim.join(helper, GameType.SURVIVAL);
            check(helper, !Sim.knows(helper, second, "stick"), "the second player starts from zero");
            Sim.breakBlock(helper, second, Blocks.STONE, CELL);
            Sim.breakBlock(helper, second, Blocks.STONE, CELL);
            ItemEntity dropped = second.drop(new ItemStack(Items.DIRT, 2), false, true);
            check(helper, !Sim.knows(helper, second, "stick"), "two of three dirt dropped");
            ItemEntity droppedMore = second.drop(new ItemStack(Items.DIRT, 1), false, true);
            check(helper, Sim.knows(helper, second, "stick"), "the 'or' branch unlocks too");
            dropped.discard();
            droppedMore.discard();
            level.removeBlock(helper.absolutePos(CELL), false);
        });
    }

    static void everyTemplateCounts(GameTestHelper helper) {
        Sim.run(helper, () -> {
            ServerLevel level = helper.getLevel();
            // Statistics the player brought along do not count: only what is gained while the rule exists.
            Sim.config("lock_all_recipes = true\n");
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);
            player.getStats().setValue(player, Stats.CUSTOM.get(Stats.JUMP), 500);
            Sim.config("""
                    [unlocks]
                    "minecraft:stick" = "stat jump 3"
                    "minecraft:torch" = "pickup string 3"
                    "minecraft:ladder" = "hit zombie 2"
                    "minecraft:bow" = "kill zombie"
                    "minecraft:furnace" = "place #minecraft:logs"
                    "minecraft:bread" = "eat apple"
                    "minecraft:chest" = "break torch"
                    "minecraft:barrel" = "use ender_pearl"
                    "minecraft:bucket" = "break * 3"
                    """);
            BlockPos cell = helper.absolutePos(CELL);
            Vec3 at = Vec3.atCenterOf(cell);

            player.awardStat(Stats.JUMP);
            player.awardStat(Stats.JUMP);
            check(helper, !Sim.knows(helper, player, "stick"), "two jumps of three: " + Sim.progressLine(player, "minecraft:stick"));
            player.awardStat(Stats.JUMP);
            check(helper, Sim.knows(helper, player, "stick"), "stat: a game statistic");

            for (int picked = 1; picked <= 3; picked++) {
                check(helper, !Sim.knows(helper, player, "torch"), "picked up " + (picked - 1) + " of three: " + Sim.progressLine(player, "minecraft:torch"));
                ItemEntity string = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STRING));
                string.setNoPickUpDelay();
                level.addFreshEntity(string);
                string.playerTouch(player);
            }
            check(helper, Sim.knows(helper, player, "torch"), "pickup: picking an item up from the ground");

            Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            zombie.snapTo(at.x, at.y, at.z);
            level.addFreshEntity(zombie);
            player.attack(zombie);
            player.attack(zombie);
            check(helper, !Sim.knows(helper, player, "ladder"), "a swing during the hurt cooldown is not a hit: " + Sim.progressLine(player, "minecraft:ladder"));
            zombie.invulnerableTime = 0;
            player.attack(zombie);
            check(helper, Sim.knows(helper, player, "ladder"), "hit: a hit that lands on a mob");
            zombie.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
            check(helper, Sim.knows(helper, player, "bow"), "kill: killing a mob");
            zombie.discard();

            level.setBlock(cell, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            ItemStack log = new ItemStack(Items.OAK_LOG);
            player.setItemInHand(InteractionHand.MAIN_HAND, log);
            player.gameMode.useItemOn(player, level, log, InteractionHand.MAIN_HAND,
                    new BlockHitResult(at.add(0, 0.5, 0), Direction.UP, cell, false));
            check(helper, level.getBlockState(cell.above()).is(Blocks.OAK_LOG), "the log was placed");
            check(helper, Sim.knows(helper, player, "furnace"), "place: placing a block, target given as a tag");
            level.removeBlock(cell.above(), false);

            EventHooks.onItemUseFinish(player, new ItemStack(Items.APPLE), 0, ItemStack.EMPTY);
            check(helper, Sim.knows(helper, player, "bread"), "eat: finishing a food item");

            Sim.breakBlock(helper, player, Blocks.WALL_TORCH, CELL);
            check(helper, Sim.knows(helper, player, "chest"), "break: a block written by its item id covers the wall variant");

            player.awardStat(Stats.ITEM_USED.get(Items.ENDER_PEARL));
            check(helper, Sim.knows(helper, player, "barrel"), "use: using an item");

            check(helper, !Sim.knows(helper, player, "bucket"), "'break *' counted one block so far");
            Sim.breakBlock(helper, player, Blocks.DIRT, CELL);
            Sim.breakBlock(helper, player, Blocks.SAND, CELL);
            check(helper, Sim.knows(helper, player, "bucket"), "break *: any block");
        });
    }

    /** An item with a rule opens by the rule and by nothing else. */
    static void ruleIsTheOnlyWay(GameTestHelper helper) {
        Sim.run(helper, () -> {
            Sim.config(STICK_RULE);
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);

            Sim.obtainPlanks(player);
            check(helper, !Sim.knows(helper, player, "stick"), "getting planks does not unlock a recipe that has a rule");
            Sim.unlock(helper, player, "stick");
            check(helper, !Sim.knows(helper, player, "stick"), "nor does anything else the game does on its own");
            check(helper, Sim.sticksFromInventoryGrid(player).isEmpty(), "so sticks cannot be crafted");

            Sim.obtainPlanks(player);
            Sim.unlock(helper, player, "torch");
            check(helper, Sim.knows(helper, player, "torch"), "recipes without a rule still unlock the vanilla way");

            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            jumpOn(helper, player, Blocks.COBBLESTONE);
            check(helper, Sim.knows(helper, player, "stick") && Sim.sticksFromInventoryGrid(player).is(Items.STICK), "the rule unlocks it");

            // An operator may still hand the recipe out.
            ServerPlayer friend = Sim.join(helper, GameType.SURVIVAL);
            Sim.console(helper, "recipe give " + friend.getGameProfile().name() + " minecraft:stick");
            check(helper, Sim.knows(helper, friend, "stick"), "/recipe give works for a recipe with a rule");
            helper.getLevel().removeBlock(helper.absolutePos(CELL), false);
        });
    }

    /** lock_all_recipes off, rules on: only the items that have a rule are locked. */
    static void onlyRuledItemsLockedWithoutLockAll(GameTestHelper helper) {
        Sim.run(helper, () -> {
            Sim.config(STICK_RULE.replace("lock_all_recipes = true", "lock_all_recipes = false"));
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);
            check(helper, Sim.sticksFromInventoryGrid(player).isEmpty(), "the item with a rule is locked");
            check(helper, Sim.inventoryGridResult(player, new ItemStack(Items.COAL), new ItemStack(Items.STICK)).is(Items.TORCH),
                    "everything else is crafted freely");

            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            jumpOn(helper, player, Blocks.COBBLESTONE);
            check(helper, Sim.sticksFromInventoryGrid(player).is(Items.STICK), "and the rule opens the locked item");

            Sim.config(STICK_RULE.replace("lock_all_recipes = true", "lock_all_recipes = false").replace("template_unlocks = true", "template_unlocks = false"));
            ServerPlayer free = Sim.join(helper, GameType.SURVIVAL);
            check(helper, Sim.sticksFromInventoryGrid(free).is(Items.STICK), "both switches off: nothing is locked");
            helper.getLevel().removeBlock(helper.absolutePos(CELL), false);
        });
    }

    static void creativeIsNotCounted(GameTestHelper helper) {
        Sim.run(helper, () -> {
            Sim.config(STICK_RULE);
            ServerPlayer player = Sim.join(helper, GameType.CREATIVE);
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            check(helper, Sim.progressLine(player, "minecraft:stick").contains("break minecraft:stone 0/2"),
                    "creative mode is not counted: " + Sim.progressLine(player, "minecraft:stick"));

            Sim.config(STICK_RULE.replace("template_unlocks = true", "template_unlocks = false"));
            ServerPlayer survival = Sim.join(helper, GameType.SURVIVAL);
            Sim.breakBlock(helper, survival, Blocks.STONE, CELL);
            Sim.breakBlock(helper, survival, Blocks.STONE, CELL);
            jumpOn(helper, survival, Blocks.COBBLESTONE);
            check(helper, !Sim.knows(helper, survival, "stick"), "with template_unlocks off the rules do nothing");
            helper.getLevel().removeBlock(helper.absolutePos(CELL), false);
        });
    }

    static void resetTakesRecipesBack(GameTestHelper helper) {
        Sim.run(helper, () -> {
            Sim.config(STICK_RULE);
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);
            Sim.unlock(helper, player, "torch");
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            Sim.breakBlock(helper, player, Blocks.STONE, CELL);
            jumpOn(helper, player, Blocks.COBBLESTONE);
            check(helper, Sim.knows(helper, player, "stick"), "unlocked by the rule");
            // The result is already on screen when the admin resets the player.
            player.inventoryMenu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS));
            player.inventoryMenu.getSlot(3).set(new ItemStack(Items.OAK_PLANKS));
            check(helper, player.inventoryMenu.getSlot(0).getItem().is(Items.STICK), "sticks are offered");

            check(helper, UnlockManager.reset(player) == 2, "reset takes back both stick recipes the rule gave");
            check(helper, !Sim.knows(helper, player, "stick"), "the recipe is locked again");
            check(helper, player.inventoryMenu.getSlot(0).getItem().isEmpty(), "and the result that was on screen is gone");
            player.inventoryMenu.getSlot(1).set(ItemStack.EMPTY);
            player.inventoryMenu.getSlot(3).set(ItemStack.EMPTY);
            check(helper, Sim.knows(helper, player, "torch"), "recipes the mod did not give are left alone");
            check(helper, Sim.progressLine(player, "minecraft:stick").contains("break minecraft:stone 0/2"), "counters start over");

            // While the rule exists, getting planks (the vanilla way to learn sticks) does not help...
            Sim.obtainPlanks(player);
            check(helper, !Sim.knows(helper, player, "stick"), "the rule is still the only way after a reset");
            // ...but once the rule is deleted, the recipe the game wanted to give is handed over.
            Sim.config("lock_all_recipes = true\n");
            UnlockManager.index(helper.getLevel().getServer());
            check(helper, Sim.knows(helper, player, "stick"), "without the rule the vanilla unlock applies again");
            helper.getLevel().removeBlock(helper.absolutePos(CELL), false);
        });
    }

    static void progressIsSaved(GameTestHelper helper) {
        Sim.run(helper, () -> {
            ProgressData data = ProgressData.get(helper.getLevel().getServer());
            UUID id = UUID.randomUUID();
            Identifier recipe = Identifier.withDefaultNamespace("stick");
            data.of(id).counters.put("break minecraft:stone", 2);
            data.of(id).awarded.add(recipe);
            data.of(id).withheld.add(recipe);

            Tag saved = ProgressData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
            ProgressData loaded = ProgressData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
            data.of(id).counters.clear();
            data.of(id).awarded.clear();
            data.of(id).withheld.clear();

            ProgressData.PlayerProgress progress = loaded.peek(id);
            check(helper, progress != null && progress.counters.getInt("break minecraft:stone") == 2, "counters survive saving and loading");
            check(helper, progress != null && progress.awarded.contains(recipe) && progress.withheld.contains(recipe),
                    "so do the lists of recipes the mod gave and held back");
        });
    }

    /** Stands the player on {@code block} and makes the jump the server performs when a client reports one. */
    private static void jumpOn(GameTestHelper helper, ServerPlayer player, Block block) {
        BlockPos pos = helper.absolutePos(CELL);
        helper.getLevel().setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        player.snapTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        player.setOnGround(true);
        player.mainSupportingBlockPos = Optional.of(pos);
        player.jumpFromGround();
    }
}
