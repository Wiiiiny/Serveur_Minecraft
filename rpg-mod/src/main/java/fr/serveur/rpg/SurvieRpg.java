package fr.serveur.rpg;

import fr.serveur.rpg.commands.EconomyCommands;
import fr.serveur.rpg.commands.GmCommands;
import fr.serveur.rpg.economy.ObscureStone;
import fr.serveur.rpg.commands.PlayerCommands;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.jobs.JobConfig;
import fr.serveur.rpg.mining.HarvestRules;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Serveur Survie RPG — point d'entrée (partie commune serveur + client). */
public class SurvieRpg implements ModInitializer {
    public static final String MOD_ID = "survierpg";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Boutique remplie automatiquement au premier démarrage (le GM peut tout modifier ensuite). */
    private static void seedShop(RpgState state) {
        if (state.shopSeeded) return;
        Object[][] defaults = {
                {fr.serveur.rpg.items.ModItems.CROCHET, 1, 25}, {fr.serveur.rpg.items.ModItems.LARME_DE_VIE, 1, 100},
                {net.minecraft.item.Items.LANTERN, 1, 8}, {net.minecraft.item.Items.TORCH, 16, 3},
                {net.minecraft.item.Items.BREAD, 8, 6}, {net.minecraft.item.Items.PHANTOM_MEMBRANE, 1, 12},
                {net.minecraft.item.Items.WHITE_BED, 1, 10}};
        for (Object[] d : defaults) {
            fr.serveur.rpg.data.MarketItem m = new fr.serveur.rpg.data.MarketItem();
            m.id = state.nextMarketId++;
            m.sellerName = "GM";
            m.stack = new net.minecraft.item.ItemStack((net.minecraft.item.Item) d[0], (int) d[1]);
            m.price = (int) d[2];
            state.shop.add(m);
        }
        state.shopSeeded = true;
        state.markDirty();
    }

    @Override
    public void onInitialize() {
        fr.serveur.rpg.items.ModItems.register();
        fr.serveur.rpg.raid.statue.Statues.register();
        fr.serveur.rpg.raid.thief.Thieves.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            JobConfig.load();
            fr.serveur.rpg.jobs.SideJobConfig.load();
            fr.serveur.rpg.economy.EconomyConfig.load();
            fr.serveur.rpg.craft.CraftRules.load();
            fr.serveur.rpg.auto.AutoConfig.load();
            fr.serveur.rpg.sombre.SombreConfig.load();
            fr.serveur.rpg.sombre.Rarete.resolve();
            fr.serveur.rpg.faction.FactionConfig.load();
            seedShop(RpgState.get(server)); // charge les données et remplit la boutique la première fois
            fr.serveur.rpg.economy.DynamicShop.seed(RpgState.get(server));
            fr.serveur.rpg.economy.DynamicShop.seedRaid(RpgState.get(server));
            RpgState.get(server).shop.forEach(fr.serveur.rpg.economy.DynamicShop::refresh);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            JobConfig.resolve();
            fr.serveur.rpg.jobs.SideJobConfig.resolve();
            fr.serveur.rpg.craft.CraftRules.resolve();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> RpgState.get(server).player(handler.player));

        HarvestRules.register();
        fr.serveur.rpg.network.RpgNetwork.register();
        ObscureStone.register();
        fr.serveur.rpg.protection.Protection.register();
        fr.serveur.rpg.world.DayCycle.register();
        fr.serveur.rpg.death.DeathSystem.register();
        fr.serveur.rpg.mobs.MobRules.register();
        fr.serveur.rpg.events.GmEvents.register();
        fr.serveur.rpg.jobs.SideJobRules.register();
        fr.serveur.rpg.world.PlayableZone.register();
        fr.serveur.rpg.world.TravelBeacon.register();
        fr.serveur.rpg.network.NameTags.register();
        fr.serveur.rpg.economy.EconomyTicker.register();
        fr.serveur.rpg.raid.RaidCycle.register();
        fr.serveur.rpg.sombre.MondeSombre.register();
        fr.serveur.rpg.faction.Territories.register();
        fr.serveur.rpg.faction.Wars.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PlayerCommands.register(dispatcher);
            EconomyCommands.register(dispatcher);
            fr.serveur.rpg.commands.RankingCommands.register(dispatcher);
            GmCommands.register(dispatcher, registryAccess);
            fr.serveur.rpg.commands.RaidCommands.register(dispatcher);
            fr.serveur.rpg.commands.SombreCommands.register(dispatcher);
            fr.serveur.rpg.commands.FactionCommands.register(dispatcher);
        });
    }
}
