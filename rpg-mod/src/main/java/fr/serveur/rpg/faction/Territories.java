package fr.serveur.rpg.faction;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.mining.HarvestRules;
import fr.serveur.rpg.world.PlayableZone;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BannerBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Territoires de faction : cercles revendiqués avec un Étendard (rayon 20 + 5 par niveau), à 50 blocs au moins des autres
 * factions. Le premier devient la base principale (Cœur de faction, réapparition, téléportation, coffre commun).
 * Protection : seuls les membres construisent, cassent, ouvrent coffres, portes et mécanismes ; pas de PvP en territoire
 * hors guerre. Pendant une guerre, aux heures d'attaque (jour, ou nuit acceptée par les deux chefs), les ennemis peuvent
 * tout faire et frapper le Cœur. Entrer et sortir d'un territoire s'affiche ; ses limites brillent quand on s'en approche.
 */
public final class Territories {
    public record Owner(Faction faction, Faction.Territory territory) {}

    private static final Map<UUID, Integer> LAST_TERRITORY = new HashMap<>();
    private static final Map<UUID, Long> LAST_WARN = new HashMap<>();
    private static final Map<UUID, Integer> LAST_HEART_HIT = new HashMap<>();
    /** Téléportation vers la base en cours : fin de la canalisation et position de départ. */
    private static final Map<UUID, Object[]> TELEPORTS = new HashMap<>();

    private Territories() {}

    // ------------------------------------------------------------------ recherche

    /** Territoire qui couvre cette position (le plus ancien s'ils se chevauchent), ou null. */
    public static Owner ownerAt(MinecraftServer server, World world, BlockPos pos) {
        String dim = world.getRegistryKey().getValue().toString();
        Owner best = null;
        for (Faction f : FactionState.get(server).factions.values()) {
            int r = f.radius();
            long r2 = (long) r * r;
            for (Faction.Territory t : f.territories) {
                if (!t.dim.equals(dim)) continue;
                long dx = pos.getX() - t.center.getX(), dz = pos.getZ() - t.center.getZ();
                if (dx * dx + dz * dz > r2) continue;
                if (best == null || t.created < best.territory.created) best = new Owner(f, t);
            }
        }
        return best;
    }

    /** Le joueur peut-il construire, casser et ouvrir ici ? */
    public static boolean canBuild(ServerPlayerEntity p, Owner o) {
        if (o == null || HarvestRules.isExempt(p)) return true;
        if (o.faction.members.containsKey(p.getUuid())) return true;
        Faction mine = Factions.of(p);
        return mine != null && Wars.canAttack(p.getServer(), mine, o.faction);
    }

    /** Explosions : rien ne se détruit dans un territoire, sauf s'il est attaquable en ce moment (guerre, heures d'attaque). */
    public static boolean shielded(World world, BlockPos pos) {
        MinecraftServer server = world.getServer();
        if (server == null) return false;
        Owner o = ownerAt(server, world, pos);
        if (o == null) return false;
        FactionState.War w = FactionState.get(server).warOf(o.faction.id);
        return w == null || !Wars.attackOpen(server, w);
    }

    private static void warn(ServerPlayerEntity p, Owner o, String what) {
        p.sendMessage(Text.literal("⚑ Territoire de « " + o.faction.name + " » : " + what).formatted(Formatting.RED), true);
        long now = System.currentTimeMillis();
        Long last = LAST_WARN.get(p.getUuid());
        if (last != null && now - last < 5000) return;
        LAST_WARN.put(p.getUuid(), now);
        Faction mine = Factions.of(p);
        if (mine != null && mine.id != o.faction.id) {
            Wars.incident(p.getServer(), o.faction, mine, p.getGameProfile().getName() + " a tenté de " + what.replace("impossible de ", ""));
        }
    }

