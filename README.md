# CoHero Pixel Dungeon

CoHero Pixel Dungeon is an experimental [Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon) mod where you play with an autonomous second hero.

You control the main Hero. The CoHero explores, fights, uses items, and tries to survive on its own.

## Status

**Early alpha / public playtest.**

The core game is playable, but AI behaviour and balance are still being tuned. Bugs and unexpected decisions are expected.

## Playing with CoHero

- Choose a Hero and a CoHero at the start of each run.
- You control the Hero. The CoHero acts independently, exploring, fighting, collecting items, and trying to survive.
- CoHero usually stays near the Hero and helps when danger arises, but its decisions are not entirely predictable.
- You cannot directly command CoHero's movements or attacks. You can influence it through equipment, items, and your own actions.
- CoHero has its own backpack and equipment, while dungeon resources are shared. Open its backpack using the CoHero backpack icon; a thin gold border marks items it can use or equip.
- Hero talents do not apply to CoHero.
- If either character dies, the run ends. On boss and branch floors, you can choose whether CoHero comes along.

## CoHero class abilities

Each class has an innate passive ability. A second ability unlocks when the **Hero chooses a subclass using Tengu's Mask**.

| Class | Starting passive | Additional passive |
| --- | --- | --- |
| **Warrior** | More strength and health (Might) | Reduced damage taken (Tenacity) |
| **Mage** | Faster wand recharging (Energy) | Resistance to elemental and magical effects (Elements) |
| **Rogue** | Faster movement (Haste) | Faster melee attacks (Furor) |
| **Huntress** | Stronger, more durable thrown weapons (Sharpshooting) | Stronger enchantments and glyphs (Arcana) |
| **Duelist** | Faster melee attacks (Furor) | Reduced damage taken (Tenacity) |
| **Cleric** | Permanent Bless effect | Shares Bless with the nearby Hero |

These are innate bonuses, generally equivalent to **+0 rings**. They use no ring slots, and equipped rings still work normally.

## Settings

- **Enemy spawn multiplier:** Adjust the spawn rate from **1.0x to 4.0x** (default **1.5x**).
- **CoHero debug log:** Records AI decisions and performance information to help diagnose problems. When enabled, exported saves include `cohero-diagnostics.txt`.

Save export/import uses `Documents/spd_saves/<app name>/` on Android and Desktop. CoHero and CoHero + SMM have different app names and therefore keep separate snapshots.

## Downloads

Each release provides four files:

- **CoHero Android:** signed APK
- **CoHero Desktop:** JAR
- **CoHero + SMM Android:** signed APK
- **CoHero + SMM Desktop:** JAR

Download them from the repository's **Releases** page.

## Feedback

Bug reports and gameplay feedback are welcome through GitHub Issues.

The most useful reports include:

- what the CoHero did,
- what you expected it to do,
- the floor / situation where it happened.

For implementation details and current design rules, see [docs/DESIGN.zh-TW.md](docs/DESIGN.zh-TW.md).

## About

CoHero aims to keep Shattered Pixel Dungeon's original mechanics intact while adding the pressure of surviving with an autonomous second hero.

## Dual ultimates

Fight together to build **Link** with melee, thrown weapons, and direct wand hits. The **crossed-blades Tag** shows Link and stores up to three ultimate casts. Tap to use your class-pair ultimate or long-press to read its effects; the companion inventory also explains the current skill.

There are **36 ordered Hero + CoHero class combinations**. The visuals combine each class's motifs, while Link builds only through cooperative attacks. See [detailed rules](docs/DESIGN.zh-TW.md).
