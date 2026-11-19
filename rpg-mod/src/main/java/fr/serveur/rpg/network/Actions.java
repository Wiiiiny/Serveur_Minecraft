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
import fr.serveur.rpg.protection.Lockpick;
import fr.serveur.rpg.world.DayCycle;
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
                double d = n.getDouble("delta");
                switch (key) {
                    case "dayMin" -> state.dayMinutes = clamp(state.dayMinutes + d, 1, 120);
                    case "nightMin" -> state.nightMinutes = clamp(state.nightMinutes + d, 1, 120);
                    case "lockpick" -> state.lockpickChance = (int) clamp(state.lockpickChance + d, 0, 100);
                    case "prestigeBonus" -> state.prestigeBonusPercent = (int) clamp(state.prestigeBonusPercent + d, 0, 200);
                    case "jobPrice" -> cfg.prixChangementMetier = (int) clamp(cfg.prixChangementMetier + d, 0, 100000);
                    case "dailyCount" -> cfg.quetesParJour = (int) clamp(cfg.quetesParJour + d, 1, 6);
                    case "rewardMult" -> cfg.multiplicateurRecompenses = Math.round(clamp(cfg.multiplicateurRecompenses + d, 0.1, 10) * 10) / 10.0;
                    case "autoEvents" -> cfg.evenementsAuto = !cfg.evenementsAuto;
                    case "eventInterval" -> cfg.evenementIntervalleMinutes = (int) clamp(cfg.evenementIntervalleMinutes + d, 10, 1440);
                    case "eventDuration" -> cfg.evenementDureeMinutes = (int) clamp(cfg.evenementDureeMinutes + d, 1, 180);
                    case "eventMinPlayers" -> cfg.evenementJoueursMin = (int) clamp(cfg.evenementJoueursMin + d, 1, 50);
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
                ServerWorld ow = server.getOverworld();
                long target = "night".equals(n.getString("to")) ? DayCycle.NIGHT_START : DayCycle.NIGHT_END;
                long day = ow.getTimeOfDay() - Math.floorMod(ow.getTimeOfDay(), 24000L);
                long t = day + target;
                if (t <= ow.getTimeOfDay()) t += 24000;
                ow.setTimeOfDay(t);
                return "night".equals(n.getString("to")) ? "La nuit tombe." : "Le jour se lève.";
            }
            case "gm.room.here":
                state.waitingRoomDim = p.getWorld().getRegistryKey().getValue().toString();
                state.waitingRoom = p.getBlockPos();
                state.markDirty();
                return "Salle d'attente définie ici.";
            case "gm.zone.protect": {
                int r = Math.max(3, Math.min(100, n.getInt("radius")));
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
                MarketItem m = new MarketItem();
                m.id = state.nextMarketId++;
                m.sellerName = "GM";
                m.stack = held.copy();
                m.price = price;
                state.shop.add(m);
                state.markDirty();
                return "Ajouté à la boutique : " + m.stack.getCount() + "x " + m.stack.getName().getString() + ".";
            }
            case "gm.shop.price": {
                int id = n.getInt("id");
                for (MarketItem m : state.shop) {
                    if (m.id == id) {
                        m.price = Math.max(1, m.price + n.getLong("delta"));
                        state.markDirty();
                        return "Prix : " + Bank.format(m.price) + ".";
                    }
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
        int v = n.getInt("value");
        switch (op) {
            case "xp" -> {
                if (target == null) return "!Le joueur doit être connecté.";
                if (data.job == null) return "!Ce joueur n'a pas de métier.";
                Progression.addXp(target, v, false);
            }
            case "level" -> {
                if (data.current() == null) return "!Ce joueur n'a pas de métier.";
                data.current().level = Math.max(1, Math.min(Job.MAX_LEVEL, data.current().level + v));
                data.current().xp = 0;
            }
            case "money" -> data.money = Math.max(0, data.money + v);
            case "job" -> {
                Job job = Job.parse(n.getString("job"));
                if (job == null) return "!Métier inconnu.";
                data.job = job;
                data.current();
                if (target != null) JobService.giveStarterTool(target, job);
            }
            case "give" -> {
                if (target == null) return "!Le joueur doit être connecté.";
                ItemStack stack = switch (n.getString("item")) {
                    case "larme" -> DeathSystem.createTear(1);
                    case "crochet" -> Lockpick.create(1);
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
