package me.aleksilassila.litematica.printer;

import me.aleksilassila.litematica.printer.event.KeyCallbacks;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;

public class LitematicaMixinMod implements ClientModInitializer {
    public static Printer printer;

    @Override
    public void onInitializeClient() {
        KeyCallbacks.init(MinecraftClient.getInstance());
        Printer.logger.info("{} initialized.", PrinterReference.MOD_STRING);
    }
}
