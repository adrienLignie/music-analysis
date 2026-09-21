package fr.musique.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Durée et étiquettes d'un fichier flac, lues dans ses blocs d'en-tête.
 *
 * <p>Le format est fait pour cela : après les quatre lettres {@code fLaC}, une suite de blocs qui
 * annoncent chacun leur type et leur longueur. Le premier, obligatoire, porte la fréquence
 * d'échantillonnage et le nombre total d'échantillons — d'où la durée se déduit exactement, sans
 * aucune estimation. Un autre porte les étiquettes.
 *
 * <p>Le son, lui, n'est jamais touché : on saute de bloc en bloc jusqu'à celui qui est annoncé
 * comme le dernier, ce qui représente deux ou trois lectures de quelques octets sur un fichier de
 * trente mégaoctets.
 */
final class Flac {

    private static final String SIGNATURE = "fLaC";

    /** Bloc qui porte la fréquence d'échantillonnage et le nombre d'échantillons. */
    private static final int STREAMINFO = 0;

    /** Bloc qui porte les étiquettes, au dialecte de Vorbis. */
    private static final int COMMENTAIRES = 4;

    /** Un flac en porte une poignée ; au-delà, le fichier est malformé. */
    private static final int BLOCS_EXAMINES_AU_PLUS = 32;

    /** Position, dans le bloc, des huit octets qui portent fréquence et nombre d'échantillons. */
    private static final int DECALAGE_DES_ECHANTILLONS = 10;

    /**
     * Position, dans le bloc, de l'empreinte MD5 du signal non compressé.
     *
     * <p>Elle suit immédiatement les huit octets de la fréquence et des échantillons, et clôt le
     * bloc : trente-quatre octets en tout. C'est la seule preuve de contenu que ce programme
     * obtienne sans décoder une seconde de son.
     */
    private static final int DECALAGE_DE_L_EMPREINTE = 18;

    /** Longueur de l'empreinte MD5, en octets. */
    private static final int LONGUEUR_DE_L_EMPREINTE = 16;

    /** Sorte de l'empreinte, pour qu'un MD5 de flac ne soit jamais comparé à celle d'un mp3. */
    private static final String SORTE_D_EMPREINTE = "flac-md5:";

    private Flac() {
        // Lecture seule, pas d'instance.
    }

    /** Étiquette lue dans l'en-tête, vide quand le fichier n'est pas un flac. */
    static Etiquette lire(Fenetre fenetre) throws IOException {
        if (fenetre.taille() < 8 || !SIGNATURE.equals(fenetre.etiquette(0))) {
            return Etiquette.vide();
        }
        OptionalDouble duree = OptionalDouble.empty();
        Optional<String> empreinte = Optional.empty();
        Map<String, String> champs = new HashMap<>();
        long position = SIGNATURE.length();
        for (int blocs = 0; blocs < BLOCS_EXAMINES_AU_PLUS && position + 4 <= fenetre.taille();
                blocs++) {
            long entete = fenetre.entierGrosBoutien(position, 4);
            boolean dernier = (entete & 0x8000_0000L) != 0;
            int type = (int) ((entete >>> 24) & 0x7F);
            long longueur = entete & 0xFF_FFFFL;
            long contenu = position + 4;
            if (contenu + longueur > fenetre.taille()) {
                break;
            }
            if (type == STREAMINFO && longueur >= DECALAGE_DES_ECHANTILLONS + 8) {
                duree = lireLaDuree(fenetre, contenu);
                if (longueur >= DECALAGE_DE_L_EMPREINTE + LONGUEUR_DE_L_EMPREINTE) {
                    empreinte = lireLEmpreinte(fenetre, contenu);
                }
            } else if (type == COMMENTAIRES) {
                champs.putAll(CommentairesVorbis.lire(fenetre, contenu, contenu + longueur));
            }
            if (dernier) {
                break;
            }
            position = contenu + longueur;
        }
        return Etiquette.de(duree, champs, empreinte);
    }

    /**
     * Lit l'empreinte MD5 du signal non compressé.
     *
     * <p>Une empreinte entièrement nulle n'est pas une empreinte : c'est ce qu'écrit un encodeur
     * qui n'a pas voulu la calculer, ou qui produisait un flux sans en connaître la fin. La
     * confondre avec une vraie valeur ferait tenir pour identiques tous les fichiers de cette
     * sorte — l'exact contraire de ce qu'on lui demande.
     */
    private static Optional<String> lireLEmpreinte(Fenetre fenetre, long contenu)
            throws IOException {
        ByteBuffer octets =
                fenetre.lire(contenu + DECALAGE_DE_L_EMPREINTE, LONGUEUR_DE_L_EMPREINTE);
        StringBuilder empreinte = new StringBuilder(LONGUEUR_DE_L_EMPREINTE * 2);
        boolean toutNul = true;
        while (octets.hasRemaining()) {
            int octet = octets.get() & 0xFF;
            toutNul &= octet == 0;
            empreinte.append(Character.forDigit(octet >>> 4, 16))
                    .append(Character.forDigit(octet & 0xF, 16));
        }
        return toutNul ? Optional.empty() : Optional.of(SORTE_D_EMPREINTE + empreinte);
    }

    /**
     * Lit la fréquence et le nombre d'échantillons, qui partagent huit octets.
     *
     * <p>Vingt bits pour la fréquence, trois pour le nombre de canaux, cinq pour la profondeur,
     * trente-six pour le total des échantillons : le flac ne gaspille pas un bit. Un total à zéro
     * n'est pas une durée nulle mais une durée inconnue, ce qu'écrit un encodeur qui produit un
     * flux sans en connaître la fin.
     */
    private static OptionalDouble lireLaDuree(Fenetre fenetre, long contenu) throws IOException {
        long empile = fenetre.entierGrosBoutien(contenu + DECALAGE_DES_ECHANTILLONS, 8);
        long frequence = (empile >>> 44) & 0xF_FFFFL;
        long echantillons = empile & 0xF_FFFF_FFFFL;
        return frequence <= 0 || echantillons <= 0
                ? OptionalDouble.empty()
                : OptionalDouble.of((double) echantillons / frequence);
    }
}
