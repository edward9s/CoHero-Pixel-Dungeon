# Developer notes — CoHero Pixel Dungeon

Maintainer-only release and test build policy. Player-facing information remains in the [README](../README.md).

## Build and release policy

The CoHero version is manually set in `core/src/main/java/com/spd/cohero/CoHeroVersion.java`. CI does not increment it.

- **Scheduled `Release CoHero` (13:00 Taiwan time):** The official Release tag uses **both CoHero and SPD versions**, `CoHero-v<cohero>-SPD-v<spd>`. If this exact pair is already published, the schedule skips compilation and leaves all official assets unchanged. If either version changes to a previously unpublished pair, the schedule builds four APK/JAR assets and creates a new official Release. SMM-only and source-code-only changes never trigger a scheduled rebuild.
- **Manual `Release CoHero`:** Always compile and publish all four APK/JAR assets. If the official tag already exists, the workflow replaces the files and refreshes the release. Replaced official assets lose their individual download counts.
- **Manual `Build CoHero`:** Always compile and replace the four APK/JAR assets in the rolling **`build-test` prerelease**. This workflow has **no schedule**, uses the latest published SPD and SMM `main`, checks the required signing secrets, and verifies the signed APK files before uploading. Replaced test assets also lose their download counts.

Official releases use the latest published SMM version for the bundled variant, but **SMM is not part of the official Release tag or automatic build trigger**.
