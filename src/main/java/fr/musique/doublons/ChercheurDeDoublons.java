package fr.musique.doublons;

import fr.musique.media.Etiquettes;
import fr.musique.nom.CleDeTitre;
import fr.musique.nom.MotsTechniques;
import fr.musique.nom.NomDAlbum;
import fr.musique.nom.OrigineDeLArtiste;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Regroupe les dossiers qui semblent porter le même album.
 *
 * <h2>Pourquoi on ne compare pas les titres deux à deux</h2>
 * Une distance entre chaînes, si fine soit-elle, réunit {@code Sécurité} et {@code Sérénité},
 * {@code Volume 1} et {@code Volume 2}, {@code Live} et {@code Life}. Le rapprochement se fait
 * donc par <b>égalité</b> de clé, sur quatre clés possibles : artiste et titre réunis, le titre
 * seul, le même sans son article, et chaque titre alternatif trouvé entre parenthèses.
 *
 * <p>Le titre seul n'est indexé que s'il n'est pas générique. Sans cette réserve, les quarante
 * {@code Greatest Hits} d'une discothèque formeraient un seul groupe de doublons, et les
 * {@code Best Of} avec eux : c'est le faux positif le plus massif qu'une bibliothèque musicale
 * puisse produire, là où une vidéothèque n'en produit aucun d'équivalent.
 *
 * <h2>Ce qui sépare deux dossiers malgré une clé commune</h2>
 * <ul>
 *   <li>Une <b>mention de version</b> différente : {@code Unplugged}, {@code Live},
 *       {@code Remixes} désignent une autre œuvre, pas une autre édition. Confondre les deux
 *       proposerait de supprimer un disque qui n'existe nulle part ailleurs — c'est l'erreur la
 *       plus coûteuse que ce programme puisse commettre, et la seule qu'il ne commet jamais.</li>
 *   <li>Un <b>numéro de volume</b> différent : {@code Hits Vol. 1} n'est pas {@code Hits
 *       Vol. 2}.</li>
 *   <li>Deux <b>artistes connus et différents</b> : deux albums homonymes de deux groupes ne se
 *       rapprochent pas.</li>
 * </ul>
 *
 * <h2>Pourquoi le rapprochement se fait en deux temps</h2>
 * Le regroupement est transitif — c'est ce qui réunit en un seul groupe trois rangements du même
 * album plutôt qu'en trois paires qui se recouvrent. Mais les trois premières règles ci-dessus
 * sont des <b>égalités</b> — mêmes versions, même volume, même artiste —, donc transitives par
 * construction, tandis que la quatrième ne l'est pas : un dossier qui ne nomme pas son artiste est
 * compatible avec <i>tous</i> les artistes. Réunir au fil des paires ferait de lui un pont, et
 * {@code Muse/Absolution}, {@code Absolution} et {@code Bob Dylan/Absolution} formeraient un seul
 * groupe où le rapport proposerait de supprimer l'un pour l'autre : exactement l'erreur que la
 * première règle s'interdit.
 *
 * <p>Les égalités sont donc réunies d'abord, puis les dossiers sans artiste rejoignent la classe
 * qui en nomme un — <b>à condition qu'une seule la réclame</b>. Deux artistes qui se le disputent
 * le laissent de côté : on ne sait pas duquel il est, et le supposer coûterait un original. Ce
 * dossier-là n'est pas perdu pour autant, c'est le propre de la section des dossiers mal rangés
 * que de signaler un album dont personne ne nomme l'artiste.
 *
 * <h2>Ce qui ne sépare pas, à la différence d'une vidéothèque</h2>
 * L'<b>année</b>. Deux dossiers du même artiste portant le même titre à vingt ans d'écart sont
 * une réédition, non deux œuvres : c'est même le cas le plus fréquent de doublon musical, celui
 * de l'album original et de son remastering. L'écart d'années fait descendre la confiance et
 * s'inscrit dans le motif ; il ne défait pas le rapprochement.
 *
 * <p>Le poids n'entre à aucun moment dans le rapprochement, et le nombre de pistes non plus : un
 * album téléchargé à moitié reste le même album, et c'est précisément ce qu'il faut signaler.
 */
