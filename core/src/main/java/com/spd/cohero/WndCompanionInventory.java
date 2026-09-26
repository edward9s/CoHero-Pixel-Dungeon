package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.ui.InventorySlot;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndInfoItem;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.watabou.noosa.ColorBlock;

import java.util.Locale;

/** Inventory UI for the autonomous companion. */
public class WndCompanionInventory extends Window {

    private static final int PORTRAIT_WIDTH = 134;
    private static final int LANDSCAPE_WIDTH = 236;
    private static final int PORTRAIT_SLOT = 26;
    private static final int LANDSCAPE_SLOT = 22;
    private static final int MIN_PORTRAIT_SLOT = 22;
    private static final int SLOT_GAP = 1;
    private static final int BACKPACK_COLS = 5;
    private static final int ADD_ITEM_INSET = 6;
    private static final int LANDSCAPE_PANEL_GAP = 4;

    private final CoHeroAlly companion;
    private final CompanionInventory inventory;
    private final int layoutWidth;
    private final int slotSize;
    private final int slotGap;
    private String shownUpdateVersion;

    public WndCompanionInventory(CoHeroAlly companion) {
        if (companion == null || !companion.isAlive()) {
            throw new IllegalArgumentException("companion must be alive");
        }
        this.companion = companion;
        this.inventory = companion.inventory();

        CoHeroUpdates.checkForUpdate();
        shownUpdateVersion = CoHeroUpdates.latestVersion();

        boolean landscape = PixelScene.landscape();
        int availableWidth = Math.max(
                1,
                PixelScene.uiCamera.width - (int) Math.ceil(chrome.marginHor()) - 2);

        slotGap = SLOT_GAP;
        if (landscape) {
            layoutWidth = Math.min(LANDSCAPE_WIDTH, availableWidth);
            slotSize = LANDSCAPE_SLOT;
        } else {
            layoutWidth = Math.min(PORTRAIT_WIDTH, availableWidth);
            slotSize = Math.max(
                    MIN_PORTRAIT_SLOT,
                    Math.min(
                            PORTRAIT_SLOT,
                            (layoutWidth - slotGap * (BACKPACK_COLS - 1))
                                    / BACKPACK_COLS));
        }

        RenderedTextBlock title = PixelScene.renderTextBlock(titleText(shownUpdateVersion), 9);
        title.hardlight(TITLE_COLOR);
        title.maxWidth(layoutWidth);
        title.setPos(0, 1);
        add(title);

        float contentY = title.bottom() + 4;
        if (landscape) {
            layoutLandscape(contentY);
        } else {
            layoutPortrait(contentY);
        }
    }

    @Override
    public void update() {
        super.update();

        String latest = CoHeroUpdates.latestVersion();
        if (latest == null ? shownUpdateVersion != null : !latest.equals(shownUpdateVersion)) {
            shownUpdateVersion = latest;
            refreshWindow();
        }
    }

    private void layoutPortrait(float startY) {
        float statsY = startY;
        addStatCell(0, 0, statsY, layoutWidth, text("inventory.level"),
                Integer.toString(companion.level()));
        addStatCell(1, 0, statsY, layoutWidth, text("inventory.health"), healthText());
        addStatCell(2, 0, statsY, layoutWidth, text("inventory.strength"),
                Integer.toString(companion.STR()));

        statsY += 18;
        addStatCell(0, 0, statsY, layoutWidth, text("inventory.damage"), damageText());
        addStatCell(1, 0, statsY, layoutWidth, text("inventory.defense"), defenseText());
        addStatCell(2, 0, statsY, layoutWidth, text("inventory.speed"), speedText());

        float afterEquipment = addEquipment(0, statsY + 19, layoutWidth, true);

        RenderedTextBlock backpackLabel = backpackLabel(layoutWidth);
        backpackLabel.setPos(0, afterEquipment + 3);
        add(backpackLabel);

        float backpackY = backpackLabel.bottom() + 2;
        for (int i = 0; i < CompanionInventory.BACKPACK_CAPACITY; i++) {
            addBackpackSlot(i, 0, backpackY);
        }

        int rows = (int) Math.ceil(
                CompanionInventory.BACKPACK_CAPACITY / (float) BACKPACK_COLS);
        resize(layoutWidth, (int) (backpackY + rows * (slotSize + slotGap)));
    }

