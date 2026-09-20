package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Belongings;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.ui.ItemButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.OptionSlider;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndInfoItem;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;

import java.util.Locale;

/** Inventory UI for the autonomous companion. */
public class WndCompanionInventory extends Window {

    private static final int PREFERRED_WIDTH = 134;
    private static final int PREFERRED_SLOT = 26;
    private static final int MIN_SLOT = 22;
    private static final int SLOT_GAP = 1;
    private static final int BACKPACK_COLS = 5;
    private static final int CONTROL_INSET = 6;

    private final CoHeroAlly companion;
    private final CompanionInventory inventory;
    private final int layoutWidth;
    private final int slotSize;
    private final int slotGap;

    public WndCompanionInventory(CoHeroAlly companion) {
        if (companion == null || !companion.isAlive()) {
            throw new IllegalArgumentException("companion must be alive");
        }
        this.companion = companion;
        this.inventory = companion.inventory();

        int availableWidth = Math.max(
                1,
                PixelScene.uiCamera.width - (int) Math.ceil(chrome.marginHor()) - 2);
        layoutWidth = Math.min(PREFERRED_WIDTH, availableWidth);
        slotGap = SLOT_GAP;
        slotSize = Math.max(
                MIN_SLOT,
                Math.min(
                        PREFERRED_SLOT,
                        (layoutWidth - slotGap * (BACKPACK_COLS - 1)) / BACKPACK_COLS));

        RenderedTextBlock title = PixelScene.renderTextBlock(text("inventory.title"), 9);
        title.hardlight(TITLE_COLOR);
        title.maxWidth(layoutWidth);
        title.setPos(0, 1);
        add(title);

        float statsY = title.bottom() + 4;
        addStatCell(0, 0, statsY, layoutWidth, text("inventory.level"),
                Integer.toString(companion.level()));
        addStatCell(1, 0, statsY, layoutWidth, text("inventory.health"), healthText());
        addStatCell(2, 0, statsY, layoutWidth, text("inventory.strength"),
                Integer.toString(companion.STR()));

        statsY += 18;
        addStatCell(0, 0, statsY, layoutWidth, text("inventory.damage"), damageText());
        addStatCell(1, 0, statsY, layoutWidth, text("inventory.defense"), defenseText());
        addStatCell(2, 0, statsY, layoutWidth, text("inventory.speed"), speedText());

        float afterControls = addControlsAndEquipment(0, statsY + 19, layoutWidth);

        RenderedTextBlock backpackLabel = backpackLabel(layoutWidth);
        backpackLabel.setPos(0, afterControls + 3);
        add(backpackLabel);

        float backpackY = backpackLabel.bottom() + 2;
        for (int i = 0; i < CompanionInventory.BACKPACK_CAPACITY; i++) {
            addBackpackButton(i, 0, backpackY);
        }

        int rows = (int) Math.ceil(
                CompanionInventory.BACKPACK_CAPACITY / (float) BACKPACK_COLS);
        resize(layoutWidth, (int) (backpackY + rows * (slotSize + slotGap)));
    }

    private float addControlsAndEquipment(float x, float startY, int width) {
        int inset = Math.min(CONTROL_INSET, Math.max(0, (width - 80) / 2));
        float controlX = x + inset;
        int controlWidth = width - inset * 2;

        final float enemySpawnLabelY = startY;
        final RenderedTextBlock enemySpawnValue =
                PixelScene.renderTextBlock(enemySpawnValueText(), 7);
        enemySpawnValue.setPos(
                controlX + controlWidth - enemySpawnValue.width(),
                enemySpawnLabelY);
        add(enemySpawnValue);

        RenderedTextBlock enemySpawnLabel =
                PixelScene.renderTextBlock(text("inventory.enemy_spawn"), 7);
        enemySpawnLabel.maxWidth(
                Math.max(
                        1,
                        controlWidth - (int) Math.ceil(enemySpawnValue.width()) - 2));
        enemySpawnLabel.setPos(controlX, enemySpawnLabelY);
        add(enemySpawnLabel);

        OptionSlider enemySpawnSlider = new OptionSlider(
                "",
                "1.0x",
                "2.0x",
                CompanionEnemySurge.MIN_MULTIPLIER_TENTHS,
                CompanionEnemySurge.MAX_MULTIPLIER_TENTHS) {
            @Override
            protected void onChange() {
                companion.setEnemySpawnMultiplierTenths(getSelectedValue());
                enemySpawnValue.text(enemySpawnValueText());
                enemySpawnValue.setPos(
                        controlX + controlWidth - enemySpawnValue.width(),
                        enemySpawnLabelY);
            }
        };
        enemySpawnSlider.setSelectedValue(companion.enemySpawnMultiplierTenths());
        enemySpawnSlider.setRect(
                controlX,
                enemySpawnLabel.bottom() + 1,
                controlWidth,
                21);
        add(enemySpawnSlider);

        RenderedTextBlock equipmentLabel =
                PixelScene.renderTextBlock(text("inventory.equipment"), 7);
        equipmentLabel.setPos(x, enemySpawnSlider.bottom() + 3);
        add(equipmentLabel);

        float equipmentY = equipmentLabel.bottom() + 2;
        addEquipmentButton(0, x, equipmentY, SlotType.WEAPON);
        addEquipmentButton(1, x, equipmentY, SlotType.ARMOR);
        addEquipmentButton(2, x, equipmentY, SlotType.RING_ONE);
        addEquipmentButton(3, x, equipmentY, SlotType.RING_TWO);

        RedButton addItem = new RedButton(text("inventory.add_item")) {
            @Override
            protected void onClick() {
                selectItemFromHero();
            }
        };
        addItem.setRect(
                controlX,
                equipmentY + slotSize + 3,
                controlWidth,
                16);
        add(addItem);
        return addItem.bottom();
    }

