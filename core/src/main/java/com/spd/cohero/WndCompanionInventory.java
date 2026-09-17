package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Belongings;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
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
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;

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

        RenderedTextBlock backpackLabel = PixelScene.renderTextBlock(backpackText(), 7);
        backpackLabel.maxWidth(WIDTH);
        backpackLabel.setPos(0, equipmentY + SLOT + 5);
        add(backpackLabel);

        float backpackY = backpackLabel.bottom() + 2;
        for (int i = 0; i < CompanionInventory.BACKPACK_CAPACITY; i++) {
            addBackpackButton(i, backpackY);
        }

        int rows = (int) Math.ceil(CompanionInventory.BACKPACK_CAPACITY / (float) COLS);
        resize(WIDTH, (int) (backpackY + rows * (SLOT + GAP)));
    }

    private void addEquipmentButton(int column, float y, SlotType type) {
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

        button.setRect(column * (SLOT + GAP), y, SLOT, SLOT);
        button.item(equipmentItemFor(type));
        add(button);
    }

    private void addBackpackButton(int index, float startY) {
        int col = index % COLS;
        int row = index / COLS;

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

        button.setRect(col * (SLOT + GAP), startY + row * (SLOT + GAP), SLOT, SLOT);
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
        if (inventory.backpack().size() >= CompanionInventory.BACKPACK_CAPACITY) {
            GLog.w(backpackFullText());
            return;
        }

        GameScene.selectItem(new WndBag.ItemSelector() {
            @Override
            public String textPrompt() {
                return selectBackpackItemPrompt();
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
                    backpackActionPrompt(),
                    equipText(),
                    giveToHeroText()) {
                @Override
                protected void onSelect(int index) {
                    if (index == 0) {
                        equipFromBackpack(item);
                    } else if (index == 1) {
                        giveBackpackItemToHero(item);
                    }
                }
            });
        } else if (item instanceof Weapon || item instanceof Wand) {
            GameScene.show(new WndOptions(
                    item.title(),
                    backpackActionPrompt(),
                    giveToHeroText()) {
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
                chooseRingSlotPrompt(),
                ringSlotText(1, inventory.ringOne()),
                ringSlotText(2, inventory.ringTwo())) {
            @Override
            protected void onSelect(int index) {
                equipRingInto(ring, index + 1);
            }
        });
    }

    private void equipRingInto(Ring ring, int slot) {
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

    private void warnUnequipBlocked(Item item) {
        if (item != null && inventory.cannotUnequip(item)) {
            GLog.w(Messages.get(EquipableItem.class, "unequip_cursed"));
        } else {
            GLog.w(backpackFullText());
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

    private String backpackText() {
        String count = inventory.backpack().size() + "/" + CompanionInventory.BACKPACK_CAPACITY;
        if (Messages.lang() == Languages.CHI_TRAD) return "背包 " + count + "（點空格加入物品）";
        if (Messages.lang() == Languages.CHI_SMPL) return "背包 " + count + "（点空格加入物品）";
        return "Backpack " + count + " (tap an empty slot to add)";
    }

    private String selectBackpackItemPrompt() {
        if (Messages.lang() == Languages.CHI_TRAD) return "選擇要交給夥伴英雄的武器、護甲、戒指或法杖";
        if (Messages.lang() == Languages.CHI_SMPL) return "选择要交给伙伴英雄的武器、护甲、戒指或法杖";
        return "Choose a weapon, armor, ring, or wand for your companion";
    }

    private String backpackActionPrompt() {
        if (Messages.lang() == Languages.CHI_TRAD) return "要怎麼處理這件物品？";
        if (Messages.lang() == Languages.CHI_SMPL) return "要怎么处理这件物品？";
        return "What should the companion do with this item?";
    }

    private String equipText() {
        if (Messages.lang() == Languages.CHI_TRAD) return "裝備";
        if (Messages.lang() == Languages.CHI_SMPL) return "装备";
        return "Equip";
    }

    private String giveToHeroText() {
        if (Messages.lang() == Languages.CHI_TRAD) return "交還玩家";
        if (Messages.lang() == Languages.CHI_SMPL) return "交还玩家";
        return "Give to hero";
    }

    private String chooseRingSlotPrompt() {
        if (Messages.lang() == Languages.CHI_TRAD) return "兩個戒指槽都已使用。要替換哪一枚？";
        if (Messages.lang() == Languages.CHI_SMPL) return "两个戒指槽都已使用。要替换哪一枚？";
        return "Both ring slots are occupied. Which ring should be replaced?";
    }

    private String ringSlotText(int slot, Ring ring) {
        if (Messages.lang() == Languages.CHI_TRAD) return "戒指 " + slot + "：" + ring.title();
        if (Messages.lang() == Languages.CHI_SMPL) return "戒指 " + slot + "：" + ring.title();
        return "Ring " + slot + ": " + ring.title();
    }

    private String backpackFullText() {
        if (Messages.lang() == Languages.CHI_TRAD) return "夥伴英雄的背包已滿。";
        if (Messages.lang() == Languages.CHI_SMPL) return "伙伴英雄的背包已满。";
        return "The companion's backpack is full.";
    }

    private enum SlotType {
        WEAPON,
        ARMOR,
        RING_ONE,
        RING_TWO
    }
}
