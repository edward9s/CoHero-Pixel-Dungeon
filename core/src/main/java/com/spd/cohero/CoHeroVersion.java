package com.spd.cohero;

/**
 * CoHero's own version, independent from the host SPD build.
 *
 * Keep this class dependency-light so the same compiled class can be reused by
 * source-patch builds and future binary APK injection payloads.
 */
public final class CoHeroVersion {

    public static final String VERSION = "0.1.7";

    private CoHeroVersion() {
    }

    public static String version() {
        return VERSION;
    }

    public static String display(String spdVersion) {
        return display(spdVersion, null);
    }

    public static String display(String spdVersion, String latestVersion) {
        return "CoH v" + VERSION
                + (latestVersion == null ? "" : " | new " + latestVersion)
                + " | SPD v" + spdVersion;
    }
}
