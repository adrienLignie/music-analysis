package fr.musique.scan;

import fr.musique.nom.MotsTechniques;
import fr.musique.suivi.Avancement;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parcours des dossiers à analyser.
 *
 * <h2>Lecture seule, sans réserve</h2>
 * Rien n'est ouvert, rien n'est écrit, rien n'est renommé : seuls les attributs que le système
 * fournit déjà pendant l'énumération sont lus. Une interruption en cours de route ne laisse donc
 * aucun état à réparer, contrairement à un outil qui déplace des fichiers.
 *
 * <p>Les liens symboliques ne sont pas suivis. Les suivre exposerait à des boucles et ferait
 * compter deux fois un même album, ce qui gonflerait la place prétendument récupérable.
 *
 * <h2>Le parcours ne lit aucun nom</h2>
 * Il ne rend que des chemins, des tailles et des clés de fichiers. Lire les titres est un travail
 * de calcul, sans rapport avec le disque : le mêler au parcours obligerait à l'attendre dossier
 * après dossier, alors qu'il peut se faire ensuite, sur plusieurs fils à la fois. C'est
 * {@link Identification} qui s'en charge.
 *
 * <h2>Les fichiers sont rendus dossier par dossier, et c'est essentiel</h2>
 * Un album <b>est</b> un dossier. Le regroupement n'est donc pas une commodité de présentation :
 * c'est la structure même de ce qui sera analysé. Le chemin complet de chaque dossier est
 * conservé, car il faudra savoir qu'un {@code CD2} appartient à l'album du dessus.
 */
public final class Parcours {

    private static final Logger log = LoggerFactory.getLogger(Parcours.class);

    private final CriteresDeScan criteres;
    private final Avancement avancement;

    /** Parcours silencieux. */
    public Parcours(CriteresDeScan criteres) {
        this(criteres, Avancement.muet());
    }

    public Parcours(CriteresDeScan criteres, Avancement avancement) {
        this.criteres = criteres;
        this.avancement = avancement;
    }

    /**
     * Parcourt plusieurs racines en un seul lot.
     *
     * <p>Un seul lot et non un par racine : sans cela, un album présent à la fois sur le disque et
     * sur la sauvegarde ne serait jamais rapproché de lui-même.
     */
    public Arborescence parcourir(List<Path> racines) {
        avancement.etape("Parcours");
        Set<Path> retenues = new LinkedHashSet<>(racinesDistinctes(racines));
        Visiteur visiteur = new Visiteur(retenues);
        for (Path racine : retenues) {
            try {
                Files.walkFileTree(racine, visiteur);
            } catch (IOException echec) {
                log.warn("Parcours interrompu sous {} : {}", racine, echec.getMessage());
            } finally {
                // Un parcours interrompu laisse des dossiers ouverts dans la pile. Les abandonner
                // ici évite que la racine suivante empile par-dessus et perde ses fichiers.
                visiteur.abandonnerLesDossiersOuverts();
            }
        }
        return new Arborescence(
                visiteur.dossiers,
                retenues,
                repetitionsPhysiques(visiteur.dossiers),
                visiteur.fichiersIgnores,
                visiteur.dossiersIllisibles);
    }

