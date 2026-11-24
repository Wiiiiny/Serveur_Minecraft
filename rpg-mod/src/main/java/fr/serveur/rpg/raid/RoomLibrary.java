package fr.serveur.rpg.raid;

import fr.serveur.rpg.SurvieRpg;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Les salles disponibles, lues dans config/survierpg/raid/salles/{exterieur,intermediaire,coeur}/. */
public final class RoomLibrary {
    private static final Map<Room.Ring, List<Room>> ROOMS = new EnumMap<>(Room.Ring.class);
    private static int height;

    private RoomLibrary() {}

    public static String folder(Room.Ring ring) {
        return switch (ring) {
            case EXTERIEUR -> "exterieur";
            case INTERMEDIAIRE -> "intermediaire";
            case COEUR -> "coeur";
        };
    }

    /** Relit tous les schematics. @return un résumé pour le GM. */
    public static String load() {
        int cell = RaidConfig.get().cellule;
        ROOMS.clear();
        height = 0;
        List<String> errors = new ArrayList<>();
        for (Room.Ring ring : Room.Ring.values()) {
            List<Room> list = new ArrayList<>();
            for (Path p : Schematic.list(RaidConfig.roomsDir().resolve(folder(ring)))) {
                try {
                    Room r = Room.load(p, ring, cell);
                    list.add(r);
                    height = Math.max(height, Math.min(64, r.schem.height));
                } catch (Exception e) {
                    errors.add(p.getFileName().toString());
                    SurvieRpg.LOGGER.error("Salle illisible : {}", p, e);
                }
            }
            ROOMS.put(ring, list);
        }
        String summary = "Salles : " + count(Room.Ring.EXTERIEUR) + " extérieur, " + count(Room.Ring.INTERMEDIAIRE) + " intermédiaire, "
                + count(Room.Ring.COEUR) + " cœur (" + kinds() + ")" + (errors.isEmpty() ? "" : " — illisibles : " + String.join(", ", errors));
        SurvieRpg.LOGGER.info("[raid] {}", summary);
        return summary;
    }

    public static List<Room> rooms(Room.Ring ring) {
        return ROOMS.getOrDefault(ring, List.of());
    }

    public static List<Room> rooms(Room.Ring ring, Room.Kind kind) {
        return rooms(ring).stream().filter(r -> r.kind == kind).toList();
    }

    /** Toutes les salles d'un type, quel que soit l'anneau. */
    public static List<Room> all(Room.Kind kind) {
        List<Room> out = new ArrayList<>();
        ROOMS.values().forEach(l -> l.stream().filter(r -> r.kind == kind).forEach(out::add));
        return out;
    }

    /** Hauteur de la plus haute salle (toutes les salles sont posées sur cette hauteur, complétée par de l'air). */
    public static int height() {
        return Math.max(8, height);
    }

    public static boolean isEmpty() {
        return ROOMS.values().stream().allMatch(List::isEmpty);
    }

    private static int count(Room.Ring ring) {
        return rooms(ring).size();
    }

    private static String kinds() {
        return all(Room.Kind.ARRIVEE).size() + " arrivée, " + all(Room.Kind.ARENE).size() + " arènes, "
                + all(Room.Kind.EXTRACTION).size() + " extraction";
    }
}
