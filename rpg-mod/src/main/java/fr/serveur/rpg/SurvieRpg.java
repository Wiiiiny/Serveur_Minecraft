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

/** Serveur Survie RPG — point d'entrée. Mod 100 % côté serveur (aucun item ajouté). */
public class SurvieRpg implements ModInitializer {
    public static final String MOD_ID = "survierpg";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        fr.serveur.rpg.items.ModItems.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            JobConfig.load();
            fr.serveur.rpg.craft.CraftRules.load();
            RpgState.get(server); // charge les données dès le démarrage
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            JobConfig.resolve();
            fr.serveur.rpg.craft.CraftRules.resolve();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> RpgState.get(server).player(handler.player));

        HarvestRules.register();
        ObscureStone.register();
        fr.serveur.rpg.protection.Protection.register();
        fr.serveur.rpg.world.DayCycle.register();
        fr.serveur.rpg.death.DeathSystem.register();
        fr.serveur.rpg.mobs.MobRules.register();
        fr.serveur.rpg.events.GmEvents.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PlayerCommands.register(dispatcher);
            EconomyCommands.register(dispatcher);
            fr.serveur.rpg.commands.RankingCommands.register(dispatcher);
            GmCommands.register(dispatcher, registryAccess);
        });
    }
}
