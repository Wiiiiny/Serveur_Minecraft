# Graph Report - Serveur_Minecraft  (2026-11-25)

## Corpus Check
- 370 files · ~226,573 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 422 file(s) not represented in the graph (top: .jar 157, .nbt 147, .mca 59)
- WARNING: 422 of 837 files seen (50%) have no supported extractor and are missing from this graph entirely. It may look complete while representing only part of the codebase.

## Summary
- 2247 nodes · 8569 edges · 116 communities (44 shown, 72 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 311 edges (avg confidence: 0.85)
- Token cost: 285,268 input · 0 output

## Graph Freshness
- Built from commit: `f26462d7`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- .format
- Client Data & GUI Widgets
- JobConfig
- Docs: Discord Announce & GM Guide
- net.minecraft.server.command.ServerCommandSource
- list
- FishingBobberEntityMixin.java
- MarketItem
- RpgState
- Shadow Armor Material
- Nightfall World Rendering
- org.spongepowered.asm.mixin.injection.Inject
- Horror Ambience (client)
- Factions
- Craft Rules & Crafting Mixins
- Nightfall Mod Core
- net.minecraft.text.Text
- Night Fog Mixin
- Faction
- Tier
- Job
- .get
- DevShots Screenshot Tool
- net.minecraft.item.ItemStack
- Neutral Mobs Mixin
- net.minecraft.world.World
- SideJobRules
- SurvieRpgClient.java
- Sword & Axe Textures
- Hoe & Pickaxe Textures
- ThiefEntity.java
- Territories
- CoffresCaches
- SideJob
- net.minecraft.util.math.Vec3d
- GmEvents
- net.minecraft.server.network.ServerPlayerEntity
- AshlandsTerraBlender.java
- net.minecraft.item.Item
- Dark Material Textures
- net.minecraft.server.network.ServerPlayerEntity
- Eyes & Wisp Textures
- Tear & Coin Textures
- Lockpick Texture
- StatueEntity
- .get
- RaidClient.java
- ThiefModel
- Exotiques
- Source
- Ring
- .get
- ThiefEntity
- OfficialPickaxeItem
- net.minecraft.util.math.BlockPos
- Described
- .startCycle
- PlayerEntityRendererMixin.java
- Builder
- net.minecraft.block.BlockState
- RaidConfig
- Layout
- FireBlockMixin.java
- net.minecraft.util.Formatting
- AnvilScreenHandlerMixin.java
- BrewingStandBlockEntityMixin.java
- org.spongepowered.asm.mixin.injection.Inject
- DayCycle
- .get
- SombreConfig
- OfficialSwordItem
- TravelBeacon
- CLAUDE.md
- .claude/CLAUDE.md
- FactionConfig
- .player
- SombreState
- AnvilScreenHandlerMixin.java
- AutoConfig
- ChestMenu.java
- FactionChest
- Fear
- .use
- .register
- Mode
- RaidState
- ModBlocks.java
- .forge
- .register
- .build
- .scan
- PistonBlockMixin.java
- FactionItems
- TravelBeaconItem
- HandledScreenMixin.java

## God Nodes (most connected - your core abstractions)
1. `RpgState` - 102 edges
2. `RpgScreen` - 79 edges
3. `PlayerData` - 78 edges
4. `Faction` - 60 edges
5. `Msg` - 46 edges
6. `Factions` - 43 edges
7. `SideJob` - 41 edges
8. `Job` - 40 edges
9. `SurvieRpg` - 38 edges
10. `ThiefEntity` - 38 edges

## Surprising Connections (you probably didn't know these)
- `Événements automatiques (toutes les 2 h)` --semantically_similar_to--> `Événements aléatoires (podium 100/50/25)`  [INFERRED] [semantically similar]
  MESSAGE_DISCORD.md → rpg-mod/GUIDE_GM.md
- `Mettre à jour un mod (gradlew build)` --semantically_similar_to--> `Développement (gradlew build, gradlew runShots)`  [INFERRED] [semantically similar]
  MESSAGE_DISCORD.md → rpg-mod/GUIDE_GM.md
- `Partie admin / GM` --semantically_similar_to--> `Mise en route (mairie, salle d'attente)`  [INFERRED] [semantically similar]
  MESSAGE_DISCORD.md → rpg-mod/GUIDE_GM.md
- `Minecraft EULA (eula=true)` --conceptually_related_to--> `Annonce Discord : Mise à jour Survie RPG`  [AMBIGUOUS]
  Server/eula.txt → MESSAGE_DISCORD.md
- `Nightfall Shadow armor layer 1 texture (dark purple armor skin)` --semantically_similar_to--> `Eclat obscur texture (purple dark crystal shard)`  [INFERRED] [semantically similar]
  nightfall-mod/src/main/resources/assets/minecraft/textures/models/armor/nightfall_shadow_layer_1.png → rpg-mod/src/main/resources/assets/survierpg/textures/item/eclat_obscur.png

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Fichiers de configuration et données survierpg** — rpg_mod_guide_gm_automatisation_json, rpg_mod_guide_gm_metiers_json, rpg_mod_guide_gm_crafts_json, rpg_mod_guide_gm_survierpg_dat [EXTRACTED 1.00]
- **Cycle jour/nuit : brume, soleil brûlant, protections** — message_discord_nuit_brume, message_discord_soleil_brulant, message_discord_capuche_d_ombre, message_discord_lanterne, nightfall_mod_src_main_java_fr_serveur_nightfall_nightfallconfig [EXTRACTED 1.00]
- **Chaîne économique : Pioche des ténèbres → Pierre obscure → Éclat obscur → Pièce d'or → Banque** — rpg_mod_pioche_tenebres, rpg_mod_pierre_obscure, rpg_mod_eclat_obscur, rpg_mod_piece_or, rpg_mod_guide_gm_banque [EXTRACTED 1.00]
- **Pierre obscure dark material set (ore, shard, pickaxe)** — rpg_mod_src_main_resources_assets_survierpg_textures_block_pierre_obscure_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_eclat_obscur_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_pioche_tenebres_texture [INFERRED 0.85]
- **Adventurer Tool (Sword) Material Tiers** — rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_aventurier_bois_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_aventurier_pierre_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_aventurier_fer_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_aventurier_diamant_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_aventurier_netherite_texture [INFERRED 0.95]
- **Lumberjack Tool (Axe) Material Tiers** — rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_bucheron_bois_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_bucheron_pierre_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_bucheron_fer_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_bucheron_diamant_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_bucheron_netherite_texture [INFERRED 0.95]
- **Farmer Tool (hoe) material tiers (wood to netherite)** — rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_fermier_bois_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_fermier_pierre_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_fermier_fer_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_fermier_diamant_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_fermier_netherite_texture [INFERRED 0.95]
- **Miner Tool (pickaxe) material tiers (wood to netherite)** — rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_mineur_bois_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_mineur_pierre_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_mineur_fer_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_mineur_diamant_texture, rpg_mod_src_main_resources_assets_survierpg_textures_item_outil_mineur_netherite_texture [INFERRED 0.95]

## Communities (116 total, 72 thin omitted)

### Community 0 - ".format"
Cohesion: 0.10
Nodes (5): EconomyCommands, MarketMenus, Result, Actions, RpgNetwork

### Community 1 - "Client Data & GUI Widgets"
Cohesion: 0.05
Nodes (27): ClientData, RareteClient, RpgScreen, Hot, LoreBox, Style, DANGER, NORMAL (+19 more)

### Community 2 - "JobConfig"
Cohesion: 0.16
Nodes (7): JobConfig, Data, Palier, Tier, SideJobConfig, Data, Tier

### Community 3 - "Docs: Discord Announce & GM Guide"
Cohesion: 0.06
Nodes (49): Annonce Discord : Mise à jour Survie RPG, Capuche d'ombre, Coffres protégés et cambriolages, Combat et créatures neutres, Économie (Pierre obscure → Éclat → Pièce d'or, banque), Événements automatiques (toutes les 2 h), Fabric API 0.92.12+1.20.1, Installation requise (Fabric 1.20.1, survierpg, nightfall, fabric-api) (+41 more)