public final class ChercheurDeDoublons {

    private static final Logger log = LoggerFactory.getLogger(ChercheurDeDoublons.class);

    /** Au-delà, un groupe est tenu pour un artefact de nommage plutôt que pour des doublons. */
    private static final int MEMBRES_AVANT_SOUPCON = 4;

    /**
     * Écart d'années au-delà duquel le rapprochement perd un cran de confiance.
     *
     * <p>Un an d'écart n'est rien : une parution européenne l'année suivant la parution
     * américaine, une date de pressage prise pour une date de sortie. Au-delà, il s'agit d'une
     * réédition — ce qui reste un doublon, mais mérite d'être regardé.
     */
    private static final int ECART_D_ANNEES_TOLERE = 1;

    /** Écart de pistes au-delà duquel deux dossiers ne portent pas le même contenu. */
    private static final int PISTES_D_ECART_TOLEREES = 1;

    /**
     * Cherche les doublons parmi ces albums.
     *
     * <p>Le résultat est trié par place récupérable décroissante, et l'ordre ne dépend pas de
     * l'ordre d'arrivée : deux exécutions sur la même bibliothèque rendent le même rapport, ce qui
     * permet de les comparer.
     */
    public List<GroupeDeDoublons> chercher(List<Album> albums) {
        UnionDAlbums union = new UnionDAlbums(albums.size());
        List<Rattachement> aArtisteEmprunte = new ArrayList<>();
        for (List<Integer> bloc : indexer(albums).values()) {
            rapprocherDansLeBloc(albums, bloc, union, aArtisteEmprunte);
        }
        rattacherLesDossiersSansArtiste(albums, aArtisteEmprunte, union);
        return construireLesGroupes(albums, union);
    }

    /**
     * Range chaque album sous toutes ses clés possibles.
     *
     * <p>Un {@link TreeMap} plutôt qu'une table de hachage : l'ordre de parcours des blocs devient
     * celui des clés, donc reproductible.
     */
    private static Map<String, List<Integer>> indexer(List<Album> albums) {
        Map<String, List<Integer>> index = new TreeMap<>();
        for (int i = 0; i < albums.size(); i++) {
            for (String cle : clesDe(albums.get(i).nom())) {
                index.computeIfAbsent(cle, inutilise -> new ArrayList<>()).add(i);
            }
        }
        return index;
    }

    /** Toutes les clés sous lesquelles un album peut être retrouvé. */
    static Set<String> clesDe(NomDAlbum nom) {
        Set<String> cles = new LinkedHashSet<>();
        cles.add(nom.cleComplete());
        if (!nom.aUnTitreGenerique()) {
            // Le titre seul relie deux rangements dont l'un ne nomme pas son artiste. Générique,
            // il relierait surtout des albums qui n'ont rien à voir.
            cles.add(nom.cle());
            cles.add(nom.cleSansArticle());
        }
        for (String alternatif : nom.titresAlternatifs()) {
            String cle = CleDeTitre.normaliser(alternatif);
            if (!cle.isEmpty() && !MotsTechniques.estUnTitreGenerique(cle)) {
                cles.add(nom.cleArtiste().isEmpty() ? cle : nom.cleArtiste() + " | " + cle);
                cles.add(cle);
            }
        }
        cles.removeIf(String::isEmpty);
        return cles;
    }

    /** Un dossier sans artiste, et le dossier qui en nomme un auquel il pourrait appartenir. */
    private record Rattachement(int sansArtiste, int avecArtiste) {}

