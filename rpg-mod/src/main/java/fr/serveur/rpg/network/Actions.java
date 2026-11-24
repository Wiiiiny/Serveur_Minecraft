package fr.serveur.rpg.network;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.auto.AutoConfig;
import fr.serveur.rpg.auto.Forge;
import fr.serveur.rpg.commands.EconomyCommands;
import fr.serveur.rpg.data.MarketItem;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.data.Zone;
import fr.serveur.rpg.death.DeathSystem;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.economy.ObscureStone;
import fr.serveur.rpg.events.GmEvents;
import fr.serveur.rpg.gui.MarketMenus;
import fr.serveur.rpg.items.ModItems;
import fr.serveur.rpg.jobs.Job;
import fr.serveur.rpg.jobs.JobService;
import fr.serveur.rpg.jobs.Progression;
import fr.serveur.rpg.jobs.SideJob;
import fr.serveur.rpg.jobs.SideJobs;
import fr.serveur.rpg.protection.Lockpick;
import fr.serveur.rpg.world.DayCycle;
import fr.serveur.rpg.world.PlayableZone;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;

/** Exécute les actions demandées par l'interface (touche B). Tout est vérifié ici, côté serveur. */
public final class Actions {
    private Actions() {}

    public static void handle(ServerPlayerEntity p, NbtCompound n) {
        String a = n.getString("a");
        String result;
        try {
            result = a.startsWith("gm.") ? gm(p, a, n) : player(p, a, n);
        } catch (Exception e) {
            result = "!Action impossible : " + e.getMessage();
        }
        // Convention : un message qui commence par « ! » est une erreur.
        if (result == null) RpgNetwork.send(p, null, true);
        else if (result.startsWith("!")) RpgNetwork.send(p, result.substring(1), false);
        else RpgNetwork.send(p, result, true);
    }

    private static String err(String s) {
        return s == null ? null : "!" + s;
    }

    /** Résultat d'une action de faction : l'erreur, ou le message de réussite. */
    private static String fac(String error, String ok) {
        return error != null ? "!" + error : ok;
    }

    // ------------------------------------------------------------------ joueur

