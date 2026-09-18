package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Poison;
import com.shatteredpixel.shatteredpixeldungeon.effects.Flare;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfExperience;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.audio.Sample;

/**
 * Explicit adapters for Hero-bound consumables that have clear CoHero semantics.
 * Unknown consumables are intentionally unsupported rather than guessed compatible.
 */
final class CompanionItemUse {

    private CompanionItemUse() {
    }

    static boolean supported(Item item) {
        return item instanceof PotionOfHealing
                || item instanceof PotionOfExperience
                || item instanceof PotionOfStrength;
    }

    static void use(CoHeroAlly companion, Item item) {
        if (companion == null || !companion.isAlive()) {
            throw new IllegalArgumentException("companion must be alive");
        }
        if (!supported(item)) {
            throw new IllegalArgumentException("Unsupported CoHero consumable: "
                    + (item == null ? "null" : item.getClass().getName()));
        }
        if (!companion.inventory().containsInBackpack(item)) {
            throw new IllegalArgumentException("Consumable must be in the CoHero backpack");
        }

        // Match stock Potion.drink ordering: consume first, then spend the user's action and apply.
        companion.inventory().consumeOne(item);
        companion.spendItemUseTime();

        if (item instanceof PotionOfHealing) {
            useHealing(companion, (PotionOfHealing) item);
        } else if (item instanceof PotionOfExperience) {
            useExperience(companion, (PotionOfExperience) item);
        } else if (item instanceof PotionOfStrength) {
            useStrength(companion, (PotionOfStrength) item);
        } else {
            throw new IllegalStateException("Supported CoHero consumable has no adapter: "
                    + item.getClass().getName());
        }

        Sample.INSTANCE.play(Assets.Sounds.DRINK);
        if (companion.sprite != null) {
            companion.sprite.operate(companion.pos);
        }
        Catalog.countUse(item.getClass());
    }

    private static void useHealing(CoHeroAlly companion, PotionOfHealing potion) {
        potion.identify();
        PotionOfHealing.cure(companion);

        if (Dungeon.isChallenged(Challenges.NO_HEALING)) {
            // Stock pharmacophobia scales poison from Hero level. CoHero uses its own level.
            Buff.affect(companion, Poison.class).set(4 + companion.level() / 2);
        } else {
            PotionOfHealing.heal(companion);
            GLog.p(Messages.get(PotionOfHealing.class, "heal"));
        }
    }

    private static void useExperience(CoHeroAlly companion, PotionOfExperience potion) {
        potion.identify();
        if (Dungeon.hero == null) {
            throw new IllegalStateException("CoHero experience potion used without Dungeon.hero");
        }

        int amount = Dungeon.hero.maxExp();
        if (companion.sprite != null) {
            companion.sprite.showStatusWithIcon(
                    CharSprite.POSITIVE,
                    Integer.toString(amount),
                    FloatingText.EXPERIENCE);
            new Flare(6, 32).color(0xFFFF00, true).show(companion.sprite, 2f);
        }
        Dungeon.hero.earnExp(amount, PotionOfExperience.class);
    }

    private static void useStrength(CoHeroAlly companion, PotionOfStrength potion) {
        potion.identify();
        if (Dungeon.hero == null) {
            throw new IllegalStateException("CoHero strength potion used without Dungeon.hero");
        }

        // STR is deliberately shared. Dungeon.hero is the one mutable authority.
        Dungeon.hero.STR++;
        if (companion.sprite != null) {
            companion.sprite.showStatusWithIcon(CharSprite.POSITIVE, "1", FloatingText.STRENGTH);
        }
        GLog.p(Messages.get(potion, "msg", companion.STR()));
        Badges.validateStrengthAttained();
        Badges.validateDuelistUnlock();
    }
}
