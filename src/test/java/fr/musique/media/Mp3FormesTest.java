package fr.musique.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Éprouve le lecteur mp3 sur les formes que le format autorise et qu'on rencontre vraiment.
 *
 * <p>Le mp3 n'a pas d'en-tête de fichier : chaque trame décrit la suivante, et cinq combinaisons
 * de version, de couche et de canaux donnent cinq longueurs de trame différentes. Se tromper sur
 * l'une d'elles ne produit pas une erreur mais une durée fausse, ce qui est pire.
 */
class Mp3FormesTest {

    @TempDir
    private Path racine;

    /**
     * Écrit un mp3 fait de trames identiques, décrites bit à bit.
     *
     * @param version 3 pour MPEG 1, 2 pour MPEG 2
     * @param couche  1 pour la couche III, 2 pour la couche II, 3 pour la couche I
     * @param mono    vrai pour un flux à un seul canal
     */
    private Path ecrire(String nom, int version, int couche, int indexDeDebit, boolean mono,
            int trames) throws IOException {
        int entete = 0xFFE0_0000
                | (version << 19)
                | (couche << 17)
                | (indexDeDebit << 12)
                | (mono ? 3 << 6 : 0);
        byte[] quatre = {
                (byte) (entete >>> 24), (byte) (entete >>> 16),
                (byte) (entete >>> 8), (byte) entete};
        int debit = debitEnKilobits(version, couche, indexDeDebit) * 1000;
        int frequence = version == 3 ? 44_100 : 22_050;
        int longueur = couche == 3
                ? (12 * debit / frequence) * 4
                : echantillons(version, couche) / 8 * debit / frequence;

        ByteArrayOutputStream flux = new ByteArrayOutputStream();
        for (int trame = 0; trame < trames; trame++) {
            flux.writeBytes(quatre);
            flux.writeBytes(new byte[longueur - 4]);
        }
        return FichiersDEssai.octets(racine.resolve(nom), flux.toByteArray());
    }

    private static int echantillons(int version, int couche) {
        if (couche == 3) {
            return 384;
        }
        return couche == 2 || version == 3 ? 1152 : 576;
    }

