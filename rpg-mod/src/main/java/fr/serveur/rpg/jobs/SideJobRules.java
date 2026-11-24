package fr.serveur.rpg.jobs;

import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.events.GmEvents;
import fr.serveur.rpg.world.DayCycle;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BrewingStandBlock;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Règles des métiers supplémentaires. Les événements Fabric sont branchés ici ; les mixins
 * (pose de blocs, enclume, table d'enchantement, alambic, pêche, troc) appellent les méthodes statiques.
 */
public final class SideJobRules {
    /** Dernières positions où chaque joueur a gagné de l'XP en posant un bloc (contre « poser / casser / reposer »). */
    private static final Map<UUID, LongLinkedOpenHashSet> RECENT_PLACES = new HashMap<>();
    /** Dernier joueur qui a ouvert chaque alambic (c'est son niveau d'Alchimiste qui compte). */
    private static final Map<BrewingStandBlockEntity, UUID> BREWERS = new WeakHashMap<>();
    private static final Map<BrewingStandBlockEntity, Long> BREW_NOTIFIED = new WeakHashMap<>();
    /** Alambic en cours de mise à jour (pour savoir lequel demande s'il peut brasser). */
    public static BrewingStandBlockEntity brewingNow;
    private static final String BREWED_TAG = "survierpg_brewed";

    private SideJobRules() {}

    public static void register() {
        // Alambic réservé à l'Alchimiste, table d'enchantement réservée à l'Enchanteur.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp) || SideJobs.exempt(sp)) return ActionResult.PASS;
            if (sp.isSneaking() && !sp.getStackInHand(hand).isEmpty()) return ActionResult.PASS;
            BlockState state = world.getBlockState(hit.getBlockPos());
            Block block = state.getBlock();
            PlayerData data = RpgState.get(sp.getServer()).player(sp);
            if (block instanceof BrewingStandBlock) {
                if (data.sideJob != SideJob.ALCHIMISTE) return refuse(sp, "Seul l'Alchimiste peut utiliser l'alambic.");
                BlockEntity be = world.getBlockEntity(hit.getBlockPos());
                if (be instanceof BrewingStandBlockEntity stand) BREWERS.put(stand, sp.getUuid());
            } else if (block instanceof EnchantingTableBlock) {
                if (data.sideJob != SideJob.ENCHANTEUR) return refuse(sp, "Seul l'Enchanteur peut utiliser la table d'enchantement.");
            }
            return ActionResult.PASS;
        });

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
            if (!(killer instanceof ServerPlayerEntity sp) || killed instanceof PlayerEntity) return;
            PlayerData data = RpgState.get(sp.getServer()).player(sp);
            if (data.sideJob == SideJob.PACIFICATEUR && killed instanceof Monster) onMonsterKilled(sp, data, killed);
            else if (data.sideJob == SideJob.PECHEUR && killed instanceof AnimalEntity) {
                int xp = (int) Math.max(1, Math.round(killed.getMaxHealth() * SideJobConfig.data().pecheurXpChasseParPv));
                SideJobs.addActionXp(sp, SideJob.PECHEUR, xp);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RECENT_PLACES.remove(handler.player.getUuid()));
    }

    private static ActionResult refuse(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message).formatted(Formatting.RED), true);
        return ActionResult.FAIL;
    }

    // ------------------------------------------------------------------ Ingénieur / Constructeur

    /** Avant la pose d'un bloc : null si autorisé, sinon la raison du refus. */
    public static String checkPlacement(ServerPlayerEntity player, Block block) {
        if (SideJobs.exempt(player)) return null;
        SideJobConfig.Tier tier = SideJobConfig.reservedPlacement(block);
        if (tier == null) return null;
        PlayerData data = RpgState.get(player.getServer()).player(player);
        if (data.sideJob != tier.job()) {
            return (tier.job() == SideJob.INGENIEUR ? "Seul l'Ingénieur peut poser ce bloc de redstone."
                    : "Bloc de prestige : seul le Constructeur peut le poser.");
        }
        int level = data.side().level;
        return level >= tier.level() ? null : "Niveau " + tier.level() + " de " + tier.job().displayName + " requis.";
    }

    /** Après la pose d'un bloc : XP de l'Ingénieur et du Constructeur. */
    public static void afterPlacement(ServerPlayerEntity player, World world, BlockPos pos, Block block) {
        if (SideJobs.exempt(player)) return;
        PlayerData data = RpgState.get(player.getServer()).player(player);
        if (data.sideJob != SideJob.INGENIEUR && data.sideJob != SideJob.CONSTRUCTEUR) return;
        SideJobConfig.Tier tier = SideJobConfig.reservedPlacement(block);
        int xp;
        if (tier != null && tier.job() == data.sideJob) xp = tier.xp();
        else if (data.sideJob == SideJob.CONSTRUCTEUR) xp = SideJobConfig.data().constructeurXpBlocOrdinaire;
        else return;
        if (xp <= 0) return;
        // Pas d'XP deux fois au même endroit (poser / casser / reposer).
        LongLinkedOpenHashSet recent = RECENT_PLACES.computeIfAbsent(player.getUuid(), u -> new LongLinkedOpenHashSet());
        long key = pos.asLong() * 31 + world.getRegistryKey().getValue().hashCode();
        if (!recent.add(key)) return;
        if (recent.size() > 2048) recent.removeFirstLong();
        int given = SideJobs.capPlaceXp(player, data, xp);
        if (given > 0) SideJobs.addActionXp(player, data.sideJob, given);
    }

    // ------------------------------------------------------------------ Pacificateur

    private static void onMonsterKilled(ServerPlayerEntity player, PlayerData data, LivingEntity killed) {
        SideJobConfig.Data cfg = SideJobConfig.data();
        RpgState state = RpgState.get(player.getServer());
        if (DayCycle.isNight(player.getServer().getOverworld())) {
            long fine = fr.serveur.rpg.economy.Economy.fine(player.getServer(), player.getUuid(),
                    fr.serveur.rpg.economy.Economy.Source.AMENDE, cfg.pacificateurAmendeNuit, "monstre tué de nuit");
            player.sendMessage(Text.literal("Monstre tué de nuit : pas de prime" + (fine > 0 ? ", amende de " + fine + " pièce" + (fine > 1 ? "s" : "") : "") + ".")
                    .formatted(Formatting.RED), true);
            return;
        }
        int level = data.side().level;
        int bounty = cfg.pacificateurPrime + (cfg.pacificateurPrimePlusTousLesNiveaux > 0 ? level / cfg.pacificateurPrimePlusTousLesNiveaux : 0);
        if (GmEvents.running()) bounty = (int) Math.round(bounty * cfg.pacificateurMultiplicateurEvenement);
        data.rollDailyCaps();
        bounty = Math.max(0, Math.min(bounty, cfg.pacificateurPlafondPrimesJour - data.bountyToday));
        data.bountyToday += bounty;
        if (bounty > 0) bounty = (int) fr.serveur.rpg.economy.Economy.earn(player.getServer(), player.getUuid(),
                fr.serveur.rpg.economy.Economy.Source.PRIME, bounty, killed.getType().getUntranslatedName());
        state.markDirty();
        int xp = (int) Math.max(1, Math.round(killed.getMaxHealth() * cfg.pacificateurXpParPv));
        SideJobs.addXp(player, SideJob.PACIFICATEUR, xp, true);
        PlayerData.JobProgress p = data.side();
        player.sendMessage(SideJobs.xpLine(SideJob.PACIFICATEUR, p, xp).copy()
                .append(Text.literal(bounty > 0 ? "  · prime +" + bounty : "  · plafond de primes atteint").formatted(Formatting.GOLD)), true);
    }

    // ------------------------------------------------------------------ Enchanteur

    /** Table d'enchantement : la ligne {@code id} (0, 1, 2) est-elle accessible ? Null si oui, sinon la raison. */
    public static String checkEnchantLine(ServerPlayerEntity player, int id) {
        if (SideJobs.exempt(player)) return null;
        PlayerData data = RpgState.get(player.getServer()).player(player);
        if (data.sideJob != SideJob.ENCHANTEUR) return "Seul l'Enchanteur peut enchanter.";
        int need = id >= 2 ? SideJobConfig.data().enchanteurNiveauLigne3 : id == 1 ? SideJobConfig.data().enchanteurNiveauLigne2 : 1;
        return data.side().level >= need ? null : "Niveau " + need + " d'Enchanteur requis pour cette ligne.";
    }

    public static void afterEnchant(ServerPlayerEntity player, int id) {
        SideJobs.addActionXp(player, SideJob.ENCHANTEUR, (id + 1) * SideJobConfig.data().enchanteurXpParNiveauDepense);
    }

    /** Vrai si cet objet porte des enchantements (objet enchanté ou livre enchanté). */
    public static boolean isMagic(ItemStack stack) {
        return !stack.isEmpty() && (stack.hasEnchantments() || !EnchantedBookItem.getEnchantmentNbt(stack).isEmpty());
    }

    /** Enclume : le joueur peut-il transférer des enchantements ? */
    public static boolean canUseMagicOnAnvil(PlayerEntity player) {
        return !(player instanceof ServerPlayerEntity sp) || SideJobs.exempt(sp) || SideJobs.is(sp, SideJob.ENCHANTEUR);
    }

    public static void afterAnvilMagic(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity sp) SideJobs.addActionXp(sp, SideJob.ENCHANTEUR, SideJobConfig.data().enchanteurXpEnclume);
    }

    /** Coffre à butin qu'on ouvre pour la première fois : l'Enchanteur gagne de l'XP pour chaque livre enchanté. */
    public static void afterLootGenerated(PlayerEntity player, Inventory inventory) {
        if (!(player instanceof ServerPlayerEntity sp) || !SideJobs.is(sp, SideJob.ENCHANTEUR)) return;
        int books = 0;
        for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i).isOf(Items.ENCHANTED_BOOK)) books++;
        if (books > 0) {
            SideJobs.addActionXp(sp, SideJob.ENCHANTEUR, books * SideJobConfig.data().enchanteurXpLivreTrouve);
            sp.sendMessage(Text.literal("Livre enchanté trouvé !").formatted(Formatting.LIGHT_PURPLE), false);
        }
    }

    /** Échange avec un villageois. */
    public static void onTrade(PlayerEntity customer, TradeOffer offer) {
        if (!(customer instanceof ServerPlayerEntity sp) || !SideJobs.is(sp, SideJob.ENCHANTEUR)) return;
        int xp = SideJobConfig.data().enchanteurXpTroc;
        if (offer.getSellItem().isOf(Items.ENCHANTED_BOOK)) xp *= 2;
        SideJobs.addActionXp(sp, SideJob.ENCHANTEUR, xp);
    }

    // ------------------------------------------------------------------ Alchimiste

    /** Appelé quand l'alambic {@link #brewingNow} vérifie s'il peut brasser : faux si l'ingrédient est trop avancé. */
    public static boolean brewingAllowed(BrewingStandBlockEntity stand, ItemStack ingredient) {
        if (stand == null || ingredient.isEmpty() || !(stand.getWorld() instanceof ServerWorld world)) return true;
        SideJobConfig.Data cfg = SideJobConfig.data();
        int need;
        String what;
        if (ingredient.isOf(Items.REDSTONE)) { need = cfg.alchimisteNiveauDuree; what = "les potions longue durée"; }
        else if (ingredient.isOf(Items.GLOWSTONE_DUST)) { need = cfg.alchimisteNiveauPuissance; what = "les potions renforcées"; }
        else if (ingredient.isOf(Items.GUNPOWDER)) { need = cfg.alchimisteNiveauJetable; what = "les potions jetables"; }
        else if (ingredient.isOf(Items.DRAGON_BREATH)) { need = cfg.alchimisteNiveauPersistante; what = "les potions persistantes"; }
        else return true;
        if (need <= 1) return true;
        UUID brewer = BREWERS.get(stand);
        PlayerData data = brewer == null ? null : RpgState.get(world.getServer()).players.get(brewer);
        if (data != null && data.sideLevel(SideJob.ALCHIMISTE) >= need) return true;
        // Prévient l'alchimiste (au plus toutes les 5 secondes).
        long now = world.getTime();
        Long last = BREW_NOTIFIED.get(stand);
        if (brewer != null && (last == null || now - last > 100)) {
            BREW_NOTIFIED.put(stand, now);
            ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(brewer);
            if (p != null) p.sendMessage(Text.literal("Niveau " + need + " d'Alchimiste requis pour " + what + ".").formatted(Formatting.RED), true);
        }
        return false;
    }

    /** Potion retirée de l'alambic : XP une seule fois par potion brassée. */
    public static void onPotionTaken(PlayerEntity player, ItemStack stack) {
        if (!(player instanceof ServerPlayerEntity sp) || stack.isEmpty() || !SideJobs.is(sp, SideJob.ALCHIMISTE)) return;
        if (stack.getNbt() != null && stack.getNbt().getBoolean(BREWED_TAG)) return;
        var potion = PotionUtil.getPotion(stack);
        if (potion == Potions.EMPTY || potion == Potions.WATER) return;
        SideJobConfig.Data cfg = SideJobConfig.data();
        boolean basic = potion == Potions.AWKWARD || potion == Potions.MUNDANE || potion == Potions.THICK;
        int xp = basic ? 1 : stack.isOf(Items.LINGERING_POTION) ? cfg.alchimisteXpPersistante
                : stack.isOf(Items.SPLASH_POTION) ? cfg.alchimisteXpJetable : cfg.alchimisteXpPotion;
        String id = Registries.POTION.getId(potion).getPath();
        if (id.startsWith("long_") || id.startsWith("strong_")) xp += cfg.alchimisteXpAmelioration;
        stack.getOrCreateNbt().putBoolean(BREWED_TAG, true);
        SideJobs.addActionXp(sp, SideJob.ALCHIMISTE, xp * Math.max(1, stack.getCount()));
    }

    // ------------------------------------------------------------------ Pêcheur

    /** Prises d'une partie de pêche (avant qu'elles n'arrivent au joueur). */
    public static void onFished(ServerPlayerEntity player, Collection<ItemStack> loot) {
        if (loot.isEmpty() || !SideJobs.is(player, SideJob.PECHEUR)) return;
        SideJobConfig.Data cfg = SideJobConfig.data();
        int xp = 0;
        boolean treasure = false;
        for (ItemStack s : loot) {
            Integer v = cfg.pecheurXp.get(Registries.ITEM.getId(s.getItem()).toString());
            if (v != null) xp += v * s.getCount();
            else if (isTreasure(s)) {
                xp += cfg.pecheurXpTresor;
                treasure = true;
            } else xp += cfg.pecheurXpAutre;
        }
        SideJobs.addActionXp(player, SideJob.PECHEUR, xp);
        if (treasure) player.sendMessage(Text.literal("Trésor repêché !").formatted(Formatting.GOLD), false);
    }

    private static boolean isTreasure(ItemStack s) {
        return s.isOf(Items.ENCHANTED_BOOK) || s.isOf(Items.NAME_TAG) || s.isOf(Items.SADDLE) || s.isOf(Items.NAUTILUS_SHELL)
                || (s.isOf(Items.BOW) || s.isOf(Items.FISHING_ROD)) && EnchantmentHelper.get(s).size() > 0;
    }

    /** Bonus d'Appât (+1 par niveau atteint dans la liste) et de Chance de la mer pour le Pêcheur. */
    public static int fishingLureBonus(PlayerEntity player) {
        return bonus(player, SideJobConfig.data().pecheurNiveauxAppat);
    }

    public static int fishingLuckBonus(PlayerEntity player) {
        return bonus(player, SideJobConfig.data().pecheurNiveauxChance);
    }

    private static int bonus(PlayerEntity player, java.util.List<Integer> levels) {
        if (!(player instanceof ServerPlayerEntity sp) || levels == null) return 0;
        int level = SideJobs.level(sp, SideJob.PECHEUR);
        int n = 0;
        for (int l : levels) if (level >= l) n++;
        return n;
    }
}