    private static String player(ServerPlayerEntity p, String a, NbtCompound n) {
        RpgState state = RpgState.get(p.getServer());
        PlayerData data = state.player(p);
        switch (a) {
            case "open", "refresh":
                return null;
            case "job.choose": {
                Job job = Job.parse(n.getString("job"));
                if (job == null) return "!Métier inconnu.";
                String e = JobService.choose(p, job);
                return e != null ? err(e) : "Tu es maintenant " + job.displayName + ".";
            }
            case "side.choose": {
                SideJob job = SideJob.parse(n.getString("job"));
                if (job == null) return "!Métier inconnu.";
                String e = SideJobs.choose(p, job);
                return e != null ? err(e) : "Métier supplémentaire : " + job.displayName + ".";
            }
            case "raid.enter": {
                String e = fr.serveur.rpg.raid.RaidCycle.enter(p);
                return e != null ? err(e) : null;
            }
            case "raid.repair": {
                String e = fr.serveur.rpg.commands.RaidCommands.repair(p);
                return e != null ? err(e) : "Brise-statue réparé.";
            }
            case "raid.companion":
                data.companionDisabled = !data.companionDisabled;
                state.markDirty();
                return data.companionDisabled ? "Compagnon désactivé." : "Compagnon activé.";
            case "raid.invite": {
                ServerPlayerEntity to = p.getServer().getPlayerManager().getPlayer(n.getString("name").trim());
                if (to == null) return "!Joueur introuvable (il doit être connecté).";
                String e = fr.serveur.rpg.raid.Party.invite(p, to);
                if (e != null) return err(e);
                Msg.send(to, Text.literal(p.getGameProfile().getName() + " t'invite dans son groupe de raid : menu B › Raid, ou /raid groupe accepter")
                        .formatted(Formatting.GOLD));
                return "Invitation envoyée.";
            }
            case "raid.accept": {
                String e = fr.serveur.rpg.raid.Party.accept(p);
                return e != null ? err(e) : "Tu as rejoint le groupe.";
            }
            case "raid.leave":
                fr.serveur.rpg.raid.Party.leave(p.getUuid());
                return "Tu as quitté ton groupe.";
            case "prestige":
                return Progression.prestige(p) ? "Prestige atteint ! Retour au niveau 1." : "!Le prestige est disponible au niveau " + Job.MAX_LEVEL + ".";
            case "forge": {
                ModItems.Tier tier = ModItems.Tier.valueOf(n.getString("tier"));
                String e = Forge.forge(p, tier);
                return e != null ? err(e) : "Outil forgé !";
            }
            case "bank.deposit": {
                int amount = n.getInt("amount");
                int all = Bank.countCurrency(p);
                int x = amount < 0 ? all : amount;
                if (x <= 0 || !Bank.deposit(p, x)) return "!Tu n'as pas assez de Pièces d'or sur toi.";
                return "Dépôt de " + Bank.format(x) + ".";
            }
            case "bank.withdraw": {
                long amount = n.getLong("amount");
                if (amount <= 0) return "!Indique un montant.";
                return Bank.withdraw(p, amount) ? "Retrait de " + Bank.format(amount) + "." : "!Solde insuffisant.";
            }
            case "bank.transfer": {
                String e = EconomyCommands.transfer(p, n.getString("name").trim(), n.getLong("amount"));
                return e != null ? err(e) : "Virement envoyé.";
            }
            case "hdv.click": {
                MarketMenus.Result r = MarketMenus.clickAuction(p, n.getInt("id"));
                return (r.ok() ? "" : "!") + r.message();
            }
            case "hdv.sell": {
                String e = EconomyCommands.sellHeld(p, n.getLong("price"));
                return e != null ? err(e) : "Objet mis en vente.";
            }
            case "shop.buy": {
                MarketMenus.Result r = MarketMenus.buyFromShop(p, n.getInt("id"));
                return (r.ok() ? "" : "!") + r.message();
            }
            case "fac.create": return fac(fr.serveur.rpg.faction.Factions.create(p, n.getString("name")), "Faction fondée !");
            case "fac.invite": return fac(fr.serveur.rpg.faction.Factions.invite(p, n.getString("name")), "Invitation envoyée à " + n.getString("name") + ".");
            case "fac.join": return fac(fr.serveur.rpg.faction.Factions.join(p, n.getInt("id")), "Bienvenue dans ta faction !");
            case "fac.leave": return fac(fr.serveur.rpg.faction.Factions.leave(p), "Tu as quitté ta faction.");
            case "fac.kick": return fac(fr.serveur.rpg.faction.Factions.kick(p, UUID.fromString(n.getString("uuid"))), "Membre exclu.");
            case "fac.rank": return fac(fr.serveur.rpg.faction.Factions.setRank(p, UUID.fromString(n.getString("uuid")), n.getInt("delta")), "Rang modifié.");
            case "fac.vote.start": {
                fr.serveur.rpg.faction.Faction.VoteType type = fr.serveur.rpg.faction.Faction.VoteType.valueOf(n.getString("type"));
                UUID target = n.getString("uuid").isEmpty() ? null : UUID.fromString(n.getString("uuid"));
                return fac(fr.serveur.rpg.faction.Factions.startVote(p, type, target, n.getInt("territory")), "Vote ouvert.");
            }
            case "fac.vote": return fac(fr.serveur.rpg.faction.Factions.vote(p, n.getInt("id"), n.getBoolean("yes")), "Vote enregistré.");
            case "fac.deposit": return fac(fr.serveur.rpg.faction.Factions.deposit(p, n.getLong("amount")), "Merci pour ta contribution !");
            case "fac.withdraw": return fac(fr.serveur.rpg.faction.Factions.withdraw(p, n.getLong("amount")), "Retrait fait.");
            case "fac.buy": return fac(fr.serveur.rpg.faction.Factions.buy(p, n.getInt("index")), "Achat fait.");
            case "fac.chest": {
                fr.serveur.rpg.faction.Faction f = fr.serveur.rpg.faction.Factions.of(p);
                return fac(f == null ? "Tu n'es dans aucune faction." : fr.serveur.rpg.faction.Territories.openChest(p, f), null);
            }
            case "fac.base.tp": return fac(fr.serveur.rpg.faction.Territories.teleportBase(p), "Ne bouge plus…");
            case "fac.base.move": return fac(fr.serveur.rpg.faction.Territories.moveBase(p), "Base déplacée ici.");
            case "fac.war.declare": return fac(fr.serveur.rpg.faction.Wars.declare(p, n.getInt("id")), "Guerre déclarée.");
            case "fac.war.night": return fac(fr.serveur.rpg.faction.Wars.setNight(p, n.getBoolean("on")), "Choix enregistré.");
            case "fac.war.offer": return fac(fr.serveur.rpg.faction.Wars.offerCapitulation(p), "Capitulation proposée.");
            case "fac.war.answer": return fac(fr.serveur.rpg.faction.Wars.answerCapitulation(p, n.getBoolean("yes")), n.getBoolean("yes") ? "Capitulation acceptée." : "Capitulation refusée.");
            case "fac.banner": {
                java.util.List<fr.serveur.rpg.faction.Faction.Layer> layers = new java.util.ArrayList<>();
                net.minecraft.nbt.NbtList l = n.getList("layers", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < l.size(); i++) layers.add(new fr.serveur.rpg.faction.Faction.Layer(l.getCompound(i).getString("p"), l.getCompound(i).getInt("c")));
                return fac(fr.serveur.rpg.faction.Factions.setBanner(p, n.getInt("base"), layers), "Bannière enregistrée.");
            }
            case "fac.banner.copy": {
                fr.serveur.rpg.faction.Faction held = fr.serveur.rpg.faction.Banners.readHeld(p.getMainHandStack());
                if (held == null) return "!Prends une bannière en main (faite au métier à tisser).";
                return fac(fr.serveur.rpg.faction.Factions.setBanner(p, held.bannerBase, held.layers), "Bannière copiée.");
            }
            case "lore.save": {
                String text = n.getString("text").replace("§", "");
                if (text.length() > 1500) text = text.substring(0, 1500);
                data.lore = text;
                data.lorePublic = n.getBoolean("public");
                state.markDirty();
                return data.lorePublic ? "Histoire enregistrée (publique : /lore " + data.name + ")." : "Histoire enregistrée (privée).";
            }
            case "eclat.use": {
                ItemStack eclat = ItemStack.EMPTY;
                for (ItemStack s : p.getInventory().main) if (s.isOf(fr.serveur.rpg.sombre.SombreItems.ECLAT_LUMIERE)) { eclat = s; break; }
                if (eclat.isEmpty()) return "!Tu n'as pas d'Éclat de Lumière.";
                String e = fr.serveur.rpg.sombre.Lumiere.use(p);
                if (e != null) return err(e);
                if (!p.getAbilities().creativeMode) eclat.decrement(1);
                return "L'Éclat s'éveille : le jour approche.";
            }
            case "shop.sell": {
                MarketMenus.Result r = MarketMenus.sellToShop(p, n.getInt("id"));
                return (r.ok() ? "" : "!") + r.message();
            }
            case "death.spectator":
                if (!data.awaitingRevive) return "!Tu n'es pas mort.";
                DeathSystem.goSpectator(p);
                return "Mode spectateur.";
            case "death.room":
                if (!data.awaitingRevive) return "!Tu n'es pas mort.";
                return DeathSystem.goWaitingRoom(p) ? "Bienvenue dans la salle d'attente." : "!Aucune salle d'attente définie.";
            case "death.tear":
                return DeathSystem.useTear(p) ? "La Larme de vie te ramène à la vie." : "!Impossible d'utiliser une Larme de vie.";
            default:
                return "!Action inconnue.";
        }
    }