### Community 4 - "net.minecraft.server.command.ServerCommandSource"
Cohesion: 0.10
Nodes (5): EconomyAdminCommands, FactionCommands, GmCommands, RankingCommands, SombreCommands

### Community 9 - "RpgState"
Cohesion: 0.16
Nodes (4): PlayerData, RpgState, Bank, Msg

### Community 10 - "Shadow Armor Material"
Cohesion: 0.13
Nodes (6): ShadowArmorMaterial, Type, COMBAT, RECOLTE, RESSOURCE, SPECIAL

### Community 12 - "org.spongepowered.asm.mixin.injection.Inject"
Cohesion: 0.10
Nodes (11): InGameHudMixin, WorldRendererMixin, BrewingPotionSlotMixin, InGameHudMixin, EntityRemoveMixin, ItemEntityMixin, ItemStackMixin, MobEntityMixin (+3 more)

### Community 13 - "Horror Ambience (client)"
Cohesion: 0.19
Nodes (3): HorrorAmbience, NightEffects, Wisp

### Community 15 - "Craft Rules & Crafting Mixins"
Cohesion: 0.15
Nodes (4): CraftRules, Data, CraftingScreenHandlerMixin, SmithingScreenHandlerMixin

### Community 19 - "Faction"
Cohesion: 0.07
Nodes (18): Banners, Faction, Layer, Member, MissionProgress, Rank, CHEF, MEMBRE (+10 more)

