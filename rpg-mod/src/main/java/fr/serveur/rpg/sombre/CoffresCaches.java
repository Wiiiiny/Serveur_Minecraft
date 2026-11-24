package fr.serveur.rpg.sombre;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.economy.Economy;
import fr.serveur.rpg.mobs.MobRules;
import fr.serveur.rpg.raid.Party;
import fr.serveur.rpg.world.PlayableZone;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.EvokerFangsEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coffres cachés : posés au hasard autour des joueurs, dissimulés dans le décor (la brume de la nuit les cache, certains
 * sont enfouis), ils restent en place jusqu'à leur ouverture. Le Détecteur d'ombre (Obscurcie) les repère.
 * Ouverture : tout le groupe présent (/raid groupe, à moins de 12 blocs) l'ouvre ensemble, un seul membre tiré au sort
 * remporte le butin (placé dans son inventaire, ou au sol à son seul nom). Le butin dépend du type de coffre, du hasard
 * et du niveau de la zone (distance au point d'apparition). Certains coffres sont piégés (explosion, malédiction,
 * monstres, magie) ; le coffre de boss réveille d'abord un gardien qu'il faut abattre.
 */
public final class CoffresCaches {
    public enum Type {
        CLASSIQUE("Coffre classique", Formatting.WHITE, 0),
        ENFOUI("Coffre enfoui", Formatting.GOLD, 2),
        MAUDIT("Coffre maudit", Formatting.DARK_RED, 0),
        EXOTIQUE("Coffre exotique", Formatting.YELLOW, 4),
        LEGENDAIRE("Coffre légendaire", Formatting.LIGHT_PURPLE, 6),
        BOSS("Coffre de boss", Formatting.RED, 0),
        MAGIQUE("Coffre magique", Formatting.AQUA, 0);

        public final String nom;
        public final Formatting color;
        /** Profondeur sous la surface (0 = posé au sol). */
        public final int profondeur;

        Type(String nom, Formatting color, int profondeur) {
            this.nom = nom;
            this.color = color;
            this.profondeur = profondeur;
        }

        public static Type parse(String s) {
            for (Type t : values()) if (t.name().equalsIgnoreCase(s)) return t;
            return CLASSIQUE;
        }
    }

    private record Guardian(ServerBossBar bar, BlockPos chest, RegistryKey<World> dim) {}

    private record Trap(RegistryKey<World> dim, BlockPos pos, Type type, UUID opener, int zone, int at) {}

    private static final Map<UUID, Guardian> GUARDIANS = new HashMap<>();
    private static final List<Trap> TRAPS = new ArrayList<>();
    private static final String TRAP_TAG = "survierpg_piege";
    private static final DustParticleEffect VIOLET = new DustParticleEffect(new Vector3f(0.62f, 0.3f, 1f), 1.2f);

