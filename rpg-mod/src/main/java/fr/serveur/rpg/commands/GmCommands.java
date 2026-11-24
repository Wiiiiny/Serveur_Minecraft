package fr.serveur.rpg.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.data.Zone;
import fr.serveur.rpg.jobs.Job;
import fr.serveur.rpg.jobs.JobConfig;
import fr.serveur.rpg.jobs.Progression;
import fr.serveur.rpg.jobs.SideJob;
import fr.serveur.rpg.jobs.SideJobs;
import fr.serveur.rpg.mining.HarvestRules;
import fr.serveur.rpg.tools.OfficialTools;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Iterator;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** /gm ... : commandes du Game Master (opérateurs niveau 2). */
public final class GmCommands {
    private static final SuggestionProvider<ServerCommandSource> JOBS = (c, b) -> CommandSource.suggestMatching(Job.ids(), b);
    private static final SuggestionProvider<ServerCommandSource> SIDE_JOBS = (c, b) -> CommandSource.suggestMatching(SideJob.ids(), b);

    private GmCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d, CommandRegistryAccess registryAccess) {
        LiteralArgumentBuilder<ServerCommandSource> gm = literal("gm").requires(s -> s.hasPermissionLevel(2));

        // ---- métiers / niveaux / prestige
        gm.then(literal("metier")
                .then(literal("definir").then(argument("joueur", EntityArgumentType.player()).then(argument("metier", StringArgumentType.word()).suggests(JOBS)
                        .executes(c -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
                            Job job = job(c);
                            RpgState s = state(c);
                            PlayerData data = s.player(p);
                            data.job = job;
                            data.current();
                            s.markDirty();
                            Msg.send(p, Text.literal("Le GM t'a attribué le métier ").append(Text.literal(job.displayName).formatted(job.color)).append("."));
                            Msg.ok(c.getSource(), data.name + " est maintenant " + job.displayName + " (niveau " + data.current().level + ").");
                            return 1;
                        }))))
                .then(literal("retirer").then(argument("joueur", EntityArgumentType.player()).executes(c -> {
                    ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
                    RpgState s = state(c);
                    s.player(p).job = null;
                    s.markDirty();
                    Msg.ok(c.getSource(), p.getGameProfile().getName() + " n'a plus de métier (sa progression est conservée).");
                    return 1;
                }))));

        gm.then(literal("niveau").then(argument("joueur", EntityArgumentType.player()).then(argument("niveau", IntegerArgumentType.integer(1, Job.MAX_LEVEL))
                .executes(c -> {
                    ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
                    PlayerData.JobProgress jp = requireJob(c, p);
                    jp.level = IntegerArgumentType.getInteger(c, "niveau");
                    jp.xp = 0;
                    state(c).markDirty();
                    Msg.ok(c.getSource(), p.getGameProfile().getName() + " est maintenant niveau " + jp.level + ".");
                    return 1;
                }))));

        gm.then(literal("xp").then(argument("joueur", EntityArgumentType.player()).then(argument("montant", IntegerArgumentType.integer(1))
                .executes(c -> {
                    ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
                    requireJob(c, p);
                    int amount = IntegerArgumentType.getInteger(c, "montant");
                    Progression.addXp(p, amount, false);
                    Msg.ok(c.getSource(), "+" + amount + " XP de métier pour " + p.getGameProfile().getName() + ".");
                    return 1;
                }))));

        gm.then(literal("prestige")
                .then(literal("definir").then(argument("joueur", EntityArgumentType.player()).then(argument("valeur", IntegerArgumentType.integer(0))
                        .executes(c -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
                            PlayerData.JobProgress jp = requireJob(c, p);
                            jp.prestige = IntegerArgumentType.getInteger(c, "valeur");
                            state(c).markDirty();
                            Msg.ok(c.getSource(), p.getGameProfile().getName() + " : prestige " + jp.prestige + ".");
                            return 1;
                        }))))
                .then(literal("bonus").then(argument("pourcent", IntegerArgumentType.integer(0, 1000)).executes(c -> {
                    RpgState s = state(c);
                    s.prestigeBonusPercent = IntegerArgumentType.getInteger(c, "pourcent");
                    s.markDirty();
                    Msg.ok(c.getSource(), "Bonus d'argent des quêtes : +" + s.prestigeBonusPercent + " % par prestige.");
                    return 1;
                }))));

        // ---- outils officiels
        gm.then(literal("outil")
                .then(literal("donner").then(argument("joueur", EntityArgumentType.player()).then(argument("outil", ItemStackArgumentType.itemStack(registryAccess))
                        .executes(c -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
                            Item item = ItemStackArgumentType.getItemStackArgument(c, "outil").getItem();
                            Job job = Job.forTool(item);
                            if (job == null) {
                                Msg.info(c.getSource(), Text.literal("Ce n'est pas un outil de métier (pioche, hache, houe ou épée).").formatted(Formatting.RED));
                                return 0;
                            }
                            // Un outil vanilla est remplacé par l'outil officiel du mod du même matériau.
                            if (!(item instanceof fr.serveur.rpg.items.OfficialToolItem) && item instanceof net.minecraft.item.ToolItem ti) {
                                Item official = fr.serveur.rpg.items.ModItems.official(job, ti.getMaterial());
                                if (official != null) item = official;
                            }
                            ItemStack tool = OfficialTools.create(item, job, state(c));
                            if (!p.getInventory().insertStack(tool)) p.dropItem(tool, false);
                            Msg.send(p, Text.literal("Tu as reçu un outil officiel : ").append(tool.getName()));
                            Msg.ok(c.getSource(), "Outil officiel (" + job.displayName + ") donné à " + p.getGameProfile().getName() + ".");
                            return 1;
                        }))))
                .then(literal("revoquer").executes(c -> {
                    ItemStack held = c.getSource().getPlayerOrThrow().getMainHandStack();
                    UUID id = OfficialTools.idOf(held);
                    if (id == null || !state(c).officialTools.remove(id)) {
                        Msg.info(c.getSource(), Text.literal("Tiens en main un outil officiel valide.").formatted(Formatting.RED));
                        return 0;
                    }
                    state(c).markDirty();
                    Msg.ok(c.getSource(), "Outil révoqué : il ne permet plus de récolter.");
                    return 1;
                }))
                .then(literal("info").executes(c -> {
                    ItemStack held = c.getSource().getPlayerOrThrow().getMainHandStack();
                    UUID id = OfficialTools.idOf(held);
                    Job job = OfficialTools.jobOf(held, state(c));
                    Msg.info(c.getSource(), Text.literal(id == null ? "Pas un outil officiel."
                            : "ID " + id + " — " + (job == null ? "RÉVOQUÉ / invalide" : "valide, métier " + job.displayName)));
                    return 1;
                })));

        // ---- zones de métiers
        gm.then(literal("zone")
                .then(literal("creer").then(argument("nom", StringArgumentType.word()).then(argument("metier", StringArgumentType.word()).suggests(JOBS)
                        .then(argument("de", BlockPosArgumentType.blockPos()).then(argument("a", BlockPosArgumentType.blockPos()).executes(c -> {
                            String name = StringArgumentType.getString(c, "nom");
                            Job job = job(c);
                            BlockPos a = BlockPosArgumentType.getBlockPos(c, "de");
                            BlockPos b = BlockPosArgumentType.getBlockPos(c, "a");
                            String dim = c.getSource().getWorld().getRegistryKey().getValue().toString();
                            Zone z = new Zone(name, job, dim, a, b);
                            RpgState s = state(c);
                            s.zones.put(name, z);
                            s.markDirty();
                            Msg.ok(c.getSource(), "Zone créée : " + z.describe());
                            return 1;
                        }))))))
                .then(literal("supprimer").then(argument("nom", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(state(c).zones.keySet(), b))
                        .executes(c -> {
                            RpgState s = state(c);
                            if (s.zones.remove(StringArgumentType.getString(c, "nom")) == null) {
                                Msg.info(c.getSource(), Text.literal("Zone inconnue.").formatted(Formatting.RED));
                                return 0;
                            }
                            s.markDirty();
                            Msg.ok(c.getSource(), "Zone supprimée.");
                            return 1;
                        })))
                .then(literal("liste").executes(c -> {
                    MutableText t = Text.literal("Zones (" + state(c).zones.size() + ") :").formatted(Formatting.GOLD);
                    state(c).zones.values().forEach(z -> t.append(Text.literal("\n- " + z.describe()).formatted(Formatting.WHITE)));
                    Msg.info(c.getSource(), t);
                    return 1;
                })));

        // ---- paliers de métier
        gm.then(literal("palier")
                .then(literal("liste").then(argument("metier", StringArgumentType.word()).suggests(JOBS).executes(c -> {
                    Job job = job(c);
                    MutableText t = Text.literal("Paliers " + job.displayName + " :").formatted(Formatting.GOLD);
                    for (JobConfig.Palier p : JobConfig.data().metiers.getOrDefault(job.name(), java.util.List.of())) {
                        t.append(Text.literal("\nniv. " + p.niveau + " (" + p.xp + " XP) : ").formatted(Formatting.YELLOW))
                                .append(Text.literal(String.join(", ", p.blocs)).formatted(Formatting.WHITE));
                    }
                    Msg.info(c.getSource(), t);
                    return 1;
                })))
                .then(literal("ajouter").then(argument("metier", StringArgumentType.word()).suggests(JOBS)
                        .then(argument("niveau", IntegerArgumentType.integer(1, Job.MAX_LEVEL)).then(argument("xp", IntegerArgumentType.integer(0))
                                .then(argument("bloc", StringArgumentType.greedyString()).executes(c -> {
                                    Job job = job(c);
                                    String bloc = StringArgumentType.getString(c, "bloc").trim();
                                    if (!JobConfig.isValidEntry(bloc)) {
                                        Msg.info(c.getSource(), Text.literal("Bloc ou tag inconnu : " + bloc + " (ex. minecraft:stone ou #minecraft:logs)").formatted(Formatting.RED));
                                        return 0;
                                    }
                                    removeEverywhere(job, bloc);
                                    JobConfig.Palier p = new JobConfig.Palier();
                                    p.niveau = IntegerArgumentType.getInteger(c, "niveau");
                                    p.xp = IntegerArgumentType.getInteger(c, "xp");
                                    p.blocs.add(bloc);
                                    JobConfig.data().metiers.computeIfAbsent(job.name(), k -> new java.util.ArrayList<>()).add(p);
                                    JobConfig.save();
                                    JobConfig.resolve();
                                    Msg.ok(c.getSource(), bloc + " → " + job.displayName + " niveau " + p.niveau + " (" + p.xp + " XP).");
                                    return 1;
                                }))))))
                .then(literal("retirer").then(argument("metier", StringArgumentType.word()).suggests(JOBS)
                        .then(argument("bloc", StringArgumentType.greedyString()).executes(c -> {
                            Job job = job(c);
                            String bloc = StringArgumentType.getString(c, "bloc").trim();
                            if (!removeEverywhere(job, bloc)) {
                                Msg.info(c.getSource(), Text.literal(bloc + " n'est pas dans les paliers " + job.displayName + ".").formatted(Formatting.RED));
                                return 0;
                            }
                            JobConfig.save();
                            JobConfig.resolve();
                            Msg.ok(c.getSource(), bloc + " retiré des paliers " + job.displayName + ".");
                            return 1;
                        })))));

        // ---- quêtes
        gm.then(literal("quete")
                .then(literal("donner").then(argument("joueur", EntityArgumentType.player()).then(argument("texte", StringArgumentType.greedyString())
                        .executes(c -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
                            RpgState s = state(c);
                            PlayerData.Quest q = new PlayerData.Quest();
                            q.id = s.nextQuestId++;
                            q.text = StringArgumentType.getString(c, "texte");
                            s.player(p).quests.add(q);
                            s.markDirty();
                            Msg.send(p, Text.literal("Nouvelle quête #" + q.id + " : ").formatted(Formatting.GOLD).append(Text.literal(q.text).formatted(Formatting.WHITE)));
                            Msg.ok(c.getSource(), "Quête #" + q.id + " donnée à " + p.getGameProfile().getName() + ".");
                            return 1;
                        }))))
                .then(literal("valider").then(argument("joueur", EntityArgumentType.player()).then(argument("id", IntegerArgumentType.integer(1))
                        .executes(c -> validateQuest(c, 0, 0))
                        .then(argument("xp", IntegerArgumentType.integer(0))
                                .executes(c -> validateQuest(c, IntegerArgumentType.getInteger(c, "xp"), 0))
                                .then(argument("argent", IntegerArgumentType.integer(0))
                                        .executes(c -> validateQuest(c, IntegerArgumentType.getInteger(c, "xp"), IntegerArgumentType.getInteger(c, "argent"))))))))
                .then(literal("retirer").then(argument("joueur", EntityArgumentType.player()).then(argument("id", IntegerArgumentType.integer(1))
                        .executes(c -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
                            RpgState s = state(c);
                            int id = IntegerArgumentType.getInteger(c, "id");
                            boolean removed = s.player(p).quests.removeIf(q -> q.id == id);
                            s.markDirty();
                            Msg.ok(c.getSource(), removed ? "Quête #" + id + " retirée." : "Quête introuvable.");
                            return removed ? 1 : 0;
                        }))))
                .then(literal("liste").then(argument("joueur", EntityArgumentType.player())
                        .executes(c -> PlayerCommands.showQuests(c.getSource(), EntityArgumentType.getPlayer(c, "joueur"))))));

        // ---- économie
        gm.then(literal("argent")
                .then(literal("voir").then(argument("joueur", StringArgumentType.word()).suggests(EconomyCommands.KNOWN_PLAYERS).executes(c -> {
                    var e = EconomyCommands.resolve(c, state(c));
                    Msg.info(c.getSource(), Text.literal(e.getValue().name + " : " + fr.serveur.rpg.economy.Bank.format(e.getValue().money)));
                    return 1;
                })))
                .then(moneyOp("donner", (cur, n) -> cur + n))
                .then(moneyOp("retirer", (cur, n) -> Math.max(0, cur - n)))
                .then(moneyOp("definir", (cur, n) -> n)));

        gm.then(literal("pioche").then(argument("joueur", EntityArgumentType.player()).executes(c -> {
            ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
            fr.serveur.rpg.economy.ObscureStone.give(p);
            Msg.ok(c.getSource(), "Pioche des ténèbres donnée à " + p.getGameProfile().getName() + ".");
            return 1;
        })));

        gm.then(literal("boutique")
                .then(literal("ajouter").then(argument("prix", IntegerArgumentType.integer(1)).executes(c -> {
                    ItemStack held = c.getSource().getPlayerOrThrow().getMainHandStack();
                    if (held.isEmpty()) throw EconomyCommands.err("Tiens en main l'objet à mettre en boutique (la quantité compte).");
                    RpgState s = state(c);
                    fr.serveur.rpg.data.MarketItem m = new fr.serveur.rpg.data.MarketItem();
                    m.id = s.nextMarketId++;
                    m.sellerName = "GM";
                    m.stack = held.copy();
                    m.price = IntegerArgumentType.getInteger(c, "prix");
                    s.shop.add(m);
                    s.markDirty();
                    Msg.ok(c.getSource(), "Boutique #" + m.id + " : " + m.stack.getCount() + "x " + m.stack.getName().getString() + " à " + m.price + " pièces.");
                    return 1;
                })))
                .then(literal("prix").then(argument("id", IntegerArgumentType.integer(1)).then(argument("prix", IntegerArgumentType.integer(1)).executes(c -> {
                    int id = IntegerArgumentType.getInteger(c, "id");
                    var m = state(c).shop.stream().filter(x -> x.id == id).findFirst().orElseThrow(() -> EconomyCommands.err("Article introuvable."));
                    m.price = IntegerArgumentType.getInteger(c, "prix");
                    state(c).markDirty();
                    Msg.ok(c.getSource(), "Article #" + id + " : nouveau prix " + m.price + " pièces.");
                    return 1;
                }))))
                .then(literal("retirer").then(argument("id", IntegerArgumentType.integer(1)).executes(c -> {
                    int id = IntegerArgumentType.getInteger(c, "id");
                    boolean removed = state(c).shop.removeIf(x -> x.id == id);
                    state(c).markDirty();
                    Msg.ok(c.getSource(), removed ? "Article #" + id + " retiré." : "Article introuvable.");
                    return removed ? 1 : 0;
                })))
                .then(literal("liste").executes(c -> {
                    MutableText t = Text.literal("Boutique du GM :").formatted(Formatting.GOLD);
                    state(c).shop.forEach(m -> t.append(Text.literal("\n#" + m.id + " " + m.stack.getCount() + "x " + m.stack.getName().getString() + " — " + m.price + " pièces")));
                    Msg.info(c.getSource(), t);
                    return 1;
                })));

        gm.then(literal("hdv").then(literal("retirer").then(argument("id", IntegerArgumentType.integer(1)).executes(c -> {
            int id = IntegerArgumentType.getInteger(c, "id");
            RpgState s = state(c);
            var m = s.auctions.stream().filter(x -> x.id == id).findFirst().orElseThrow(() -> EconomyCommands.err("Annonce introuvable."));
            s.auctions.remove(m);
            s.markDirty();
            ServerPlayerEntity gmPlayer = c.getSource().getPlayerOrThrow();
            if (!gmPlayer.getInventory().insertStack(m.stack.copy())) gmPlayer.dropItem(m.stack.copy(), false);
            fr.serveur.rpg.economy.Bank.notify(c.getSource().getServer(), m.seller, "Le GM a retiré ton annonce : " + m.stack.getName().getString() + ".");
            Msg.ok(c.getSource(), "Annonce #" + id + " retirée (l'objet est dans ton inventaire).");
            return 1;
        }))));

        // ---- protections (mairie), coffres et crochets
        gm.then(literal("protection").then(literal("creer").then(argument("nom", StringArgumentType.word())
                .then(argument("de", BlockPosArgumentType.blockPos()).then(argument("a", BlockPosArgumentType.blockPos())
                        .executes(c -> createProtected(c, 5))
                        .then(argument("marge", IntegerArgumentType.integer(0, 64))
                                .executes(c -> createProtected(c, IntegerArgumentType.getInteger(c, "marge")))))))));

        gm.then(literal("coffre")
                .then(literal("info").then(argument("pos", BlockPosArgumentType.blockPos()).executes(c -> {
                    BlockPos pos = BlockPosArgumentType.getBlockPos(c, "pos");
                    UUID owner = state(c).chestOwner(c.getSource().getWorld(), pos);
                    Msg.info(c.getSource(), Text.literal(owner == null ? "Coffre sans propriétaire."
                            : "Propriétaire : " + fr.serveur.rpg.protection.Protection.nameOf(state(c), owner)));
                    return 1;
                })))
                .then(literal("attribuer").then(argument("pos", BlockPosArgumentType.blockPos())
                        .then(argument("joueur", StringArgumentType.word()).suggests(EconomyCommands.KNOWN_PLAYERS).executes(c -> {
                            BlockPos pos = BlockPosArgumentType.getBlockPos(c, "pos");
                            if (!(c.getSource().getWorld().getBlockState(pos).getBlock() instanceof net.minecraft.block.ChestBlock)) {
                                throw EconomyCommands.err("Il n'y a pas de coffre à cette position.");
                            }
                            var e = EconomyCommands.resolve(c, state(c));
                            state(c).setChestOwner(c.getSource().getWorld(), pos, e.getKey());
                            Msg.ok(c.getSource(), "Le coffre appartient maintenant à " + e.getValue().name + ".");
                            return 1;
                        }))))
                .then(literal("liberer").then(argument("pos", BlockPosArgumentType.blockPos()).executes(c -> {
                    state(c).setChestOwner(c.getSource().getWorld(), BlockPosArgumentType.getBlockPos(c, "pos"), null);
                    Msg.ok(c.getSource(), "Coffre libéré (plus de propriétaire).");
                    return 1;
                }))));

        gm.then(literal("crochet")
                .then(literal("donner").then(argument("joueur", EntityArgumentType.player())
                        .executes(c -> giveLockpicks(c, 1))
                        .then(argument("nombre", IntegerArgumentType.integer(1, 64))
                                .executes(c -> giveLockpicks(c, IntegerArgumentType.getInteger(c, "nombre"))))))
                .then(literal("chance").then(argument("pourcent", IntegerArgumentType.integer(0, 100)).executes(c -> {
                    state(c).lockpickChance = IntegerArgumentType.getInteger(c, "pourcent");
                    state(c).markDirty();
                    Msg.ok(c.getSource(), "Chance de réussite d'un crochet : " + state(c).lockpickChance + " %.");
                    return 1;
                }))));

        // ---- jour / nuit et morts
        gm.then(literal("cycle")
                .then(literal("jour").then(argument("minutes", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.5, 120)).executes(c -> {
                    state(c).dayMinutes = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "minutes");
                    state(c).markDirty();
                    Msg.ok(c.getSource(), "Durée du jour : " + state(c).dayMinutes + " min.");
                    return 1;
                })))
                .then(literal("nuit").then(argument("minutes", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.5, 120)).executes(c -> {
                    state(c).nightMinutes = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "minutes");
                    state(c).markDirty();
                    Msg.ok(c.getSource(), "Durée de la nuit : " + state(c).nightMinutes + " min.");
                    return 1;
                })))
                .then(literal("info").executes(c -> {
                    long t = Math.floorMod(c.getSource().getServer().getOverworld().getTimeOfDay(), 24000L);
                    Msg.info(c.getSource(), Text.literal("Jour " + state(c).dayMinutes + " min, nuit " + state(c).nightMinutes
                            + " min. Heure actuelle : " + t + (fr.serveur.rpg.world.DayCycle.isNight(c.getSource().getServer().getOverworld()) ? " (nuit)" : " (jour)")));
                    return 1;
                }))
                .then(literal("passer").then(literal("nuit").executes(c -> setTime(c, fr.serveur.rpg.world.DayCycle.NIGHT_START)))
                        .then(literal("jour").executes(c -> setTime(c, fr.serveur.rpg.world.DayCycle.NIGHT_END)))));

        gm.then(literal("salle").then(literal("definir").executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            RpgState s = state(c);
            s.waitingRoomDim = p.getWorld().getRegistryKey().getValue().toString();
            s.waitingRoom = p.getBlockPos();
            s.markDirty();
            Msg.ok(c.getSource(), "Salle d'attente des morts définie ici.");
            return 1;
        })));

        gm.then(literal("larme").then(argument("joueur", EntityArgumentType.player())
                .executes(c -> giveTears(c, 1))
                .then(argument("nombre", IntegerArgumentType.integer(1, 64)).executes(c -> giveTears(c, IntegerArgumentType.getInteger(c, "nombre"))))));

        gm.then(literal("reanimer").then(argument("joueur", EntityArgumentType.player()).executes(c -> {
            ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
            fr.serveur.rpg.death.DeathSystem.revive(p, "Le GM te ramène à la vie.");
            Msg.ok(c.getSource(), p.getGameProfile().getName() + " réanimé.");
            return 1;
        })));

        // ---- crafts contrôlés
        gm.then(literal("craft")
                .then(literal("liste").executes(c -> {
                    Msg.info(c.getSource(), Text.literal("Crafts bloqués : " + String.join(", ", fr.serveur.rpg.craft.CraftRules.data().bloques)));
                    return 1;
                }))
                .then(literal("bloquer").then(argument("objet", StringArgumentType.greedyString()).executes(c -> {
                    String e = StringArgumentType.getString(c, "objet").trim();
                    if (!fr.serveur.rpg.craft.CraftRules.isValidEntry(e)) throw EconomyCommands.err("Objet ou tag inconnu : " + e);
                    if (!fr.serveur.rpg.craft.CraftRules.data().bloques.contains(e)) fr.serveur.rpg.craft.CraftRules.data().bloques.add(e);
                    fr.serveur.rpg.craft.CraftRules.save();
                    fr.serveur.rpg.craft.CraftRules.resolve();
                    Msg.ok(c.getSource(), "Craft bloqué : " + e);
                    return 1;
                })))
                .then(literal("debloquer").then(argument("objet", StringArgumentType.greedyString()).executes(c -> {
                    String e = StringArgumentType.getString(c, "objet").trim();
                    if (!fr.serveur.rpg.craft.CraftRules.data().bloques.remove(e)) throw EconomyCommands.err(e + " n'est pas dans la liste.");
                    fr.serveur.rpg.craft.CraftRules.save();
                    fr.serveur.rpg.craft.CraftRules.resolve();
                    Msg.ok(c.getSource(), "Craft débloqué : " + e);
                    return 1;
                }))));

        // ---- Nether / End
        gm.then(literal("dimension")
                .then(literal("ouvrir").then(literal("nether").executes(c -> setDimension(c, true, true)))
                        .then(literal("end").executes(c -> setDimension(c, false, true))))
                .then(literal("fermer").then(literal("nether").executes(c -> setDimension(c, true, false)))
                        .then(literal("end").executes(c -> setDimension(c, false, false)))));

        // ---- événements
        gm.then(literal("event")
                .then(literal("lancer").then(argument("type", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(java.util.Arrays.stream(fr.serveur.rpg.events.GmEvents.Type.values()).map(t -> t.name().toLowerCase()), b))
                        .then(argument("minutes", IntegerArgumentType.integer(1, 600)).then(argument("titre", StringArgumentType.greedyString()).executes(c -> {
                            var type = fr.serveur.rpg.events.GmEvents.Type.parse(StringArgumentType.getString(c, "type"));
                            if (type == null) throw EconomyCommands.err("Type inconnu : combat, recolte, ressource ou special.");
                            fr.serveur.rpg.events.GmEvents.start(c.getSource().getServer(), type, IntegerArgumentType.getInteger(c, "minutes"),
                                    StringArgumentType.getString(c, "titre"));
                            return 1;
                        })))))
                .then(literal("terminer").executes(c -> {
                    if (!fr.serveur.rpg.events.GmEvents.running()) throw EconomyCommands.err("Aucun événement en cours.");
                    fr.serveur.rpg.events.GmEvents.stop(c.getSource().getServer());
                    return 1;
                }))
                .then(literal("points").then(argument("joueur", EntityArgumentType.player()).then(argument("points", IntegerArgumentType.integer(1)).executes(c -> {
                    if (!fr.serveur.rpg.events.GmEvents.running()) throw EconomyCommands.err("Aucun événement en cours.");
                    fr.serveur.rpg.events.GmEvents.add(EntityArgumentType.getPlayer(c, "joueur"), IntegerArgumentType.getInteger(c, "points"));
                    Msg.ok(c.getSource(), "Points ajoutés.");
                    return 1;
                }))))
                .then(literal("scores").executes(c -> {
                    MutableText t = Text.literal("Scores en cours :").formatted(Formatting.AQUA);
                    fr.serveur.rpg.events.GmEvents.scores().forEach((n, s) -> t.append(Text.literal("\n" + n + " — " + s)));
                    Msg.info(c.getSource(), t);
                    return 1;
                })));

        // ---- maintenance
        gm.then(literal("bypass").executes(c -> {
            UUID id = c.getSource().getPlayerOrThrow().getUuid();
            boolean on = HarvestRules.BYPASS.add(id) || !HarvestRules.BYPASS.remove(id);
            Msg.ok(c.getSource(), "Contournement des règles de récolte : " + (on ? "ACTIVÉ" : "désactivé") + ".");
            return 1;
        }));
        gm.then(literal("diag").then(argument("joueur", EntityArgumentType.player())
                .executes(c -> PlayerCommands.showInfo(c.getSource(), EntityArgumentType.getPlayer(c, "joueur")))));
        gm.then(literal("recharger").executes(c -> {
            JobConfig.load();
            fr.serveur.rpg.jobs.SideJobConfig.load();
            fr.serveur.rpg.economy.EconomyConfig.load();
            fr.serveur.rpg.sombre.MondeSombre.reload(c.getSource().getServer());
            Msg.ok(c.getSource(), "Config rechargée (metiers.json, metiers2.json, economie.json, monde_sombre.json, objets_rares.json, factions.json).");
            return 1;
        }));

        // ---- économie automatisée : audit, gel, annulation, sauvegardes
        gm.then(EconomyAdminCommands.build());
        gm.then(RaidCommands.gm());

        // ---- métier supplémentaire
        gm.then(literal("metier2")
                .then(literal("definir").then(argument("joueur", StringArgumentType.word()).suggests(EconomyCommands.KNOWN_PLAYERS)
                        .then(argument("metier", StringArgumentType.word()).suggests(SIDE_JOBS).executes(c -> {
                            SideJob job = SideJob.parse(StringArgumentType.getString(c, "metier"));
                            if (job == null) {
                                Msg.info(c.getSource(), Text.literal("Métier inconnu. Choix : " + String.join(", ", SideJob.ids())).formatted(Formatting.RED));
                                return 0;
                            }
                            return setSideJob(c, job);
                        }))))
                .then(literal("retirer").then(argument("joueur", StringArgumentType.word()).suggests(EconomyCommands.KNOWN_PLAYERS)
                        .executes(c -> setSideJob(c, null))))
                .then(literal("niveau").then(argument("joueur", StringArgumentType.word()).suggests(EconomyCommands.KNOWN_PLAYERS)
                        .then(argument("niveau", IntegerArgumentType.integer(1, Job.MAX_LEVEL)).executes(c -> {
                            var e = state(c).findByName(StringArgumentType.getString(c, "joueur"));
                            if (e == null || e.getValue().side() == null) {
                                Msg.info(c.getSource(), Text.literal("Joueur inconnu ou sans métier supplémentaire.").formatted(Formatting.RED));
                                return 0;
                            }
                            e.getValue().side().level = IntegerArgumentType.getInteger(c, "niveau");
                            e.getValue().side().xp = 0;
                            state(c).markDirty();
                            Msg.ok(c.getSource(), e.getValue().name + " : " + e.getValue().sideJob.displayName + " niveau " + e.getValue().side().level + ".");
                            return 1;
                        })))));

        // ---- zone jouable (seul le Voyageur peut en sortir)
        gm.then(literal("bordure")
                .then(literal("coin1").executes(c -> borderCorner(c, 1, null))
                        .then(argument("x", IntegerArgumentType.integer()).then(argument("z", IntegerArgumentType.integer())
                                .executes(c -> borderCorner(c, 1, new BlockPos(IntegerArgumentType.getInteger(c, "x"), 0, IntegerArgumentType.getInteger(c, "z")))))))
                .then(literal("coin2").executes(c -> borderCorner(c, 2, null))
                        .then(argument("x", IntegerArgumentType.integer()).then(argument("z", IntegerArgumentType.integer())
                                .executes(c -> borderCorner(c, 2, new BlockPos(IntegerArgumentType.getInteger(c, "x"), 0, IntegerArgumentType.getInteger(c, "z")))))))
                .then(literal("retirer").executes(c -> {
                    state(c).clearBorder();
                    Msg.ok(c.getSource(), "Zone jouable retirée : plus aucune limite.");
                    return 1;
                }))
                .then(literal("info").executes(c -> {
                    var box = fr.serveur.rpg.world.PlayableZone.box(state(c));
                    Msg.info(c.getSource(), Text.literal(box == null ? "Zone jouable non définie (il faut les deux coins : /gm bordure coin1 puis coin2)."
                            : "Zone jouable — " + box.describe()).formatted(Formatting.GOLD));
                    return 1;
                })));

        d.register(gm);
    }

    private static int validateQuest(CommandContext<ServerCommandSource> c, int xp, int money) throws CommandSyntaxException {
        ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
        RpgState s = state(c);
        int id = IntegerArgumentType.getInteger(c, "id");
        PlayerData.Quest quest = s.player(p).quests.stream().filter(q -> q.id == id).findFirst().orElse(null);
        if (quest == null || quest.done) {
            Msg.info(c.getSource(), Text.literal("Quête introuvable ou déjà validée.").formatted(Formatting.RED));
            return 0;
        }
        quest.done = true;
        s.markDirty();
        Msg.send(p, Text.literal("Quête #" + id + " validée : ").formatted(Formatting.GREEN).append(Text.literal(quest.text).formatted(Formatting.WHITE)));
        if (xp > 0) Progression.addXp(p, xp, false);
        long paid = 0;
        if (money > 0) {
            PlayerData data = s.player(p);
            int prestige = data.current() == null ? 0 : data.current().prestige;
            paid = fr.serveur.rpg.economy.Economy.earn(c.getSource().getServer(), p.getUuid(), fr.serveur.rpg.economy.Economy.Source.QUETE,
                    Math.round(money * (1 + s.prestigeBonusPercent * prestige / 100.0)), "quête #" + id + " (GM)");
            s.markDirty();
            Msg.send(p, Text.literal("Récompense : +" + fr.serveur.rpg.economy.Bank.format(paid) + " sur ton compte"
                    + (paid > money ? " (bonus prestige inclus)" : "") + ".").formatted(Formatting.GOLD));
        }
        Msg.ok(c.getSource(), "Quête #" + id + " validée" + (xp > 0 ? " (+" + xp + " XP)" : "")
                + (paid > 0 ? " (+" + paid + " pièces)" : "") + ".");
        return 1;
    }

    private static int createProtected(CommandContext<ServerCommandSource> c, int margin) {
        String name = StringArgumentType.getString(c, "nom");
        String dim = c.getSource().getWorld().getRegistryKey().getValue().toString();
        Zone z = Zone.protectedArea(name, dim, BlockPosArgumentType.getBlockPos(c, "de"), BlockPosArgumentType.getBlockPos(c, "a"), margin);
        RpgState s = state(c);
        s.zones.put(name, z);
        s.markDirty();
        Msg.ok(c.getSource(), "Zone protégée créée (marge " + margin + " blocs) : " + z.describe());
        return 1;
    }

    private static int setDimension(CommandContext<ServerCommandSource> c, boolean nether, boolean open) {
        RpgState s = state(c);
        if (nether) s.netherOpen = open;
        else s.endOpen = open;
        s.markDirty();
        String name = nether ? "Nether" : "End";
        Msg.broadcast(c.getSource().getServer(), Text.literal(open ? "Le " + name + " est désormais ouvert !" : "Le " + name + " est scellé.")
                .formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
        return 1;
    }

    private static int setTime(CommandContext<ServerCommandSource> c, long target) {
        var server = c.getSource().getServer();
        if (fr.serveur.rpg.sombre.Lumiere.controls(server)) {
            // Nuit permanente : l'heure est fixée par la lumière (jour invoqué ou nuit).
            boolean night = target == fr.serveur.rpg.world.DayCycle.NIGHT_START;
            if (night) fr.serveur.rpg.sombre.Lumiere.forceNight(server);
            else fr.serveur.rpg.sombre.Lumiere.forceDay(server, "le destin");
            fr.serveur.rpg.sombre.Journal.write(server, "gm", night ? "lumiere_nuit" : "lumiere_jour", "gm", c.getSource().getName());
            Msg.ok(c.getSource(), night ? "La nuit retombe." : "Le jour se lève (jour invoqué).");
            return 1;
        }
        var ow = server.getOverworld();
        long day = ow.getTimeOfDay() - Math.floorMod(ow.getTimeOfDay(), 24000L);
        long t = day + target;
        if (t <= ow.getTimeOfDay()) t += 24000;
        ow.setTimeOfDay(t);
        Msg.ok(c.getSource(), "Heure du monde : " + target + ".");
        return 1;
    }

    private static int giveTears(CommandContext<ServerCommandSource> c, int count) throws CommandSyntaxException {
        ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
        ItemStack tears = fr.serveur.rpg.death.DeathSystem.createTear(count);
        if (!p.getInventory().insertStack(tears)) p.dropItem(tears, false);
        Msg.ok(c.getSource(), count + " Larme(s) de vie donnée(s) à " + p.getGameProfile().getName() + ".");
        return 1;
    }

    private static int giveLockpicks(CommandContext<ServerCommandSource> c, int count) throws CommandSyntaxException {
        ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
        ItemStack picks = fr.serveur.rpg.protection.Lockpick.create(count);
        if (!p.getInventory().insertStack(picks)) p.dropItem(picks, false);
        Msg.ok(c.getSource(), count + " crochet(s) donné(s) à " + p.getGameProfile().getName() + ".");
        return 1;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> moneyOp(String name, java.util.function.LongBinaryOperator op) {
        return literal(name).then(argument("joueur", StringArgumentType.word()).suggests(EconomyCommands.KNOWN_PLAYERS)
                .then(argument("montant", com.mojang.brigadier.arguments.LongArgumentType.longArg(0)).executes(c -> {
                    RpgState s = state(c);
                    var e = EconomyCommands.resolve(c, s);
                    long amount = com.mojang.brigadier.arguments.LongArgumentType.getLong(c, "montant");
                    fr.serveur.rpg.economy.Economy.admin(c.getSource().getServer(), e.getKey(),
                            op.applyAsLong(e.getValue().money, amount), true, c.getSource().getName());
                    Msg.ok(c.getSource(), e.getValue().name + " : " + fr.serveur.rpg.economy.Bank.format(e.getValue().money) + ".");
                    return 1;
                })));
    }

    private static boolean removeEverywhere(Job job, String bloc) {
        boolean removed = false;
        Iterator<JobConfig.Palier> it = JobConfig.data().metiers.getOrDefault(job.name(), new java.util.ArrayList<>()).iterator();
        while (it.hasNext()) {
            JobConfig.Palier p = it.next();
            removed |= p.blocs.remove(bloc);
            if (p.blocs.isEmpty()) it.remove();
        }
        return removed;
    }

    private static RpgState state(CommandContext<ServerCommandSource> c) {
        return RpgState.get(c.getSource().getServer());
    }

    /** /gm metier2 definir|retirer : le joueur peut être hors ligne (sa balise sera ajustée à sa connexion). */
    private static int setSideJob(CommandContext<ServerCommandSource> c, SideJob job) {
        RpgState s = state(c);
        var e = s.findByName(StringArgumentType.getString(c, "joueur"));
        if (e == null) {
            Msg.info(c.getSource(), Text.literal("Joueur inconnu.").formatted(Formatting.RED));
            return 0;
        }
        ServerPlayerEntity target = c.getSource().getServer().getPlayerManager().getPlayer(e.getKey());
        SideJobs.apply(target, e.getValue(), job);
        if (job != null) e.getValue().sideChosenOnce = true;
        s.markDirty();
        if (target != null) {
            Msg.send(target, job == null ? Text.literal("Le GM t'a retiré ton métier supplémentaire.")
                    : Text.literal("Le GM t'a attribué le métier supplémentaire ").append(Text.literal(job.displayName).formatted(job.color)).append("."));
        }
        Msg.ok(c.getSource(), e.getValue().name + (job == null ? " n'a plus de métier supplémentaire." : " est maintenant " + job.displayName + "."));
        return 1;
    }

    /** /gm bordure coin1|coin2 [x z] : sans coordonnées, prend la position du GM. */
    private static int borderCorner(CommandContext<ServerCommandSource> c, int corner, BlockPos pos) {
        ServerCommandSource src = c.getSource();
        BlockPos p = pos != null ? pos : BlockPos.ofFloored(src.getPosition());
        RpgState s = state(c);
        s.setBorderCorner(corner, src.getWorld().getRegistryKey().getValue().toString(), p);
        var box = fr.serveur.rpg.world.PlayableZone.box(s);
        Msg.ok(src, "Coin " + corner + " : X " + p.getX() + ", Z " + p.getZ() + "."
                + (box == null ? " Définis l'autre coin pour activer la zone." : " Zone jouable active — " + box.describe()));
        return 1;
    }

    private static Job job(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        Job job = Job.parse(StringArgumentType.getString(c, "metier"));
        if (job == null) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                Text.literal("Métier inconnu. Choix : " + String.join(", ", Job.ids()))).create();
        return job;
    }

    private static PlayerData.JobProgress requireJob(CommandContext<ServerCommandSource> c, ServerPlayerEntity p) throws CommandSyntaxException {
        PlayerData.JobProgress jp = state(c).player(p).current();
        if (jp == null) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                Text.literal(p.getGameProfile().getName() + " n'a pas de métier.")).create();
        return jp;
    }
}