### Community 20 - "Tier"
Cohesion: 0.25
Nodes (6): Tier, BOIS, DIAMANT, FER, NETHERITE, PIERRE

### Community 21 - "Job"
Cohesion: 0.08
Nodes (10): Zone, OfficialAxeItem, OfficialHoeItem, OfficialToolItem, Job, AVENTURIER, BUCHERON, FERMIER (+2 more)

### Community 22 - ".get"
Cohesion: 0.19
Nodes (3): FactionState, War, Wars

### Community 26 - "net.minecraft.item.ItemStack"
Cohesion: 0.24
Nodes (3): SombreItems, DetecteurItem, EclatItem

### Community 31 - "Sword & Axe Textures"
Cohesion: 0.29
Nodes (10): Adventurer Tool (Wooden Sword) Texture, Adventurer Tool (Diamond Sword) Texture, Adventurer Tool (Iron Sword) Texture, Adventurer Tool (Netherite Sword) Texture, Adventurer Tool (Stone Sword) Texture, Lumberjack Tool (Wooden Axe) Texture, Lumberjack Tool (Diamond Axe) Texture, Lumberjack Tool (Iron Axe) Texture (+2 more)

### Community 32 - "Hoe & Pickaxe Textures"
Cohesion: 0.29
Nodes (10): Wooden Farmer Tool (hoe) texture, Diamond Farmer Tool (hoe) texture, Iron Farmer Tool (hoe) texture, Netherite Farmer Tool (hoe) texture, Stone Farmer Tool (hoe) texture, Wooden Miner Tool (pickaxe) texture, Diamond Miner Tool (pickaxe) texture, Iron Miner Tool (pickaxe) texture (+2 more)

### Community 35 - "CoffresCaches"
Cohesion: 0.11
Nodes (10): CoffresCaches, Trap, Type, BOSS, CLASSIQUE, ENFOUI, EXOTIQUE, LEGENDAIRE (+2 more)

### Community 36 - "SideJob"
Cohesion: 0.15
Nodes (9): SideJob, ALCHIMISTE, CONSTRUCTEUR, ENCHANTEUR, INGENIEUR, PACIFICATEUR, PECHEUR, VOYAGEUR (+1 more)

### Community 39 - "net.minecraft.server.network.ServerPlayerEntity"
Cohesion: 0.15
Nodes (3): MondeSombre, Obscurcie, Personnage

### Community 40 - "AshlandsTerraBlender.java"
Cohesion: 0.09
Nodes (4): PlacedBlocks, Guardian, AshlandsTerraBlender, AshlandsRegion

### Community 42 - "Dark Material Textures"
Cohesion: 0.50
Nodes (5): Nightfall Shadow armor layer 1 texture (dark purple armor skin), Shadow Hood texture (dark hooded helmet item), Pierre obscure block texture (dark ore block with purple crystals), Eclat obscur texture (purple dark crystal shard), Pioche tenebres texture (purple dark pickaxe)

### Community 45 - "net.minecraft.server.network.ServerPlayerEntity"
Cohesion: 0.13
Nodes (3): DailyQuests, JobService, Progression

### Community 54 - ".get"
Cohesion: 0.12
Nodes (5): RaidBuilder, Doors, Fill, Job, RaidWorld

### Community 56 - "ThiefModel"
Cohesion: 0.14
Nodes (4): StatueModel, StatueRenderer, ThiefModel, ThiefRenderer

### Community 58 - "Source"
Cohesion: 0.06
Nodes (28): Economy, Source, AMENDE, ANNULATION, BOUTIQUE, CHANGEMENT_METIER, COFFRE, DEPOT (+20 more)

### Community 60 - "Ring"
Cohesion: 0.18
Nodes (5): Ring, COEUR, EXTERIEUR, INTERMEDIAIRE, RoomLibrary

### Community 61 - ".get"
Cohesion: 0.21
Nodes (6): Lumiere, Phase, CYCLE, JOUR, NUIT, PREPARATION

### Community 64 - "net.minecraft.util.math.BlockPos"
Cohesion: 0.15
Nodes (3): Lockpick, RaidMobs, BossType

