package fr.musique.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fabrique des fichiers audio réduits à leur en-tête.
 *
 * <h2>Pourquoi fabriquer plutôt que joindre des extraits</h2>
 * Un vrai morceau pèse des mégaoctets, et le tronquer donnerait un cas particulier qu'on ne
 * saurait plus faire varier. Les en-têtes, eux, tiennent en quelques dizaines d'octets et
 * s'écrivent à la main : on obtient ainsi une durée exacte, des étiquettes choisies, et de quoi
 * éprouver aussi les formes que le programme doit refuser — un fichier vide, un en-tête tronqué,
 * une durée nulle.
 *
 * <p>Ce qui est écrit ici est un en-tête valide et rien de plus : aucun son. Les lecteurs n'en
 * demandent pas davantage, et c'est précisément ce qu'on attend d'eux — n'ouvrir les fichiers que
 * du bout des lèvres.
 */
public final class FichiersDEssai {

    private FichiersDEssai() {
        // Fabrique seule, pas d'instance.
    }

    /**
     * Écrit un flac annonçant cette durée et ces étiquettes.
     *
     * @param echantillons nombre total d'échantillons, dont la durée se déduit
     * @param frequence    fréquence d'échantillonnage en hertz
     * @param champs       étiquettes, sous leurs noms du dialecte de Vorbis
     */
    public static Path flac(Path chemin, long echantillons, long frequence,
            Map<String, String> champs) throws IOException {
        return flac(chemin, echantillons, frequence, champs, new byte[16]);
    }

    /**
     * Écrit un flac dont le premier bloc porte cette empreinte du signal.
     *
     * <p>Seize octets, qui closent le bloc. Une empreinte entièrement nulle est ce qu'écrit un
     * encodeur qui ne l'a pas calculée : c'est la forme que rend {@link #flac}, et le lecteur doit
     * la tenir pour absente plutôt que pour une valeur partagée par tous.
     */
    public static Path flac(Path chemin, long echantillons, long frequence,
            Map<String, String> champs, byte[] empreinte) throws IOException {
        byte[] streaminfo = new byte[34];
        ByteBuffer empile = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        // Vingt bits de fréquence, trois de canaux, cinq de profondeur, trente-six d'échantillons.
        empile.putLong((frequence << 44) | (1L << 41) | (15L << 36) | echantillons);
        System.arraycopy(empile.array(), 0, streaminfo, 10, 8);
        System.arraycopy(empreinte, 0, streaminfo, 18, 16);

        byte[] commentaires = commentairesVorbis(champs);
        return ecrire(chemin, concatener(
                "fLaC".getBytes(StandardCharsets.US_ASCII),
                blocFlac(0, false, streaminfo),
                blocFlac(4, true, commentaires)));
    }

    /** Seize octets d'empreinte tous égaux à cette valeur, de quoi distinguer deux fichiers. */
    public static byte[] empreinte(int valeur) {
        byte[] octets = new byte[16];
        java.util.Arrays.fill(octets, (byte) valeur);
        return octets;
    }

    /**
     * Écrit un flac dont le premier bloc s'arrête avant son empreinte.
     *
     * <p>Dix-huit octets : de quoi porter la fréquence et le nombre d'échantillons, pas les seize
     * octets d'empreinte qui suivent. Le lecteur doit rendre la durée et renoncer à l'empreinte,
     * plutôt que de lire seize octets qui appartiennent au bloc suivant.
     */
    public static Path flacAuBlocEcourte(Path chemin, long echantillons, long frequence)
            throws IOException {
        byte[] streaminfo = new byte[18];
        ByteBuffer empile = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        empile.putLong((frequence << 44) | (1L << 41) | (15L << 36) | echantillons);
        System.arraycopy(empile.array(), 0, streaminfo, 10, 8);
        return ecrire(chemin, concatener(
                "fLaC".getBytes(StandardCharsets.US_ASCII),
                blocFlac(0, false, streaminfo),
                blocFlac(4, true, commentairesVorbis(etiquettesOrdinaires()))));
    }

