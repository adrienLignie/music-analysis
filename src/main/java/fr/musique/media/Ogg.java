package fr.musique.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Durée et étiquettes d'un flux Ogg, qu'il porte du Vorbis ou de l'Opus.
 *
 * <h2>Un format qui n'a pas de table des matières</h2>
 * Un Ogg est une suite de pages, chacune marquée {@code OggS}, et rien au début du fichier ne dit
 * combien il y en a ni combien de temps dure le tout. La durée se trouve donc <b>à la fin</b> :
 * la dernière page porte la position du dernier échantillon, qu'il suffit de diviser par la
 * fréquence pour obtenir des secondes.
 *
 * <p>Deux lectures suffisent : les premiers kilo-octets, qui portent l'identification et les
 * étiquettes, et les derniers, où l'on cherche la dernière page à rebours. Un fichier de cent
 * mégaoctets n'est donc jamais relu en entier.
 *
 * <p>L'Opus compte toujours ses échantillons à quarante-huit mille par seconde, quelle que soit la
 * fréquence réelle de l'enregistrement : c'est une règle du format, et l'ignorer donnerait des
 * durées fausses d'un facteur deux sur la moitié des fichiers.
 */
final class Ogg {

    private static final byte[] PAGE = {'O', 'g', 'g', 'S'};
    private static final byte[] ENTETE_OPUS = "OpusHead".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ETIQUETTES_OPUS = "OpusTags".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ENTETE_VORBIS = {1, 'v', 'o', 'r', 'b', 'i', 's'};
    private static final byte[] ETIQUETTES_VORBIS = {3, 'v', 'o', 'r', 'b', 'i', 's'};

    /** Fréquence à laquelle l'Opus compte ses échantillons, quoi qu'il encode. */
    private static final int FREQUENCE_DE_L_OPUS = 48_000;

    /** Fenêtre de recherche en tête : l'identification et les étiquettes y tiennent toujours. */
    private static final int OCTETS_EN_TETE = 64 * 1024;

    /** Fenêtre de recherche en queue, où se trouve la dernière page. */
    private static final int OCTETS_EN_QUEUE = 64 * 1024;

    /** Position, dans l'en-tête d'une page, du compteur d'échantillons. */
    private static final int DECALAGE_DU_COMPTEUR = 6;

    private Ogg() {
        // Lecture seule, pas d'instance.
    }

    /** Étiquette lue dans le flux, vide quand ce n'est pas un Ogg ou qu'il ne dit rien. */
    static Etiquette lire(Fenetre fenetre) throws IOException {
        if (fenetre.taille() < 32 || !"OggS".equals(fenetre.etiquette(0))) {
            return Etiquette.vide();
        }
        long opus = fenetre.chercher(ENTETE_OPUS, 0, OCTETS_EN_TETE, false);
        int frequence = opus >= 0
                ? FREQUENCE_DE_L_OPUS
                : frequenceDuVorbis(fenetre);
        return Etiquette.de(duree(fenetre, frequence), lireLesChamps(fenetre, opus >= 0));
    }

    /**
     * Fréquence annoncée par l'identification d'un flux Vorbis.
     *
     * <p>Elle se trouve huit octets après la signature du paquet : version sur quatre octets,
     * nombre de canaux sur un, puis la fréquence, écrite du plus faible au plus fort.
     */
    private static int frequenceDuVorbis(Fenetre fenetre) throws IOException {
        long entete = fenetre.chercher(ENTETE_VORBIS, 0, OCTETS_EN_TETE, false);
        if (entete < 0 || entete + 16 > fenetre.taille()) {
            return 0;
        }
        return (int) fenetre.entierPetitBoutien(entete + 12, 4);
    }

    /**
     * Durée du flux, tirée du compteur d'échantillons de sa dernière page.
     *
     * <p>La recherche part de la fin : la dernière page est celle dont la signature apparaît le
     * plus loin. Un flux dont la dernière page annonce un compteur nul ou négatif — un fichier
     * coupé en cours d'écriture — ne rend pas de durée plutôt qu'une durée fausse.
     */
    private static OptionalDouble duree(Fenetre fenetre, int frequence) throws IOException {
        if (frequence <= 0) {
            return OptionalDouble.empty();
        }
        long debut = Math.max(0, fenetre.taille() - OCTETS_EN_QUEUE);
        long page = fenetre.chercher(PAGE, debut, OCTETS_EN_QUEUE, true);
        if (page < 0 || page + DECALAGE_DU_COMPTEUR + 8 > fenetre.taille()) {
            return OptionalDouble.empty();
        }
        long echantillons = fenetre.entierPetitBoutien(page + DECALAGE_DU_COMPTEUR, 8);
        return echantillons <= 0
                ? OptionalDouble.empty()
                : OptionalDouble.of((double) echantillons / frequence);
    }

    /**
     * Lit le paquet d'étiquettes, écrit dans le dialecte de Vorbis quel que soit le codec.
     *
     * <p>Une réserve : le paquet peut déborder d'une page sur la suivante, et les quelques octets
     * d'en-tête de page qui s'intercalent alors interrompent la lecture au milieu d'une étiquette.
     * On récupère ce qui précède plutôt que de tout perdre — l'artiste et l'album viennent en
     * tête, la parole longue est celle des paroles de la chanson.
     */
    private static Map<String, String> lireLesChamps(Fenetre fenetre, boolean estOpus)
            throws IOException {
        byte[] signature = estOpus ? ETIQUETTES_OPUS : ETIQUETTES_VORBIS;
        long paquet = fenetre.chercher(signature, 0, OCTETS_EN_TETE, false);
        if (paquet < 0) {
            return new HashMap<>();
        }
        long debut = paquet + signature.length;
        long fin = Math.min(fenetre.taille(), debut + OCTETS_EN_TETE);
        return CommentairesVorbis.lire(fenetre, debut, fin);
    }
}
