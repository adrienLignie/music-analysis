package fr.musique.scan;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ce qui est retenu et ce qui est écarté pendant le parcours.
 *
 * @param tailleMinimale     en deçà, un fichier audio n'est pas tenu pour un morceau
 * @param dossiersExclus     noms de dossiers ignorés, comparés sans tenir compte de la casse
 * @param motifsDeNomsExclus fragments de noms de fichiers à écarter
 */
public record CriteresDeScan(
        long tailleMinimale, Set<String> dossiersExclus, Set<String> motifsDeNomsExclus) {

    /**
     * Seuil par défaut : 300 ko.
     *
     * <p>Bien plus bas que celui d'une vidéothèque, et pour cause : un morceau de deux minutes en
     * mp3 pèse deux mégaoctets, et un interlude de trente secondes moins d'un. Le seuil n'écarte
     * donc que ce qui ne peut pas être de la musique — un fichier de quelques dizaines de
     * kilo-octets est un bip, un silence, ou un téléchargement qui n'a jamais commencé.
     */
    public static final long TAILLE_MINIMALE_PAR_DEFAUT = 300L * 1024;

    /**
     * Corbeilles et index système, écartés sans être même pesés.
     *
     * <p>Les parcourir ferait remonter comme doublons des fichiers déjà supprimés. Et ce qu'ils
     * pèsent n'appartient à aucun album : une corbeille n'est pas le livret d'un disque, c'est un
     * problème d'une autre nature, que cet outil-ci n'a pas à traiter.
     */
    private static final Set<String> DOSSIERS_SYSTEME = Set.of(
            "$recycle.bin", "system volume information", "@eadir", "#recycle", ".trash");

    /**
     * Dossiers qui accompagnent la musique sans en être.
     *
     * <p>Pochettes, livrets numérisés, listes de lecture : rien de tout cela n'est un morceau, et
     * un dossier {@code Scans} ne doit jamais être pris pour un album. Mais cela <b>occupe la
     * place</b>, souvent beaucoup — un livret numérisé en trois cents points par pouce pèse plus
     * lourd que le disque qu'il illustre. Leur poids est donc porté au compte de l'album qui les
     * abrite, sous {@link DossierTrouve#octetsHorsAudio}, là où il se voit.
     */
    private static final Set<String> DOSSIERS_DE_PRESENTATION = Set.of(
            "scans", "scan", "artwork", "covers", "cover", "booklet", "booklets", "playlists");

    /**
     * Fragments qui trahissent un fichier annexe plutôt qu'un morceau.
     *
     * <p>La liste est volontairement courte. Un titre d'album ou de chanson peut contenir presque
     * n'importe quel mot — {@code Sample} et {@code Intro} sont de vrais titres —, et écarter sur
     * le nom coûte donc plus cher ici que dans une vidéothèque.
     */
    private static final Set<String> NOMS_ANNEXES = Set.of(
            "audiocheck", "test tone", "silence.mp3", "pregap");

    /** Fichiers de téléchargement inachevés, qu'il ne faut ni compter ni comparer. */
    private static final Pattern EN_COURS = Pattern.compile("(?i).*\\.(part|crdownload|!ut|tmp)$");

    /** Critères par défaut, sans exclusion supplémentaire. */
    public static CriteresDeScan parDefaut() {
        return new CriteresDeScan(TAILLE_MINIMALE_PAR_DEFAUT, DOSSIERS_SYSTEME, NOMS_ANNEXES);
    }

    /**
     * Indique si ce dossier accompagne la musique sans en être.
     *
     * <p>Il n'est pas un album et n'en abrite pas, mais ce qu'il contient pèse sur le disque et
     * revient à l'album du dessus. Les exclusions demandées en ligne de commande n'entrent pas
     * ici : {@code -x Karaoke} veut dire « ne me parle pas de ça », pas « compte-le ailleurs ».
     */
    public boolean dossierDePresentation(Path dossier) {
        Path nom = dossier.getFileName();
        return nom != null && DOSSIERS_DE_PRESENTATION.contains(enMinuscules(nom.toString()));
    }

    /** Mêmes critères, augmentés des exclusions demandées en ligne de commande. */
    public CriteresDeScan avecExclusions(List<String> dossiersSupplementaires) {
        Set<String> dossiers = new LinkedHashSet<>(dossiersExclus);
        dossiersSupplementaires.stream().map(CriteresDeScan::enMinuscules).forEach(dossiers::add);
        return new CriteresDeScan(tailleMinimale, Set.copyOf(dossiers), motifsDeNomsExclus);
    }

    /** Mêmes critères, avec un autre seuil de taille. */
    public CriteresDeScan avecTailleMinimale(long nouvelleTaille) {
        return new CriteresDeScan(nouvelleTaille, dossiersExclus, motifsDeNomsExclus);
    }

    /** Indique si ce dossier doit être ignoré, lui et tout ce qu'il contient. */
    public boolean dossierExclu(Path dossier) {
        Path nom = dossier.getFileName();
        return nom != null && dossiersExclus.contains(enMinuscules(nom.toString()));
    }

    /** Indique si ce nom de fichier désigne autre chose qu'un morceau. */
    public boolean nomExclu(String nomDeFichier) {
        if (EN_COURS.matcher(nomDeFichier).matches()) {
            return true;
        }
        String enMinuscules = enMinuscules(nomDeFichier);
        return motifsDeNomsExclus.stream().anyMatch(enMinuscules::contains);
    }

    private static String enMinuscules(String texte) {
        return texte.toLowerCase(Locale.ROOT);
    }
}
