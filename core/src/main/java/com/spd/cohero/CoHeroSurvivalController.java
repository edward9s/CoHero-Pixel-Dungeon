package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.AllyBuff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barkskin;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.LostInventory;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Stamina;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHaste;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfCleansing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfEarthenArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfStamina;
import com.shatteredpixel.shatteredpixeldungeon.plants.Earthroot;
import com.shatteredpixel.shatteredpixeldungeon.plants.Fadeleaf;
import com.shatteredpixel.shatteredpixeldungeon.plants.Mageroyal;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;
import com.shatteredpixel.shatteredpixeldungeon.effects.SpellSprite;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;

final class CoHeroSurvivalController {

    private final CoHeroAlly owner;

    CoHeroSurvivalController(CoHeroAlly owner) {
        this.owner = owner;
    }

    private boolean hasSeriousCleansableNegative() {
        int negatives = 0;
        for (Buff active : owner.buffs()) {
            if (active.type != Buff.buffType.NEGATIVE
                    || active instanceof AllyBuff
                    || active instanceof LostInventory) {
                continue;
            }
            negatives++;
            if (active instanceof Buff.DOTbuff) {
                return true;
            }
        }

        return owner.rooted
                || negatives >= 2
                || (negatives > 0 && owner.HT > 0 && owner.HP * 100 < owner.HT * 50);
    }