    private RenderedTextBlock backpackLabel(int width) {
        RenderedTextBlock label = PixelScene.renderTextBlock(
                text(
                        "inventory.backpack",
                        inventory.backpack().size(),
                        CompanionInventory.BACKPACK_CAPACITY),
                7);
        label.maxWidth(width);
        return label;
    }

    private void addStatCell(
            int column, float areaX, float y, float areaWidth, String label, String value) {
        float cellWidth = areaWidth / 3f;
        float x = areaX + column * cellWidth;

        RenderedTextBlock statLabel = PixelScene.renderTextBlock(label, 6);
        statLabel.maxWidth((int) cellWidth - 2);
        statLabel.setPos(x, y);
        add(statLabel);

        RenderedTextBlock statValue = PixelScene.renderTextBlock(value, 7);
        statValue.maxWidth((int) cellWidth - 2);
        statValue.setPos(x, y + 8);
        add(statValue);
    }

    private String healthText() {
        int shielding = companion.shielding();
        if (shielding > 0) {
            return companion.HP + "+" + shielding + "/" + companion.HT;
        }
        return companion.HP + "/" + companion.HT;
    }

    private String damageText() {
        MeleeWeapon weapon = inventory.weapon();
        if (weapon == null) {
            return "-";
        }

        int min = weapon.augment.damageFactor(weapon.min());
        int max = weapon.augment.damageFactor(weapon.max());
        int excessStrength = Math.max(0, companion.STR() - weapon.STRReq());
        max += excessStrength;
        return min + "-" + max;
    }

    private String defenseText() {
        int min = 0;
        int max = 0;

        Armor armor = inventory.armor();
        if (armor != null) {
            int encumbrance = Math.max(0, armor.STRReq() - companion.STR());
            min += Math.max(0, armor.DRMin() - 2 * encumbrance);
            max += Math.max(0, armor.DRMax() - 2 * encumbrance);
        }

        MeleeWeapon weapon = inventory.weapon();
        if (weapon != null) {
            int encumbrance = Math.max(0, weapon.STRReq() - companion.STR());
            max += Math.max(0, weapon.defenseFactor(companion) - 2 * encumbrance);
        }

        return min + "-" + max;
    }

    private String speedText() {
        return String.format(Locale.ENGLISH, "%.2fx", companion.speed());
    }

    private String enemySpawnValueText() {
        return String.format(
                Locale.ENGLISH,
                "%.1fx",
                companion.enemySpawnMultiplierTenths() / 10f);
    }

    private void addEquipmentButton(int column, float startX, float y, SlotType type) {
        ItemButton button = new ItemButton() {
            @Override
            protected void onClick() {
                Item equipped = equippedItem(type);
                if (equipped == null) {
                    return;
                }
                if (unequipToBackpack(type)) {
                    refreshWindow();
                } else {
                    warnUnequipBlocked(equipped);
                }
            }

            @Override
            protected boolean onLongClick() {
                Item equipped = equippedItem(type);
                if (equipped == null) {
                    return false;
                }
                GameScene.show(new WndInfoItem(equipped));
                return true;
            }
        };

        button.setRect(
                startX + column * (slotSize + slotGap),
                y,
                slotSize,
                slotSize);
        button.item(equipmentItemFor(type));
        add(button);
    }