### Community 66 - ".startCycle"
Cohesion: 0.10
Nodes (5): Extraction, RaidCycle, RaidDeath, ReturnBeacon, Channel

### Community 68 - "Builder"
Cohesion: 0.22
Nodes (3): ExampleRooms, B, Builder

### Community 69 - "net.minecraft.block.BlockState"
Cohesion: 0.25
Nodes (3): Room, Marker, Schematic

### Community 70 - "RaidConfig"
Cohesion: 0.13
Nodes (6): Loot, RaidConfig, Data, LootData, LootEntry, Threat

### Community 71 - "Layout"
Cohesion: 0.17
Nodes (7): Layout, Placement, Kind, ARENE, ARRIVEE, EXTRACTION, NORMALE

### Community 73 - "net.minecraft.util.Formatting"
Cohesion: 0.15
Nodes (6): Rarete, COMMUN, EPIQUE, EXOTIQUE, PEU_COMMUN, RARE

### Community 76 - "org.spongepowered.asm.mixin.injection.Inject"
Cohesion: 0.17
Nodes (4): BlockItemMixin, EnchantmentScreenHandlerMixin, HopperBlockEntityMixin, ServerPlayerPortalMixin

### Community 78 - ".get"
Cohesion: 0.13
Nodes (4): EconomyConfig, Data, EconomyTicker, Snapshot

### Community 79 - "SombreConfig"
Cohesion: 0.19
Nodes (11): SombreConfig, Coffres, Data, Decouverte, Effet, Exotique, Lot, Lumiere (+3 more)

### Community 84 - "FactionConfig"
Cohesion: 0.33
Nodes (4): FactionConfig, Article, Data, Mission

### Community 87 - ".player"
Cohesion: 0.12
Nodes (3): DeathSystem, ObscureStone, HarvestRules

### Community 88 - "SombreState"
Cohesion: 0.22
Nodes (7): SombreState, Coffre, Exo, Statut, ABSENT, EN_JEU, PERDU

### Community 90 - "AutoConfig"
Cohesion: 0.43
Nodes (3): AutoConfig, Data, ForgeTier

### Community 96 - "Mode"
Cohesion: 0.40
Nodes (5): Mode, APPROCHE, COMPAGNON, FUITE, SUIT

### Community 97 - "RaidState"
Cohesion: 0.14
Nodes (10): PasteRoom, RaidState, Area, Chest, Corpse, Spot, Status, FERME (+2 more)

## Ambiguous Edges - Review These
- `Annonce Discord : Mise à jour Survie RPG` → `Minecraft EULA (eula=true)`  [AMBIGUOUS]
  Server/eula.txt · relation: conceptually_related_to

## Knowledge Gaps
- **114 isolated node(s):** `PROFIL`, `PERSO`, `FACTION`, `METIER2`, `QUETES` (+109 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 390 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **72 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `RpgScreen` connect `Client Data & GUI Widgets` to `SideJob`, `net.minecraft.item.Item`, `net.minecraft.text.Text`, `DevShots Screenshot Tool`, `net.minecraft.item.ItemStack`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **What connects `PROFIL`, `PERSO`, `FACTION` to the rest of the system?**
  _114 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `.format` be split into smaller, more focused modules?**
  _Cohesion score 0.1026827012025902 - nodes in this community are weakly interconnected._
- **Why does `Source` connect `Source` to `.format`?**
  _High betweenness centrality (0.040) - this node is a cross-community bridge._
- **Should `Client Data & GUI Widgets` be split into smaller, more focused modules?**
  _Cohesion score 0.05032149846239866 - nodes in this community are weakly interconnected._
- **Why does `RpgState` connect `RpgState` to `.format`, `Client Data & GUI Widgets`, `net.minecraft.server.command.ServerCommandSource`, `list`, `MarketItem`, `Job`, `CoffresCaches.java`, `SurvieRpg.java`, `ThiefEntity.java`, `net.minecraft.util.math.Vec3d`, `net.minecraft.item.Item`, `Source`, `RaidBuilder.java`, `net.minecraft.util.math.BlockPos`, `RaidConfig`, `FireBlockMixin.java`, `org.spongepowered.asm.mixin.injection.Inject`, `.get`, `.player`, `.build`, `.scan`, `PistonBlockMixin.java`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._

### Low-confidence Hints
_AMBIGUOUS edges — the extractor was unsure. Verify before acting on these._

- **What is the exact relationship between `Annonce Discord : Mise à jour Survie RPG` and `Minecraft EULA (eula=true)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._