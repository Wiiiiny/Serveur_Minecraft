package fr.serveur.rpg.raid;

import fr.serveur.rpg.SurvieRpg;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Pose le plan d'une partie dans le monde du raid, petit à petit (un certain nombre de blocs par tick) pour ne pas
 * figer le serveur. Chaque salle est posée sur toute sa hauteur (air compris) : elle remplace celle du cycle précédent.
 * Les marqueurs (panneaux, coffres) sont relevés dans {@link RaidState} au passage.
 */
public final class RaidBuilder {
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

    /** Une étape de la génération : pose au plus {@code budget} blocs, renvoie le nombre posé. */
    private interface Job {
        int run(ServerWorld world, int budget);

        boolean done();
    }

    private static final Deque<Job> JOBS = new ArrayDeque<>();
    private static Runnable onFinish;
    private static long total, placed;

    private RaidBuilder() {}

    public static boolean running() {
        return !JOBS.isEmpty() || onFinish != null;
    }

    /** Avancement en pour cent. */
    public static int progress() {
        return total <= 0 ? 100 : (int) Math.min(100, placed * 100 / total);
    }

    public static void start(ServerWorld world, Layout layout, RaidState state, Runnable finish) {
        JOBS.clear();
        RaidConfig.Data c = RaidConfig.get();
        int cs = c.cellule, h = RoomLibrary.height(), y0 = c.hauteurSol;
        int ox = RaidWorld.originX(), oz = RaidWorld.originZ(), side = RaidWorld.sideBlocks();
        state.height = h;
        total = placed = 0;
        BlockState seal = parse(c.blocScellement);
        for (Layout.Placement p : layout.placements) {
            int size = p.cells() * cs;
            JOBS.add(new PasteRoom(p, ox + p.gx * cs, y0, oz + p.gz * cs, size, h, state, layout.ring(p.gx, p.gz)));
            total += (long) size * size * h;
        }
        for (int[] e : layout.emptyCells()) {
            JOBS.add(new Fill(ox + e[0] * cs, y0, oz + e[1] * cs, ox + e[0] * cs + cs - 1, y0 + h - 1, oz + e[1] * cs + cs - 1, seal));
            total += (long) cs * cs * h;
        }
        JOBS.add(new Doors(layout, ox, y0, oz, cs, seal));
        // Plancher et plafond de barrière, murs de barrière autour de la zone (personne ne sort par le vide).
        BlockState barrier = Blocks.BARRIER.getDefaultState();
        JOBS.add(new Fill(ox - 1, y0 - 1, oz - 1, ox + side, y0 - 1, oz + side, barrier));
        JOBS.add(new Fill(ox - 1, y0 + h, oz - 1, ox + side, y0 + h, oz + side, barrier));
        JOBS.add(new Fill(ox - 1, y0, oz - 1, ox + side, y0 + h - 1, oz - 1, barrier));
        JOBS.add(new Fill(ox - 1, y0, oz + side, ox + side, y0 + h - 1, oz + side, barrier));
        JOBS.add(new Fill(ox - 1, y0, oz - 1, ox - 1, y0 + h - 1, oz + side, barrier));
        JOBS.add(new Fill(ox + side, y0, oz - 1, ox + side, y0 + h - 1, oz + side, barrier));
        total += 2L * (side + 2) * (side + 2) + 4L * (side + 2) * h;
        onFinish = finish;
    }

    /** Appelé à chaque tick du serveur. */
    public static void tick(ServerWorld world) {
        if (world == null || (JOBS.isEmpty() && onFinish == null)) return;
        int budget = Math.max(500, RaidConfig.get().blocsParTick);
        try {
            while (budget > 0 && !JOBS.isEmpty()) {
                Job j = JOBS.peek();
                int n = j.run(world, budget);
                budget -= Math.max(1, n);
                placed += n;
                if (j.done()) JOBS.poll();
            }
            if (JOBS.isEmpty() && onFinish != null) {
                Runnable r = onFinish;
                onFinish = null;
                r.run();
            }
        } catch (Exception e) {
            SurvieRpg.LOGGER.error("[raid] erreur pendant la génération", e);
            JOBS.clear();
            onFinish = null;
            fr.serveur.rpg.economy.Economy.alert(world.getServer(), "Raid : erreur pendant la génération (" + e.getMessage() + "). Voir les journaux.");
        }
    }

    public static void cancel() {
        JOBS.clear();
        onFinish = null;
    }