    /** Écrit un flac dont l'en-tête n'annonce aucun échantillon, comme un flux inachevé. */
    public static Path flacSansDuree(Path chemin) throws IOException {
        return flac(chemin, 0, 44_100, Map.of());
    }

    /**
     * Écrit un mp3 : un bloc d'étiquettes, puis des trames à débit constant.
     *
     * @param trames       nombre de trames écrites, dont la durée se déduit
     * @param debitEnKilobits débit annoncé par chaque trame
     * @param champs       étiquettes, sous leurs noms de la version 2.3
     */
    public static Path mp3(Path chemin, int trames, int debitEnKilobits, Map<String, String> champs)
            throws IOException {
        byte[] etiquettes = blocId3(champs);
        byte[] entete = trameMpeg(debitEnKilobits);
        int longueur = longueurDeTrame(debitEnKilobits);
        ByteArrayOutputStream son = new ByteArrayOutputStream();
        for (int trame = 0; trame < trames; trame++) {
            son.writeBytes(entete);
            son.writeBytes(new byte[longueur - entete.length]);
        }
        return ecrire(chemin, concatener(etiquettes, son.toByteArray()));
    }

    /** Écrit un mp3 dont la première trame porte la table des encodeurs à débit variable. */
    public static Path mp3AvecTableDeTrames(Path chemin, int trames, int debitEnKilobits)
            throws IOException {
        return mp3AvecTableDeTrames(chemin, trames, debitEnKilobits, null, 0, 0);
    }

    /**
     * Écrit un mp3 dont la table annonce, en plus du nombre de trames, tous ses champs
     * facultatifs.
     *
     * <p>Poids du flux, table de positions de cent octets, indice de qualité : chacun décale
     * l'extension de l'encodeur d'autant. C'est la seule forme qui éprouve le calcul de ce
     * décalage, et se tromper d'un octet y ferait lire du son à la place des mesures.
     */
    public static Path mp3AvecTableDeTramesCompletes(Path chemin, int trames, int debitEnKilobits,
            String encodeur, long longueurDuSon, int controle) throws IOException {
        return mp3AvecTableDeTrames(
                chemin, trames, debitEnKilobits, encodeur, longueurDuSon, controle, 0x0F);
    }

    /**
     * Écrit un mp3 dont la table de trames porte aussi l'extension d'un encodeur.
     *
     * <p>Cette extension est la seule chose qu'un mp3 dise de son <b>son</b> : la longueur exacte
     * du flux et un contrôle calculé dessus. Un nom d'encodeur nul l'omet entièrement, ce qui est
     * le cas de la plupart des fichiers.
     *
     * @param encodeur       neuf lettres, {@code LAME3.100} ou autre, ou {@code null} pour omettre
     *                       l'extension
     * @param longueurDuSon  longueur annoncée du flux audio, en octets
     * @param controle       contrôle du flux audio, sur seize bits
     */
    public static Path mp3AvecTableDeTrames(Path chemin, int trames, int debitEnKilobits,
            String encodeur, long longueurDuSon, int controle) throws IOException {
        return mp3AvecTableDeTrames(
                chemin, trames, debitEnKilobits, encodeur, longueurDuSon, controle, 0x01);
    }

