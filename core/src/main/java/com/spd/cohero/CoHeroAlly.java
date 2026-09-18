package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.DirectableAlly;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfAccuracy;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfEvasion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfMight;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Bundle;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import java.util.ArrayList;

public class CoHeroAlly extends DirectableAlly {

    private static final String EXPLORATION_TARGET = "cohero_exploration_target";
    private static final String INVENTORY = "cohero_inventory";

    private int explorationTarget = -1;
    private int syncedLevel = 1;
    private final CompanionInventory inventory = new CompanionInventory(this);

    {
        spriteClass = CoHeroAllySprite.class;
        HT = HP = 20;
        attacksAutomatically = false;
    }

    public CompanionInventory inventory() {
        return inventory;
    }

    public MeleeWeapon weapon() {
        return inventory.weapon();
    }

    public Armor armor() {
        return inventory.armor();
    }

    public int level() {
        if (Dungeon.hero == null) {
            throw new IllegalStateException("CoHero level requested without Dungeon.hero");
        }
        return Dungeon.hero.lvl;
    }

    /**
     * CoHero strength deliberately has one authority: the player's current effective STR.
     * No second mutable STR value is stored or synchronized.
     */
    public int STR() {
        if (Dungeon.hero == null) {
            throw new IllegalStateException("CoHero STR requested without Dungeon.hero");
        }
        return Dungeon.hero.STR();
    }

    void updateHT(boolean boostHP) {
        int oldHT = HT;
        HT = Math.round((20 + 5 * (level() - 1)) * RingOfMight.HTMultiplier(this));
        if (boostHP) {
            HP += Math.max(HT - oldHT, 0);
        }
        HP = Math.min(HP, HT);
    }

    int armorTier() {
        Armor armor = armor();
        if (armor instanceof ClassArmor) {
            return 6;
        }
        return armor == null ? 0 : armor.tier;
    }

    void updateArmorSprite() {
        if (sprite instanceof CoHeroAllySprite) {
            ((CoHeroAllySprite) sprite).updateArmor();
        }
    }

    @Override
    public String name() {
        HeroClass heroClass = CoHero.companionClass();
        return heroClass == null ? CoHeroMessages.get("companion.name") : heroClass.title();
    }

    @Override
    public void storeInBundle(Bundle bundle) {
        super.storeInBundle(bundle);
        bundle.put(EXPLORATION_TARGET, explorationTarget);

        Bundle inventoryBundle = new Bundle();
        inventory.storeInBundle(inventoryBundle);
        bundle.put(INVENTORY, inventoryBundle);
    }

    @Override
    public void restoreFromBundle(Bundle bundle) {
        super.restoreFromBundle(bundle);

        syncedLevel = level();

        explorationTarget = bundle.contains(EXPLORATION_TARGET)
                ? bundle.getInt(EXPLORATION_TARGET)
                : -1;

        if (bundle.contains(INVENTORY)) {
            inventory.restoreFromBundle(bundle.getBundle(INVENTORY));
        }
    }

    void enterLevel(int cell) {
        if (!isAlive()) {
            throw new IllegalStateException("Cannot move a dead CoHero companion to a new level");
        }

        pos = cell;
        explorationTarget = -1;
        target = -1;
        enemy = null;
        enemyID = -1;
        enemySeen = false;
        alerted = false;
        path = null;
        defendingPos = -1;
        movingToDefendPos = false;
        state = WANDERING;
        timeToNow();

        // Recreate item-owned buffs against this live Char after save restoration / floor transfer.
        inventory.rebuildPassiveEffects();
        syncedLevel = level();
        updateHT(false);
        Buff.affect(this, CompanionRegeneration.class);
    }

    private void syncSharedLevel() {
        int currentLevel = level();
        if (currentLevel == syncedLevel) {
            return;
        }

        boolean gainedLevel = currentLevel > syncedLevel;
        syncedLevel = currentLevel;
        updateHT(gainedLevel);
    }

    private int weaponEncumbrance() {
        return weapon() == null ? 0 : Math.max(0, weapon().STRReq() - STR());
    }

    private int armorEncumbrance() {
        return armor() == null ? 0 : Math.max(0, armor().STRReq() - STR());
    }

    @Override
    protected boolean canAttack(Char enemy) {
        return weapon() != null && (super.canAttack(enemy) || weapon().canReach(this, enemy.pos));
    }

    @Override
    public int attackSkill(Char target) {
        float accuracy = 9 + level();
        accuracy *= RingOfAccuracy.accuracyMultiplier(this);

        if (weapon() != null) {
            accuracy *= weapon().accuracyFactor(this, target);
            int encumbrance = weaponEncumbrance();
            if (encumbrance > 0) {
                accuracy /= Math.pow(1.5, encumbrance);
            }
        }
        return Math.round(accuracy);
    }

