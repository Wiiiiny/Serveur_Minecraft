# Survie RPG — Guide du GM

Mod Fabric 1.20.1 (`survierpg`), à installer **sur le serveur ET chez chaque joueur** (avec Nightfall
et Fabric API). Il faut être opérateur (`/op <pseudo>`) pour l'onglet **Maître du jeu** et les commandes `/gm`.

Presque tout se fait dans le **menu du serveur, touche B** (modifiable dans Options › Commandes).
Les commandes `/gm` ci-dessous restent disponibles pour tout ce que le menu ne couvre pas.

## Ce qui tourne tout seul

| Automatisation | Fonctionnement | Réglage |
|---|---|---|
| Choix du métier | Gratuit la première fois, l'outil en bois est donné aussitôt | — |
| Changement de métier | Le joueur paie (50 pièces par défaut), sans le GM | Menu GM › Économie |
| Forge | Le joueur fabrique ses outils officiels : matériaux + pièces + niveau | `automatisation.json` (`forge`) |
| Quêtes du jour | 3 par joueur selon son métier, validées et payées toutes seules, renouvelées à minuit | Menu GM › Automatisation |
| Événements | Un événement aléatoire toutes les 2 h (si 2 joueurs connectés), podium payé 100/50/25 | Menu GM › Automatisation |
| Boutique | Remplie au premier démarrage (crochet, Larme de vie, lanterne, torches, pain, membrane, lit) | Menu GM › Économie |
| Pioche des ténèbres | Donnée à la première connexion, jamais perdue | — |

## Mise en route (une seule fois)

1. **Mairie** : placez-vous au centre, menu B › Maître du jeu › Monde › *Zone protégée autour de moi*
   (rayon 10, 20 ou 30). Pour coller exactement à un bâtiment : `/gm protection creer mairie <x y z> <x y z>`.
2. **Salle d'attente des morts** : placez-vous dedans, Monde › *Définir ici*.
3. Vérifiez les durées jour/nuit (Monde) et la boutique (Économie).

## L'onglet Maître du jeu (touche B)

- **Joueurs** : liste de tous les joueurs connus (en ligne en premier). On tape une valeur, puis : + XP, = Niveau,
  + / − / = Pièces, Larmes et Crochets (la valeur = la quantité), Pioche, réanimer, changer de métier.
- **Monde** : passer au jour ou à la nuit, durées du jour et de la nuit, ouvrir/fermer le Nether et l'End,
  salle d'attente, zones protégées (création autour de soi, suppression).