    private static Path mp3AvecTableDeTrames(Path chemin, int trames, int debitEnKilobits,
            String encodeur, long longueurDuSon, int controle, int drapeaux) throws IOException {
        byte[] entete = trameMpeg(debitEnKilobits);
        int longueur = longueurDeTrame(debitEnKilobits);
        byte[] premiere = new byte[longueur];
        System.arraycopy(entete, 0, premiere, 0, entete.length);
        // La table se trouve après la zone que le décodeur se réserve : trente-deux octets pour un
        // flux MPEG 1 qui n'est pas mono.
        int decalage = 4 + 32;
        System.arraycopy("Xing".getBytes(StandardCharsets.US_ASCII), 0, premiere, decalage, 4);
        premiere[decalage + 7] = (byte) drapeaux;
        ByteBuffer nombre = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(trames);
        System.arraycopy(nombre.array(), 0, premiere, decalage + 8, 4);
        if (encodeur != null) {
            // L'extension suit les seuls champs que les drapeaux annoncent, et chacun la décale.
            int extension = decalage + 8
                    + ((drapeaux & 1) != 0 ? 4 : 0)
                    + ((drapeaux & 2) != 0 ? 4 : 0)
                    + ((drapeaux & 4) != 0 ? 100 : 0)
                    + ((drapeaux & 8) != 0 ? 4 : 0);
            System.arraycopy(encodeur.getBytes(StandardCharsets.US_ASCII), 0,
                    premiere, extension, 9);
            System.arraycopy(grosBoutien(longueurDuSon, 4), 0, premiere, extension + 28, 4);
            System.arraycopy(grosBoutien(controle, 2), 0, premiere, extension + 32, 2);
        }

        ByteArrayOutputStream tout = new ByteArrayOutputStream();
        tout.writeBytes(premiere);
        // Une seconde trame, que la vérification de synchronisation attend juste derrière.
        tout.writeBytes(entete);
        tout.writeBytes(new byte[longueur - entete.length]);
        return ecrire(chemin, tout.toByteArray());
    }

    /**
     * Écrit un conteneur ISO annonçant cette durée et ces étiquettes.
     *
     * @param version 0 pour la forme ordinaire, 1 pour celle qui écrit la durée sur huit octets
     */
    public static Path m4a(Path chemin, double secondes, int version, Map<String, String> champs)
            throws IOException {
        long echelle = 1000;
        long duree = Math.round(secondes * echelle);
        ByteBuffer entete = ByteBuffer.allocate(version == 1 ? 112 : 100)
                .order(ByteOrder.BIG_ENDIAN);
        entete.put((byte) version).put(new byte[] {0, 0, 0});
        if (version == 1) {
            entete.putLong(0).putLong(0).putInt((int) echelle).putLong(duree);
        } else {
            entete.putInt(0).putInt(0).putInt((int) echelle).putInt((int) duree);
        }
        byte[] mvhd = atome("mvhd", entete.array());
        byte[] moov = atome("moov", concatener(mvhd, donneesUtilisateur(champs)));
        // Le ftyp d'abord : c'est ce qui oblige le lecteur à sauter un atome avant de trouver le
        // sien, comme dans un vrai fichier.
        byte[] ftyp = atome("ftyp", "M4A ".getBytes(StandardCharsets.US_ASCII));
        return ecrire(chemin, concatener(ftyp, moov));
    }

    /**
     * Écrit un flux Ogg portant de l'Opus.
     *
     * @param echantillons compteur de la dernière page, à quarante-huit mille par seconde
     */
    public static Path opus(Path chemin, long echantillons, Map<String, String> champs)
            throws IOException {
        byte[] identification = concatener(
                "OpusHead".getBytes(StandardCharsets.US_ASCII),
                new byte[] {1, 2},
                petitBoutien(312, 2),
                petitBoutien(48_000, 4),
                new byte[] {0, 0, 0});
        byte[] etiquettes = concatener(
                "OpusTags".getBytes(StandardCharsets.US_ASCII),
                commentairesVorbis(champs));
        return ecrire(chemin, concatener(
                pageOgg(0, identification),
                pageOgg(0, etiquettes),
                pageOgg(echantillons, new byte[] {0})));
    }

    /** Écrit un flux Ogg portant du Vorbis, dont la fréquence est annoncée par son en-tête. */
    public static Path ogg(Path chemin, long echantillons, int frequence) throws IOException {
        byte[] identification = concatener(
                new byte[] {1},
                "vorbis".getBytes(StandardCharsets.US_ASCII),
                petitBoutien(0, 4),
                new byte[] {2},
                petitBoutien(frequence, 4),
                new byte[8]);
        byte[] etiquettes = concatener(
                new byte[] {3},
                "vorbis".getBytes(StandardCharsets.US_ASCII),
                commentairesVorbis(Map.of("ARTIST", "Ogg")));
        return ecrire(chemin, concatener(
                pageOgg(0, identification),
                pageOgg(0, etiquettes),
                pageOgg(echantillons, new byte[] {0})));
    }