    @Override
    public int damageRoll() {
        if (weapon() == null) {
            return super.damageRoll();
        }

        int damage = weapon().damageRoll(this);
        int excessStrength = STR() - weapon().STRReq();
        if (excessStrength > 0) {
            damage += Random.NormalIntRange(0, excessStrength);
        }
        return damage;
    }

    @Override
    public float attackDelay() {
        float delay = super.attackDelay();
        if (weapon() != null) {
            delay *= weapon().delayFactor(this);
            int encumbrance = weaponEncumbrance();
            if (encumbrance > 0) {
                delay *= Math.pow(1.2, encumbrance);
            }
        }
        return delay;
    }

    @Override
    public int defenseSkill(Char enemy) {
        float evasion = (4 + level()) * RingOfEvasion.evasionMultiplier(this);
        if (armor() != null) {
            float armoredEvasion = armor().evasionFactor(this, evasion);
            int encumbrance = armorEncumbrance();
            if (encumbrance > 0 && armoredEvasion != 0) {
                // Armor.evasionFactor applies this before the flat augment bonus for Hero owners.
                float augmentBonus = armor().augment.evasionFactor(armor().buffedLvl());
                armoredEvasion = (float) (evasion / Math.pow(1.5, encumbrance)) + augmentBonus;
            }
            evasion = armoredEvasion;
        }
        return Math.round(evasion);
    }

    @Override
    public int glyphLevel(Class<? extends Armor.Glyph> cls) {
        if (armor() != null && armor().hasGlyph(cls, this)) {
            return Math.max(super.glyphLevel(cls), armor().buffedLvl());
        }
        return super.glyphLevel(cls);
    }

    @Override
    public float speed() {
        float speed = super.speed();
        int encumbrance = armorEncumbrance();
        if (encumbrance > 0) {
            speed /= Math.pow(1.2, encumbrance);
        }
        return speed;
    }

    @Override
    public int drRoll() {
        int dr = super.drRoll();
        if (armor() != null) {
            int armorDr = Random.NormalIntRange(armor().DRMin(), armor().DRMax());
            armorDr -= 2 * armorEncumbrance();
            if (armorDr > 0) {
                dr += armorDr;
            }
        }
        if (weapon() != null) {
            int weaponDr = Random.NormalIntRange(0, weapon().defenseFactor(this));
            weaponDr -= 2 * weaponEncumbrance();
            if (weaponDr > 0) {
                dr += weaponDr;
            }
        }
        return dr;
    }

    @Override
    public int attackProc(Char enemy, int damage) {
        damage = super.attackProc(enemy, damage);
        if (weapon() != null) {
            damage = weapon().proc(this, enemy, damage);
        }
        return damage;
    }

    @Override
    public int defenseProc(Char enemy, int damage) {
        if (armor() != null) {
            damage = armor().proc(enemy, this, damage);
        }
        return super.defenseProc(enemy, damage);
    }

    @Override
    protected boolean act() {
        syncSharedLevel();

        if (fieldOfView == null || fieldOfView.length != Dungeon.level.length()) {
            fieldOfView = new boolean[Dungeon.level.length()];
        }
        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();

        // If the player is already waiting on the exit, reaching an adjacent rally cell should
        // immediately use the player's normal transition flow.
        if (CoHero.tryAutoExit(this)) {
            return true;
        }

        if (paralysed > 0) {
            spend(TICK);
            return true;
        }

        ArrayList<Mob> visibleThreats = visibleAwakeEnemies();
        if (!visibleThreats.isEmpty()) {
            int escapeStep = chooseEscapeStep(visibleThreats);
            if (escapeStep != -1) {
                int oldPos = pos;
                if (getCloser(escapeStep)) {
                    spend(1 / speed());
                    Dungeon.level.updateFieldOfView(this, fieldOfView);
                    revealVisibleCells();
                    CoHero.tryAutoExit(this);
                    return moveSprite(oldPos, pos);
                }
            }

            // Combat AI is intentionally still separate from inventory support. Until it is added,
            // never deliberately walk closer to a visible awake enemy.
            spend(TICK);
            return true;
        }

        int exit = Dungeon.level.exit();
        LevelTransition exitTransition = isKnown(exit) ? Dungeon.level.getTransition(exit) : null;
        if (exitTransition != null && exitTransition.type == LevelTransition.Type.REGULAR_EXIT) {
            if (CoHero.isAdjacentToTransition(pos, exitTransition)) {
                explorationTarget = pos;
                spend(TICK);
                return true;
            }

            explorationTarget = chooseExitWaitingCell(exitTransition);
        } else if (explorationTarget == -1
                || explorationTarget == pos
                || !Dungeon.level.passable[explorationTarget]
                || (Actor.findChar(explorationTarget) != null && Actor.findChar(explorationTarget) != this)
                || !isSleepSafe(explorationTarget)) {
            explorationTarget = chooseExplorationTarget();
        }

        int oldPos = pos;
        if (explorationTarget != -1 && getCloser(explorationTarget)) {
            spend(1 / speed());

            Dungeon.level.updateFieldOfView(this, fieldOfView);
            revealVisibleCells();
            CoHero.tryAutoExit(this);
            return moveSprite(oldPos, pos);
        }

        explorationTarget = exitTransition != null
                ? chooseExitWaitingCell(exitTransition)
                : chooseExplorationTarget();
        spend(TICK);
        return true;
    }

