package fr.serveur.rpg.raid;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SignBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

/**
 * Salles d'exemple, écrites en .schem dans config/survierpg/raid/salles/ (commande /gm raid exemples, ou automatiquement
 * si le dossier est vide). Elles montrent les conventions (portes, coffres, panneaux-marqueurs) et permettent de jouer
 * tout de suite ; les salles construites par l'équipe s'ajoutent à côté (//schem save avec WorldEdit).
 */
public final class ExampleRooms {
    private static final int H = 12;
    private static final BlockState FLOOR = Blocks.POLISHED_BLACKSTONE.getDefaultState();
    private static final BlockState FLOOR2 = Blocks.BASALT.getDefaultState();
    private static final BlockState WALL = Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
    private static final BlockState WALL2 = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();
    private static final BlockState CEIL = Blocks.BLACKSTONE.getDefaultState();
    private static final BlockState LIGHT = Blocks.SHROOMLIGHT.getDefaultState();
    private static final BlockState LAVA = Blocks.LAVA.getDefaultState();
    private static final BlockState PILLAR = Blocks.POLISHED_BASALT.getDefaultState();

    private ExampleRooms() {}

    /** @return le nombre de salles écrites. */
    public static int write() throws IOException {
        Path dir = RaidConfig.roomsDir();
        Path ext = dir.resolve("exterieur"), mid = dir.resolve("intermediaire"), core = dir.resolve("coeur");
        int n = 0;
        // Anneau extérieur : ruines de forteresse.
        n += save(room(1, "NS", 1).chest(4, 1, 8).sign(11, 1, 8, "[statue]"), ext.resolve("exemple_couloir_droit.schem"));
        n += save(room(1, "NE", 2).chest(4, 1, 11), ext.resolve("exemple_couloir_angle.schem"));
        n += save(room(1, "NESW", 3).lava(6, 6, 9, 9).chest(3, 1, 3).chest(12, 1, 12), ext.resolve("exemple_carrefour.schem"));
        n += save(room(1, "NEW", 4).chest(3, 1, 12).chest(12, 1, 12).sign(8, 1, 12, "[statue]"), ext.resolve("exemple_salle_t.schem"));
        n += save(room(1, "NESW", 5).sign(8, 1, 8, "[arrivee]").chest(3, 1, 3), ext.resolve("exemple_ruines_arrivee.schem"));
        // Anneau intermédiaire : labyrinthes de basalte, lacs de lave, ponts.
        n += save(room(1, "NESW", 6).pillars().chest(12, 1, 3).sign(3, 1, 12, "[statue]").sign(12, 1, 12, "[statue]")
                .sign(3, 1, 3, "[voleur]"), mid.resolve("exemple_labyrinthe_basalte.schem"));
        n += save(room(1, "NS", 7).lava(1, 1, 14, 14).bridgeNS().chest(9, 1, 2), mid.resolve("exemple_pont_de_lave.schem"));
        n += save(room(1, "NEW", 8).pillars().chest(8, 1, 12).sign(4, 1, 8, "[statue]"), mid.resolve("exemple_galerie.schem"));
        n += save(room(2, "NESW", 9).pillars().sign(16, 1, 16, "[boss]").chest(3, 1, 3).chest(28, 1, 28), mid.resolve("exemple_arene_gardien.schem"));
        // Cœur : arène finale, salle d'extraction.
        n += save(room(1, "NESW", 10).chest(3, 1, 3).chest(12, 1, 12).sign(12, 1, 3, "[statue]").sign(3, 1, 12, "[statue]"),
                core.resolve("exemple_salle_du_coeur.schem"));
        n += save(room(2, "NESW", 11).lava(4, 4, 7, 7).lava(24, 24, 27, 27).sign(16, 1, 16, "[boss]").chest(28, 1, 3),
                core.resolve("exemple_arene_du_coeur.schem"));
        n += save(room(2, "NESW", 12).sign(16, 1, 12, "[extraction]").sign(16, 1, 20, "[portail]").sign(16, 1, 27, "[tresor]")
                .sign(5, 1, 5, "[statue]").sign(26, 1, 5, "[statue]"), core.resolve("exemple_salle_extraction.schem"));
        return n;
    }

