package com.spd.cohero;

/** Standalone formatting checks, runnable without libGDX or Android. */
public final class CoHeroVersionDisplayChecks {
    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        String normal = "CoH v" + CoHeroVersion.VERSION;
        String newer = "CoH v" + CoHeroVersion.VERSION + " | new 9.9.9";
        assertEquals(normal, CoHeroVersion.display(null));
        assertEquals(newer, CoHeroVersion.display("9.9.9"));
        System.out.println("CoHero version display checks passed");
    }
}
