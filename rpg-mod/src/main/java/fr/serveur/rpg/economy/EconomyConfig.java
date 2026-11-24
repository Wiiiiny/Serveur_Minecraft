package fr.serveur.rpg.economy;

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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Réglages de l'économie automatisée : config/survierpg/economie.json (toutes les valeurs sont des points de départ
 * à équilibrer). Après une modification à la main : /gm recharger.
 */
public final class EconomyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("survierpg").resolve("economie.json");

    public static final class Data {
        /** Métiers payés à chaque action : pièces par point d'XP de métier (0,05 = 1 pièce pour 20 XP). */
        public double metierPiecesParXp = 0.05;

        /**
         * Plafond de gains par joueur et par jour, par source (0 = pas de plafond). Au-delà : rendement dégressif
         * ({@link #rendementApresPlafond} jusqu'au double du plafond, puis {@link #rendementApresDouble}).
         */
        public Map<String, Integer> plafondsJour = new LinkedHashMap<>();
        public double rendementApresPlafond = 0.5, rendementApresDouble = 0.1;

        /** Hôtel des ventes : taxe sur chaque vente, durée d'une annonce, prix plancher / plafond (fois le prix de la boutique). */
        public double taxeHdv = 0.05;
        public int dureeAnnonceJours = 7;
        public double hdvPrixPlancher = 0.25, hdvPrixPlafond = 4.0;

        /**
         * Virements entre joueurs : illimités (décision du cahier des charges), sinon plafonnés à virementPlafondJour par jour ;
         * délai après la première connexion (heures).
         */
        public boolean virementsIllimites = true;
        public long virementPlafondJour = 1000;
        public int virementDelaiNouveauHeures = 24;
        /** Banque : pièces physiques au plus par dépôt ou retrait. */
        public int conversionMaxParTransaction = 2304;

        /** Boutique : le serveur vend {@code ecartAchatVente} plus cher qu'il ne rachète ; le stock revient vers sa cible. */
        public double ecartAchatVente = 0.25, retourStockParHeure = 0.05, variationPrixMaxParTransaction = 0.10;

        /** Régulateur (toutes les heures) : masse monétaire visée par joueur actif, tolérance, pas et bornes des multiplicateurs. */
        public long masseCibleParJoueurActif = 1500;
        public double regulateurTolerance = 0.20, regulateurPas = 0.02, regulateurMin = 0.70, regulateurMax = 1.30;
        /** Joueur actif = connecté depuis moins de N jours. */
        public int joueurActifJours = 7;

        /** Détection d'anomalie : gains de la dernière heure supérieurs à N fois la médiane (et à un minimum) → compte gelé. */
        public double anomalieFacteurMediane = 10;
        public long anomalieGainMinimum = 300;

        /** Sauvegarde des soldes toutes les N minutes ; nombre de sauvegardes gardées. */
        public int sauvegardeMinutes = 15, sauvegardesGardees = 96;
    }

    private static Data data = defaults();

    private EconomyConfig() {}

    public static Data get() {
        return data;
    }

    /** Plafond du jour pour cette source (0 = aucun). */
    public static int cap(String source) {
        Integer v = data.plafondsJour.get(source);
        return v == null ? 0 : v;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    Data loaded = GSON.fromJson(r, Data.class);
                    if (loaded != null) data = loaded;
                }
                // Nouvelles sources ajoutées par une mise à jour : on complète sans écraser les réglages.
                defaults().plafondsJour.forEach(data.plafondsJour::putIfAbsent);
            } else {
                data = defaults();
            }
            save();
        } catch (Exception e) {
            SurvieRpg.LOGGER.error("Impossible de lire {} : on garde la config précédente", FILE, e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (IOException e) {
            SurvieRpg.LOGGER.error("Impossible d'écrire {}", FILE, e);
        }
    }

    private static Data defaults() {
        Data d = new Data();
        d.plafondsJour.put(Economy.Source.METIER.key, 150);
        d.plafondsJour.put(Economy.Source.QUETE.key, 400);
        d.plafondsJour.put(Economy.Source.EVENEMENT.key, 0);
        d.plafondsJour.put(Economy.Source.PRIME.key, 0);
        d.plafondsJour.put(Economy.Source.RACHAT.key, 300);
        d.plafondsJour.put(Economy.Source.RAID.key, 800);
        d.plafondsJour.put(Economy.Source.COFFRE.key, 600);
        d.plafondsJour.put(Economy.Source.NIVEAU.key, 0);
        return d;
    }
}
