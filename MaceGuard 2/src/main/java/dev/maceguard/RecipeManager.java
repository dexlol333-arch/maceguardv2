package dev.maceguard;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registers every armor piece and weapon as a real shaped crafting recipe, so they show up in the
 * crafting table's recipe book (click a recipe to auto-fill the grid from your inventory).
 *
 * <pre>
 *   A B A        A = corner items   B = edge items
 *   B X B        X = the base armor piece / tool
 *   A C A        C = a MaceRituals Heavy Core of the right element
 * </pre>
 */
public final class RecipeManager {

    /** What a recipe needs beyond its ingredients, and how to announce it. */
    public record Info(String core, String display) {
    }

    private final MaceGuard plugin;
    private final GuardManager guards;
    private final Map<NamespacedKey, Info> ours = new LinkedHashMap<>();

    public RecipeManager(MaceGuard plugin, GuardManager guards) {
        this.plugin = plugin;
        this.guards = guards;
    }

    public Info info(NamespacedKey key) {
        return ours.get(key);
    }

    public Collection<NamespacedKey> keys() {
        return ours.keySet();
    }

    public void unregister() {
        for (NamespacedKey key : ours.keySet()) {
            Bukkit.removeRecipe(key);
        }
        ours.clear();
    }

    public void register() {
        unregister();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("recipes");
        if (root == null || !root.getBoolean("enabled", true)) return;

        ConfigurationSection armor = root.getConfigurationSection("armor");
        if (armor != null) {
            for (GuardSet set : guards.sets()) {
                ConfigurationSection c = armor.getConfigurationSection(set.id());
                if (c == null) continue;
                Material corners = material(c.getString("corners"), "armor." + set.id() + ".corners");
                Material edges = material(c.getString("edges"), "armor." + set.id() + ".edges");
                if (corners == null || edges == null) continue;
                String core = c.getString("core", set.counters()).toLowerCase(Locale.ROOT);

                for (GuardManager.Piece piece : GuardManager.Piece.values()) {
                    Material base = Material.matchMaterial(set.base() + "_" + piece.name());
                    ItemStack result = guards.buildArmor(set, piece);
                    if (base == null || result == null) continue;
                    add("armor_" + set.id() + "_" + piece.name().toLowerCase(Locale.ROOT), result,
                            base, corners, edges, core, "armor_" + set.id(),
                            new Info(core, set.name() + " " + piece.label));
                }
            }
        }

        ConfigurationSection tools = root.getConfigurationSection("weapons");
        if (tools != null) {
            for (GuardManager.WeaponDef def : guards.weapons()) {
                ConfigurationSection c = tools.getConfigurationSection(def.element());
                if (c == null) continue;
                Material corners = material(c.getString("corners"), "weapons." + def.element() + ".corners");
                Material edges = material(c.getString("edges"), "weapons." + def.element() + ".edges");
                if (corners == null || edges == null) continue;
                String core = c.getString("core", def.element()).toLowerCase(Locale.ROOT);

                for (GuardManager.Kind kind : GuardManager.Kind.values()) {
                    Material base = Material.matchMaterial(def.base() + "_" + kind.name());
                    ItemStack result = guards.buildWeapon(def, kind); // logs if the material is missing
                    if (base == null || result == null) continue;
                    add("weapon_" + def.element() + "_" + kind.name().toLowerCase(Locale.ROOT), result,
                            base, corners, edges, core, "weapon_" + def.element(),
                            new Info(core, def.name() + " " + kind.label));
                }
            }
        }

        plugin.getLogger().info("Registered " + ours.size() + " crafting recipes.");
    }

    private void add(String id, ItemStack result, Material base, Material corners, Material edges,
                     String core, String group, Info info) {
        NamespacedKey key = new NamespacedKey(plugin, id);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("ABA", "BXB", "ACA");
        recipe.setIngredient('A', corners);
        recipe.setIngredient('B', edges);
        recipe.setIngredient('X', base);
        recipe.setIngredient('C', Material.HEAVY_CORE);
        recipe.setGroup("maceguard_" + group);
        recipe.setCategory(CraftingBookCategory.EQUIPMENT);
        if (Bukkit.addRecipe(recipe)) {
            ours.put(key, info);
        } else {
            plugin.getLogger().warning("Could not register recipe " + id);
        }
    }

    private Material material(String name, String where) {
        Material m = name == null ? null : Material.matchMaterial(name);
        if (m == null) {
            plugin.getLogger().warning("recipes." + where + ": unknown material '" + name + "' - recipe skipped");
        }
        return m;
    }

    // ------------------------------------------------------------------ unlocking

    /** Add every recipe to the player's recipe book. */
    public void unlock(Player player) {
        player.discoverRecipes(new ArrayList<>(ours.keySet()));
    }

    public void unlockAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) unlock(p);
    }

    public List<String> ids() {
        List<String> out = new ArrayList<>();
        for (NamespacedKey k : ours.keySet()) out.add(k.getKey());
        return out;
    }
}