    private void addBackpackButton(int index, float startX, float startY) {
        int col = index % BACKPACK_COLS;
        int row = index / BACKPACK_COLS;

        ItemButton button = new ItemButton() {
            @Override
            protected void onClick() {
                if (index < inventory.backpack().size()) {
                    showBackpackItemActions(inventory.backpack().get(index));
                } else {
                    selectItemFromHero();
                }
            }

            @Override
            protected boolean onLongClick() {
                if (index >= inventory.backpack().size()) {
                    return false;
                }
                GameScene.show(new WndInfoItem(inventory.backpack().get(index)));
                return true;
            }
        };

        button.setRect(
                startX + col * (slotSize + slotGap),
                startY + row * (slotSize + slotGap),
                slotSize,
                slotSize);
        button.item(backpackItemFor(index));
        add(button);
    }

    private Item equipmentItemFor(SlotType type) {
        Item item = equippedItem(type);
        if (item != null) {
            return item;
        }

        switch (type) {
            case WEAPON:
                return new WndBag.Placeholder(ItemSpriteSheet.WEAPON_HOLDER);
            case ARMOR:
                return new WndBag.Placeholder(ItemSpriteSheet.ARMOR_HOLDER);
            case RING_ONE:
            case RING_TWO:
                return new WndBag.Placeholder(ItemSpriteSheet.RING_HOLDER);
            default:
                throw new IllegalStateException("Unknown slot type: " + type);
        }
    }

    private Item backpackItemFor(int index) {
        if (index < inventory.backpack().size()) {
            return inventory.backpack().get(index);
        }
        return new WndBag.Placeholder(ItemSpriteSheet.SOMETHING);
    }

    private void selectItemFromHero() {
        // A WndBag is another window. Close this snapshot first so a fresh inventory window
        // cannot end up stacked over a stale one after the selection callback.
        hide();
        GameScene.selectItem(new WndBag.ItemSelector() {
            @Override
            public String textPrompt() {
                return text("inventory.select_item");
            }

            @Override
            public Class<? extends Bag> preferredBag() {
                return Belongings.Backpack.class;
            }

            @Override
            public boolean itemSelectable(Item item) {
                return CompanionInventory.supported(item) && inventory.canAddToBackpack(item);
            }

            @Override
            public void onSelect(Item item) {
                if (item != null) {
                    Item moved = takeFromPlayer(item);
                    if (moved != null && !inventory.addToBackpack(moved)) {
                        returnToPlayer(moved);
                        throw new IllegalStateException("Selected CoHero item no longer fits in backpack");
                    }
                }
                GameScene.show(new WndCompanionInventory(companion));
            }
        });
    }

    private Item takeFromPlayer(Item item) {
        if (!CompanionInventory.supported(item)) {
            throw new IllegalArgumentException("Unsupported CoHero item: " + item.getClass().getName());
        }

        if (item.isEquipped(Dungeon.hero)) {
            if (!(item instanceof EquipableItem)) {
                throw new IllegalStateException("Equipped item is not EquipableItem: " + item.getClass().getName());
            }
            if (!((EquipableItem) item).doUnequip(Dungeon.hero, false, false)) {
                return null;
            }
            return item;
        }

        return item.detachAll(Dungeon.hero.belongings.backpack);
    }

    private void showBackpackItemActions(Item item) {
        if (item instanceof MeleeWeapon || item instanceof Armor || item instanceof Ring) {
            GameScene.show(new WndOptions(
                    item.title(),
                    text("inventory.action_prompt"),
                    text("inventory.equip"),
                    text("inventory.give_to_hero")) {
                @Override
                protected void onSelect(int index) {
                    if (index == 0) {
                        equipFromBackpack(item);
                    } else if (index == 1) {
                        giveBackpackItemToHero(item);
                    }
                }
            });
        } else if (item instanceof Weapon
                || item instanceof Wand
                || item instanceof Potion
                || item instanceof Scroll
                || item instanceof Ankh) {
            GameScene.show(new WndOptions(
                    item.title(),
                    text("inventory.action_prompt"),
                    text("inventory.give_to_hero")) {
                @Override
                protected void onSelect(int index) {
                    if (index == 0) {
                        giveBackpackItemToHero(item);
                    }
                }
            });
        } else {
            throw new IllegalStateException("Unsupported item reached CoHero backpack UI: " + item.getClass().getName());
        }
    }

