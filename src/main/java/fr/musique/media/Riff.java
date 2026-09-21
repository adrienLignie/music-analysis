package fr.musique.media;

import java.io.IOException;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Durée d'un fichier {@code .wav}, lue dans ses deux blocs d'en-tête.
 *
 * <p>Le format est le plus simple de tous : un bloc {@code fmt } qui donne le nombre d'octets par
 * seconde, un bloc {@code data} qui annonce la longueur du son. La division des deux donne la
 * durée, exactement.
 *
 * <p>Le wav ne porte pas d'étiquettes dans le cas général — quelques logiciels y glissent un bloc
 * {@code LIST}, mais rarement, et jamais avec les champs qui nous intéressent. La durée seule est
 * donc lue : elle suffit à juger le débit, qui est ce qu'on vient chercher dans un format sans
 * compression.
 */
final class Riff {

    private static final String CONTENEUR = "RIFF";
    private static final String TYPE = "WAVE";
    private static final String FORMAT = "fmt ";
    private static final String SON = "data";

    /** En-tête d'un bloc : quatre octets de nom, quatre de longueur. */
    private static final int ENTETE = 8;

    /** Blocs examinés au plus, pour ne pas suivre un fichier malformé. */
    private static final int BLOCS_EXAMINES_AU_PLUS = 32;

    /** Position, dans le bloc de format, des quatre octets qui donnent le débit. */
    private static final int DECALAGE_DU_DEBIT = 8;

    private Riff() {
        // Lecture seule, pas d'instance.
    }

    /** Étiquette réduite à la durée, vide quand le fichier n'est pas un wav lisible. */
    static Etiquette lire(Fenetre fenetre) throws IOException {
        if (fenetre.taille() < 12
                || !CONTENEUR.equals(fenetre.etiquette(0))
                || !TYPE.equals(fenetre.etiquette(8))) {
            return Etiquette.vide();
        }
        long octetsParSeconde = 0;
        long position = 12;
        for (int blocs = 0; blocs < BLOCS_EXAMINES_AU_PLUS
                && position + ENTETE <= fenetre.taille(); blocs++) {
            String nom = fenetre.etiquette(position);
            long longueur = fenetre.entierPetitBoutien(position + 4);
            long contenu = position + ENTETE;
            if (longueur <= 0 || contenu + longueur > fenetre.taille()) {
                return Etiquette.vide();
            }
            if (FORMAT.equals(nom) && longueur >= DECALAGE_DU_DEBIT + 4) {
                octetsParSeconde = fenetre.entierPetitBoutien(contenu + DECALAGE_DU_DEBIT, 4);
            } else if (SON.equals(nom) && octetsParSeconde > 0) {
                return Etiquette.de(
                        OptionalDouble.of((double) longueur / octetsParSeconde), Map.of());
            }
            // Les blocs sont alignés sur un nombre pair d'octets : un bloc de longueur impaire
            // est suivi d'un octet de remplissage, que sauter est le seul moyen de rester en phase.
            position = contenu + longueur + (longueur % 2);
        }
        return Etiquette.vide();
    }
}
