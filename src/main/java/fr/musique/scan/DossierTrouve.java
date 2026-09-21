package fr.musique.scan;

import java.nio.file.Path;
import java.util.List;

/**
 * Les fichiers audio retenus dans un même dossier.
 *
 * <p>Le dossier n'est pas un rangement de commodité, c'est <b>l'unité d'analyse</b> : un album est
 * un dossier. Son chemin est donc conservé entier, et pas seulement son nom — c'est lui qui dira
 * qu'un dossier {@code CD2} appartient à l'album du dessus, et qui permettra de retrouver
 * l'artiste dans le dossier parent.
 *
 * @param chemin             chemin du dossier
 * @param fichiers           fichiers audio retenus qu'il contient, ses sous-dossiers exclus
 * @param parentEstUneRacine vrai quand le dossier au-dessus est l'une des racines analysées : ce
 *                           qui s'y trouve n'appartient pas à la bibliothèque et ne peut donc être
 *                           ni un nom d'artiste ni le dossier d'un album à disques multiples
 * @param octetsHorsAudio    poids de tout ce que ce dossier contient et qui n'est pas un morceau
 *                           retenu : pochettes, livrets numérisés, journaux d'extraction, fichiers
 *                           trop petits, et le contenu entier des sous-dossiers de présentation.
 *                           C'est de la place occupée que rien d'autre ne compte, et elle vaut
 *                           souvent plus qu'on ne croit
 */
public record DossierTrouve(
        Path chemin,
        List<FichierTrouve> fichiers,
        boolean parentEstUneRacine,
        long octetsHorsAudio) {

    public DossierTrouve {
        fichiers = List.copyOf(fichiers);
    }

    /** Dossier dont on n'a pas pesé ce qui n'est pas de la musique. */
    public DossierTrouve(Path chemin, List<FichierTrouve> fichiers, boolean parentEstUneRacine) {
        this(chemin, fichiers, parentEstUneRacine, 0);
    }

    /** Nom du dossier, chaîne vide pour une racine du système de fichiers. */
    public String nom() {
        Path nom = chemin.getFileName();
        return nom == null ? "" : nom.toString();
    }

    /**
     * Nom du dossier parent, ou {@code null} quand il n'y en a pas d'exploitable.
     *
     * <p>Une racine analysée n'est pas un parent exploitable : elle a été choisie par
     * l'utilisateur comme point de départ, et rien ne dit qu'elle porte un nom d'artiste.
     */
    public String nomDuParent() {
        if (parentEstUneRacine || chemin.getParent() == null) {
            return null;
        }
        Path nom = chemin.getParent().getFileName();
        return nom == null ? null : nom.toString();
    }
}