    /**
     * Réunit ce qui se réunit sans supposition, et met de côté le reste.
     *
     * <p>Une égalité d'artistes — connus des deux côtés et identiques, ou inconnus des deux côtés
     * — est réunie tout de suite : l'ordre dans lequel les paires arrivent n'y change rien. Une
     * paire dont un seul côté nomme son artiste est mise en attente, parce qu'on ne peut pas
     * décider de son sort en la regardant seule.
     */
    private static void rapprocherDansLeBloc(
            List<Album> albums, List<Integer> bloc, UnionDAlbums union,
            List<Rattachement> aArtisteEmprunte) {
        for (int i = 0; i < bloc.size(); i++) {
            for (int j = i + 1; j < bloc.size(); j++) {
                int premier = bloc.get(i);
                int second = bloc.get(j);
                NomDAlbum unNom = albums.get(premier).nom();
                NomDAlbum lAutreNom = albums.get(second).nom();
                if (!sontLeMemeAlbum(unNom, lAutreNom)) {
                    continue;
                }
                if (unNom.cleArtiste().equals(lAutreNom.cleArtiste())) {
                    union.reunir(premier, second);
                } else if (unNom.cleArtiste().isEmpty()) {
                    aArtisteEmprunte.add(new Rattachement(premier, second));
                } else {
                    aArtisteEmprunte.add(new Rattachement(second, premier));
                }
            }
        }
    }

    /**
     * Rattache chaque dossier sans artiste à celui qui en nomme un, et à un seul.
     *
     * <p>La décision se prend par <b>classe</b> et non par paire : deux dossiers sans artiste déjà
     * réunis par leur titre sont le même album, et doivent donc suivre le même sort. La classe
     * rejoint l'artiste connu qui la réclame quand il est le seul ; dès que deux artistes
     * différents la réclament, elle reste à l'écart, puisque la rattacher à l'un ferait proposer
     * la suppression d'un album de l'autre.
     *
     * <p>Les classes sont parcourues dans l'ordre de leurs indices, pour que deux exécutions sur
     * la même bibliothèque prennent les mêmes décisions.
     */
    private static void rattacherLesDossiersSansArtiste(
            List<Album> albums, List<Rattachement> rattachements, UnionDAlbums union) {
        Map<Integer, List<Rattachement>> parClasse = new TreeMap<>();
        Map<Integer, Set<String>> pretendants = new TreeMap<>();
        for (Rattachement rattachement : rattachements) {
            // La racine est relevée avant tout rattachement : les classes formées au premier temps
            // sont homogènes en artiste, et c'est sur elles que la décision porte.
            int classe = union.racineDe(rattachement.sansArtiste());
            parClasse.computeIfAbsent(classe, inutilise -> new ArrayList<>()).add(rattachement);
            pretendants.computeIfAbsent(classe, inutilise -> new TreeSet<>())
                    .add(albums.get(rattachement.avecArtiste()).nom().cleArtiste());
        }
        for (Map.Entry<Integer, List<Rattachement>> classe : parClasse.entrySet()) {
            Set<String> artistes = pretendants.get(classe.getKey());
            if (artistes.size() > 1) {
                log.debug("Dossier sans artiste laissé à l'écart, {} artistes le réclament ({}) :"
                                + " {}",
                        artistes.size(), artistes,
                        albums.get(classe.getValue().get(0).sansArtiste()).dossier());
                continue;
            }
            for (Rattachement rattachement : classe.getValue()) {
                union.reunir(rattachement.sansArtiste(), rattachement.avecArtiste());
            }
        }
    }

    /** Décide si deux noms partageant une clé désignent bien le même album. */
    static boolean sontLeMemeAlbum(NomDAlbum premier, NomDAlbum second) {
        if (!premier.versions().equals(second.versions())) {
            return false;
        }
        if (premier.volumeEffectif() != second.volumeEffectif()) {
            return false;
        }
        return lesArtistesSontCompatibles(premier, second);
    }

