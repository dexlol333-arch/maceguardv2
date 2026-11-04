package dev.maceguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.Locale;

/** Recipe unlocking, Heavy Core validation, and the "ritual" flourish when something is forged. */
public final class RecipeListener implements Listener {

    private final MaceGuard plugin;
    private final GuardManager guards;
    private final RecipeManager recipes;

    public RecipeListener(MaceGuard plugin, GuardManager guards, RecipeManager recipes) {
        this.plugin = plugin;
        this.guards = guards;
        this.recipes = recipes;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String mode = plugin.getConfig().getString("recipes.unlock", "join");
        if (mode.equalsIgnoreCase("join")) {
            recipes.unlock(e.getPlayer());
        }
    }

    /** True if the grid holds a Heavy Core of the element the recipe needs. */
    private boolean hasCore(ItemStack[] matrix, String element) {
        for (ItemStack s : matrix) {
            if (s == null || s.getType() != Material.HEAVY_CORE) continue;
            String type = guards.coreTypeOf(s);
            if (type != null && type.equalsIgnoreCase(element)) return true;
        }
        return false;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        if (!(e.getRecipe() instanceof Keyed keyed)) return;
        RecipeManager.Info info = recipes.info(keyed.getKey());
        if (info == null) return;

        if (!hasCore(e.getInventory().getMatrix(), info.core())) {
            e.getInventory().setResult(null);
            Component hint = GuardManager.text("&cThis needs a " + capitalize(info.core()) + " Heavy Core");
            for (HumanEntity viewer : e.getViewers()) {
                if (viewer instanceof Player p) p.sendActionBar(hint);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getRecipe() instanceof Keyed keyed)) return;
        RecipeManager.Info info = recipes.info(keyed.getKey());
        if (info == null) return;

        if (!hasCore(e.getInventory().getMatrix(), info.core())) {
            e.setCancelled(true);
        }
    }

    /** Altar-style ceremony: lightning flash, particles, a title, and an optional broadcast. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForged(CraftItemEvent e) {
        if (!(e.getRecipe() instanceof Keyed keyed)) return;
        RecipeManager.Info info = recipes.info(keyed.getKey());
        if (info == null) return;
        if (!plugin.getConfig().getBoolean("recipes.ceremony.enabled", true)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Location loc = player.getLocation();
        World world = player.getWorld();
        world.strikeLightningEffect(loc);
        world.spawnParticle(Particle.ENCHANT, loc.clone().add(0, 1, 0), 80, 0.8, 1.0, 0.8, 0.5);
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);

        player.showTitle(Title.title(
                GuardManager.text("&6&lForged"),
                GuardManager.text(info.display()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));

        if (plugin.getConfig().getBoolean("recipes.ceremony.broadcast", true)) {
            Bukkit.broadcast(GuardManager.text("&6&l✦ &f" + player.getName() + " &7forged " + info.display() + "&7!"));
        }
    }
}
