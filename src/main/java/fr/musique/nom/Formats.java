package fr.musique.nom;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Ce que vaut un format audio, et ce qu'il promet.
 *
 * <h2>La différence avec les films, et elle est à l'avantage de la musique</h2>
 * La résolution d'un film est <b>annoncée</b> dans son nom, que rien n'oblige à dire vrai. Le
 * format d'une piste, lui, est porté par son <b>extension</b> : un fichier {@code .flac} est un
 * flac, il n'y a pas à le croire sur parole. Le premier critère de qualité devient donc une
 * donnée et non une déclaration.
 *
 * <p>Ce que l'extension ne dit pas, en revanche, c'est le <b>débit</b> : un {@code .mp3} peut
 * porter du 320 kbit/s comme du 96, et deux dossiers du même album ne se départagent souvent que
 * là-dessus. Le débit est donc cherché dans le nom du dossier — {@code [320]}, {@code V0} — et,
 * si l'on va ouvrir les fichiers, mesuré pour de bon.
 *
 * <p>Reste un mensonge que ni l'extension ni le nom ne trahissent : le flac fabriqué à partir d'un
 * mp3. Il porte l'extension d'un format sans perte et le poids qui va avec, sans en avoir le
 * contenu. Seul son débit, anormalement bas pour du sans perte, le signale — c'est ce que mesure
 * {@link #debitPlancher}.
 */
public final class Formats {

    /** Rang d'un fichier dont on ne reconnaît pas le format : on ne sait pas, donc en bas. */
    public static final int RANG_INCONNU = 0;

    /** Rang d'un format qui perd de l'information à l'encodage. */
    public static final int RANG_AVEC_PERTE = 1;

    /** Rang d'un format qui restitue la source intacte. */
    public static final int RANG_SANS_PERTE = 2;

    /**
     * Ce qu'un format dit d'un fichier.
     *
     * @param rang          plus il est haut, mieux c'est ; sert à choisir l'exemplaire à garder
     * @param debitPlancher en deçà, en octets par seconde, le fichier ne peut pas contenir ce que
     *                      son format promet
     */
    private record Palier(int rang, long debitPlancher) {}

    /**
     * Formats reconnus.
     *
     * <p>Les débits planchers sont bas à dessein : ils ne jugent pas de la qualité d'un encodage,
     * ils n'attrapent que l'invraisemblable — un flac à 150 kbit/s n'est pas un flac sobre, c'est
     * un mp3 déguisé ou un fichier coupé.
     *
     * <p>{@code m4a} est rangé avec les formats à perte bien qu'il puisse porter de l'ALAC sans
     * perte : l'extension ne tranche pas, et se tromper dans ce sens ne coûte qu'un rang de
     * qualité, là où l'inverse ferait garder un fichier moins bon que son voisin.
     */
    private static final Map<String, Palier> PALIERS = Map.ofEntries(
            // Sans perte compressé : autour de 700 à 1000 kbit/s en musique amplifiée.
            Map.entry("flac", new Palier(RANG_SANS_PERTE, 37_500L)),
            Map.entry("ape", new Palier(RANG_SANS_PERTE, 37_500L)),
            Map.entry("wv", new Palier(RANG_SANS_PERTE, 37_500L)),
            Map.entry("tak", new Palier(RANG_SANS_PERTE, 37_500L)),
            Map.entry("tta", new Palier(RANG_SANS_PERTE, 37_500L)),
            Map.entry("shn", new Palier(RANG_SANS_PERTE, 37_500L)),
            Map.entry("alac", new Palier(RANG_SANS_PERTE, 37_500L)),
            // Sans compression : 1411 kbit/s pour du 16 bits stéréo à 44,1 kHz.
            Map.entry("wav", new Palier(RANG_SANS_PERTE, 50_000L)),
            Map.entry("aiff", new Palier(RANG_SANS_PERTE, 50_000L)),
            Map.entry("aif", new Palier(RANG_SANS_PERTE, 50_000L)),
            Map.entry("aifc", new Palier(RANG_SANS_PERTE, 50_000L)),
            Map.entry("dsf", new Palier(RANG_SANS_PERTE, 50_000L)),
            Map.entry("dff", new Palier(RANG_SANS_PERTE, 50_000L)),
            // Avec perte : 48 kbit/s est déjà une misère, en deçà c'est un accident.
            Map.entry("mp3", new Palier(RANG_AVEC_PERTE, 6_000L)),
            Map.entry("mp2", new Palier(RANG_AVEC_PERTE, 6_000L)),
            Map.entry("m4a", new Palier(RANG_AVEC_PERTE, 6_000L)),
            Map.entry("aac", new Palier(RANG_AVEC_PERTE, 6_000L)),
            Map.entry("ogg", new Palier(RANG_AVEC_PERTE, 6_000L)),
            Map.entry("oga", new Palier(RANG_AVEC_PERTE, 6_000L)),
            Map.entry("wma", new Palier(RANG_AVEC_PERTE, 6_000L)),
            Map.entry("mpc", new Palier(RANG_AVEC_PERTE, 6_000L)),
            // Opus tient la parole à des débits où les autres renoncent : son plancher est plus bas.
            Map.entry("opus", new Palier(RANG_AVEC_PERTE, 3_000L)));

    /** Débits annoncés dans un nom de dossier, en kilobits par seconde. */
    private static final Map<String, Integer> DEBITS_ANNONCES = Map.ofEntries(
            Map.entry("320", 320), Map.entry("256", 256), Map.entry("224", 224),
            Map.entry("192", 192), Map.entry("160", 160), Map.entry("128", 128),
            Map.entry("112", 112), Map.entry("96", 96), Map.entry("64", 64),
            // Les préréglages de LAME, dont le débit varie : on retient leur moyenne annoncée.
            Map.entry("v0", 245), Map.entry("v1", 225), Map.entry("v2", 190));

    private Formats() {
        // Table de correspondance seule, pas d'instance.
    }

    /** Rang de qualité du format, {@link #RANG_INCONNU} pour une extension non reconnue. */
    public static int rang(String extension) {
        Palier palier = palierDe(extension);
        return palier == null ? RANG_INCONNU : palier.rang();
    }

    /** Indique si ce format restitue la source intacte. */
    public static boolean estSansPerte(String extension) {
        return rang(extension) == RANG_SANS_PERTE;
    }

    /**
     * Débit en deçà duquel un fichier ne peut pas contenir ce que son format promet, en octets par
     * seconde. Vide pour un format qu'on ne connaît pas.
     */
    public static OptionalLong debitPlancher(String extension) {
        Palier palier = palierDe(extension);
        return palier == null ? OptionalLong.empty() : OptionalLong.of(palier.debitPlancher());
    }

    /**
     * Débit annoncé par les jetons d'un nom de dossier, en kilobits par seconde.
     *
     * <p>C'est le seul moyen de départager deux dossiers du même album au même format sans ouvrir
     * un seul fichier. Le plus élevé l'emporte : un dossier nommé
     * {@code MP3 320 (V0 pour deux titres)} vaut par ce qu'il annonce de mieux.
     */
    public static OptionalInt debitAnnonce(List<String> jetons) {
        int meilleur = 0;
        for (String jeton : jetons) {
            Integer debit = DEBITS_ANNONCES.get(jeton);
            if (debit != null) {
                meilleur = Math.max(meilleur, debit);
            }
        }
        return meilleur == 0 ? OptionalInt.empty() : OptionalInt.of(meilleur);
    }

    private static Palier palierDe(String extension) {
        return extension == null ? null : PALIERS.get(extension.toLowerCase(Locale.ROOT));
    }
}
