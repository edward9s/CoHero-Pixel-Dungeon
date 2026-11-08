# CoHero integration profiles

CoHero-owned Java lives under `core/src/main/java/com/spd/cohero` and is intended to stay shared across supported Pixel Dungeon forks.

Fork-specific source patching belongs under `integration/<fork>/`. The current Shattered profile is `integration/shattered/apply.sh`, with its Shattered-only patch implementations under `integration/shattered/patches/`.

## Profile phases

Every profile should keep the same small phase contract when possible:

- `base`: apply the fork-specific source hooks required before compilation.
- `wndgame`: patch the game-menu integration after any other mod that may replace or rewrite `WndGame`.
- `messages`: install CoHero message keys into the fork's message resources.

The workflow passes the upstream checkout root and CoHero checkout root explicitly:

```sh
bash cohero/integration/shattered/apply.sh base spd cohero
bash cohero/integration/shattered/apply.sh wndgame spd cohero
bash cohero/integration/shattered/apply.sh messages spd cohero
```

## Blast-style alchemy options

The Shattered integration includes reverse runestone alchemy and its original-style
alchemy guide page, with one runestone icon displaying quantity 2. Two matching
runestones make one identified scroll at no energy cost; enchantment stones
cannot be reversed by default. The compile-time
`CoHeroSettings.ENABLE_ENCHANTMENT_STONE_REVERSE_ALCHEMY` switch (default
`false`) controls the enchantment-stone exception.

Wands and upgradeable thrown weapons with *known curse status* may also be
recycled into arcane resin and liquid metal, including cursed ones. The
original ingredient requirements, output quantity formulas, and energy costs
are otherwise unchanged.

The profile translates the nine original alchemy guide categories to ten
display categories without rewriting saved document identifiers. The new
reverse-runestone category shares the original runestone page's unlock state.
All seven Shattered-specific alchemy patches run in the `base` phase before
either the regular CoHero or the SMM overlay is built. The `messages` phase
installs the new `cohero.alchemy.reverse_stones.*` strings.

## Porting rule

Do not add fork detection or compatibility branches to common CoHero Java just to make a new fork build.

Prefer a new `integration/<fork>/apply.sh` plus exact, fail-fast patch scripts under that fork's own `patches/` directory. Shared Java should change only when the underlying CoHero behavior itself is shared.

Profiles should preserve patch order explicitly and fail as soon as an expected upstream anchor or file no longer matches. This keeps upstream drift visible instead of silently producing a partially patched build.


Top-level `scripts/` is reserved for fork-independent repository tooling such as version extraction and source-layout validation. Do not put host-specific patch implementations there.

For Java integration hooks, prefer one patch script per upstream Java file. Do not group unrelated host files into one atomic patch command; keep failures localized to the file that drifted.
