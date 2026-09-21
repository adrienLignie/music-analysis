package fr.musique.nom;

import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecture du nom d'un dossier d'album.
 *
 * <p>Le nom est découpé sur les tirets entourés d'espaces, puis chaque segment est trié : une
 * année, un artiste, un titre, ou de la technique à jeter. Les groupes entre parenthèses et
 * crochets sont sortis d'abord, car c'est là que se rangent l'année, l'édition et le format.
 *
 * <pre>
 * Daft Punk - Discovery (2001) [FLAC]         artiste, titre, année, format
 * 1973 - The Dark Side Of The Moon            année en tête, titre
 * The Wall [Remastered 2011] {24bit}          titre, édition, technique
 * Nirvana - MTV Unplugged In New York         artiste, titre — et une version, pas une édition
 * </pre>
 *
 * <h2>Ce que la méthode garantit, et ce qu'elle ne garantit pas</h2>
 * Elle ne prétend pas comprendre les titres : elle prétend produire une clé <b>reproductible</b>
 * et ne jamais confondre deux disques différents. Les deux pièges qui ont guidé son écriture :
 * <ul>
 *   <li>un titre qui est un nombre — {@code 1984}, {@code 1999}, {@code 90125} — ne doit pas se
 *       faire prendre son nom pour une année ;</li>
 *   <li>un {@code Unplugged} ou un {@code Live} ne se confond jamais avec l'album studio du même
 *       titre : c'est la seule erreur de ce programme qui détruirait un disque irremplaçable.</li>
 * </ul>
 */
public final class ParseurDeNomDAlbum {

    private static final int ANNEE_MINIMUM = 1900;
    private static final int ANNEES_D_AVANCE_TOLEREES = 1;
    private static final int LONGUEUR_D_UNE_ANNEE = 4;
    private static final int LETTRES_MINIMALES_D_UN_TITRE = 4;

    /** Un groupe entre parenthèses, crochets ou accolades, contenu capturé. */
    private static final Pattern GROUPE = Pattern.compile("[(\\[{]([^)\\]}]*)[)\\]}]");

    /** Séparateur entre segments d'un nom de dossier, tel qu'il s'écrit réellement. */
    private static final Pattern SEPARATEUR = Pattern.compile("\\s+-\\s+|\\s+–\\s+|_-_");

    /** Unités qui introduisent un numéro de volume : {@code Vol. 2}, {@code Part II}. */
    private static final Set<String> UNITES_DE_VOLUME =
            Set.of("vol", "volume", "part", "partie", "pt", "chapter", "chapitre", "act");

    private final int anneeMaximum;

    /** Parseur calé sur l'année courante, tolérant un an d'avance pour les sorties annoncées. */
    public ParseurDeNomDAlbum() {
        this(Year.now().getValue() + ANNEES_D_AVANCE_TOLEREES);
    }

    /** Parseur calé sur une année maximale donnée, pour que les tests ne dépendent pas de la date. */
    public ParseurDeNomDAlbum(int anneeMaximum) {
        this.anneeMaximum = anneeMaximum;
    }

    /** Analyse le nom d'un dossier d'album, sans rien savoir de son dossier parent. */
    public NomDAlbum analyser(String nomDuDossier) {
        return analyser(nomDuDossier, null);
    }