    private void layoutLandscape(float startY) {
        int backpackWidth = BACKPACK_COLS * slotSize
                + (BACKPACK_COLS - 1) * slotGap;
        int leftWidth = layoutWidth - LANDSCAPE_PANEL_GAP - backpackWidth;
        if (leftWidth < 96) {
            throw new IllegalStateException(
                    "CoHero inventory cannot fit landscape layout");
        }

        int backpackX = leftWidth + LANDSCAPE_PANEL_GAP;

        float statsY = startY;
        addStatCell(0, 0, statsY, leftWidth, text("inventory.level"),
                Integer.toString(companion.level()));
        addStatCell(1, 0, statsY, leftWidth, text("inventory.health"), healthText());
        addStatCell(2, 0, statsY, leftWidth, text("inventory.strength"),
                Integer.toString(companion.STR()));

        statsY += 18;
        addStatCell(0, 0, statsY, leftWidth, text("inventory.damage"), damageText());
        addStatCell(1, 0, statsY, leftWidth, text("inventory.defense"), defenseText());
        addStatCell(2, 0, statsY, leftWidth, text("inventory.speed"), speedText());

        float leftBottom = addEquipment(0, statsY + 14, leftWidth, false);

        float backpackHeaderBottom = addItemButton(backpackX, startY, backpackWidth);
        RenderedTextBlock backpackLabel = backpackLabel(backpackWidth);
        backpackLabel.setPos(backpackX, backpackHeaderBottom + 3);
        add(backpackLabel);

        float backpackY = backpackLabel.bottom() + 2;
        for (int i = 0; i < CompanionInventory.BACKPACK_CAPACITY; i++) {
            addBackpackSlot(i, backpackX, backpackY);
        }

        int rows = (int) Math.ceil(
                CompanionInventory.BACKPACK_CAPACITY / (float) BACKPACK_COLS);
        float rightBottom = backpackY + rows * (slotSize + slotGap);
        resize(layoutWidth, (int) Math.max(leftBottom, rightBottom));
    }

    private float addEquipment(
            float x, float startY, int width, boolean includeAddItemButton) {
        int inset = Math.min(ADD_ITEM_INSET, Math.max(0, width - 80));
        int buttonWidth = width - inset;

        RenderedTextBlock equipmentLabel =
                PixelScene.renderTextBlock(text("inventory.equipment"), 7);
        equipmentLabel.setPos(x, startY);
        add(equipmentLabel);

        float equipmentY = equipmentLabel.bottom() + 2;
        addEquipmentButton(0, x, equipmentY, SlotType.WEAPON);
        addEquipmentButton(1, x, equipmentY, SlotType.ARMOR);
        addEquipmentButton(2, x, equipmentY, SlotType.RING_ONE);
        addEquipmentButton(3, x, equipmentY, SlotType.RING_TWO);

        float equipmentBottom = equipmentY + slotSize;
        if (!includeAddItemButton) {
            return equipmentBottom;
        }
        return addItemButton(x, equipmentBottom + 3, buttonWidth);
    }

