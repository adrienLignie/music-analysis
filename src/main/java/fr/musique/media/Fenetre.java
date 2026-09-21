package fr.musique.media;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;

/**
 * Lecture d'octets à une position donnée d'un fichier.
 *
 * <p>Les conteneurs audio lus par le programme sont faits de blocs qui annoncent leur longueur :
 * on n'en lit jamais le son, seulement les quelques octets de chaque en-tête, en sautant d'un
 * bloc au suivant. Un album de quatre cents mégaoctets se laisse ainsi interroger en une dizaine de
 * lectures de seize octets.
 */
final class Fenetre {

    private final SeekableByteChannel canal;
    private final long taille;

    Fenetre(SeekableByteChannel canal) throws IOException {
        this.canal = canal;
        this.taille = canal.size();
    }

    /** Longueur du fichier. */
    long taille() {
        return taille;
    }

    /**
     * Lit exactement {@code longueur} octets à partir de {@code position}.
     *
     * @throws EOFException si le fichier s'arrête avant, ce qui trahit un fichier tronqué
     */
    ByteBuffer lire(long position, int longueur) throws IOException {
        if (position < 0 || longueur < 0 || position + longueur > taille) {
            throw new EOFException("Lecture au-delà de la fin : " + position + " + " + longueur);
        }
        ByteBuffer tampon = ByteBuffer.allocate(longueur).order(ByteOrder.BIG_ENDIAN);
        canal.position(position);
        while (tampon.hasRemaining()) {
            if (canal.read(tampon) < 0) {
                throw new EOFException("Fichier plus court qu'annoncé à la position " + position);
            }
        }
        return tampon.flip();
    }

    /** Lit un entier non signé de {@code longueur} octets, du plus fort au plus faible. */
    long entierGrosBoutien(long position, int longueur) throws IOException {
        ByteBuffer octets = lire(position, longueur);
        long valeur = 0;
        while (octets.hasRemaining()) {
            valeur = (valeur << 8) | (octets.get() & 0xFFL);
        }
        return valeur;
    }

    /** Lit un entier non signé de quatre octets, du plus faible au plus fort. */
    long entierPetitBoutien(long position) throws IOException {
        return lire(position, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFF_FFFFL;
    }

    /** Lit un entier non signé de {@code longueur} octets, du plus faible au plus fort. */
    long entierPetitBoutien(long position, int longueur) throws IOException {
        ByteBuffer octets = lire(position, longueur);
        long valeur = 0;
        for (int rang = 0; rang < longueur; rang++) {
            valeur |= (octets.get() & 0xFFL) << (8 * rang);
        }
        return valeur;
    }

    /** Lit quatre octets comme une étiquette ASCII : {@code moov}, {@code fLaC}, {@code OggS}. */
    String etiquette(long position) throws IOException {
        return etiquette(position, 4);
    }

    /** Lit {@code longueur} octets comme une étiquette ASCII : {@code OpusTags}, {@code TPE1}. */
    String etiquette(long position, int longueur) throws IOException {
        ByteBuffer octets = lire(position, longueur);
        StringBuilder etiquette = new StringBuilder(longueur);
        while (octets.hasRemaining()) {
            etiquette.append((char) (octets.get() & 0xFF));
        }
        return etiquette.toString();
    }

    /**
     * Cherche une suite d'octets dans une portion du fichier, et rend sa position.
     *
     * <p>Sert aux formats qui ne disent pas où sont leurs blocs : un flux Ogg ne porte aucune
     * table des matières, et sa dernière page ne se trouve qu'en la cherchant depuis la fin. La
     * fenêtre de recherche est bornée par l'appelant, pour qu'un fichier de trois cents mégaoctets
     * ne soit jamais relu en entier.
     *
     * @return la position absolue du motif, ou {@code -1} s'il n'est pas dans la fenêtre
     */
    long chercher(byte[] motif, long debut, int longueur, boolean depuisLaFin) throws IOException {
        if (motif.length == 0 || debut < 0 || debut >= taille) {
            return -1;
        }
        int aLire = (int) Math.min(longueur, taille - debut);
        if (aLire < motif.length) {
            return -1;
        }
        ByteBuffer tampon = lire(debut, aLire);
        byte[] octets = new byte[aLire];
        tampon.get(octets);
        int dernier = aLire - motif.length;
        for (int decalage = 0; decalage <= dernier; decalage++) {
            int position = depuisLaFin ? dernier - decalage : decalage;
            if (correspond(octets, position, motif)) {
                return debut + position;
            }
        }
        return -1;
    }

    private static boolean correspond(byte[] octets, int position, byte[] motif) {
        for (int i = 0; i < motif.length; i++) {
            if (octets[position + i] != motif[i]) {
                return false;
            }
        }
        return true;
    }
}
