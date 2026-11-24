package fr.serveur.rpg.raid;

import fr.serveur.rpg.SurvieRpg;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Schematic au format Sponge (.schem, versions 2 et 3), celui qu'écrit WorldEdit avec //schem save.
 * Index d'un bloc : x + z × largeur + y × largeur × longueur.
 */
public final class Schematic {
    public final int width, height, length;
    public final BlockState[] palette;
    public final int[] blocks;
    /** Données des blocs à inventaire, panneaux... par index de bloc (sans Pos ni Id). */
    public final Map<Integer, NbtCompound> blockEntities;

    public Schematic(int width, int height, int length, BlockState[] palette, int[] blocks, Map<Integer, NbtCompound> blockEntities) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.palette = palette;
        this.blocks = blocks;
        this.blockEntities = blockEntities;
    }

    public int index(int x, int y, int z) {
        return x + z * width + y * width * length;
    }

    public BlockState get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= length) return Blocks.AIR.getDefaultState();
        return palette[blocks[index(x, y, z)]];
    }

    public NbtCompound blockEntity(int x, int y, int z) {
        return blockEntities.get(index(x, y, z));
    }

    // ------------------------------------------------------------------ lecture

    public static Schematic read(Path file) throws IOException {
        NbtCompound root;
        try (InputStream in = Files.newInputStream(file)) {
            root = NbtIo.readCompressed(in);
        }
        boolean v3 = root.contains("Schematic", NbtElement.COMPOUND_TYPE);
        NbtCompound s = v3 ? root.getCompound("Schematic") : root;
        int w = s.getShort("Width") & 0xFFFF, h = s.getShort("Height") & 0xFFFF, l = s.getShort("Length") & 0xFFFF;
        NbtCompound blocksTag = v3 ? s.getCompound("Blocks") : s;
        NbtCompound paletteTag = blocksTag.getCompound("Palette");
        byte[] data = v3 ? blocksTag.getByteArray("Data") : blocksTag.getByteArray("BlockData");

        int max = 0;
        for (String k : paletteTag.getKeys()) max = Math.max(max, paletteTag.getInt(k));
        BlockState[] palette = new BlockState[max + 1];
        java.util.Arrays.fill(palette, Blocks.AIR.getDefaultState());
        for (String k : paletteTag.getKeys()) palette[paletteTag.getInt(k)] = parseState(k);

        int[] blocks = new int[w * h * l];
        int i = 0, pos = 0;
        while (pos < data.length && i < blocks.length) {
            int value = 0, shift = 0, b;
            do {
                b = data[pos++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0 && pos < data.length);
            blocks[i++] = value < palette.length ? value : 0;
        }

        Map<Integer, NbtCompound> entities = new HashMap<>();
        NbtList be = blocksTag.getList("BlockEntities", NbtElement.COMPOUND_TYPE);
        if (be.isEmpty() && !v3) be = s.getList("TileEntities", NbtElement.COMPOUND_TYPE);
        for (int k = 0; k < be.size(); k++) {
            NbtCompound e = be.getCompound(k);
            int[] p = e.getIntArray("Pos");
            if (p.length < 3) continue;
            NbtCompound d = v3 && e.contains("Data", NbtElement.COMPOUND_TYPE) ? e.getCompound("Data").copy() : e.copy();
            d.remove("Pos");
            d.remove("Id");
            d.remove("id");
            d.remove("x");
            d.remove("y");
            d.remove("z");
            entities.put(p[0] + p[2] * w + p[1] * w * l, d);
        }
        return new Schematic(w, h, l, palette, blocks, entities);
    }

    private static BlockState parseState(String text) {
        try {
            return BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), text, false).blockState();
        } catch (Exception e) {
            SurvieRpg.LOGGER.warn("Schematic : bloc inconnu « {} » remplacé par de l'air", text);
            return Blocks.AIR.getDefaultState();
        }
    }

    // ------------------------------------------------------------------ écriture (salles d'exemple)

    /** Construit un schematic bloc par bloc (utilisé pour écrire les salles d'exemple). */
    public static final class Builder {
        final int w, h, l;
        final BlockState[] states;
        final Map<BlockPos, NbtCompound> entities = new LinkedHashMap<>();

        public Builder(int w, int h, int l) {
            this.w = w;
            this.h = h;
            this.l = l;
            states = new BlockState[w * h * l];
            java.util.Arrays.fill(states, Blocks.AIR.getDefaultState());
        }

        public Builder set(int x, int y, int z, BlockState s) {
            if (x >= 0 && y >= 0 && z >= 0 && x < w && y < h && z < l) states[x + z * w + y * w * l] = s;
            return this;
        }

        public Builder fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, s);
            return this;
        }

        public Builder entity(int x, int y, int z, NbtCompound nbt) {
            entities.put(new BlockPos(x, y, z), nbt);
            return this;
        }

        public void write(Path file) throws IOException {
            Map<String, Integer> palette = new LinkedHashMap<>();
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            for (BlockState s : states) {
                String key = BlockArgumentParser.stringifyBlockState(s);
                int id = palette.computeIfAbsent(key, k -> palette.size());
                while ((id & ~0x7F) != 0) {
                    data.write((id & 0x7F) | 0x80);
                    id >>>= 7;
                }
                data.write(id);
            }
            NbtCompound root = new NbtCompound();
            root.putInt("Version", 2);
            root.putInt("DataVersion", net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            root.putShort("Width", (short) w);
            root.putShort("Height", (short) h);
            root.putShort("Length", (short) l);
            root.putIntArray("Offset", new int[]{0, 0, 0});
            root.putInt("PaletteMax", palette.size());
            NbtCompound p = new NbtCompound();
            palette.forEach(p::putInt);
            root.put("Palette", p);
            root.putByteArray("BlockData", data.toByteArray());
            NbtList be = new NbtList();
            entities.forEach((pos, nbt) -> {
                NbtCompound e = nbt.copy();
                e.putIntArray("Pos", new int[]{pos.getX(), pos.getY(), pos.getZ()});
                e.putString("Id", Registries.BLOCK.getId(states[pos.getX() + pos.getZ() * w + pos.getY() * w * l].getBlock()).toString());
                be.add(e);
            });
            root.put("BlockEntities", be);
            Files.createDirectories(file.getParent());
            // Racine nommée « Schematic », comme l'écrit WorldEdit.
            try (OutputStream os = Files.newOutputStream(file);
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(os)))) {
                out.writeByte(NbtElement.COMPOUND_TYPE);
                out.writeUTF("Schematic");
                root.write(out);
            }
        }
    }

    /** Liste les fichiers .schem d'un dossier (vide s'il n'existe pas). */
    public static List<Path> list(Path dir) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (var s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".schem")).sorted().forEach(out::add);
        } catch (IOException ignored) {
        }
        return out;
    }
}
