package fr.serveur.rpg.data;

import fr.serveur.rpg.jobs.Job;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Zone cubique tracée par le GM :
 * - zone de métier ({@code job} non nul) : seule ce métier peut y récolter ;
 * - zone protégée ({@code protectedZone}, ex. la mairie) : ni destruction, ni PVP.
 */
public final class Zone {
    public final String name;
    public final Job job;
    public final boolean protectedZone;
    public final String dimension;
    public final int minX, minY, minZ, maxX, maxY, maxZ;

    public Zone(String name, Job job, String dimension, BlockPos a, BlockPos b) {
        this(name, job, false, dimension,
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    /** Zone protégée (mairie...) agrandie de {@code margin} blocs dans toutes les directions. */
    public static Zone protectedArea(String name, String dimension, BlockPos a, BlockPos b, int margin) {
        return new Zone(name, null, true, dimension,
                Math.min(a.getX(), b.getX()) - margin, Math.min(a.getY(), b.getY()) - margin, Math.min(a.getZ(), b.getZ()) - margin,
                Math.max(a.getX(), b.getX()) + margin, Math.max(a.getY(), b.getY()) + margin, Math.max(a.getZ(), b.getZ()) + margin);
    }

    private Zone(String name, Job job, boolean protectedZone, String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.name = name;
        this.job = job;
        this.protectedZone = protectedZone;
        this.dimension = dimension;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    public boolean contains(World world, BlockPos pos) {
        return world.getRegistryKey().getValue().toString().equals(dimension)
                && pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    /** La zone touche-t-elle le cube centré sur {@code pos} de rayon {@code r} ? */
    public boolean intersects(World world, BlockPos pos, int r) {
        return world.getRegistryKey().getValue().toString().equals(dimension)
                && pos.getX() + r >= minX && pos.getX() - r <= maxX
                && pos.getY() + r >= minY && pos.getY() - r <= maxY
                && pos.getZ() + r >= minZ && pos.getZ() - r <= maxZ;
    }

    public String describe() {
        return name + " [" + (protectedZone ? "PROTÉGÉE" : job == null ? "-" : job.displayName) + "] " + dimension
                + " (" + minX + " " + minY + " " + minZ + ") -> (" + maxX + " " + maxY + " " + maxZ + ")";
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putString("name", name);
        if (job != null) n.putString("job", job.name());
        n.putBoolean("protected", protectedZone);
        n.putString("dim", dimension);
        n.putIntArray("box", new int[]{minX, minY, minZ, maxX, maxY, maxZ});
        return n;
    }

    public static Zone fromNbt(NbtCompound n) {
        int[] b = n.getIntArray("box");
        if (b.length != 6) return null;
        return new Zone(n.getString("name"), n.contains("job") ? Job.parse(n.getString("job")) : null,
                n.getBoolean("protected"), n.getString("dim"), b[0], b[1], b[2], b[3], b[4], b[5]);
    }
}
