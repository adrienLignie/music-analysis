package fr.musique.media;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ce qu'un fichier audio dit de lui-même : sa durée et ses étiquettes.
 *
 * <h2>Pourquoi cela vaut le détour</h2>
 * Tout le reste du programme travaille sur des <b>noms de dossiers</b>. Un rangement se trompe,
 * ment, ou ne dit rien ; les étiquettes, elles, voyagent avec le fichier. Et la durée est la seule
 * façon de connaître le <b>débit</b> réel, c'est-à-dire ce que vaut vraiment un encodage : un
 * dossier nommé {@code FLAC} dont les pistes tiennent à cent kilobits par seconde n'est pas du
 * flac, c'est un mp3 réencodé qui a pris l'habit du sans perte.
 *
 * <h2>Ce que sa lecture coûte</h2>
 * Le fichier est ouvert en lecture seule et l'on n'en lit que quelques dizaines d'octets, sauf
 * pour l'Ogg, dont la durée n'est écrite qu'à la fin. Aucun son n'est décodé, aucun octet n'est
 * écrit.
 *
 * <p>C'est malgré tout une ouverture de fichier, là où le reste du programme se contente des
 * attributs que le parcours fournit déjà. Sur une bibliothèque de cent mille pistes, la faire
 * partout coûterait cent mille ouvertures, la plupart en réseau : elle ne se fait donc que sur
 * demande, et seulement là où la réponse change quelque chose.
 */
public final class EtiquettesDuFichier {

    private static final Logger log = LoggerFactory.getLogger(EtiquettesDuFichier.class);

    private static final Set<String> FLAC = Set.of("flac");
    private static final Set<String> MPEG = Set.of("mp3", "mp2");
    private static final Set<String> ISO = Set.of("m4a", "mp4", "m4b", "aac", "alac");
    private static final Set<String> OGG = Set.of("ogg", "oga", "opus");
    private static final Set<String> RIFF = Set.of("wav", "wave");

    private EtiquettesDuFichier() {
        // Lecture seule, pas d'instance.
    }

    /** Indique si l'on sait lire les étiquettes d'un fichier portant ce nom. */
    public static boolean saitLire(String nomDeFichier) {
        String extension = extensionDe(nomDeFichier);
        return FLAC.contains(extension) || MPEG.contains(extension) || ISO.contains(extension)
                || OGG.contains(extension) || RIFF.contains(extension);
    }

    /**
     * Étiquette du fichier, vide quand son format n'est pas lu ou qu'il ne se laisse pas lire.
     *
     * <p>Aucun échec n'est propagé : un fichier illisible, disparu depuis le parcours ou mal formé
     * ne doit pas interrompre une analyse dont les étiquettes ne sont qu'un raffinement.
     */
    public static Etiquette lire(Path chemin) {
        String extension = extensionDe(chemin.getFileName().toString());
        try (SeekableByteChannel canal = Files.newByteChannel(chemin)) {
            Fenetre fenetre = new Fenetre(canal);
            if (FLAC.contains(extension)) {
                return Flac.lire(fenetre);
            }
            if (MPEG.contains(extension)) {
                return Mp3.lire(fenetre);
            }
            if (ISO.contains(extension)) {
                return Mp4Audio.lire(fenetre);
            }
            if (OGG.contains(extension)) {
                return Ogg.lire(fenetre);
            }
            if (RIFF.contains(extension)) {
                return Riff.lire(fenetre);
            }
            return Etiquette.vide();
        } catch (IOException | RuntimeException echec) {
            log.debug("Étiquettes non lues pour {} : {}", chemin, echec.toString());
            return Etiquette.vide();
        }
    }

    private static String extensionDe(String nomDeFichier) {
        int point = nomDeFichier.lastIndexOf('.');
        return point < 0 ? "" : nomDeFichier.substring(point + 1).toLowerCase(Locale.ROOT);
    }
}
