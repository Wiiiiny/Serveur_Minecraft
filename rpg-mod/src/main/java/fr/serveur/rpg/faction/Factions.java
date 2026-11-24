package fr.serveur.rpg.faction;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.economy.Economy;
import fr.serveur.rpg.sombre.Journal;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Factions : création (gratuite), invitations (tout membre peut inviter, nombre de membres illimité), rangs (Chef, Officier,
 * Vétéran, Membre, Recrue), niveau illimité et XP, trésor (contributions volontaires), boutique, missions du jour,
 * votes à plus de 50 % (exclusion, élection du chef, abandon de territoire), bannière, historique.
 */
public final class Factions {
    private Factions() {}

    public static FactionState state(MinecraftServer server) {
        return FactionState.get(server);
    }

    public static Faction of(ServerPlayerEntity p) {
        return state(p.getServer()).of(p.getUuid());
    }

    public static boolean isLeader(Faction f, UUID u) {
        Faction.Member m = f.member(u);
        return m != null && (m.rank == Faction.Rank.CHEF || m.rank == Faction.Rank.OFFICIER);
    }

    public static boolean isChief(Faction f, UUID u) {
        Faction.Member m = f.member(u);
        return m != null && m.rank == Faction.Rank.CHEF;
    }

    /** Message à tous les membres connectés. */
    public static void tell(MinecraftServer server, Faction f, Text t) {
        for (UUID u : f.members.keySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(u);
            if (p != null) Msg.send(p, Text.literal("[" + f.name + "] ").formatted(Formatting.DARK_AQUA).append(t));
        }
    }

    static void journal(MinecraftServer server, Faction f, String event, Object... kv) {
        Object[] all = new Object[kv.length + 2];
        all[0] = "faction";
        all[1] = f.name;
        System.arraycopy(kv, 0, all, 2, kv.length);
        Journal.write(server, "factions", event, all);
    }

    private static void dirty(MinecraftServer server) {
        state(server).markDirty();
    }

    // ------------------------------------------------------------------ création, membres, rangs

