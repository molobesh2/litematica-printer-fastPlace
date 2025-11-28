package me.aleksilassila.litematica.printer.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
    // field_6004 = prevYaw
    @Accessor("field_6004")
    float getPrevYaw();

    @Accessor("field_6004")
    void setPrevYaw(float prevYaw);

    // field_5965 = prevPitch
    @Accessor("field_5965")
    float getPrevPitch();

    @Accessor("field_5965")
    void setPrevPitch(float prevPitch);
}