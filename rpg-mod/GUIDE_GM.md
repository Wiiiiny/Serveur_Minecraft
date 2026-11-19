# Survie RPG — Guide du GM

Mod Fabric 1.20.1 (`survierpg`), à installer **sur le serveur ET chez chaque joueur** (avec Nightfall
et Fabric API), car il ajoute des items.

## Items du mod (onglet créatif « Survie RPG »)

| Item | ID | Rôle |
|---|---|---|
| Pioche des ténèbres | `survierpg:pioche_tenebres` | Seul outil qui récolte la Pierre obscure. Incassable, jamais perdue |
| Larme de vie | `survierpg:larme_de_vie` | Réapparition immédiate après une mort de jour (clic droit ou `/larme`) |
| Crochet | `survierpg:crochet` | Tenter d'ouvrir le coffre d'un autre joueur |
| Outils officiels | `survierpg:outil_<metier>_<materiau>` | `metier` = mineur, bucheron, fermier, aventurier ; `materiau` = bois, pierre, fer, diamant, netherite |

Un outil officiel sorti du menu créatif ou donné par `/give` reçoit automatiquement son identifiant
unique dès qu'il arrive dans un inventaire (infobulle « ID xxxxxxxx »). Seul le GM peut en obtenir.
Toutes les commandes `/gm` demandent d'être opérateur (`/op <pseudo>`).
Les données sont dans `world/data/survierpg.dat` (sauvegardé avec le monde) ;
les réglages modifiables à la main dans `config/survierpg/` (`/gm recharger` après modification).

## Démarrage d'un joueur

1. À sa première connexion, il reçoit la **Pioche des ténèbres** (incassable, gardée à la mort).
2. Il choisit son métier : `/metier choisir mineur|bucheron|fermier|aventurier` (une seule fois).
3. Le GM lui donne son outil officiel : `/gm outil donner <joueur> survierpg:outil_mineur_bois`.

## Commandes joueurs

| Commande | Effet |
|---|---|
| `/metier` | Métier, niveau, progression, prestige, quêtes en cours |
| `/metier choisir <métier>` | Premier choix de métier (les changements passent par le GM) |
| `/prestige` puis `/prestige confirmer` | Au niveau 25 : retour niveau 1, +1 prestige |
| `/quetes` | Liste des quêtes |
| `/banque` · `deposer <n>\|tout` · `retirer <n>` · `virer <joueur> <n>` | Banque en poudres de glowstone |
| `/hdv` · `/hdv vendre <prix>` | Hôtel des ventes (objet en main ; recliquer sur sa vente pour la retirer) |
| `/boutique` | Boutique du GM |
| `/classement metiers\|richesse\|combats\|morts` | Classements |
| `/attente spectateur\|salle` · `/larme` | Après une mort de jour |

## Métiers et récolte

