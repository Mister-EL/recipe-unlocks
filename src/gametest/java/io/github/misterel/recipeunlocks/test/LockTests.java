package io.github.misterel.recipeunlocks.test;

import static io.github.misterel.recipeunlocks.test.Sim.check;

import io.github.misterel.recipeunlocks.PlayerContext;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Crafting is refused until the recipe is in the player's recipe book. */
final class LockTests {
    private static final String LOCK_ONLY = "lock_all_recipes = true\ntemplate_unlocks = false\n";

    private LockTests() {}

    static void craftingLockedUntilUnlocked(GameTestHelper helper) {
        Sim.run(helper, () -> {
            Sim.config(LOCK_ONLY);
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);
            check(helper, !Sim.knows(helper, player, "stick"), "a new player does not know the stick recipe");
            check(helper, Sim.sticksFromInventoryGrid(player).isEmpty(), "a locked recipe gives no result");

            Sim.unlock(helper, player, "stick");
            ItemStack sticks = Sim.sticksFromInventoryGrid(player);
            check(helper, sticks.is(Items.STICK) && sticks.getCount() == 4, "an unlocked recipe crafts normally, got " + sticks);

            ServerPlayer creative = Sim.join(helper, GameType.CREATIVE);
            check(helper, Sim.sticksFromInventoryGrid(creative).is(Items.STICK), "creative players are not restricted");

            Sim.config("lock_all_recipes = false\n");
            ServerPlayer free = Sim.join(helper, GameType.SURVIVAL);
            check(helper, Sim.sticksFromInventoryGrid(free).is(Items.STICK), "with lock_all_recipes off nothing is restricted");
        });
    }

    static void smithingLocked(GameTestHelper helper) {
        Sim.run(helper, () -> {
            Sim.config(LOCK_ONLY);
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);
            SmithingMenu menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), player.blockPosition()));
            menu.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            menu.getSlot(1).set(new ItemStack(Items.DIAMOND_PICKAXE));
            menu.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT));
            check(helper, menu.getSlot(3).getItem().isEmpty(), "smithing a locked recipe gives no result");

            Sim.unlock(helper, player, "netherite_pickaxe_smithing");
            menu.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT));
            check(helper, menu.getSlot(3).getItem().is(Items.NETHERITE_PICKAXE), "smithing works once unlocked, got " + menu.getSlot(3).getItem());
        });
    }

    static void stonecutterLocked(GameTestHelper helper) {
        Sim.run(helper, () -> {
            Sim.config(LOCK_ONLY);
            ServerLevel level = helper.getLevel();
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);
            StonecutterMenu menu = new StonecutterMenu(1, player.getInventory(), ContainerLevelAccess.create(level, player.blockPosition()));
            ItemStack stone = new ItemStack(Items.STONE, 4);
            menu.getSlot(0).set(stone);
            check(helper, !menu.clickMenuButton(player, 0), "clicking a locked stonecutter recipe is refused");
            check(helper, menu.getSlot(1).getItem().isEmpty(), "and no result appears");

            RecipeHolder<StonecutterRecipe> first = level.recipeAccess().stonecutterRecipes().selectByInput(stone).entries().get(0).recipe().recipe().orElseThrow();
            player.awardRecipes(List.of(first));
            check(helper, menu.clickMenuButton(player, 0), "the unlocked stonecutter recipe can be selected");
            check(helper, !menu.getSlot(1).getItem().isEmpty(), "and gives a result");
            check(helper, !menu.clickMenuButton(player, 1) && menu.getSlot(1).getItem().is(first.value().assemble(null).getItem()),
                    "the other recipes stay locked");
        });
    }

    static void furnaceLocked(GameTestHelper helper) {
        Sim.run(helper, () -> {
            Sim.config(LOCK_ONLY);
            ServerLevel level = helper.getLevel();
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 0));
            level.setBlock(pos, Blocks.FURNACE.defaultBlockState(), Block.UPDATE_CLIENTS);
            AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(pos);
            String smelting = "iron_ingot_from_smelting_raw_iron";

            // Input: a real click packet with raw iron on the cursor.
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);
            player.openMenu(furnace);
            player.containerMenu.setCarried(new ItemStack(Items.RAW_IRON));
            Sim.clickSlot(player, 0);
            check(helper, furnace.getItem(0).isEmpty() && player.containerMenu.getCarried().is(Items.RAW_IRON),
                    "ore of a locked smelting recipe cannot be put into the furnace");

            player.containerMenu.setCarried(new ItemStack(Items.DIRT));
            Sim.clickSlot(player, 0);
            check(helper, furnace.getItem(0).is(Items.DIRT), "things that cannot be smelted at all are not our business");
            furnace.setItem(0, ItemStack.EMPTY);

            Sim.unlock(helper, player, smelting);
            player.containerMenu.setCarried(new ItemStack(Items.RAW_IRON));
            Sim.clickSlot(player, 0);
            check(helper, furnace.getItem(0).is(Items.RAW_IRON), "once unlocked the ore goes in");
            furnace.setItem(0, ItemStack.EMPTY);

            // Without a player (hopper, tick) nothing is restricted.
            check(helper, player.containerMenu.getSlot(0).mayPlace(new ItemStack(Items.RAW_GOLD)), "automation is not restricted");

            // Output: somebody else's smelting result.
            furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 2));
            furnace.setRecipeUsed(Sim.recipe(helper, smelting));
            ServerPlayer other = Sim.join(helper, GameType.SURVIVAL);
            other.openMenu(furnace);
            Sim.clickSlot(other, 2);
            check(helper, furnace.getItem(2).getCount() == 2 && other.containerMenu.getCarried().isEmpty(),
                    "the result of a locked recipe cannot be taken");
            check(helper, !Sim.knows(helper, other, smelting), "and the recipe stays locked");

            Sim.clickSlot(player, 2);
            check(helper, furnace.getItem(2).isEmpty() && player.containerMenu.getCarried().is(Items.IRON_INGOT),
                    "a player who unlocked the recipe takes the result");
            level.removeBlock(pos, false);
        });
    }

    static void campfireLocked(GameTestHelper helper) {
        Sim.run(helper, () -> {
            Sim.config(LOCK_ONLY);
            ServerLevel level = helper.getLevel();
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 0));
            level.setBlock(pos, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_CLIENTS);
            CampfireBlockEntity campfire = (CampfireBlockEntity) level.getBlockEntity(pos);
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);

            check(helper, !campfire.placeFood(level, player, new ItemStack(Items.BEEF)), "locked campfire recipe: the food is refused");
            check(helper, campfire.placeFood(level, null, new ItemStack(Items.BEEF)), "no player involved: not restricted");
            Sim.unlock(helper, player, "cooked_beef_from_campfire_cooking");
            check(helper, campfire.placeFood(level, player, new ItemStack(Items.BEEF)), "unlocked campfire recipe: the food is accepted");
            level.removeBlock(pos, false);
        });
    }

    /** What makes crafting stations of other mods respect the lock. */
    static void lookupsHiddenFromActingPlayer(GameTestHelper helper) {
        Sim.run(helper, () -> {
            Sim.config(LOCK_ONLY);
            ServerLevel level = helper.getLevel();
            ServerPlayer player = Sim.join(helper, GameType.SURVIVAL);
            RecipeManager recipes = level.getServer().getRecipeManager();
            CraftingInput planks = CraftingInput.of(1, 2, List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS)));
            @SuppressWarnings("unchecked")
            RecipeHolder<CraftingRecipe> stick = (RecipeHolder<CraftingRecipe>) Sim.recipe(helper, "stick");

            check(helper, recipes.getRecipeFor(RecipeType.CRAFTING, planks, level).isPresent(), "machines see every recipe");
            PlayerContext.run(player, () -> {
                check(helper, recipes.getRecipeFor(RecipeType.CRAFTING, planks, level).isEmpty(), "a lookup for a player skips locked recipes");
                check(helper, recipes.getRecipeFor(RecipeType.CRAFTING, planks, level, stick).isEmpty(), "also when the caller remembers the recipe");
                check(helper, recipes.getRecipeFor(RecipeType.CRAFTING, planks, level, stick.id()).isEmpty(), "or remembers its id");
            });
            check(helper, PlayerContext.get() == null, "the acting player is forgotten afterwards");

            Sim.config(LOCK_ONLY + "block_modded_stations = false\n");
            PlayerContext.run(player, () ->
                    check(helper, recipes.getRecipeFor(RecipeType.CRAFTING, planks, level).isPresent(), "the filter can be switched off"));

            Sim.config(LOCK_ONLY);
            Sim.unlock(helper, player, "stick");
            PlayerContext.run(player, () ->
                    check(helper, recipes.getRecipeFor(RecipeType.CRAFTING, planks, level).equals(Optional.of(stick)), "unlocked recipes are found"));
        });
    }

    /** The crafter block crafts only what the player who placed it has unlocked, and only while that player is online. */
    static void crafterFollowsOwner(GameTestHelper helper) {
        Sim.config(LOCK_ONLY + "lock_crafter = true\n");
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = Sim.join(helper, GameType.SURVIVAL);
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 0));
        BlockPos crafterPos = floor.above();
        BlockPos power = crafterPos.east();
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        // The owner places the crafter like a player does: right click with the item on top of the floor block.
        ItemStack inHand = new ItemStack(Items.CRAFTER);
        owner.setItemInHand(InteractionHand.MAIN_HAND, inHand);
        owner.gameMode.useItemOn(owner, level, inHand, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
        if (!(level.getBlockEntity(crafterPos) instanceof CrafterBlockEntity crafter)) {
            helper.fail("the crafter was not placed");
            return;
        }
        Runnable fill = () -> {
            crafter.setItem(0, new ItemStack(Items.OAK_PLANKS));
            crafter.setItem(3, new ItemStack(Items.OAK_PLANKS));
        };
        Runnable pulse = () -> {
            level.removeBlock(power, false);
            level.setBlock(power, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        };

        fill.run();
        pulse.run();
        helper.startSequence()
                .thenIdle(12)
                .thenExecute(() -> {
                    check(helper, !crafter.getItem(0).isEmpty(), "the owner has not unlocked sticks: the crafter must not craft");
                    Sim.unlock(helper, owner, "stick");
                    level.removeBlock(power, false);
                })
                .thenIdle(4)
                .thenExecute(pulse)
                .thenIdle(12)
                .thenExecute(() -> {
                    check(helper, crafter.getItem(0).isEmpty(), "the owner unlocked sticks: the crafter crafts");
                    fill.run();
                    Sim.leave(owner);
                    level.removeBlock(power, false);
                })
                .thenIdle(4)
                .thenExecute(pulse)
                .thenIdle(12)
                .thenExecute(() -> {
                    check(helper, !crafter.getItem(0).isEmpty(), "the owner is offline: the crafter must not craft");
                    Sim.config(LOCK_ONLY + "lock_crafter = false\n");
                    level.removeBlock(power, false);
                })
                .thenIdle(4)
                .thenExecute(pulse)
                .thenIdle(12)
                .thenExecute(() -> {
                    check(helper, crafter.getItem(0).isEmpty(), "with lock_crafter off the crafter crafts for anybody");
                    level.removeBlock(power, false);
                    level.removeBlock(crafterPos, false);
                    level.removeBlock(floor, false);
                    Sim.leaveAll();
                })
                .thenSucceed();
    }
}
