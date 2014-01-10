package net.canarymod;

import net.canarymod.api.inventory.CanaryEnchantment;
import net.canarymod.api.inventory.CanaryItem;
import net.canarymod.api.inventory.Enchantment;
import net.canarymod.api.inventory.Item;
import net.canarymod.serialize.EnchantmentSerializer;
import net.canarymod.serialize.ItemSerializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.gui.MinecraftServerGui;

import javax.swing.*;
import java.awt.*;

public class Main {
    private static CanaryMod mod;

    private static void initBird() {
        // Initialize the bird
        mod = new CanaryMod();
        Canary.setCanary(mod);
        // Add system internal serializers
        Canary.addSerializer(new ItemSerializer(), CanaryItem.class);
        Canary.addSerializer(new ItemSerializer(), Item.class);
        Canary.addSerializer(new EnchantmentSerializer(), CanaryEnchantment.class);
        Canary.addSerializer(new EnchantmentSerializer(), Enchantment.class);
    }

    /**
     * The canary Bootstrap process
     *
     * @param args
     */
    public static void main(String[] args) {
        Canary.logInfo("Starting: " + Canary.getImplementationTitle() + " " + Canary.getImplementationVersion());
        try {
            Class.forName("org.sqlite.JDBC");
        }
        catch (ClassNotFoundException e) {
        } // Need to initialize the SQLite driver for some reason, initialize here for plugin use as well
        try {
            // Sets the default state for the gui, true is off, false is on
            MinecraftServer.setHeadless(true);
            boolean runUnControlled = false, headless = GraphicsEnvironment.isHeadless();
            for (int index = 0; index < args.length; ++index) {
                String key = args[index];
                String value = index == args.length - 1 ? null : args[index + 1];
                // Replace the nogui option with gui option so the gui is off by default
                if (key.equals("gui") || key.equals("--gui")|| key.equals("-gui")) {
                    MinecraftServer.setHeadless(false);
                }
                else if (key.equals("noControl") || key.equals("-noControl") || key.equals("--noControl")) {
                    runUnControlled = true;
                }
            }

            // Check if there is a Console in use and if we should launch a GUI as replacement for no console
            if (System.console() == null) {
                if (!headless && !runUnControlled) { //if not headless, no console, and not unControlled, launch the GUI
                    MinecraftServer.setHeadless(false);
                }
                else if (runUnControlled) { // If allowed to be unControlled, just log a warning
                    Canary.logWarning("Server is starting with no Console or GUI be warned!");
                }
                else { // No graphics environment and not allowed to be uncontrolled? KILL IT!
                    Canary.logSevere("Server can not start no Console or GUI is available to control the server.");
                    System.exit(42);
                }
            }

            if (!MinecraftServer.isHeadless()) {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception interruptedexception) {
                    ;
                }
                MinecraftServerGui.getLog();
            }

            initBird(); // Initialize the Bird
            MinecraftServer.main(args); // Boot up the native server

            // They need the server to be set
            mod.initPermissions();
            // Initialize providers that require Canary to be set already
            mod.initUserAndGroupsManager();
            mod.initKits();
            // Warps need the DimensionType data which is created upon servre start
            mod.initWarps();
            // commands require a valid commandOwner which is the server.
            // That means for commands to work, we gotta load Minecraft first
            mod.initCommands();
            // and finally throw in the MOTDListner
            mod.initMOTDListener();
        }
        catch (Throwable t) {
            Canary.logStacktrace("Exception while starting the server: ", t);
        }
    }

    /**
     * Restart the server without killing the JVM
     *
     * @param reloadCanary
     */
    public static void restart(boolean reloadCanary) {
        throw new UnsupportedOperationException("Restart is not implemented yet!");
    }
}
