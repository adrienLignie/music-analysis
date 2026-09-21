# music-analysis

Recherche d'**albums en double** et classement des **répertoires** par poids, dans une ou plusieurs
arborescences musicales. Lecture seule : le programme n'écrit, ne renomme et ne supprime rien.

```
$ music-analysis /mnt/databis/musique /mnt/sauvegarde/musique --top 5

ALBUMS EN DOUBLE : 1 groupes, 90,00 Mo récupérables

── Confiance FORTE (1 groupes) ──

  Pink Floyd — « the dark side of the moon », 1973 / sans année, 10 pistes,
  formats différents : flac / mp3  |  récupérable : 90,00 Mo
    garder ?      300,00 Mo  10 pistes  flac   .../Pink Floyd/The Dark Side Of The Moon (1973)
                   90,00 Mo  10 pistes  mp3    .../Pink Floyd - The Dark Side Of The Moon [MP3 320]

LES 5 ALBUMS LES PLUS LOURDS
     316,00 Mo  10 pistes  flac   .../Muse/Black Holes And Revelations
     300,00 Mo  10 pistes  flac   .../Pink Floyd/The Dark Side Of The Moon (1973)
      96,00 Mo   3 pistes  flac   .../Daft Punk/Discovery (2001)
```

Le classement porte sur des **dossiers**, et c'est tout l'objet du programme : personne ne supprime
six pistes sur douze. Le coffret de Muse, arrivé en tête, est un seul album de deux disques —
comptés séparément, ses deux dossiers seraient passés inaperçus.

---

## Sommaire

