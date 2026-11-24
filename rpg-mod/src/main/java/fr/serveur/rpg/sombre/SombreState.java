package fr.serveur.rpg.sombre;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * État persistant du monde sombre (world/data/survierpg_sombre.dat) : nuit permanente et jour invoqué,
 * coffres cachés en place, registre des objets exotiques.
 */
public final class SombreState extends PersistentState {
    private static final String ID = "survierpg_sombre";

    public static final class Coffre {
        public BlockPos pos;
        public String dim = "minecraft:overworld";
        public CoffresCaches.Type type = CoffresCaches.Type.CLASSIQUE;
        public boolean piege;
        public long cree;
        /** Coffre de boss : gardien en vie (null s'il n'a pas encore été réveillé), vaincu ou non. */
        public UUID boss;
        public boolean bossVaincu;
    }

    /** Statut d'un exotique : jamais apparu, en jeu (un exemplaire existe), perdu (détruit, disparu). */
    public enum Statut { ABSENT, EN_JEU, PERDU }

    public static final class Exo {
        /** Numéro de l'exemplaire valide : tout autre exemplaire trouvé est un duplicata (ou un ancien) et est supprimé. */
        public String serial = "";
        public Statut statut = Statut.ABSENT;
        public String porteur = "";
        public long vuLe, perduLe;
    }

    public boolean nuitPermanente = true;
    /** Éclat de Lumière : fin de la préparation, début et fin du jour invoqué, prochain Éclat possible (ms, 0 = rien). */
    public long prepUntil, dayStart, dayUntil, nextEclatAt;
    public String eclatBy = "";
    public final List<Coffre> coffres = new ArrayList<>();
    public final Map<String, Exo> exotiques = new LinkedHashMap<>();

    public static SombreState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(SombreState::fromNbt, SombreState::new, ID);
    }

    public Exo exo(String id) {
        return exotiques.computeIfAbsent(id, k -> new Exo());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("nuitPermanente", nuitPermanente);
        nbt.putLong("prepUntil", prepUntil);
        nbt.putLong("dayStart", dayStart);
        nbt.putLong("dayUntil", dayUntil);
        nbt.putLong("nextEclatAt", nextEclatAt);
        nbt.putString("eclatBy", eclatBy);
        NbtList list = new NbtList();
        for (Coffre c : coffres) {
            NbtCompound t = new NbtCompound();
            t.putLong("pos", c.pos.asLong());
            t.putString("dim", c.dim);
            t.putString("type", c.type.name());
            t.putBoolean("piege", c.piege);
            t.putLong("cree", c.cree);
            if (c.boss != null) t.putUuid("boss", c.boss);
            t.putBoolean("bossVaincu", c.bossVaincu);
            list.add(t);
        }
        nbt.put("coffres", list);
        NbtCompound exo = new NbtCompound();
        exotiques.forEach((id, e) -> {
            NbtCompound t = new NbtCompound();
            t.putString("serial", e.serial);
            t.putString("statut", e.statut.name());
            t.putString("porteur", e.porteur);
            t.putLong("vuLe", e.vuLe);
            t.putLong("perduLe", e.perduLe);
            exo.put(id, t);
        });
        nbt.put("exotiques", exo);
        return nbt;
    }

    public static SombreState fromNbt(NbtCompound nbt) {
        SombreState s = new SombreState();
        if (nbt.contains("nuitPermanente")) s.nuitPermanente = nbt.getBoolean("nuitPermanente");
        s.prepUntil = nbt.getLong("prepUntil");
        s.dayStart = nbt.getLong("dayStart");
        s.dayUntil = nbt.getLong("dayUntil");
        s.nextEclatAt = nbt.getLong("nextEclatAt");
        s.eclatBy = nbt.getString("eclatBy");
        NbtList list = nbt.getList("coffres", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound t = list.getCompound(i);
            Coffre c = new Coffre();
            c.pos = BlockPos.fromLong(t.getLong("pos"));
            c.dim = t.getString("dim");
            c.type = CoffresCaches.Type.parse(t.getString("type"));
            c.piege = t.getBoolean("piege");
            c.cree = t.getLong("cree");
            if (t.containsUuid("boss")) c.boss = t.getUuid("boss");
            c.bossVaincu = t.getBoolean("bossVaincu");
            s.coffres.add(c);
        }
        NbtCompound exo = nbt.getCompound("exotiques");
        for (String id : exo.getKeys()) {
            NbtCompound t = exo.getCompound(id);
            Exo e = new Exo();
            e.serial = t.getString("serial");
            try {
                e.statut = Statut.valueOf(t.getString("statut"));
            } catch (IllegalArgumentException ex) {
                e.statut = Statut.ABSENT;
            }
            e.porteur = t.getString("porteur");
            e.vuLe = t.getLong("vuLe");
            e.perduLe = t.getLong("perduLe");
            s.exotiques.put(id, e);
        }
        return s;
    }
}
