package fr.musique.nom;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Vocabulaire des jetons qui ne font pas partie d'un titre d'album.
 *
 * <p>Les dossiers d'une bibliothèque musicale accolent au titre une longue liste de
 * renseignements : format, débit, profondeur et fréquence d'échantillonnage, source, équipe de
 * rip. Ce vocabulaire sert à repérer où s'arrête le titre. La comparaison est faite en minuscules
 * et sans accents.
 *
 * <h2>Trois natures de mentions, et non une seule</h2>
 * <ul>
 *   <li>Les jetons <b>techniques</b> ferment le titre et sont jetés : {@code FLAC}, {@code 320},
 *       {@code 24bit}, {@code WEB}.</li>
 *   <li>Les mentions d'<b>édition</b> ferment aussi le titre, mais sont conservées : une édition
 *       de luxe contient l'édition simple, et c'est elle qu'il faut garder.</li>
 *   <li>Les mentions de <b>version</b> ne désignent pas la même œuvre : {@code Unplugged},
 *       {@code Live}, {@code Demos}, {@code Remixes}. Elles <b>séparent</b> deux dossiers que leur
 *       titre rapprochait. C'est la différence la plus coûteuse à manquer : supprimer le
 *       {@code MTV Unplugged} d'un groupe parce qu'on possède l'album studio du même nom détruit
 *       un disque qui n'existe nulle part ailleurs.</li>
 * </ul>
 *
 * <p>Le vocabulaire n'est pas exhaustif et n'a pas à l'être : il lui suffit de reconnaître le
 * <b>premier</b> jeton technique d'un nom, puisque tout ce qui suit est abandonné.
 */
public final class MotsTechniques {

    /** Jetons dont l'apparition marque la fin du titre. */
    private static final Set<String> COUPURE = Set.of(
            // Formats et codecs
            "flac", "mp3", "m4a", "aac", "alac", "ogg", "oga", "opus", "wav", "wave", "aiff",
            "aif", "ape", "wv", "wavpack", "mpc", "musepack", "wma", "dsd", "dsf", "dff", "tta",
            "tak", "shn", "lossless", "lossy",
            // Débits et résolutions audio
            "kbps", "kbit", "kbs", "cbr", "vbr", "abr", "lame", "v0", "v2", "aps", "apx",
            "khz", "hz", "bit", "bits", "16bit", "24bit", "32bit", "44", "48", "88", "96", "176",
            "192", "320", "256", "128", "hires", "hi", "res",
            // Sources
            "cd", "cda", "cdrip", "cdda", "eac", "web", "webrip", "webflac", "vinyl", "vinyle",
            "lp", "sacd", "dvda", "dvd", "hdtracks", "qobuz", "tidal", "deezer", "spotify",
            "itunes", "bandcamp", "promo", "bootleg", "rip", "retail", "scene",
            // Restes d'archivage
            "www", "com", "net", "org", "torrent", "rar", "cue", "log", "scans", "covers",
            "artwork", "nfo", "by", "team"
    );

    /**
     * Mentions d'édition. Elles ferment le titre comme les jetons techniques, mais sont conservées
     * à part : deux dossiers d'un même album dont l'un est une édition de luxe restent des
     * doublons, et il faut le signaler pour que ce ne soit pas la version la plus complète qui
     * disparaisse.
     */
    private static final Set<String> EDITIONS = Set.of(
            "deluxe", "luxe", "expanded", "bonus", "anniversary", "anniversaire", "collector",
            "limited", "limitee", "special", "speciale", "edition", "reissue", "reedition",
            "remaster", "remastered", "remasterise", "remasterisee", "remastering", "integrale",
            "coffret", "digipak", "boxset", "box", "complete", "ultimate", "japanese", "japan",
            "explicit", "clean", "mono", "stereo"
    );

