package fr.serveur.rpg.sombre;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.serveur.rpg.SurvieRpg;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Journaux du monde sombre : une ligne JSON par événement, dans world/survierpg/journaux/&lt;journal&gt;-AAAA-MM-JJ.jsonl.
 * Journaux : exotiques, coffres, recompenses, lumiere, gm. Invisibles pour les joueurs.
 */
public final class Journal {
    private static final Gson GSON = new Gson();

    private Journal() {}

    public static Path dir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("survierpg").resolve("journaux");
    }

    /** {@code kv} : clés et valeurs alternées ("joueur", "Alex", "objet", "...", ...). */
    public static void write(MinecraftServer server, String journal, String event, Object... kv) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        JsonObject o = new JsonObject();
        o.addProperty("t", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        o.addProperty("evenement", event);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            String k = String.valueOf(kv[i]);
            if (v instanceof Number n) o.addProperty(k, n);
            else if (v instanceof Boolean b) o.addProperty(k, b);
            else o.addProperty(k, String.valueOf(v));
        }
        Path file = dir(server).resolve(journal + "-" + now.toLocalDate() + ".jsonl");
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(GSON.toJson(o));
                w.newLine();
            }
        } catch (IOException e) {
            SurvieRpg.LOGGER.error("Journal {} : écriture impossible", journal, e);
        }
    }
}
