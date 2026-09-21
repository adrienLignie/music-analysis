package fr.musique.rapport;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.Normalizer;
import java.util.Map;

/**
 * Caractères du rapport, ajustés à ce que la sortie sait représenter.
 *
 * <p>Le rapport trace ses séparateurs en {@code ─} et cite les titres d'albums entre {@code « »}. Une
 * console Windows en page de code 850 ou 1252 ne connaît aucun des deux : le rapport s'y affiche
 * criblé de points d'interrogation, et le lecteur croit à un défaut du programme. Plutôt que
 * d'exiger un {@code chcp 65001} de sa part, le rapport regarde ce que sa sortie accepte et se
 * rabat sur l'ASCII quand il le faut.
 *
 * <p>La dégradation est volontairement complète : un rapport à moitié translittéré serait pire
 * que l'un ou l'autre des deux états. Les accents tombent donc avec les tirets de tracé, et
 * {@code « discovery »} devient {@code "discovery"}.
 */
public final class Glyphes {

    /** Les caractères du rapport qui ne sont pas dans l'ASCII : si l'un manque, tous tombent. */
    private static final String CARACTERES_A_TRACER = "─«»…é";

    private static final int LARGEUR_DU_SEPARATEUR = 78;

    /** Ce que devient chaque caractère hors ASCII que la décomposition ne règle pas seule. */
    private static final Map<Character, String> EQUIVALENTS = Map.ofEntries(
            Map.entry('─', "-"), Map.entry('—', "-"), Map.entry('–', "-"),
            Map.entry('«', "\""), Map.entry('»', "\""), Map.entry('’', "'"),
            Map.entry('…', "..."), Map.entry('°', "o"),
            Map.entry('œ', "oe"), Map.entry('Œ', "OE"),
            Map.entry('æ', "ae"), Map.entry('Æ', "AE"));

    private final boolean ascii;

    private Glyphes(boolean ascii) {
        this.ascii = ascii;
    }

    /** Rapport tracé au complet. */
    public static Glyphes completes() {
        return new Glyphes(false);
    }

    /** Rapport réduit à l'ASCII, accents compris. */
    public static Glyphes ascii() {
        return new Glyphes(true);
    }

    /**
     * Glyphes adaptés à ce jeu de caractères.
     *
     * @param forcerAscii vrai quand l'utilisateur a demandé l'ASCII sans qu'on ait à deviner
     */
    public static Glyphes pour(Charset jeu, boolean forcerAscii) {
        return forcerAscii || !saitEcrire(jeu, CARACTERES_A_TRACER) ? ascii() : completes();
    }

    private static boolean saitEcrire(Charset jeu, String caracteres) {
        if (!jeu.canEncode()) {
            return false;
        }
        CharsetEncoder encodeur = jeu.newEncoder();
        return encodeur.canEncode(caracteres);
    }

    /** Indique si le rapport est écrit en ASCII. */
    public boolean estAscii() {
        return ascii;
    }

    /** Filet horizontal des sections. */
    public String separateur() {
        return (ascii ? "-" : "─").repeat(LARGEUR_DU_SEPARATEUR);
    }

    /** Adapte une ligne du rapport à ce que la sortie sait représenter. */
    public String adapter(String texte) {
        if (!ascii) {
            return texte;
        }
        String decompose = Normalizer.normalize(resserrer(texte), Normalizer.Form.NFD);
        StringBuilder resultat = new StringBuilder(decompose.length());
        for (int i = 0; i < decompose.length(); i++) {
            char caractere = decompose.charAt(i);
            if (caractere < 128) {
                resultat.append(caractere);
            } else if (EQUIVALENTS.containsKey(caractere)) {
                resultat.append(EQUIVALENTS.get(caractere));
            } else if (!estUneMarqueDAccent(caractere)) {
                // Un caractère qu'on ne sait pas rendre : mieux vaut un point d'interrogation
                // qu'un chemin tronqué, car ces lignes servent à retrouver des fichiers.
                resultat.append('?');
            }
        }
        return resultat.toString();
    }

    /**
     * Rapproche du texte ce que la typographie française tient à distance.
     *
     * <p>Les guillemets français prennent une espace à l'intérieur, les guillemets droits n'en
     * veulent pas : {@code « Amélie »} doit devenir {@code "Amelie"} et non {@code " Amelie "},
     * qui se lirait comme une citation mal fermée.
     */
    private static String resserrer(String texte) {
        return texte.replace(" ", " ").replace("« ", "«").replace(" »", "»");
    }

    /** Vrai pour les accents détachés par la décomposition, qui disparaissent simplement. */
    private static boolean estUneMarqueDAccent(char caractere) {
        return Character.UnicodeBlock.of(caractere)
                == Character.UnicodeBlock.COMBINING_DIACRITICAL_MARKS;
    }
}
