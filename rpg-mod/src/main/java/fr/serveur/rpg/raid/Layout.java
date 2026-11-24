package fr.serveur.rpg.raid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Plan d'une partie : quelles salles, où, dans quel sens. Grille de cellules carrées autour du centre (0, 0) :
 * cœur au milieu (salle d'extraction), anneau intermédiaire, anneau extérieur (salles d'arrivée).
 * Un arbre couvrant garantit un chemin entre toutes les salles (donc de chaque arrivée à l'extraction) ;
 * une porte qu'aucune salle ne possède est percée, une porte qui donne sur un mur est bouchée.
 */
public final class Layout {
    /** Une salle posée : cellule de départ (coin nord-ouest), rotation, portes percées et bouchées (côté × 2 + tronçon). */
    public static final class Placement {
        public final Room room;
        public final int gx, gz;
        public int rot;
        public final Set<Integer> carve = new HashSet<>(), seal = new HashSet<>();
        final Set<Integer> required = new HashSet<>();

        Placement(Room room, int gx, int gz) {
            this.room = room;
            this.gx = gx;
            this.gz = gz;
        }

        public int cells() {
            return room.cells;
        }

        /** Portes naturelles après rotation. */
        Set<Integer> naturalDoors(Room r, int rot) {
            Set<Integer> out = new HashSet<>();
            for (int side = 0; side < 4; side++) {
                for (int k = 0; k < r.cells; k++) {
                    if (!r.doors[side][k]) continue;
                    int[] d = Room.rotateDoor(side, k, rot, r.cells);
                    out.add(key(d[0], d[1]));
                }
            }
            return out;
        }
    }

    public static int key(int side, int seg) {
        return side * 2 + seg;
    }

    public final int n;
    public final List<Placement> placements = new ArrayList<>();
    private final int[][] owner;

    private Layout(int n) {
        this.n = n;
        owner = new int[n][n];
        for (int[] row : owner) java.util.Arrays.fill(row, -1);
    }

    public Room.Ring ring(int gx, int gz) {
        RaidConfig.Data c = RaidConfig.get();
        double mid = (n - 1) / 2.0;
        double d = Math.max(Math.abs(gx - mid), Math.abs(gz - mid));
        if (d <= c.rayonCoeur) return Room.Ring.COEUR;
        if (d <= c.rayonIntermediaire) return Room.Ring.INTERMEDIAIRE;
        return Room.Ring.EXTERIEUR;
    }

    /** @return le plan, ou une exception avec un message lisible par le GM. */
    public static Layout generate(Random random) {
        RaidConfig.Data c = RaidConfig.get();
        Layout l = new Layout(Math.max(4, c.tailleZone / c.cellule));
        int n = l.n;

        // 1. Salle d'extraction au centre.
        List<Room> extraction = RoomLibrary.all(Room.Kind.EXTRACTION);
        if (extraction.isEmpty()) throw new IllegalStateException("Aucune salle d'extraction (panneau [extraction]) : /gm raid exemples");
        Room ex = pick(extraction, random);
        int start = (n - ex.cells) / 2;
        l.place(ex, start, start);

        // 2. Arènes (cœur puis intermédiaire) et salles d'arrivée (extérieur).
        l.placeSome(Room.Kind.ARENE, Room.Ring.COEUR, c.arenesCoeur, random);
        l.placeSome(Room.Kind.ARENE, Room.Ring.INTERMEDIAIRE, c.arenesIntermediaires, random);
        l.placeSome(Room.Kind.ARRIVEE, Room.Ring.EXTERIEUR, c.sallesArrivee, random);
        if (l.placements.stream().noneMatch(p -> p.room.kind == Room.Kind.ARRIVEE)) {
            throw new IllegalStateException("Aucune salle d'arrivée (panneau [arrivee]) : /gm raid exemples");
        }

        // 3. Remplissage : salles normales de l'anneau de chaque cellule (parfois une grande salle 2 × 2).
        List<int[]> cells = new ArrayList<>();
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) cells.add(new int[]{x, z});
        Collections.shuffle(cells, random);
        for (int[] cell : cells) {
            int x = cell[0], z = cell[1];
            if (l.owner[x][z] >= 0) continue;
            Room.Ring ring = l.ring(x, z);
            List<Room> pool = new ArrayList<>(RoomLibrary.rooms(ring, Room.Kind.NORMALE));
            if (pool.isEmpty()) pool.addAll(RoomLibrary.all(Room.Kind.NORMALE));
            if (pool.isEmpty()) pool.addAll(RoomLibrary.rooms(ring));
            if (pool.isEmpty()) throw new IllegalStateException("Aucune salle pour l'anneau " + RoomLibrary.folder(ring));
            List<Room> big = pool.stream().filter(r -> r.cells == 2).toList();
            List<Room> small = pool.stream().filter(r -> r.cells == 1).toList();
            if (small.isEmpty()) small = RoomLibrary.all(Room.Kind.NORMALE).stream().filter(r -> r.cells == 1).toList();
            if (!big.isEmpty() && random.nextInt(4) == 0 && l.free(x, z, 2)) l.place(pick(big, random), x, z);
            else if (!small.isEmpty()) l.place(pick(small, random), x, z);
            else if (!big.isEmpty() && l.free(x, z, 2)) l.place(pick(big, random), x, z);
            // sinon la cellule reste vide : elle sera remplie de pierre (aucune salle de 16 × 16 disponible)
        }

        l.connect(random);
        l.chooseRooms(random);
        return l;
    }

    private static <T> T pick(List<T> list, Random random) {
        return list.get(random.nextInt(list.size()));
    }

    private boolean free(int x, int z, int size) {
        if (x + size > n || z + size > n) return false;
        for (int i = 0; i < size; i++) for (int j = 0; j < size; j++) if (owner[x + i][z + j] >= 0) return false;
        return true;
    }

    private void place(Room room, int x, int z) {
        Placement p = new Placement(room, x, z);
        placements.add(p);
        for (int i = 0; i < room.cells; i++) for (int j = 0; j < room.cells; j++) owner[x + i][z + j] = placements.size() - 1;
    }

    /** Cellules restées sans salle (remplies de pierre à la génération). */
    public List<int[]> emptyCells() {
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) if (owner[x][z] < 0) out.add(new int[]{x, z});
        return out;
    }

    private void placeSome(Room.Kind kind, Room.Ring ring, int count, Random random) {
        List<Room> pool = new ArrayList<>(RoomLibrary.rooms(ring, kind));
        if (pool.isEmpty()) pool.addAll(RoomLibrary.all(kind));
        if (pool.isEmpty() || count <= 0) return;
        List<int[]> spots = new ArrayList<>();
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) if (ring(x, z) == ring) spots.add(new int[]{x, z});
        Collections.shuffle(spots, random);
        int placed = 0;
        for (int[] s : spots) {
            if (placed >= count) break;
            Room r = pick(pool, random);
            if (!free(s[0], s[1], r.cells) || tooClose(s[0], s[1], kind)) continue;
            place(r, s[0], s[1]);
            placed++;
        }
    }

    /** Évite que deux salles du même type spécial soient collées. */
    private boolean tooClose(int x, int z, Room.Kind kind) {
        for (Placement p : placements) {
            if (p.room.kind == kind && Math.abs(p.gx - x) + Math.abs(p.gz - z) < 4) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ portes

    /** Arbre couvrant aléatoire entre salles voisines (Kruskal), plus quelques passages en plus pour faire des boucles. */
    private void connect(Random random) {
        record Slot(int a, int b, int ax, int az, int dir) {}
        List<Slot> slots = new ArrayList<>();
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                int a = owner[x][z];
                if (x + 1 < n && owner[x + 1][z] != a) slots.add(new Slot(a, owner[x + 1][z], x, z, Room.E));
                if (z + 1 < n && owner[x][z + 1] != a) slots.add(new Slot(a, owner[x][z + 1], x, z, Room.S));
            }
        }
        Collections.shuffle(slots, random);
        int[] parent = new int[placements.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        Set<Long> linkedPairs = new HashSet<>();
        double extra = RaidConfig.get().passagesEnPlus;
        for (Slot s : slots) {
            if (s.a() < 0 || s.b() < 0) continue;
            int ra = find(parent, s.a()), rb = find(parent, s.b());
            long pair = Math.min(s.a(), s.b()) * 100_000L + Math.max(s.a(), s.b());
            boolean take = ra != rb || (!linkedPairs.contains(pair) && random.nextDouble() < extra);
            if (!take) continue;
            parent[ra] = rb;
            linkedPairs.add(pair);
            Placement pa = placements.get(s.a()), pb = placements.get(s.b());
            int bx = s.ax() + (s.dir() == Room.E ? 1 : 0), bz = s.az() + (s.dir() == Room.S ? 1 : 0);
            pa.required.add(key(s.dir(), segment(pa, s.ax(), s.az(), s.dir())));
            int opposite = (s.dir() + 2) % 4;
            pb.required.add(key(opposite, segment(pb, bx, bz, opposite)));
        }
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) i = parent[i] = parent[parent[i]];
        return i;
    }

    private static int segment(Placement p, int cx, int cz, int side) {
        int k = (side == Room.N || side == Room.S) ? cx - p.gx : cz - p.gz;
        return Math.max(0, Math.min(p.cells() - 1, k));
    }

    /** Choisit la salle (parmi celles du même anneau, type et taille) et la rotation qui couvrent le mieux les portes demandées. */
    private void chooseRooms(Random random) {
        for (Placement p : placements) {
            List<Room> candidates = new ArrayList<>();
            if (p.room.kind == Room.Kind.NORMALE) {
                for (Room r : RoomLibrary.rooms(p.room.ring, Room.Kind.NORMALE)) if (r.cells == p.cells()) candidates.add(r);
            }
            if (candidates.isEmpty()) candidates.add(p.room);
            Collections.shuffle(candidates, random);
            Room best = p.room;
            int bestRot = 0, bestScore = Integer.MIN_VALUE;
            for (Room r : candidates) {
                for (int rot = 0; rot < 4; rot++) {
                    Set<Integer> nat = p.naturalDoors(r, rot);
                    int covered = 0, extra = 0;
                    for (int d : p.required) if (nat.contains(d)) covered++;
                    for (int d : nat) if (!p.required.contains(d)) extra++;
                    int score = covered * 10 - extra + random.nextInt(2);
                    if (score > bestScore) {
                        bestScore = score;
                        best = r;
                        bestRot = rot;
                    }
                }
            }
            p.rot = bestRot;
            if (best != p.room) {
                Placement q = new Placement(best, p.gx, p.gz);
                q.rot = bestRot;
                q.required.addAll(p.required);
                placements.set(placements.indexOf(p), q);
            }
        }
        // Portes : percer celles qui manquent, boucher celles qui donnent sur un mur (ou hors de la zone).
        Map<Integer, Set<Integer>> natural = new HashMap<>();
        for (int i = 0; i < placements.size(); i++) natural.put(i, placements.get(i).naturalDoors(placements.get(i).room, placements.get(i).rot));
        for (int i = 0; i < placements.size(); i++) {
            Placement p = placements.get(i);
            Set<Integer> nat = natural.get(i);
            for (int d : p.required) if (!nat.contains(d)) p.carve.add(d);
            for (int d : nat) {
                if (p.required.contains(d)) continue;
                int side = d / 2, seg = d % 2;
                int[] other = neighborCell(p, side, seg);
                boolean keep = false;
                if (other != null) {
                    int j = owner[other[0]][other[1]];
                    if (j >= 0 && j != i) {
                        Placement q = placements.get(j);
                        int opp = (side + 2) % 4;
                        int qd = key(opp, segment(q, other[0], other[1], opp));
                        keep = natural.get(j).contains(qd) || q.required.contains(qd);
                    }
                }
                if (!keep) p.seal.add(d);
            }
        }
    }

    /** Cellule de l'autre côté d'une porte (null si hors de la grille). */
    private int[] neighborCell(Placement p, int side, int seg) {
        int x, z;
        switch (side) {
            case Room.N -> { x = p.gx + seg; z = p.gz - 1; }
            case Room.E -> { x = p.gx + p.cells(); z = p.gz + seg; }
            case Room.S -> { x = p.gx + seg; z = p.gz + p.cells(); }
            default -> { x = p.gx - 1; z = p.gz + seg; }
        }
        return x < 0 || z < 0 || x >= n || z >= n ? null : new int[]{x, z};
    }
}