    private static int save(B b, Path file) throws IOException {
        b.s.write(file);
        return 1;
    }

    /** Salle de base : sol, murs, plafond éclairé, portes demandées (« NESW »). */
    private static B room(int cells, String doors, long seed) {
        int size = cells * 16;
        B b = new B(size, new Random(seed));
        Schematic.Builder s = b.s;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                s.set(x, 0, z, b.r.nextInt(5) == 0 ? FLOOR2 : FLOOR);
                s.set(x, H - 1, z, (x % 6 == 3 && z % 6 == 3) ? LIGHT : CEIL);
                boolean edge = x == 0 || z == 0 || x == size - 1 || z == size - 1;
                if (edge) for (int y = 1; y < H - 1; y++) s.set(x, y, z, b.r.nextInt(6) == 0 ? WALL2 : WALL);
            }
        }
        for (char c : doors.toCharArray()) {
            int side = "NESW".indexOf(c);
            for (int k = 0; k < cells; k++) b.door(side, k);
        }
        return b;
    }

    private static final class B {
        final int size;
        final Random r;
        final Schematic.Builder s;

        B(int size, Random r) {
            this.size = size;
            this.r = r;
            this.s = new Schematic.Builder(size, H, size);
        }

        /** Ouverture de 4 de large (blocs 6 à 9 du tronçon) sur 3 de haut. */
        void door(int side, int k) {
            for (int a = k * 16 + 6; a <= k * 16 + 9; a++) {
                for (int y = 1; y <= 3; y++) {
                    switch (side) {
                        case Room.N -> s.set(a, y, 0, Room.air());
                        case Room.E -> s.set(size - 1, y, a, Room.air());
                        case Room.S -> s.set(a, y, size - 1, Room.air());
                        default -> s.set(0, y, a, Room.air());
                    }
                }
            }
        }

        B chest(int x, int y, int z) {
            s.set(x, y, z, Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH));
            return this;
        }

        B lava(int x1, int z1, int x2, int z2) {
            s.fill(x1, 0, z1, x2, 0, z2, LAVA);
            return this;
        }

        B bridgeNS() {
            s.fill(6, 0, 0, 9, 0, size - 1, Blocks.NETHER_BRICKS.getDefaultState());
            s.fill(5, 1, 1, 5, 1, size - 2, Blocks.NETHER_BRICK_FENCE.getDefaultState());
            s.fill(10, 1, 1, 10, 1, size - 2, Blocks.NETHER_BRICK_FENCE.getDefaultState());
            return this;
        }

        B pillars() {
            for (int x = 3; x < size - 3; x += 5) {
                for (int z = 3; z < size - 3; z += 5) {
                    if (r.nextInt(3) == 0 || (x >= 6 && x <= 9) || (z >= 6 && z <= 9)) continue; // garde les passages
                    s.fill(x, 1, z, x, H - 2, z, PILLAR);
                }
            }
            return this;
        }

        B sign(int x, int y, int z, String text) {
            s.set(x, y, z, Blocks.OAK_SIGN.getDefaultState().with(SignBlock.ROTATION, 0));
            NbtCompound front = new NbtCompound();
            NbtList lines = new NbtList();
            lines.add(NbtString.of("{\"text\":\"" + text + "\"}"));
            for (int i = 0; i < 3; i++) lines.add(NbtString.of("\"\""));
            front.put("messages", lines);
            front.putString("color", "black");
            front.putBoolean("has_glowing_text", false);
            NbtCompound back = front.copy();
            NbtList empty = new NbtList();
            for (int i = 0; i < 4; i++) empty.add(NbtString.of("\"\""));
            back.put("messages", empty);
            NbtCompound nbt = new NbtCompound();
            nbt.put("front_text", front);
            nbt.put("back_text", back);
            nbt.putBoolean("is_waxed", false);
            s.entity(x, y, z, nbt);
            return this;
        }
    }
}
