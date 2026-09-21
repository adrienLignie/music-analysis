package fr.musique.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Durée et étiquettes d'un fichier mp3.
 *
 * <h2>Le format qui ne dit rien de lui-même</h2>
 * Un mp3 n'a pas d'en-tête de fichier : c'est une suite de trames qui portent chacune sa propre
 * description, et rien n'annonce nulle part la durée totale. Trois chemins existent, du plus sûr
 * au moins sûr, et le programme les prend dans cet ordre :
 * <ol>
 *   <li>la table {@code Xing} ou {@code Info}, que les encodeurs à débit variable écrivent dans la
 *       première trame : elle porte le nombre de trames, d'où la durée se déduit exactement ;</li>
 *   <li>à défaut, le débit annoncé par la première trame, qui donne la durée juste tant que le
 *       fichier est à débit constant ;</li>
 *   <li>rien, si aucune trame n'est trouvée là où elle devrait être.</li>
 * </ol>
 *
 * <p>Les étiquettes, elles, sont dans un bloc {@code ID3} en tête, dont les trois versions ne
 * codent pas leurs longueurs de la même façon. La version 2.4 écrit ses tailles en « synchsafe »,
 * sept bits utiles par octet, pour qu'aucune taille ne puisse ressembler à une synchronisation
 * audio — précaution qui coûte une ligne de lecture et évite qu'un lecteur ne s'égare.
 */
final class Mp3 {

    private static final String SIGNATURE = "ID3";

    /** Longueur de l'en-tête d'un bloc ID3v2. */
    private static final int ENTETE_ID3 = 10;

    /** Au-delà, on cesse de chercher une trame : le fichier n'en porte pas. */
    private static final int OCTETS_CHERCHES_AVANT_LA_PREMIERE_TRAME = 64 * 1024;

    /** Un mp3 n'a pas cinquante étiquettes utiles ; la borne protège d'un bloc malformé. */
    private static final int CHAMPS_EXAMINES_AU_PLUS = 64;

    /** Étiquettes retenues, sous leurs noms de version 2.3 et 2.4 puis de version 2.2. */
    private static final Map<String, String> CHAMPS = Map.ofEntries(
            Map.entry("TPE1", "artist"), Map.entry("TP1", "artist"),
            Map.entry("TPE2", "albumartist"), Map.entry("TP2", "albumartist"),
            Map.entry("TALB", "album"), Map.entry("TAL", "album"),
            Map.entry("TIT2", "title"), Map.entry("TT2", "title"),
            Map.entry("TRCK", "tracknumber"), Map.entry("TRK", "tracknumber"),
            Map.entry("TYER", "year"), Map.entry("TYE", "year"),
            Map.entry("TDRC", "date"), Map.entry("TDRL", "date"));

    /** Débits, en kilobits par seconde, indexés par le nombre écrit dans la trame. */
    private static final int[] DEBITS_V1_COUCHE1 =
            {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, -1};
    private static final int[] DEBITS_V1_COUCHE2 =
            {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, -1};
    private static final int[] DEBITS_V1_COUCHE3 =
            {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1};
    private static final int[] DEBITS_V2_COUCHE1 =
            {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, -1};
    private static final int[] DEBITS_V2_AUTRES =
            {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1};

    /** Fréquences d'échantillonnage, par version puis par index. */
    private static final int[][] FREQUENCES = {
            {11025, 12000, 8000},   // MPEG 2.5
            {0, 0, 0},              // réservé
            {22050, 24000, 16000},  // MPEG 2
            {44100, 48000, 32000}   // MPEG 1
    };

    /** Signatures de la table qui porte le nombre de trames d'un fichier à débit variable. */
    private static final Set<String> TABLE_DE_TRAMES = Set.of("Xing", "Info");

    /**
     * Longueur du nom d'encodeur qui ouvre l'extension LAME de la table de trames.
     *
     * <p>Neuf lettres, {@code LAME3.100} ou {@code Lavf58.29}, suivies de vingt-sept octets de
     * mesures faites à l'encodage. Deux d'entre elles décrivent le son et lui seul.
     */
    private static final int LONGUEUR_DU_NOM_D_ENCODEUR = 9;

