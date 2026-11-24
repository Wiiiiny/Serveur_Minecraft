package fr.serveur.rpg.sombre;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.SurvieRpg;
import fr.serveur.rpg.economy.Bank;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Objets exotiques : chacun a un nom, un lore, un prix, et n'existe qu'en un seul exemplaire à la fois.
 * Le registre (SombreState) retient le numéro de l'exemplaire valide. Toutes les 2 secondes, les inventaires des joueurs
 * (et leur coffre de l'Ender) sont contrôlés : un exemplaire au mauvais numéro, ou un second exemplaire du même numéro,
 * est un duplicata — il est supprimé, l'origine est journalisée et les GM connectés sont prévenus (sans que les joueurs le voient).
 * « Disparaître » = l'objet au sol est détruit (lave, feu, explosion, vide, cactus, /kill) ou disparaît après 5 minutes au sol
 * (ramassé par un joueur ou un entonnoir : il n'est pas perdu).
 * L'exotique est alors perdu ; après le délai de réintroduction, il peut revenir dans un coffre exotique (nouveau numéro :
 * un ancien exemplaire retrouvé plus tard serait supprimé). Les exotiques sont incassables et ne s'achètent jamais en boutique.
 */
public final class Exotiques {
    private static final DustParticleEffect YELLOW = new DustParticleEffect(new Vector3f(1f, 0.85f, 0.15f), 0.9f);

    private Exotiques() {}

    public static boolean isExotic(ItemStack s) {
        return Rarete.isExotic(s);
    }

    public static String idOf(ItemStack s) {
        return s.getNbt() == null ? "" : s.getNbt().getCompound(Rarete.EXO_NBT).getString("id");
    }

    public static String serialOf(ItemStack s) {
        return s.getNbt() == null ? "" : s.getNbt().getCompound(Rarete.EXO_NBT).getString("serial");
    }

    public static SombreConfig.Exotique def(String id) {
        for (SombreConfig.Exotique e : SombreConfig.objets().exotiques) if (e.id.equals(id)) return e;
        return null;
    }

    public static ItemStack create(SombreConfig.Exotique d, String serial) {
        Identifier itemId = Identifier.tryParse(d.objet);
        Item item = itemId == null ? Items.AIR : Registries.ITEM.get(itemId);
        if (item == Items.AIR) {
            SurvieRpg.LOGGER.warn("Exotique {} : objet inconnu {}", d.id, d.objet);
            return ItemStack.EMPTY;
        }
        ItemStack st = new ItemStack(item);
        st.setCustomName(Text.literal(d.nom).setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withBold(true).withItalic(false)));
        NbtList lore = new NbtList();
        lore.add(line(Text.literal("Exemplaire unique sur tout le serveur").setStyle(Style.EMPTY.withColor(Formatting.GOLD).withItalic(false))));
        for (String l : d.lore) lore.add(line(Text.literal(l).setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY).withItalic(true))));
        if (d.prix > 0) lore.add(line(Text.literal("Valeur : " + Bank.format(d.prix)).setStyle(Style.EMPTY.withColor(Formatting.GOLD).withItalic(false))));
        st.getOrCreateSubNbt("display").put("Lore", lore);
        for (Map.Entry<String, Integer> e : d.enchantements.entrySet()) {
            Identifier eid = Identifier.tryParse(e.getKey());
            Enchantment ench = eid == null ? null : Registries.ENCHANTMENT.get(eid);
            if (ench != null) st.addEnchantment(ench, Math.max(1, e.getValue()));
        }
        st.getOrCreateNbt().putBoolean("Unbreakable", true);
        NbtCompound exo = new NbtCompound();
        exo.putString("id", d.id);
        exo.putString("serial", serial);
        st.getOrCreateNbt().put(Rarete.EXO_NBT, exo);
        return st;
    }

    private static NbtString line(Text t) {
        return NbtString.of(Text.Serializer.toJson(t));
    }

    /** Un exotique qui peut apparaître maintenant (jamais apparu, ou perdu depuis assez longtemps), au hasard ; null sinon. */
    public static String available(MinecraftServer server) {
        SombreState s = SombreState.get(server);
        long now = System.currentTimeMillis();
        long delay = (long) (SombreConfig.objets().reintroductionHeures * 3_600_000);
        List<String> ok = new ArrayList<>();
        for (SombreConfig.Exotique d : SombreConfig.objets().exotiques) {
            SombreState.Exo e = s.exotiques.get(d.id);
            if (e == null || e.statut == SombreState.Statut.ABSENT
                    || (e.statut == SombreState.Statut.PERDU && now - e.perduLe >= delay)) ok.add(d.id);
        }
        return ok.isEmpty() ? null : ok.get(server.getOverworld().random.nextInt(ok.size()));
    }

    /** Fait apparaître l'exotique (nouveau numéro d'exemplaire). @return l'objet, ou EMPTY si l'exotique n'existe pas. */
    public static ItemStack introduce(MinecraftServer server, String id, String holder, String origine, boolean announce) {
        SombreConfig.Exotique d = def(id);
        if (d == null) return ItemStack.EMPTY;
        SombreState s = SombreState.get(server);
        SombreState.Exo e = s.exo(id);
        String old = e.serial;
        e.serial = UUID.randomUUID().toString();
        e.statut = SombreState.Statut.EN_JEU;
        e.porteur = holder;
        e.vuLe = System.currentTimeMillis();
        s.markDirty();
        Journal.write(server, "exotiques", "introduction", "id", id, "serial", e.serial, "ancien", old, "porteur", holder, "origine", origine);
        if (announce) Msg.broadcast(server, Text.literal("✦ L'objet exotique « " + d.nom + " » a été retrouvé par " + holder + ".").formatted(Formatting.YELLOW));
        return create(d, e.serial);
    }

    /** L'exemplaire valide a été détruit ou a disparu. */
    public static void markLost(MinecraftServer server, ItemStack stack, String cause) {
        String id = idOf(stack), serial = serialOf(stack);
        SombreState s = SombreState.get(server);
        SombreState.Exo e = s.exotiques.get(id);
        if (e == null || !serial.equals(e.serial) || e.statut != SombreState.Statut.EN_JEU) return;
        e.statut = SombreState.Statut.PERDU;
        e.perduLe = System.currentTimeMillis();
        s.markDirty();
        SombreConfig.Exotique d = def(id);
        Journal.write(server, "exotiques", "perte", "id", id, "serial", serial, "cause", cause, "dernierPorteur", e.porteur);
        gmAlert(server, "Exotique perdu : " + (d == null ? id : d.nom) + " (" + cause + ", dernier porteur " + e.porteur + ").");
    }

    /** Alerte invisible pour les joueurs : journal du serveur et GM connectés. */
    static void gmAlert(MinecraftServer server, String message) {
        SurvieRpg.LOGGER.warn("[exotiques] {}", message);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.hasPermissionLevel(2)) p.sendMessage(Text.literal("[GM] " + message).formatted(Formatting.GOLD), false);
        }
    }

    // ------------------------------------------------------------------ contrôle des inventaires

    /**
     * Contrôle un inventaire : supprime les duplicatas, met à jour le porteur. {@code seen} : numéros déjà vus pendant ce
     * contrôle (tous joueurs confondus). @return vrai si un objet a été supprimé.
     */
    public static boolean scan(ServerPlayerEntity p, Inventory inv, String where, Set<String> seen) {
        MinecraftServer server = p.getServer();
        SombreState s = SombreState.get(server);
        boolean removed = false;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty() || !isExotic(st)) continue;
            String id = idOf(st), serial = serialOf(st);
            SombreState.Exo e = s.exotiques.get(id);
            boolean valid = def(id) != null && e != null && e.statut != SombreState.Statut.ABSENT && serial.equals(e.serial);
            String reason = !valid ? (e != null && !serial.equals(e.serial) ? "numéro périmé ou falsifié" : "exotique inconnu")
                    : !seen.add(serial) ? "second exemplaire du même numéro" : null;
            if (reason != null) {
                inv.setStack(i, ItemStack.EMPTY);
                removed = true;
                Journal.write(server, "exotiques", "duplicata_supprime", "id", id, "serial", serial, "joueur", p.getGameProfile().getName(),
                        "emplacement", where, "raison", reason, "pos", p.getBlockPos().toShortString());
                gmAlert(server, "Duplicata supprimé chez " + p.getGameProfile().getName() + " : " + id + " (" + reason + ", " + where + ").");
                continue;
            }
            if (e.statut != SombreState.Statut.EN_JEU) {
                e.statut = SombreState.Statut.EN_JEU;
                Journal.write(server, "exotiques", "retrouve", "id", id, "serial", serial, "joueur", p.getGameProfile().getName());
            }
            if (!e.porteur.equals(p.getGameProfile().getName())) {
                Journal.write(server, "exotiques", "porteur", "id", id, "serial", serial, "avant", e.porteur, "apres", p.getGameProfile().getName());
                e.porteur = p.getGameProfile().getName();
            }
            e.vuLe = System.currentTimeMillis();
            s.markDirty();
        }
        return removed;
    }

    /** Effets des exotiques portés au bon endroit (appelé toutes les 2 secondes). */
    public static void applyEffects(ServerPlayerEntity p) {
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.isEmpty() || !isExotic(st)) continue;
            SombreConfig.Exotique d = def(idOf(st));
            if (d == null || d.effets.isEmpty()) continue;
            boolean active = switch (d.emplacement) {
                case "main" -> p.getMainHandStack() == st;
                case "secondaire" -> p.getOffHandStack() == st;
                case "armure" -> isWorn(p, st);
                case "inventaire" -> true;
                default -> false;
            };
            if (!active) continue;
            for (SombreConfig.Effet ef : d.effets) {
                Identifier eid = Identifier.tryParse(ef.effet);
                StatusEffect effect = eid == null ? null : Registries.STATUS_EFFECT.get(eid);
                if (effect == null) continue;
                int duration = effect == StatusEffects.NIGHT_VISION ? 320 : 120;
                p.addStatusEffect(new StatusEffectInstance(effect, duration, Math.max(0, ef.niveau), true, false, true));
            }
        }
    }

    private static boolean isWorn(ServerPlayerEntity p, ItemStack st) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (p.getEquippedStack(slot) == st) return true;
        }
        return false;
    }

    /** Particules jaunes autour d'un joueur qui tient un exotique. */
    public static void sparkleHolder(ServerPlayerEntity p) {
        if (!(isExotic(p.getMainHandStack()) || isExotic(p.getOffHandStack()))) return;
        ServerWorld w = p.getServerWorld();
        w.spawnParticles(YELLOW, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.35, 0.4, 0.35, 0);
    }

    // ------------------------------------------------------------------ objet au sol (mixin ItemEntity)

    public static void onItemEntityTick(ItemEntity e) {
        if (e.age % 8 != 0 || !(e.getWorld() instanceof ServerWorld w) || !isExotic(e.getStack())) return;
        w.spawnParticles(YELLOW, e.getX(), e.getY() + 0.35, e.getZ(), 2, 0.15, 0.15, 0.15, 0);
    }

    /** Un exotique au sol vient d'être détruit ou de disparaître (mixins ItemEntity, ItemStack, Entity). */
    public static void onItemEntityLost(ItemEntity e, String cause) {
        if (!(e.getWorld() instanceof ServerWorld w)) return;
        ItemStack st = e.getStack();
        if (st.isEmpty() || !isExotic(st)) return;
        markLost(w.getServer(), st, cause + " en " + e.getBlockPos().toShortString());
    }
}