    // ------------------------------------------------------------------ événements

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity p)) return true;
            if (state.isOf(FactionItems.COEUR)) return false;
            Owner o = ownerAt(p.getServer(), world, pos);
            if (canBuild(p, o)) return true;
            warn(p, o, "impossible de casser ici");
            return false;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity p)) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            BlockState state = world.getBlockState(pos);
            if (state.isOf(FactionItems.COEUR)) {
                Faction f = heartOwner(p.getServer(), world, pos);
                if (f != null && f.members.containsKey(p.getUuid())) openChest(p, f);
                else if (f != null) p.sendMessage(Text.literal("Cœur de la faction « " + f.name + " ».").formatted(Formatting.GRAY), true);
                return ActionResult.SUCCESS;
            }
            Owner o = ownerAt(p.getServer(), world, pos);
            Owner o2 = ownerAt(p.getServer(), world, pos.offset(hit.getSide()));
            if (canBuild(p, o) && canBuild(p, o2)) return ActionResult.PASS;
            warn(p, o != null ? o : o2, "impossible d'interagir ici");
            return ActionResult.FAIL;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack held = player.getStackInHand(hand);
            if (world.isClient || !(player instanceof ServerPlayerEntity p)) return TypedActionResult.pass(held);
            if (!(held.getItem() instanceof BucketItem || held.getItem() instanceof FlintAndSteelItem || held.getItem() instanceof FireChargeItem)) {
                return TypedActionResult.pass(held);
            }
            Owner o = ownerAt(p.getServer(), world, p.getBlockPos());
            if (canBuild(p, o)) return TypedActionResult.pass(held);
            warn(p, o, "impossible d'utiliser ça ici");
            return TypedActionResult.fail(held);
        });

        // Cadres, supports d'armure, animaux, véhicules : protégés comme les blocs (les monstres restent attaquables).
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity p) || target instanceof PlayerEntity || target instanceof Monster) return ActionResult.PASS;
            Owner o = ownerAt(p.getServer(), world, target.getBlockPos());
            if (canBuild(p, o)) return ActionResult.PASS;
            warn(p, o, "impossible de toucher à ça ici");
            return ActionResult.FAIL;
        });
        UseEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity p) || target instanceof PlayerEntity || target instanceof Monster) return ActionResult.PASS;
            Owner o = ownerAt(p.getServer(), world, target.getBlockPos());
            if (canBuild(p, o)) return ActionResult.PASS;
            warn(p, o, "impossible d'interagir ici");
            return ActionResult.FAIL;
        });

        // PvP : interdit en territoire hors guerre ; ailleurs, frapper un membre d'une autre faction est un incident.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity victim) || !(source.getAttacker() instanceof ServerPlayerEntity attacker) || attacker == victim) return true;
            MinecraftServer server = victim.getServer();
            Faction fa = Factions.of(attacker), fv = Factions.of(victim);
            boolean atWar = fa != null && fv != null && Wars.canAttack(server, fa, fv);
            Owner ov = ownerAt(server, victim.getWorld(), victim.getBlockPos()), oa = ownerAt(server, attacker.getWorld(), attacker.getBlockPos());
            if ((ov != null || oa != null) && !atWar) {
                attacker.sendMessage(Text.literal("⚑ Pas de combat dans un territoire de faction hors guerre.").formatted(Formatting.RED), true);
                return false;
            }
            if (!atWar && fa != null && fv != null && fa.id != fv.id) {
                Wars.incident(server, fv, fa, attacker.getGameProfile().getName() + " a attaqué " + victim.getGameProfile().getName());
            }
            return true;
        });

        // Cœur de faction : frappé à la main pendant une attaque permise.
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity p) || !world.getBlockState(pos).isOf(FactionItems.COEUR)) return ActionResult.PASS;
            Faction f = heartOwner(p.getServer(), world, pos);
            if (f == null) return ActionResult.PASS;
            Wars.hitHeart(p, f);
            return ActionResult.FAIL;
        });

        // Réapparition sans lit : à la base de sa faction.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, p, alive) -> {
            if (alive || p.getSpawnPointPosition() != null || fr.serveur.rpg.raid.RaidWorld.isIn(p)) return;
            if (RpgState.get(p.getServer()).player(p).awaitingRevive) return;
            Faction f = Factions.of(p);
            ServerWorld w = f == null || f.heart == null ? null : world(p.getServer(), f.heartDim);
            if (w == null) return;
            Vec3d spot = safeSpot(w, f.heart);
            p.teleport(w, spot.x, spot.y, spot.z, p.getYaw(), p.getPitch());
            Msg.send(p, Text.literal("Tu reviens à la base de ta faction.").formatted(Formatting.DARK_AQUA));
        });

        ServerTickEvents.END_SERVER_TICK.register(Territories::tick);
    }

    /** Un endroit sûr près du Cœur : sol solide et deux blocs libres (sinon, sur le Cœur lui-même). */
    static Vec3d safeSpot(ServerWorld w, BlockPos heart) {
        for (int r = 1; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 2; dy >= -3; dy--) {
                        BlockPos feet = heart.add(dx, dy, dz);
                        if (w.getBlockState(feet.down()).isSideSolidFullSquare(w, feet.down(), net.minecraft.util.math.Direction.UP)
                                && w.getBlockState(feet).getCollisionShape(w, feet).isEmpty() && w.getBlockState(feet.up()).getCollisionShape(w, feet.up()).isEmpty()
                                && w.getFluidState(feet).isEmpty()) {
                            return new Vec3d(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                        }
                    }
                }
            }
        }
        return new Vec3d(heart.getX() + 0.5, heart.getY() + 1, heart.getZ() + 0.5);
    }

    static ServerWorld world(MinecraftServer server, String dim) {
        Identifier id = Identifier.tryParse(dim);
        return id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    }

    static Faction heartOwner(MinecraftServer server, World world, BlockPos pos) {
        String dim = world.getRegistryKey().getValue().toString();
        for (Faction f : FactionState.get(server).factions.values()) if (pos.equals(f.heart) && dim.equals(f.heartDim)) return f;
        return null;
    }

    // ------------------------------------------------------------------ revendication, base, abandon

    /** Étendard de revendication utilisé : nouveau territoire centré sur le joueur. @return null si c'est fait, sinon la raison. */
    public static String claim(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        FactionState s = FactionState.get(server);
        FactionConfig.Data c = FactionConfig.get();
        Faction f = s.of(p.getUuid());
        if (f == null) return "Il faut être dans une faction (menu B › Faction).";
        if (!Factions.isLeader(f, p.getUuid())) return "Seuls le chef et les officiers revendiquent un territoire.";
        World w = p.getWorld();
        if (w.getRegistryKey() == fr.serveur.rpg.raid.RaidWorld.KEY) return "Pas de territoire dans la zone de raid.";
        boolean first = f.base() == null;
        // Base : le Cœur est posé 2 blocs devant le joueur et devient le centre du territoire.
        BlockPos center = first ? p.getBlockPos().offset(p.getHorizontalFacing(), 2) : p.getBlockPos();
        RpgState rs = RpgState.get(server);
        if (rs.nearProtected(w, center, f.radius())) return "Trop près d'une zone protégée (mairie).";
        PlayableZone.Box zone = PlayableZone.box(rs);
        if (zone != null && (zone.dim() != w.getRegistryKey() || !zone.contains(center.getX(), center.getZ()))) return "En dehors de la zone jouable.";
        String dim = w.getRegistryKey().getValue().toString();
        for (Faction o : s.factions.values()) {
            for (Faction.Territory t : o.territories) {
                if (!t.dim.equals(dim)) continue;
                double d = Math.sqrt(t.center.getSquaredDistance(center));
                if (o.id != f.id && d < f.radius() + o.radius() + c.distanceMinEntreFactions) {
                    return "Trop près du territoire de « " + o.name + " » (" + c.distanceMinEntreFactions + " blocs au moins entre deux factions).";
                }
                if (o.id == f.id && d < 8) return "Ta faction a déjà un territoire centré ici.";
            }
        }
        if (first && (!(w.getBlockState(center).isAir() || w.getBlockState(center).isReplaceable())
                || !(w.getBlockState(center.up()).isAir() || w.getBlockState(center.up()).isReplaceable()))) {
            return "Il faut 2 blocs libres devant toi pour poser le Cœur de faction.";
        }
        Faction.Territory t = new Faction.Territory();
        t.id = s.nextTerritoryId++;
        t.dim = dim;
        t.center = center.toImmutable();
        t.created = System.currentTimeMillis();
        f.territories.add(t);
        if (first) setBase(server, f, t, (ServerWorld) w, center);
        f.log(p.getGameProfile().getName() + " revendique un territoire en " + center.getX() + " / " + center.getZ() + (first ? " (base principale)." : "."));
        Factions.journal(server, f, "territoire", "pos", center.toShortString(), "dim", dim, "base", first, "par", p.getGameProfile().getName());
        s.markDirty();
        Factions.tell(server, f, Text.literal("Nouveau territoire en " + center.getX() + " / " + center.getZ() + " (rayon " + f.radius() + ")"
                + (first ? " : c'est votre base principale, le Cœur de faction y est posé." : ".")).formatted(Formatting.GREEN));
        ((ServerWorld) w).spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5, 60, 0.6, 1, 0.6, 0.3);
        return null;
    }

    /** La base principale passe dans ce territoire ; le Cœur est posé à {@code at}. */
    public static void setBase(MinecraftServer server, Faction f, Faction.Territory t, ServerWorld w, BlockPos at) {
        removeHeart(server, f);
        f.baseTerritory = t.id;
        f.heart = at.toImmutable();
        f.heartDim = w.getRegistryKey().getValue().toString();
        f.heartHp = FactionConfig.get().coeurPointsDeVie;
        w.setBlockState(at, FactionItems.COEUR.getDefaultState(), Block.NOTIFY_ALL);
        placeBannerOnHeart(server, f);
    }

    /** Le chef déplace la base dans le territoire où il se trouve (hors guerre). */
    public static String moveBase(ServerPlayerEntity p) {
        Faction f = Factions.of(p);
        if (f == null || !Factions.isChief(f, p.getUuid())) return "Seul le chef déplace la base.";
        if (FactionState.get(p.getServer()).warOf(f.id) != null) return "Pas pendant une guerre.";
        Owner o = ownerAt(p.getServer(), p.getWorld(), p.getBlockPos());
        if (o == null || o.faction.id != f.id) return "Place-toi dans un territoire de ta faction.";
        BlockPos at = p.getBlockPos().offset(p.getHorizontalFacing(), 2);
        if (!(p.getWorld().getBlockState(at).isAir() || p.getWorld().getBlockState(at).isReplaceable())) return "Il faut un bloc libre 2 blocs devant toi.";
        setBase(p.getServer(), f, o.territory, p.getServerWorld(), at);
        f.log(p.getGameProfile().getName() + " déplace la base en " + at.getX() + " / " + at.getZ() + ".");
        FactionState.get(p.getServer()).markDirty();
        return null;
    }

    public static void removeHeart(MinecraftServer server, Faction f) {
        if (f.heart == null) return;
        ServerWorld w = world(server, f.heartDim);
        if (w != null) {
            if (w.getBlockState(f.heart).isOf(FactionItems.COEUR)) w.setBlockState(f.heart, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            if (w.getBlockState(f.heart.up()).getBlock() instanceof BannerBlock) w.setBlockState(f.heart.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
        f.heart = null;
    }

    /** Bannière de la faction plantée sur le Cœur. */
    public static void placeBannerOnHeart(MinecraftServer server, Faction f) {
        if (f.heart == null) return;
        ServerWorld w = world(server, f.heartDim);
        if (w == null) return;
        BlockPos up = f.heart.up();
        BlockState here = w.getBlockState(up);
        if (!(here.isAir() || here.getBlock() instanceof BannerBlock)) return;
        w.setBlockState(up, BannerBlock.getForColor(DyeColor.byId(f.bannerBase)).getDefaultState(), Block.NOTIFY_ALL);
        if (w.getBlockEntity(up) instanceof BannerBlockEntity be) {
            be.readFrom(Banners.stack(f), DyeColor.byId(f.bannerBase));
            be.markDirty();
            w.updateListeners(up, w.getBlockState(up), w.getBlockState(up), Block.NOTIFY_ALL);
        }
    }

    public static void abandon(MinecraftServer server, Faction f, int territoryId) {
        Faction.Territory t = f.territory(territoryId);
        if (t == null) return;
        f.territories.remove(t);
        if (t.id == f.baseTerritory) {
            removeHeart(server, f);
            f.baseTerritory = -1;
        }
        f.log("Territoire abandonné en " + t.center.getX() + " / " + t.center.getZ() + ".");
        Factions.journal(server, f, "abandon", "pos", t.center.toShortString());
        FactionState.get(server).markDirty();
    }

    // ------------------------------------------------------------------ base : coffre commun, téléportation

    public static String openChest(ServerPlayerEntity p, Faction f) {
        Owner o = ownerAt(p.getServer(), p.getWorld(), p.getBlockPos());
        if (!HarvestRules.isExempt(p) && (o == null || o.faction.id != f.id || o.territory.id != f.baseTerritory)) {
            return "Le coffre commun s'ouvre à la base principale (ou en touchant le Cœur).";
        }
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, pl) -> GenericContainerScreenHandler.createGeneric9x6(syncId, inv, f.chest),
                Text.literal("Coffre commun — " + f.name)));
        return null;
    }

    public static String teleportBase(ServerPlayerEntity p) {
        Faction f = Factions.of(p);
        if (f == null) return "Tu n'es dans aucune faction.";
        if (f.heart == null) return "Ta faction n'a pas encore de base (Étendard de revendication).";
        if (fr.serveur.rpg.raid.RaidWorld.isIn(p)) return "Impossible depuis la zone de raid.";
        long now = System.currentTimeMillis();
        long cd = (long) FactionConfig.get().teleportBaseRechargeMinutes * 60_000;
        Long last = LAST_TP.get(p.getUuid());
        if (last != null && now - last < cd) return "Téléportation à la base possible dans " + Msg.duration((cd - (now - last)) / 1000) + ".";
        if (TELEPORTS.containsKey(p.getUuid())) return "Téléportation déjà en cours.";
        TELEPORTS.put(p.getUuid(), new Object[]{now + FactionConfig.get().teleportBaseSecondes * 1000L, p.getPos()});
        p.sendMessage(Text.literal("Ne bouge plus : téléportation à la base dans " + FactionConfig.get().teleportBaseSecondes + " s…").formatted(Formatting.DARK_AQUA), true);
        return null;
    }

    private static final Map<UUID, Long> LAST_TP = new HashMap<>();

    // ------------------------------------------------------------------ boucle

    private static final DustParticleEffect[] DUST = new DustParticleEffect[16];

    private static DustParticleEffect dust(int base) {
        if (DUST[base] == null) {
            int c = DyeColor.byId(base).getFireworkColor();
            DUST[base] = new DustParticleEffect(new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f), 1.4f);
        }
        return DUST[base];
    }

    private static void tick(MinecraftServer server) {
        int t = server.getTicks();
        if (!TELEPORTS.isEmpty()) teleports(server);
        if (t % 10 == 0) enterLeave(server);
        if (t % 20 == 5) borders(server);
        if (t % 20 == 10) hearts(server);
    }

    private static void teleports(MinecraftServer server) {
        long now = System.currentTimeMillis();
        TELEPORTS.entrySet().removeIf(e -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p == null) return true;
            Vec3d from = (Vec3d) e.getValue()[1];
            if (p.getPos().squaredDistanceTo(from) > 0.25) {
                p.sendMessage(Text.literal("Téléportation annulée : tu as bougé.").formatted(Formatting.RED), true);
                return true;
            }
            if (now < (long) e.getValue()[0]) return false;
            Faction f = Factions.of(p);
            ServerWorld w = f == null || f.heart == null ? null : world(server, f.heartDim);
            if (w != null) {
                Vec3d spot = safeSpot(w, f.heart);
                p.teleport(w, spot.x, spot.y, spot.z, p.getYaw(), p.getPitch());
                w.playSound(null, f.heart, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1f);
                LAST_TP.put(p.getUuid(), now);
            }
            return true;
        });
    }

    /** « Territoire de X » / « Terres sauvages » quand on franchit une limite. */
    private static void enterLeave(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Owner o = ownerAt(server, p.getWorld(), p.getBlockPos());
            int id = o == null ? 0 : o.faction.id;
            Integer before = LAST_TERRITORY.put(p.getUuid(), id);
            if (before == null || before == id) continue;
            if (o == null) {
                p.sendMessage(Text.literal("Terres sauvages").formatted(Formatting.GRAY), true);
            } else {
                boolean mine = o.faction.members.containsKey(p.getUuid());
                Faction pf = Factions.of(p);
                boolean war = pf != null && FactionState.get(server).warBetween(pf.id, o.faction.id) != null;
                p.sendMessage(Text.literal("⚑ Territoire de « " + o.faction.name + " »" + (o.territory.id == o.faction.baseTerritory ? " — base" : "")
                        + (war ? " — EN GUERRE" : "")).formatted(mine ? Formatting.GREEN : war ? Formatting.RED : Formatting.AQUA), true);
            }
        }
    }

    /** Limites visibles : particules de la couleur de la faction sur l'arc de cercle proche du joueur. */
    private static void borders(MinecraftServer server) {
        FactionState s = FactionState.get(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            String dim = p.getWorld().getRegistryKey().getValue().toString();
            ServerWorld w = p.getServerWorld();
            for (Faction f : s.factions.values()) {
                int r = f.radius();
                for (Faction.Territory t : f.territories) {
                    if (!t.dim.equals(dim)) continue;
                    double dx = p.getX() - (t.center.getX() + 0.5), dz = p.getZ() - (t.center.getZ() + 0.5);
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (Math.abs(d - r) > 10) continue;
                    double a0 = Math.atan2(dz, dx);
                    for (int i = -6; i <= 6; i++) {
                        double a = a0 + i * (3.0 / Math.max(r, 1));
                        double x = t.center.getX() + 0.5 + Math.cos(a) * r, z = t.center.getZ() + 0.5 + Math.sin(a) * r;
                        w.spawnParticles(p, dust(f.bannerBase), false, x, p.getY() + 0.3, z, 1, 0, 0.4, 0, 0);
                    }
                }
            }
        }
    }

    /** Cœurs : regagnent des points de vie sans coup reçu, et sont reposés s'ils ont disparu. */
    private static void hearts(MinecraftServer server) {
        FactionConfig.Data c = FactionConfig.get();
        long now = System.currentTimeMillis();
        for (Faction f : FactionState.get(server).factions.values()) {
            if (f.heart == null) continue;
            if (f.heartHp < c.coeurPointsDeVie && now - f.lastHeartHit > c.coeurRegenSecondes * 1000L
                    && server.getTicks() % (20 * Math.max(1, c.coeurRegenSecondes)) < 20) {
                f.heartHp++;
                FactionState.get(server).markDirty();
            }
            ServerWorld w = world(server, f.heartDim);
            if (w != null && w.getChunkManager().isChunkLoaded(f.heart.getX() >> 4, f.heart.getZ() >> 4)
                    && !w.getBlockState(f.heart).isOf(FactionItems.COEUR)) {
                w.setBlockState(f.heart, FactionItems.COEUR.getDefaultState(), Block.NOTIFY_ALL);
                placeBannerOnHeart(server, f);
            }
        }
    }

    static boolean recentlyHit(ServerPlayerEntity p, int ticks) {
        Integer last = LAST_HEART_HIT.get(p.getUuid());
        int now = p.getServer().getTicks();
        if (last != null && now - last < ticks) return true;
        LAST_HEART_HIT.put(p.getUuid(), now);
        return false;
    }

    public static void forget(UUID player) {
        LAST_TERRITORY.remove(player);
        LAST_WARN.remove(player);
        TELEPORTS.remove(player);
    }
}
