package fr.musique.nom;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Réduction d'un nom d'artiste ou d'un titre d'album à une forme comparable.
 *
 * <p>Le même album s'écrit {@code The Dark Side Of The Moon}, {@code the dark side of the moon} ou
 * {@code The.Dark.Side.Of.The.Moon} selon qui l'a rangé ; {@code Amélie} arrive tantôt en NFC
 * tantôt en NFD selon qu'il est passé par un NAS ou un Mac. La clé supprime ces écarts sans en
 * inventer d'autres.
 *
 * <p>Ce qui est retiré : les accents, la casse, la ponctuation, les espaces en trop. Ce qui est
 * <b>conservé</b> : les nombres du titre et l'ordre des mots. {@code Chapter 1} et
 * {@code Chapter 2} doivent rester deux clés distinctes, faute de quoi l'outil déclarerait
 * doublons deux disques qui ne le sont pas.
 *
 * <p>La clé secondaire retire en plus l'article initial. Elle sert à rapprocher {@code The Wall} de
 * {@code Wall} sans risquer d'y attirer un titre dont le premier mot n'est pas un article. C'est
 * la même précaution que pour les films, et elle vaut ici davantage : les gestionnaires de
 * bibliothèque musicale rangent volontiers {@code The Beatles} sous {@code Beatles, The}.
 */
public final class CleDeTitre {

    private static final Set<String> ARTICLES_INITIAUX = Set.of(
            "le", "la", "les", "l", "un", "une", "des", "du", "de", "the", "a", "an");

    private CleDeTitre() {
        // Fonctions de normalisation seules, pas d'instance.
    }

    /**
     * Normalise un nom : minuscules, sans accents, ponctuation ramenée à des espaces simples.
     *
     * <p>L'apostrophe devient un espace et non rien du tout, pour que {@code L'Aventurier} donne
     * {@code l aventurier} et se rapproche de {@code L Aventurier}, forme qu'emploient les
     * systèmes de fichiers qui refusent l'apostrophe.
     */
    public static String normaliser(String titre) {
        String sansAccents = Normalizer.normalize(titre, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        StringBuilder resultat = new StringBuilder(sansAccents.length());
        for (int i = 0; i < sansAccents.length(); i++) {
            char caractere = sansAccents.charAt(i);
            resultat.append(Character.isLetterOrDigit(caractere) ? caractere : ' ');
        }
        return resultat.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Normalise puis retire l'article initial, s'il y en a un et s'il reste un mot derrière. */
    public static String normaliserSansArticle(String titre) {
        String cle = normaliser(titre);
        int premierEspace = cle.indexOf(' ');
        if (premierEspace < 0) {
            return cle;
        }
        String premierMot = cle.substring(0, premierEspace);
        return ARTICLES_INITIAUX.contains(premierMot) ? cle.substring(premierEspace + 1) : cle;
    }

    /**
     * Remet dans l'ordre un nom rangé à l'envers : {@code Beatles, The} redevient
     * {@code the beatles}.
     *
     * <p>Cette inversion est propre aux bibliothèques musicales, où le classement alphabétique des
     * artistes a longtemps voulu que l'article passe derrière. Deux rangements du même artiste
     * doivent se rejoindre malgré elle.
     */
    public static String normaliserArtiste(String artiste) {
        int virgule = artiste.lastIndexOf(',');
        if (virgule > 0 && virgule < artiste.length() - 1) {
            String queue = normaliser(artiste.substring(virgule + 1));
            if (ARTICLES_INITIAUX.contains(queue)) {
                return normaliser(queue + " " + artiste.substring(0, virgule));
            }
        }
        return normaliser(artiste);
    }
}
