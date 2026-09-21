package fr.musique.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lecture d'un bloc de commentaires Vorbis, le dialecte d'étiquettes que partagent le flac, l'ogg
 * et l'opus.
 *
 * <p>Sa forme est d'une simplicité rare dans le monde des formats audio : une longueur sur quatre
 * octets, un nom de logiciel, un nombre d'entrées, puis autant de chaînes {@code CLÉ=valeur} en
 * UTF-8, chacune précédée de sa longueur. Les entiers sont écrits du plus faible au plus fort,
 * hérités du monde qui a vu naître Vorbis.
 *
 * <p>Toutes les bornes sont vérifiées et plafonnées. Un fichier mal formé — ou fabriqué pour
 * l'être — annoncerait volontiers quatre milliards d'entrées de quatre gigaoctets chacune ; il
 * n'obtiendra ici qu'une lecture écourtée.
 */
final class CommentairesVorbis {

    /** Au-delà, ce n'est plus un jeu d'étiquettes mais un fichier malformé ou hostile. */
    private static final int ENTREES_AU_PLUS = 128;

    /** Une étiquette de plus de quelques kilo-octets ne porte pas un titre mais une pochette. */
    private static final int LONGUEUR_D_UNE_ENTREE_AU_PLUS = 8 * 1024;

    private CommentairesVorbis() {
        // Lecture seule, pas d'instance.
    }

    /**
     * Lit les champs d'un bloc de commentaires.
     *
     * @param fenetre fichier ouvert
     * @param debut   position du premier octet du bloc
     * @param fin     position à ne pas dépasser
     */
    static Map<String, String> lire(Fenetre fenetre, long debut, long fin) throws IOException {
        Map<String, String> champs = new HashMap<>();
        if (debut + 8 > fin) {
            return champs;
        }
        long position = debut + 4 + fenetre.entierPetitBoutien(debut, 4);
        if (position + 4 > fin) {
            return champs;
        }
        long entrees = Math.min(fenetre.entierPetitBoutien(position, 4), ENTREES_AU_PLUS);
        position += 4;
        for (long entree = 0; entree < entrees && position + 4 <= fin; entree++) {
            long longueur = fenetre.entierPetitBoutien(position, 4);
            position += 4;
            if (longueur <= 0 || position + longueur > fin) {
                return champs;
            }
            if (longueur <= LONGUEUR_D_UNE_ENTREE_AU_PLUS) {
                ajouter(champs, texte(fenetre, position, (int) longueur));
            }
            position += longueur;
        }
        return champs;
    }

    /** Range une entrée {@code CLÉ=valeur}, la première occurrence l'emportant sur les suivantes. */
    static void ajouter(Map<String, String> champs, String entree) {
        int egal = entree.indexOf('=');
        if (egal <= 0 || egal == entree.length() - 1) {
            return;
        }
        champs.putIfAbsent(
                entree.substring(0, egal).trim().toLowerCase(Locale.ROOT),
                entree.substring(egal + 1));
    }

    private static String texte(Fenetre fenetre, long position, int longueur) throws IOException {
        ByteBuffer octets = fenetre.lire(position, longueur);
        byte[] contenu = new byte[longueur];
        octets.get(contenu);
        return new String(contenu, StandardCharsets.UTF_8);
    }
}
