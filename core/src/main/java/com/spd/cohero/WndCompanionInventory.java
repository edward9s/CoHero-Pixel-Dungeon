package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.ui.ItemButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndInfoItem;

/** Inventory UI for the autonomous companion. */
public class WndCompanionInventory extends Window {

    private static final int WIDTH = 146;
    private static final int SLOT = 26;
    private static final int GAP = 3;
    private static final int COLS = 5;

    private final CompanionHero companion;
    private final CompanionInventory inventory;

    public WndCompanionInventory(CompanionHero companion) {
        if (companion == null || !companion.isAlive()) {
            throw new IllegalArgumentException("companion must be alive");
        }
        this.companion = companion;
        this.inventory = companion.inventory();

        RenderedTextBlock title = PixelScene.renderTextBlock(titleText(), 9);
        title.hardlight(TITLE_COLOR);
        title.maxWidth(WIDTH);
        title.setPos(0, 1);
        add(title);

        RenderedTextBlock equipmentLabel = PixelScene.renderTextBlock(equipmentText(), 7);
        equipmentLabel.setPos(0, title.bottom() + 4);
        add(equipmentLabel);

        float equipmentY = equipmentLabel.bottom() + 2;
        addEquipmentButton(0, equipmentY, SlotType.WEAPON);
        addEquipmentButton(1, equipmentY, SlotType.ARMOR);
        addEquipmentButton(2, equipmentY, SlotType.RING_ONE);
        addEquipmentButton(3, equipmentY, SlotType.RING_TWO);

        RenderedTextBlock wandLabel = PixelScene.renderTextBlock(wandsText(), 7);
        wandLabel.setPos(0, equipmentY + SLOT + 5);
        add(wandLabel);

        float wandY = wandLabel.bottom() + 2;
        for (int i = 0; i < CompanionInventory.MAX_WANDS; i++) {
            addWandButton(i, wandY);
        }

        int rows = (int) Math.ceil(CompanionInventory.MAX_WANDS / (float) COLS);
        resize(WIDTH, (int) (wandY + rows * (SLOT + GAP)));
    }

