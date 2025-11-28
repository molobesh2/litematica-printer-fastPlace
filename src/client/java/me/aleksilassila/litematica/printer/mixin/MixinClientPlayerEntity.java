package me.aleksilassila.litematica.printer.mixin;

import me.aleksilassila.litematica.printer.Printer;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity {

    @Inject(method = "getYaw", at = @At("HEAD"), cancellable = true)
    private void getYaw(CallbackInfoReturnable<Float> cir) {
        // Если принтер сейчас ставит блок и есть нужный угол -> подменяем
        if (Printer.isPlacing && Printer.overrideRotation) {
            cir.setReturnValue(Printer.targetYaw);
        }
    }

    @Inject(method = "getPitch", at = @At("HEAD"), cancellable = true)
    private void getPitch(CallbackInfoReturnable<Float> cir) {
        if (Printer.isPlacing && Printer.overrideRotation) {
            cir.setReturnValue(Printer.targetPitch);
        }
    }
}