    /**
     * Mentions qui désignent une <b>autre</b> œuvre, et non une autre édition de la même.
     *
     * <p>Deux dossiers qui portent le même titre mais pas la même mention ne sont pas des
     * doublons : {@code Nirvana - Unplugged In New York} n'est pas {@code Nirvana - In Utero},
     * et {@code Discovery} n'est pas {@code Discovery (Remixes)}. Laisser ces mentions se
     * confondre reviendrait à proposer la suppression d'un disque irremplaçable.
     */
    private static final Set<String> VERSIONS = Set.of(
            "live", "unplugged", "acoustic", "acoustique", "demo", "demos", "instrumental",
            "instrumentals", "karaoke", "remix", "remixes", "remixed", "dub", "mixes", "megamix",
            "concert", "tour", "session", "sessions", "rehearsal", "outtakes", "raritees",
            "rarities", "bside", "bsides"
    );

    /** Titres si répandus qu'ils ne désignent aucun album en particulier hors de leur artiste. */
    private static final Set<String> TITRES_GENERIQUES = Set.of(
            "greatest hits", "best of", "the best of", "hits", "compilation", "divers",
            "various", "various artists", "album", "albums", "single", "singles", "ep", "demo",
            "live", "unknown album", "inconnu", "musique", "music", "divers artistes"
    );

    /** Mentions qui désignent une compilation de plusieurs artistes. */
    private static final Set<String> ARTISTES_MULTIPLES = Set.of(
            "various", "various artists", "va", "divers", "divers artistes", "compilation",
            "compilations", "multi interpretes", "artistes varies", "soundtrack", "ost",
            "bande originale"
    );

    /** Extensions retenues comme fichiers audio. */
    private static final Set<String> EXTENSIONS_AUDIO = Set.of(
            "mp3", "flac", "m4a", "aac", "alac", "ogg", "oga", "opus", "wma", "wav", "aiff",
            "aif", "aifc", "ape", "wv", "mpc", "mp2", "dsf", "dff", "tta", "tak", "shn", "ra",
            "amr", "au", "aa3", "oma"
    );

    /**
     * Renseignements techniques collés faute de séparateur : {@code 320kbps}, {@code 24bit96khz},
     * {@code FLAC16}. Ils échappent au vocabulaire mot à mot et laisseraient dans le titre une
     * bouillie qui ne ressemble à aucune autre, donc un album seul dans son groupe.
     *
     * <p>Les fragments cherchés sont volontairement longs : un simple {@code bit} couperait
     * {@code Bittersweet Symphony} après son premier caractère utile.
     */
    private static final Pattern TECHNIQUE_COLLE = Pattern.compile(
            ".*(\\d{2,3}kbps|\\d{2}bits?|\\d{2,3}khz|v[02]vbr|mp3\\d{3}|flac\\d{2}).*");

    private MotsTechniques() {
        // Vocabulaire seul, pas d'instance.
    }

    /** Indique si ce jeton ferme le titre. */
    public static boolean estTechnique(String jeton) {
        return COUPURE.contains(jeton) || TECHNIQUE_COLLE.matcher(jeton).matches();
    }

    /** Indique si ce jeton est une mention d'édition. */
    public static boolean estEdition(String jeton) {
        return EDITIONS.contains(jeton);
    }

    /** Indique si ce jeton désigne une autre œuvre plutôt qu'une autre édition. */
    public static boolean estVersion(String jeton) {
        return VERSIONS.contains(jeton);
    }

    /** Indique si ce titre normalisé est trop répandu pour désigner un album à lui seul. */
    public static boolean estUnTitreGenerique(String titreNormalise) {
        return TITRES_GENERIQUES.contains(titreNormalise);
    }

    /** Indique si ce nom d'artiste normalisé désigne en réalité une compilation. */
    public static boolean estArtistesMultiples(String artisteNormalise) {
        return ARTISTES_MULTIPLES.contains(artisteNormalise);
    }

    /** Indique si cette extension, sans le point et en minuscules, désigne un fichier audio. */
    public static boolean estExtensionAudio(String extension) {
        return EXTENSIONS_AUDIO.contains(extension);
    }
}