    private float addItemButton(float x, float y, int width) {
        RedButton addItem = new RedButton(text("inventory.add_item")) {
            @Override
            protected void onClick() {
                selectItemFromHero();
            }
        };
        addItem.setRect(x, y, width, 16);
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

    private void addEquipmentButton(int column, float startX, float y, SlotType type) {
        InventorySlot slot = new CoHeroInventorySlot(equipmentItemFor(type)) {
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

        slot.setRect(
                startX + column * (slotSize + slotGap),
                y,
                slotSize,
                slotSize);
        add(slot);
    }

    private void addBackpackSlot(int index, float startX, float startY) {
        int col = index % BACKPACK_COLS;
        int row = index / BACKPACK_COLS;

        InventorySlot slot = new CoHeroInventorySlot(backpackItemFor(index)) {
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

        slot.setRect(
                startX + col * (slotSize + slotGap),
                startY + row * (slotSize + slotGap),
                slotSize,
                slotSize);
        add(slot);
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
        return index < inventory.backpack().size()
                ? inventory.backpack().get(index)
                : null;
    }

    private void selectItemFromHero() {
        // WndBag is another window. Close this snapshot once, then keep the selector active
        // across successful transfers so several items can be given to CoHero in sequence.
        hide();
        openHeroItemSelector();
    }

    private void openHeroItemSelector() {
        GameScene.selectItem(new WndBag.ItemSelector() {
            @Override
            public String textPrompt() {
                return text("inventory.select_item");
            }

            @Override
            public boolean itemSelectable(Item item) {
                return inventory.canAddToBackpack(item);
            }

            @Override
            public void onSelect(Item item) {
                if (item == null) {
                    GameScene.show(new WndCompanionInventory(companion));
                    return;
                }

                if (needsAmountChoice(item)) {
                    showTakeFromPlayerAmount(item);
                } else {
                    transferFromPlayer(item, TransferAmount.ALL);
                }
            }
        });
    }

    private void showTakeFromPlayerAmount(Item item) {
        GameScene.show(new WndOptions(
                item.title(),
                text("inventory.transfer_amount"),
                text("inventory.transfer_one"),
                text("inventory.transfer_all")) {
            @Override
            protected void onSelect(int index) {
                transferFromPlayer(
                        item,
                        transferAmountForOption(index));
            }
        });
    }

    private void transferFromPlayer(Item item, TransferAmount amount) {
        Item moved = takeFromPlayer(item, amount);
        if (moved != null && !inventory.addToBackpack(moved)) {
            returnToPlayer(moved);
            throw new IllegalStateException("Selected CoHero item no longer fits in backpack");
        }

        // Match SMM Tools' Un/Identify selector: a successful action immediately
        // reopens the item selector. Cancelling is the explicit way back.
        openHeroItemSelector();
    }

    private Item takeFromPlayer(Item item, TransferAmount amount) {
        if (item.isEquipped(Dungeon.hero)) {
            if (!(item instanceof EquipableItem)) {
                throw new IllegalStateException("Equipped item is not EquipableItem: " + item.getClass().getName());
            }
            if (amount == TransferAmount.ONE && item.quantity() > 1) {
                throw new IllegalStateException("Stackable equipped item cannot be split during transfer");
            }
            if (!((EquipableItem) item).doUnequip(Dungeon.hero, false, false)) {
                return null;
            }
            return item;
        }

        return amount == TransferAmount.ONE
                ? item.detach(Dungeon.hero.belongings.backpack)
                : item.detachAll(Dungeon.hero.belongings.backpack);
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
                        returnBackpackItemToPlayer(item);
                    }
                }
            });
            return;
        }

        if (needsAmountChoice(item)) {
            showReturnToPlayerAmount(item);
            return;
        }

        GameScene.show(new WndOptions(
                item.title(),
                text("inventory.action_prompt"),
                text("inventory.give_to_hero")) {
            @Override
            protected void onSelect(int index) {
                if (index == 0) {
                    returnBackpackItemToPlayer(item, TransferAmount.ALL);
                }
            }
        });
    }

    private void showReturnToPlayerAmount(Item item) {
        GameScene.show(new WndOptions(
                item.title(),
                text("inventory.transfer_amount"),
                text("inventory.transfer_one"),
                text("inventory.transfer_all")) {
            @Override
            protected void onSelect(int index) {
                returnBackpackItemToPlayer(
                        item,
                        transferAmountForOption(index));
            }
        });
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

    private void returnBackpackItemToPlayer(Item item) {
        if (needsAmountChoice(item)) {
            showReturnToPlayerAmount(item);
        } else {
            returnBackpackItemToPlayer(item, TransferAmount.ALL);
        }
    }

    private void returnBackpackItemToPlayer(Item item, TransferAmount amount) {
        Item removed = amount == TransferAmount.ONE
                ? inventory.removeOneFromBackpack(item)
                : inventory.removeFromBackpack(item);
        if (removed == null) {
            throw new IllegalStateException("CoHero backpack item disappeared before transfer");
        }
        returnToPlayer(removed);
        refreshWindow();
    }

    private static boolean needsAmountChoice(Item item) {
        return item != null && item.stackable && item.quantity() > 1;
    }

    private static TransferAmount transferAmountForOption(int index) {
        switch (index) {
            case 0:
                return TransferAmount.ONE;
            case 1:
                return TransferAmount.ALL;
            default:
                throw new IllegalArgumentException("Unknown transfer amount option: " + index);
        }
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

    private static String titleText(String latestVersion) {
        String title = text("inventory.title");
        return latestVersion == null ? title : title + " (new " + latestVersion + ")";
    }

    private static String text(String key, Object... args) {
        return CoHeroMessages.get(key, args);
    }

    /**
     * Uses the stock backpack slot chrome and state tinting, with only a subtle
     * frame to mark items the CoHero can actively use.
     */
    private class CoHeroInventorySlot extends InventorySlot {

        private static final int USABLE_FRAME_COLOR = 0xCCB8A45A;

        private Item displayedItem;
        private ColorBlock frameTop;
        private ColorBlock frameBottom;
        private ColorBlock frameLeft;
        private ColorBlock frameRight;

        CoHeroInventorySlot(Item item) {
            super(item);
        }

        @Override
        protected void createChildren() {
            super.createChildren();

            frameTop = new ColorBlock(1, 1, USABLE_FRAME_COLOR);
            frameBottom = new ColorBlock(1, 1, USABLE_FRAME_COLOR);
            frameLeft = new ColorBlock(1, 1, USABLE_FRAME_COLOR);
            frameRight = new ColorBlock(1, 1, USABLE_FRAME_COLOR);

            // Capability frame renders above the stock InventorySlot contents.
            add(frameTop);
            add(frameBottom);
            add(frameLeft);
            add(frameRight);
        }

        @Override
        protected void layout() {
            super.layout();

            frameTop.x = x;
            frameTop.y = y;
            frameTop.size(width, 1);

            frameBottom.x = x;
            frameBottom.y = y + height - 1;
            frameBottom.size(width, 1);

            frameLeft.x = x;
            frameLeft.y = y;
            frameLeft.size(1, height);

            frameRight.x = x + width - 1;
            frameRight.y = y;
            frameRight.size(1, height);
        }

        @Override
        public void item(Item item) {
            displayedItem = item;
            super.item(item);

            // Stock ItemSlot disables null entries. CoHero keeps empty backpack
            // cells clickable so they can still open the Hero item selector.
            if (item == null) {
                enable(true);
            }
            applyCapabilityFrame();
        }

        private void applyCapabilityFrame() {
            boolean visible = displayedItem != null
                    && !(displayedItem instanceof WndBag.Placeholder)
                    && inventory.canUse(displayedItem);
            frameTop.visible = visible;
            frameBottom.visible = visible;
            frameLeft.visible = visible;
            frameRight.visible = visible;
        }
    }

    private enum TransferAmount {
        ONE,
        ALL
    }

    private enum SlotType {
        WEAPON,
        ARMOR,
        RING_ONE,
        RING_TWO
    }
}
