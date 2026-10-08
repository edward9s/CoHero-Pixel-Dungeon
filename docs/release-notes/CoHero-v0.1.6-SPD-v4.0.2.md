## Changes

- Updated the base game from Shattered Pixel Dungeon v4.0.1 to v4.0.2.
- **Closer idle exploration:** CoHero explores reachable unknown tiles within 10 tiles of the Hero, then roams known areas within 6 tiles. It returns toward the Hero if it strays too far; single-exit room guarding keeps its own patrol area.
- **Faster Hero support:** Nearby enemies now trigger support within 6 tiles, and ranged threats can trigger support up to 10 tiles away (previously 4 and 8). Safer meeting points and room-guarding movement reduce unnecessary doorway oscillation.
- **Improved combat tactics:** CoHero prioritizes necromancers, including spectral necromancers, when defeating the summoner also removes its summons. Target selection and positioning better account for tactical threats.
- **More measured survival decisions:** Retreat considers estimated danger and whether the Hero can help, rather than treating enemy numbers alone as an emergency. Healing is still available below 35% HP; emergency consumables are reserved for serious immediate danger, with instant shielding favored over gradual healing.
- **Expanded resource collection:** CoHero automatically collects more runestones, seeds, potions, scrolls, food, and crafting materials, keeping items it can use and transferring other resources to the Hero. Automatic equipment upgrades remain restricted to eligible gear CoHero picks up itself.
- **Shared dewdrop recovery:** Collected dewdrops can heal an injured CoHero or refill the Hero's Waterskin, depending on current health and Waterskin capacity.
- **Safer environmental responses:** When affected by Burning or Ooze, CoHero can seek a nearby known water tile if it can reach it safely. Hazard avoidance also better handles threats such as Death Gaze and special vault mechanisms.
- **Special-area transition fixes:** Forced exits in the Imp's vault now preserve the CoHero's state, improving companion handling during vault transitions.

> Built against Shattered Pixel Dungeon v4.0.2. The optional CoHero + SMM build uses Shattered Master Mode m0.3.9.

## Downloads

- CoHero Android APK
- CoHero Desktop JAR
- CoHero + SMM Android APK
- CoHero + SMM Desktop JAR