    /** Position, dans l'extension LAME, de la longueur exacte du flux audio en octets. */
    private static final int DECALAGE_DE_LA_LONGUEUR_DU_SON = 28;

    /** Position, dans l'extension LAME, du contrôle du flux audio. */
    private static final int DECALAGE_DU_CONTROLE_DU_SON = 32;

    /** Longueur totale de l'extension LAME. */
    private static final int LONGUEUR_DE_L_EXTENSION_LAME = 36;

    /** Sorte de l'empreinte, pour qu'un contrôle de mp3 ne soit jamais comparé au MD5 d'un flac. */
    private static final String SORTE_D_EMPREINTE = "mp3-lame:";

    private Mp3() {
        // Lecture seule, pas d'instance.
    }

    /**
     * Ce que le flux audio dit de lui-même : sa durée, et son empreinte quand il en porte une.
     *
     * <p>Les deux sortent de la même table de trames, trouvée au prix du même balayage : les
     * séparer obligerait à chercher deux fois la première trame, c'est-à-dire à relire jusqu'à
     * soixante-quatre kilo-octets pour rien.
     */
    private record Son(OptionalDouble duree, Optional<String> empreinte) {

        static final Son INCONNU = new Son(OptionalDouble.empty(), Optional.empty());

        static Son de(OptionalDouble duree) {
            return new Son(duree, Optional.empty());
        }
    }

    /** Étiquette lue dans le fichier, vide quand rien n'a pu l'être. */
    static Etiquette lire(Fenetre fenetre) throws IOException {
        Map<String, String> champs = new HashMap<>();
        long debutDuSon = lireLeBlocId3(fenetre, champs);
        Son son = lireLeSon(fenetre, debutDuSon);
        return Etiquette.de(son.duree(), champs, son.empreinte());
    }

    /**
     * Lit le bloc d'étiquettes s'il y en a un, et rend la position où le son commence.
     *
     * <p>Cette position est celle à partir de laquelle chercher la première trame : la chercher
     * depuis le début du fichier ferait prendre pour une synchronisation audio n'importe quels
     * deux octets d'une pochette enfouie dans les étiquettes.
     */
    private static long lireLeBlocId3(Fenetre fenetre, Map<String, String> champs)
            throws IOException {
        if (fenetre.taille() < ENTETE_ID3 || !SIGNATURE.equals(fenetre.etiquette(0, 3))) {
            return 0;
        }
        int version = (int) fenetre.entierGrosBoutien(3, 1);
        long longueur = synchsafe(fenetre.entierGrosBoutien(6, 4));
        long fin = Math.min(ENTETE_ID3 + longueur, fenetre.taille());
        lireLesChamps(fenetre, ENTETE_ID3, fin, version, champs);
        return fin;
    }

    /**
     * Parcourt les champs du bloc d'étiquettes.
     *
     * <p>Les versions 2.2 d'un côté, 2.3 et 2.4 de l'autre, n'écrivent ni le même nombre de
     * lettres par nom de champ ni la même longueur d'en-tête. Un champ dont le nom ne commence pas
     * par une lettre marque le rembourrage de fin du bloc, et non un champ de plus : c'est là que
     * s'arrête la lecture.
     */
    private static void lireLesChamps(
            Fenetre fenetre, long debut, long fin, int version, Map<String, String> champs)
            throws IOException {
        boolean ancienneVersion = version <= 2;
        int longueurDuNom = ancienneVersion ? 3 : 4;
        int longueurDeLEntete = ancienneVersion ? 6 : 10;
        long position = debut;
        for (int examines = 0;
                examines < CHAMPS_EXAMINES_AU_PLUS && position + longueurDeLEntete <= fin;
                examines++) {
            String nom = fenetre.etiquette(position, longueurDuNom);
            if (!Character.isLetterOrDigit(nom.charAt(0))) {
                return;
            }
            long longueur = ancienneVersion
                    ? fenetre.entierGrosBoutien(position + 3, 3)
                    : longueurDUnChamp(fenetre, position + 4, version);
            long contenu = position + longueurDeLEntete;
            if (longueur <= 0 || contenu + longueur > fin) {
                return;
            }
            String champ = CHAMPS.get(nom);
            if (champ != null) {
                texte(fenetre, contenu, (int) longueur)
                        .ifPresent(valeur -> champs.putIfAbsent(champ, valeur));
            }
            position = contenu + longueur;
        }
    }

