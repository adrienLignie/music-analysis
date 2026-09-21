package fr.musique.media;

import fr.musique.suivi.Avancement;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Étiquettes lues, pour les fichiers dont on les a demandées.
 *
 * <p>La table est volontairement partielle. Sonder cent mille pistes pour n'en utiliser que
 * quelques centaines coûterait cent mille ouvertures de fichiers, la plupart en réseau ; seules
 * sont lues les pistes dont la réponse change quelque chose — celles des dossiers qu'on
 * soupçonne d'être en double, et celles dont le poids paraît suspect.
 *
 * <p>Une étiquette absente ne veut donc pas dire « fichier sans étiquette » mais « fichier non
 * sondé, ou illisible ». Aucun appelant ne doit conclure de son absence.
 */
public final class Etiquettes {

    private static final Logger log = LoggerFactory.getLogger(Etiquettes.class);

    private static final Etiquettes AUCUNE = new Etiquettes(Map.of());

    private final Map<Path, Etiquette> parChemin;

    private Etiquettes(Map<Path, Etiquette> parChemin) {
        this.parChemin = parChemin;
    }

    /** Aucune étiquette lue : l'état par défaut, quand l'utilisateur ne les a pas demandées. */
    public static Etiquettes aucune() {
        return AUCUNE;
    }

    /** Table bâtie sur des étiquettes déjà connues, pour les tests. */
    public static Etiquettes de(Map<Path, Etiquette> etiquettes) {
        return etiquettes.isEmpty() ? AUCUNE : new Etiquettes(Map.copyOf(etiquettes));
    }

    /**
     * Nombre de lectures menées de front, au plus.
     *
     * <p>Un plafond et non l'infini : au-delà, un disque en réseau passe son temps à arbitrer des
     * demandes concurrentes, et un disque à plateaux à déplacer sa tête. Trente-deux est le point
     * où l'attente se recouvre sans que le disque se mette à travailler contre lui-même.
     */
    private static final int LECTURES_DE_FRONT_AU_PLUS = 32;

    /**
     * Lit les étiquettes de chacun de ces fichiers.
     *
     * <h2>Pourquoi un ensemble de fils à part, et non celui de la machine virtuelle</h2>
     * Ce travail n'est pas du calcul : chaque fichier demande une ouverture et trois
     * positionnements, c'est-à-dire de l'attente. Le nombre de lectures qu'on peut mener de front
     * n'a donc rien à voir avec le nombre de cœurs — c'est ce que le disque accepte de servir à la
     * fois, et un disque en réseau en accepte bien plus qu'un processeur ne compte de cœurs.
     *
     * <p>L'ensemble de fils commun de la machine virtuelle, lui, est dimensionné sur les cœurs :
     * s'en servir plafonnerait le recouvrement à huit ou seize lectures sur une machine ordinaire,
     * alors que l'attente est là et n'occupe personne. Sur un disque en réseau, l'écart se compte
     * en minutes sur une grande discothèque.
     *
     * <p>L'ensemble est refermé dans tous les cas : un rapport écrit ne doit pas laisser derrière
     * lui des fils qui empêcheraient le programme de rendre la main.
     */
    public static Etiquettes lire(Collection<Path> chemins, Avancement avancement) {
        if (chemins.isEmpty()) {
            return AUCUNE;
        }
        avancement.etape("Lecture des étiquettes");
        Map<Path, Etiquette> lues = new LinkedHashMap<>();
        int fils = Math.min(LECTURES_DE_FRONT_AU_PLUS, chemins.size());
        ForkJoinPool lecteurs = new ForkJoinPool(fils);
        try {
            lecteurs.submit(() -> rassembler(chemins, avancement, lues)).get();
        } catch (InterruptedException interruption) {
            // Rendre l'interruption à qui saura quoi en faire, sans perdre ce qui a été lu : la
            // table partielle reste juste, une étiquette absente valant « non sondée ».
            Thread.currentThread().interrupt();
            log.debug("Lecture des étiquettes interrompue après {} fichiers", lues.size());
        } catch (ExecutionException echec) {
            // Aucun fichier ne peut faire échouer la lecture — chacun est protégé chez lui. Ce qui
            // arrive ici est donc une panne de l'ensemble de fils lui-même, et l'analyse continue
            // sans ce raffinement plutôt que de s'arrêter.
            log.warn("Lecture des étiquettes abandonnée : {}", echec.getCause().toString());
        } finally {
            lecteurs.shutdown();
        }
        return lues.isEmpty() ? AUCUNE : new Etiquettes(Map.copyOf(lues));
    }