    /**
     * Analyse le nom d'un dossier d'album.
     *
     * @param nomDuDossier       nom du dossier qui contient les pistes
     * @param nomDuDossierParent nom du dossier au-dessus, d'où l'artiste est tiré quand le dossier
     *                           de l'album n'en porte pas ; {@code null} quand l'album est
     *                           directement sous une racine analysée, auquel cas le dossier
     *                           au-dessus n'est pas un artiste mais un point de départ
     */
    public NomDAlbum analyser(String nomDuDossier, String nomDuDossierParent) {
        List<String> titresAlternatifs = new ArrayList<>();
        Set<String> editions = new LinkedHashSet<>();
        Set<String> versions = new LinkedHashSet<>();
        String reste = extraireLesGroupes(nomDuDossier, titresAlternatifs, editions, versions);
        OptionalInt anneeEntreParentheses = relireAnneeDesGroupes(nomDuDossier);

        List<String> jetonsDuNom = ParseurDeNomDePiste.decouper(nomDuDossier);
        releverLesMentions(jetonsDuNom, editions, versions);
        OptionalInt debit = Formats.debitAnnonce(jetonsDuNom);

        OptionalInt annee = anneeEntreParentheses;
        List<String> utiles = new ArrayList<>();
        for (String segment : decouperEnSegments(reste)) {
            OptionalInt anneeDuSegment = lireAnnee(CleDeTitre.normaliser(segment));
            // Un segment qui n'est qu'une année est écarté du titre — mais une seule fois : quand
            // l'année est déjà connue, un second nombre de quatre chiffres n'en est plus une.
            // C'est ce qui sauve « Van Halen - 1984 (1984) », dont le titre est un millésime.
            if (anneeDuSegment.isPresent() && annee.isEmpty()) {
                annee = anneeDuSegment;
                continue;
            }
            if (estEntierementTechnique(segment)) {
                continue;
            }
            utiles.add(segment);
        }

        String artisteLu = utiles.size() > 1 ? utiles.get(0) : null;
        String titreBrut = utiles.isEmpty()
                ? nomDuDossier
                : utiles.get(utiles.size() > 1 ? 1 : 0);

        List<String> jetonsDuTitre = garderLeTitre(ParseurDeNomDePiste.decouper(titreBrut));
        OptionalInt volume = extraireLeVolume(jetonsDuTitre);
        retirerLesMentionsFinales(jetonsDuTitre);
        if (annee.isEmpty()) {
            annee = retirerLAnneeDuTitre(jetonsDuTitre);
        }
        String titre = String.join(" ", jetonsDuTitre).trim();
        if (titre.isEmpty()) {
            // Un titre ne peut pas être vide : mieux vaut le nom du dossier tel quel qu'une clé
            // vide, sous laquelle tous les albums mal nommés se retrouveraient doublons.
            titre = CleDeTitre.normaliser(titreBrut);
        }

        return construire(
                artisteLu, nomDuDossierParent, titre, titresAlternatifs, annee, volume, editions,
                versions, debit);
    }

    private NomDAlbum construire(
            String artisteLu, String nomDuDossierParent, String titre,
            List<String> titresAlternatifs, OptionalInt annee, OptionalInt volume,
            Set<String> editions, Set<String> versions, OptionalInt debit) {
        Optional<String> artiste = Optional.ofNullable(artisteLu).map(String::trim);
        OrigineDeLArtiste origine = OrigineDeLArtiste.DOSSIER_DE_L_ALBUM;
        if (artiste.isEmpty() && nomDuDossierParent != null) {
            String duParent = nettoyerUnArtiste(nomDuDossierParent);
            if (!duParent.isBlank()) {
                artiste = Optional.of(duParent);
                origine = OrigineDeLArtiste.DOSSIER_PARENT;
            }
        }
        String cleArtiste = artiste.map(CleDeTitre::normaliserArtiste).orElse("");
        boolean compilation = MotsTechniques.estArtistesMultiples(cleArtiste);
        if (compilation) {
            // « Various Artists » n'est pas un artiste : le garder comme clé réunirait sous un
            // même nom toutes les compilations de la bibliothèque.
            cleArtiste = "";
        }
        return new NomDAlbum(
                compilation ? Optional.empty() : artiste,
                cleArtiste,
                artiste.isEmpty() || compilation ? OrigineDeLArtiste.INCONNUE : origine,
                titre,
                CleDeTitre.normaliser(titre),
                CleDeTitre.normaliserSansArticle(titre),
                titresAlternatifs,
                annee,
                volume,
                editions,
                versions,
                debit,
                compilation);
    }