    /** La version 2.4 écrit les longueurs en synchsafe, la version 2.3 en entier ordinaire. */
    private static long longueurDUnChamp(Fenetre fenetre, long position, int version)
            throws IOException {
        long brute = fenetre.entierGrosBoutien(position, 4);
        return version >= 4 ? synchsafe(brute) : brute;
    }

    /** Recompose un entier écrit sept bits par octet, le huitième étant toujours nul. */
    private static long synchsafe(long valeur) {
        return ((valeur & 0x7F00_0000L) >>> 3) | ((valeur & 0x7F_0000L) >>> 2)
                | ((valeur & 0x7F00L) >>> 1) | (valeur & 0x7FL);
    }

    /**
     * Lit le contenu d'un champ, dont le premier octet dit dans quel alphabet il est écrit.
     *
     * <p>Quatre encodages coexistent selon la version du bloc et l'humeur du logiciel qui l'a
     * écrit. Les ignorer rendrait {@code Björk} illisible une fois sur deux, et l'artiste ne se
     * rapprocherait plus de lui-même.
     */
    private static Optional<String> texte(Fenetre fenetre, long position, int longueur)
            throws IOException {
        ByteBuffer octets = fenetre.lire(position, longueur);
        byte[] contenu = new byte[longueur];
        octets.get(contenu);
        Charset alphabet = switch (contenu[0]) {
            case 1 -> StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            case 3 -> StandardCharsets.UTF_8;
            default -> StandardCharsets.ISO_8859_1;
        };
        String valeur = new String(contenu, 1, longueur - 1, alphabet)
                .replace('\0', ' ')
                .trim();
        return valeur.isEmpty() ? Optional.empty() : Optional.of(valeur);
    }

    /** Durée du son, lue dans la table de trames ou déduite du débit annoncé. */
    private static Son lireLeSon(Fenetre fenetre, long debutDuSon) throws IOException {
        long trame = chercherLaPremiereTrame(fenetre, debutDuSon);
        if (trame < 0) {
            return Son.INCONNU;
        }
        long entete = fenetre.entierGrosBoutien(trame, 4);
        int version = (int) ((entete >>> 19) & 0x3);
        int couche = (int) ((entete >>> 17) & 0x3);
        int indexDeDebit = (int) ((entete >>> 12) & 0xF);
        int indexDeFrequence = (int) ((entete >>> 10) & 0x3);
        int canaux = (int) ((entete >>> 6) & 0x3);
        if (indexDeFrequence == 3 || couche == 0) {
            return Son.INCONNU;
        }
        int frequence = FREQUENCES[version][indexDeFrequence];
        int debit = debitEnKilobits(version, couche, indexDeDebit);
        if (frequence <= 0 || debit <= 0) {
            return Son.INCONNU;
        }
        Son parLaTable = lireLaTableDeTrames(fenetre, trame, version, couche, canaux, frequence);
        if (parLaTable.duree().isPresent()) {
            return parLaTable;
        }
        double octetsDeSon = fenetre.taille() - trame;
        return Son.de(OptionalDouble.of(octetsDeSon * 8 / (debit * 1000.0)));
    }

    /**
     * Cherche l'octet où commence la première trame.
     *
     * <p>Une trame s'annonce par onze bits à un. Ce motif tombe par hasard tous les deux mille
     * octets dans des données quelconques, et une pochette enfouie dans les étiquettes en contient
     * donc plusieurs. Deux vérifications le distinguent d'une vraie trame : ce que l'en-tête
     * annonce doit avoir un sens — version, couche, débit et fréquence renseignés —, et la trame
     * <b>suivante</b> doit se trouver exactement là où la longueur de celle-ci la place.
     *
     * <p>Sans cette seconde vérification, un fichier corrompu rend une durée fantaisiste, ce qui
     * est pire qu'une absence de durée : le rapport blanchirait alors une piste coupée en lui
     * trouvant un débit convenable.
     */
    private static long chercherLaPremiereTrame(Fenetre fenetre, long debut) throws IOException {
        long limite = Math.min(debut + OCTETS_CHERCHES_AVANT_LA_PREMIERE_TRAME,
                fenetre.taille() - 4);
        for (long position = debut; position <= limite; position++) {
            long entete = fenetre.entierGrosBoutien(position, 4);
            if (!estUnEnTeteDeTrame(entete)) {
                continue;
            }
            long longueur = longueurDeLaTrame(entete);
            if (longueur <= 0 || position + longueur + 4 > fenetre.taille()) {
                continue;
            }
            long suivante = fenetre.entierGrosBoutien(position + longueur, 4);
            // Même version, même couche et même fréquence : les trois champs qui ne peuvent pas
            // changer d'une trame à l'autre au sein d'un même fichier.
            if (estUnEnTeteDeTrame(suivante)
                    && (suivante & 0x000E_0C00L) == (entete & 0x000E_0C00L)) {
                return position;
            }
        }
        return -1;
    }

