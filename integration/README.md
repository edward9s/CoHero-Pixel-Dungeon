# CoHero integration profiles

CoHero-owned Java lives under `core/src/main/java/com/spd/cohero` and is intended to stay shared across supported Pixel Dungeon forks.

Fork-specific source patching belongs under `integration/<fork>/`. The current Shattered profile is `integration/shattered/apply.sh`.

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

## Porting rule

Do not add fork detection or compatibility branches to common CoHero Java just to make a new fork build.

Prefer a new `integration/<fork>/apply.sh` plus exact, fail-fast patch scripts for that fork. Shared Java should change only when the underlying CoHero behavior itself is shared.

Profiles should preserve patch order explicitly and fail as soon as an expected upstream anchor or file no longer matches. This keeps upstream drift visible instead of silently producing a partially patched build.
