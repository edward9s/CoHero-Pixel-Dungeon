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

## Cooperative ultimates

> **Highly experimental:** Cooperative ultimates are still being tested. Their names, effects, balance, and even underlying mechanics are likely to change substantially in future updates.

When **both the Hero and CoHero damage enemies in the same fight**, they build **Link** for a cooperative ultimate. Solo attacks do not build Link. The Hero's class determines the main attack, while CoHero's class adds a follow-up, creating **36 ordered class combinations**.

To use an ultimate, both heroes must be nearby and able to act. Tap the **crossed-swords Tag** to select an enemy (tap again to confirm, or choose another enemy on the map). Cleric-led support skills activate immediately instead. Both heroes spend a turn, and saved Link carries between floors.

### Hero's main skill

| Hero class | Main effect |
| --- | --- |
| **Warrior** | A shockwave damages enemies around the target, then safely knocks surviving foes back **after CoHero's follow-up**. |
| **Mage** | A magical blast damages clustered enemies and weakens and cripples foes in a wider area. |
| **Rogue** | A powerful strike deals extra damage to wounded prey, then grants the Hero 6 turns of invisibility without moving. |
| **Huntress** | Fires a prismatic ray from the Hero that pierces enemies along its path until blocked by terrain, and briefly blinds the target. |
| **Duelist** | Strikes the target, sweeps nearby enemies around either hero, and grants Bless to both. |
| **Cleric** | Uses a dedicated rescue or support skill based on CoHero's class, listed below. No enemy target is needed. |

### CoHero's follow-up

These effects accompany the five offensive Hero skills:

| CoHero class | Follow-up effect |
| --- | --- |
| **Warrior** | Shields both heroes and, when safe, knocks the surviving target back. |
| **Mage** | Chains damage to other nearby enemies through clear terrain and weakens survivors. |
| **Rogue** | Makes the surviving target vulnerable and briefly hides CoHero. |
| **Huntress** | Fires a second prismatic ray **from CoHero's position** and blinds the target. Enemies already hit by the first ray are not hit again. |
| **Duelist** | Strikes the surviving target again when close enough, cripples it, and gains Bless. |
| **Cleric** | Cleanses and heals both heroes, adding protective shields if either is still badly wounded. |

### Cleric-led ultimates

When the **Hero is a Cleric**, CoHero's class selects one of six standalone support skills:

| CoHero class | Ultimate | Effect |
| --- | --- | --- |
| **Warrior** | **Guardian's Oath** | Gives both heroes strong shields and Bless. |
| **Mage** | **Holy Purification** | Damages and cripples enemies around the Hero while cleansing and healing both heroes. |
| **Rogue** | **Veiled Blessing** | Heals both heroes and makes them temporarily invisible for a retreat. |
| **Huntress** | **Sanctuary Arrows** | Heals both heroes while damaging and blinding reachable enemies near CoHero. |
| **Duelist** | **Oathbound Riposte** | Grants both heroes protective shields and a longer Bless for holding their ground. |
| **Cleric** | **Twin Sanctuary** | Strongly heals, cleanses, and shields both heroes. |

Each pairing has its own name. **Long-press the crossed-swords Tag or open CoHero's backpack** to see the name and full effects of your current combination.

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
