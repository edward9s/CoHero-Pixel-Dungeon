package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;

/** CoHero message namespace backed by SPD's standard I18N bundles. */
final class CoHeroMessages {

    private static final String PREFIX = "cohero.";

    private CoHeroMessages() {
    }

    static String get(String key, Object... args) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("CoHero message key must not be empty");
        }
        return Messages.get(PREFIX + key, args);
    }
}