    static BlockState parse(String id) {
        try {
            return BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), id, false).blockState();
        } catch (Exception e) {
            return Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        }
    }

    /** Pose un bloc en vidant d'abord un éventuel inventaire (sinon le contenu tomberait au sol). */
    static void set(ServerWorld world, BlockPos pos, BlockState state) {
        BlockState old = world.getBlockState(pos);
        if (old == state) return;
        if (old.hasBlockEntity() && world.getBlockEntity(pos) instanceof Inventory inv) inv.clear();
        world.setBlockState(pos, state, FLAGS);
    }

    // ------------------------------------------------------------------ étapes

    /** Pose une salle, couche par couche, en relevant ses marqueurs. */
    private static final class PasteRoom implements Job {
        final Layout.Placement p;
        final int ox, oy, oz, size, h;
        final RaidState state;
        final int tier;
        final Map<Integer, Room.Marker> markers = new HashMap<>();
        int i;

        PasteRoom(Layout.Placement p, int ox, int oy, int oz, int size, int h, RaidState state, Room.Ring ring) {
            this.p = p;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.size = size;
            this.h = h;
            this.state = state;
            this.tier = ring == Room.Ring.COEUR ? 3 : ring == Room.Ring.INTERMEDIAIRE ? 2 : 1;
            for (Room.Marker m : p.room.markers) markers.put(m.x() + m.z() * 1000 + m.y() * 1_000_000, m);
            state.rooms.add(p.room.name + " @" + p.gx + "," + p.gz + " r" + p.rot);
            if (p.room.kind == Room.Kind.ARENE) state.arenas.add(new RaidState.Area(ox, oz, ox + size - 1, oz + size - 1));
        }

        @Override
        public boolean done() {
            return i >= size * size * h;
        }

        @Override
        public int run(ServerWorld world, int budget) {
            int n = 0;
            BlockPos.Mutable pos = new BlockPos.Mutable();
            int back = (4 - (p.rot & 3)) & 3;
            var rotation = Room.rotation(p.rot);
            Schematic s = p.room.schem;
            while (n < budget && !done()) {
                int y = i / (size * size), rem = i % (size * size), rz = rem / size, rx = rem % size;
                i++;
                n++;
                int[] src = Room.rotate(rx, rz, back, size);
                int sx = src[0], sz = src[1];
                pos.set(ox + rx, oy + y, oz + rz);
                Room.Marker m = markers.get(sx + sz * 1000 + y * 1_000_000);
                BlockState st = s.get(sx, y, sz).rotate(rotation);
                if (m != null) {
                    BlockPos at = pos.toImmutable();
                    switch (m.type()) {
                        case "coffre", "piege" -> {
                            set(world, at, st);
                            state.chests.add(new RaidState.Chest(at, tier, m.type().equals("piege")
                                    || world.random.nextDouble() < RaidConfig.get().coffrePiegeChance));
                            continue;
                        }
                        case "arrivee" -> state.spawns.add(at);
                        case "extraction" -> {
                            set(world, at, Blocks.LODESTONE.getDefaultState());
                            state.pedestal = at;
                            continue;
                        }
                        case "portail" -> state.portalCenter = at;
                        case "tresor" -> {
                            set(world, at, Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
                            state.treasure = at;
                            continue;
                        }
                        default -> state.spots.add(new RaidState.Spot(at, m.type()));
                    }
                    set(world, at, Blocks.AIR.getDefaultState()); // le panneau-marqueur disparaît
                    continue;
                }
                set(world, pos, st);
                if (st.hasBlockEntity() && !(st.getBlock() instanceof ChestBlock)) {
                    NbtCompound nbt = s.blockEntity(sx, y, sz);
                    BlockEntity be = world.getBlockEntity(pos);
                    if (nbt != null && be != null) {
                        NbtCompound copy = nbt.copy();
                        copy.putInt("x", pos.getX());
                        copy.putInt("y", pos.getY());
                        copy.putInt("z", pos.getZ());
                        be.readNbt(copy);
                        be.markDirty();
                    }
                }
            }
            return n;
        }
    }

    /** Remplit un pavé avec un bloc. */
    private static final class Fill implements Job {
        final int x1, y1, z1, w, h, l;
        final BlockState st;
        long i;

        Fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState st) {
            this.x1 = Math.min(x1, x2);
            this.y1 = Math.min(y1, y2);
            this.z1 = Math.min(z1, z2);
            this.w = Math.abs(x2 - x1) + 1;
            this.h = Math.abs(y2 - y1) + 1;
            this.l = Math.abs(z2 - z1) + 1;
            this.st = st;
        }

        @Override
        public boolean done() {
            return i >= (long) w * h * l;
        }

        @Override
        public int run(ServerWorld world, int budget) {
            int n = 0;
            BlockPos.Mutable pos = new BlockPos.Mutable();
            while (n < budget && !done()) {
                long y = i / ((long) w * l), rem = i % ((long) w * l);
                pos.set(x1 + (int) (rem % w), y1 + (int) y, z1 + (int) (rem / w));
                set(world, pos, st);
                i++;
                n++;
            }
            return n;
        }
    }

    /** Perce les portes manquantes et bouche celles qui ne mènent nulle part. */
    private static final class Doors implements Job {
        final Layout layout;
        final int ox, oy, oz, cs;
        final BlockState seal;
        boolean finished;

        Doors(Layout layout, int ox, int oy, int oz, int cs, BlockState seal) {
            this.layout = layout;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.cs = cs;
            this.seal = seal;
        }

        @Override
        public boolean done() {
            return finished;
        }

        @Override
        public int run(ServerWorld world, int budget) {
            int n = 0;
            for (Layout.Placement p : layout.placements) {
                int size = p.cells() * cs, px = ox + p.gx * cs, pz = oz + p.gz * cs;
                for (int d : p.carve) n += door(world, px, pz, size, d / 2, d % 2, 2, pos -> set(world, pos, Blocks.AIR.getDefaultState()));
                for (int d : p.seal) n += door(world, px, pz, size, d / 2, d % 2, 1, pos -> set(world, pos, seal));
            }
            finished = true;
            return n;
        }

        private int door(ServerWorld world, int px, int pz, int size, int side, int seg, int depth, Consumer<BlockPos> action) {
            int n = 0;
            for (int a = seg * cs + cs / 2 - 2; a <= seg * cs + cs / 2 + 1; a++) {
                for (int y = 1; y <= 3; y++) {
                    for (int d = 0; d < depth; d++) {
                        int x, z;
                        switch (side) {
                            case Room.N -> { x = px + a; z = pz + d; }
                            case Room.E -> { x = px + size - 1 - d; z = pz + a; }
                            case Room.S -> { x = px + a; z = pz + size - 1 - d; }
                            default -> { x = px + d; z = pz + a; }
                        }
                        action.accept(new BlockPos(x, oy + y, z));
                        n++;
                    }
                }
            }
            return n;
        }
    }
}
