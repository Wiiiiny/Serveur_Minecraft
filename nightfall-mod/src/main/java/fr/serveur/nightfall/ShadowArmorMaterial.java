package fr.serveur.nightfall;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Matériau de la Capuche d'ombre : protection équivalente au cuir. */
public final class ShadowArmorMaterial implements ArmorMaterial {
    public static final ShadowArmorMaterial INSTANCE = new ShadowArmorMaterial();

    private ShadowArmorMaterial() {}

    @Override public int getDurability(ArmorItem.Type type) { return 165; }
    @Override public int getProtection(ArmorItem.Type type) { return 1; }
    @Override public int getEnchantability() { return 15; }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ITEM_ARMOR_EQUIP_LEATHER; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.ofItems(Items.PHANTOM_MEMBRANE); }
    /** Texture portée : assets/minecraft/textures/models/armor/nightfall_shadow_layer_1.png */
    @Override public String getName() { return "nightfall_shadow"; }
    @Override public float getToughness() { return 0.0f; }
    @Override public float getKnockbackResistance() { return 0.0f; }
}