    private void addEquipmentButton(int column, float y, SlotType type) {
        ItemButton button = new ItemButton() {
            @Override
            protected void onClick() {
                Item equipped = equippedItem(type);
                if (equipped == null) {
                    chooseEquipment(type, this);
                } else {
                    returnEquipment(type, equipped, this);
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

        button.setRect(column * (SLOT + GAP), y, SLOT, SLOT);
        refreshEquipmentButton(button, type);
        add(button);
    }

    private void addWandButton(int index, float startY) {
        int col = index % COLS;
        int row = index / COLS;

        ItemButton button = new ItemButton() {
            @Override
            protected void onClick() {
                if (index < inventory.wands().size()) {
                    Wand wand = inventory.removeWand(index);
                    returnToPlayer(wand);
                    refreshWandButtons();
                } else if (index == inventory.wands().size() && inventory.hasWandSpace()) {
                    chooseWand(this);
                }
            }

            @Override
            protected boolean onLongClick() {
                if (index >= inventory.wands().size()) {
                    return false;
                }
                GameScene.show(new WndInfoItem(inventory.wands().get(index)));
                return true;
            }
        };

        button.setRect(col * (SLOT + GAP), startY + row * (SLOT + GAP), SLOT, SLOT);
        button.slot().name = "cohero_wand_" + index;
        button.item(wandItemFor(index));
        add(button);
    }

    private void refreshWandButtons() {
        // Rebuilding the window is intentionally avoided. ItemButton references are not retained,
        // so close/reopen after a removal that shifts slots. This keeps slot identity deterministic.
        hide();
        GameScene.show(new WndCompanionInventory(companion));
    }

    private Item wandItemFor(int index) {
        if (index < inventory.wands().size()) {
            return inventory.wands().get(index);
        }
        if (index == inventory.wands().size() && inventory.hasWandSpace()) {
            return new WndBag.Placeholder(ItemSpriteSheet.WAND_HOLDER);
        }
        return new WndBag.Placeholder(ItemSpriteSheet.SOMETHING);
    }

    private void chooseEquipment(SlotType type, ItemButton button) {
        GameScene.selectItem(new WndBag.ItemSelector() {
            @Override
            public String textPrompt() {
                return selectPrompt(type);
            }

            @Override
            public boolean itemSelectable(Item item) {
                switch (type) {
                    case WEAPON:
                        return item instanceof MeleeWeapon;
                    case ARMOR:
                        return item instanceof Armor;
                    case RING_ONE:
                    case RING_TWO:
                        return item instanceof Ring;
                    default:
                        return false;
                }
            }

            @Override
            public void onSelect(Item item) {
                if (item == null || !takeFromPlayer(item)) {
                    return;
                }

                switch (type) {
                    case WEAPON:
                        inventory.equipWeapon((MeleeWeapon) item);
                        break;
                    case ARMOR:
                        inventory.equipArmor((Armor) item);
                        break;
                    case RING_ONE:
                        inventory.equipRingOne((Ring) item);
                        break;
                    case RING_TWO:
                        inventory.equipRingTwo((Ring) item);
                        break;
                }
                refreshEquipmentButton(button, type);
            }
        });
    }

    private void chooseWand(ItemButton button) {
        GameScene.selectItem(new WndBag.ItemSelector() {
            @Override
            public String textPrompt() {
                return selectWandPrompt();
            }

            @Override
            public boolean itemSelectable(Item item) {
                return item instanceof Wand;
            }

            @Override
            public void onSelect(Item item) {
                if (!(item instanceof Wand) || !takeFromPlayer(item)) {
                    return;
                }
                inventory.addWand((Wand) item);
                button.item(item);
                refreshWandButtons();
            }
        });
    }

    private boolean takeFromPlayer(Item item) {
        if (!CompanionInventory.supported(item)) {
            throw new IllegalArgumentException("Unsupported CoHero item: " + item.getClass().getName());
        }

        if (item.isEquipped(Dungeon.hero)) {
            if (!(item instanceof EquipableItem)) {
                throw new IllegalStateException("Equipped item is not EquipableItem: " + item.getClass().getName());
            }
            return ((EquipableItem) item).doUnequip(Dungeon.hero, false, false);
        }

        item.detach(Dungeon.hero.belongings.backpack);
        return true;
    }

    private void returnEquipment(SlotType type, Item item, ItemButton button) {
        if (item.cursed && companion.buff(MagicImmune.class) == null) {
            GLog.w(Messages.get(EquipableItem.class, "unequip_cursed"));
            return;
        }

        switch (type) {
            case WEAPON:
                inventory.removeWeapon();
                break;
            case ARMOR:
                inventory.removeArmor();
                break;
            case RING_ONE:
                inventory.removeRingOne();
                break;
            case RING_TWO:
                inventory.removeRingTwo();
                break;
        }

        returnToPlayer(item);
        refreshEquipmentButton(button, type);
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

    private void refreshEquipmentButton(ItemButton button, SlotType type) {
        Item item = equippedItem(type);
        if (item != null) {
            button.item(item);
            return;
        }

        switch (type) {
            case WEAPON:
                button.item(new WndBag.Placeholder(ItemSpriteSheet.WEAPON_HOLDER));
                break;
            case ARMOR:
                button.item(new WndBag.Placeholder(ItemSpriteSheet.ARMOR_HOLDER));
                break;
            case RING_ONE:
            case RING_TWO:
                button.item(new WndBag.Placeholder(ItemSpriteSheet.RING_HOLDER));
                break;
        }
    }

    private String titleText() {
        if (Messages.lang() == Languages.CHI_TRAD) return "夥伴英雄背包";
        if (Messages.lang() == Languages.CHI_SMPL) return "伙伴英雄背包";
        return "Companion Inventory";
    }

    private String equipmentText() {
        if (Messages.lang() == Languages.CHI_TRAD) return "裝備：武器 / 護甲 / 戒指 / 戒指";
        if (Messages.lang() == Languages.CHI_SMPL) return "装备：武器 / 护甲 / 戒指 / 戒指";
        return "Equipment: weapon / armor / ring / ring";
    }

    private String wandsText() {
        if (Messages.lang() == Languages.CHI_TRAD) return "法杖";
        if (Messages.lang() == Languages.CHI_SMPL) return "法杖";
        return "Wands";
    }

    private String selectPrompt(SlotType type) {
        if (Messages.lang() == Languages.CHI_TRAD) {
            switch (type) {
                case WEAPON: return "選擇給夥伴英雄的武器";
                case ARMOR: return "選擇給夥伴英雄的護甲";
                default: return "選擇給夥伴英雄的戒指";
            }
        }
        if (Messages.lang() == Languages.CHI_SMPL) {
            switch (type) {
                case WEAPON: return "选择给伙伴英雄的武器";
                case ARMOR: return "选择给伙伴英雄的护甲";
                default: return "选择给伙伴英雄的戒指";
            }
        }
        switch (type) {
            case WEAPON: return "Select a weapon for your companion";
            case ARMOR: return "Select armor for your companion";
            default: return "Select a ring for your companion";
        }
    }

    private String selectWandPrompt() {
        if (Messages.lang() == Languages.CHI_TRAD) return "選擇放入夥伴背包的法杖";
        if (Messages.lang() == Languages.CHI_SMPL) return "选择放入伙伴背包的法杖";
        return "Select a wand for your companion";
    }

    private enum SlotType {
        WEAPON,
        ARMOR,
        RING_ONE,
        RING_TWO
    }
}