    /**
     * Deux artistes sont compatibles si l'un des deux est inconnu, ou s'ils sont le même.
     *
     * <p>Une compilation n'est compatible qu'avec une autre compilation : {@code Various Artists}
     * n'est pas un artiste, et laisser un album d'un artiste nommé rejoindre une compilation
     * homonyme ferait proposer la suppression de l'un pour l'autre.
     */
    private static boolean lesArtistesSontCompatibles(NomDAlbum premier, NomDAlbum second) {
        if (premier.estCompilation() != second.estCompilation()) {
            return false;
        }
        if (premier.cleArtiste().isEmpty() || second.cleArtiste().isEmpty()) {
            return true;
        }
        return premier.cleArtiste().equals(second.cleArtiste());
    }

    private static List<GroupeDeDoublons> construireLesGroupes(
            List<Album> albums, UnionDAlbums union) {
        Map<Integer, List<Album>> parRacine = new TreeMap<>();
        for (int i = 0; i < albums.size(); i++) {
            int racine = union.racineDe(i);
            if (union.tailleDe(racine) > 1) {
                parRacine.computeIfAbsent(racine, inutilise -> new ArrayList<>())
                        .add(albums.get(i));
            }
        }
        return parRacine.values().stream()
                .map(ChercheurDeDoublons::composer)
                .sorted(GroupeDeDoublons.DU_PLUS_GROS_GAIN)
                .toList();
    }

    private static GroupeDeDoublons composer(List<Album> membres) {
        // Le meilleur exemplaire d'abord, et non le plus gros : le poids ne dit rien de ce que
        // vaut un encodage. Les débits réels ne sont pas connus à ce stade ; le groupe sera remis
        // dans l'ordre si l'on va lire les étiquettes.
        List<Album> tries = membres.stream()
                .sorted(Qualite.meilleurDAbord(Etiquettes.aucune(), membres))
                .toList();
        NiveauDeConfiance confiance = evaluer(tries);
        return new GroupeDeDoublons(tries, confiance, redigerLeMotif(tries, confiance));
    }

    /**
     * Évalue le rapprochement.
     *
     * <p>La confiance est forte quand rien n'a été supposé : même artiste connu partout, même
     * titre exact, années qui ne se contredisent pas, et autant de pistes d'un dossier à l'autre.
     * Il suffit qu'un seul de ces points manque pour que le groupe descende d'un cran — non parce
     * qu'il serait faux, mais parce qu'il demande un regard.
     */
    private static NiveauDeConfiance evaluer(List<Album> membres) {
        if (membres.size() > MEMBRES_AVANT_SOUPCON) {
            return NiveauDeConfiance.A_VERIFIER;
        }
        boolean memeCle = membres.stream()
                .map(album -> album.nom().cleComplete()).distinct().count() == 1;
        boolean artistesConnus = membres.stream().allMatch(ChercheurDeDoublons::aUnArtiste);
        return memeCle && artistesConnus && lesAnneesConcordent(membres)
                && lesPistesConcordent(membres)
                ? NiveauDeConfiance.FORTE
                : NiveauDeConfiance.MOYENNE;
    }

    private static boolean aUnArtiste(Album album) {
        return album.nom().origineDeLArtiste() != OrigineDeLArtiste.INCONNUE
                || album.nom().estCompilation();
    }

    /** Vrai quand aucune paire d'années connues ne s'écarte de plus de la tolérance. */
    static boolean lesAnneesConcordent(List<Album> membres) {
        List<Integer> connues = membres.stream()
                .map(album -> album.nom().annee())
                .filter(OptionalInt::isPresent)
                .map(OptionalInt::getAsInt)
                .toList();
        if (connues.size() < 2) {
            return true;
        }
        int minimum = connues.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int maximum = connues.stream().mapToInt(Integer::intValue).max().orElseThrow();
        return maximum - minimum <= ECART_D_ANNEES_TOLERE;
    }