    boolean tryUseCombatStamina(
            Mob targetMob, ArrayList<Mob> threats, CoHeroCombatRisk risk) {
        if (risk == null) {
            throw new IllegalArgumentException("Combat stamina requires current combat risk");
        }
        if (targetMob == null
                || threats == null
                || threats.isEmpty()
                || risk.retreat
                || owner.buff(Stamina.class) != null
                || owner.buff(Haste.class) != null
                || owner.buff(Invisibility.class) != null) {
            return false;
        }

        boolean rangedPressure = owner.hasRangedPressure(threats);
        boolean multipleThreats = threats.size() >= 2;
        boolean bossFight = Char.hasProp(targetMob, Char.Property.BOSS)
                || Char.hasProp(targetMob, Char.Property.MINIBOSS);

        float outgoing = risk.outgoingDpt;
        boolean shortTrivialFight = threats.size() == 1
                && !rangedPressure
                && !bossFight
                && outgoing > 0.01f
                && targetMob.HP <= outgoing;

        if (shortTrivialFight || (!multipleThreats && !rangedPressure && !bossFight)) {
            return false;
        }

        Potion potion = owner.inventory().takeOneAutoStaminaPotion();
        if (!(potion instanceof PotionOfStamina)) {
            return false;
        }

        Buff.prolong(owner, Stamina.class, Stamina.DURATION);
        Catalog.countUse(PotionOfStamina.class);
        SpellSprite.show(owner, SpellSprite.HASTE, 0.5f, 1f, 0.5f);
        Sample.INSTANCE.play(Assets.Sounds.DRINK);
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    boolean tryUseCleansingPotion(CoHeroCombatRisk risk) {
        if (!hasSeriousCleansableNegative()) {
            return false;
        }

        // Do not spend an emergency turn cleansing when the current volley is already lethal.
        // Controlled/random teleport or immediate shielding remain safer in that situation.
        if (risk != null && risk.immediateIncoming * 1.35f >= owner.HP + owner.shielding()) {
            return false;
        }

        Potion potion = owner.inventory().takeOneAutoCleansingPotion();
        if (!(potion instanceof PotionOfCleansing)) {
            return false;
        }

        PotionOfCleansing.cleanse(owner);
        Catalog.countUse(PotionOfCleansing.class);
        Sample.INSTANCE.play(Assets.Sounds.DRINK);
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    Boolean tryKnownRecoveryPlant() {
        // Sungrass only heals while its target remains on the activation cell.
        if (owner.buff(Sungrass.Health.class) != null && owner.HP < owner.HT) {
            owner.spendActionTime(Actor.TICK);
            return true;
        }

        if (hasSeriousCleansableNegative()) {
            int mageroyal = nearestKnownPlantCell(Mageroyal.class, 4);
            if (mageroyal != -1) {
                return moveTowardKnownPlant(mageroyal);
            }
        }

        if (owner.HT > 0
                && owner.HP * 100 < owner.HT * 60
                && owner.buff(Healing.class) == null) {
            int sungrass = nearestKnownPlantCell(Sungrass.class, 6);
            if (sungrass != -1) {
                return moveTowardKnownPlant(sungrass);
            }
        }

        return null;
    }

    Boolean tryKnownCleansingPlant() {
        if (owner.rooted || !hasSeriousCleansableNegative()) {
            return null;
        }

        int mageroyal = adjacentKnownPlantCell(Mageroyal.class);
        return mageroyal == -1 ? null : moveOntoAdjacentPlant(mageroyal);
    }

    Boolean tryKnownCombatArmorPlant(Mob targetMob, ArrayList<Mob> threats) {
        if (owner.rooted || targetMob == null || threats == null || threats.isEmpty()) {
            return null;
        }

        boolean hardFight = threats.size() >= 2
                || Char.hasProp(targetMob, Char.Property.BOSS)
                || Char.hasProp(targetMob, Char.Property.MINIBOSS);
        if (!hardFight
                || owner.buff(Earthroot.Armor.class) != null
                || Barkskin.currentLevel(owner) > 0) {
            return null;
        }

        int earthroot = adjacentKnownPlantCell(Earthroot.class);
        return earthroot == -1 ? null : moveOntoAdjacentPlant(earthroot);
    }

    Boolean tryKnownRetreatPlant(CoHeroCombatRisk risk, ArrayList<Mob> threats) {
        if (owner.rooted || risk == null || threats == null || threats.isEmpty()) {
            return null;
        }

        boolean severe = risk.attackersNow >= 2 || risk.ttd <= 3f;
        if (severe) {
            int fadeleaf = adjacentKnownPlantCell(Fadeleaf.class);
            if (fadeleaf != -1) {
                return moveOntoAdjacentPlant(fadeleaf);
            }
        }

        if (hasSeriousCleansableNegative()
                && risk.immediateIncoming * 1.35f < owner.HP + owner.shielding()) {
            int mageroyal = adjacentKnownPlantCell(Mageroyal.class);
            if (mageroyal != -1) {
                return moveOntoAdjacentPlant(mageroyal);
            }
        }

        return null;
    }

    private int adjacentKnownPlantCell(Class<? extends Plant> plantType) {
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (!Dungeon.level.insideMap(cell)
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || !Dungeon.level.visited[cell]
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !owner.isMovementSafe(cell)) {
                continue;
            }

            Plant plant = Dungeon.level.plants.get(cell);
            if (plantType.isInstance(plant)) {
                return cell;
            }
        }
        return -1;
    }

    private int nearestKnownPlantCell(Class<? extends Plant> plantType, int maxDistance) {
        PathFinder.buildDistanceMap(owner.pos, Dungeon.level.passable, maxDistance);

        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (Plant plant : Dungeon.level.plants.valueList()) {
            if (!plantType.isInstance(plant)) {
                continue;
            }
            int cell = plant.pos;
            if (!Dungeon.level.visited[cell]
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !owner.isMovementSafe(cell)
                    || PathFinder.distance[cell] == Integer.MAX_VALUE) {
                continue;
            }

            if (PathFinder.distance[cell] < bestDistance) {
                best = cell;
                bestDistance = PathFinder.distance[cell];
            }
        }
        return best;
    }

    private Boolean moveTowardKnownPlant(int plantCell) {
        if (plantCell == -1 || owner.rooted) {
            return null;
        }

        int oldPos = owner.pos;
        if (!owner.getCloser(plantCell)) {
            return null;
        }

        owner.spendActionTime(1 / owner.speed());
        owner.refreshOwnFieldOfView();
        return owner.finishMovementAnimation(oldPos);
    }

    private Boolean moveOntoAdjacentPlant(int plantCell) {
        if (plantCell == -1
                || owner.rooted
                || Dungeon.level.distance(owner.pos, plantCell) != 1) {
            return null;
        }

        int oldPos = owner.pos;
        owner.setMovementDecision("plant_move", plantCell);
        owner.move(plantCell, true);
        owner.spendActionTime(1 / owner.speed());
        owner.refreshOwnFieldOfView();

        // Fadeleaf teleports during Level.occupyCell(). Its teleport VFX already placed the sprite,
        // so do not draw a second long-distance movement animation from the pre-plant cell.
        if (owner.pos != plantCell) {
            owner.clearNavigationPath();
            return true;
        }
        return owner.finishMovementAnimation(oldPos);
    }

    boolean tryUseCombatEarthenArmor(
            Mob targetMob, ArrayList<Mob> threats, CoHeroCombatRisk risk) {
        if (risk == null) {
            throw new IllegalArgumentException("Combat earthen armor requires current combat risk");
        }
        if (targetMob == null
                || threats == null
                || threats.isEmpty()
                || risk.retreat
                || Barkskin.currentLevel(owner) > 0
                || owner.buff(Earthroot.Armor.class) != null) {
            return false;
        }

        boolean hardFight = threats.size() >= 2
                || Char.hasProp(targetMob, Char.Property.BOSS)
                || Char.hasProp(targetMob, Char.Property.MINIBOSS);
        if (!hardFight) {
            return false;
        }

        Potion potion = owner.inventory().takeOneAutoEarthenArmorPotion();
        if (!(potion instanceof PotionOfEarthenArmor)) {
            return false;
        }

        Barkskin.conditionallyAppend(owner, 2 + owner.level() / 3, 50);
        Catalog.countUse(PotionOfEarthenArmor.class);
        Sample.INSTANCE.play(Assets.Sounds.DRINK);
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    boolean tryAutoSurvivalPotion() {
        if (!owner.isBelowLowHealthThreshold()) {
            return false;
        }
        return consumeSurvivalPotion(false);
    }

    boolean tryUseInvisibilityPotion() {
        if (owner.buff(Invisibility.class) != null) {
            return false;
        }

        Potion potion = owner.inventory().takeOneAutoInvisibilityPotion();
        if (!(potion instanceof PotionOfInvisibility)) {
            return false;
        }

        Buff.prolong(owner, Invisibility.class, Invisibility.DURATION);
        Catalog.countUse(PotionOfInvisibility.class);
        Sample.INSTANCE.play(Assets.Sounds.DRINK);
        Sample.INSTANCE.play(Assets.Sounds.MELD);
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    boolean tryUseHastePotion() {
        if (owner.buff(Haste.class) != null || owner.buff(Stamina.class) != null) {
            return false;
        }

        Potion potion = owner.inventory().takeOneAutoHastePotion();
        if (!(potion instanceof PotionOfHaste)) {
            return false;
        }

        Buff.prolong(owner, Haste.class, Haste.DURATION);
        Catalog.countUse(PotionOfHaste.class);
        SpellSprite.show(owner, SpellSprite.HASTE, 1f, 1f, 0f);
        Sample.INSTANCE.play(Assets.Sounds.DRINK);
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    boolean tryEmergencySurvivalPotion() {
        return consumeSurvivalPotion(true);
    }

    private boolean consumeSurvivalPotion(boolean shieldingFirst) {
        if (shieldingFirst && tryConsumeShieldingPotion()) {
            return true;
        }

        // Healing is the normal first choice, but not during a trapped emergency because its
        // recovery is spread over future turns.
        if (owner.buff(Healing.class) == null) {
            Potion healing = owner.inventory().takeOneAutoHealingPotion();
            if (healing != null) {
                PotionOfHealing.cure(owner);
                PotionOfHealing.heal(owner);
                Sample.INSTANCE.play(Assets.Sounds.DRINK);
                owner.spendActionTime(Actor.TICK);
                return true;
            }
        }

        return shieldingFirst ? false : tryConsumeShieldingPotion();
    }

    private boolean tryConsumeShieldingPotion() {
        Barrier barrier = owner.buff(Barrier.class);
        if (barrier != null && barrier.shielding() > 0) {
            return false;
        }

        Potion shielding = owner.inventory().takeOneAutoShieldingPotion();
        if (shielding == null) {
            return false;
        }

        int amount = (int) (0.6f * owner.HT + 10);
        Buff.affect(owner, Barrier.class).setShield(amount);
        CharSprite sprite = owner.attachedSprite();
        if (sprite != null) {
            sprite.showStatusWithIcon(
                    CharSprite.POSITIVE,
                    Integer.toString(amount),
                    FloatingText.SHIELDING);
        }
        Sample.INSTANCE.play(Assets.Sounds.DRINK);
        owner.spendActionTime(Actor.TICK);
        return true;
    }
}
