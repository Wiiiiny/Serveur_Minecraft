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
| Métier supplémentaire | Un 2e métier au choix (onglet « 2e métier ») : gratuit la première fois, puis 50 pièces | `metiers2.json` (`prixChangement`) |
| Balise du voyageur | Donnée en devenant Voyageur, reprise s'il change de métier, jamais perdue à la mort | `metiers2.json` |
| Forge | Le joueur fabrique ses outils officiels : matériaux + pièces + niveau | `automatisation.json` (`forge`) |
| Quêtes du jour | 3 par joueur selon son métier, validées et payées toutes seules, renouvelées à minuit | Menu GM › Automatisation |
| Événements | Un événement aléatoire toutes les 2 h (si 2 joueurs connectés), podium payé 100/50/25 | Menu GM › Automatisation |
| Boutique | Remplie au premier démarrage, **prix dynamiques** (offre et demande), rachat des poissons | Menu GM › Économie |
| Paie des métiers | Chaque action de métier rapporte des pièces (0,05 par XP), plafonné par jour avec rendement dégressif | `economie.json` |
| Régulateur | Chaque heure : si l'argent par joueur actif dépasse la cible, les gains baissent et les taxes montent (et inversement) | `economie.json` |
| Anomalies | Un joueur qui gagne 10× la médiane en 1 h est gelé automatiquement, alerte aux GM connectés | `/gm economie degeler` |
| Sauvegardes | Soldes sauvegardés toutes les 15 min, journal de chaque transaction | `/gm economie ...` |
| Pioche des ténèbres | Donnée à la première connexion, jamais perdue | — |
| Nuit permanente | Le monde reste à minuit ; seul un Éclat de Lumière appelle le jour (annonce, 60 s, puis jour) | Menu GM › Monde |
| Coffres cachés | Posés au hasard autour des joueurs (6 + 3 par joueur, 40 au plus), ils restent jusqu'à leur ouverture | `monde_sombre.json` |
| Factions | Votes, missions du jour, guerres (début 5 min après la déclaration, attaques de jour), fin de guerre, paix : tout est automatique | `factions.json` |
| Exotiques | Un seul exemplaire de chacun ; duplicatas supprimés, perte détectée, retour dans un coffre exotique 12 h après | `objets_rares.json` |

## Mise en route (une seule fois)

1. **Mairie** : placez-vous au centre, menu B › Maître du jeu › Monde › *Zone protégée autour de moi*
   (rayon 10, 20 ou 30). Pour coller exactement à un bâtiment : `/gm protection creer mairie <x y z> <x y z>`.
2. **Salle d'attente des morts** : placez-vous dedans, Monde › *Définir ici*.
3. Vérifiez les durées jour/nuit (Monde) et la boutique (Économie).
4. **Raid** : au hub, placez-vous à l'endroit du portail du raid, menu B › Raid › *Portail ici* (et *Hub ici* là où les joueurs
   reviennent). La zone se génère toute seule au démarrage du serveur.
5. **Zone jouable** (seul le Voyageur peut en sortir) : placez-vous sur un coin du monde, Monde › *Coin 1 ici*,
   puis sur le coin opposé, *Coin 2 ici*. Ou `/gm bordure coin1 <x> <z>` puis `/gm bordure coin2 <x> <z>`.
   Tant que les deux coins ne sont pas posés, il n'y a aucune limite.

## L'onglet Maître du jeu (touche B)

- **Joueurs** : liste de tous les joueurs connus (en ligne en premier). On tape une valeur, puis : + XP, = Niveau,
  + / − / = Pièces, Larmes et Crochets (la valeur = la quantité), Pioche, réanimer, changer de métier,
  et en bas la rangée d'icônes du **métier supplémentaire** (la barrière le retire).
