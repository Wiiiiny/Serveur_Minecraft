package fr.serveur.rpg.raid;

import fr.serveur.rpg.SurvieRpg;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Une salle du raid, lue depuis un schematic. Conventions pour construire une salle :
 * - empreinte de 16 × 16 (une cellule) ou 32 × 32 (quatre cellules), le sol sur la couche la plus basse ;
 * - une porte = ouverture au milieu d'un côté (blocs 7 et 8 de chaque trançon de 16), libre sur 2 de haut au-dessus du sol ;
 * - un coffre (ou tonneau) = coffre à butin ; un coffre piégé = toujours un piège ;
 * - un panneau dont la 1re ligne est [arrivee], [statue], [voleur], [boss], [extraction], [portail] ou [tresor] = marqueur
 *   (le panneau disparaît à la génération).
 */
public final class Room {
    public enum Ring { EXTERIEUR, INTERMEDIAIRE, COEUR }

    public enum Kind { NORMALE, ARRIVEE, ARENE, EXTRACTION }

    /** Côtés : nord (z = 0), est (x = max), sud (z = max), ouest (x = 0). */
    public static final int N = 0, E = 1, S = 2, W = 3;

    public record Marker(String type, int x, int y, int z) {}

    public final String name;
    public final Ring ring;
    public final Kind kind;
    public final Schematic schem;
    /** Taille en cellules (1 = 16 × 16, 2 = 32 × 32). */
    public final int cells;
    /** Portes naturelles : doors[côté][tronçon]. */
    public final boolean[][] doors;
    public final List<Marker> markers;

    private Room(String name, Ring ring, Kind kind, Schematic schem, int cells, boolean[][] doors, List<Marker> markers) {
        this.name = name;
        this.ring = ring;
        this.kind = kind;
        this.schem = schem;
        this.cells = cells;
        this.doors = doors;
        this.markers = markers;
    }

    public int size(int cellSize) {
        return cells * cellSize;
    }

    public static Room load(Path file, Ring ring, int cellSize) throws java.io.IOException {
        Schematic s = Schematic.read(file);
        int cells = Math.max(s.width, s.length) > cellSize ? 2 : 1;
        int size = cells * cellSize;
        List<Marker> markers = new ArrayList<>();
        for (int y = 0; y < s.height; y++) {
            for (int z = 0; z < Math.min(size, s.length); z++) {
                for (int x = 0; x < Math.min(size, s.width); x++) {
                    BlockState st = s.get(x, y, z);
                    if (st.getBlock() instanceof AbstractSignBlock) {
                        String text = signText(s.blockEntity(x, y, z));
                        if (text.startsWith("[") && text.contains("]")) {
                            markers.add(new Marker(text.substring(1, text.indexOf(']')).trim().toLowerCase(Locale.ROOT)
                                    .replace("é", "e").replace("è", "e").replace("ê", "e"), x, y, z));
                        }
                    } else if (st.getBlock() instanceof TrappedChestBlock) {
                        markers.add(new Marker("piege", x, y, z));
                    } else if (st.getBlock() instanceof ChestBlock || st.getBlock() instanceof BarrelBlock) {
                        markers.add(new Marker("coffre", x, y, z));
                    }
                }
            }
        }
        Kind kind = Kind.NORMALE;
        for (Marker m : markers) {
            if (m.type().equals("extraction")) kind = Kind.EXTRACTION;
            else if (m.type().equals("boss") && kind != Kind.EXTRACTION) kind = Kind.ARENE;
            else if (m.type().equals("arrivee") && kind == Kind.NORMALE) kind = Kind.ARRIVEE;
        }
        boolean[][] doors = new boolean[4][cells];
        for (int side = 0; side < 4; side++) {
            for (int k = 0; k < cells; k++) doors[side][k] = isOpen(s, side, k, size, cellSize);
        }
        String name = file.getFileName().toString().replace(".schem", "");
        if (s.width != s.length || (s.width != cellSize && s.width != 2 * cellSize)) {
            SurvieRpg.LOGGER.warn("Salle {} : {} × {} au lieu de {} × {} ou {} × {} (complétée / coupée)", name, s.width, s.length,
                    cellSize, cellSize, 2 * cellSize, 2 * cellSize);
        }
        return new Room(name, ring, kind, s, cells, doors, markers);
    }

    /** Une porte : les deux blocs du milieu du tronçon sont libres juste au-dessus du sol (2 de haut). */
    private static boolean isOpen(Schematic s, int side, int k, int size, int cellSize) {
        int a = k * cellSize + cellSize / 2 - 1;
        for (int along = a; along <= a + 1; along++) {
            for (int y = 1; y <= 2; y++) {
                int x, z;
                switch (side) {
                    case N -> { x = along; z = 0; }
                    case E -> { x = size - 1; z = along; }
                    case S -> { x = along; z = size - 1; }
                    default -> { x = 0; z = along; }
                }
                if (!passable(s.get(x, y, z))) return false;
            }
        }
        return true;
    }

    static boolean passable(BlockState st) {
        return st.isAir() || st.isIn(BlockTags.DOORS) || st.isIn(BlockTags.FENCE_GATES) || st.getBlock() instanceof AbstractSignBlock;
    }

    private static String signText(NbtCompound nbt) {
        if (nbt == null) return "";
        try {
            if (nbt.contains("front_text", NbtElement.COMPOUND_TYPE)) {
                NbtList lines = nbt.getCompound("front_text").getList("messages", NbtElement.STRING_TYPE);
                for (int i = 0; i < lines.size(); i++) {
                    String t = lineText(lines.getString(i));
                    if (!t.isBlank()) return t.trim();
                }
            }
            for (int i = 1; i <= 4; i++) {
                String t = lineText(nbt.getString("Text" + i));
                if (!t.isBlank()) return t.trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String lineText(String json) {
        if (json == null || json.isEmpty()) return "";
        try {
            Text t = Text.Serializer.fromJson(json);
            return t == null ? json : t.getString();
        } catch (Exception e) {
            return json;
        }
    }

    // ------------------------------------------------------------------ rotation

    /** Rotation d'un quart de tour vers la droite, appliquée {@code r} fois, à une position de bloc (x, z) d'une salle de taille {@code size}. */
    public static int[] rotate(int x, int z, int r, int size) {
        for (int i = 0; i < (r & 3); i++) {
            int nx = size - 1 - z;
            z = x;
            x = nx;
        }
        return new int[]{x, z};
    }

    public static BlockRotation rotation(int r) {
        return switch (r & 3) {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    /** Côté et tronçon d'une porte après rotation : {côté, tronçon}. */
    public static int[] rotateDoor(int side, int k, int r, int cells) {
        for (int i = 0; i < (r & 3); i++) {
            switch (side) {
                case N -> side = E;
                case E -> { side = S; k = cells - 1 - k; }
                case S -> side = W;
                default -> { side = N; k = cells - 1 - k; }
            }
        }
        return new int[]{side, k};
    }

    public static BlockState air() {
        return Blocks.AIR.getDefaultState();
    }
}
