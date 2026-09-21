package fr.musique.doublons;

import fr.musique.nom.NomDePiste;
import java.nio.file.Path;

/**
 * Un fichier audio : son emplacement, son poids, et ce qu'on a su lire de son nom.
 *
 * <p>La piste n'est pas l'unité du rapport — c'est l'album qui l'est —, mais c'est l'unité du
 * disque : c'est elle qui pèse, elle qui porte un numéro, elle qui manque quand un album est
 * incomplet.
 *
 * @param chemin       chemin du fichier
 * @param taille       poids du fichier en octets
 * @param nom          identité lue dans le nom du fichier
 * @param cleDuFichier ce qui désigne le fichier <b>physique</b> — couple périphérique-inœud sous
 *                     Unix —, ou {@code null} quand le système de fichiers n'en fournit pas
 */
public record Piste(Path chemin, long taille, NomDePiste nom, Object cleDuFichier) {

    /** Piste dont on ne connaît pas la clé de fichier, forme la plus simple. */
    public Piste(Path chemin, long taille, NomDePiste nom) {
        this(chemin, taille, nom, null);
    }
}
