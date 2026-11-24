package fr.serveur.rpg.economy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.serveur.rpg.SurvieRpg;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Journal des transactions : une ligne JSON par mouvement d'argent, dans
 * world/survierpg/economie/transactions-AAAA-MM-JJ.jsonl (horodatage, joueur, source, montant, solde avant/après).
 * Sert à l'audit et à l'annulation (/gm economie annuler).
 */
public final class Ledger {
    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public record Entry(long id, LocalDateTime time, UUID player, String name, String source, long amount, long before, long after, String detail) {}

    private Ledger() {}

    public static Path dir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("survierpg").resolve("economie");
    }

    public static void write(MinecraftServer server, Entry e) {
        JsonObject o = new JsonObject();
        o.addProperty("id", e.id());
        o.addProperty("t", e.time().format(TIME));
        o.addProperty("joueur", e.player().toString());
        o.addProperty("nom", e.name());
        o.addProperty("source", e.source());
        o.addProperty("montant", e.amount());
        o.addProperty("avant", e.before());
        o.addProperty("apres", e.after());
        if (e.detail() != null && !e.detail().isEmpty()) o.addProperty("detail", e.detail());
        Path file = dir(server).resolve("transactions-" + e.time().toLocalDate() + ".jsonl");
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(GSON.toJson(o));
                w.newLine();
            }
        } catch (IOException ex) {
            SurvieRpg.LOGGER.error("Journal des transactions : écriture impossible ({})", file, ex);
        }
    }

    /** Transactions des {@code days} derniers jours (les plus récentes à la fin). */
    public static List<Entry> read(MinecraftServer server, int days) {
        List<Entry> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            Path file = dir(server).resolve("transactions-" + today.minusDays(i) + ".jsonl");
            if (!Files.exists(file)) continue;
            try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                lines.forEach(line -> {
                    try {
                        JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                        out.add(new Entry(o.get("id").getAsLong(), LocalDateTime.parse(o.get("t").getAsString(), TIME),
                                UUID.fromString(o.get("joueur").getAsString()), o.get("nom").getAsString(), o.get("source").getAsString(),
                                o.get("montant").getAsLong(), o.get("avant").getAsLong(), o.get("apres").getAsLong(),
                                o.has("detail") ? o.get("detail").getAsString() : ""));
                    } catch (Exception ignored) {
                        // ligne abîmée : on l'ignore
                    }
                });
            } catch (IOException ex) {
                SurvieRpg.LOGGER.error("Journal des transactions : lecture impossible ({})", file, ex);
            }
        }
        return out;
    }
}
