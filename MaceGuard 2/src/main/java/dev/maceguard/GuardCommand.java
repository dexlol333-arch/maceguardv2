package dev.maceguard;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GuardCommand implements CommandExecutor, TabCompleter {

    private final MaceGuard plugin;
    private final GuardManager guards;
    private final RecipeManager recipes;

    public GuardCommand(MaceGuard plugin, GuardManager guards, RecipeManager recipes) {
        this.plugin = plugin;
        this.guards = guards;
        this.recipes = recipes;
    }

    private void say(CommandSender to, String msg) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&3&lGUARD &8» ");
        to.sendMessage(GuardManager.text(prefix + msg));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("maceguard.admin")) {
            say(sender, "&cYou don't have permission.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reload();
                say(sender, "&aReloaded config.");
            }
            case "list" -> list(sender);
            case "give" -> give(sender, label, args);
            case "unlock" -> unlock(sender, label, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender s, String label) {
        say(s, "&7Commands:");
        s.sendMessage(GuardManager.text("&8 - &f/" + label + " give <player> armor <set> [helmet|chestplate|leggings|boots|all]"));
        s.sendMessage(GuardManager.text("&8 - &f/" + label + " give <player> weapon <element> [sword|axe|spear|all]"));
        s.sendMessage(GuardManager.text("&8 - &f/" + label + " unlock <player|all>  &7(add all recipes to the recipe book)"));
        s.sendMessage(GuardManager.text("&8 - &f/" + label + " list"));
        s.sendMessage(GuardManager.text("&8 - &f/" + label + " reload"));
    }

    private void list(CommandSender s) {
        say(s, "&7Armor sets:");
        for (GuardSet set : guards.sets()) {
            s.sendMessage(GuardManager.text("&8 - &f" + set.id() + " &7" + set.name() + " &8(counters &e"
                    + set.counters() + "&8, " + set.base().toLowerCase(Locale.ROOT) + ", trim "
                    + set.trimPattern() + "/" + set.trimMaterial() + ")"));
        }
        say(s, "&7Weapon elements:");
        for (GuardManager.WeaponDef def : guards.weapons()) {
            s.sendMessage(GuardManager.text("&8 - &f" + def.element() + " &7" + def.name()
                    + " &8(" + def.base().toLowerCase(Locale.ROOT) + " sword / axe / spear)"));
        }
    }

    private void unlock(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            help(sender, label);
            return;
        }
        if (args[1].equalsIgnoreCase("all")) {
            recipes.unlockAllOnline();
            say(sender, "&aUnlocked all recipes for every online player.");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            say(sender, "&cPlayer not found: &f" + args[1]);
            return;
        }
        recipes.unlock(target);
        say(sender, "&aUnlocked all recipes for &f" + target.getName() + "&a.");
    }

    private void give(CommandSender sender, String label, String[] args) {
        if (args.length < 4) {
            help(sender, label);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            say(sender, "&cPlayer not found: &f" + args[1]);
            return;
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        String which = args.length > 4 ? args[4] : "all";
        List<ItemStack> items = new ArrayList<>();

        if (type.equals("armor")) {
            GuardSet set = guards.set(args[3]);
            if (set == null) {
                say(sender, "&cUnknown armor set: &f" + args[3]);
                return;
            }
            if (which.equalsIgnoreCase("all")) {
                for (GuardManager.Piece p : GuardManager.Piece.values()) items.add(guards.buildArmor(set, p));
            } else {
                GuardManager.Piece p = GuardManager.Piece.parse(which);
                if (p == null) {
                    say(sender, "&cUnknown piece: &f" + which);
                    return;
                }
                items.add(guards.buildArmor(set, p));
            }
        } else if (type.equals("weapon")) {
            GuardManager.WeaponDef def = guards.weapon(args[3]);
            if (def == null) {
                say(sender, "&cUnknown weapon element: &f" + args[3]);
                return;
            }
            if (which.equalsIgnoreCase("all")) {
                for (GuardManager.Kind k : GuardManager.Kind.values()) items.add(guards.buildWeapon(def, k));
            } else {
                GuardManager.Kind k = GuardManager.Kind.parse(which);
                if (k == null) {
                    say(sender, "&cUnknown weapon type: &f" + which);
                    return;
                }
                items.add(guards.buildWeapon(def, k));
            }
        } else {
            help(sender, label);
            return;
        }

        int given = 0;
        for (ItemStack item : items) {
            if (item == null) continue; // material missing on this server version; already logged
            Map<Integer, ItemStack> left = target.getInventory().addItem(item);
            for (ItemStack extra : left.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), extra);
            }
            given++;
        }
        if (given == 0) {
            say(sender, "&cNothing was given (check the console for missing materials).");
        } else {
            say(sender, "&aGave &f" + given + " &aitem(s) to &f" + target.getName() + "&a.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("maceguard.admin")) return out;

        if (args.length == 1) {
            out.addAll(List.of("give", "unlock", "list", "reload", "help"));
        } else if (args[0].equalsIgnoreCase("unlock")) {
            if (args.length == 2) {
                out.add("all");
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
        } else if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (args.length == 3) {
                out.addAll(List.of("armor", "weapon"));
            } else if (args.length == 4) {
                if (args[2].equalsIgnoreCase("armor")) {
                    for (GuardSet s : guards.sets()) out.add(s.id());
                } else if (args[2].equalsIgnoreCase("weapon")) {
                    for (GuardManager.WeaponDef d : guards.weapons()) out.add(d.element());
                }
            } else if (args.length == 5) {
                out.add("all");
                if (args[2].equalsIgnoreCase("armor")) {
                    for (GuardManager.Piece p : GuardManager.Piece.values()) out.add(p.name().toLowerCase(Locale.ROOT));
                } else {
                    for (GuardManager.Kind k : GuardManager.Kind.values()) out.add(k.name().toLowerCase(Locale.ROOT));
                }
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