    /** Vrai quand ces quatre octets peuvent être l'en-tête d'une trame. */
    private static boolean estUnEnTeteDeTrame(long entete) {
        if ((entete & 0xFFE0_0000L) != 0xFFE0_0000L) {
            return false;
        }
        int version = (int) ((entete >>> 19) & 0x3);
        int couche = (int) ((entete >>> 17) & 0x3);
        int indexDeDebit = (int) ((entete >>> 12) & 0xF);
        int indexDeFrequence = (int) ((entete >>> 10) & 0x3);
        return version != 1 && couche != 0 && indexDeDebit != 0 && indexDeDebit != 15
                && indexDeFrequence != 3;
    }

    /**
     * Longueur d'une trame en octets, d'après ce que son en-tête annonce.
     *
     * <p>Une trame porte toujours le même nombre d'échantillons ; sa longueur en octets se déduit
     * donc du débit et de la fréquence. La couche I compte à part : ses trames sont quatre fois
     * plus courtes et se mesurent par groupes de quatre octets.
     */
    private static long longueurDeLaTrame(long entete) {
        int version = (int) ((entete >>> 19) & 0x3);
        int couche = (int) ((entete >>> 17) & 0x3);
        int indexDeDebit = (int) ((entete >>> 12) & 0xF);
        int indexDeFrequence = (int) ((entete >>> 10) & 0x3);
        int remplissage = (int) ((entete >>> 9) & 0x1);
        int frequence = FREQUENCES[version][indexDeFrequence];
        int debit = debitEnKilobits(version, couche, indexDeDebit) * 1000;
        if (frequence <= 0 || debit <= 0) {
            return -1;
        }
        if (couche == 3) {
            return (12L * debit / frequence + remplissage) * 4;
        }
        int echantillons = echantillonsParTrame(version, couche);
        return echantillons / 8L * debit / frequence + remplissage;
    }

    /**
     * Durée tirée de la table que les encodeurs à débit variable écrivent dans la première trame.
     *
     * <p>Sa position dans la trame dépend de la version et du nombre de canaux : elle vient après
     * la zone que le décodeur utilise pour son propre travail, dont la taille change avec les
     * deux. C'est la seule durée exacte qu'un mp3 puisse donner.
     */
    private static Son lireLaTableDeTrames(
            Fenetre fenetre, long trame, int version, int couche, int canaux, int frequence)
            throws IOException {
        boolean mpeg1 = version == 3;
        boolean mono = canaux == 3;
        int decalage = 4 + (mpeg1 ? (mono ? 17 : 32) : (mono ? 9 : 17));
        long position = trame + decalage;
        if (position + 12 > fenetre.taille()) {
            return Son.INCONNU;
        }
        if (!TABLE_DE_TRAMES.contains(fenetre.etiquette(position))) {
            return Son.INCONNU;
        }
        long drapeaux = fenetre.entierGrosBoutien(position + 4, 4);
        if ((drapeaux & 1) == 0) {
            return Son.INCONNU;
        }
        long trames = fenetre.entierGrosBoutien(position + 8, 4);
        if (trames <= 0) {
            return Son.INCONNU;
        }
        int echantillonsParTrame = echantillonsParTrame(version, couche);
        return new Son(
                OptionalDouble.of((double) trames * echantillonsParTrame / frequence),
                lireLEmpreinte(fenetre, position, drapeaux, trames));
    }

