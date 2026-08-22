package me.aleksilassila.litematica.printer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;

abstract public class BlockHelper {
    public static List<Class<?>> interactiveBlocks = new ArrayList<>(Arrays.asList(
            AbstractChestBlock.class, AbstractFurnaceBlock.class, CraftingTableBlock.class,
            LeverBlock.class,
            DoorBlock.class, TrapDoorBlock.class, BedBlock.class, RedStoneWireBlock.class,
            ScaffoldingBlock.class,
            HopperBlock.class, EnchantingTableBlock.class, NoteBlock.class, JukeboxBlock.class,
            CakeBlock.class,
            FenceGateBlock.class, BrewingStandBlock.class, DragonEggBlock.class, CommandBlock.class,
            BeaconBlock.class, AnvilBlock.class, ComparatorBlock.class, RepeaterBlock.class,
            DropperBlock.class, DispenserBlock.class, ShulkerBoxBlock.class, LecternBlock.class,
            FlowerPotBlock.class, BarrelBlock.class, BellBlock.class, SmithingTableBlock.class,
            LoomBlock.class, CartographyTableBlock.class, GrindstoneBlock.class,
            StonecutterBlock.class, SignBlock.class, AbstractCandleBlock.class));

    public static final Item[] SHOVEL_ITEMS = new Item[]{
            Items.NETHERITE_SHOVEL,
            Items.DIAMOND_SHOVEL,
            Items.GOLDEN_SHOVEL,
            Items.IRON_SHOVEL,
            Items.STONE_SHOVEL,
            Items.WOODEN_SHOVEL
    };
}
