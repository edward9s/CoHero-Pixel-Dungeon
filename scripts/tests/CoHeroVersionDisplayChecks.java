package com.spd.cohero;

/** Standalone formatting checks, runnable without libGDX or Android. */
public final class CoHeroVersionDisplayChecks {
    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        String normal = "CoH v" + CoHeroVersion.VERSION + " | SPD v4.0.2";
        String newer = "CoH v" + CoHeroVersion.VERSION
                + " | new 9.9.9 | SPD v4.0.2";
        assertEquals(normal, CoHeroVersion.display("4.0.2"));
        assertEquals(normal, CoHeroVersion.display("4.0.2", null));
        assertEquals(newer, CoHeroVersion.display("4.0.2", "9.9.9"));
        System.out.println("CoHero version display checks passed");
    }
}
