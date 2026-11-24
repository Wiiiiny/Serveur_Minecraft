package fr.serveur.rpg.faction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.serveur.rpg.SurvieRpg;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Réglages des factions : config/survierpg/factions.json (relu par /gm recharger). */
public final class FactionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("survierpg").resolve("factions.json");

    public static final class Article {
        /** Identifiant de l'objet, ou « extension » (agrandit tous les territoires de 2 blocs, prix × nombre d'extensions + 1). */
        public String objet = "minecraft:stone";
        public int quantite = 1;
        public long prix = 10;
        /** Niveau de faction requis. */
        public int niveau = 1;

        public Article() {}

        Article(String objet, int quantite, long prix, int niveau) {
            this.objet = objet;
            this.quantite = quantite;
            this.prix = prix;
            this.niveau = niveau;
        }
    }

    public static final class Mission {
        /** tuer (monstres), coffre (coffres cachés), quete (quêtes du jour), pierre (Pierre obscure), eclat (Éclats de Lumière brisés). */
        public String type = "tuer";
        public int nombre = 50;
        public int xp = 150;
        public long pieces = 100;
        public String texte = "";

        public Mission() {}

        Mission(String type, int nombre, int xp, long pieces, String texte) {
            this.type = type;
            this.nombre = nombre;
            this.xp = xp;
            this.pieces = pieces;
            this.texte = texte;
        }
    }

    public static final class Data {
        public int nomMin = 3, nomMax = 20;
        /** Territoire : rayon 20 au niveau 1, +5 par niveau ; 50 blocs au moins entre deux factions différentes. */
        public int rayonBase = 20, rayonParNiveau = 5, distanceMinEntreFactions = 50;
        /** XP pour passer du niveau n au niveau n+1 : xpBase + xpParNiveau × (n − 1). Pas de niveau maximum, pas de prestige. */
        public int xpBase = 500, xpParNiveau = 250;
        /** Pièces versées au trésor de la faction à chaque niveau : piecesParNiveau × niveau atteint. */
        public long piecesParNiveau = 100;
        public double xpMonstre = 1, xpBoss = 15, xpCoffreCache = 20, xpCoffreEnGroupe = 10, xpQuete = 10, xpNiveauMetier = 10,
                xpGuerreGagnee = 500, xpParPieceDonnee = 0.05, xpParPieceDepensee = 0.05, xpEclat = 30;
        /** Cœur de faction : points de vie (1 coup = 1 point), et 1 point regagné toutes les N secondes sans coup. */
        public int coeurPointsDeVie = 60, coeurRegenSecondes = 20;
        /** Guerre : début après N minutes ; défaite : part de l'XP du niveau perdue (au plus 1 niveau), points de classement, paix. */
        public int delaiGuerreMinutes = 5;
        public double perteXpDefaite = 0.20, paixJours = 3;
        public int pointsVictoire = 100, pointsDefaite = 50;
        /** Votes (exclusion, élection du chef, abandon de territoire) : ouverts N heures, adoptés à plus de 50 % des membres. */
        public double voteHeures = 24, delaiEntreElectionsHeures = 24;
        /** Chef considéré comme inactif après N jours (affiché pour lancer une élection). */
        public int chefInactifJours = 7;
        /** Téléportation à la base : N secondes immobile, recharge en minutes. */
        public int teleportBaseSecondes = 5, teleportBaseRechargeMinutes = 10;
        /** Incidents entre factions (pas de guerre automatique) : un message par paire de factions au plus toutes les N minutes. */
        public int incidentIntervalleMinutes = 5;
        public List<Article> boutique = new ArrayList<>();
        public List<Mission> missions = new ArrayList<>();
        public int missionsParJour = 3;
    }

    private static Data data = defaults();

    private FactionConfig() {}

    public static Data get() {
        return data;
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                Data d = GSON.fromJson(r, Data.class);
                if (d != null) data = d;
            } catch (Exception e) {
                SurvieRpg.LOGGER.error("factions.json illisible, valeurs par défaut utilisées", e);
                data = defaults();
            }
        } else {
            data = defaults();
        }
        if (data.boutique == null || data.boutique.isEmpty()) data.boutique = defaults().boutique;
        if (data.missions == null || data.missions.isEmpty()) data.missions = defaults().missions;
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (IOException e) {
            SurvieRpg.LOGGER.error("Écriture de factions.json impossible", e);
        }
    }

    private static Data defaults() {
        Data d = new Data();
        d.boutique = new ArrayList<>(List.of(
                new Article("survierpg:etendard", 1, 500, 1),
                new Article("extension", 1, 1000, 2),
                new Article("minecraft:stone_bricks", 64, 60, 1),
                new Article("minecraft:spruce_planks", 64, 40, 1),
                new Article("minecraft:glass", 32, 40, 1),
                new Article("minecraft:lantern", 8, 40, 1),
                new Article("minecraft:white_banner", 4, 30, 1),
                new Article("minecraft:cooked_beef", 32, 50, 1),
                new Article("minecraft:arrow", 64, 50, 2),
                new Article("minecraft:shield", 1, 50, 2),
                new Article("minecraft:golden_carrot", 16, 60, 2),
                new Article("minecraft:crossbow", 1, 80, 3),
                new Article("minecraft:golden_apple", 2, 120, 3),
                new Article("minecraft:iron_block", 4, 300, 4),
                new Article("survierpg:larme_de_vie", 1, 150, 4),
                new Article("minecraft:diamond", 4, 400, 5),
                new Article("survierpg:detecteur_ombre", 1, 600, 6),
                new Article("minecraft:netherite_scrap", 1, 800, 8),
                new Article("survierpg:eclat_lumiere", 1, 2000, 10)));
        d.missions = new ArrayList<>(List.of(
                new Mission("tuer", 60, 150, 100, "Abattre 60 monstres"),
                new Mission("tuer", 150, 300, 250, "Abattre 150 monstres"),
                new Mission("coffre", 2, 200, 150, "Ouvrir 2 coffres cachés"),
                new Mission("coffre", 5, 400, 300, "Ouvrir 5 coffres cachés"),
                new Mission("quete", 6, 200, 150, "Terminer 6 quêtes du jour"),
                new Mission("pierre", 40, 150, 120, "Extraire 40 Pierres obscures"),
                new Mission("eclat", 1, 250, 200, "Briser un Éclat de Lumière")));
        return d;
    }
}
