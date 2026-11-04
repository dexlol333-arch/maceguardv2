# MaceGuard

Add-on for **MaceRituals** (Paper 1.21.x, Java 21). Adds three trimmed armor sets that counter the
three maces, plus an elemental sword, axe and spear for each element.

| Armor set   | Counters     | Trim (pattern / material) | Base      |
|-------------|--------------|---------------------------|-----------|
| Frostguard  | Inferno      | tide / diamond            | Iron      |
| Groundwire  | Tempest      | bolt / copper             | Diamond   |
| Voidanchor  | Voidbreaker  | ward / amethyst           | Netherite |

Weapons: `inferno`, `tempest`, `voidbreaker` x `sword`, `axe`, `spear`.

Each armor piece reduces, per piece worn (a full set is 4x, capped at 100%), damage, burn/potion
duration and knockback/pull from the element it counters, whether the attacker uses a MaceRituals
mace or one of these weapons. Everything is configurable in `config.yml`.

## Crafting
Every piece and tool is a real crafting recipe, listed in the crafting table's recipe book:

    A B A     A = corner items (x4)   B = edge items (x3)
    B X B     X = base armor piece / tool
    A C A     C = Heavy Core of the matching element (checked by the plugin)

| Item | Corners (A) | Edges (B) | Core |
|---|---|---|---|
| Frostguard armor | Blue Ice | Diamond | Inferno |
| Groundwire armor | Lightning Rod | Copper Block | Tempest |
| Voidanchor armor | Ender Eye | Amethyst Block | Voidbreaker |
| Inferno tools | Blaze Rod | Magma Block | Inferno |
| Tempest tools | Lightning Rod | Wind Charge | Tempest |
| Voidbreaker tools | Ender Eye | Echo Shard | Voidbreaker |

Click a recipe in the book to fill the grid, then take the result. Forging plays a short
ceremony (lightning flash, particles, title, optional server broadcast). Edit `recipes:` in
`config.yml` to change ingredients.

## Commands (`maceguard.admin`)
- `/maceguard give <player> armor <set> [helmet|chestplate|leggings|boots|all]`
- `/maceguard give <player> weapon <element> [sword|axe|spear|all]`
- `/maceguard unlock <player|all>` adds every recipe to the recipe book
- `/maceguard list`, `/maceguard reload`

## Build
`mvn package` -> `target/MaceGuard.jar`. Put it in `plugins/` next to MaceRituals.
Spears need Minecraft 1.21.11+ (the plugin logs a warning and skips them otherwise).
