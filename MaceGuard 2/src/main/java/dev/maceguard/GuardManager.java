package dev.maceguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads armor sets and elemental weapons from config, builds the items and identifies them. */
public final class GuardManager {

    public enum Piece {
        HELMET("Helmet"), CHESTPLATE("Chestplate"), LEGGINGS("Leggings"), BOOTS("Boots");

        public final String label;

        Piece(String label) {
            this.label = label;
        }

        public static Piece parse(String s) {
            for (Piece p : values()) {
                if (p.name().equalsIgnoreCase(s)) return p;
            }
            return null;
        }
    }

    public enum Kind {
        SWORD("Sword"), AXE("Axe"), SPEAR("Spear");

        public final String label;

        Kind(String label) {
            this.label = label;
        }

        public static Kind parse(String s) {
            for (Kind k : values()) {
                if (k.name().equalsIgnoreCase(s)) return k;
            }
            return null;
        }
    }

    public record WeaponDef(String element, String name, String base, Map<String, Integer> enchants,
                            double bonusDamage, int fireSeconds, double lightningChance,
                            double lightningDamage, List<String> potions, List<String> lore) {
    }

    public record Potion(PotionEffectType type, int ticks, int amplifier) {
    }

    /** The status effects an element applies (fire plus potions). */
    public record Profile(int fireSeconds, List<Potion> potions) {
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final MaceGuard plugin;
    private final Plugin maceRituals;
    private final NamespacedKey maceKey;
    private final NamespacedKey coreKey;
    private final NamespacedKey setKey;
    private final NamespacedKey pieceKey;
    private final NamespacedKey elementKey;
    private final NamespacedKey kindKey;

    private final Map<String, GuardSet> sets = new LinkedHashMap<>();
    private final Map<String, WeaponDef> weapons = new LinkedHashMap<>();

    public GuardManager(MaceGuard plugin, Plugin maceRituals) {
        this.plugin = plugin;
        this.maceRituals = maceRituals;
        this.maceKey = new NamespacedKey(maceRituals, "mace_type");
        this.coreKey = new NamespacedKey(maceRituals, "core_type");
        this.setKey = new NamespacedKey(plugin, "guard_set");
        this.pieceKey = new NamespacedKey(plugin, "guard_piece");
        this.elementKey = new NamespacedKey(plugin, "weapon_element");
        this.kindKey = new NamespacedKey(plugin, "weapon_kind");
    }

    // ------------------------------------------------------------------ loading

    public void load() {
        sets.clear();
        weapons.clear();

        ConfigurationSection armors = plugin.getConfig().getConfigurationSection("armors");
        if (armors != null) {
            for (String id : armors.getKeys(false)) {
                ConfigurationSection s = armors.getConfigurationSection(id);
                if (s == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                sets.put(key, new GuardSet(
                        key,
                        s.getString("name", id),
                        s.getString("counters", "").toLowerCase(Locale.ROOT),
                        s.getString("base", "IRON"),
                        s.getString("trim.pattern", "").toLowerCase(Locale.ROOT),
                        s.getString("trim.material", "").toLowerCase(Locale.ROOT),
                        readEnchants(s.getConfigurationSection("enchants")),
                        s.getDouble("damage-reduction", 0.10),
                        s.getDouble("effect-reduction", 0.25),
                        s.getDouble("knockback-reduction", 0.10),
                        s.getStringList("lore")));
            }
        }

        ConfigurationSection tools = plugin.getConfig().getConfigurationSection("weapons");
        if (tools != null) {
            for (String id : tools.getKeys(false)) {
                ConfigurationSection s = tools.getConfigurationSection(id);
                if (s == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                weapons.put(key, new WeaponDef(
                        key,
                        s.getString("name", id),
                        s.getString("base", "NETHERITE"),
                        readEnchants(s.getConfigurationSection("enchants")),
                        s.getDouble("bonus-damage", 0.0),
                        s.getInt("fire-seconds", 0),
                        s.getDouble("lightning-chance", 0.0),
                        s.getDouble("lightning-damage", 0.0),
                        s.getStringList("potions"),
                        s.getStringList("lore")));
            }
        }
    }

    private static Map<String, Integer> readEnchants(ConfigurationSection sec) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (sec == null) return out;
        for (String k : sec.getKeys(false)) {
            out.put(k.toLowerCase(Locale.ROOT), sec.getInt(k));
        }
        return out;
    }

    public Collection<GuardSet> sets() {
        return sets.values();
    }

    public Collection<WeaponDef> weapons() {
        return weapons.values();
    }

    public GuardSet set(String id) {
        return id == null ? null : sets.get(id.toLowerCase(Locale.ROOT));
    }

    public WeaponDef weapon(String element) {
        return element == null ? null : weapons.get(element.toLowerCase(Locale.ROOT));
    }

    /** The armor set that counters the given element (mace id or weapon element). */
    public GuardSet setCountering(String element) {
        if (element == null) return null;
        for (GuardSet s : sets.values()) {
            if (s.counters().equalsIgnoreCase(element)) return s;
        }
        return null;
    }

    // ------------------------------------------------------------------ building items

    public ItemStack buildArmor(GuardSet set, Piece piece) {
        Material mat = Material.matchMaterial(set.base() + "_" + piece.name());
        if (mat == null) {
            plugin.getLogger().warning("Unknown armor material '" + set.base() + "_" + piece.name()
                    + "' for set " + set.id());
            return null;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        meta.displayName(text(set.name() + " " + piece.label));

        List<Component> lore = new ArrayList<>();
        for (String line : set.lore()) lore.add(text(line));
        lore.add(text(""));
        lore.add(text("&7Counters: &e" + capitalize(set.counters()) + " &7maces & weapons"));
        lore.add(text("&7Per piece: &a-" + pct(set.damageReduction()) + "% &7damage"));
        lore.add(text("&7           &a-" + pct(set.effectReduction()) + "% &7burn / potion effects"));
        lore.add(text("&7           &a-" + pct(set.knockbackReduction()) + "% &7knockback / pull"));
        meta.lore(lore);

        applyEnchants(meta, set.enchants());

        if (meta instanceof ArmorMeta armorMeta) {
            TrimMaterial tm = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(set.trimMaterial()));
            TrimPattern tp = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(set.trimPattern()));
            if (tm != null && tp != null) {
                armorMeta.setTrim(new ArmorTrim(tm, tp));
            } else {
                plugin.getLogger().warning("Unknown trim '" + set.trimPattern() + "' / '"
                        + set.trimMaterial() + "' for set " + set.id());
            }
        }

        meta.getPersistentDataContainer().set(setKey, PersistentDataType.STRING, set.id());
        meta.getPersistentDataContainer().set(pieceKey, PersistentDataType.STRING, piece.name());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildWeapon(WeaponDef def, Kind kind) {
        Material mat = Material.matchMaterial(def.base() + "_" + kind.name());
        if (mat == null) {
            plugin.getLogger().warning("Unknown tool material '" + def.base() + "_" + kind.name()
                    + "' for element " + def.element()
                    + (kind == Kind.SPEAR ? " (spears need Minecraft 1.21.11+)" : ""));
            return null;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        meta.displayName(text(def.name() + " " + kind.label));

        List<Component> lore = new ArrayList<>();
        for (String line : def.lore()) lore.add(text(line));
        lore.add(text(""));
        lore.add(text("&7Element: &e" + capitalize(def.element())));
        lore.add(text("&7Countered by the matching trimmed armor."));
        meta.lore(lore);

        applyEnchants(meta, def.enchants());

        meta.getPersistentDataContainer().set(elementKey, PersistentDataType.STRING, def.element());
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind.name());
        item.setItemMeta(meta);
        return item;
    }

    private void applyEnchants(ItemMeta meta, Map<String, Integer> enchants) {
        for (Map.Entry<String, Integer> e : enchants.entrySet()) {
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e.getKey()));
            if (ench == null) {
                plugin.getLogger().warning("Unknown enchantment '" + e.getKey() + "'");
                continue;
            }
            meta.addEnchant(ench, e.getValue(), true);
        }
    }

