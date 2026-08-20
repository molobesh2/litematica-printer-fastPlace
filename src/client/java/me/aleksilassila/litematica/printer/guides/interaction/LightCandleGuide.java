package me.aleksilassila.litematica.printer.guides.interaction;

import me.aleksilassila.litematica.printer.SchematicBlockState;
import net.minecraft.block.AbstractCandleBlock;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.Properties;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class LightCandleGuide extends InteractionGuide {
    boolean shouldBeLit;
    boolean isLit;

    public LightCandleGuide(SchematicBlockState state) {
        super(state);

        shouldBeLit = getProperty(targetState, Properties.LIT).orElse(false);
        isLit = getProperty(currentState, Properties.LIT).orElse(false);
    }

    @Override
    protected @Nonnull List<ItemStack> getRequiredItems() {
        return Collections.singletonList(new ItemStack(Items.FLINT_AND_STEEL));
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        if (!super.canExecute(player))
            return false;

        return (currentState.getBlock() instanceof AbstractCandleBlock) && shouldBeLit && !isLit;
    }
}