    /** Retire d'un nom de dossier d'artiste ce qui n'est pas son nom : {@code [FLAC]}, années. */
    private static String nettoyerUnArtiste(String nomDuDossier) {
        String sansGroupes = GROUPE.matcher(nomDuDossier).replaceAll(" ").trim();
        return sansGroupes.isBlank() ? nomDuDossier.trim() : sansGroupes;
    }

    /**
     * Sort du nom les groupes entre parenthèses, crochets et accolades, en triant leur contenu.
     *
     * <p>Un groupe est soit une année, soit purement technique ({@code [FLAC]}, {@code {24bit}}),
     * soit une mention d'édition ou de version, soit un titre alternatif. Ce dernier cas est celui
     * qui a de la valeur : il relie un album rangé sous son titre original à un autre rangé sous
     * sa traduction.
     */
    private String extraireLesGroupes(
            String nom, List<String> titresAlternatifs, Set<String> editions,
            Set<String> versions) {
        Matcher matcher = GROUPE.matcher(nom);
        StringBuilder reste = new StringBuilder();
        int curseur = 0;
        while (matcher.find()) {
            reste.append(nom, curseur, matcher.start()).append(' ');
            curseur = matcher.end();
            String contenu = matcher.group(1).trim();
            if (contenu.isEmpty() || lireAnnee(contenu).isPresent()) {
                continue;
            }
            List<String> jetons = ParseurDeNomDePiste.decouper(contenu);
            if (jetons.isEmpty()) {
                continue;
            }
            // Un groupe qui mêle une mention et une année — « [Remastered 2011] » — est une
            // mention : l'année qui l'accompagne est celle de la réédition, pas de l'œuvre.
            if (jetons.stream().allMatch(ParseurDeNomDAlbum::estUneMentionOuUnNombre)) {
                releverLesMentions(jetons, editions, versions);
                continue;
            }
            if (jetons.stream().allMatch(ParseurDeNomDAlbum::estUnJetonSansValeur)
                    || compterLesLettres(jetons) <= LETTRES_MINIMALES_D_UN_TITRE) {
                continue;
            }
            releverLesMentions(jetons, editions, versions);
            titresAlternatifs.add(contenu);
        }
        reste.append(nom.substring(curseur));
        return reste.toString();
    }

    private static boolean estUneMentionOuUnNombre(String jeton) {
        return MotsTechniques.estEdition(jeton) || MotsTechniques.estVersion(jeton)
                || Numeros.lireEntier(jeton).isPresent();
    }

    private static boolean estUnJetonSansValeur(String jeton) {
        return MotsTechniques.estTechnique(jeton) || Numeros.lireEntier(jeton).isPresent();
    }

    private static int compterLesLettres(List<String> jetons) {
        return jetons.stream().mapToInt(String::length).sum();
    }

    /** Relit les groupes pour n'en garder que l'année, qui prime sur une année laissée en vrac. */
    private OptionalInt relireAnneeDesGroupes(String nom) {
        Matcher matcher = GROUPE.matcher(nom);
        OptionalInt trouvee = OptionalInt.empty();
        while (matcher.find()) {
            OptionalInt annee = lireAnnee(matcher.group(1).trim());
            if (annee.isPresent()) {
                trouvee = annee;
            }
        }
        return trouvee;
    }

    private static List<String> decouperEnSegments(String nom) {
        List<String> segments = new ArrayList<>();
        for (String segment : SEPARATEUR.split(nom)) {
            if (!segment.isBlank()) {
                segments.add(segment.trim());
            }
        }
        return segments;
    }

    private static boolean estEntierementTechnique(String segment) {
        List<String> jetons = ParseurDeNomDePiste.decouper(segment);
        return !jetons.isEmpty() && jetons.stream().allMatch(MotsTechniques::estTechnique);
    }