    private void equipFromBackpack(Item item) {
        CompanionInventory.EquipFailure failure = inventory.equipFailure(item);
        if (failure != CompanionInventory.EquipFailure.NONE) {
            warnEquipBlocked(failure);
            return;
        }

        boolean equipped;
        if (item instanceof MeleeWeapon) {
            Item current = inventory.weapon();
            equipped = inventory.equipWeapon((MeleeWeapon) item);
            if (!equipped) {
                warnUnequipBlocked(current);
                return;
            }
        } else if (item instanceof Armor) {
            Item current = inventory.armor();
            equipped = inventory.equipArmor((Armor) item);
            if (!equipped) {
                warnUnequipBlocked(current);
                return;
            }
        } else if (item instanceof Ring) {
            equipRingFromBackpack((Ring) item);
            return;
        } else {
            throw new IllegalArgumentException("Item cannot be equipped by CoHero: " + item.getClass().getName());
        }

        refreshWindow();
    }

    private void equipRingFromBackpack(Ring ring) {
        if (inventory.ringOne() == null) {
            equipRingInto(ring, 1);
            return;
        }
        if (inventory.ringTwo() == null) {
            equipRingInto(ring, 2);
            return;
        }

        GameScene.show(new WndOptions(
                ring.title(),
                text("inventory.choose_ring_slot"),
                text("inventory.ring_slot", 1, inventory.ringOne().title()),
                text("inventory.ring_slot", 2, inventory.ringTwo().title())) {
            @Override
            protected void onSelect(int index) {
                equipRingInto(ring, index + 1);
            }
        });
    }

    private void equipRingInto(Ring ring, int slot) {
        CompanionInventory.EquipFailure failure = inventory.equipFailure(ring);
        if (failure != CompanionInventory.EquipFailure.NONE) {
            warnEquipBlocked(failure);
            return;
        }

        Item current = slot == 1 ? inventory.ringOne() : inventory.ringTwo();
        if (!inventory.equipRing(ring, slot)) {
            warnUnequipBlocked(current);
            return;
        }
        refreshWindow();
    }

    private void giveBackpackItemToHero(Item item) {
        Item removed = inventory.removeFromBackpack(item);
        if (removed == null) {
            throw new IllegalStateException("CoHero backpack item disappeared before transfer");
        }
        returnToPlayer(removed);
        refreshWindow();
    }

    private boolean unequipToBackpack(SlotType type) {
        switch (type) {
            case WEAPON:
                return inventory.unequipWeaponToBackpack();
            case ARMOR:
                return inventory.unequipArmorToBackpack();
            case RING_ONE:
                return inventory.unequipRingToBackpack(1);
            case RING_TWO:
                return inventory.unequipRingToBackpack(2);
            default:
                throw new IllegalStateException("Unknown slot type: " + type);
        }
    }

    private void warnEquipBlocked(CompanionInventory.EquipFailure failure) {
        switch (failure) {
            case CURSED_OR_UNKNOWN:
                GLog.w(text("inventory.cant_cursed"));
                break;
            case TOO_HEAVY_UNKNOWN:
                GLog.w(text("inventory.cant_strength_unknown"));
                break;
            case TOO_HEAVY:
                GLog.w(text("inventory.cant_strength"));
                break;
            case NONE:
                throw new IllegalArgumentException("Cannot warn for a successful equip check");
            default:
                throw new IllegalStateException("Unknown equip failure: " + failure);
        }
    }

    private void warnUnequipBlocked(Item item) {
        if (item != null && inventory.cannotUnequip(item)) {
            GLog.w(Messages.get(EquipableItem.class, "unequip_cursed"));
        } else {
            GLog.w(text("inventory.backpack_full"));
        }
    }

    private void returnToPlayer(Item item) {
        if (!item.collect(Dungeon.hero.belongings.backpack)) {
            Dungeon.level.drop(item, Dungeon.hero.pos).sprite.drop();
        }
    }

    private Item equippedItem(SlotType type) {
        switch (type) {
            case WEAPON:
                return inventory.weapon();
            case ARMOR:
                return inventory.armor();
            case RING_ONE:
                return inventory.ringOne();
            case RING_TWO:
                return inventory.ringTwo();
            default:
                throw new IllegalStateException("Unknown slot type: " + type);
        }
    }

    private void refreshWindow() {
        hide();
        GameScene.show(new WndCompanionInventory(companion));
    }

    private static String text(String key, Object... args) {
        return CoHeroMessages.get(key, args);
    }

    private enum SlotType {
        WEAPON,
        ARMOR,
        RING_ONE,
        RING_TWO
    }
}
