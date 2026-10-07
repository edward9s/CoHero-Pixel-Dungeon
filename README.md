# CoHero Pixel Dungeon

CoHero Pixel Dungeon is an experimental [Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon) mod where you play with an autonomous second hero.

You control the main Hero. The CoHero explores, fights, uses items, and tries to survive on its own.

## Status

**Early alpha / public playtest.**

The core game is playable, but AI behaviour and balance are still being tuned. Bugs and unexpected decisions are expected.

## How it works

- Choose a Hero and a CoHero at the start of a run.
- You directly control only the main Hero.
- The CoHero moves, explores, and fights by itself.
- The CoHero has its own backpack, weapons, armor, rings, wands, and consumables.
- Dungeon resources are shared between both heroes.
- Giving the CoHero different equipment is the main way to influence its behaviour.
- Auto-equip is part of auto-loot, not a continuous backpack optimizer: when the CoHero itself picks up a known-uncursed, usable melee weapon or armor that is strictly better than its current equipment, it equips it immediately. Items manually transferred into the CoHero backpack are not auto-equipped.
- Either Hero dying ends the run.
- Boss and branch floors let you choose whether the CoHero comes with you.
- The **Settings** menu includes an enemy spawn multiplier from **1.0x to 4.0x** in **0.25x** steps, defaulting to **1.5x**, for difficulty tuning.

Use the CoHero backpack tag to open its inventory.

## What to expect from the CoHero AI

The CoHero is designed to be autonomous rather than a second character you manually command. Its behaviour should be understandable and reasonably predictable, but not fully deterministic.

- **Passive enemies and hazards:** the CoHero does not proactively attack passive Statues. It avoids Piranha-occupied water and the shoreline attack zone around that water. If it ends up inside that danger zone, leaving it takes priority over fighting.
- **Off-screen actions:** gameplay outside the Hero's field of view resolves immediately instead of waiting for every CoHero animation to finish. Remote animations are presentation only and do not block the Hero's turns.
- **Room guarding:** when the Hero stays in a suitable single-exit room, the CoHero may patrol the area outside the entrance instead of following inside. This commonly applies to places such as shops and alchemy rooms.
- **Sacrificial Fire:** when the Hero is in a room with active Sacrificial Fire, the CoHero can position itself to lure suitable melee enemies toward the sacrifice area instead of simply fighting them wherever they are.
- **Hero support:** an enemy within **6 tiles** of the Hero triggers nearby support. Enemies farther away can also trigger support out to **10 tiles** when they can attack the Hero from range. This support trigger is separate from attack selection, so a sleeping or passive enemy can make the CoHero move closer without making it proactively attack that enemy.
- **Idle exploration:** when combat, survival, support, guarding, and useful loot do not take priority, the CoHero explores on its own and chooses roaming targets with some randomness.
- **No direct movement or attack commands:** protecting the CoHero is part of the run. Equipment, backpack contents, positioning, and the Hero's own decisions are the main ways to influence what the CoHero can do.
- **Class traits:** Hero talents are not copied onto the CoHero. Each stock CoHero class instead has its own intrinsic passive effect. After the Hero completes the Tengu-mask subclass choice, the CoHero unlocks its second class passive.
- **Items and equipment:** the CoHero picks up resources and uses supported items when appropriate. In the CoHero backpack, items with a currently supported use/equip capability are marked with a thin gold frame. Items the CoHero collects but cannot use may be routed to the Hero instead.

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
