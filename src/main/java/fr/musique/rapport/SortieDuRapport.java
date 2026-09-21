package fr.musique.rapport;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;

/**
 * Où et comment le rapport est écrit.
 *
 * <p>Le flux n'est pas ouvert sur le descripteur de sortie mais posé <b>par-dessus</b> celui qu'on
 * lui donne : les octets encodés ici traversent le flux d'origine sans être relus. C'est ce qui
 * permet de choisir le jeu de caractères sans perdre la possibilité de détourner la sortie, dont
 * les tests ont besoin.
 */
public final class SortieDuRapport {

    /**
     * Propriétés qui disent comment la sortie standard est encodée, par autorité décroissante.
     *
     * <p>Ce sont celles que la machine virtuelle consulte elle-même pour {@code System.out} :
     * {@code stdout.encoding} depuis Java 18, son ancien nom auparavant, et à défaut le jeu par
     * défaut — c'est-à-dire {@code file.encoding} quand l'utilisateur l'a imposé. Suivre le même
     * ordre garantit qu'on écrit dans l'encodage que le terminal attend vraiment, au lieu de le
     * supposer.
     */
    private static final List<String> PROPRIETES_D_ENCODAGE =
            List.of("stdout.encoding", "sun.stdout.encoding");

    private SortieDuRapport() {
        // Plomberie seule, pas d'instance.
    }

    /** Jeu de caractères dans lequel la sortie standard est réellement rendue. */
    public static Charset jeuDeLaSortieStandard() {
        for (String propriete : PROPRIETES_D_ENCODAGE) {
            Charset jeu = lire(System.getProperty(propriete));
            if (jeu != null) {
                return jeu;
            }
        }
        return Charset.defaultCharset();
    }

    private static Charset lire(String nom) {
        if (nom == null || nom.isBlank()) {
            return null;
        }
        try {
            return Charset.forName(nom);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException inconnu) {
            return null;
        }
    }

    /**
     * Enveloppe un flux pour y écrire dans ce jeu de caractères.
     *
     * <p>Sans vidage automatique : un rapport fait des milliers de lignes, et vider à chacune
     * coûte un appel système par ligne sur un terminal lent. L'appelant vide à la fin.
     */
    public static PrintStream envelopper(OutputStream flux, Charset jeu) {
        return new PrintStream(flux, false, jeu);
    }
}