    /**
     * Relève les chemins qui mènent à un fichier déjà atteint par un autre chemin.
     *
     * <p>Un lien dur donne plusieurs noms à un même contenu, qui n'occupe la place qu'une fois.
     * Compté deux fois, il gonfle le total de la bibliothèque et fait figurer deux fois le même
     * album au classement des plus lourds.
     *
     * <p>La clé du fichier, que le système fournit pendant l'énumération, répond à la question
     * sans un seul appel de plus, là où comparer deux à deux les fichiers de même taille en
     * demanderait des millions : dans une discothèque, les morceaux de même durée encodés au même
     * débit pèsent le même poids par milliers.
     *
     * <p>C'est le chemin le plus petit dans l'ordre alphabétique qui est tenu pour l'original, et
     * non le premier rencontré : l'ordre d'énumération d'un dossier n'est garanti par aucun
     * système, et le rapport doit rester comparable d'une exécution à l'autre.
     */
    private static Set<Path> repetitionsPhysiques(List<DossierTrouve> dossiers) {
        Map<Object, List<Path>> parFichierPhysique = new HashMap<>();
        for (DossierTrouve dossier : dossiers) {
            for (FichierTrouve fichier : dossier.fichiers()) {
                if (fichier.cleDuFichier() != null) {
                    parFichierPhysique
                            .computeIfAbsent(fichier.cleDuFichier(), inutilise -> new ArrayList<>())
                            .add(fichier.chemin());
                }
            }
        }
        Set<Path> repetitions = new LinkedHashSet<>();
        for (List<Path> chemins : parFichierPhysique.values()) {
            if (chemins.size() > 1) {
                chemins.stream()
                        .sorted(Comparator.comparing(Path::toString))
                        .skip(1)
                        .forEach(repetitions::add);
            }
        }
        return repetitions;
    }

    /**
     * Ramène les chemins donnés à des racines qui ne se recouvrent pas.
     *
     * <p>Deux chemins qui désignent le même dossier — la même racine écrite deux fois, ou un
     * dossier et l'un de ses parents — feraient compter chaque album deux fois : autant de faux
     * doublons, une place récupérable doublée, et un rapport qui propose de supprimer un dossier
     * au profit de lui-même.
     */
    private static List<Path> racinesDistinctes(List<Path> demandees) {
        List<Path> retenues = new ArrayList<>();
        for (Path demandee : demandees) {
            if (!Files.isDirectory(demandee)) {
                log.warn("Chemin ignoré, ce n'est pas un dossier : {}", demandee);
                continue;
            }
            Path resolue = resoudre(demandee);
            if (retenues.stream().anyMatch(resolue::startsWith)) {
                log.warn("Chemin ignoré, déjà couvert par une autre racine : {}", demandee);
                continue;
            }
            retenues.removeIf(dejaRetenue -> {
                boolean couverte = dejaRetenue.startsWith(resolue);
                if (couverte) {
                    log.warn("Racine {} abandonnée : elle est contenue dans {}",
                            dejaRetenue, demandee);
                }
                return couverte;
            });
            retenues.add(resolue);
        }
        return retenues;
    }

    /** Chemin réel, liens résolus. En cas d'échec, la forme absolue et normalisée suffit. */
    private static Path resoudre(Path chemin) {
        try {
            return chemin.toRealPath();
        } catch (IOException echec) {
            log.debug("Chemin non résolu, pris tel quel : {} ({})", chemin, echec.getMessage());
            return chemin.toAbsolutePath().normalize();
        }
    }

    /**
     * Un dossier en cours de visite : ce qu'on y a retenu, et ce qu'on y a seulement pesé.
     *
     * <p>Les deux vont ensemble et se dépilent ensemble : séparer les deux piles exposerait à ce
     * qu'un parcours interrompu en abandonne une et pas l'autre.
     */
    private static final class EnCours {

        private final List<FichierTrouve> fichiers = new ArrayList<>();
        private long octetsHorsAudio;
    }

    private final class Visiteur implements FileVisitor<Path> {

        private final Set<Path> racines;
        private final List<DossierTrouve> dossiers = new ArrayList<>();
        private final Deque<EnCours> parDossier = new ArrayDeque<>();
        private int fichiersIgnores;
        private int dossiersIllisibles;
        private int fichiersRetenus;

        /**
         * Profondeur à laquelle on se trouve dans un dossier de présentation.
         *
         * <p>Un compteur et non un booléen : un {@code Scans/2003} est encore de la présentation,
         * et il faut savoir quand on en sort.
         */
        private int dansLaPresentation;

        Visiteur(Set<Path> racines) {
            this.racines = racines;
        }

