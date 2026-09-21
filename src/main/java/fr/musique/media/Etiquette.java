package fr.musique.media;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Ce qu'un fichier audio dit de lui-même, par opposition à ce que son chemin en laisse deviner.
 *
 * <h2>La seule information qui ne vienne pas d'un nom de dossier</h2>
 * Tout le reste du programme raisonne sur des conventions de rangement, qui varient d'une
 * bibliothèque à l'autre et parfois d'un dossier à l'autre. Les étiquettes, elles, voyagent avec
 * le fichier : elles survivent à un renommage, à une copie, à un passage par un lecteur qui range
 * autrement. Deux dossiers dont les noms ne se ressemblent pas mais dont les étiquettes portent le
 * même artiste et le même album sont le même album.
 *
 * <p>Elles ne sont pas infaillibles pour autant : une étiquette vide est fréquente, une étiquette
 * fausse existe. C'est pourquoi elles ne défont jamais un rapprochement à elles seules — elles le
 * confirment ou le nuancent.
 *
 * @param secondes       durée réelle, d'où se déduit le débit et donc la qualité effective
 * @param artiste        artiste de la piste, ou l'artiste de l'album quand il est renseigné
 * @param album          titre de l'album tel que le fichier le porte
 * @param titre          titre de la piste
 * @param numero         numéro de piste, qui dit les trous mieux qu'un nom de fichier
 * @param annee          année de parution
 * @param empreinteAudio empreinte du <b>son</b> seul, quand le format en porte une ; voir
 *                       {@link #empreinteAudio}
 */
public record Etiquette(
        OptionalDouble secondes,
        Optional<String> artiste,
        Optional<String> album,
        Optional<String> titre,
        OptionalInt numero,
        OptionalInt annee,
        Optional<String> empreinteAudio) {

    private static final Etiquette VIDE = new Etiquette(
            OptionalDouble.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            OptionalInt.empty(), OptionalInt.empty(), Optional.empty());

    private static final int ANNEE_MINIMUM = 1900;
    private static final int ANNEE_MAXIMUM = 2200;

    /**
     * Étiquette dont le format ne porte aucune empreinte du son.
     *
     * <p>C'est le cas de la plupart d'entre eux : seuls le flac et le mp3 encodé par LAME écrivent
     * de quoi reconnaître leur son sans le décoder.
     */
    public Etiquette(
            OptionalDouble secondes,
            Optional<String> artiste,
            Optional<String> album,
            Optional<String> titre,
            OptionalInt numero,
            OptionalInt annee) {
        this(secondes, artiste, album, titre, numero, annee, Optional.empty());
    }

    /** Étiquette qui ne dit rien : le fichier n'a pas été sondé, ou n'a rien livré. */
    public static Etiquette vide() {
        return VIDE;
    }

    /**
     * Étiquette bâtie sur une durée et un jeu de champs bruts.
     *
     * <p>Les clés sont normalisées par les lecteurs, qui parlent chacun un dialecte différent —
     * {@code TPE1} pour un mp3, {@code ARTIST} pour un flac, {@code ©ART} pour un m4a. Les trois
     * arrivent ici sous le même nom, et c'est le seul endroit où le sens des champs est fixé.
     */
    public static Etiquette de(OptionalDouble secondes, Map<String, String> champs) {
        return de(secondes, champs, Optional.empty());
    }

    /**
     * Étiquette bâtie sur une durée, des champs bruts et une empreinte du son.
     *
     * <h2>Ce que l'empreinte vaut, et pourquoi elle ne coûte rien</h2>
     * C'est la seule chose que le programme sache dire du <b>contenu</b> sans en décoder une
     * seconde. Un flac écrit dans son premier bloc l'empreinte MD5 du signal non compressé : deux
     * flac qui la partagent portent exactement le même son, quels que soient leurs étiquettes,
     * leur niveau de compression ou leur découpage en blocs. Elle tient dans les trente-quatre
     * premiers octets du fichier, à côté de la durée qu'on y lisait déjà.
     *
     * <p>Un mp3 encodé par LAME porte, dans la même table de trames qui donne sa durée, un
     * contrôle du flux audio et sa longueur exacte en octets. Plus court qu'un MD5, donc moins
     * décisif, mais assez pour reconnaître un fichier qu'on a seulement réétiqueté — le cas le
     * plus fréquent du doublon exact.
     *
     * <h2>Ce qu'elle ne dit pas</h2>
     * Deux empreintes <b>différentes</b> ne concluent rien. Un flac et un mp3 du même album n'en
     * partagent évidemment aucune, et deux extractions du même disque à des décalages différents
     * non plus. L'empreinte ne sert donc qu'à <b>élever</b> une certitude, jamais à défaire un
     * rapprochement : c'est la règle de tout ce que les fichiers disent d'eux-mêmes ici.
     *
     * <p>Le préfixe dit de quelle sorte elle est, pour qu'un MD5 de flac ne soit jamais comparé au
     * contrôle d'un mp3.
     */
    public static Etiquette de(
            OptionalDouble secondes, Map<String, String> champs, Optional<String> empreinteAudio) {
        return new Etiquette(
                secondes,
                premier(champs, "artist", "albumartist", "album_artist", "performer"),
                premier(champs, "album"),
                premier(champs, "title"),
                nombre(premier(champs, "tracknumber", "track"), 1, 999),
                nombre(premier(champs, "date", "year", "originaldate"),
                        ANNEE_MINIMUM, ANNEE_MAXIMUM),
                empreinteAudio);
    }

    /** Indique que rien n'a été lu dans ce fichier. */
    public boolean estVide() {
        return equals(VIDE);
    }

    private static Optional<String> premier(Map<String, String> champs, String... noms) {
        for (String nom : noms) {
            String valeur = champs.get(nom);
            if (valeur != null && !valeur.isBlank()) {
                return Optional.of(valeur.trim());
            }
        }
        return Optional.empty();
    }

    /**
     * Lit un nombre au début d'un champ, dans les bornes données.
     *
     * <p>Le début seulement : un numéro de piste s'écrit {@code 3}, {@code 03} ou {@code 3/12}, et
     * une date de parution {@code 1973} ou {@code 1973-03-01}. Prendre ce qui précède le premier
     * séparateur répond aux six formes d'un coup.
     */
    private static OptionalInt nombre(Optional<String> champ, int minimum, int maximum) {
        if (champ.isEmpty()) {
            return OptionalInt.empty();
        }
        String texte = champ.get().trim().toLowerCase(Locale.ROOT);
        int fin = 0;
        while (fin < texte.length() && Character.isDigit(texte.charAt(fin))) {
            fin++;
        }
        if (fin == 0 || fin > 9) {
            return OptionalInt.empty();
        }
        int valeur = Integer.parseInt(texte.substring(0, fin));
        return valeur >= minimum && valeur <= maximum
                ? OptionalInt.of(valeur)
                : OptionalInt.empty();
    }
}
