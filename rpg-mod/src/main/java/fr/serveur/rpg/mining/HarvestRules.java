package fr.serveur.rpg.mining;

import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.data.Zone;
import fr.serveur.rpg.economy.ObscureStone;
import fr.serveur.rpg.jobs.Job;
import fr.serveur.rpg.jobs.JobConfig;
import fr.serveur.rpg.jobs.Progression;
import fr.serveur.rpg.tools.OfficialTools;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Règles de récolte :
 * - à la main : uniquement les blocs de la liste « mainNue » (terre, feuilles, fleurs...) ;
 * - sinon il faut un outil officiel de SON métier, adapté au bloc ;
 * - les blocs d'un métier demandent le bon métier et le bon niveau ;
 * - dans une zone de métier, seuls les joueurs de ce métier peuvent récolter.
 */
public final class HarvestRules {
    /** GM qui ont activé /gm bypass. */
    public static final Set<UUID> BYPASS = new HashSet<>();

    private HarvestRules() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return true;
            String refusal = check(sp, world, pos, state);
            if (refusal == null) return true;
            sp.sendMessage(Text.literal(refusal).formatted(Formatting.RED), true);
            return false;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            boolean placedByPlayer = PlacedBlocks.remove(world, pos);
            if (player instanceof ServerPlayerEntity owner) RpgState.get(owner.getServer()).setChestOwner(world, pos, null);
            if (!(player instanceof ServerPlayerEntity sp) || isExempt(sp)) return;
            Block block = state.getBlock();
            // Les cultures sont toujours plantées : on ne regarde que leur maturité.
            Boolean mature = cropMaturity(block, state);
            if (mature != null ? !mature : placedByPlayer) return;
            PlayerData data = RpgState.get(sp.getServer()).player(sp);
            if (data.job == null) return;
            JobConfig.Tier tier = JobConfig.tier(data.job, block);
            if (tier == null || tier.xp() <= 0) return;
            if (EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, sp.getMainHandStack()) > 0) return;
            Progression.addXp(sp, tier.xp(), false);
        });

        // XP de l'Aventurier : tuer des créatures avec son épée officielle.
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
            if (!(killer instanceof ServerPlayerEntity sp) || killed instanceof PlayerEntity) return;
            RpgState rs = RpgState.get(sp.getServer());
            PlayerData data = rs.player(sp);
            if (data.job != Job.AVENTURIER || OfficialTools.jobOf(sp.getMainHandStack(), rs) != Job.AVENTURIER) return;
            int xp = (int) Math.max(1, Math.round(killed.getMaxHealth() * JobConfig.data().xpAventurierParPv));
            Progression.addXp(sp, xp, false);
        });
    }

    /** Pour une culture plantée : vrai si mûre, faux sinon. Null si ce n'est pas une culture. */
    private static Boolean cropMaturity(Block block, BlockState state) {
        if (block instanceof CropBlock crop) return crop.isMature(state);
        if (block instanceof NetherWartBlock) return state.get(NetherWartBlock.AGE) >= 3;
        if (block instanceof CocoaBlock) return state.get(CocoaBlock.AGE) >= 2;
        if (block instanceof SweetBerryBushBlock) return state.get(SweetBerryBushBlock.AGE) >= 2;
        return null;
    }

    public static boolean isExempt(ServerPlayerEntity player) {
        return player.isCreative() || player.isSpectator() || BYPASS.contains(player.getUuid());
    }

    /** @return null si le joueur peut casser le bloc, sinon la raison du refus. */
    public static String check(ServerPlayerEntity player, World world, BlockPos pos, BlockState state) {
        if (isExempt(player) || state.isAir()) return null;
        RpgState rs = RpgState.get(player.getServer());
        PlayerData data = rs.player(player);
        Block block = state.getBlock();

        if (rs.isProtected(world, pos)) return "Zone protégée : aucune destruction possible.";
        Zone zone = rs.forbiddingJobZone(world, pos, data.job);
        if (zone != null) return "Zone réservée au métier " + zone.job.displayName + ".";
        java.util.UUID owner = rs.chestOwner(world, pos);
        if (owner != null && !owner.equals(player.getUuid())) return "Ce coffre appartient à un autre joueur.";

        ItemStack held = player.getMainHandStack();
        // Pierre obscure : uniquement avec la Pioche des ténèbres, qui ne récolte rien d'autre.
        if (ObscureStone.isObscureStone(state)) {
            return ObscureStone.isPickaxe(held) ? null : "Il faut la Pioche des ténèbres pour récolter la Pierre obscure.";
        }
        if (ObscureStone.isPickaxe(held)) return "La Pioche des ténèbres ne récolte que la Pierre obscure.";

        if (JobConfig.isHandBreakable(block)) return null;

        Job toolJob = OfficialTools.jobOf(held, rs);
        if (toolJob == null) {
            return held.isEmpty() ? "Ce bloc est incassable à la main." : "Seuls les outils officiels permettent de casser ce bloc.";
        }
        if (data.job == null) return "Tu n'as pas de métier. Tape /metier choisir <métier>.";
        if (toolJob != data.job) return "Cet outil est réservé au métier " + toolJob.displayName + ".";
        if (state.isToolRequired() && !held.isSuitableFor(state)) return "Outil inadapté pour ce bloc.";

        JobConfig.Tier tier = JobConfig.tier(data.job, block);
        if (tier != null) {
            int level = data.current().level;
            return level >= tier.level() ? null : "Niveau " + tier.level() + " de " + data.job.displayName + " requis.";
        }
        Job other = JobConfig.reservedFor(block, data.job);
        if (other != null) return "Bloc réservé au métier " + other.displayName + ".";
        return null; // bloc ordinaire (décoration, planches...) : n'importe quel outil officiel de son métier
    }
}