    private static void releverLesMentions(
            List<String> jetons, Set<String> editions, Set<String> versions) {
        for (String jeton : jetons) {
            if (MotsTechniques.estEdition(jeton)) {
                editions.add(jeton);
            }
            if (MotsTechniques.estVersion(jeton)) {
                versions.add(jeton);
            }
        }
    }

    /**
     * Coupe la liste au premier jeton technique : le titre est ce qui précède.
     *
     * <p>Le premier jeton est gardé quoi qu'il arrive. Un titre ne peut pas être vide, et des
     * albums portent pour titre entier un mot du vocabulaire technique : {@code Vinyl},
     * {@code Bits}, {@code Promo}.
     */
    private static List<String> garderLeTitre(List<String> jetons) {
        List<String> titre = new ArrayList<>();
        for (int i = 0; i < jetons.size(); i++) {
            if (i > 0 && MotsTechniques.estTechnique(jetons.get(i))) {
                break;
            }
            titre.add(jetons.get(i));
        }
        return titre;
    }

    /**
     * Retire du titre les mentions d'édition ou de version qui le <b>terminent</b>, et elles
     * seules.
     *
     * <p>{@code Discovery Remixes} devient {@code Discovery}, dont il sera séparé par sa mention de
     * version. Mais {@code MTV Unplugged In New York} garde son titre entier : sa mention est au
     * milieu, et l'amputer laisserait un album nommé {@code MTV}. La règle tient en une phrase —
     * une mention n'est retirée que là où elle ne peut être qu'un ajout au titre.
     */
    private static void retirerLesMentionsFinales(List<String> jetons) {
        while (jetons.size() > 1) {
            String dernier = jetons.get(jetons.size() - 1);
            if (!MotsTechniques.estEdition(dernier) && !MotsTechniques.estVersion(dernier)) {
                return;
            }
            jetons.remove(jetons.size() - 1);
        }
    }

    /**
     * Retire l'année du titre quand elle y traîne, et la rend.
     *
     * <p>Jamais en première position : {@code 1984} et {@code 1999} sont des titres d'albums, et
     * les vider de leur seul mot les réunirait tous sous une clé vide.
     */
    private OptionalInt retirerLAnneeDuTitre(List<String> jetons) {
        for (int i = jetons.size() - 1; i >= 1; i--) {
            OptionalInt annee = lireAnnee(jetons.get(i));
            if (annee.isPresent()) {
                jetons.remove(i);
                return annee;
            }
        }
        return OptionalInt.empty();
    }

    private OptionalInt lireAnnee(String jeton) {
        if (jeton.length() != LONGUEUR_D_UNE_ANNEE) {
            return OptionalInt.empty();
        }
        OptionalInt valeur = Numeros.lireEntier(jeton);
        if (valeur.isEmpty()) {
            return OptionalInt.empty();
        }
        int annee = valeur.getAsInt();
        return annee >= ANNEE_MINIMUM && annee <= anneeMaximum ? valeur : OptionalInt.empty();
    }

    /**
     * Extrait le numéro de volume et le retire du titre.
     *
     * <p>Seule la forme explicite est lue — {@code Vol. 2}, {@code Part II} —, jamais un nombre
     * isolé : {@code Chicago 17} et {@code Led Zeppelin IV} portent leur numéro dans leur titre,
     * et le leur retirer réunirait toute la discographie sous une clé commune.
     */
    private static OptionalInt extraireLeVolume(List<String> jetons) {
        for (int i = 0; i < jetons.size() - 1; i++) {
            if (!UNITES_DE_VOLUME.contains(jetons.get(i))) {
                continue;
            }
            OptionalInt valeur = Numeros.lireVolume(jetons.get(i + 1));
            if (valeur.isPresent()) {
                jetons.remove(i + 1);
                jetons.remove(i);
                return valeur;
            }
        }
        return OptionalInt.empty();
    }
}