    private CoffresCaches() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CoffresCaches::tick);

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity p) || p.isSpectator()) return ActionResult.PASS;
            SombreState.Coffre c = find(SombreState.get(p.getServer()), world, hit.getBlockPos());
            if (c == null) return ActionResult.PASS;
            open(p, (ServerWorld) world, c);
            return ActionResult.SUCCESS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (world.isClient || player.getServer() == null) return true;
            SombreState s = SombreState.get(player.getServer());
            SombreState.Coffre c = find(s, world, pos);
            if (c == null) return true;
            if (player.isCreative()) {
                s.coffres.remove(c);
                s.markDirty();
                return true;
            }
            player.sendMessage(Text.literal("Ce coffre ne se brise pas : ouvre-le (clic droit).").formatted(Formatting.GRAY), true);
            return false;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Guardian g = GUARDIANS.remove(entity.getUuid());
            if (g == null || !(entity.getWorld() instanceof ServerWorld w)) return;
            g.bar.clearPlayers();
            SombreState s = SombreState.get(w.getServer());
            for (SombreState.Coffre c : s.coffres) {
                if (entity.getUuid().equals(c.boss)) {
                    c.bossVaincu = true;
                    s.markDirty();
                }
            }
            w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, g.chest.getX() + 0.5, g.chest.getY() + 1, g.chest.getZ() + 0.5, 60, 0.6, 0.8, 0.6, 0.3);
            for (ServerPlayerEntity p : w.getPlayers(pl -> pl.squaredDistanceTo(Vec3d.ofCenter(g.chest)) < 48 * 48)) {
                Msg.send(p, Text.literal("Le gardien est tombé : le coffre de boss peut être ouvert.").formatted(Formatting.GOLD));
            }
        });
    }

    // ------------------------------------------------------------------ recherche

    static SombreState.Coffre find(SombreState s, World world, BlockPos pos) {
        String dim = world.getRegistryKey().getValue().toString();
        for (SombreState.Coffre c : s.coffres) if (c.pos.equals(pos) && c.dim.equals(dim)) return c;
        return null;
    }

    /** Le coffre caché le plus proche dans ce monde, à moins de {@code range} blocs (null s'il n'y en a pas). */
    static SombreState.Coffre nearest(SombreState s, World world, Vec3d from, double range) {
        String dim = world.getRegistryKey().getValue().toString();
        SombreState.Coffre best = null;
        double bestD = range * range;
        for (SombreState.Coffre c : s.coffres) {
            if (!c.dim.equals(dim)) continue;
            double d = Vec3d.ofCenter(c.pos).squaredDistanceTo(from);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    /** Détecteur d'ombre : direction et distance du coffre le plus proche, particules vers lui. */
    public static void guide(ServerPlayerEntity p, ServerWorld w) {
        SombreState.Coffre c = nearest(SombreState.get(p.getServer()), w, p.getPos(), SombreConfig.get().obscurcie.detecteurPortee);
        if (c == null) {
            p.sendMessage(Text.literal("✦ Le détecteur reste froid : aucun coffre caché à portée.").formatted(Formatting.DARK_GRAY), true);
            return;
        }
        Vec3d target = Vec3d.ofCenter(c.pos);
        double dx = target.x - p.getX(), dz = target.z - p.getZ(), dy = target.y - p.getY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float rel = MathHelper.wrapDegrees(yaw - p.getYaw());
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        String arrow = arrows[Math.floorMod(Math.round(rel / 45f), 8)];
        String hint = flat < 4 && dy < -1.5 ? " · sous tes pieds, creuse !" : dy < -4 ? " · en profondeur" : dy > 4 ? " · plus haut" : "";
        p.sendMessage(Text.literal("✦ Coffre caché  ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal(arrow + "  " + Math.round(Math.sqrt(flat * flat + dy * dy)) + " blocs").formatted(Formatting.WHITE, Formatting.BOLD))
                .append(Text.literal(hint).formatted(Formatting.GRAY)), true);
        Vec3d eye = p.getEyePos().add(0, -0.4, 0);
        Vec3d dir = target.subtract(eye).normalize();
        for (int i = 1; i <= 6; i++) {
            Vec3d at = eye.add(dir.multiply(0.7 + i * 0.6));
            w.spawnParticles(VIOLET, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        w.spawnParticles(ParticleTypes.REVERSE_PORTAL, eye.x + dir.x * 4.5, eye.y + dir.y * 4.5, eye.z + dir.z * 4.5, 4, 0.1, 0.1, 0.1, 0.02);
    }

    // ------------------------------------------------------------------ cycle

    private static void tick(MinecraftServer server) {
        int t = server.getTicks();
        if (!TRAPS.isEmpty()) fireTraps(server, t);
        if (t % 10 == 0 && !GUARDIANS.isEmpty()) updateGuardians(server);
        if (t % 40 == 0) ambiance(server);
        int every = Math.max(5, SombreConfig.get().coffres.intervalleSecondes) * 20;
        if (t % every == 0 && t > 0) {
            validate(server);
            spawn(server, false);
        }
    }

    /** Coffres magiques : lueur d'enchantement visible de près. */
    private static void ambiance(MinecraftServer server) {
        SombreState s = SombreState.get(server);
        for (SombreState.Coffre c : s.coffres) {
            if (c.type != Type.MAGIQUE && c.type != Type.MAUDIT) continue;
            ServerWorld w = world(server, c.dim);
            if (w == null || !loaded(w, c.pos)) continue;
            if (w.getClosestPlayer(c.pos.getX() + 0.5, c.pos.getY(), c.pos.getZ() + 0.5, 24, false) == null) continue;
            if (c.type == Type.MAGIQUE) {
                w.spawnParticles(ParticleTypes.ENCHANT, c.pos.getX() + 0.5, c.pos.getY() + 1.2, c.pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.6);
            } else {
                w.spawnParticles(ParticleTypes.SOUL, c.pos.getX() + 0.5, c.pos.getY() + 1.1, c.pos.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0.01);
            }
        }
    }

    static boolean loaded(ServerWorld w, BlockPos pos) {
        return w.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    static ServerWorld world(MinecraftServer server, String dim) {
        Identifier id = Identifier.tryParse(dim);
        return id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    }

    /** Oublie les coffres dont le bloc a disparu (explosion, GM...). */
    private static void validate(MinecraftServer server) {
        SombreState s = SombreState.get(server);
        boolean changed = false;
        for (Iterator<SombreState.Coffre> it = s.coffres.iterator(); it.hasNext(); ) {
            SombreState.Coffre c = it.next();
            ServerWorld w = world(server, c.dim);
            if (w == null) {
                it.remove();
                changed = true;
            } else if (loaded(w, c.pos) && !(w.getBlockState(c.pos).getBlock() instanceof ChestBlock)) {
                it.remove();
                changed = true;
                Journal.write(server, "coffres", "disparu", "type", c.type.name(), "pos", c.pos.toShortString());
            }
        }
        if (changed) s.markDirty();
    }

    /**
     * Pose un nouveau coffre si on est sous l'objectif (base + par joueur connecté) ; {@code force} : jusqu'au maximum absolu
     * (commande GM). @return vrai si un coffre a été posé.
     */
    public static boolean spawn(MinecraftServer server, boolean force) {
        SombreConfig.Coffres cfg = SombreConfig.get().coffres;
        ServerWorld w = server.getOverworld();
        List<ServerPlayerEntity> players = w.getPlayers(p -> !p.isSpectator() && p.isAlive());
        if (players.isEmpty()) return false;
        SombreState s = SombreState.get(server);
        int target = force ? cfg.maxActifs : Math.min(cfg.maxActifs, cfg.baseActifs + cfg.parJoueur * players.size());
        if (s.coffres.size() >= target) return false;
        Random r = w.random;
        ServerPlayerEntity around = players.get(r.nextInt(players.size()));
        Type type = rollType(server, r);
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = r.nextDouble() * Math.PI * 2;
            int dist = cfg.distanceMin + r.nextInt(Math.max(1, cfg.distanceMax - cfg.distanceMin));
            int x = (int) Math.floor(around.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(around.getZ() + Math.sin(angle) * dist);
            BlockPos pos = place(w, x, z, type, r);
            if (pos != null) {
                record(server, w, pos, type, r.nextDouble() < cfg.chancePiege.getOrDefault(type.name(), 0.0));
                return true;
            }
        }
        return false;
    }

    private static Type rollType(MinecraftServer server, Random r) {
        Map<String, Integer> weights = SombreConfig.get().coffres.poids;
        boolean exotic = Exotiques.available(server) != null;
        int total = 0;
        for (Type t : Type.values()) if (t != Type.EXOTIQUE || exotic) total += Math.max(0, weights.getOrDefault(t.name(), 0));
        if (total <= 0) return Type.CLASSIQUE;
        int roll = r.nextInt(total);
        for (Type t : Type.values()) {
            if (t == Type.EXOTIQUE && !exotic) continue;
            roll -= Math.max(0, weights.getOrDefault(t.name(), 0));
            if (roll < 0) return t;
        }
        return Type.CLASSIQUE;
    }

    /** Trouve et prépare l'emplacement (chunk déjà chargé, zone jouable, hors zones protégées). @return la position du coffre, ou null. */
    private static BlockPos place(ServerWorld w, int x, int z, Type type, Random r) {
        if (!w.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return null;
        RpgState rs = RpgState.get(w.getServer());
        PlayableZone.Box zone = PlayableZone.box(rs);
        if (zone != null && (zone.dim() != w.getRegistryKey() || !zone.contains(x + 0.5, z + 0.5))) return null;
        int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (top <= w.getBottomY() + 10) return null;
        BlockPos ground = new BlockPos(x, top - 1, z);
        BlockState gs = w.getBlockState(ground);
        if (!gs.getFluidState().isEmpty() || gs.isAir() || gs.isIn(BlockTags.LEAVES) || gs.hasBlockEntity()) return null;
        if (rs.isProtected(w, ground)) return null;
        if (fr.serveur.rpg.faction.Territories.ownerAt(w.getServer(), w, ground) != null) return null; // pas dans un territoire de faction
        BlockPos pos;
        if (type.profondeur > 0) {
            pos = ground.down(type.profondeur);
            for (BlockPos p = pos; p.getY() <= ground.getY(); p = p.up()) {
                BlockState b = w.getBlockState(p);
                if (b.hasBlockEntity() || b.isOf(Blocks.BEDROCK) || !b.isOpaqueFullCube(w, p)) return null;
            }
        } else {
            pos = ground.up();
            BlockState here = w.getBlockState(pos);
            if (!(here.isAir() || here.isReplaceable()) || !here.getFluidState().isEmpty()) return null;
            if (!gs.isSideSolidFullSquare(w, ground, Direction.UP)) return null;
        }
        Direction facing = Direction.fromHorizontal(r.nextInt(4));
        w.setBlockState(pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing), Block.NOTIFY_ALL);
        if (type == Type.MAUDIT && (gs.isIn(BlockTags.DIRT) || gs.isIn(BlockTags.SAND) || gs.isOf(Blocks.STONE))) {
            w.setBlockState(ground, Blocks.SOUL_SOIL.getDefaultState(), Block.NOTIFY_ALL);
        }
        return pos;
    }

    private static void record(MinecraftServer server, ServerWorld w, BlockPos pos, Type type, boolean trap) {
        SombreState s = SombreState.get(server);
        String dim = w.getRegistryKey().getValue().toString();
        s.coffres.removeIf(o -> o.pos.equals(pos) && o.dim.equals(dim)); // un seul coffre par emplacement
        SombreState.Coffre c = new SombreState.Coffre();
        c.pos = pos.toImmutable();
        c.dim = dim;
        c.type = type;
        c.piege = trap;
        c.cree = System.currentTimeMillis();
        s.coffres.add(c);
        s.markDirty();
        Journal.write(server, "coffres", "apparition", "type", type.name(), "pos", pos.toShortString(), "piege", trap);
    }

    /** GM : pose un coffre caché à cet endroit (le bloc doit être libre). */
    public static String spawnAt(ServerWorld w, BlockPos pos, Type type, boolean trap) {
        BlockState here = w.getBlockState(pos);
        if (!(here.isAir() || here.isReplaceable())) return "L'emplacement n'est pas libre.";
        w.setBlockState(pos, Blocks.CHEST.getDefaultState(), Block.NOTIFY_ALL);
        record(w.getServer(), w, pos, type, trap);
        return null;
    }

    // ------------------------------------------------------------------ ouverture

    private static void open(ServerPlayerEntity opener, ServerWorld w, SombreState.Coffre c) {
        MinecraftServer server = w.getServer();
        SombreConfig.Coffres cfg = SombreConfig.get().coffres;
        SombreState s = SombreState.get(server);

        if (c.type == Type.BOSS && !c.bossVaincu) {
            Entity e = c.boss == null ? null : w.getEntity(c.boss);
            if (e != null && e.isAlive()) {
                opener.sendMessage(Text.literal("Le gardien veille encore sur ce coffre.").formatted(Formatting.RED), true);
                return;
            }
            spawnGuardian(w, c, group(opener, w, c.pos));
            s.markDirty();
            return;
        }

        List<ServerPlayerEntity> group = group(opener, w, c.pos);
        ServerPlayerEntity winner = group.get(w.random.nextInt(group.size()));
        int zone = zoneLevel(w, c.pos);
        List<ItemStack> loot = rollLoot(c.type, zone, w.random);
        String exoId = null;
        if (c.type == Type.EXOTIQUE) {
            exoId = Exotiques.available(server);
            if (exoId != null) {
                ItemStack exo = Exotiques.introduce(server, exoId, winner.getGameProfile().getName(), "coffre exotique " + c.pos.toShortString(), true);
                if (!exo.isEmpty()) loot.add(0, exo);
            }
        }
        if (w.random.nextDouble() < cfg.chanceEclat.getOrDefault(c.type.name(), 0.0)) loot.add(new ItemStack(SombreItems.ECLAT_LUMIERE));
        for (ItemStack st : loot) giveLocked(winner, st.copy());
        long coins = Economy.earn(server, winner.getUuid(), Economy.Source.COFFRE,
                (double) cfg.pieces.getOrDefault(c.type.name(), 0) * zone, c.type.nom);

        w.setBlockState(c.pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        s.coffres.remove(c);
        s.markDirty();
        Vec3d at = Vec3d.ofCenter(c.pos);
        w.playSound(null, c.pos, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 1f, 0.8f);
        w.playSound(null, c.pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.6f, 1.4f);
        w.spawnParticles(c.type == Type.EXOTIQUE || c.type == Type.LEGENDAIRE ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.HAPPY_VILLAGER,
                at.x, at.y + 0.5, at.z, 30, 0.5, 0.5, 0.5, 0.2);

        SombreConfig.Personnage pc = SombreConfig.get().personnage;
        for (ServerPlayerEntity m : group) {
            Obscurcie.addXp(m, SombreConfig.get().obscurcie.xpCoffre, true);
            Personnage.addXp(m, pc.xpCoffre, null);
        }
        fr.serveur.rpg.faction.Factions.onChest(group);
        RpgState.get(server).player(winner).chestsOpened++;
        RpgState.get(server).markDirty();

        String winName = winner.getGameProfile().getName();
        MutableText list = Text.empty();
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < loot.size(); i++) {
            ItemStack st = loot.get(i);
            if (i > 0) list.append(Text.literal(", ").formatted(Formatting.GRAY));
            list.append(Text.literal(st.getCount() + "× ").formatted(Formatting.GRAY)).append(st.getName().copy().formatted(Formatting.WHITE));
            log.append(i > 0 ? ", " : "").append(st.getCount()).append("x ").append(Registries.ITEM.getId(st.getItem()));
        }
        for (ServerPlayerEntity m : group) {
            MutableText head = Text.literal("✦ " + c.type.nom).formatted(c.type.color, Formatting.BOLD);
            if (group.size() > 1) head.append(Text.literal(" — " + (m == winner ? "tu remportes le butin !" : winName + " remporte le butin."))
                    .formatted(Formatting.GOLD).styled(st -> st.withBold(false)));
            Msg.send(m, head);
            if (m == winner) {
                Msg.send(m, list.copy().append(coins > 0 ? Text.literal(" · +" + Bank.format(coins)).formatted(Formatting.GOLD) : Text.empty()));
            }
        }
        Journal.write(server, "coffres", "ouverture", "type", c.type.name(), "pos", c.pos.toShortString(), "zone", zone,
                "gagnant", winName, "groupe", group.size(), "butin", log.toString(), "pieces", coins, "piege", c.piege,
                "exotique", exoId == null ? "" : exoId);
        Journal.write(server, "recompenses", "coffre", "joueur", winName, "type", c.type.name(), "butin", log.toString(), "pieces", coins);

        if (c.piege) {
            for (ServerPlayerEntity m : group) m.sendMessage(Text.literal("Un déclic résonne sous le couvercle…").formatted(Formatting.DARK_RED), true);
            w.playSound(null, c.pos, SoundEvents.BLOCK_TRIPWIRE_CLICK_ON, SoundCategory.BLOCKS, 1f, 0.6f);
            TRAPS.add(new Trap(w.getRegistryKey(), c.pos, c.type, opener.getUuid(), zone, server.getTicks() + 20));
        }
    }

    /** Le joueur et les membres de son groupe présents (même monde, à moins de rayonGroupe blocs du coffre). */
    private static List<ServerPlayerEntity> group(ServerPlayerEntity opener, ServerWorld w, BlockPos pos) {
        double r = SombreConfig.get().coffres.rayonGroupe;
        List<ServerPlayerEntity> out = new ArrayList<>();
        out.add(opener);
        for (UUID id : Party.members(opener.getUuid())) {
            if (id.equals(opener.getUuid())) continue;
            ServerPlayerEntity m = w.getServer().getPlayerManager().getPlayer(id);
            if (m != null && m.getWorld() == w && m.isAlive() && !m.isSpectator() && m.squaredDistanceTo(Vec3d.ofCenter(pos)) <= r * r) out.add(m);
        }
        return out;
    }

    /** Niveau de la zone : 1 près du point d'apparition, +1 tous les N blocs, jusqu'au maximum. */
    static int zoneLevel(ServerWorld w, BlockPos pos) {
        SombreConfig.Coffres cfg = SombreConfig.get().coffres;
        BlockPos spawn = w.getSpawnPos();
        double d = Math.sqrt(Math.pow(pos.getX() - spawn.getX(), 2) + Math.pow(pos.getZ() - spawn.getZ(), 2));
        return (int) Math.max(1, Math.min(cfg.niveauZoneMax, 1 + d / Math.max(1, cfg.niveauZoneTousLesBlocs)));
    }

    static List<ItemStack> rollLoot(Type type, int zone, Random r) {
        SombreConfig.Coffres cfg = SombreConfig.get().coffres;
        List<SombreConfig.Lot> lots = cfg.butin.getOrDefault(type.name(), List.of());
        List<ItemStack> out = new ArrayList<>();
        int total = 0;
        for (SombreConfig.Lot l : lots) total += Math.max(0, l.poids);
        if (total <= 0) return out;
        int rolls = (int) Math.round(cfg.tirages.getOrDefault(type.name(), 3) * (1 + 0.25 * (zone - 1)));
        for (int i = 0; i < rolls; i++) {
            int roll = r.nextInt(total);
            SombreConfig.Lot pick = null;
            for (SombreConfig.Lot l : lots) {
                roll -= Math.max(0, l.poids);
                if (roll < 0) {
                    pick = l;
                    break;
                }
            }
            if (pick == null) continue;
            Identifier id = Identifier.tryParse(pick.objet);
            Item item = id == null ? Items.AIR : Registries.ITEM.get(id);
            if (item == Items.AIR) continue;
            int count = pick.min + (pick.max > pick.min ? r.nextInt(pick.max - pick.min + 1) : 0);
            ItemStack st = new ItemStack(item, Math.max(1, Math.min(count, item.getMaxCount())));
            if (pick.niveauEnchantement > 0) {
                int lvl = pick.niveauEnchantement + 2 * (zone - 1);
                st = item == Items.ENCHANTED_BOOK ? EnchantmentHelper.enchant(r, new ItemStack(Items.BOOK), lvl, true)
                        : EnchantmentHelper.enchant(r, st, lvl, false);
            }
            merge(out, st);
        }
        return out;
    }

    private static void merge(List<ItemStack> out, ItemStack st) {
        for (ItemStack o : out) {
            if (ItemStack.canCombine(o, st) && o.getCount() + st.getCount() <= o.getMaxCount()) {
                o.increment(st.getCount());
                return;
            }
        }
        out.add(st);
    }

    /** Dans l'inventaire du gagnant ; s'il est plein, au sol et ramassable par lui seul. */
    static void giveLocked(ServerPlayerEntity p, ItemStack st) {
        if (p.getInventory().insertStack(st) && st.isEmpty()) return;
        ItemEntity e = p.dropItem(st, false);
        if (e != null) {
            e.setOwner(p.getUuid());
            e.resetPickupDelay();
        }
    }

    // ------------------------------------------------------------------ pièges

    private static void fireTraps(MinecraftServer server, int now) {
        for (Iterator<Trap> it = TRAPS.iterator(); it.hasNext(); ) {
            Trap t = it.next();
            if (now < t.at) continue;
            it.remove();
            ServerWorld w = server.getWorld(t.dim);
            if (w == null) continue;
            ServerPlayerEntity opener = server.getPlayerManager().getPlayer(t.opener);
            Vec3d at = Vec3d.ofCenter(t.pos);
            List<ServerPlayerEntity> near = w.getPlayers(p -> p.squaredDistanceTo(at) < 8 * 8 && !p.isSpectator());
            Vec3d spawnNear = opener != null && opener.getWorld() == w ? opener.getPos() : at;
            switch (t.type) {
                case ENFOUI -> {
                    // Piège mortel : explosion qui ne casse aucun bloc.
                    w.createExplosion(null, at.x, at.y, at.z, 3.5f, World.ExplosionSourceType.NONE);
                }
                case MAUDIT -> {
                    for (ServerPlayerEntity p : near) {
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 160, 1));
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 120, 0));
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 300, 1));
                    }
                    w.playSound(null, t.pos, SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 1f, 0.7f);
                    for (int i = 0; i < 2 + t.zone / 2; i++) {
                        VexEntity vex = EntityType.VEX.create(w);
                        if (vex == null) continue;
                        vex.refreshPositionAndAngles(at.x + w.random.nextGaussian(), at.y + 1.5, at.z + w.random.nextGaussian(), 0, 0);
                        vex.setLifeTicks(20 * 40);
                        vex.addCommandTag(TRAP_TAG);
                        provoke(vex, near);
                        w.spawnEntity(vex);
                    }
                }
                case MAGIQUE -> {
                    for (ServerPlayerEntity p : near) p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 80, 1));
                    for (int i = 0; i < 12; i++) {
                        double a = i * Math.PI / 6;
                        w.spawnEntity(new EvokerFangsEntity(w, spawnNear.x + Math.cos(a) * 2.2, spawnNear.y, spawnNear.z + Math.sin(a) * 2.2,
                                (float) a, 6 + i, null));
                    }
                    w.playSound(null, t.pos, SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 1f, 1f);
                }
                default -> {
                    // Monstres : classique, exotique, légendaire.
                    int n = (t.type == Type.CLASSIQUE ? 2 : t.type == Type.EXOTIQUE ? 3 : 4) + t.zone;
                    List<EntityType<? extends MobEntity>> kinds = new ArrayList<>(List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER));
                    if (t.type != Type.CLASSIQUE) kinds.add(EntityType.WITHER_SKELETON);
                    for (int i = 0; i < n; i++) spawnMob(w, kinds.get(w.random.nextInt(kinds.size())), spawnNear, near);
                    w.playSound(null, t.pos, SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.HOSTILE, 1f, 0.6f);
                }
            }
            Journal.write(server, "coffres", "piege", "type", t.type.name(), "pos", t.pos.toShortString(), "joueurs", near.size());
        }
    }

    private static void provoke(MobEntity m, List<ServerPlayerEntity> targets) {
        for (ServerPlayerEntity p : targets) m.addCommandTag(MobRules.AGGRO_TAG_PREFIX + p.getUuidAsString());
        if (!targets.isEmpty()) m.setTarget(targets.get(0));
    }

    private static MobEntity spawnMob(ServerWorld w, EntityType<? extends MobEntity> type, Vec3d near, List<ServerPlayerEntity> targets) {
        MobEntity m = type.create(w);
        if (m == null) return null;
        Vec3d at = near;
        for (int tries = 0; tries < 10; tries++) {
            double a = w.random.nextDouble() * Math.PI * 2, d = 2 + w.random.nextDouble() * 3;
            Vec3d floor = floor(w, m, near.add(Math.cos(a) * d, 0, Math.sin(a) * d));
            if (floor != null) {
                at = floor;
                break;
            }
        }
        m.refreshPositionAndAngles(at.x, at.y, at.z, w.random.nextFloat() * 360, 0);
        m.initialize(w, w.getLocalDifficulty(BlockPos.ofFloored(at)), SpawnReason.EVENT, null, null);
        m.addCommandTag(TRAP_TAG);
        provoke(m, targets);
        w.spawnEntityAndPassengers(m);
        return m;
    }

    /** Un emplacement libre posé sur un sol solide, entre 2 blocs au-dessus et 5 blocs sous {@code p} ; null sinon. */
    private static Vec3d floor(ServerWorld w, MobEntity m, Vec3d p) {
        BlockPos base = BlockPos.ofFloored(p);
        for (int dy = 2; dy >= -5; dy--) {
            BlockPos feet = base.up(dy);
            if (!w.getBlockState(feet.down()).isSideSolidFullSquare(w, feet.down(), Direction.UP)) continue;
            m.refreshPositionAndAngles(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, w.random.nextFloat() * 360, 0);
            if (w.isSpaceEmpty(m)) return new Vec3d(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        }
        return null;
    }

    // ------------------------------------------------------------------ gardien du coffre de boss

    private static void spawnGuardian(ServerWorld w, SombreState.Coffre c, List<ServerPlayerEntity> group) {
        SombreConfig.Coffres cfg = SombreConfig.get().coffres;
        int zone = zoneLevel(w, c.pos);
        Vec3d at = Vec3d.ofCenter(c.pos).add(0, 0.5, 0);
        MobEntity boss = spawnMob(w, EntityType.WITHER_SKELETON, at, group);
        if (boss == null) return;
        double hp = (cfg.bossVie + cfg.bossVieParJoueur * group.size()) * (1 + 0.2 * (zone - 1));
        var maxHp = boss.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(hp);
        boss.setHealth((float) hp);
        var armor = boss.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (armor != null) armor.setBaseValue(8 + zone);
        var dmg = boss.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() + 2 + zone);
        boss.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        boss.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        boss.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
        boss.setEquipmentDropChance(EquipmentSlot.HEAD, 0f);
        boss.setCustomName(Text.literal("Gardien du coffre").formatted(Formatting.RED, Formatting.BOLD));
        boss.setCustomNameVisible(true);
        boss.setPersistent();
        c.boss = boss.getUuid();
        ServerBossBar bar = new ServerBossBar(Text.literal("Gardien du coffre").formatted(Formatting.RED), BossBar.Color.RED, BossBar.Style.NOTCHED_10);
        GUARDIANS.put(boss.getUuid(), new Guardian(bar, c.pos, w.getRegistryKey()));
        w.playSound(null, c.pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.8f, 1.2f);
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 40, 0.5, 1, 0.5, 0.05);
        for (ServerPlayerEntity p : group) {
            Msg.send(p, Text.literal("Un gardien se dresse ! Abats-le pour ouvrir le coffre.").formatted(Formatting.RED));
        }
    }

    private static void updateGuardians(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Guardian>> it = GUARDIANS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Guardian> e = it.next();
            Guardian g = e.getValue();
            ServerWorld w = server.getWorld(g.dim);
            Entity ent = w == null ? null : w.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) {
                g.bar.clearPlayers();
                it.remove();
                continue;
            }
            g.bar.setPercent(Math.max(0, le.getHealth() / le.getMaxHealth()));
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                boolean near = p.getWorld() == w && p.squaredDistanceTo(le) < 40 * 40;
                if (near && !g.bar.getPlayers().contains(p)) g.bar.addPlayer(p);
                else if (!near && g.bar.getPlayers().contains(p)) g.bar.removePlayer(p);
            }
        }
    }
}
