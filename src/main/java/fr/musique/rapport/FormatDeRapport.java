package fr.musique.rapport;

import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Formes sous lesquelles le rapport peut sortir.
 *
 * <p>{@link #TEXTE} est fait pour être lu, les deux autres pour être repris par un script. C'est
 * la contrepartie du refus d'écrire dans l'arborescence : le programme ne supprimera jamais rien,
 * mais il rend son résultat sous une forme où la décision et le geste appartiennent entièrement à
 * qui les prend.
 */
public enum FormatDeRapport {

    /** Rapport lisible, adapté à ce que le terminal sait afficher. */
    TEXTE,

    /** Document complet, poids en octets, pour un filtre ou un script. */
    JSON,

    /** Une ligne par dossier, pour un tableur ou un {@code awk}. */
    CSV;

    /** Construit le rapport correspondant. */
    public Rapport creer(PrintStream sortie, Glyphes glyphes) {
        return switch (this) {
            case TEXTE -> new RapportConsole(sortie, glyphes);
            case JSON -> new RapportJson(sortie);
            case CSV -> new RapportCsv(sortie);
        };
    }

    /**
     * Jeu de caractères dans lequel écrire cette forme.
     *
     * <p>Les formes machine sont toujours en UTF-8 : elles sont faites pour être relues par un
     * programme, souvent après redirection, et un JSON dans la page de code d'une console
     * Windows serait illisible pour l'outil qui le reçoit. La forme lisible suit le terminal.
     */
    public Charset jeuDeCaracteres() {
        return this == TEXTE ? SortieDuRapport.jeuDeLaSortieStandard() : StandardCharsets.UTF_8;
    }
}
