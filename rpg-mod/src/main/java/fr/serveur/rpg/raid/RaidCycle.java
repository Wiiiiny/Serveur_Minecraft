package fr.serveur.rpg.raid;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.SurvieRpg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.economy.Economy;
import fr.serveur.rpg.mining.HarvestRules;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.ChestBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Cycle de vie du raid (monde partagé) : génération, ouverture, annonce 5 min avant la fin, évacuation (le butin non
 * extrait est perdu), régénération toutes les 3 h. Entrée payante depuis le portail du hub. Règles du monde du raid :
 * murs indestructibles, pas de construction, coffres de corps, pièges, socle d'extraction.
 */
public final class RaidCycle {
    private static final ServerBossBar TIMER = new ServerBossBar(Text.literal("Zone de raid"), BossBar.Color.PURPLE, BossBar.Style.PROGRESS);
    /** Dernière entrée de chaque joueur (point d'arrivée, instant) : le groupe arrive au même endroit. */
    private static final Map<UUID, long[]> LAST_ENTRY = new HashMap<>();
    /** Reconnexion dans un raid régénéré : sortie au tick indiqué (laisser le client finir de charger le monde). */
    private static final Map<UUID, Integer> EVACUATE_AT = new HashMap<>();
    private static long ticks, retryAt;
    private static boolean announced;
    private static Integer forcedThreat;

