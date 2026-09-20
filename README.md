# CoHero Pixel Dungeon

CoHero Pixel Dungeon is an experimental Shattered Pixel Dungeon mod where you play with an autonomous second hero.

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
- Either Hero dying ends the run.
- Boss and branch floors let you choose whether the CoHero comes with you.
- The CoHero backpack includes an enemy spawn multiplier from **1.0x to 3.0x**, defaulting to **1.5x**, for difficulty tuning.

Long-press the CoHero locator to open its backpack.

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