        void abandonnerLesDossiersOuverts() {
            if (!parDossier.isEmpty()) {
                log.debug("{} dossiers laissés ouverts par un parcours interrompu",
                        parDossier.size());
                parDossier.clear();
                dansLaPresentation = 0;
            }
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dossier, BasicFileAttributes attributs) {
            if (criteres.dossierExclu(dossier)) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            if (dansLaPresentation > 0 || criteres.dossierDePresentation(dossier)) {
                dansLaPresentation++;
            }
            parDossier.push(new EnCours());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path fichier, BasicFileAttributes attributs) {
            if (!attributs.isRegularFile()) {
                return FileVisitResult.CONTINUE;
            }
            // Toujours dans un dossier : les racines qui n'en sont pas ont été écartées avant le
            // parcours, et c'est ce qui garantit qu'il y a un compte où verser ce poids.
            EnCours courant = parDossier.peek();
            String nom = fichier.getFileName().toString();
            boolean retenable = dansLaPresentation == 0
                    && estUnFichierAudio(nom)
                    && !criteres.nomExclu(nom);
            // La taille et la clé du fichier viennent des attributs que le visiteur a déjà reçus :
            // pas d'appel système supplémentaire par fichier, ce qui compte sur cent mille
            // entrées en réseau.
            if (!retenable) {
                courant.octetsHorsAudio += attributs.size();
                return FileVisitResult.CONTINUE;
            }
            if (attributs.size() < criteres.tailleMinimale()) {
                fichiersIgnores++;
                courant.octetsHorsAudio += attributs.size();
                return FileVisitResult.CONTINUE;
            }
            courant.fichiers.add(new FichierTrouve(fichier, attributs.size(), attributs.fileKey()));
            avancement.pas(++fichiersRetenus, -1);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path fichier, IOException echec) {
            log.debug("Fichier illisible, ignoré : {} ({})", fichier, echec.getMessage());
            fichiersIgnores++;
            return FileVisitResult.CONTINUE;
        }

        /**
         * Referme un dossier, et décide de ce que devient son poids hors musique.
         *
         * <p>Un dossier qui porte des morceaux est un album : il garde ce poids, qui est celui de
         * ce qui les accompagne. Un dossier qui n'en porte aucun n'est pas un album — c'est un
         * dossier d'artiste, une racine, un dossier de pochettes — et son poids remonte au dossier
         * du dessus, jusqu'à trouver un album à qui l'attribuer. Faute de quoi ce qui pèse le plus
         * lourd dans une discothèque, un livret numérisé de quatre cents mégaoctets, n'apparaîtrait
         * dans aucune ligne du rapport.
         */
        @Override
        public FileVisitResult postVisitDirectory(Path dossier, IOException echec) {
            if (echec != null) {
                log.debug("Dossier partiellement lu : {} ({})", dossier, echec.getMessage());
                dossiersIllisibles++;
            }
            EnCours courant = parDossier.pop();
            if (dansLaPresentation > 0) {
                dansLaPresentation--;
            }
            if (courant.fichiers.isEmpty()) {
                EnCours parent = parDossier.peek();
                if (parent != null) {
                    parent.octetsHorsAudio += courant.octetsHorsAudio;
                }
                return FileVisitResult.CONTINUE;
            }
            dossiers.add(new DossierTrouve(dossier, courant.fichiers,
                    parentEstUneRacine(dossier), courant.octetsHorsAudio));
            return FileVisitResult.CONTINUE;
        }

        /**
         * Vrai quand le dossier au-dessus est l'un des points de départ de l'analyse.
         *
         * <p>Une racine est un choix de l'utilisateur, pas un élément de la bibliothèque : elle ne
         * peut donc être ni un nom d'artiste ni l'album auquel rattacher un dossier {@code CD1}.
         * Un dossier qui est lui-même une racine n'a pas de parent exploitable non plus.
         */
        private boolean parentEstUneRacine(Path dossier) {
            return racines.contains(dossier)
                    || dossier.getParent() != null && racines.contains(dossier.getParent());
        }
    }

    private static boolean estUnFichierAudio(String nomDeFichier) {
        int point = nomDeFichier.lastIndexOf('.');
        if (point < 0 || point == nomDeFichier.length() - 1) {
            return false;
        }
        return MotsTechniques.estExtensionAudio(
                nomDeFichier.substring(point + 1).toLowerCase(Locale.ROOT));
    }
}
