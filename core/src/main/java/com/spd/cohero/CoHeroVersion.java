package com.spd.cohero;

/**
 * CoHero's own version, independent from the host SPD build.
 *
 * Keep this class dependency-light so the same compiled class can be reused by
 * source-patch builds and future binary APK injection payloads.
 */
public final class CoHeroVersion {

    public static final String VERSION = "0.1.3";

    private CoHeroVersion() {
    }

    public static String version() {
        return VERSION;
    }

    public static String display(String spdVersion) {
        return "SPD v" + spdVersion + " | CoH v" + VERSION;
    }
}