    private static int debitEnKilobits(int version, int couche, int index) {
        int[] v1c1 = {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, -1};
        int[] v1c2 = {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, -1};
        int[] v1c3 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1};
        int[] v2c1 = {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, -1};
        int[] v2autres = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1};
        if (version == 3) {
            return switch (couche) {
                case 3 -> v1c1[index];
                case 2 -> v1c2[index];
                default -> v1c3[index];
            };
        }
        return couche == 3 ? v2c1[index] : v2autres[index];
    }

    @Test
    @DisplayName("un flux MPEG 1 couche III à 320 kbit/s rend la durée attendue")
    void mpeg1CoucheTrois() throws IOException {
        Path fichier = ecrire("320.mp3", 3, 1, 14, false, 100);

        assertThat(EtiquettesDuFichier.lire(fichier).secondes().getAsDouble())
                .isCloseTo(2.61, within(0.05));
    }

    @Test
    @DisplayName("un flux mono est lu comme un flux stéréo")
    void leFluxMonoEstLuAussi() throws IOException {
        Path fichier = ecrire("mono.mp3", 3, 1, 9, true, 100);

        assertThat(EtiquettesDuFichier.lire(fichier).secondes()).isPresent();
    }

    @Test
    @DisplayName("un flux MPEG 2 a des trames deux fois plus courtes, et une durée juste")
    void mpeg2ADesTramesPlusCourtes() throws IOException {
        Path fichier = ecrire("mpeg2.mp3", 2, 1, 9, false, 100);

        assertThat(EtiquettesDuFichier.lire(fichier).secondes().getAsDouble())
                .isCloseTo(2.61, within(0.05));
    }

    @Test
    @DisplayName("les couches I et II se lisent comme la couche III")
    void lesAutresCouchesSeLisent() throws IOException {
        assertThat(EtiquettesDuFichier.lire(ecrire("couche1.mp3", 3, 3, 10, false, 100))
                .secondes()).isPresent();
        assertThat(EtiquettesDuFichier.lire(ecrire("couche2.mp3", 3, 2, 10, false, 100))
                .secondes()).isPresent();
    }

    @Test
    @DisplayName("un débit annoncé comme libre ou réservé n'est pas un débit")
    void leDebitLibreNEstPasUnDebit() throws IOException {
        byte[] trame = {(byte) 0xFF, (byte) 0xFB, (byte) 0x00, 0};
        Path fichier = FichiersDEssai.octets(racine.resolve("libre.mp3"),
                FichiersDEssai.concatener(trame, new byte[1000]));

        assertThat(EtiquettesDuFichier.lire(fichier).secondes()).isEmpty();
    }

    @Test
    @DisplayName("une trame isolée ne suffit pas : la suivante doit être là où elle est annoncée")
    void laTrameIsoleeNeSuffitPas() throws IOException {
        Path fichier = ecrire("isolee.mp3", 3, 1, 9, false, 1);

        assertThat(EtiquettesDuFichier.lire(fichier).secondes()).isEmpty();
    }

    @Test
    @DisplayName("un bloc d'étiquettes d'ancienne version est lu lui aussi")
    void leBlocDAncienneVersionEstLu() throws IOException {
        byte[] corps = FichiersDEssai.concatener(
                "TP1".getBytes(StandardCharsets.US_ASCII),
                new byte[] {0, 0, 5},
                new byte[] {0},
                "Muse".getBytes(StandardCharsets.ISO_8859_1));
        byte[] bloc = FichiersDEssai.concatener(
                "ID3".getBytes(StandardCharsets.US_ASCII),
                new byte[] {2, 0, 0},
                new byte[] {0, 0, 0, (byte) corps.length},
                corps);

        Path fichier = FichiersDEssai.octets(racine.resolve("ancien.mp3"), bloc);

        assertThat(EtiquettesDuFichier.lire(fichier).artiste()).contains("Muse");
    }

    @Test
    @DisplayName("un bloc d'étiquettes dont un champ déborde s'arrête là")
    void leChampQuiDeborde() throws IOException {
        byte[] corps = FichiersDEssai.concatener(
                "TPE1".getBytes(StandardCharsets.US_ASCII),
                new byte[] {0, 0, 0, 100},
                new byte[] {0, 0},
                new byte[] {0},
                "Muse".getBytes(StandardCharsets.ISO_8859_1));
        byte[] bloc = FichiersDEssai.concatener(
                "ID3".getBytes(StandardCharsets.US_ASCII),
                new byte[] {3, 0, 0},
                new byte[] {0, 0, 0, (byte) corps.length},
                corps);

        Path fichier = FichiersDEssai.octets(racine.resolve("deborde.mp3"), bloc);

        assertThat(EtiquettesDuFichier.lire(fichier).artiste()).isEmpty();
    }

    @Test
    @DisplayName("les étiquettes en UTF-16 sont lues comme les autres")
    void lesEtiquettesEnUtf16SontLues() throws IOException {
        byte[] texte = "Björk".getBytes(StandardCharsets.UTF_16);
        byte[] corps = FichiersDEssai.concatener(
                "TPE1".getBytes(StandardCharsets.US_ASCII),
                new byte[] {0, 0, 0, (byte) (texte.length + 1)},
                new byte[] {0, 0},
                new byte[] {1},
                texte);
        byte[] bloc = FichiersDEssai.concatener(
                "ID3".getBytes(StandardCharsets.US_ASCII),
                new byte[] {4, 0, 0},
                new byte[] {0, 0, 0, (byte) corps.length},
                corps);

        Path fichier = FichiersDEssai.octets(racine.resolve("utf16.mp3"), bloc);

        assertThat(EtiquettesDuFichier.lire(fichier).artiste()).contains("Björk");
    }

    @Test
    @DisplayName("un mp3 sans étiquette ni trame ne rend rien, sans se plaindre")
    void leMp3MuetNeRendRien() throws IOException {
        Path fichier = FichiersDEssai.mp3(racine.resolve("muet.mp3"), 0, 128, Map.of());

        assertThat(EtiquettesDuFichier.lire(fichier).estVide()).isTrue();
    }

    @ParameterizedTest(name = "version {0}, couche {1}, débit d''index {2}")
    @CsvSource({"3, 1, 5", "3, 1, 9", "3, 1, 11", "3, 2, 8", "3, 3, 6", "2, 1, 3", "2, 2, 5",
            "2, 3, 7", "0, 1, 9"})
    @DisplayName("toutes les combinaisons du format rendent une durée cohérente")
    void toutesLesCombinaisonsRendentUneDuree(int version, int couche, int index)
            throws IOException {
        Path fichier = ecrire(
                "forme-" + version + couche + index + ".mp3", version, couche, index, false, 50);

        assertThat(EtiquettesDuFichier.lire(fichier).secondes()).isPresent();
    }
}