- **Économie** : prix du changement de métier, chance de réussite des crochets, bonus de prestige,
  argent en circulation, boutique (prix saisi, retrait, ajout de l'objet tenu en main).
- **Automatisation** : quêtes par jour, multiplicateur des récompenses, événements automatiques
  (marche/arrêt, intervalle, durée, joueurs minimum, récompenses du podium), lancer ou terminer un événement.

Chaque réglage se tape dans son champ puis **OK** (le bouton s'allume seulement si la valeur a changé).

## Items du mod (onglet créatif « Survie RPG »)

| Item | ID | Rôle |
|---|---|---|
| Pièce d'or | `survierpg:piece_or` | La monnaie. 4 Éclats obscurs → 1 pièce à l'établi |
| Pierre obscure | `survierpg:pierre_obscure` | Minerai violet, en filons dans les 3 dimensions (nouveaux chunks seulement). Ne donne jamais le bloc |
| Éclat obscur | `survierpg:eclat_obscur` | Ce que donne la Pierre obscure à la Pioche des ténèbres |
| Pioche des ténèbres | `survierpg:pioche_tenebres` | Seul outil qui extrait la Pierre obscure. Incassable, jamais perdue |
| Larme de vie | `survierpg:larme_de_vie` | Réapparition immédiate après une mort de jour |
| Crochet | `survierpg:crochet` | Tenter d'ouvrir le coffre d'un autre joueur |
| Outils officiels | `survierpg:outil_<metier>_<materiau>` | `metier` = mineur, bucheron, fermier, aventurier ; `materiau` = bois, pierre, fer, diamant, netherite |

Un outil officiel sorti du menu créatif ou d'un `/give` reçoit son identifiant unique dès qu'il arrive
dans un inventaire (infobulle « ID xxxxxxxx »).

## Fichiers de configuration (`config/survierpg/`)

| Fichier | Contenu |
|---|---|
| `automatisation.json` | Prix du changement de métier, quêtes par jour, multiplicateur, événements (intervalle, durée, joueurs min, récompenses du podium), **coûts de la forge** par matériau (niveau, pièces, matériau de la tête, objets en plus) |
| `metiers.json` | Blocs cassables à la main, paliers de chaque métier (niveau, XP, blocs ou #tags), courbe d'XP |
| `crafts.json` | Crafts bloqués (objets ou #tags) |

Le menu GM écrit directement dans `automatisation.json`. Après une modification à la main : `/gm recharger`
(métiers) ou redémarrage (automatisation). Les données (joueurs, argent, coffres, zones, marché) sont dans
`world/data/survierpg.dat` : à sauvegarder avec le monde.

Quantité de Pierre obscure générée : `src/main/resources/data/survierpg/worldgen/placed_feature/pierre_obscure.json`
(`count`, 8 filons par chunk) et `configured_feature/pierre_obscure.json` (`size`, 6 blocs par filon), puis recompiler.

## Commandes joueurs

| Commande | Effet |
|---|---|
| Touche **B** | Le menu (profil, quêtes, forge, banque, marché, boutique, classements) |
| `/metier` · `/metier choisir <métier>` | Fiche et choix du métier |
| `/prestige` puis `/prestige confirmer` | Au niveau 25 |
| `/quetes` | Quêtes du GM |
| `/banque` · `deposer <n>\|tout` · `retirer <n>` · `virer <joueur> <n>` | Banque en Pièces d'or |
| `/hdv` · `/hdv vendre <prix>` | Hôtel des ventes |
| `/boutique` · `/classement <type>` | Boutique, classements |
| `/attente spectateur\|salle` · `/larme` | Après une mort de jour |

## Commandes GM

| Commande | Effet |
|---|---|
| `/gm metier definir <joueur> <métier>` · `retirer` | Métier (gratuit pour le GM) |
| `/gm niveau <joueur> <1-25>` · `/gm xp <joueur> <n>` · `/gm prestige definir\|bonus` | Progression |
| `/gm outil donner <joueur> <outil>` · `info` · `revoquer` | Outils officiels (un outil vanilla est converti) |
| `/gm palier liste\|ajouter\|retirer` | Paliers de métier |
| `/gm zone creer <nom> <métier> <x y z> <x y z>` · `supprimer` · `liste` | Zones de récolte réservées |
| `/gm quete donner <joueur> <texte>` · `valider <joueur> <id> [xp] [argent]` | Quêtes manuelles |
| `/gm argent voir\|donner\|retirer\|definir <joueur> <n>` | Comptes (même hors ligne) |
| `/gm boutique ajouter <prix>` · `prix <id> <prix>` · `retirer <id>` · `liste` | Boutique |
| `/gm hdv retirer <id>` · `/gm pioche <joueur>` | Modération, pioche |
| `/gm protection creer <nom> <x y z> <x y z> [marge=5]` · `/gm salle definir` | Mairie, salle d'attente |
| `/gm coffre info\|liberer <x y z>` · `attribuer <x y z> <joueur>` | Coffres |
| `/gm crochet donner <joueur> [n]` · `chance <%>` · `/gm larme <joueur> [n]` | Objets spéciaux |
| `/gm cycle jour\|nuit <min>` · `info` · `passer jour\|nuit` · `/gm reanimer <joueur>` | Jour/nuit, morts |
| `/gm craft liste\|bloquer\|debloquer` · `/gm dimension ouvrir\|fermer nether\|end` | Crafts, dimensions |
| `/gm event lancer <type> <min> <titre>` · `points` · `scores` · `terminer` | Événements |
| `/gm diag <joueur>` · `/gm bypass` · `/gm recharger` | Maintenance |

## Règles de jeu (rappel)

- À la main : terre, feuilles, fleurs, sable, gravier, argile, neige, torches. Le reste : outil officiel de son métier.
- Un bloc posé par un joueur ne donne ni XP ni progression de quête quand on le recasse (mémorisé avec le monde,
  y compris après un redémarrage).
- Mort de **nuit** : on garde tout. Mort de **jour** : tout tombe (au tueur, ou à personne), on attend la nuit
  (spectateur ou salle d'attente) sauf avec une Larme de vie.
- Mairie : pas de destruction, pas de PVP, pistons coupés à 13 blocs. Coffres : à leur propriétaire, entonnoirs bloqués
  (`mixin.block.hopper=false` doit rester dans `config/lithium.properties`).
- Le mod gère le cycle jour/nuit : passer par le menu GM ou `/gm cycle`, pas `/gamerule doDaylightCycle`.

## Développement

`gradlew build` (Java 17+) puis copier `build/libs/survierpg-1.0.0.jar` dans `Server/mods` et chez les joueurs.
`gradlew runShots` ouvre le monde de test `run/saves/rpgtest` et capture chaque onglet du menu dans `run/screenshots/`.