    /**
     * Empreinte du flux audio, lue dans l'extension que LAME ajoute à la table de trames.
     *
     * <p>Elle réunit trois mesures faites à l'encodage et qui ne décrivent que le son : le nombre
     * de trames, la longueur exacte du flux en octets, et un contrôle calculé sur ce flux. Ni les
     * étiquettes, ni la pochette, ni le nom du fichier n'y entrent — réétiqueter un mp3 change son
     * poids sur le disque et ne change pas son empreinte. C'est précisément ce qu'on lui demande :
     * reconnaître deux exemplaires du même encodage sous deux rangements différents.
     *
     * <p>Seize bits de contrôle ne sont pas cent vingt-huit : l'empreinte d'un mp3 corrobore là où
     * celle d'un flac démontre. Jointe à la longueur exacte du flux, elle reste assez sûre pour ce
     * qu'on en fait — élever une confiance, jamais défaire un rapprochement.
     *
     * <p>Sa position se déduit des drapeaux : les champs facultatifs de la table qui précèdent
     * l'extension n'occupent de place que s'ils sont annoncés.
     */
    private static Optional<String> lireLEmpreinte(
            Fenetre fenetre, long table, long drapeaux, long trames) throws IOException {
        // Les quatre premiers octets sont ceux du nombre de trames, toujours présents : l'appelant
        // a renoncé plus haut si son drapeau manquait, faute de quoi il n'aurait pas de durée. Les
        // trois autres champs, eux, n'occupent de place que s'ils sont annoncés.
        long extension = table + 8 + 4
                + ((drapeaux & 2) != 0 ? 4 : 0)
                + ((drapeaux & 4) != 0 ? 100 : 0)
                + ((drapeaux & 8) != 0 ? 4 : 0);
        if (extension + LONGUEUR_DE_L_EXTENSION_LAME > fenetre.taille()) {
            return Optional.empty();
        }
        String encodeur = fenetre.etiquette(extension, LONGUEUR_DU_NOM_D_ENCODEUR);
        if (!estUnNomDEncodeur(encodeur)) {
            // Sans nom d'encodeur lisible, il n'y a pas d'extension LAME à cet endroit : ce qu'on
            // lirait plus loin serait du son pris pour des mesures.
            return Optional.empty();
        }
        long longueurDuSon =
                fenetre.entierGrosBoutien(extension + DECALAGE_DE_LA_LONGUEUR_DU_SON, 4);
        long controle = fenetre.entierGrosBoutien(extension + DECALAGE_DU_CONTROLE_DU_SON, 2);
        if (longueurDuSon <= 0 || controle == 0) {
            // Un encodeur qui n'a pas calculé ces mesures les laisse à zéro. Les retenir ferait
            // tenir pour identiques tous les fichiers qu'il a produits.
            return Optional.empty();
        }
        return Optional.of(SORTE_D_EMPREINTE + Long.toHexString(trames)
                + ":" + Long.toHexString(longueurDuSon) + ":" + Long.toHexString(controle));
    }

    /** Vrai quand ces neuf octets se lisent comme un nom d'encodeur et non comme du son. */
    private static boolean estUnNomDEncodeur(String encodeur) {
        if (!Character.isLetter(encodeur.charAt(0))) {
            return false;
        }
        for (int rang = 0; rang < encodeur.length(); rang++) {
            char lettre = encodeur.charAt(rang);
            if (lettre < ' ' || lettre > '~') {
                return false;
            }
        }
        return true;
    }

    private static int echantillonsParTrame(int version, int couche) {
        if (couche == 3) {
            return 384;
        }
        boolean mpeg1 = version == 3;
        return couche == 2 || mpeg1 ? 1152 : 576;
    }

    private static int debitEnKilobits(int version, int couche, int index) {
        if (index == 0 || index == 15) {
            return 0;
        }
        boolean mpeg1 = version == 3;
        int[] table;
        if (mpeg1) {
            table = switch (couche) {
                case 3 -> DEBITS_V1_COUCHE1;
                case 2 -> DEBITS_V1_COUCHE2;
                default -> DEBITS_V1_COUCHE3;
            };
        } else {
            table = couche == 3 ? DEBITS_V2_COUCHE1 : DEBITS_V2_AUTRES;
        }
        return table[index];
    }

}
