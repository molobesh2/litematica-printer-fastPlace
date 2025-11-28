package me.aleksilassila.litematica.printer.config;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import me.aleksilassila.litematica.printer.PrinterReference;

import java.util.List;

public class Hotkeys {
    private static final String HOTKEY_KEY = PrinterReference.MOD_KEY + ".config.hotkeys";

    // Hotkeys
    public static final ConfigHotkey PRINT = new ConfigHotkey("print", "V", KeybindSettings.PRESS_ALLOWEXTRA_EMPTY).apply(HOTKEY_KEY);
    public static final ConfigHotkey TOGGLE_PRINTING_MODE = new ConfigHotkey("togglePrintingMode", "CAPS_LOCK", KeybindSettings.PRESS_ALLOWEXTRA_EMPTY).apply(HOTKEY_KEY);
    
    // НОВАЯ КЛАВИША: Точный режим. "GRAVE_ACCENT" это клавиша тильды/ё.
    public static final ConfigHotkey TOGGLE_ACCURATE_MODE = new ConfigHotkey("toggleAccurateMode", "GRAVE_ACCENT", KeybindSettings.PRESS_ALLOWEXTRA_EMPTY).apply(HOTKEY_KEY);

    public static List<ConfigHotkey> getHotkeyList() {
        List<ConfigHotkey> list = new java.util.ArrayList<>(fi.dy.masa.litematica.config.Hotkeys.HOTKEY_LIST);
        list.add(PRINT);
        list.add(TOGGLE_PRINTING_MODE);
        list.add(TOGGLE_ACCURATE_MODE);

        return ImmutableList.copyOf(list);
    }
}