    // ------------------------------------------------------------------ GM

    private static String gm(ServerPlayerEntity p, String a, NbtCompound n) {
        if (!p.hasPermissionLevel(2)) return "!Réservé au GM.";
        MinecraftServer server = p.getServer();
        RpgState state = RpgState.get(server);
        AutoConfig.Data cfg = AutoConfig.get();
        switch (a) {
            case "gm.player": return gmPlayer(p, state, n);
            case "gm.set": {
                String key = n.getString("key");
                // « value » = valeur saisie par le GM ; sinon « delta » (ancien fonctionnement, interrupteurs).
                boolean abs = n.contains("value");
                double v = n.getDouble("value"), d = n.getDouble("delta");
                switch (key) {
                    case "dayMin" -> state.dayMinutes = clamp(abs ? v : state.dayMinutes + d, 0.5, 600);
                    case "permanentNight" -> {
                        fr.serveur.rpg.sombre.SombreState ss = fr.serveur.rpg.sombre.SombreState.get(server);
                        ss.nuitPermanente = !ss.nuitPermanente;
                        ss.markDirty();
                        fr.serveur.rpg.sombre.Journal.write(server, "gm", "nuit_permanente_" + ss.nuitPermanente, "gm", p.getGameProfile().getName());
                    }
                    case "nightMin" -> state.nightMinutes = clamp(abs ? v : state.nightMinutes + d, 0.5, 600);
                    case "lockpick" -> state.lockpickChance = (int) clamp(abs ? v : state.lockpickChance + d, 0, 100);
                    case "prestigeBonus" -> state.prestigeBonusPercent = (int) clamp(abs ? v : state.prestigeBonusPercent + d, 0, 1000);
                    case "jobPrice" -> cfg.prixChangementMetier = (int) clamp(abs ? v : cfg.prixChangementMetier + d, 0, 1_000_000);
                    case "dailyCount" -> cfg.quetesParJour = (int) clamp(abs ? v : cfg.quetesParJour + d, 1, 8);
                    case "rewardMult" -> cfg.multiplicateurRecompenses = Math.round(clamp(abs ? v : cfg.multiplicateurRecompenses + d, 0.1, 100) * 10) / 10.0;
                    case "autoEvents" -> cfg.evenementsAuto = !cfg.evenementsAuto;
                    case "eventInterval" -> cfg.evenementIntervalleMinutes = (int) clamp(abs ? v : cfg.evenementIntervalleMinutes + d, 5, 10080);
                    case "eventDuration" -> cfg.evenementDureeMinutes = (int) clamp(abs ? v : cfg.evenementDureeMinutes + d, 1, 600);
                    case "eventMinPlayers" -> cfg.evenementJoueursMin = (int) clamp(abs ? v : cfg.evenementJoueursMin + d, 1, 100);
                    case "eventRewards" -> {
                        java.util.List<Integer> list = new java.util.ArrayList<>();
                        for (String part : n.getString("text").split("[^0-9]+")) if (!part.isEmpty()) list.add(Integer.parseInt(part));
                        if (list.isEmpty()) return "!Indique les récompenses, ex. 100 50 25.";
                        cfg.evenementRecompenses = list;
                    }
                    case "nether" -> {
                        state.netherOpen = !state.netherOpen;
                        Msg.broadcast(server, Text.literal(state.netherOpen ? "Le Nether est désormais ouvert !" : "Le Nether est scellé.").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
                    }
                    case "end" -> {
                        state.endOpen = !state.endOpen;
                        Msg.broadcast(server, Text.literal(state.endOpen ? "L'End est désormais ouvert !" : "L'End est scellé.").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
                    }
                    default -> { return "!Réglage inconnu."; }
                }
                state.markDirty();
                AutoConfig.save();
                return "Réglage enregistré.";
            }
            case "gm.time": {
                if (fr.serveur.rpg.sombre.Lumiere.controls(server)) {
                    boolean night = "night".equals(n.getString("to"));
                    if (night) fr.serveur.rpg.sombre.Lumiere.forceNight(server);
                    else fr.serveur.rpg.sombre.Lumiere.forceDay(server, "le destin");
                    fr.serveur.rpg.sombre.Journal.write(server, "gm", night ? "lumiere_nuit" : "lumiere_jour", "gm", p.getGameProfile().getName());
                    return night ? "La nuit retombe." : "Le jour se lève (jour invoqué).";
                }
                ServerWorld ow = server.getOverworld();
                long target = "night".equals(n.getString("to")) ? DayCycle.NIGHT_START : DayCycle.NIGHT_END;
                long day = ow.getTimeOfDay() - Math.floorMod(ow.getTimeOfDay(), 24000L);
                long t = day + target;
                if (t <= ow.getTimeOfDay()) t += 24000;
                ow.setTimeOfDay(t);
                return "night".equals(n.getString("to")) ? "La nuit tombe." : "Le jour se lève.";
            }
            case "gm.border.corner": {
                int corner = n.getInt("corner") == 2 ? 2 : 1;
                state.setBorderCorner(corner, p.getWorld().getRegistryKey().getValue().toString(), p.getBlockPos());
                PlayableZone.Box box = PlayableZone.box(state);
                return "Coin " + corner + " défini ici." + (box == null ? " Définis l'autre coin pour activer la zone." : " Zone jouable active.");
            }
            case "gm.border.clear":
                state.clearBorder();
                return "Zone jouable retirée.";
            case "gm.raid.regen": {
                String e = fr.serveur.rpg.raid.RaidCycle.startCycle(server, "GM");
                return e != null ? err(e) : "Régénération de la zone de raid lancée.";
            }
            case "gm.raid.hub": {
                var s = fr.serveur.rpg.raid.RaidState.get(server);
                s.hubDim = p.getWorld().getRegistryKey().getValue().toString();
                s.hub = p.getBlockPos();
                s.hubYaw = p.getYaw();
                s.markDirty();
                return "Point de retour du raid défini ici.";
            }
            case "gm.raid.portal": {
                var s = fr.serveur.rpg.raid.RaidState.get(server);
                s.portal = p.getBlockPos();
                if (s.hub == null) {
                    s.hubDim = p.getWorld().getRegistryKey().getValue().toString();
                    s.hub = p.getBlockPos();
                    s.hubYaw = p.getYaw();
                }
                s.markDirty();
                return "Portail du raid défini ici.";
            }
            case "gm.raid.rooms":
                return fr.serveur.rpg.raid.RoomLibrary.load();
            case "gm.room.here":
                state.waitingRoomDim = p.getWorld().getRegistryKey().getValue().toString();
                state.waitingRoom = p.getBlockPos();
                state.markDirty();
                return "Salle d'attente définie ici.";
            case "gm.zone.protect": {
                int r = Math.max(3, Math.min(200, n.getInt("radius")));
                BlockPos c = p.getBlockPos();
                String name = state.zones.containsKey("mairie") ? "protegee-" + (state.zones.size() + 1) : "mairie";
                Zone z = Zone.protectedArea(name, p.getWorld().getRegistryKey().getValue().toString(),
                        c.add(-r, -6, -r), c.add(r, r + 10, r), 0);
                state.zones.put(name, z);
                state.markDirty();
                return "Zone protégée « " + name + " » créée (rayon " + r + ").";
            }
            case "gm.zone.delete":
                if (state.zones.remove(n.getString("name")) == null) return "!Zone introuvable.";
                state.markDirty();
                return "Zone supprimée.";
            case "gm.event.start": {
                String type = n.getString("type");
                if ("random".equals(type)) GmEvents.startRandom(server);
                else {
                    GmEvents.Type t = GmEvents.Type.parse(type);
                    if (t == null) return "!Type d'événement inconnu.";
                    String title = switch (t) {
                        case COMBAT -> "Chasse nocturne";
                        case RECOLTE -> "Grande récolte";
                        case RESSOURCE -> "Ruée vers la Pierre obscure";
                        case SPECIAL -> "Événement spécial";
                    };
                    GmEvents.start(server, t, Math.max(1, cfg.evenementDureeMinutes), title);
                }
                return "Événement lancé.";
            }
            case "gm.event.stop":
                if (!GmEvents.running()) return "!Aucun événement en cours.";
                GmEvents.stop(server);
                return "Événement terminé, podium récompensé.";
            case "gm.shop.add": {
                ItemStack held = p.getMainHandStack();
                long price = n.getLong("price");
                if (held.isEmpty()) return "!Tiens en main l'objet à mettre en boutique.";
                if (price <= 0) return "!Indique un prix.";
                MarketItem m = fr.serveur.rpg.economy.DynamicShop.create(state, held, price, true, false);
                state.shop.add(m);
                state.markDirty();
                return "Ajouté à la boutique : " + m.stack.getCount() + "x " + m.stack.getName().getString() + " (prix de base " + price + ").";
            }
            case "gm.shop.price": {
                int id = n.getInt("id");
                for (MarketItem m : state.shop) {
                    if (m.id == id) {
                        // Le GM règle le prix de base ; les bornes suivent (moitié / double).
                        m.basePrice = Math.max(1, n.contains("value") ? n.getLong("value") : m.basePrice + n.getLong("delta"));
                        m.minPrice = Math.max(1, m.basePrice / 2);
                        m.maxPrice = Math.max(2, m.basePrice * 2);
                        fr.serveur.rpg.economy.DynamicShop.refresh(m);
                        state.markDirty();
                        return "Prix de base : " + Bank.format(m.basePrice) + " (actuel " + Bank.format(m.price) + ").";
                    }
                }
                return "!Article introuvable.";
            }
            case "gm.shop.toggle": {
                int id = n.getInt("id");
                for (MarketItem m : state.shop) {
                    if (m.id != id) continue;
                    if ("buyback".equals(n.getString("what"))) m.buyback = !m.buyback;
                    else m.sold = !m.sold;
                    if (!m.sold && !m.buyback) m.sold = true;
                    state.markDirty();
                    return (m.sold ? "Vendu" : "Pas vendu") + " · " + (m.buyback ? "racheté" : "pas racheté") + " par le serveur.";
                }
                return "!Article introuvable.";
            }
            case "gm.shop.remove": {
                int id = n.getInt("id");
                boolean removed = state.shop.removeIf(m -> m.id == id);
                state.markDirty();
                return removed ? "Article retiré." : "!Article introuvable.";
            }
            case "gm.hdv.remove": {
                int id = n.getInt("id");
                MarketItem m = state.auctions.stream().filter(x -> x.id == id).findFirst().orElse(null);
                if (m == null) return "!Annonce introuvable.";
                state.auctions.remove(m);
                state.markDirty();
                if (!p.getInventory().insertStack(m.stack.copy())) p.dropItem(m.stack.copy(), false);
                Bank.notify(server, m.seller, "Le GM a retiré ton annonce : " + m.stack.getName().getString() + ".");
                return "Annonce retirée (objet dans ton inventaire).";
            }
            default:
                return "!Action GM inconnue.";
        }
    }

    private static String gmPlayer(ServerPlayerEntity gm, RpgState state, NbtCompound n) {
        Map.Entry<UUID, PlayerData> e = state.findByName(n.getString("name"));
        if (e == null) return "!Joueur inconnu.";
        PlayerData data = e.getValue();
        ServerPlayerEntity target = gm.getServer().getPlayerManager().getPlayer(e.getKey());
        String op = n.getString("op");
        long v = n.getLong("value");
        switch (op) {
            case "xp" -> {
                if (target == null) return "!Le joueur doit être connecté.";
                if (data.job == null) return "!Ce joueur n'a pas de métier.";
                if (v <= 0) return "!Indique une quantité d'XP.";
                Progression.addXp(target, (int) Math.min(v, Integer.MAX_VALUE), false);
            }
            case "level" -> {
                if (data.current() == null) return "!Ce joueur n'a pas de métier.";
                data.current().level = (int) Math.max(1, Math.min(Job.MAX_LEVEL, data.current().level + v));
                data.current().xp = 0;
            }
            case "setlevel" -> {
                if (data.current() == null) return "!Ce joueur n'a pas de métier.";
                if (v < 1 || v > Job.MAX_LEVEL) return "!Le niveau va de 1 à " + Job.MAX_LEVEL + ".";
                data.current().level = (int) v;
                data.current().xp = 0;
            }
            case "money" -> fr.serveur.rpg.economy.Economy.admin(gm.getServer(), e.getKey(), v, false, gm.getGameProfile().getName());
            case "setmoney" -> fr.serveur.rpg.economy.Economy.admin(gm.getServer(), e.getKey(), v, true, gm.getGameProfile().getName());
            case "freeze" -> {
                data.frozen = !data.frozen;
                if (target != null) Msg.send(target, Text.literal(data.frozen ? "Ton compte a été gelé par un GM." : "Ton compte est dégelé.")
                        .formatted(data.frozen ? Formatting.RED : Formatting.GREEN));
            }
            case "job" -> {
                Job job = Job.parse(n.getString("job"));
                if (job == null) return "!Métier inconnu.";
                data.job = job;
                data.current();
                if (target != null) JobService.giveStarterTool(target, job);
            }
            case "side" -> {
                String s = n.getString("job");
                SideJob job = s.isEmpty() ? null : SideJob.parse(s);
                if (!s.isEmpty() && job == null) return "!Métier inconnu.";
                SideJobs.apply(target, data, job);
                if (job != null) data.sideChosenOnce = true;
            }
            case "give" -> {
                if (target == null) return "!Le joueur doit être connecté.";
                int count = (int) Math.max(1, Math.min(64, n.contains("count") ? n.getLong("count") : 1));
                ItemStack stack = switch (n.getString("item")) {
                    case "larme" -> DeathSystem.createTear(count);
                    case "crochet" -> Lockpick.create(count);
                    case "pioche" -> ObscureStone.createPickaxe();
                    default -> ItemStack.EMPTY;
                };
                if (stack.isEmpty()) return "!Objet inconnu.";
                if (!target.getInventory().insertStack(stack)) target.dropItem(stack, false);
            }
            case "revive" -> {
                if (target == null) return "!Le joueur doit être connecté.";
                DeathSystem.revive(target, "Le GM te ramène à la vie.");
            }
            default -> { return "!Action inconnue."; }
        }
        state.markDirty();
        return "Fait pour " + data.name + ".";
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
