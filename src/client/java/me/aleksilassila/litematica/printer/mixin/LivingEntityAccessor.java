package me.aleksilassila.litematica.printer.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    // field_6241 = prevHeadYaw
    @Accessor("field_6241")
    float getPrevHeadYaw();

    @Accessor("field_6241")
    void setPrevHeadYaw(float prevHeadYaw);

    // field_6220 = prevBodyYaw
    @Accessor("field_6220")
    float getPrevBodyYaw();

    @Accessor("field_6220")
    void setPrevBodyYaw(float prevBodyYaw);
}