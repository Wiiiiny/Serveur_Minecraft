package fr.serveur.nightfall;

/** Réglages du mod. Modifie ces valeurs puis recompile pour équilibrer. */
public final class NightfallConfig {
    private NightfallConfig() {}

    // --- Nuit : brume -----------------------------------------------------
    /** Distance de vision (blocs) dans la brume de nuit, sans lanterne. */
    public static final float NIGHT_FOG_DISTANCE = 6.0f;
    /** Distance de vision (blocs) dans la brume de nuit, lanterne en main. */
    public static final float LANTERN_FOG_DISTANCE = 16.0f;
    /** Couleur de la brume sans lanterne (R, G, B entre 0 et 1) : presque noir. */
    public static final float[] NIGHT_FOG_COLOR = {0.012f, 0.012f, 0.02f};
    /** Couleur de la brume avec lanterne : noir légèrement chaud. */
    public static final float[] LANTERN_FOG_COLOR = {0.05f, 0.035f, 0.02f};
    /** Assombrissement des bords de l'écran la nuit (0 = rien, 1 = très fort). */
    public static final float NIGHT_VIGNETTE = 0.8f;

    // --- Nuit : lenteur ---------------------------------------------------
    /** Niveau de Lenteur la nuit sans lanterne (0 = Lenteur I, 1 = Lenteur II). */
    public static final int NIGHT_SLOWNESS_AMPLIFIER = 0;

    // --- Nuit : ambiance horreur (chances par tick, 20 ticks = 1 s) --------
    /** Yeux qui t'observent dans la brume : 1 chance sur N par tick (~25 s). */
    public static final int EYES_CHANCE = 500;
    /** Bruits de pas derrière toi : 1 chance sur N par tick (~60 s). */
    public static final int FOOTSTEPS_CHANCE = 1200;
    /** Bruits de grotte lointains : 1 chance sur N par tick (~90 s). */
    public static final int CAVE_SOUND_CHANCE = 1800;
    /** Nombre moyen de particules de cendre par tick autour du joueur. */
    public static final int ASH_PER_TICK = 4;

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
