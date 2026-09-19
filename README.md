# CoHero Pixel Dungeon

CoHero Pixel Dungeon is an experimental Shattered Pixel Dungeon derivative built around one change: the player directly controls one hero while a second hero explores and fights autonomously.

The goal is not to redesign Shattered Pixel Dungeon. The project should preserve upstream mechanics as much as possible and make the new gameplay emerge from having to survive alongside an autonomous second hero while sharing the same dungeon resources.

Key ideas:

- The player controls only the primary hero.
- The companion hero has its own inventory and equipment resources.
- The companion moves and explores autonomously.
- Either hero dying ends the run.
- The companion itself remains visible even outside the player's field of view, without sharing its surrounding vision.
- The player cannot stop the companion from progressing.
- The player does not issue movement or combat commands to the companion; inventory and equipment allocation are the control interface.
- Companion combat behaviour is driven primarily by currently available combat capabilities, target properties, range, and damage rather than hard-coded class logic.
- The companion should avoid intentionally waking sleeping enemies when a reasonable route can keep its distance.
- The project should remain close enough to upstream Shattered Pixel Dungeon to follow future releases and, where practical, support an SMM-like injection path into compatible forks.

See [docs/DESIGN.zh-TW.md](docs/DESIGN.zh-TW.md) for the current design consensus and unresolved questions.