    /** Écrit un wav annonçant ce débit et cette longueur de son. */
    public static Path wav(Path chemin, int octetsParSeconde, int octetsDeSon) throws IOException {
        byte[] format = new byte[16];
        System.arraycopy(petitBoutien(octetsParSeconde, 4), 0, format, 8, 4);
        byte[] contenu = concatener(
                "WAVE".getBytes(StandardCharsets.US_ASCII),
                blocRiff("fmt ", format),
                blocRiff("data", new byte[octetsDeSon]));
        return ecrire(chemin, blocRiff("RIFF", contenu));
    }

    /** Écrit un fichier qui porte l'extension d'un morceau sans en être un. */
    public static Path fichierQuiNEnEstPasUn(Path chemin) throws IOException {
        return ecrire(chemin, "ceci n'est pas de la musique".getBytes(StandardCharsets.UTF_8));
    }

    /** Écrit ces octets tels quels : de quoi fabriquer un fichier que rien ne valide. */
    public static Path octets(Path chemin, byte[] contenu) throws IOException {
        return ecrire(chemin, contenu);
    }

    /** Étiquettes ordinaires d'un morceau, pour les cas où leur contenu importe peu. */
    public static Map<String, String> etiquettesOrdinaires() {
        Map<String, String> champs = new LinkedHashMap<>();
        champs.put("ARTIST", "Muse");
        champs.put("ALBUM", "Absolution");
        champs.put("TITLE", "Hysteria");
        champs.put("TRACKNUMBER", "5/14");
        champs.put("DATE", "2003-09-29");
        return champs;
    }

    private static Path ecrire(Path chemin, byte[] octets) throws IOException {
        Files.createDirectories(chemin.getParent());
        Files.write(chemin, octets);
        return chemin;
    }

    // --- flac et commentaires Vorbis ---------------------------------------------------------

    private static byte[] blocFlac(int type, boolean dernier, byte[] contenu) {
        byte[] entete = new byte[4];
        entete[0] = (byte) ((dernier ? 0x80 : 0) | type);
        entete[1] = (byte) ((contenu.length >> 16) & 0xFF);
        entete[2] = (byte) ((contenu.length >> 8) & 0xFF);
        entete[3] = (byte) (contenu.length & 0xFF);
        return concatener(entete, contenu);
    }

    static byte[] commentairesVorbis(Map<String, String> champs) {
        ByteArrayOutputStream octets = new ByteArrayOutputStream();
        byte[] vendeur = "essai".getBytes(StandardCharsets.UTF_8);
        octets.writeBytes(petitBoutien(vendeur.length, 4));
        octets.writeBytes(vendeur);
        octets.writeBytes(petitBoutien(champs.size(), 4));
        champs.forEach((nom, valeur) -> {
            byte[] entree = (nom + "=" + valeur).getBytes(StandardCharsets.UTF_8);
            octets.writeBytes(petitBoutien(entree.length, 4));
            octets.writeBytes(entree);
        });
        return octets.toByteArray();
    }

    // --- mp3 ----------------------------------------------------------------------------------

    /** En-tête d'une trame MPEG 1 couche III, stéréo, à 44,1 kHz. */
    private static byte[] trameMpeg(int debitEnKilobits) {
        int index = switch (debitEnKilobits) {
            case 128 -> 9;
            case 192 -> 11;
            case 320 -> 14;
            default -> 5;
        };
        return new byte[] {
                (byte) 0xFF,
                (byte) 0xFB,
                (byte) ((index << 4) | 0x00),
                (byte) 0x00};
    }

    private static int longueurDeTrame(int debitEnKilobits) {
        return 144 * debitEnKilobits * 1000 / 44_100;
    }