    /** Vrai quand tous les dossiers portent à peu près le même nombre de pistes. */
    static boolean lesPistesConcordent(List<Album> membres) {
        int minimum = membres.stream().mapToInt(Album::nombreDePistes).min().orElse(0);
        int maximum = membres.stream().mapToInt(Album::nombreDePistes).max().orElse(0);
        return maximum - minimum <= PISTES_D_ECART_TOLEREES;
    }

    private static String redigerLeMotif(List<Album> membres, NiveauDeConfiance confiance) {
        if (confiance == NiveauDeConfiance.A_VERIFIER) {
            return "groupe de " + membres.size()
                    + " exemplaires : probable série de dossiers au nommage régulier,"
                    + " à regarder avant tout";
        }
        StringBuilder motif = new StringBuilder();
        String artistes = membres.stream()
                .map(album -> album.nom().artiste().orElse(
                        album.nom().estCompilation() ? "compilation" : "sans artiste"))
                .distinct()
                .collect(Collectors.joining(" / "));
        motif.append(artistes).append(" — « ").append(membres.stream()
                .map(album -> album.nom().cle())
                .distinct()
                .collect(Collectors.joining(" / "))).append(" »");

        String annees = membres.stream()
                .map(album -> album.nom().annee().isPresent()
                        ? String.valueOf(album.nom().annee().getAsInt())
                        : "sans année")
                .distinct()
                .collect(Collectors.joining(" / "));
        motif.append(", ").append(annees);
        if (!lesAnneesConcordent(membres)) {
            motif.append(" (réédition ?)");
        }
        motif.append(", ").append(membres.stream()
                .map(album -> album.nombreDePistes() + " pistes")
                .distinct()
                .collect(Collectors.joining(" / ")));

        String formats = membres.stream()
                .map(Album::formatDominant)
                .distinct()
                .collect(Collectors.joining(" / "));
        if (formats.contains("/")) {
            motif.append(", formats différents : ").append(formats);
        }
        String editions = membres.stream()
                .flatMap(album -> album.nom().editions().stream())
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
        if (!editions.isEmpty()) {
            motif.append(", éditions différentes : ").append(editions);
        }
        if (membres.stream().anyMatch(album ->
                album.nom().origineDeLArtiste() == OrigineDeLArtiste.DOSSIER_PARENT)) {
            // Écrit en clair : devant un rapprochement surprenant, il faut pouvoir comprendre d'où
            // vient l'artiste sans aller rouvrir les dossiers.
            motif.append(", artiste lu sur le dossier parent");
        }
        return motif.toString();
    }

    /**
     * Union-find minimal, sur les indices des albums.
     *
     * <p>Le rapprochement est transitif : si A rejoint B par leur titre et B rejoint C par son
     * titre alternatif, les trois forment un seul groupe. Une comparaison deux à deux sans cette
     * fusion produirait trois paires qui se recouvrent, et donc trois fois le même travail à
     * l'écran.
     */
    private static final class UnionDAlbums {

        private final int[] parent;
        private final int[] taille;

        UnionDAlbums(int nombreDAlbums) {
            parent = new int[nombreDAlbums];
            taille = new int[nombreDAlbums];
            for (int i = 0; i < nombreDAlbums; i++) {
                parent[i] = i;
                taille[i] = 1;
            }
        }

        int racineDe(int element) {
            int racine = element;
            while (parent[racine] != racine) {
                racine = parent[racine];
            }
            int courant = element;
            while (parent[courant] != racine) {
                int suivant = parent[courant];
                parent[courant] = racine;
                courant = suivant;
            }
            return racine;
        }

        int tailleDe(int racine) {
            return taille[racine];
        }

        void reunir(int premier, int second) {
            int racinePremier = racineDe(premier);
            int racineSecond = racineDe(second);
            if (racinePremier == racineSecond) {
                return;
            }
            if (taille[racinePremier] < taille[racineSecond]) {
                int echange = racinePremier;
                racinePremier = racineSecond;
                racineSecond = echange;
            }
            parent[racineSecond] = racinePremier;
            taille[racinePremier] += taille[racineSecond];
        }
    }
}
