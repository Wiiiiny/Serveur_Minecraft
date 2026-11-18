package fr.serveur.nightfall;

/** Réglages du mod. Modifie ces valeurs puis recompile pour équilibrer. */
public final class NightfallConfig {
    private NightfallConfig() {}

    // --- Nuit -------------------------------------------------------------
    /** Distance de vision (blocs) dans la brume de nuit, sans lanterne. */
    public static final float NIGHT_FOG_DISTANCE = 10.0f;
    /** Distance de vision (blocs) dans la brume de nuit, lanterne en main. */
    public static final float LANTERN_FOG_DISTANCE = 16.0f;
    /** Niveau de Lenteur la nuit sans lanterne (0 = Lenteur I, 1 = Lenteur II). */
    public static final int NIGHT_SLOWNESS_AMPLIFIER = 0;

    // --- Jour -------------------------------------------------------------
    /** Intervalle entre deux dégâts de lumière, en ticks (20 ticks = 1 s). */
    public static final int SUN_DAMAGE_INTERVAL_TICKS = 100;
    /** Dégâts infligés à chaque fois (1.0 = un demi-cœur). */
    public static final float SUN_DAMAGE_AMOUNT = 1.0f;
    /** Les dégâts de lumière ne descendent jamais sous cette vie (4.0 = 2 cœurs). */
    public static final float SUN_DAMAGE_MIN_HEALTH = 4.0f;

    // --- Cycle (heure du monde, 0..24000) ---------------------------------
    /** Début du crépuscule : la brume commence à monter. */
    public static final long DUSK_START = 12000;
    /** Nuit complète (lenteur + brume maximale). */
    public static final long NIGHT_START = 13000;
    /** Fin de la nuit complète : la brume commence à se lever. */
    public static final long NIGHT_END = 23000;
}