    // ------------------------------------------------------------------ identifying items

    private static String read(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** The mace id of a MaceRituals mace, or null. */
    public String maceElementOf(ItemStack item) {
        String id = read(item, maceKey);
        return id == null ? null : id.toLowerCase(Locale.ROOT);
    }

    /** The element id of a MaceRituals Heavy Core, or null. */
    public String coreTypeOf(ItemStack item) {
        String id = read(item, coreKey);
        return id == null ? null : id.toLowerCase(Locale.ROOT);
    }

    /** The weapon definition if the item is one of our elemental tools, else null. */
    public WeaponDef weaponDefOf(ItemStack item) {
        return weapon(read(item, elementKey));
    }

    /** The armor set if the item is one of our armor pieces, else null. */
    public GuardSet guardSetOf(ItemStack item) {
        return set(read(item, setKey));
    }

    public boolean isGuardItem(ItemStack item) {
        return read(item, setKey) != null || read(item, elementKey) != null;
    }

    public int piecesWorn(Player player, GuardSet set) {
        int n = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            String id = read(armor, setKey);
            if (id != null && id.equalsIgnoreCase(set.id())) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ element effects

    /**
     * The fire and potion effects an element applies. Weapons read our own config; maces read
     * MaceRituals' config (maces.&lt;id&gt;.fire-seconds / potions).
     */
    public Profile profileOf(String element, boolean weapon) {
        if (weapon) {
            WeaponDef def = weapon(element);
            return def == null ? null : new Profile(def.fireSeconds(), parsePotions(def.potions()));
        }
        ConfigurationSection s = maceRituals.getConfig().getConfigurationSection("maces." + element);
        if (s == null) return null;
        return new Profile(s.getInt("fire-seconds", 0), parsePotions(s.getStringList("potions")));
    }

    public List<Potion> parsePotions(List<String> raw) {
        List<Potion> out = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.split(":");
            if (parts.length < 2) continue;
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
            if (type == null) continue;
            try {
                int ticks = Integer.parseInt(parts[1].trim());
                int amp = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
                out.add(new Potion(type, ticks, amp));
            } catch (NumberFormatException ignored) {
                // skip malformed entry
            }
        }
        return out;
    }

    /** Right-click ability radius of a MaceRituals mace (falls back to 10). */
    public double abilityRadius(String maceId) {
        return maceRituals.getConfig().getDouble("maces." + maceId + ".ability.radius", 10.0);
    }

    // ------------------------------------------------------------------ helpers

    public static Component text(String legacy) {
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String pct(double v) {
        return String.valueOf(Math.round(v * 100.0));
    }
}