    /**
     * Lit tous les fichiers de front et range ce qu'ils ont livré.
     *
     * <p>Le rassemblement est séquentiel et ordonné : deux exécutions doivent rendre la même
     * table, sans quoi le rapport cesserait d'être comparable d'une fois sur l'autre.
     */
    private static void rassembler(
            Collection<Path> chemins, Avancement avancement, Map<Path, Etiquette> lues) {
        AtomicInteger faits = new AtomicInteger();
        chemins.parallelStream()
                .map(chemin -> {
                    Etiquette etiquette = EtiquettesDuFichier.lire(chemin);
                    avancement.pas(faits.incrementAndGet(), chemins.size());
                    return etiquette.estVide() ? null : Map.entry(chemin, etiquette);
                })
                .filter(Objects::nonNull)
                .forEachOrdered(entree -> lues.put(entree.getKey(), entree.getValue()));
    }

    /** Étiquette de ce fichier, vide si elle n'a pas été demandée ou pas été lue. */
    public Etiquette de(Path chemin) {
        return parChemin.getOrDefault(chemin, Etiquette.vide());
    }

    /** Durée de ce fichier en secondes, vide si elle n'a pas été lue. */
    public OptionalDouble dureeDe(Path chemin) {
        return de(chemin).secondes();
    }

    /**
     * Empreinte du son de ce fichier, vide quand son format n'en porte pas ou qu'on ne l'a pas lu.
     *
     * <p>C'est la seule chose que le programme sache du contenu lui-même. Deux empreintes égales
     * démontrent que deux fichiers portent le même son ; deux empreintes différentes ne démontrent
     * rien, et une empreinte absente encore moins.
     */
    public Optional<String> empreinteDe(Path chemin) {
        return de(chemin).empreinteAudio();
    }

    /**
     * Débit réel de ce fichier, en octets par seconde.
     *
     * <p>C'est le seul chiffre qui dise ce que vaut un encodage. Un nom de dossier annonce ce
     * qu'il veut, une extension promet une famille de formats ; le poids divisé par la durée, lui,
     * ne se discute pas.
     */
    public OptionalLong debitDe(Path chemin, long octets) {
        OptionalDouble secondes = dureeDe(chemin);
        if (secondes.isEmpty() || secondes.getAsDouble() <= 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of((long) (octets / secondes.getAsDouble()));
    }

    /** Indique qu'aucune étiquette n'a été lue. */
    public boolean estVide() {
        return parChemin.isEmpty();
    }

    /** Nombre de fichiers dont on a lu quelque chose. */
    public int taille() {
        return parChemin.size();
    }

    /**
     * Durée en minutes et secondes, telle qu'on parle d'un morceau.
     *
     * <p>Les minutes et non les heures : un album se compte en dizaines de minutes, et écrire
     * {@code 0 h 42} pour un disque serait une précision empruntée au cinéma.
     */
    public static String enTexte(double secondes) {
        long total = Math.round(secondes);
        long heures = total / 3600;
        long minutes = (total % 3600) / 60;
        long restantes = total % 60;
        return heures == 0
                ? String.format(Locale.FRANCE, "%d min %02d s", minutes, restantes)
                : String.format(Locale.FRANCE, "%d h %02d min", heures, minutes);
    }

    /** Débit en kilobits par seconde, tel qu'on parle d'un encodage. */
    public static String debitEnTexte(long octetsParSeconde) {
        return String.format(Locale.FRANCE, "%d kbit/s", octetsParSeconde * 8 / 1000);
    }
}