| Commande | Effet |
|---|---|
| `/gm metier definir <joueur> <métier>` · `retirer <joueur>` | Changer / retirer le métier (la progression par métier est conservée) |
| `/gm niveau <joueur> <1-25>` · `/gm xp <joueur> <n>` | Régler la progression |
| `/gm prestige definir <joueur> <n>` · `/gm prestige bonus <%>` | Prestige ; bonus d'argent des quêtes par prestige |
| `/gm outil donner <joueur> <outil>` | Outil officiel. Accepte `survierpg:outil_mineur_fer` ou un outil vanilla (`minecraft:iron_pickaxe` → remplacé par la pioche de Mineur en fer) |
| `/gm outil info` · `/gm outil revoquer` | Sur l'outil tenu en main |
| `/gm palier liste <métier>` | Blocs débloqués par niveau |
| `/gm palier ajouter <métier> <niveau> <xp> <bloc ou #tag>` · `retirer <métier> <bloc>` | Modifier les paliers |
| `/gm zone creer <nom> <métier> <x y z> <x y z>` · `supprimer <nom>` · `liste` | Zones de récolte réservées |
| `/gm quete donner <joueur> <texte>` · `valider <joueur> <id> [xp] [argent]` · `retirer` · `liste` | Quêtes (l'argent reçoit le bonus de prestige) |

Règles : à la main, seulement terre, feuilles, fleurs, sable/gravier/argile, neige, torches.
Tout le reste demande un **outil officiel de son métier**. Un bloc d'un autre métier est refusé,
un bloc de son métier demande le niveau du palier. Les blocs « ordinaires » (planches, verre…)
se cassent avec n'importe quel outil officiel de son métier. L'Aventurier gagne son XP en tuant
des créatures avec son épée officielle.

## Économie

| Commande | Effet |
|---|---|
| `/gm argent voir\|donner\|retirer\|definir <joueur> <n>` | Comptes en banque (même hors ligne) |
| `/gm boutique ajouter <prix>` (objet en main) · `prix <id> <prix>` · `retirer <id>` · `liste` | Boutique du GM |
| `/gm hdv retirer <id>` | Modérer une annonce |
| `/gm pioche <joueur>` | Redonner une Pioche des ténèbres |

Pierre obscure = sable des âmes (renommé par Nightfall). Uniquement récoltable avec la Pioche des
ténèbres. Recette : 4 Pierres obscures → 1 poudre de glowstone.

## Mairie, coffres, cambriolages

| Commande | Effet |
|---|---|
| `/gm protection creer <nom> <x y z> <x y z> [marge=5]` | Zone protégée : coins du bâtiment, +5 blocs autour |
| `/gm salle definir` | Salle d'attente des morts (là où tu te tiens) |
| `/gm coffre info\|liberer <x y z>` · `attribuer <x y z> <joueur>` | Propriété des coffres |
| `/gm crochet donner <joueur> [n]` · `/gm crochet chance <%>` | Crochets (50 % de réussite par défaut) |

Dans une zone protégée : aucune destruction (joueurs, explosions, feu), pas de pose, pas de PVP,
pistons désactivés dans un rayon de 13 blocs. Un coffre posé appartient à son poseur : personne
d'autre ne l'ouvre ni ne le casse, et les entonnoirs ne peuvent pas le vider.

## Créatures, morts, jour/nuit

| Commande | Effet |
|---|---|
| `/gm cycle jour <min>` · `nuit <min>` · `info` | Durées (12 min / 8 min par défaut) |
| `/gm cycle passer nuit\|jour` | Sauter à la nuit / au jour |
| `/gm larme <joueur> [n]` | Larmes de vie |
| `/gm reanimer <joueur>` | Faire revenir un mort tout de suite |

- Mort de **nuit** : on garde tout, retour au lit.
- Mort de **jour** : tout tombe. Tué par un joueur → seul le tueur peut ramasser ; sinon personne.
  Le mort attend la nuit (spectateur ou salle d'attente), sauf avec une Larme de vie.
- On ne peut pas dormir ; cliquer sur un lit définit le point de réapparition.
- Animaux +5 cœurs. Zombies, squelettes, araignées, pillards neutres (seul celui qu'on frappe
  riposte). Phantoms deux fois plus lents, deux fois moins d'attaques.

## Crafts, dimensions, événements

| Commande | Effet |
|---|---|
| `/gm craft liste` · `bloquer <objet ou #tag>` · `debloquer <...>` | Crafts contrôlés |
| `/gm dimension ouvrir\|fermer nether\|end` | Accès aux dimensions (fermées par défaut) |
| `/gm event lancer <combat\|recolte\|ressource\|special> <min> <titre>` | Événement avec barre de temps |
| `/gm event points <joueur> <n>` · `scores` · `terminer` | Points manuels, scores, fin + classement |

## Maintenance

`/gm diag <joueur>` (fiche complète), `/gm bypass` (ignorer les règles de récolte),
`/gm recharger` (relire `config/survierpg/`).
