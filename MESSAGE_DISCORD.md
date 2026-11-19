# 🌑 Mise à jour du serveur : Survie RPG
Le serveur passe en **survie RPG** : métiers, économie, cambriolages, nuits angoissantes et soleil qui brûle.

## 📦 À installer (obligatoire pour se connecter)
Minecraft **Java 1.20.1** avec **Fabric**. Dans le dossier `mods` de votre instance, en plus des mods habituels :
• `nightfall-1.0.0.jar` : brume, ambiance de nuit, Capuche d'ombre
• `survierpg-1.0.0.jar` : métiers, économie, items du serveur
• `fabric-api-0.92.12+1.20.1.jar` (si vous ne l'avez pas déjà)
Demandez les deux premiers fichiers à l'admin. Sans eux, le serveur refuse la connexion.

## 🚀 Premiers pas
1. À la première connexion, vous recevez la **Pioche des ténèbres**.
2. Choisissez votre métier : `/metier choisir mineur|bucheron|fermier|aventurier`. Ce choix est définitif : pour changer, il faut l'accord du GM.
3. Demandez votre **outil officiel** au GM.
4. `/metier` affiche votre niveau et votre progression, `/quetes` vos quêtes.
---8<---
## 🌙 La nuit
• Brume noire : on voit à **6 blocs**, et on subit de la **Lenteur**.
• Une **lanterne en main** (normale ou des âmes) enlève la Lenteur et repousse la brume à **16 blocs**.
• Le ciel disparaît, des **yeux** vous observent dans le noir, des pas vous suivent… Les yeux fuient si vous les fixez.
• Mourir la nuit ne coûte rien : vous gardez tout et revenez à votre lit.

## ☀️ Le jour
• La lumière brûle : ½ cœur toutes les 5 secondes, partout, même à l'abri. Ça s'arrête à 2 cœurs : le soleil ne tue pas.
• La **Capuche d'ombre** protège : 3 cuir sur la ligne du haut, puis cuir / membrane de phantom / cuir sur la ligne du milieu.
• Mourir le jour coûte cher (voir plus bas).

## ⏳ Le cycle
• Jour : environ 12 min. Nuit : environ 8 min. Annonces **LA NUIT TOMBE** et **LE JOUR SE LÈVE**.
• Impossible de dormir : cliquer sur un lit définit seulement votre point de réapparition.
---8<---
## ⛏️ Les métiers (25 niveaux chacun)
**Mineur** (pioche) · **Bûcheron** (hache) · **Fermier** (houe) · **Aventurier** (épée)
• À la main, on ne casse que la terre, les feuilles, les fleurs, le sable, le gravier, l'argile, la neige et les torches.
• Tout le reste demande un **outil officiel de votre métier**, fabriqué par le GM : apportez-lui les matériaux. Les crafts vanilla d'outils et d'armes sont bloqués.
• Chaque niveau débloque des blocs. Exemple Mineur : charbon au niveau 1, fer au 8, diamant au 20, ancient debris au 25. Les blocs d'un autre métier sont refusés.
• On gagne de l'XP en récoltant, et avec les quêtes du GM. L'Aventurier gagne la sienne en tuant des créatures avec son épée officielle.
• Les outils se prêtent et s'échangent, mais seul le bon métier peut s'en servir.
• Certaines **zones** sont réservées à un métier.
• Au niveau 25 : `/prestige`. Retour au niveau 1, avec un bonus d'argent sur les quêtes.
---8<---
## 💰 L'économie
• La **Pierre obscure** (l'ancien sable des âmes) se récolte uniquement avec la **Pioche des ténèbres**. Elle est incassable et ne se perd jamais.
• À l'établi : **4 Pierres obscures = 1 poudre de glowstone**. La poudre de glowstone est **la monnaie** du serveur.
• `/banque` : votre solde
• `/banque deposer <n>` ou `/banque deposer tout` : déposer vos poudres
• `/banque retirer <n>` : les reprendre
• `/banque virer <joueur> <n>` : payer quelqu'un, même déconnecté
• `/hdv` : l'hôtel des ventes. Prenez l'objet en main, puis `/hdv vendre <prix>`. On peut acheter même quand le vendeur est déconnecté : l'argent arrive sur son compte. Recliquez sur votre annonce pour la retirer.
• `/boutique` : la boutique du GM.
• Les achats se paient avec l'argent **en banque**, pensez à déposer.
---8<---
## ⚔️ Combat et créatures
• Le PVP est actif partout, **sauf à la mairie**.
• Zombies, squelettes, araignées et pillards sont **neutres**. Seul celui que vous frappez riposte, jusqu'à sa mort ; les autres ne l'aident pas.
• Animaux : +5 cœurs. Phantoms : deux fois plus lents. Endermen : inchangés.
• Se déconnecter en plein combat est annoncé à tout le serveur.

## 💀 Mourir le jour
• Tout votre inventaire tombe au sol.
• Tué par un joueur : **seul le tueur** peut ramasser le butin. Mort par chute, lave ou monstre : **personne** ne peut le ramasser.
• Vous ne réapparaissez qu'**à la tombée de la nuit**. En attendant : `/attente spectateur` ou `/attente salle` (salle d'attente de la mairie).
• Une **Larme de vie** permet de revenir tout de suite : clic droit ou `/larme`. Usage unique.
• La Pioche des ténèbres et les Larmes de vie ne sont jamais perdues.
---8<---
## 🔒 Coffres et cambriolages
• Un coffre que vous posez vous appartient. Personne d'autre ne peut l'ouvrir ni le casser, même quand vous êtes déconnecté, et les entonnoirs ne peuvent pas le vider.
• Impossible de coller son coffre à celui d'un autre joueur.
• Le **Crochet** permet de tenter un cambriolage : clic droit sur le coffre d'un autre. Il est consommé à chaque essai. Si ça réussit, vous choisissez **jusqu'à 3 types d'objets** et vous prenez tout. Le propriétaire n'est prévenu que si le cambriolage réussit.
• Seuls les coffres sont protégés : pas les fours, les tonneaux ni les portes.

## 🌍 Nether et End
Fermés pour l'instant. Le GM les ouvrira.

## 🏆 Classements et événements
`/classement metiers`, `/classement richesse`, `/classement combats`, `/classement morts`.
Le GM lancera des événements (combat, récolte, ressource…) avec un classement à la fin.
---8<---
# 🛠️ Partie admin / GM
Il faut être op (`/op <pseudo>`). Toutes les commandes GM commencent par `/gm`. La liste complète est dans `rpg-mod/GUIDE_GM.md`.

## ✅ À faire avant l'ouverture
1. Protéger la mairie : `/gm protection creer mairie <x y z> <x y z>`. Donnez les coins du bâtiment, la marge de 5 blocs est ajoutée toute seule.
2. Placez-vous dans la salle d'attente des morts, puis `/gm salle definir`.
3. Régler le cycle : `/gm cycle jour <minutes>` et `/gm cycle nuit <minutes>` (12 et 8 par défaut).
4. Remplir la boutique : objet en main (la quantité compte), puis `/gm boutique ajouter <prix>`.
5. Tracer les zones de métier : `/gm zone creer <nom> <métier> <x y z> <x y z>`.
6. Régler la chance des crochets : `/gm crochet chance <%>` (50 % par défaut), et le bonus de prestige : `/gm prestige bonus <%>` (10 % par défaut).

## 🎁 Donner les objets
• Outil officiel : `/gm outil donner <joueur> survierpg:outil_mineur_fer`. Métiers : mineur, bucheron, fermier, aventurier. Matériaux : bois, pierre, fer, diamant, netherite. Un outil vanilla est accepté aussi et converti.
• `/gm larme <joueur> [n]`, `/gm crochet donner <joueur> [n]`, `/gm pioche <joueur>`.
• En créatif, l'onglet **Survie RPG** contient tous les items. Un outil sorti du créatif reçoit son identifiant officiel tout seul.
---8<---
## 📋 Gestion courante
• Métiers : `/gm metier definir <joueur> <métier>`, `/gm niveau <joueur> <n>`, `/gm xp <joueur> <n>`. Fiche complète d'un joueur : `/gm diag <joueur>`.
• Quêtes : `/gm quete donner <joueur> <texte>`, puis `/gm quete valider <joueur> <id> [xp] [argent]`. L'argent reçoit le bonus de prestige.
• Argent : `/gm argent voir|donner|retirer|definir <joueur> <n>`. Marche aussi hors ligne.
• Coffres : `/gm coffre info <x y z>`, `/gm coffre attribuer <x y z> <joueur>`, `/gm coffre liberer <x y z>`.
• Morts : `/gm reanimer <joueur>`. Changer l'heure : `/gm cycle passer jour|nuit`.
• Dimensions : `/gm dimension ouvrir nether` (ou `end`, ou `fermer`).
• Crafts : `/gm craft liste`, `/gm craft bloquer <objet ou #tag>`, `/gm craft debloquer <…>`.
• Événements : `/gm event lancer combat|recolte|ressource|special <minutes> <titre>`, `/gm event points <joueur> <n>`, `/gm event terminer`.
• Construire en survie sans les règles de récolte : `/gm bypass` (ou passer en créatif).
---8<---
## 📁 Où modifier quoi
• `config/survierpg/metiers.json` : blocs cassables à la main, paliers de chaque métier (niveau, XP, blocs ou #tags), courbe d'XP. Aussi modifiable en jeu avec `/gm palier liste|ajouter|retirer`. Après une modification à la main : `/gm recharger`.
• `config/survierpg/crafts.json` : la liste des crafts bloqués.
• `world/data/survierpg.dat` : toutes les données (métiers, argent, coffres, zones, HDV…). **À sauvegarder** avec le monde.
• Brume, lenteur, dégâts du soleil, fréquence des yeux… : `nightfall-mod/src/main/java/fr/serveur/nightfall/NightfallConfig.java`, puis recompiler.
• `config/lithium.properties` : la ligne `mixin.block.hopper=false` doit rester, sinon les entonnoirs peuvent vider les coffres protégés.
• Le mod coupe le cycle jour/nuit vanilla : utilisez `/gm cycle`, pas `/gamerule doDaylightCycle`.

## 🔄 Mettre à jour un mod
Dans `nightfall-mod/` ou `rpg-mod/` : `gradlew build` (avec Java 17 ou plus), puis copiez `build/libs/*.jar` dans `Server/mods` **et** chez chaque joueur.

## ⚠️ Limites connues
• Le sable des âmes n'existe naturellement que dans le Nether, qui est fermé : il faut prévoir une source (boutique, événements, zones).
• La conversion de la carte en Dark Forest n'est pas encore faite.
• Les pistons ne marchent pas à moins de 13 blocs de la mairie. Un événement en cours est perdu si le serveur redémarre.