    private static byte[] blocId3(Map<String, String> champs) {
        ByteArrayOutputStream corps = new ByteArrayOutputStream();
        champs.forEach((nom, valeur) -> {
            byte[] texte = valeur.getBytes(StandardCharsets.ISO_8859_1);
            corps.writeBytes(nom.getBytes(StandardCharsets.US_ASCII));
            corps.writeBytes(grosBoutien(texte.length + 1, 4));
            corps.writeBytes(new byte[] {0, 0});
            corps.write(0);
            corps.writeBytes(texte);
        });
        byte[] contenu = corps.toByteArray();
        ByteArrayOutputStream tout = new ByteArrayOutputStream();
        tout.writeBytes("ID3".getBytes(StandardCharsets.US_ASCII));
        tout.writeBytes(new byte[] {3, 0, 0});
        tout.writeBytes(synchsafe(contenu.length));
        tout.writeBytes(contenu);
        return tout.toByteArray();
    }

    private static byte[] synchsafe(int longueur) {
        return new byte[] {
                (byte) ((longueur >> 21) & 0x7F),
                (byte) ((longueur >> 14) & 0x7F),
                (byte) ((longueur >> 7) & 0x7F),
                (byte) (longueur & 0x7F)};
    }

    // --- conteneur ISO -------------------------------------------------------------------------

    private static byte[] donneesUtilisateur(Map<String, String> champs) {
        ByteArrayOutputStream liste = new ByteArrayOutputStream();
        champs.forEach((nom, valeur) -> liste.writeBytes(
                atome(nom, atome("data", concatener(
                        grosBoutien(1, 4),
                        new byte[4],
                        valeur.getBytes(StandardCharsets.UTF_8))))));
        byte[] ilst = atome("ilst", liste.toByteArray());
        // L'atome meta glisse quatre octets de version avant ses enfants : c'est exactement le
        // piège que le lecteur doit savoir éviter.
        byte[] meta = atome("meta", concatener(new byte[4], ilst));
        return atome("udta", meta);
    }

    static byte[] atome(String nom, byte[] contenu) {
        byte[] entete = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                .putInt(contenu.length + 8)
                .put(nom.getBytes(StandardCharsets.ISO_8859_1))
                .array();
        return concatener(entete, contenu);
    }

    // --- Ogg et RIFF ---------------------------------------------------------------------------

    private static byte[] pageOgg(long compteur, byte[] contenu) {
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        page.writeBytes("OggS".getBytes(StandardCharsets.US_ASCII));
        page.write(0);
        page.write(0);
        page.writeBytes(petitBoutien(compteur, 8));
        page.writeBytes(new byte[] {0, 0, 0, 0});
        page.writeBytes(new byte[] {0, 0, 0, 0});
        page.writeBytes(new byte[] {0, 0, 0, 0});
        page.write(1);
        page.write(Math.min(contenu.length, 255));
        page.writeBytes(contenu);
        return page.toByteArray();
    }

    static byte[] blocRiff(String nom, byte[] contenu) {
        byte[] entete = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .put(nom.getBytes(StandardCharsets.US_ASCII))
                .putInt(contenu.length)
                .array();
        return concatener(entete, contenu);
    }

    private static byte[] petitBoutien(long valeur, int longueur) {
        byte[] octets = new byte[longueur];
        for (int rang = 0; rang < longueur; rang++) {
            octets[rang] = (byte) ((valeur >> (8 * rang)) & 0xFF);
        }
        return octets;
    }

    private static byte[] grosBoutien(long valeur, int longueur) {
        byte[] octets = new byte[longueur];
        for (int rang = 0; rang < longueur; rang++) {
            octets[longueur - 1 - rang] = (byte) ((valeur >> (8 * rang)) & 0xFF);
        }
        return octets;
    }

    static byte[] concatener(byte[]... morceaux) {
        ByteArrayOutputStream tout = new ByteArrayOutputStream();
        for (byte[] morceau : morceaux) {
            tout.writeBytes(morceau);
        }
        return tout.toByteArray();
    }
}