- [Ce que fait le programme](#ce-que-fait-le-programme)
- [Ce qu'il ne fait pas](#ce-quil-ne-fait-pas)
- [Construction et utilisation](#construction-et-utilisation)
- [Options](#options)
- [Lire le rapport](#lire-le-rapport)
- [Sortie pour un script](#sortie-pour-un-script)
- [Comment deux dossiers sont déclarés doublons](#comment-deux-dossiers-sont-déclarés-doublons)
- [Niveaux de confiance](#niveaux-de-confiance)
- [Ce qui est écarté du scan](#ce-qui-est-écarté-du-scan)
- [Limites connues](#limites-connues)
- [Architecture](#architecture)

---

## Ce que fait le programme

1. **Parcourt** un ou plusieurs dossiers et leurs sous-répertoires, en un seul lot : un album
   présent à la fois sur le disque et sur une sauvegarde est rapproché de lui-même. Un fichier
   atteint par plusieurs chemins n'est compté qu'une fois.
2. **Compose les albums** : un album est un dossier, et les disques d'un coffret — `CD1`, `CD2` —
   sont réunis sous le dossier du dessus avant toute chose.
3. **Lit l'identité** de chaque album dans son chemin : artiste, titre, année, édition, version,
   format et débit annoncés ; et celle de chaque piste dans son nom : numéro, disque, titre.
4. **Regroupe** les dossiers qui semblent porter le même album, avec un niveau de confiance et le
   motif du rapprochement.
5. **Confronte** ces rapprochements aux étiquettes des fichiers, sur demande (`--etiquettes`) :
   c'est la seule information du programme qui ne vienne pas d'un nom de dossier.
6. **Classe les albums par poids décroissant** — des répertoires, jamais des fichiers.
7. **Signale** ce qui cloche : albums à trous, pistes trop légères pour leur format, dossiers mal
   rangés.

## Ce qu'il ne fait pas

- **Aucune écriture dans l'arborescence analysée.** Pas de suppression, pas de renommage, pas
  d'étiquette corrigée, pas même derrière une confirmation : l'option n'existe pas. Le rapport sort
  sur la console, les messages techniques et l'avancement sur l'erreur standard.
- **Aucun appel réseau.** Aucune base musicale n'est interrogée, aucune empreinte n'est envoyée
  nulle part.
- **Aucune ouverture de fichier**, sauf si `--etiquettes` est demandé. Cette option-là ouvre les
  fichiers **en lecture seule** et n'en lit que l'en-tête — quelques dizaines d'octets, aucun son
  décodé.
- **Les liens symboliques ne sont pas suivis**, pour ne compter aucun album deux fois. Deux chemins
  donnés qui se recouvrent — la même racine écrite deux fois, ou un dossier et son parent — sont
  ramenés à un seul, pour la même raison.
- Le seul effet de bord possible est la **date de dernier accès** si le système la maintient : Java
  ne permet pas de l'éviter.

Une interruption en cours de route ne laisse donc aucun état à réparer.

## Construction et utilisation

```bash
mvn verify
java -jar target/music-analysis.jar /mnt/databis/musique
```

Java 17 ou plus. `mvn verify` produit le rapport de couverture dans
`target/site/jacoco/index.html` et refuse de passer en dessous des seuils fixés dans le `pom.xml`.
Il passe aussi l'**analyse statique** du bytecode, qui lit ce que ni la couverture ni les tests ne
disent — un flux laissé ouvert sur un chemin d'erreur, une comparaison entre types incompatibles.
Elle tient en quelques secondes, d'où sa place dans le cycle normal. Les cas revus et écartés sont
dans `spotbugs-exclusions.xml`, chacun avec sa raison.

Les **tests de mutation** ne font pas partie du cycle normal, parce qu'ils durent plusieurs
minutes. Ils répondent à la question que la couverture ne pose pas — ces seuils sont-ils vraiment
surveillés par une assertion ? :

```bash
mvn -P mutation test   # rapport dans target/pit-reports/index.html
```

### Binaire natif

Avec un JDK GraalVM, `mvn -P natif package` produit un exécutable qui s'ouvre en quelques
millisecondes et ne demande aucune machine virtuelle installée. Ce profil ne fait pas partie du
cycle normal : il demande ce JDK particulier et dure plusieurs minutes.

Le code ne se sert de rien qui dépasse **Java 17**, et la version visée tient dans la propriété
`java.release` du `pom.xml` : une seule valeur à changer le jour où la machine de construction
portera un JDK plus récent.

## Options

| Option | Défaut | Effet |
|---|---|---|
| `CHEMIN...` | — | un ou plusieurs dossiers, analysés **ensemble** |
| `-x`, `--exclure <DOSSIER>` | — | nom de dossier à ignorer, répétable |
| `-t`, `--top <N>` | `50` | taille du classement des albums les plus lourds |
| `--taille-min <KO>` | `300` | taille minimale d'un fichier audio, en kilo-octets |
| `-f`, `--format <FORME>` | `texte` | `texte`, `json` ou `csv` |
| `-c`, `--confiance <NIVEAU>` | tous | ne garder que les groupes au moins aussi sûrs : `certaine`, `forte`, `moyenne`, `a_verifier` |
| `-e`, `--etiquettes` | — | ouvre l'en-tête des fichiers concernés pour y lire durée et étiquettes |
| `-p`, `--pistes` | — | cherche aussi les morceaux présents dans plusieurs dossiers |
| `-q`, `--silencieux` | — | n'affiche aucun avancement pendant l'analyse |
| `--ascii` | — | rapport sans accents ni caractères de tracé |
| `-h`, `--help` | — | affiche l'aide et sort |
| `-V`, `--version` | — | affiche la version et sort |

### Exemples

Un seul dossier, réglages par défaut :

```bash
java -jar music-analysis.jar /mnt/databis/musique
```

Croiser un disque et sa sauvegarde. Les deux racines forment **un seul lot** : un album présent des
deux côtés est rapproché de lui-même, ce qui n'arriverait pas en lançant deux analyses séparées.

```bash
java -jar music-analysis.jar /mnt/databis/musique /mnt/sauvegarde/musique
```

Écarter les livres audio et les sonneries, et allonger le classement :

```bash
java -jar music-analysis.jar /mnt/databis/musique -x Livres -x Sonneries --top 100
```

Confronter les rapprochements à ce que les fichiers disent d'eux-mêmes, avant de supprimer quoi que
ce soit :

```bash
java -jar music-analysis.jar /mnt/databis/musique --etiquettes
```

Conserver le rapport dans un fichier. Les messages techniques et l'avancement partent sur l'erreur
standard : rediriger la sortie standard n'emporte donc que le rapport.

```bash
java -jar music-analysis.jar /mnt/databis/musique > rapport.txt
```

### Ce que `--etiquettes` change, et ce qu'elle coûte

C'est la seule option qui **ouvre des fichiers**. Elle le fait en lecture seule, n'en lit que
l'en-tête, et seulement là où la réponse change quelque chose : les pistes des dossiers soupçonnés
d'être en double, et les fichiers que leur poids rend suspects. Sur une discothèque de cent mille
pistes, cela représente quelques centaines d'ouvertures, pas cent mille.

Ce qu'elle apporte est ce que les noms de dossiers ne peuvent pas donner :

- **Un flac cesse d'être cru sur parole.** Un dossier nommé `FLAC` dont les pistes tiennent à cent
  kilobits par seconde n'est pas du sans perte, c'est un mp3 réencodé qui en a pris l'habit. Ni
  l'extension ni le nom ne trahissent ce mensonge : seule la division du poids par la durée le
  révèle.
- **Un rapprochement incertain devient une certitude.** Deux dossiers rangés autrement mais dont
  les fichiers portent le même artiste et le même album sont le même album. Les étiquettes voyagent
  avec le fichier ; un rangement, non.
- **Un album amputé se signale par sa durée.** Deux exemplaires qui comptent le même nombre de
  fichiers mais ne durent pas le même temps ne portent pas le même contenu.
- **Un doublon peut devenir une certitude.** Un flac écrit dans son en-tête l'empreinte MD5 de son
  signal **non compressé**. Deux dossiers dont toutes les pistes la partagent ne se ressemblent
  pas : ils portent le même son, quels que soient leurs étiquettes, leur niveau de compression ou
  leur rangement. C'est le seul verdict de ce programme qui ne suppose rien, et il est gratuit —
  l'empreinte est dans les trente-quatre premiers octets, à côté de la durée qu'on y lisait déjà.

Les seuils : au-delà de **dix pour cent** d'écart de durée totale, deux dossiers ne portent pas le
même contenu ; un flac sous **300 kbit/s** ou un fichier à perte sous **48 kbit/s** ne contient pas
ce que son format promet.

#### L'empreinte du son

Deux formats disent quelque chose de leur son sans qu'on ait à le décoder :

| Format | Ce qu'il porte | Ce que cela vaut |
|---|---|---|
| `flac` | le MD5 du signal non compressé, dans son premier bloc | une **démonstration** : même son, bit pour bit |
| `mp3` encodé par LAME | la longueur exacte du flux et un contrôle calculé dessus | une **corroboration** : reconnaît un fichier réétiqueté |

Une empreinte partagée fait monter le groupe en confiance `CERTAINE` — et elle y reste même si les
étiquettes des deux dossiers se contredisent : c'est la signature du doublon le plus courant, un
fichier recopié puis réétiqueté. Les étiquettes disent ce qu'un fichier prétend être, l'empreinte
ce qu'il est.

Une empreinte **différente** ne conclut rien et ne fait jamais descendre la confiance. Un flac et
un mp3 n'en partagent aucune par construction, et deux extractions du même disque à des décalages
différents non plus. Quand les deux sont malgré tout de la même sorte, le motif le mentionne —
deux flac au son différent sont deux extractions distinctes, ce qui explique souvent un écart de
poids qui surprendrait autrement.

Formats lus : `flac`, `mp3`, `mp2`, `m4a`, `mp4`, `m4b`, `aac`, `alac`, `ogg`, `oga`, `opus`,
`wav`, `wave`. Un fichier d'un autre format, illisible ou mal formé ne fait rien échouer : il reste
simplement sans étiquette.

### Ce que `--pistes` ajoute

Une section de plus, facultative parce qu'elle se lit autrement que les autres : le même morceau du
même artiste figure légitimement sur son album, sur une compilation et sur un best of. Elle attrape
ce qu'aucune autre section ne voit — le dossier `Téléchargements` qui répète morceau par morceau une
discothèque déjà rangée, sans qu'aucun de ses dossiers ne ressemble à un album.

Trois précautions la rendent lisible : un artiste et un titre connus des deux côtés sont exigés, les
fichiers d'un même album ne sont jamais rapprochés entre eux, et les dossiers déjà signalés comme
albums en double sont laissés de côté.

### Ce que `--exclure` attend

Un **nom de dossier**, pas un chemin ni un motif. `-x Karaoke` écarte tout dossier nommé `Karaoke`,
où qu'il se trouve dans l'arborescence, avec son contenu. La comparaison ignore la casse.

### Codes de sortie

| Code | Signification |
|---|---|
| `0` | le rapport a été produit |
| `1` | erreur interne |
| `2` | ligne de commande mal formée |
| `3` | aucun fichier audio trouvé dans les chemins donnés — le plus souvent une erreur de chemin |

Les codes 1 et 2 viennent de la bibliothèque de ligne de commande. Les codes propres au programme
commencent à 3, pour qu'une faute de frappe dans les options et une bibliothèque vide restent
distinguables.

### Accents dans le terminal

Rien à faire : le programme regarde ce que sa sortie sait écrire. Une console qui ne connaît ni le
filet `─` ni les guillemets `« »` reçoit un rapport entièrement translittéré plutôt qu'un texte
criblé de points d'interrogation.

`--ascii` force cette forme. Et une console Windows en UTF-8 continue d'afficher le rapport
complet :

```
chcp 65001
java -jar music-analysis.jar D:\Musique
```

Les sorties `json` et `csv`, elles, sont toujours écrites en UTF-8 : elles sont faites pour être
relues par un programme, pas par une console.

## Lire le rapport

Le rapport comporte un en-tête et six sections, dans l'ordre où elles servent. Les sections qui
n'ont rien à dire n'apparaissent pas.

### L'en-tête

```
7 albums retenus, 45 pistes, 895,40 Mo au total
412,00 Mo de plus ne sont pas de la musique (pochettes, livrets, journaux)
3 fichiers écartés (trop petits ou illisibles), 0 dossiers partiellement lus
1 chemins de plus mènent à un fichier déjà compté (liens durs)
40 fichiers ont été ouverts pour y lire leurs étiquettes
```

Seule la première ligne est toujours là. Le compte et le total ne retiennent **chaque fichier
qu'une fois**, quel que soit le nombre de chemins qui y mènent.

La deuxième ligne compte ce qui occupe la place sans être un morceau : pochettes, livrets
numérisés, journaux d'extraction, listes de lecture, fichiers écartés pour leur petite taille. Rien
de tout cela n'entre dans le total ni dans le classement — le classement compare des albums, et
deux albums ne se comparent pas au poids de leurs pochettes — mais le disque, lui, le compte. Un
livret numérisé en trois cents points par pouce pèse couramment plus lourd que le disque qu'il
illustre, et sans cette ligne il ne figurerait nulle part.

### 1. Albums en double

```
ALBUMS EN DOUBLE : 1 groupes, 90,00 Mo récupérables

── Confiance FORTE (1 groupes) ──

  Pink Floyd — « the dark side of the moon », 1973 / sans année, 10 pistes,
  formats différents : flac / mp3  |  récupérable : 90,00 Mo
    garder ?      300,00 Mo  10 pistes  flac   .../Pink Floyd/The Dark Side Of The Moon (1973)
                   90,00 Mo  10 pistes  mp3    .../Pink Floyd - The Dark Side Of The Moon [MP3 320]
```

| Élément | Ce qu'il dit |
|---|---|
| `Artiste — « titre », années, pistes` | le **motif** du rapprochement : l'artiste, la ou les clés retenues, les années, le nombre de pistes de chaque exemplaire. Il peut mentionner `sans artiste`, `compilation`, `(réédition ?)`, `formats différents`, `éditions différentes`, `artiste lu sur le dossier parent`, et — avec `--etiquettes` — `étiquettes concordantes`, `étiquettes différentes` ou `durées différentes` |
| `récupérable` | ce que libérerait la suppression de tous les exemplaires sauf celui marqué `garder ?` |
| `garder ?` | marque l'exemplaire à garder si l'on ne devait en garder qu'un. C'est une **suggestion**, pas un verdict |
| `mêmes fichiers` | à la place d'un poids : ce dossier mène aux fichiers d'une ligne précédente, par des liens durs. Le supprimer ne libérerait rien |
| `groupes écartés` | groupes dont **tous** les dossiers menaient aux mêmes fichiers : ce n'étaient pas des doublons |

#### Comment `garder ?` est choisi

Ce n'est pas le plus gros dossier. Un album au format sans compression pèse trois fois un flac qui
porte le même son, et un téléchargement interrompu de trois pistes sur douze l'emporterait sur
l'album entier qu'il prétend doubler. Les exemplaires sont donc classés ainsi, du plus décisif au
moins décisif :

1. la **complétude**. Rien ne rachète des pistes absentes, pas même le meilleur format du monde ;
2. le **format**, qui est ici une donnée et non une déclaration : un fichier `.flac` est du flac,
   là où un film qui s'annonce en 1080p peut ne pas l'être ;
3. le **débit** — réel quand on est allé le mesurer, annoncé par le nom sinon, et à défaut le poids
   moyen d'une piste, qui en tient lieu entre deux exemplaires du même album ;
4. l'**édition** la plus complète, parce qu'une édition de luxe contient l'édition simple ;
5. le **poids**, à égalité de tout le reste ;
6. le **chemin**, pour que deux exécutions rendent le même rapport.

Les groupes sont triés par place récupérable décroissante, à l'intérieur de chaque niveau de
confiance. L'ordre ne dépend pas de l'ordre de parcours : deux exécutions sur la même discothèque
rendent le même rapport, qui peut donc être comparé d'une fois sur l'autre.

### 2. Morceaux en double (`--pistes`)

Les mêmes morceaux trouvés dans plusieurs dossiers, hors des albums déjà signalés. Cette liste se
lit, elle ne s'applique pas : trois exemplaires d'un même titre sont souvent trois albums
légitimes.

### 3. Les N albums les plus lourds

Classement brut des **répertoires** par poids, tous albums confondus. C'est là que se trouve
l'essentiel de la place à récupérer, souvent plus que dans les doublons eux-mêmes.

Le poids est celui de la **musique**, pour que deux albums restent comparables. Un album dont ce
qui n'est pas de la musique dépasse le dixième de son poids porte la mention en clair :

```
     300,00 Mo  10 pistes  flac   .../Pink Floyd/The Dark Side Of The Moon (1973)  (+ 420,00 Mo hors musique)
```

### 4. Albums incomplets

```
ALBUMS INCOMPLETS (1)
  Trous dans la numérotation : probables téléchargements interrompus.
  5 pistes sur 11, manquent 4, 5, 6, 7, 8, 10  .../Queen/A Night At The Opera (1975)
```

C'est le pendant, pour une discothèque, des fichiers vidéo trop légers : la trace d'un
téléchargement interrompu. Elle se lit ici bien plus sûrement, parce que les pistes sont
**numérotées**. Chaque disque d'un coffret est examiné séparément, et un dossier dont trop peu de
fichiers portent un numéro n'est pas jugé — il est mal nommé, ce qui est une autre section.

### 5. Pistes suspectes

Sans `--etiquettes`, une piste est suspecte quand son poids n'a aucune commune mesure avec celui de
ses voisines de dossier — huit fois plus légère que la médiane. Avec, c'est le **débit** qui juge,
et il juge bien mieux : un flac à cent kilobits par seconde n'est pas du flac.

Ce n'est pas une certitude : c'est une liste de fichiers à écouter pour vérifier qu'ils se lisent
jusqu'au bout.

### 6. Dossiers mal rangés

```
DOSSIERS MAL RANGÉS (1)
  Là où le programme voit mal : ces dossiers ne seront rapprochés de rien.
       8,00 Mo   1 pistes  .../musique
               pistes posées à la racine, hors de tout dossier d'album
```

Cette section ne parle pas de place mais de **visibilité** : les autres listes ne peuvent rien dire
des dossiers qu'elles n'arrivent pas à identifier. Quatre cas sont relevés — des pistes posées à
même la racine, un dossier qui mêle ses pistes à des dossiers d'albums, un dossier au nom
fourre-tout, un album dont personne ne nomme l'artiste.

## Sortie pour un script

Le programme ne supprimera jamais rien. Mais ce refus ne doit obliger personne à relire cinq cents
lignes de console : `--format json` et `--format csv` rendent le même résultat sous une forme
qu'un script reprend, pour que la décision **et le geste** restent entre les mains de qui les pose.

```bash
# Les dossiers à supprimer des groupes sûrs, du plus gros gain au plus petit
java -jar music-analysis.jar /mnt/databis/musique --format json --confiance forte \
  | jq -r '.doublons.liste[].exemplaires[] | select(.garder == false and .memesFichiersQuUnAutre == false) | .dossier'
```

### Ce que porte le JSON

```json
{
  "version": 2,
  "inventaire": {"albums": 7, "pistes": 45, "octets": 939001856, "octetsHorsAudio": 412000000,
                 "fichiersIgnores": 0, "dossiersIllisibles": 0, "liensDurs": 0,
                 "fichiersSondes": 0},
  "doublons": {
    "groupes": 1, "octetsRecuperables": 90000000, "groupesEcartesMemesFichiers": 0,
    "liste": [{
      "confiance": "CERTAINE", "motif": "Pink Floyd — « the dark side of the moon », …",
      "octetsRecuperables": 90000000,
      "exemplaires": [
        {"dossier": "…/The Dark Side Of The Moon (1973)", "octets": 300000000,
         "octetsHorsAudio": 0, "pistes": 10,
         "garder": true, "memesFichiersQuUnAutre": false, "memeSonQueLExemplaireAGarder": true,
         "artiste": "Pink Floyd",
         "album": "the dark side of the moon", "annee": 1973, "format": "flac",
         "octetsParSeconde": null}
      ]
    }]
  },
  "morceauxEnDouble": [],
  "plusGrosAlbums": [{"dossier": "…", "octets": 316000000, "pistes": 10,
                      "octetsHorsAudio": 0, "format": "flac", …}],
  "albumsIncomplets": [{"dossier": "…", "pistes": 5, "pistesAttendues": 11,
                        "numerosManquants": [4, 5, 6, 7, 8, 10]}],
  "pistesSuspectes": [{"chemin": "…", "octets": 400000, "nature": "POIDS",
                       "constate": 400000, "attendu": 9000000, "format": "mp3"}],
  "dossiersMalRanges": [{"dossier": "…", "octets": 8000000, "pistes": 1,
                         "nature": "PISTES_EN_VRAC", "constat": "pistes posées à la racine, …"}]
}
```

Les poids sont en **octets** et les débits en **octets par seconde**, jamais mis en forme :
l'arrondi appartient à la sortie lisible. `octetsParSeconde` vaut `null` tant que `--etiquettes`
n'est pas demandé — un débit absent ne veut pas dire « fichier sans débit » mais « non mesuré ».

`memeSonQueLExemplaireAGarder` est le seul champ qui affirme quelque chose du **contenu**. À
`true`, les fichiers eux-mêmes en portent la preuve et un script peut supprimer sans rien
vérifier ; à `false`, les deux portent une empreinte et elles diffèrent ; à `null`, on ne le sait
pas. La nuance porte tout : prendre `null` pour `false` ne prive que d'un doublon, l'inverse
détruirait.

`version` monte dès qu'un champ change de sens, pour qu'un script sache à quoi il a affaire. Elle
est passée à **2** : le niveau de confiance `CERTAINE` est apparu, ainsi que les champs
`octetsHorsAudio` et `memeSonQueLExemplaireAGarder`.

### Ce que porte le CSV

Une ligne par dossier ou par fichier, toutes les sections dans la même table, séparées par la
colonne `section` (`doublon`, `morceau`, `top`, `incomplet`, `suspecte`, `rangement`) :

```
section,groupe,confiance,garder,memes_fichiers,octets,octets_hors_audio,pistes,octets_recuperables,format,artiste,album,annee,constate,attendu,motif,chemin
doublon,1,FORTE,true,false,300000000,0,10,90000000,flac,Pink Floyd,the dark side of the moon,1973,,,"…",/musique/…
```

La colonne `chemin` porte un **dossier** pour les albums et un **fichier** pour les morceaux et les
pistes suspectes ; la colonne `pistes`, vide pour un fichier, permet de les distinguer.

Virgule et guillemets doublés, selon la RFC 4180 — un tableur français demandera d'indiquer la
virgule à l'importation.

## Comment deux dossiers sont déclarés doublons

Le rapprochement se fait par **égalité** de clé, jamais par ressemblance. Quatre clés sont indexées
pour chaque album :

1. l'**artiste et le titre réunis** — `daft punk | discovery` ;
2. le **titre seul**, qui relie deux rangements dont l'un ne nomme pas son artiste ;
3. le même **sans son article initial**, ce qui relie `The Wall` à `Wall` ;
4. chaque **titre alternatif** trouvé entre parenthèses.

Le titre seul n'est indexé que s'il n'est pas **générique**. Sans cette réserve, les quarante
`Greatest Hits` d'une discothèque formeraient un seul groupe de doublons, et les `Best Of` avec eux :
c'est le faux positif le plus massif qu'une bibliothèque musicale puisse produire, là où une
vidéothèque n'en produit aucun d'équivalent.

Le rapprochement est **transitif** : trois rangements du même album forment un seul groupe, et non
trois paires qui se recouvrent. Mais trois des quatre règles de séparation ci-dessous sont des
égalités, donc transitives d'elles-mêmes, tandis que la quatrième — celle des artistes — ne l'est
pas : un dossier qui ne nomme pas son artiste est compatible avec *tous* les artistes. Les égalités
sont donc réunies d'abord, et les dossiers anonymes rejoignent ensuite l'artiste qui les réclame,
**à condition qu'un seul le fasse**. Deux artistes qui se disputent le même dossier anonyme le
laissent de côté : `Muse/Absolution`, `Absolution` et `Bob Dylan/Absolution` ne forment aucun
groupe, là où les réunir aurait fait proposer de supprimer l'album de l'un au profit de l'autre.

Une clé commune ne suffit pas. Sont ensuite **séparés** :

- les dossiers dont les **mentions de version** diffèrent — `Unplugged`, `Live`, `Remixes`,
  `Demos`, `Acoustic` désignent une autre œuvre, pas une autre édition. Confondre les deux
  proposerait de supprimer un disque qui n'existe nulle part ailleurs : c'est l'erreur la plus
  coûteuse que ce programme puisse commettre, et la seule qu'il ne commet jamais ;
- les dossiers dont le **numéro de volume** diffère — `Hits Vol. 1` n'est pas `Hits Vol. 2` ;
- les dossiers dont les **artistes sont connus et différents** ;
- une **compilation** et l'album homonyme d'un artiste nommé.

**Ce qui ne sépare pas, à la différence d'une vidéothèque : l'année.** Deux dossiers du même artiste
portant le même titre à vingt ans d'écart sont une réédition, non deux œuvres — c'est même le cas
le plus fréquent de doublon musical, celui de l'album original et de son remastering. L'écart
d'années fait descendre la confiance et s'inscrit dans le motif ; il ne défait pas le rapprochement.

**Le poids n'entre à aucun moment dans le rapprochement, et le nombre de pistes non plus.** Un album
téléchargé à moitié reste le même album, et c'est précisément ce qu'il faut signaler.

### Le format est une donnée, le débit une déclaration

C'est la différence de fond avec une vidéothèque, et elle est à l'avantage de la musique. La
résolution d'un film est **annoncée** dans son nom, que rien n'oblige à dire vrai. Le format d'une
piste, lui, est porté par son **extension** : un fichier `.flac` est un flac, il n'y a pas à le
croire sur parole.

Ce que l'extension ne dit pas, c'est le **débit** : un `.mp3` peut porter du 320 kbit/s comme du 96,
et deux dossiers du même album ne se départagent souvent que là-dessus. Le débit est donc cherché
dans le nom du dossier — `[320]`, `V0` — et, avec `--etiquettes`, mesuré pour de bon.

### L'artiste vient du chemin, pas du seul dossier de l'album

Quatre conventions coexistent, et la première interdit de se fier au nom du dossier :

```
Pink Floyd/The Dark Side Of The Moon (1973)/01 - Speak To Me.flac   l'artiste est le parent
Daft Punk - Discovery (2001)/01 - One More Time.mp3                 tout est dans le dossier
Musique/Best Of/01 - Pink Floyd - Money.mp3                         seules les pistes parlent
Muse/Black Holes And Revelations/CD1/01 - Take A Bow.flac           le dossier n'est qu'un disque
```

L'artiste est donc cherché dans le dossier de l'album, puis dans son parent, puis dans les pistes
elles-mêmes — et l'on retient d'où il vient, parce qu'un rapprochement ne vaut pas la même chose
selon la réponse. Le dossier au-dessus d'une racine analysée n'est jamais pris pour un artiste : il
a été choisi comme point de départ, rien ne dit qu'il porte un nom.

## Niveaux de confiance

| Niveau | Ce qui l'a produit |
|---|---|
| **CERTAINE** | les fichiers eux-mêmes portent la preuve qu'ils tiennent le même son. Rien à vérifier. Ne s'atteint qu'avec `--etiquettes` |
| **FORTE** | même artiste connu partout, même titre exact, années qui ne se contredisent pas, et autant de pistes d'un dossier à l'autre |
| **MOYENNE** | un artiste absent d'un côté, des années éloignées — une réédition —, ou un dossier qui compte bien moins de pistes que l'autre |
| **A_VERIFIER** | plus de quatre exemplaires : presque toujours une série de dossiers au nommage régulier |

Avec `--etiquettes`, le niveau peut **monter** — des étiquettes qui concordent valent mieux qu'un
rangement, une empreinte de son partagée vaut mieux que tout le reste — ou **descendre** d'un cran
quand les étiquettes se contredisent ou que les durées totales s'écartent de plus de dix pour cent.

L'ordre de ces quatre niveaux est celui que compare `--confiance` : `--confiance forte` retient
donc aussi les groupes certains.

Aucun niveau ne veut dire « supprime celui-ci ». Chaque groupe porte son motif en clair, pour que le
verdict soit vérifiable sans relire le code.

## Ce qui est écarté du scan

- Les fichiers de moins de 300 ko par défaut. Bien plus bas que le seuil d'une vidéothèque, et pour
  cause : un morceau de deux minutes en mp3 pèse deux mégaoctets. Le seuil n'écarte que ce qui ne
  peut pas être de la musique.
- Les dossiers `$RECYCLE.BIN`, `System Volume Information`, `@eaDir`, `#recycle` et `.Trash`, sans
  être même pesés : une corbeille n'appartient à aucun album, et c'est un problème d'une autre
  nature.
- Les dossiers `Scans`, `Scan`, `Artwork`, `Covers`, `Cover`, `Booklet`, `Booklets` et
  `Playlists` : ils ne sont jamais pris pour des albums, mais **leur poids est porté au compte de
  l'album qui les abrite**. C'est le gisement de place que rien d'autre ne montrerait.
- Les téléchargements inachevés : `.part`, `.crdownload`, `.!ut`, `.tmp`.

La liste des noms de fichiers écartés est volontairement courte : un titre de chanson peut contenir
presque n'importe quel mot — `Sample` et `Intro` sont de vrais titres —, et écarter sur le nom coûte
donc plus cher ici que dans une vidéothèque.

## Limites connues

- **Le `.m4a` est rangé avec les formats à perte.** L'extension ne dit pas si le conteneur porte de
  l'AAC ou de l'ALAC sans perte. Se tromper dans ce sens ne coûte qu'un rang de qualité, là où
  l'inverse ferait garder un fichier moins bon que son voisin.
- **Un titre d'album qui est une année.** `Van Halen - 1984` est lu juste quand une parenthèse
  porte déjà l'année ; seul, le nom reste ambigu — le même mot y désigne l'œuvre et sa date.
- **Les formats `ape`, `wv`, `wma`, `mpc` et DSD ne sont pas ouverts.** Ils sont scannés, classés et
  rapprochés comme les autres, mais `--etiquettes` n'a rien à en lire : leur débit reste inconnu.
- **Les liens durs ne sont reconnus hors des groupes que là où le système les désigne.** Windows ne
  rend aucune clé de fichier à Java : le total et le classement y comptent donc encore les liens
  durs deux fois, et seule la vérification faite à l'intérieur des groupes y répond.
- **Une compilation n'est rapprochée que d'une autre compilation.** Deux dossiers `Various Artists`
  portant le même titre se rejoignent, mais rien ne relie une compilation à l'album dont elle tire
  ses morceaux.
- **Aucune écoute des fichiers.** `--etiquettes` confronte les rapprochements à ce que les en-têtes
  déclarent, ce qui suffit à démasquer un débit menteur et, sur un flac ou un mp3 de LAME, à
  démontrer que deux dossiers portent le même son. Mais deux fichiers de sons **différents** ne
  sont pas départagés pour autant : deux extractions du même disque à des décalages différents ont
  deux empreintes, et rien ne dit qu'elles portent le même morceau. Reconnaître le morceau
  lui-même demanderait une empreinte acoustique, donc de décoder le son.
- **Les formats autres que flac et mp3 ne portent pas d'empreinte lisible.** Un m4a, un ogg, un wav
  livrent leur durée et leurs étiquettes, jamais de quoi démontrer leur contenu : un groupe qui n'en
  contient que de ceux-là n'atteindra pas la confiance `CERTAINE`.
- **L'empreinte confirme un rapprochement, elle n'en crée aucun.** Elle n'est lue que sur les
  dossiers déjà soupçonnés d'être en double, faute de quoi il faudrait ouvrir les cent mille
  fichiers de la discothèque. Deux dossiers que leurs noms séparent — parce que l'un est rangé sous
  `Sauvegarde de 2019` et non sous un nom d'artiste — ne seront donc jamais confrontés, même si
  leurs empreintes l'auraient tranché en un instant. C'est le prix de ne pas tout ouvrir, et il est
  payé sciemment.
- **Ce qui n'est pas de la musique n'est pas dédoublonné.** Les liens durs ne sont relevés que sur
  les morceaux, là où ils changent une décision. Une pochette atteinte par deux chemins est donc
  comptée deux fois dans le poids hors musique — personne ne supprime un dossier pour ses
  pochettes, et le chiffre reste un ordre de grandeur.

## Architecture

```
fr.musique
├── Main, CodeSortie          ligne de commande, enchaînement des cinq étapes
├── Version                   numéro écrit par la construction, jamais répété dans le code
├── nom/                      lecture d'un chemin, et rien d'autre
│   ├── MotsTechniques        où s'arrête un titre ; éditions, versions, titres génériques
│   ├── Numeros               arabes, romains, nombres en lettres
│   ├── CleDeTitre            normalisation, clé sans article, artiste rangé à l'envers
│   ├── Formats               ce qu'un format vaut et ce qu'il promet
│   ├── ParseurDeNomDePiste   numéro, disque, artiste, titre
│   ├── ParseurDeNomDAlbum    artiste, titre, année, volume, édition, version, débit
│   ├── PrefixeDeCollection   patron commun aux fichiers d'un dossier
│   ├── OrigineDeLArtiste     d'où vient l'artiste, et ce que cela vaut
│   └── IdentificationDAlbum  identité construite depuis le chemin et les pistes
├── scan/
│   ├── Parcours              entrées-sorties seules : chemins, tailles, clés de fichiers
│   ├── Arborescence          ce que le parcours a vu, avant qu'aucun nom soit lu
│   ├── Identification        calcul seul : disques réunis, noms lus, dossier par dossier
│   └── Inventaire            albums retenus, et ce qui pèse vraiment
├── media/                    ce qu'on sait du fichier lui-même, et non de son nom
│   ├── Flac, Mp3, Mp4Audio, Ogg, Riff   durée, étiquettes et empreinte, lues dans l'en-tête
│   ├── CommentairesVorbis    le dialecte d'étiquettes que flac, ogg et opus partagent
│   ├── EtiquettesDuFichier   aiguillage, et refus de jamais faire échouer l'analyse
│   └── Etiquettes            table des étiquettes lues, et débit qui s'en déduit
├── doublons/
│   ├── Album, Piste          un album est un dossier ; une piste est un fichier
│   ├── ChercheurDeDoublons   regroupement des dossiers, niveaux de confiance
│   ├── ChercheurDePistes     les mêmes morceaux d'un dossier à l'autre
│   ├── Qualite               lequel garder, quand le poids ne suffit pas à le dire
│   ├── VerificationParLesEtiquettes  ce que les fichiers font dire d'un rapprochement
│   └── LiensDurs             deux dossiers, les mêmes fichiers : ce n'est pas un doublon
├── suivi/                    ligne d'attente sur la sortie d'erreur, jamais sur le rapport
└── rapport/
    ├── Analyse               ce que l'analyse a produit, sous la forme où le rapport le reçoit
    ├── Rapport               ce que les trois formes ont en commun
    ├── RapportConsole        lisible, adapté à la console
    ├── RapportJson           document complet, poids en octets
    ├── RapportCsv            une ligne par dossier ou par fichier
    ├── AlbumsIncomplets      trous dans la numérotation
    ├── PistesSuspectes       trop légères pour leur format, au poids ou au débit
    ├── Rangements            là où le programme voit mal, et pourquoi
    ├── Glyphes               ce que la sortie sait écrire, et la traduction sinon
    └── SortieDuRapport       jeu de caractères réel de la sortie standard
```

### Le partage entre les entrées-sorties et le calcul

`Parcours` ne lit aucun nom : il rend des chemins, des tailles et des clés de fichiers.
`Identification` ne touche jamais au disque : elle n'a devant elle que des chaînes de caractères.
La frontière n'est pas décorative — c'est elle qui permet de mener la seconde moitié sur plusieurs
fils, dossier par dossier, sans qu'aucun ne dépende des autres, et de l'éprouver sur une
arborescence fabriquée sans rien écrire.

### La parenté avec `movie-analysis`

Ce programme est le frère du voisin `movie-analysis`, dont il reprend l'ossature : mêmes étapes,
mêmes niveaux de confiance, mêmes trois formes de rapport, même refus d'écrire. Trois choses
seulement diffèrent, et toutes viennent du domaine :

- **l'unité est le dossier**, non le fichier — d'où le rattachement des disques d'un coffret, et un
  classement qui porte sur des répertoires ;
- **le format est une donnée**, portée par l'extension, là où une résolution n'est qu'une
  déclaration ; en revanche le débit, lui, demande d'ouvrir le fichier ;
- **l'année ne sépare plus**, parce qu'une réédition est un doublon, tandis qu'une **mention de
  version** sépare — un `Unplugged` n'est pas l'album studio du même nom.