    public static String create(ServerPlayerEntity p, String rawName) {
        FactionState s = state(p.getServer());
        FactionConfig.Data c = FactionConfig.get();
        if (s.of(p.getUuid()) != null) return "Tu es déjà dans une faction.";
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.length() < c.nomMin || name.length() > c.nomMax) return "Le nom doit faire de " + c.nomMin + " à " + c.nomMax + " caractères.";
        if (!name.matches("[\\p{L}0-9 '_-]+")) return "Lettres, chiffres, espaces, apostrophes et tirets seulement.";
        if (s.byName(name) != null) return "Une faction porte déjà ce nom.";
        Faction f = new Faction();
        f.id = s.nextFactionId++;
        f.name = name;
        f.created = System.currentTimeMillis();
        f.bannerBase = DyeColor.values()[Math.floorMod(name.hashCode(), DyeColor.values().length)].getId();
        f.heartHp = c.coeurPointsDeVie;
        Faction.Member m = new Faction.Member();
        m.id = p.getUuid();
        m.name = p.getGameProfile().getName();
        m.rank = Faction.Rank.CHEF;
        m.joined = f.created;
        f.members.put(m.id, m);
        s.factions.put(f.id, f);
        s.invites.remove(p.getUuid());
        f.log("Faction fondée par " + m.name + ".");
        journal(p.getServer(), f, "creation", "chef", m.name);
        s.markDirty();
        Msg.broadcast(p.getServer(), Text.literal("⚑ " + m.name + " fonde la faction « " + name + " ».").formatted(Formatting.DARK_AQUA));
        return null;
    }

    public static String invite(ServerPlayerEntity from, String targetName) {
        MinecraftServer server = from.getServer();
        FactionState s = state(server);
        Faction f = s.of(from.getUuid());
        if (f == null) return "Tu n'es dans aucune faction.";
        UUID target = null;
        String name = targetName;
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(targetName);
        if (online != null) {
            target = online.getUuid();
            name = online.getGameProfile().getName();
        } else {
            for (var e : RpgState.get(server).players.entrySet()) {
                if (e.getValue().name.equalsIgnoreCase(targetName)) {
                    target = e.getKey();
                    name = e.getValue().name;
                }
            }
        }
        if (target == null) return "Joueur inconnu : " + targetName + ".";
        if (f.members.containsKey(target)) return name + " est déjà dans ta faction.";
        if (s.of(target) != null) return name + " est déjà dans une autre faction.";
        s.invites.computeIfAbsent(target, k -> new java.util.HashSet<>()).add(f.id);
        s.markDirty();
        f.log(from.getGameProfile().getName() + " invite " + name + ".");
        if (online != null) {
            Msg.send(online, Text.literal(from.getGameProfile().getName() + " t'invite dans la faction « " + f.name
                    + " » : menu B › Faction, ou /faction accepter " + f.name).formatted(Formatting.AQUA));
        }
        return null;
    }

    public static String join(ServerPlayerEntity p, int factionId) {
        FactionState s = state(p.getServer());
        if (s.of(p.getUuid()) != null) return "Tu es déjà dans une faction.";
        Faction f = s.factions.get(factionId);
        if (f == null) return "Cette faction n'existe plus.";
        if (!s.invites.getOrDefault(p.getUuid(), Set.of()).contains(factionId)) return "Tu n'as pas d'invitation de « " + f.name + " ».";
        Faction.Member m = new Faction.Member();
        m.id = p.getUuid();
        m.name = p.getGameProfile().getName();
        m.rank = Faction.Rank.RECRUE;
        m.joined = System.currentTimeMillis();
        f.members.put(m.id, m);
        s.invites.remove(p.getUuid());
        f.log(m.name + " rejoint la faction.");
        journal(p.getServer(), f, "adhesion", "joueur", m.name);
        s.markDirty();
        tell(p.getServer(), f, Text.literal(m.name + " rejoint la faction !").formatted(Formatting.GREEN));
        return null;
    }

    public static String leave(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        FactionState s = state(server);
        Faction f = s.of(p.getUuid());
        if (f == null) return "Tu n'es dans aucune faction.";
        if (s.warOf(f.id) != null) return "On ne quitte pas sa faction pendant une guerre.";
        if (isChief(f, p.getUuid())) {
            if (f.members.size() > 1) return "Le chef ne peut pas partir : faites d'abord élire un autre chef (onglet Membres).";
            dissolve(server, f, p.getGameProfile().getName() + " a dissous la faction");
            return null;
        }
        f.members.remove(p.getUuid());
        f.log(p.getGameProfile().getName() + " quitte la faction.");
        journal(server, f, "depart", "joueur", p.getGameProfile().getName());
        s.markDirty();
        tell(server, f, Text.literal(p.getGameProfile().getName() + " a quitté la faction.").formatted(Formatting.GRAY));
        return null;
    }

    /** Dissolution : territoires libérés, cœur retiré, guerres terminées sans vainqueur. */
    public static void dissolve(MinecraftServer server, Faction f, String reason) {
        FactionState s = state(server);
        Territories.removeHeart(server, f);
        for (FactionState.War w : s.wars) {
            if (!w.over && w.involves(f.id)) {
                w.over = true;
                w.ended = System.currentTimeMillis();
                w.reason = "dissolution";
            }
        }
        s.factions.remove(f.id);
        s.invites.values().forEach(set -> set.remove(f.id));
        s.markDirty();
        journal(server, f, "dissolution", "raison", reason);
        Msg.broadcast(server, Text.literal("⚑ La faction « " + f.name + " » est dissoute (" + reason + ").").formatted(Formatting.GRAY));
    }

    /** Le chef exclut directement un membre (sinon : vote d'exclusion). */
    public static String kick(ServerPlayerEntity chief, UUID target) {
        Faction f = of(chief);
        if (f == null || !isChief(f, chief.getUuid())) return "Seul le chef exclut directement (les autres membres peuvent lancer un vote).";
        Faction.Member m = f.member(target);
        if (m == null) return "Ce joueur n'est pas dans ta faction.";
        if (m.rank == Faction.Rank.CHEF) return "Le chef ne peut pas s'exclure.";
        remove(chief.getServer(), f, m, "exclu par " + chief.getGameProfile().getName());
        return null;
    }

    static void remove(MinecraftServer server, Faction f, Faction.Member m, String why) {
        f.members.remove(m.id);
        f.votes.removeIf(v -> m.id.equals(v.target));
        f.log(m.name + " est " + why + ".");
        journal(server, f, "exclusion", "joueur", m.name, "raison", why);
        dirty(server);
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.id);
        if (p != null) Msg.send(p, Text.literal("Tu as été exclu de la faction « " + f.name + " » (" + why + ").").formatted(Formatting.RED));
        tell(server, f, Text.literal(m.name + " est " + why + ".").formatted(Formatting.GRAY));
    }

    /** Le chef monte (+1) ou descend (−1) un membre d'un rang (Officier au plus). */
    public static String setRank(ServerPlayerEntity chief, UUID target, int delta) {
        Faction f = of(chief);
        if (f == null || !isChief(f, chief.getUuid())) return "Seul le chef gère les rangs.";
        Faction.Member m = f.member(target);
        if (m == null) return "Ce joueur n'est pas dans ta faction.";
        if (m.rank == Faction.Rank.CHEF) return "Le chef change par élection.";
        int i = m.rank.ordinal() - Integer.signum(delta);
        i = Math.max(Faction.Rank.OFFICIER.ordinal(), Math.min(Faction.Rank.RECRUE.ordinal(), i));
        Faction.Rank before = m.rank;
        m.rank = Faction.Rank.values()[i];
        if (before == m.rank) return delta > 0 ? m.name + " a déjà le rang le plus haut." : m.name + " est déjà Recrue.";
        f.log(m.name + " : " + before.nom + " → " + m.rank.nom + " (par " + chief.getGameProfile().getName() + ").");
        journal(chief.getServer(), f, "rang", "joueur", m.name, "rang", m.rank.name());
        dirty(chief.getServer());
        return null;
    }

    // ------------------------------------------------------------------ niveau et XP

    public static long xpNeeded(int level) {
        FactionConfig.Data c = FactionConfig.get();
        return Math.max(1, c.xpBase + (long) c.xpParNiveau * (level - 1));
    }

    public static void addXp(MinecraftServer server, Faction f, double amount, String why) {
        if (f == null || amount <= 0) return;
        f.xpFrac += amount;
        long whole = (long) Math.floor(f.xpFrac + 1e-9);
        dirty(server);
        if (whole <= 0) return;
        f.xpFrac -= whole;
        f.xp += whole;
        f.totalXp += whole;
        while (f.xp >= xpNeeded(f.level)) {
            f.xp -= xpNeeded(f.level);
            f.level++;
            long reward = FactionConfig.get().piecesParNiveau * f.level;
            f.bank += reward;
            f.log("Niveau " + f.level + " atteint (" + why + ") : +" + Bank.format(reward) + " au trésor, rayon " + f.radius() + " blocs.");
            journal(server, f, "niveau", "niveau", f.level, "tresor", reward);
            tell(server, f, Text.literal("La faction passe au niveau " + f.level + " ! +" + Bank.format(reward)
                    + " au trésor, territoires de " + f.radius() + " blocs de rayon.").formatted(Formatting.GOLD));
            if (f.level % 5 == 0) Msg.broadcast(server, Text.literal("⚑ La faction « " + f.name + " » atteint le niveau " + f.level + ".").formatted(Formatting.DARK_AQUA));
        }
    }

    /** Défaite : perd {@code fraction} de l'XP du niveau en cours, au plus un niveau. @return niveaux perdus. */
    public static int loseXp(Faction f, double fraction) {
        long loss = (long) Math.floor(xpNeeded(f.level) * fraction);
        f.xp -= loss;
        f.xpFrac = 0;
        if (f.xp >= 0) return 0;
        if (f.level <= 1) {
            f.xp = 0;
            return 0;
        }
        f.level--;
        f.xp = Math.max(0, xpNeeded(f.level) + f.xp);
        return 1;
    }

    // ------------------------------------------------------------------ trésor et boutique

    public static String deposit(ServerPlayerEntity p, long amount) {
        Faction f = of(p);
        if (f == null) return "Tu n'es dans aucune faction.";
        if (amount <= 0) return "Montant invalide.";
        String e = Economy.spend(p.getServer(), p.getUuid(), Economy.Source.FACTION, amount, "don à " + f.name);
        if (e != null) return e;
        f.bank += amount;
        f.member(p.getUuid()).contributed += amount;
        String name = p.getGameProfile().getName();
        f.log(name + " donne " + Bank.format(amount) + " au trésor.");
        journal(p.getServer(), f, "don", "joueur", name, "montant", amount, "tresor", f.bank);
        addXp(p.getServer(), f, amount * FactionConfig.get().xpParPieceDonnee, "dons");
        dirty(p.getServer());
        return null;
    }

    public static String withdraw(ServerPlayerEntity p, long amount) {
        Faction f = of(p);
        if (f == null || !isChief(f, p.getUuid())) return "Seul le chef retire de l'argent du trésor.";
        if (amount <= 0 || amount > f.bank) return "Le trésor contient " + Bank.format(f.bank) + ".";
        f.bank -= amount;
        Economy.credit(p.getServer(), p.getUuid(), Economy.Source.FACTION, amount, "retrait du trésor de " + f.name);
        f.log(p.getGameProfile().getName() + " retire " + Bank.format(amount) + " du trésor.");
        journal(p.getServer(), f, "retrait", "joueur", p.getGameProfile().getName(), "montant", amount, "tresor", f.bank);
        dirty(p.getServer());
        return null;
    }

    public static long price(Faction f, FactionConfig.Article a) {
        return "extension".equals(a.objet) ? a.prix * (f.extensions + 1) : a.prix;
    }

    public static ItemStack articleStack(FactionConfig.Article a) {
        if ("extension".equals(a.objet)) return new ItemStack(Items.FILLED_MAP);
        Identifier id = Identifier.tryParse(a.objet);
        Item item = id == null ? Items.AIR : Registries.ITEM.get(id);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, Math.min(a.quantite, item.getMaxCount() * 9)));
    }

    /** Achat à la boutique de faction, payé par le trésor (tout membre peut acheter). */
    public static String buy(ServerPlayerEntity p, int index) {
        Faction f = of(p);
        if (f == null) return "Tu n'es dans aucune faction.";
        List<FactionConfig.Article> list = FactionConfig.get().boutique;
        if (index < 0 || index >= list.size()) return "Article inconnu.";
        FactionConfig.Article a = list.get(index);
        if (f.level < a.niveau) return "Il faut une faction de niveau " + a.niveau + ".";
        long price = price(f, a);
        if (f.bank < price) return "Le trésor ne contient que " + Bank.format(f.bank) + " (il faut " + Bank.format(price) + ").";
        String what;
        if ("extension".equals(a.objet)) {
            f.extensions++;
            what = "extension de territoire n°" + f.extensions;
            tell(p.getServer(), f, Text.literal("Territoires agrandis : " + f.radius() + " blocs de rayon.").formatted(Formatting.GOLD));
        } else {
            ItemStack st = articleStack(a);
            if (st.isEmpty()) return "Objet inconnu dans factions.json : " + a.objet;
            what = a.quantite + "× " + st.getName().getString();
            int left = a.quantite;
            while (left > 0) {
                ItemStack part = st.copyWithCount(Math.min(left, st.getMaxCount()));
                left -= part.getCount();
                if (!p.getInventory().insertStack(part)) p.dropItem(part, false);
            }
        }
        f.bank -= price;
        f.log(p.getGameProfile().getName() + " achète " + what + " pour " + Bank.format(price) + ".");
        journal(p.getServer(), f, "achat", "joueur", p.getGameProfile().getName(), "article", what, "prix", price, "tresor", f.bank);
        addXp(p.getServer(), f, price * FactionConfig.get().xpParPieceDepensee, "améliorations");
        p.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1f);
        dirty(p.getServer());
        return null;
    }

    // ------------------------------------------------------------------ missions du jour

    public static void ensureMissions(Faction f) {
        String today = java.time.LocalDate.now().toString();
        if (today.equals(f.missionsDate) && !f.missions.isEmpty()) return;
        f.missionsDate = today;
        f.missions.clear();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < FactionConfig.get().missions.size(); i++) idx.add(i);
        Collections.shuffle(idx, new java.util.Random(today.hashCode() * 31L + f.id));
        for (int i = 0; i < Math.min(FactionConfig.get().missionsParJour, idx.size()); i++) {
            Faction.MissionProgress m = new Faction.MissionProgress();
            m.index = idx.get(i);
            f.missions.add(m);
        }
    }

    public static void progress(MinecraftServer server, UUID player, String type, int n) {
        Faction f = state(server).of(player);
        if (f == null) return;
        ensureMissions(f);
        List<FactionConfig.Mission> defs = FactionConfig.get().missions;
        for (Faction.MissionProgress m : f.missions) {
            if (m.done || m.index >= defs.size()) continue;
            FactionConfig.Mission d = defs.get(m.index);
            if (!d.type.equals(type)) continue;
            m.progress = Math.min(d.nombre, m.progress + n);
            dirty(server);
            if (m.progress >= d.nombre) {
                m.done = true;
                f.bank += d.pieces;
                f.log("Mission accomplie : " + d.texte + " (+" + d.xp + " XP, +" + Bank.format(d.pieces) + ").");
                journal(server, f, "mission", "mission", d.texte, "xp", d.xp, "pieces", d.pieces);
                tell(server, f, Text.literal("Mission accomplie : " + d.texte + " ! +" + d.xp + " XP de faction, +"
                        + Bank.format(d.pieces) + " au trésor.").formatted(Formatting.GOLD));
                addXp(server, f, d.xp, "mission");
            }
        }
    }

    // ------------------------------------------------------------------ votes

    public static String startVote(ServerPlayerEntity p, Faction.VoteType type, UUID target, int territory) {
        MinecraftServer server = p.getServer();
        Faction f = of(p);
        if (f == null) return "Tu n'es dans aucune faction.";
        FactionConfig.Data c = FactionConfig.get();
        long now = System.currentTimeMillis();
        switch (type) {
            case EXCLUSION -> {
                Faction.Member m = f.member(target);
                if (m == null) return "Ce joueur n'est pas dans ta faction.";
                if (m.rank == Faction.Rank.CHEF) return "On n'exclut pas le chef : élisez-en un autre.";
                if (target.equals(p.getUuid())) return "Pour partir, quitte la faction.";
            }
            case CHEF -> {
                Faction.Member m = f.member(target);
                if (m == null) return "Ce joueur n'est pas dans ta faction.";
                if (m.rank == Faction.Rank.CHEF) return m.name + " est déjà le chef.";
                if (now - f.lastElection < (long) (c.delaiEntreElectionsHeures * 3_600_000)) {
                    return "Une élection a déjà eu lieu il y a moins de " + (int) c.delaiEntreElectionsHeures + " h.";
                }
                if (f.votes.stream().anyMatch(v -> v.type == Faction.VoteType.CHEF)) return "Une élection est déjà en cours.";
            }
            case ABANDON -> {
                if (!isLeader(f, p.getUuid())) return "Seuls le chef et les officiers proposent d'abandonner un territoire.";
                Faction.Territory t = f.territory(territory);
                if (t == null) return "Territoire inconnu.";
                if (t.id == f.baseTerritory && f.territories.size() > 1) return "La base principale s'abandonne en dernier.";
            }
        }
        for (Faction.Vote v : f.votes) {
            if (v.type == type && java.util.Objects.equals(v.target, target) && v.territory == territory) return "Ce vote est déjà ouvert.";
        }
        Faction.Vote v = new Faction.Vote();
        v.id = f.nextVoteId++;
        v.type = type;
        v.target = target;
        v.territory = territory;
        v.author = p.getUuid();
        v.start = now;
        v.end = now + (long) (c.voteHeures * 3_600_000);
        v.yes.add(p.getUuid());
        f.votes.add(v);
        if (type == Faction.VoteType.CHEF) f.lastElection = now;
        String label = describe(f, v);
        f.log(p.getGameProfile().getName() + " ouvre un vote : " + label + ".");
        journal(server, f, "vote_ouvert", "vote", label, "par", p.getGameProfile().getName());
        tell(server, f, Text.literal("Vote ouvert : " + label + " — menu B › Faction › Membres (plus de 50 % des membres).").formatted(Formatting.AQUA));
        dirty(server);
        resolve(server, f, v);
        return null;
    }

    public static String describe(Faction f, Faction.Vote v) {
        return switch (v.type) {
            case EXCLUSION -> "exclure " + nameOf(f, v.target);
            case CHEF -> "élire " + nameOf(f, v.target) + " chef";
            case ABANDON -> {
                Faction.Territory t = f.territory(v.territory);
                yield "abandonner le territoire " + (t == null ? "?" : t.center.getX() + " / " + t.center.getZ());
            }
        };
    }

    private static String nameOf(Faction f, UUID u) {
        Faction.Member m = u == null ? null : f.member(u);
        return m == null ? "?" : m.name;
    }

    public static String vote(ServerPlayerEntity p, int voteId, boolean yes) {
        Faction f = of(p);
        if (f == null) return "Tu n'es dans aucune faction.";
        Faction.Vote v = f.votes.stream().filter(x -> x.id == voteId).findFirst().orElse(null);
        if (v == null) return "Ce vote est terminé.";
        v.yes.remove(p.getUuid());
        v.no.remove(p.getUuid());
        (yes ? v.yes : v.no).add(p.getUuid());
        dirty(p.getServer());
        resolve(p.getServer(), f, v);
        return null;
    }

    /** Adopté dès que plus de la moitié des membres a voté oui ; rejeté si la moitié a voté non, ou à l'échéance. */
    static void resolve(MinecraftServer server, Faction f, Faction.Vote v) {
        v.yes.retainAll(f.members.keySet());
        v.no.retainAll(f.members.keySet());
        int n = f.members.size();
        boolean pass = v.yes.size() * 2 > n;
        boolean fail = !pass && (v.no.size() * 2 >= n || System.currentTimeMillis() >= v.end);
        if (!pass && !fail) return;
        f.votes.remove(v);
        String label = describe(f, v);
        f.log("Vote " + (pass ? "adopté" : "rejeté") + " : " + label + " (" + v.yes.size() + " oui, " + v.no.size() + " non).");
        journal(server, f, pass ? "vote_adopte" : "vote_rejete", "vote", label, "oui", v.yes.size(), "non", v.no.size());
        tell(server, f, Text.literal("Vote " + (pass ? "adopté" : "rejeté") + " : " + label + ".").formatted(pass ? Formatting.GREEN : Formatting.GRAY));
        dirty(server);
        if (!pass) return;
        switch (v.type) {
            case EXCLUSION -> {
                Faction.Member m = f.member(v.target);
                if (m != null && m.rank != Faction.Rank.CHEF) remove(server, f, m, "exclu par vote");
            }
            case CHEF -> {
                Faction.Member m = f.member(v.target);
                if (m == null) return;
                for (Faction.Member o : f.members.values()) if (o.rank == Faction.Rank.CHEF) o.rank = Faction.Rank.OFFICIER;
                m.rank = Faction.Rank.CHEF;
                Msg.broadcast(server, Text.literal("⚑ " + m.name + " est élu chef de la faction « " + f.name + " ».").formatted(Formatting.DARK_AQUA));
            }
            case ABANDON -> Territories.abandon(server, f, v.territory);
        }
    }

    public static void tickVotes(MinecraftServer server) {
        for (Faction f : List.copyOf(state(server).factions.values())) {
            for (Faction.Vote v : List.copyOf(f.votes)) resolve(server, f, v);
        }
    }

    // ------------------------------------------------------------------ bannière

    public static String setBanner(ServerPlayerEntity p, int base, List<Faction.Layer> layers) {
        Faction f = of(p);
        if (f == null || !isLeader(f, p.getUuid())) return "Seuls le chef et les officiers modifient la bannière.";
        if (base < 0 || base > 15) return "Couleur invalide.";
        if (layers.size() > Banners.MAX_LAYERS) return "Au plus " + Banners.MAX_LAYERS + " motifs.";
        for (Faction.Layer l : layers) {
            if (!Banners.validPattern(l.pattern) || l.color < 0 || l.color > 15) return "Motif invalide.";
        }
        f.bannerBase = base;
        f.layers.clear();
        f.layers.addAll(layers);
        f.log(p.getGameProfile().getName() + " modifie la bannière.");
        journal(p.getServer(), f, "banniere", "par", p.getGameProfile().getName());
        Territories.placeBannerOnHeart(p.getServer(), f);
        dirty(p.getServer());
        return null;
    }

    // ------------------------------------------------------------------ sources d'XP (appelées par les autres systèmes)

    public static void onKill(ServerPlayerEntity p, boolean boss) {
        Faction f = of(p);
        if (f == null) return;
        addXp(p.getServer(), f, boss ? FactionConfig.get().xpBoss : FactionConfig.get().xpMonstre, "combats");
        progress(p.getServer(), p.getUuid(), "tuer", 1);
    }

    /** Coffre caché ouvert par un groupe : XP pour chaque faction présente, bonus si plusieurs de ses membres y étaient. */
    public static void onChest(List<ServerPlayerEntity> group) {
        if (group.isEmpty()) return;
        MinecraftServer server = group.get(0).getServer();
        java.util.Map<Integer, Integer> count = new java.util.HashMap<>();
        for (ServerPlayerEntity m : group) {
            Faction f = of(m);
            if (f != null) count.merge(f.id, 1, Integer::sum);
        }
        FactionConfig.Data c = FactionConfig.get();
        count.forEach((id, n) -> {
            Faction f = state(server).factions.get(id);
            addXp(server, f, c.xpCoffreCache + (n >= 2 ? c.xpCoffreEnGroupe : 0), "exploration");
        });
        progress(server, group.get(0).getUuid(), "coffre", 1);
    }

    public static void onQuest(ServerPlayerEntity p) {
        Faction f = of(p);
        if (f == null) return;
        addXp(p.getServer(), f, FactionConfig.get().xpQuete, "quêtes");
        progress(p.getServer(), p.getUuid(), "quete", 1);
    }

    public static void onJobLevel(ServerPlayerEntity p) {
        addXp(p.getServer(), of(p), FactionConfig.get().xpNiveauMetier, "métiers");
    }

    public static void onPierre(ServerPlayerEntity p) {
        progress(p.getServer(), p.getUuid(), "pierre", 1);
    }

    public static void onEclat(ServerPlayerEntity p) {
        Faction f = of(p);
        if (f == null) return;
        addXp(p.getServer(), f, FactionConfig.get().xpEclat, "lumière");
        progress(p.getServer(), p.getUuid(), "eclat", 1);
    }

    /** Membres actifs (connectés dans les 7 derniers jours). */
    public static int activeMembers(MinecraftServer server, Faction f) {
        long since = System.currentTimeMillis() - 7L * 86_400_000L;
        int n = 0;
        for (UUID u : f.members.keySet()) {
            PlayerData d = RpgState.get(server).players.get(u);
            if (server.getPlayerManager().getPlayer(u) != null || (d != null && d.lastSeen >= since)) n++;
        }
        return n;
    }

    /** Puissance calculée par le serveur (classement) : niveau, territoires, guerres gagnées, membres actifs, trésor, points. */
    public static long power(MinecraftServer server, Faction f) {
        return f.level * 100L + f.territories.size() * 50L + f.warsWon * 200L + activeMembers(server, f) * 30L + f.bank / 100 + f.points;
    }
}
