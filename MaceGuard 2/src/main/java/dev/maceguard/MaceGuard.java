package dev.maceguard;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class MaceGuard extends JavaPlugin {

    private GuardManager guards;
    private RecipeManager recipes;
    private Plugin maceRituals;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        maceRituals = getServer().getPluginManager().getPlugin("MaceRituals");
        if (maceRituals == null) {
            getLogger().severe("MaceRituals is required but was not found. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        guards = new GuardManager(this, maceRituals);
        guards.load();

        recipes = new RecipeManager(this, guards);
        recipes.register();

        getServer().getPluginManager().registerEvents(new GuardListener(this, guards), this);
        getServer().getPluginManager().registerEvents(new RecipeListener(this, guards, recipes), this);

        PluginCommand cmd = getCommand("maceguard");
        if (cmd != null) {
            GuardCommand handler = new GuardCommand(this, guards, recipes);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        if (getConfig().getString("recipes.unlock", "join").equalsIgnoreCase("join")) {
            recipes.unlockAllOnline();
        }
        getLogger().info("Loaded " + guards.sets().size() + " armor sets and "
                + guards.weapons().size() + " weapon elements.");
    }

    @Override
    public void onDisable() {
        if (recipes != null) recipes.unregister();
    }

    public void reload() {
        reloadConfig();
        guards.load();
        recipes.register();
        if (getConfig().getString("recipes.unlock", "join").equalsIgnoreCase("join")) {
            recipes.unlockAllOnline();
        }
    }
}