    @Override
    public void die(Object cause) {
        super.die(cause);
        if (Dungeon.hero != null && Dungeon.hero.isAlive()) {
            GLog.n(companionDeathMessage(cause));
            CoHero.markCompanionDeathGameOver();
            Hero.reallyDie(cause);
        }
    }

    private String companionDeathMessage(Object cause) {
        if (cause instanceof Char && cause != this) {
            return CoHeroMessages.get("companion.killed_by", ((Char) cause).name());
        }
        return CoHeroMessages.get("companion.died");
    }

    private void revealVisibleCells() {
        boolean changed = false;
        for (int i = 0; i < fieldOfView.length; i++) {
            if (fieldOfView[i]
                    && Dungeon.level.discoverable[i]
                    && !Dungeon.level.visited[i]) {
                Dungeon.level.visited[i] = true;
                changed = true;
            }
        }
        if (changed) {
            GameScene.updateFog(pos, viewDistance + 1);
        }
    }

    private ArrayList<Mob> visibleAwakeEnemies() {
        ArrayList<Mob> result = new ArrayList<>();
        for (Mob mob : Dungeon.level.mobs) {
            if (mob != this
                    && mob.alignment == Alignment.ENEMY
                    && mob.isAlive()
                    && mob.invisible <= 0
                    && fieldOfView[mob.pos]
                    && mob.state != mob.SLEEPING) {
                result.add(mob);
            }
        }
        return result;
    }

    private int chooseEscapeStep(ArrayList<Mob> threats) {
        int currentDistance = nearestThreatDistance(pos, threats);
        int bestCell = -1;
        int bestDistance = currentDistance;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = pos + offset;
            if (cell < 0
                    || cell >= Dungeon.level.length()
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !isSleepSafe(cell)) {
                continue;
            }

            int distance = nearestThreatDistance(cell, threats);
            if (distance > bestDistance) {
                bestDistance = distance;
                bestCell = cell;
            }
        }

        return bestCell;
    }

    private int nearestThreatDistance(int cell, ArrayList<Mob> threats) {
        int nearest = Integer.MAX_VALUE;
        for (Mob threat : threats) {
            nearest = Math.min(nearest, Dungeon.level.distance(cell, threat.pos));
        }
        return nearest;
    }

    private boolean isSleepSafe(int cell) {
        for (Mob mob : Dungeon.level.mobs) {
            if (mob != this
                    && mob.alignment == Alignment.ENEMY
                    && mob.isAlive()
                    && mob.state == mob.SLEEPING
                    && fieldOfView[mob.pos]
                    && Dungeon.level.distance(cell, mob.pos) <= 1) {
                return false;
            }
        }
        return true;
    }

    private int chooseExitWaitingCell(LevelTransition transition) {
        int bestCell = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (!Dungeon.level.passable[cell]
                    || transition.inside(cell)
                    || !CoHero.isAdjacentToTransition(cell, transition)
                    || !isSleepSafe(cell)) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant != null && occupant != this) {
                continue;
            }

            int distance = Dungeon.level.distance(pos, cell);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestCell = cell;
            }
        }

        return bestCell;
    }

    private int chooseExplorationTarget() {
        int exit = Dungeon.level.exit();
        if (isKnown(exit)) {
            LevelTransition transition = Dungeon.level.getTransition(exit);
            if (transition != null && transition.type == LevelTransition.Type.REGULAR_EXIT) {
                return chooseExitWaitingCell(transition);
            }
        }

        ArrayList<Integer> unknown = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell != pos
                    && Dungeon.level.passable[cell]
                    && Dungeon.level.discoverable[cell]
                    && !Dungeon.level.visited[cell]
                    && !Dungeon.level.mapped[cell]
                    && isSleepSafe(cell)) {
                unknown.add(cell);
            }
        }

        if (!unknown.isEmpty()) {
            return Random.element(unknown);
        }

        return Dungeon.level.randomDestination(this);
    }

    private boolean isKnown(int cell) {
        return cell >= 0
                && cell < Dungeon.level.length()
                && (Dungeon.level.visited[cell] || Dungeon.level.mapped[cell]);
    }
}
