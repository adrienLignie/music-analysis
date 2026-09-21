package fr.musique.nom;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Ce qu'on a su lire dans le nom d'un fichier audio.
 *
 * @param titre     titre tel que lu, accents et casse d'origine conservés
 * @param cle       titre normalisé, seule forme utilisée pour comparer
 * @param artiste   artiste lu dans le nom du fichier, quand il en porte un
 * @param numero    numéro de piste, quand le nom en porte un ; c'est lui qui dira si l'album a
 *                  des trous
 * @param disque    numéro de disque, pour les albums livrés en plusieurs galettes
 * @param extension extension en minuscules, sans le point : la seule chose qu'un nom de fichier
 *                  audio dise à coup sûr
 */
public record NomDePiste(
        String titre,
        String cle,
        Optional<String> artiste,
        OptionalInt numero,
        OptionalInt disque,
        String extension) {

    /** Indique si le format de cette piste restitue la source intacte. */
    public boolean estSansPerte() {
        return Formats.estSansPerte(extension);
    }
}