    private RaidCycle() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            RaidConfig.load();
            ensureRooms();
        });
        ServerTickEvents.END_SERVER_TICK.register(RaidCycle::tick);

        // Murs indestructibles, pas de construction (sauf GM en mode créatif ou /gm bypass).
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (world.getRegistryKey() != RaidWorld.KEY || !(player instanceof ServerPlayerEntity sp) || HarvestRules.isExempt(sp)) return true;
            sp.sendMessage(Text.literal("Les murs de la zone de raid sont indestructibles.").formatted(Formatting.RED), true);
            return false;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || world.getRegistryKey() != RaidWorld.KEY || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            if (Extraction.useKey(sp, sp.getStackInHand(hand), pos)) return ActionResult.SUCCESS;
            if (world.getBlockState(pos).getBlock() instanceof ChestBlock) {
                if (RaidDeath.open(sp, pos)) return ActionResult.SUCCESS;
                trapCheck(sp, pos);
            }
            return ActionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (RaidWorld.isIn(entity)) RaidMobs.onDeath(entity, source);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p) ReturnBeacon.onDamage(p);
            return true;
        });
        // Reconnexion dans un raid déjà régénéré : on ressort, sans le butin.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity p = handler.player;
            if (!RaidWorld.isIn(p)) return;
            PlayerData d = RpgState.get(server).player(p);
            RaidState s = RaidState.get(server);
            if (s.status != RaidState.Status.OUVERT || d.raidCycle != s.cycle) EVACUATE_AT.put(p.getUuid(), server.getTicks() + 40);
        }));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (EVACUATE_AT.isEmpty()) return;
            EVACUATE_AT.entrySet().removeIf(e -> {
                if (server.getTicks() < e.getValue()) return false;
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null && RaidWorld.isIn(p)) evacuate(p, "La zone a été régénérée pendant ton absence : le butin du raid est perdu.");
                return true;
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TIMER.removePlayer(handler.player));
    }

    /** Écrit les salles d'exemple si le dossier est vide, puis lit la bibliothèque. */
    public static String ensureRooms() {
        RoomLibrary.load();
        if (RoomLibrary.isEmpty()) {
            try {
                int n = ExampleRooms.write();
                SurvieRpg.LOGGER.info("[raid] aucune salle : {} salles d'exemple écrites dans {}", n, RaidConfig.roomsDir());
            } catch (Exception e) {
                SurvieRpg.LOGGER.error("[raid] impossible d'écrire les salles d'exemple", e);
            }
        }
        return RoomLibrary.load();
    }

    // ------------------------------------------------------------------ cycle

    private static void tick(MinecraftServer server) {
        ticks++;
        ServerWorld w = RaidWorld.get(server);
        if (w == null) return;
        RaidBuilder.tick(w);
        ReturnBeacon.tick(server);
        if (ticks % 20 != 0) return;
        long now = System.currentTimeMillis();
        RaidState s = RaidState.get(server);
        fr.serveur.rpg.raid.statue.Fear.tick(w, now);
        fr.serveur.rpg.raid.thief.Thieves.tickCompanions(server);
        RaidDeath.tick(w, s, now);
        if (s.status == RaidState.Status.OUVERT) {
            Extraction.tick(w, s, now);
            RaidMobs.tick(w, s, now);
            long left = s.nextReset - now;
            if (!announced && left <= RaidConfig.get().annonceMinutes * 60_000L) {
                announced = true;
                Msg.broadcast(server, Text.literal("La zone de raid se régénère dans " + RaidConfig.get().annonceMinutes
                        + " minutes : sortez avec une balise ou par l'extraction, sinon le butin sera perdu !").formatted(Formatting.DARK_RED, Formatting.BOLD));
            }
            if (left <= 0) startCycle(server, "fin du cycle");
            updateTimer(w, s, left);
        } else if (s.status == RaidState.Status.FERME && !RaidBuilder.running() && now >= retryAt) {
            startCycle(server, "démarrage");
        }
    }

    private static void updateTimer(ServerWorld w, RaidState s, long left) {
        long sec = Math.max(0, left / 1000);
        TIMER.setName(Text.literal("Zone de raid — " + s.threat().nom + " — régénération dans " + Msg.duration(sec))
                .formatted(Formatting.LIGHT_PURPLE));
        double total = RaidConfig.get().cycleHeures * 3_600_000.0;
        TIMER.setPercent((float) Math.max(0, Math.min(1, left / total)));
        for (ServerPlayerEntity p : new ArrayList<>(TIMER.getPlayers())) if (!RaidWorld.isIn(p)) TIMER.removePlayer(p);
        for (ServerPlayerEntity p : w.getPlayers()) TIMER.addPlayer(p);
    }

    /** Évacue la zone, choisit le niveau de menace, génère un nouveau plan et le pose. */
    public static String startCycle(MinecraftServer server, String reason) {
        ServerWorld w = RaidWorld.get(server);
        if (w == null) return "Le monde du raid n'existe pas (dimension survierpg:raid).";
        if (RaidBuilder.running()) return "Une génération est déjà en cours (" + RaidBuilder.progress() + " %).";
        RaidState s = RaidState.get(server);
        for (ServerPlayerEntity p : new ArrayList<>(w.getPlayers())) evacuate(p, "La zone de raid se régénère : le butin non extrait est perdu.");
        List<Entity> toRemove = new ArrayList<>();
        for (Entity e : w.iterateEntities()) if (!(e instanceof PlayerEntity)) toRemove.add(e);
        toRemove.forEach(Entity::discard);
        s.corpses.clear();
        s.clearLayout();
        RoomLibrary.load();
        Random random = new Random();
        var levels = RaidConfig.get().niveaux;
        if (forcedThreat != null) {
            s.threat = Math.max(0, Math.min(forcedThreat, levels.size() - 1));
            forcedThreat = null;
        } else {
            int total = levels.stream().mapToInt(t -> Math.max(0, t.poids)).sum(), pick = random.nextInt(Math.max(1, total));
            s.threat = 0;
            for (int i = 0; i < levels.size(); i++) {
                pick -= Math.max(0, levels.get(i).poids);
                if (pick < 0) {
                    s.threat = i;
                    break;
                }
            }
        }
        Layout layout;
        try {
            layout = Layout.generate(random);
        } catch (Exception e) {
            s.status = RaidState.Status.FERME;
            retryAt = System.currentTimeMillis() + 5 * 60_000L;
            s.markDirty();
            Economy.alert(server, "Raid : génération impossible — " + e.getMessage());
            return e.getMessage();
        }
        s.status = RaidState.Status.GENERATION;
        s.markDirty();
        announced = false;
        SurvieRpg.LOGGER.info("[raid] nouveau cycle ({}) : {} salles, niveau {}", reason, layout.placements.size(), s.threat().nom);
        RaidBuilder.start(w, layout, s, () -> finish(server, w, s));
        return null;
    }

    private static void finish(MinecraftServer server, ServerWorld w, RaidState s) {
        for (RaidState.Chest c : s.chests) Loot.fill(w, c.pos(), c.tier(), w.random);
        RaidMobs.populate(w, s);
        long now = System.currentTimeMillis();
        s.cycle++;
        s.cycleStart = now;
        s.nextReset = now + (long) (RaidConfig.get().cycleHeures * 3_600_000L);
        s.status = RaidState.Status.OUVERT;
        s.markDirty();
        Msg.broadcast(server, Text.literal("La zone de raid est ouverte — niveau ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal(s.threat().nom).formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(" (entrée : " + Bank.format(entryFee(server)) + ").").formatted(Formatting.LIGHT_PURPLE)));
        SurvieRpg.LOGGER.info("[raid] zone ouverte : {} coffres, {} arrivées, {} marqueurs", s.chests.size(), s.spawns.size(), s.spots.size());
    }

    /** Fait sortir un joueur sans extraction : le butin du raid est perdu. */
    public static void evacuate(ServerPlayerEntity p, String message) {
        var inv = p.getInventory();
        int lost = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (RaidWorld.isLoot(st)) {
                lost += st.getCount();
                inv.setStack(i, ItemStack.EMPTY);
            }
        }
        ReturnBeacon.cancel(p);
        RaidWorld.sendToHub(p);
        Msg.send(p, Text.literal(message + (lost > 0 ? " (" + lost + " objet(s) perdus)" : "")).formatted(Formatting.RED));
    }

    public static void forceThreat(int index) {
        forcedThreat = index;
    }

    // ------------------------------------------------------------------ entrée

    public static long entryFee(MinecraftServer server) {
        return Math.round(RaidState.get(server).threat().fraisEntree * RpgState.get(server).econTaxMult);
    }

    /** @return null si le joueur est entré, sinon la raison du refus. */
    public static String enter(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        RaidState s = RaidState.get(server);
        ServerWorld w = RaidWorld.get(server);
        if (w == null) return "Le monde du raid n'existe pas.";
        if (RaidWorld.isIn(p)) return "Tu es déjà dans la zone de raid.";
        if (s.status != RaidState.Status.OUVERT) return s.status == RaidState.Status.GENERATION
                ? "La zone se régénère (" + RaidBuilder.progress() + " %), patiente un peu." : "La zone de raid est fermée.";
        if (s.spawns.isEmpty()) return "Aucun point d'arrivée dans cette génération.";
        if (!p.hasPermissionLevel(2)) {
            if (s.portal == null || s.hubDim == null) return "Le GM n'a pas encore placé le portail du raid.";
            boolean near = p.getWorld().getRegistryKey().getValue().toString().equals(s.hubDim)
                    && p.getBlockPos().getSquaredDistance(s.portal) <= 36;
            if (!near) return "Va au portail du raid, au hub, pour entrer.";
        }
        long fee = entryFee(server);
        String e = Economy.spend(server, p.getUuid(), Economy.Source.FRAIS_RAID, fee, s.threat().nom);
        if (e != null) return e;
        // Le groupe arrive au même endroit qu'un membre entré dans les 2 dernières minutes.
        long now = System.currentTimeMillis();
        int spawn = new Random().nextInt(s.spawns.size());
        for (UUID m : Party.members(p.getUuid())) {
            long[] last = LAST_ENTRY.get(m);
            if (last != null && now - last[1] < 120_000 && last[0] < s.spawns.size()) spawn = (int) last[0];
        }
        LAST_ENTRY.put(p.getUuid(), new long[]{spawn, now});
        RpgState.get(server).player(p).raidCycle = s.cycle;
        RpgState.get(server).markDirty();
        BlockPos at = s.spawns.get(spawn);
        p.teleport(w, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYaw(), 0);
        Msg.send(p, Text.literal("Zone de raid — niveau " + s.threat().nom + ". Sors avec une balise de retour, ou par l'extraction finale "
                + "(2 clés de gardien). Sinon, ton butin sera perdu à la régénération.").formatted(Formatting.LIGHT_PURPLE));
        return null;
    }

    // ------------------------------------------------------------------ pièges

    private static void trapCheck(ServerPlayerEntity p, BlockPos pos) {
        RaidState s = RaidState.get(p.getServer());
        for (int i = 0; i < s.chests.size(); i++) {
            RaidState.Chest c = s.chests.get(i);
            if (!c.pos().equals(pos) || !c.trap()) continue;
            s.chests.set(i, new RaidState.Chest(c.pos(), c.tier(), false));
            s.markDirty();
            RaidMobs.trap(p.getServerWorld(), p, pos);
            return;
        }
    }
}
