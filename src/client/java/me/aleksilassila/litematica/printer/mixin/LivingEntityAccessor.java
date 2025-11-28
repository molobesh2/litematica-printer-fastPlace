package me.aleksilassila.litematica.printer.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("prevHeadYaw")
    float getPrevHeadYaw();

    @Accessor("prevHeadYaw")
    void setPrevHeadYaw(float prevHeadYaw);

    @Accessor("prevBodyYaw")
    float getPrevBodyYaw();

    @Accessor("prevBodyYaw")
    void setPrevBodyYaw(float prevBodyYaw);
}