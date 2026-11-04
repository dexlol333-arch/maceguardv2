package dev.maceguard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Combat rules:
 * <ul>
 *   <li>Elemental weapons apply their on-hit effects.</li>
 *   <li>Armor reduces damage, burn/potion duration and knockback from the element it counters,
 *       whether the attacker uses a MaceRituals mace or one of our weapons.</li>
 * </ul>
 */
public final class GuardListener implements Listener {

    /** "This player was just attacked by this element" - used for lightning and knockback. */
    private record Threat(GuardSet set, int until) {
    }

    private final MaceGuard plugin;
    private final GuardManager guards;
    private final Map<UUID, Threat> threats = new HashMap<>();
    private final Map<UUID, Integer> lastFeedback = new HashMap<>();

    public GuardListener(MaceGuard plugin, GuardManager guards) {
        this.plugin = plugin;
        this.guards = guards;
    }

    private int window() {
        return plugin.getConfig().getInt("combat.threat-window-ticks", 12);
    }

    private Threat activeThreat(Player p) {
        Threat t = threats.get(p.getUniqueId());
        if (t == null) return null;
        if (Bukkit.getCurrentTick() > t.until()) {
            threats.remove(p.getUniqueId());
            return null;
        }
        return t;
    }

    // ------------------------------------------------------------------ melee hits

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;

        ItemStack hand = attacker.getInventory().getItemInMainHand();
        GuardManager.WeaponDef def = guards.weaponDefOf(hand);
        String element;
        boolean weapon;
        if (def != null) {
            element = def.element();
            weapon = true;
        } else {
            element = guards.maceElementOf(hand);
            weapon = false;
            if (element == null) return;
        }

        if (weapon && e.getEntity() instanceof LivingEntity target) {
            applyWeaponPassive(e, def, target);
        }

        if (e.getEntity() instanceof Player victim) {
            soak(e, victim, element, weapon);
        }
    }

    private void applyWeaponPassive(EntityDamageByEntityEvent e, GuardManager.WeaponDef def, LivingEntity target) {
        e.setDamage(e.getDamage() + def.bonusDamage());

        if (def.fireSeconds() > 0) {
            target.setFireTicks(Math.max(target.getFireTicks(), def.fireSeconds() * 20));
        }
        for (GuardManager.Potion p : guards.parsePotions(def.potions())) {
            target.addPotionEffect(new PotionEffect(p.type(), p.ticks(), p.amplifier()));
        }
        if (def.lightningChance() > 0 && ThreadLocalRandom.current().nextDouble() < def.lightningChance()) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            double dmg = def.lightningDamage();
            if (dmg > 0) {
                // Next tick, so an armored victim's threat window is already open.
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!target.isDead()) target.damage(dmg);
                });
            }
        }
    }

    private void soak(EntityDamageByEntityEvent e, Player victim, String element, boolean weapon) {
        GuardSet set = guards.setCountering(element);
        if (set == null) return;
        int pieces = guards.piecesWorn(victim, set);
        if (pieces == 0) return;

        double cut = Math.min(1.0, pieces * set.damageReduction());
        e.setDamage(e.getDamage() * (1.0 - cut));

        threats.put(victim.getUniqueId(), new Threat(set, Bukkit.getCurrentTick() + window()));

        // Effects are applied during this event, so soften them on the next tick.
        plugin.getServer().getScheduler().runTask(plugin, () -> soften(victim, element, weapon, set));

        feedback(victim, set, cut);
    }

    /** Shorten (or remove) the fire and potion effects the element just applied. */
    private void soften(Player victim, String element, boolean weapon, GuardSet set) {
        if (!victim.isOnline() || victim.isDead()) return;
        GuardManager.Profile profile = guards.profileOf(element, weapon);
        if (profile == null) return;

        int pieces = guards.piecesWorn(victim, set);
        if (pieces == 0) return;
        double reduction = Math.min(1.0, pieces * set.effectReduction());

        if (profile.fireSeconds() > 0 && victim.getFireTicks() > 0) {
            if (reduction >= 1.0) {
                victim.setFireTicks(0);
            } else {
                victim.setFireTicks((int) Math.round(victim.getFireTicks() * (1.0 - reduction)));
            }
        }

        for (GuardManager.Potion p : profile.potions()) {
            PotionEffect cur = victim.getPotionEffect(p.type());
            // Only touch effects that look like the one just applied, not longer unrelated ones.
            if (cur == null || cur.getDuration() > p.ticks() + 2) continue;
            victim.removePotionEffect(p.type());
            if (reduction < 1.0) {
                int remaining = (int) Math.round(cur.getDuration() * (1.0 - reduction));
                if (remaining > 0) {
                    victim.addPotionEffect(new PotionEffect(p.type(), remaining, cur.getAmplifier(),
                            cur.isAmbient(), cur.hasParticles(), cur.hasIcon()));
                }
            }
        }
    }

    private void feedback(Player victim, GuardSet set, double cut) {
        if (!plugin.getConfig().getBoolean("messages.absorb-feedback", true)) return;
        int now = Bukkit.getCurrentTick();
        Integer last = lastFeedback.get(victim.getUniqueId());
        if (last != null && now - last < 40) return;
        lastFeedback.put(victim.getUniqueId(), now);
        victim.sendActionBar(GuardManager.text(set.name() + " &7absorbed &a" + Math.round(cut * 100) + "%"));
    }

    // ------------------------------------------------------------------ mace abilities

    /**
     * A mace right-click may trigger an ability (flame burst, storm strike, void pull). Open a
     * threat window for nearby wearers of the countering set so the pull / gust is reduced.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player caster = e.getPlayer();
        String mace = guards.maceElementOf(caster.getInventory().getItemInMainHand());
        if (mace == null) return;
        GuardSet set = guards.setCountering(mace);
        if (set == null) return;

        double r = guards.abilityRadius(mace) + 2.0;
        int until = Bukkit.getCurrentTick() + window();
        for (Entity ent : caster.getNearbyEntities(r, r, r)) {
            if (ent instanceof Player p && !p.equals(caster) && guards.piecesWorn(p, set) > 0) {
                threats.put(p.getUniqueId(), new Threat(set, until));
            }
        }
    }

    /** Knockback, wind gusts and void pulls during a threat window. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        Threat t = activeThreat(p);
        if (t == null) return;
        int pieces = guards.piecesWorn(p, t.set());
        if (pieces == 0) return;

        double scale = 1.0 - Math.min(1.0, pieces * t.set().knockbackReduction());
        if (scale <= 0.0) {
            e.setCancelled(true);
        } else {
            e.setVelocity(e.getVelocity().multiply(scale));
        }
    }

    /** Lightning and other plain damage that follows a threat (no damager attached). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOtherDamage(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() != DamageCause.CUSTOM && e.getCause() != DamageCause.LIGHTNING) return;

        Threat t = activeThreat(p);
        if (t == null) return;
        int pieces = guards.piecesWorn(p, t.set());
        if (pieces == 0) return;

        double cut = Math.min(1.0, pieces * t.set().damageReduction());
        e.setDamage(e.getDamage() * (1.0 - cut));
    }

    // ------------------------------------------------------------------ misc

    /** Keep trims (and identity) fixed: guard items cannot be re-trimmed or upgraded. */
    @EventHandler(ignoreCancelled = true)
    public void onSmithing(PrepareSmithingEvent e) {
        ItemStack base = e.getInventory().getInputEquipment();
        if (guards.isGuardItem(base)) {
            e.setResult(null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        threats.remove(e.getPlayer().getUniqueId());
        lastFeedback.remove(e.getPlayer().getUniqueId());
    }
}