- **Monde** : passer au jour ou à la nuit, durées du jour et de la nuit, ouvrir/fermer le Nether et l'End,
  salle d'attente, **zone jouable** (coin 1, coin 2, retirer), zones protégées (création autour de soi, suppression).
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
| Éclat de Lumière | `survierpg:eclat_lumiere` | Prisme jaune : appelle le jour sur le monde (Obscurcie, coffres légendaires, de boss, magiques) |
| Étendard de revendication | `survierpg:etendard` | Boutique de faction : le chef ou un officier le plante pour revendiquer un territoire (le premier devient la base) |
| Cœur de faction | `survierpg:coeur_faction` | Posé tout seul au centre de la base. Indestructible, sauf en guerre (60 coups aux heures d'attaque) |
| Détecteur d'ombre | `survierpg:detecteur_ombre` | Obscurcie tous les 10 niveaux : activé, il pointe le coffre caché le plus proche pendant 2 min puis disparaît |
| Balise du voyageur | `survierpg:balise` | Voyageur : 10 s immobile pour franchir la limite de la zone jouable (et revenir). Recharge 30 min |
| Outils officiels | `survierpg:outil_<metier>_<materiau>` | `metier` = mineur, bucheron, fermier, aventurier ; `materiau` = bois, pierre, fer, diamant, netherite |

Un outil officiel sorti du menu créatif ou d'un `/give` reçoit son identifiant unique dès qu'il arrive
dans un inventaire (infobulle « ID xxxxxxxx »).

## Fichiers de configuration (`config/survierpg/`)

| Fichier | Contenu |
|---|---|
| `automatisation.json` | Prix du changement de métier, quêtes par jour, multiplicateur, événements (intervalle, durée, joueurs min, récompenses du podium), **coûts de la forge** par matériau (niveau, pièces, matériau de la tête, objets en plus) |
| `metiers.json` | Blocs cassables à la main, paliers de chaque métier (niveau, XP, blocs ou #tags), courbe d'XP |
| `crafts.json` | Crafts bloqués (objets ou #tags) |
| `factions.json` | Factions : longueur du nom, rayon (20 + 5 par niveau), distance entre factions (50), courbe d'XP et sources d'XP, pièces par niveau, Cœur (points de vie, regain), guerre (délai, perte d'XP, points, jours de paix), votes (durée, délai entre élections), téléportation à la base, boutique (objet, quantité, prix, niveau requis ; « extension » = +2 blocs de rayon), missions du jour |
| `monde_sombre.json` | Monde sombre : préparation de l'Éclat, niveau du personnage (niveau max, courbe d'XP, perte à la mort, pièces par niveau, sources d'XP), Obscurcie (courbe, paliers d'Éclat et de détecteur, sources d'XP, ressources), coffres cachés (nombre, distances, poids des types, pièges, butin, pièces, Éclats, gardien), objets toujours connus de la boutique |
| `objets_rares.json` | Rareté de chaque objet (contour gris, vert, bleu, violet) et liste des objets exotiques (nom, lore, objet, enchantements, prix, effets portés), délai de réintroduction |
| `raid.json` | Raid : durée du cycle, niveaux de menace (frais, statues, PV des gardiens, bonus), taille de la zone, anneaux, nombre d'arènes et d'arrivées, coffres piégés, coffre de corps, balise, extraction, voleurs, réparation du Brise-statue |
| `loot.json` | Butin des coffres du raid par palier (objet, poids, quantités, enchantement) et valeur des objets pour le bonus d'extraction |
| `raid/salles/` | Les salles du raid (.schem), dans `exterieur/`, `intermediaire/`, `coeur/` |
| `economie.json` | Économie : paie des métiers, plafonds par source et rendement dégressif, taxe et durée de l'HDV, prix plancher/plafond, virements (illimités ou plafond, délai 24 h), conversion des pièces, boutique (écart achat/vente, retour du stock), régulateur (cible, pas, bornes), anomalies, sauvegardes |
| `metiers2.json` | Métiers supplémentaires : prix du changement, blocs réservés à l'Ingénieur et au Constructeur (niveau, XP), plafond d'XP de pose, niveaux de la table d'enchantement, recharge de la balise, primes et amende du Pacificateur, niveaux de l'Alchimiste, XP du Pêcheur |

Le menu GM écrit directement dans `automatisation.json`. Après une modification à la main : `/gm recharger`
(métiers et métiers supplémentaires) ou redémarrage (automatisation). Les données (joueurs, argent, coffres, zones, marché) sont dans
`world/data/survierpg.dat` : à sauvegarder avec le monde.

Quantité de Pierre obscure générée : `src/main/resources/data/survierpg/worldgen/placed_feature/pierre_obscure.json`
(`count`, 8 filons par chunk) et `configured_feature/pierre_obscure.json` (`size`, 6 blocs par filon), puis recompiler.

## Commandes joueurs

| Commande | Effet |
|---|---|
| Touche **B** | Le menu (profil, quêtes, forge, banque, marché, boutique, classements) |
| `/metier` · `/metier choisir <métier>` | Fiche et choix du métier |
| `/metier2` · `/metier2 choisir <métier>` | Fiche et choix du métier supplémentaire |
| `/faction` (ou `/f`) · `creer <nom>` · `inviter <joueur>` · `accepter <faction>` · `quitter` · `info [faction]` · `liste` | Factions (tout se fait aussi dans menu B › Faction) |
| `/f base` · `base deplacer` · `coffre` · `deposer <montant>` · `retirer <montant>` | Téléportation à la base (5 s immobile, recharge 10 min), déplacer la base (chef), coffre commun (à la base), trésor |
| `/f guerre declarer <faction>` · `capituler` · `accepter` · `refuser` · `nuit <true\|false>` | Guerre (chef seul) |
| `/personnage` | Niveau du personnage et Obscurcie (aussi dans menu B › Personnage) |
| `/lore [joueur]` | Lire son histoire, ou celle d'un joueur si elle est publique (on l'écrit dans menu B › Personnage) |
| `/raid` · `/raid entrer` · `/raid groupe inviter <joueur>\|accepter\|quitter` · `/raid reparer` · `/raid compagnon` | Zone de raid (aussi dans menu B › Raid) |
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
| `/gm metier2 definir <joueur> <métier>` · `retirer <joueur>` · `niveau <joueur> <1-25>` | Métier supplémentaire (joueur même hors ligne) |
| `/gm bordure coin1 [x z]` · `coin2 [x z]` · `retirer` · `info` | Zone jouable (sans coordonnées : votre position) |
| `/gm lumiere jour` · `nuit` · `permanente <true\|false>` · `info` | Jour invoqué tout de suite, retour de la nuit, nuit permanente ou cycle normal, état |
| `/gm personnage <joueur> <n>` · `/gm obscurcie <joueur> <n>` | Niveau du personnage, niveau d'Obscurcie (XP remise à zéro, sans récompense de palier) |
| `/gm eclat <joueur> [n]` · `/gm detecteur <joueur>` | Donner des Éclats de Lumière, un Détecteur d'ombre |
| `/gm coffres info` · `generer [n]` · `ici <type> [piégé]` · `vider` | Coffres cachés : liste (les 5 plus proches), en poser autour des joueurs, en poser un devant soi, tout retirer |
| `/gm exotique liste` · `donner <id> [joueur]` · `perdu <id>` · `reintroduire <id>` | Exotiques : état, don (l'ancien exemplaire devient invalide), marquer perdu, autoriser le retour tout de suite |
| `/gm lore <joueur>` · `effacer` | Lire l'histoire d'un joueur (même privée), l'effacer |
| `/gm faction liste` · `supprimer <faction>` · `niveau "<faction>" <n>` · `tresor "<faction>" <±montant>` · `guerre terminer <faction>` · `coeur <faction>` | Factions : dissoudre, fixer le niveau, ajuster le trésor, arrêter une guerre sans vainqueur, remettre un Cœur à neuf |
| `/gm raid info` · `hub` · `portail` · `regenerer` · `niveau <n>` | Zone de raid : état, point de retour, portail d'entrée, nouvelle génération, niveau de menace imposé |
| `/gm raid salles` · `exemples` · `spawn <statue\|voleur\|gardien>` | Relire les salles, écrire les salles d'exemple, faire apparaître une créature devant soi |
| `/gm economie audit` · `mesurer` | État de l'économie (masse, médiane, régulateur, flux, comptes gelés, alertes) |
| `/gm economie geler <joueur>` · `degeler <joueur>` | Bloque / débloque les dépenses et virements d'un compte |
| `/gm economie transactions <joueur> [n]` · `annuler <id>` · `annuler-joueur <joueur> <minutes>` | Journal et annulation de transactions |
| `/gm economie sauvegardes` · `sauvegarder` · `restaurer <sauvegarde> [joueur]` | Sauvegardes des soldes |
| `/gm economie multiplicateurs <gains> <taxes>` | Forcer le régulateur (il reprend la main l'heure suivante) |
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

## Économie automatisée

Tout l'argent en banque passe par un seul circuit, journalisé dans `world/survierpg/economie/transactions-AAAA-MM-JJ.jsonl`.

- **Ce qui crée de l'argent** (plafonné par jour et par joueur, `plafondsJour`) : la paie des métiers (`metier`), les quêtes (`quete`),
  les événements, les primes du Pacificateur, les reventes au serveur (`rachat`), et plus tard le raid. Au-delà du plafond, le gain
  est réduit de moitié, puis de 90 % au-delà du double. Les pièces physiques (Pierre obscure) entrent en banque par dépôt.
- **Ce qui en retire** : boutique, forge, changements de métier, taxe de 5 % sur l'HDV, amendes, et plus tard les frais du raid.
- **Boutique** : prix = base × (stock cible / stock)^0,25, entre la moitié et le double de la base. Acheter fait monter le prix,
  revendre le fait baisser, le stock revient vers sa cible de 5 % par heure, et une transaction ne peut pas bouger le prix de plus de 10 %.
  Le serveur rachète 25 % moins cher qu'il ne vend. Dans Menu GM › Économie : prix de base, *Vend* / *Rach* (vendu / racheté), suppression.
- **HDV** : taxe de 5 % (× multiplicateur du régulateur), annonces rendues au bout de 7 jours, et pour un objet présent en boutique,
  prix entre 0,25× et 4× sa valeur (contre le blanchiment entre comptes).
- **Virements** : illimités (`virementsIllimites`), impossibles pendant les 24 premières heures d'un compte.
- **Banque** : 2 304 pièces au plus par dépôt ou retrait.
- **Régulateur** (chaque heure) : cible de 1 500 pièces par joueur actif (connecté dans les 7 jours). Au-dessus de +20 %, gains −2 % et
  taxes +2 % par heure (bornes 70 %–130 %) ; en dessous de −20 %, l'inverse. Métriques dans `economie/metriques.jsonl`.
- **Compte gelé** : plus de dépenses ni de virements, les gains continuent d'arriver. Menu GM › Joueurs › *Geler / Dégeler*.

## Monde sombre

Le monde reste dans la **nuit permanente** (minuit, brume de Nightfall). Le jour ne vient que si un joueur brise un
**Éclat de Lumière** : annonce à tout le serveur, 60 s pour se préparer, puis jour pendant la « Durée du jour » de l'onglet
Monde (le soleil traverse le ciel et brûle comme avant). Ensuite la nuit retombe, et aucun Éclat ne peut servir avant la
« Durée de la nuit » (nuit minimale). Un seul jour à la fois : un Éclat utilisé trop tôt est refusé et n'est pas consommé.
Les règles de mort de jour (tout tombe, attente de la nuit) ne s'appliquent donc que pendant ces jours invoqués.
L'interrupteur « Nuit permanente » (Monde) rend l'ancien cycle jour/nuit automatique.

- **Niveau du personnage** (menu B › Personnage) : sans classe, distinct des métiers. XP : monstres, quêtes, coffres cachés,
  découvertes, niveaux de métier et 10 % de l'XP des métiers. 100 niveaux, pièces à chaque niveau (10 × le niveau).
  Mourir coûte 10 % de l'XP du niveau en cours, jamais un niveau.
- **Obscurcie** : métier ouvert à tous, sans niveau maximum. XP : ressources (minerais, Pierre obscure), monstres tués de nuit,
  quêtes, exploration de nuit (2 XP tous les 64 blocs, 150 par jour), Éclats, détecteurs, coffres cachés, 10 % de l'XP des métiers.
  Tous les 5 niveaux : **une** chance sur deux d'obtenir un Éclat de Lumière. Tous les 10 niveaux : un Détecteur d'ombre.
- **Coffres cachés** : 7 types — classique, enfoui (sous 2 blocs), maudit (sol d'âmes, âmes qui s'en échappent), exotique
  (sous 4 blocs, contient un exotique disponible), légendaire (sous 6 blocs), de boss (réveille un Gardien à abattre d'abord),
  magique (lueur d'enchantement). Ils ne se cassent pas : clic droit pour les ouvrir. Tout le groupe présent (`/raid groupe`,
  12 blocs) les ouvre ensemble, **un seul membre tiré au sort remporte le butin** (dans son inventaire, ou au sol à son seul nom) ;
  tous gagnent l'XP. Butin selon le type, le hasard et le **niveau de la zone** (+1 tous les 600 blocs depuis le point
  d'apparition, jusqu'à 5). Pièges : explosion sans dégât aux blocs (enfoui), malédiction et esprits (maudit), crocs et lévitation
  (magique), monstres (classique, exotique, légendaire). Le coffre ouvert disparaît, un autre apparaît ailleurs.
- **Raretés** : contour autour des objets dans les inventaires, la barre d'objets et le menu B — Peu commun gris, Commun vert,
  Rare bleu, Épique violet, Exotique noir avec des étincelles jaunes (et particules jaunes au sol ou en main). La rareté de
  chaque objet se règle dans `objets_rares.json`.
- **Exotiques** : 10 objets uniques (Lame du Crépuscule, Couronne de l'Éclipse, Lanterne éternelle…), incassables, avec nom, lore,
  valeur et effets quand on les porte. Un seul exemplaire à la fois : un duplicata (même numéro, ou ancien numéro) est supprimé
  dès qu'il est vu dans un inventaire ou un coffre de l'Ender, et les GM connectés sont prévenus. Détruit au sol ou disparu
  après 5 minutes par terre = **perdu** ; il peut revenir 12 h plus tard dans un coffre exotique (nouveau numéro).
- **Boutique de découverte** : on n'achète un objet qu'après l'avoir eu une fois dans son inventaire ; les objets inconnus sont
  masqués (« Objet inconnu »), les exotiques ne s'achètent jamais. Les objets de base (lanterne, torches, pain, lit, Larme,
  Crochet, Balise de retour, Brise-statue) restent toujours connus (`decouverte.toujoursConnus`).
- **Lore** : chaque joueur écrit l'histoire de son personnage (1 500 caractères), privée ou publique (`/lore <joueur>`).
- **Journaux** (`world/survierpg/journaux/`, un fichier par jour) : `exotiques` (apparitions, porteurs, pertes, duplicatas),
  `coffres` (apparitions, ouvertures, pièges), `recompenses`, `lumiere`, `gm` (actions GM du monde sombre). Les joueurs ne voient rien.

## Factions

Onglet **Faction** du menu B (sous-pages Accueil, Membres, Carte, Trésor, Guerre, Bannière, Podium), ou `/faction`.

- **Création** gratuite (nom de 3 à 20 caractères) ; le fondateur est **Chef**. Tout membre peut inviter, nombre de membres
  illimité, pas d'alliance. Rangs : Chef, Officier, Vétéran, Membre, Recrue (le chef monte et descend les rangs ; Officier au plus).
- **Votes** (adoptés à plus de 50 % des membres, ouverts 24 h) : exclusion (le chef peut aussi exclure directement), élection du
  chef (à tout moment, une élection par 24 h — sert aussi quand le chef est inactif), abandon d'un territoire. Les dépenses ne
  sont pas votées : tout est inscrit dans l'historique.
- **Niveau** sans limite ni prestige. XP : monstres tués par les membres, coffres cachés (bonus en groupe), quêtes du jour, niveaux
  de métier, Éclats de Lumière, dons au trésor et achats à la boutique, missions du jour (3 par jour), victoires. Chaque niveau :
  +5 blocs de rayon, pièces versées au trésor (100 × niveau), nouveaux articles à la boutique.
- **Territoires** : un Étendard (boutique de faction) planté par le chef ou un officier revendique un cercle de 20 blocs (+5 par
  niveau, +2 par extension achetée), à 50 blocs au moins des territoires des autres factions, hors mairie et hors zone de raid.
  Nombre illimité. Le premier est la **base principale** : le Cœur de faction et la bannière y sont posés (2 blocs devant le
  joueur), on y réapparaît si on n'a pas de lit, on s'y téléporte (`/f base`), le coffre commun s'y ouvre (ou en touchant le Cœur).
- **Protection** : dans un territoire, seuls les membres cassent, posent, ouvrent coffres, portes et mécanismes, touchent aux cadres
  et animaux ; les explosions ne cassent rien ; **pas de PvP hors guerre**. Entrer et sortir s'affiche, les limites brillent de la
  couleur de la faction quand on s'en approche. La carte (sous-page Carte) montre les territoires, couleurs et bannières.
- **Trésor** : dons volontaires de tous (depuis le compte en banque), retrait par le chef seul ; boutique payée par le trésor,
  ouverte à tous les membres ; coffre commun de 54 cases ouvert à tous. Dépôts, retraits, achats et dons sont journalisés
  (historique de la faction et `journaux/factions-*.jsonl`).
- **Guerres** : seul le **chef** déclare (annonce mondiale, début 5 minutes après). Les attaques ne sont permises que **de jour**
  (jour invoqué par un Éclat de Lumière) ou la nuit si les **deux chefs** l'acceptent. Pendant une attaque, les ennemis peuvent
  casser, ouvrir et se battre dans les territoires, et **frapper le Cœur** (60 coups, 1 point regagné toutes les 20 s sans coup).
  Fin : **Cœur brisé** ou **capitulation** (proposée par un chef, acceptée par l'autre) ; pas de durée limite. Le vaincu perd 20 %
  de l'XP de son niveau (au plus un niveau), 50 points de classement, et son territoire le plus récent (jamais la base) passe au
  vainqueur (+100 points, +500 XP). **Paix obligatoire de 3 jours** pour les deux. On ne quitte pas sa faction en guerre.
- **Pas de guerre automatique** : frapper un membre d'une autre faction (hors territoire) ou tenter de casser chez elle est un
  **incident**, signalé aux chefs et officiers connectés et journalisé (au plus un message toutes les 5 min par paire de factions).
- **Podium** : niveau, XP, territoires, guerres gagnées, richesse, activité (membres connectés depuis 7 jours) et puissance
  calculée (niveau, territoires, victoires, membres actifs, trésor, points). Pour comparer, sans récompense.
- Les **virements** entre joueurs sont **illimités** (toujours par la banque ; délai de 24 h pour un nouveau compte). Pour
  remettre un plafond : `virementsIllimites: false` dans `economie.json`.

## Zone Nether de raid

Un monde à part (`survierpg:raid`, ambiance des deltas de basalte), **partagé** par tous les joueurs et **régénéré toutes les 3 h**
(annonce 5 min avant). Chaque génération tire un niveau de menace (Normal, Difficile, Cauchemar) et assemble au hasard les salles
du dossier `config/survierpg/raid/salles/` : extérieur (arrivées, butin de palier 1), intermédiaire (arènes, palier 2),
cœur (arène finale, salle d'extraction, palier 3). Un chemin existe toujours des arrivées jusqu'à l'extraction.

- **Entrée** : menu B › Raid › *Entrer* (ou `/raid entrer`) à moins de 6 blocs du portail du hub. Frais selon le niveau
  (× taxes du régulateur). Le groupe (`/raid groupe`, 4 joueurs) arrive au même endroit.
- **Butin du raid** : tout ce qui sort des coffres et des gardiens est marqué « butin du raid ». Il devient à toi en sortant par
  la **balise de retour** (10 s immobile, boutique) ou par l'**extraction finale** : 2 clés de gardien sur le socle (pierre de
  magnétite) ouvrent le trésor, puis le portail s'ouvre en 45 s (vagues de monstres) et reste ouvert 60 s, avec un bonus de 20 %
  de la valeur du butin. Le butin non extrait est perdu à la régénération. Reliques, idoles, couronnes et fragments se revendent
  à la boutique.
- **Mort** : un coffre de corps reste sur place 10 min (le joueur ou son groupe), retour immédiat au hub.
- **Gardiens** (un par arène) : Capitaine Wither Squelette, Chef Piglin Brute, Matriarche Blaze ou Golem de magma, avec barre de vie,
  vie adaptée au nombre de joueurs, onde de choc, serviteurs, embrasement. Chacun laisse une clé. Balise interdite pendant leur combat.
- **Statues** (selon le niveau : 4, 8, 14) : figées tant qu'un joueur les regarde, très rapides sinon. Seul le **Brise-statue**
  (3 fragments de ténèbres + 2 bâtons, ou boutique) les brise : 3 coups pendant qu'on la regarde, 12 statues par outil, réparation
  contre pièces (menu Raid). La **Peur** monte près d'elles : lenteur, nausée, vignette sombre, puis 1 cœur par seconde.
- **Chapardeurs** : volent un objet (jamais l'armure, l'objet en main ni les objets liés) et s'enfuient 60 s. On les tue, ou on
  paie une rançon en Pièces d'or (clic droit). Chaque pièce donnée = +1 d'affinité : 20 = plus de vol, 50 = il te suit et te prévient
  des statues, 100 = **compagnon** qui ramasse les objets et te suit partout (désactivable, revient 24 h après sa mort).
- **Coffres piégés** : 5 % des coffres (et tous les coffres piégés des salles) font apparaître une statue ou un voleur.
- Dans le raid : murs indestructibles, pas de construction, les explosions ne cassent rien.

### Construire une salle

1. Construisez la salle en jeu (monde créatif ou zone à part), sur une empreinte de **16 × 16** (ou **32 × 32** pour une grande salle),
   le sol sur la couche la plus basse. Hauteur libre (la plus haute salle fixe la hauteur de toutes).
2. **Portes** : une ouverture au milieu d'un côté, sur les blocs 7 et 8 du tronçon de 16 (prévoyez 4 de large, blocs 6 à 9),
   libre sur au moins 2 de haut au-dessus du sol. Une grande salle a deux tronçons par côté. Le générateur tourne les salles,
   perce une porte qui manque et bouche celles qui ne mènent nulle part.
3. **Marqueurs** : un **coffre** ou un tonneau = coffre à butin (palier selon l'anneau) ; un **coffre piégé** = piège ;
   un **panneau** dont la 1re ligne est `[arrivee]`, `[statue]`, `[voleur]`, `[boss]`, `[extraction]` (socle), `[portail]`
   (centre du portail) ou `[tresor]` (coffre du trésor). Le panneau disparaît à la génération.
   Une salle avec `[extraction]` = salle d'extraction (au centre), avec `[boss]` = arène, avec `[arrivee]` = salle d'arrivée.
4. Sélectionnez-la avec WorldEdit (//pos1, //pos2 aux coins opposés, sol compris), `//copy` puis `//schem save nom`, et copiez le
   fichier `.schem` dans `config/survierpg/raid/salles/exterieur/`, `intermediaire/` ou `coeur/`. Puis menu B › Raid › *Relire salles*
   (ou `/gm raid salles`) : elle sera utilisée à la prochaine génération (*Régénérer* pour tout de suite).
5. Les 12 salles `exemple_*` (écrites automatiquement) montrent ces conventions : ouvrez-les avec `//schem load` pour vous en inspirer,
   et supprimez-les quand vos salles suffisent (il faut au moins une salle d'extraction et une salle d'arrivée).

## Métiers supplémentaires

Chaque joueur garde son métier principal et choisit en plus un de ces 7 métiers (niveaux 1 à 25, XP propre).
Les métiers s'affichent sous le pseudo des joueurs (« Mineur · Ingénieur »). Toutes les valeurs sont dans `metiers2.json`.

| Métier | Réservé | XP et bonus |
|---|---|---|
| Ingénieur | Lui seul **pose** la redstone : poudre, torches, répéteurs, comparateurs, pistons, observateurs, droppers, dispensers, entonnoirs, rails spéciaux (débloqués par niveau). Portes, boutons et leviers restent à tous | En posant ces blocs |
| Constructeur | Lui seul pose les blocs de prestige : pierres taillées (niv. 1), verre teinté (5), béton (10), terracotta émaillée (15), quartz et briques de deepslate (20) | En posant des blocs (+1 pour un bloc ordinaire) |
| Enchanteur | Lui seul utilise la table d'enchantement (ligne 2 au niv. 8, ligne 3 au niv. 15) et applique des enchantements à l'enclume. Réparer et renommer restent à tous | Enchanter, livres enchantés trouvés dans les coffres, troc avec les villageois |
| Voyageur | Sa balise le fait sortir de la zone jouable (10 s immobile, interrompue par un coup ou un mouvement). Les autres sont ramenés à l'intérieur. Au-delà, il construit normalement | Chaque saut, distance parcourue au-delà de la limite. La recharge baisse avec le niveau (30 min, 5 min minimum) |
| Pacificateur | — | Monstre tué **de jour** : XP + prime en banque (1 pièce, +1 tous les 10 niveaux, ×2 pendant un événement, 40 par jour max). **De nuit** : pas de prime, amende de 2 pièces |
| Alchimiste | Lui seul utilise l'alambic. Durée et puissance au niv. 5, jetables au niv. 8, persistantes au niv. 15 | Chaque potion brassée |
| Pêcheur | — | Poissons, trésors, animaux chassés. Appât +1 aux niv. 10 et 20, Chance de la mer +1 aux niv. 15 et 25 |

L'XP gagnée en posant des blocs (Ingénieur, Constructeur) est plafonnée à 600 par jour, et reposer un bloc au même endroit ne rapporte rien.
Les blocs décoratifs des autres mods (Chipped, Rechiseled, Macaw...) restent libres : ajoutez-les dans `metiers2.json` pour les réserver.

## Biome Terres cendrées

Nouveau biome de l'Overworld (via TerraBlender, déjà installé avec Biomes O' Plenty) : sol de tuf, gravier et andésite,
arbres morts, rochers, cendre grise qui tombe en continu. Il remplace certaines plaines et savanes, **dans les nouveaux chunks
seulement**. Pour le trouver : `/locate biome survierpg:terres_cendrees`.

## Règles de jeu (rappel)

- À la main : terre, feuilles, fleurs, sable, gravier, argile, neige, torches. Le reste : outil officiel de son métier.
- Un bloc posé par un joueur ne donne ni XP ni progression de quête quand on le recasse (mémorisé avec le monde,
  y compris après un redémarrage).
- Mort de **nuit** : on garde tout. Mort de **jour** (jour invoqué par un Éclat) : tout tombe (au tueur, ou à personne),
  on attend la nuit (spectateur ou salle d'attente) sauf avec une Larme de vie. Toute mort coûte 10 % de l'XP du personnage.
- Mairie : pas de destruction, pas de PVP, pistons coupés à 13 blocs. Coffres : à leur propriétaire, entonnoirs bloqués
  (`mixin.block.hopper=false` doit rester dans `config/lithium.properties`).
- Le mod gère l'heure : nuit permanente (`/gm lumiere`) ou cycle (`/gm cycle`), jamais `/time set` ni `/gamerule doDaylightCycle`.

## Développement

`gradlew build` (Java 17+) puis copier `build/libs/survierpg-1.0.0.jar` dans `Server/mods` et chez les joueurs.
`gradlew runShots` ouvre le monde de test `run/saves/rpgtest` et capture chaque onglet du menu dans `run/screenshots/`.
