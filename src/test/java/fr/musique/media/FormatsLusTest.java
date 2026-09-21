package fr.musique.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Éprouve l'aiguillage des formats : lesquels sont lus, sous quelles extensions, et ce que
 * devient un fichier dont l'extension ment.
 */
class FormatsLusTest {

    @TempDir
    private Path racine;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "morceau.flac", "morceau.mp3", "morceau.mp2", "morceau.m4a", "morceau.mp4",
            "morceau.m4b", "morceau.aac", "morceau.alac", "morceau.ogg", "morceau.oga",
            "morceau.opus", "morceau.wav", "morceau.wave", "MORCEAU.FLAC"})
    @DisplayName("les formats dont on sait lire l'en-tête sont annoncés comme tels")
    void lesFormatsLusSontAnnonces(String nom) {
        assertThat(EtiquettesDuFichier.saitLire(nom)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"morceau.ape", "morceau.wv", "morceau.wma", "morceau.dsf",
            "morceau.mpc", "morceau"})
    @DisplayName("les formats qu'on ne sait pas lire sont annoncés comme tels")
    void lesFormatsNonLusSontAnnonces(String nom) {
        assertThat(EtiquettesDuFichier.saitLire(nom)).isFalse();
    }

    @Test
    @DisplayName("un flac déguisé en ogg ne rend rien, et réciproquement")
    void lExtensionQuiMentNeRendRien() throws IOException {
        Path flacEnOgg = racine.resolve("faux.ogg");
        FichiersDEssai.flac(racine.resolve("vrai.flac"), 44_100L * 100, 44_100, Map.of());
        FichiersDEssai.octets(flacEnOgg, java.nio.file.Files.readAllBytes(
                racine.resolve("vrai.flac")));

        assertThat(EtiquettesDuFichier.lire(flacEnOgg).estVide()).isTrue();
    }

    @Test
    @DisplayName("un m4b et un aac passent par le même lecteur qu'un m4a")
    void lesVariantesIsoPassentParLeMemeLecteur() throws IOException {
        byte[] entete = new byte[100];
        entete[15] = 1;
        entete[19] = 77;
        byte[] moov = FichiersDEssai.atome("moov", FichiersDEssai.atome("mvhd", entete));

        assertThat(EtiquettesDuFichier.lire(
                FichiersDEssai.octets(racine.resolve("livre.m4b"), moov)).secondes())
                .hasValue(77.0);
        assertThat(EtiquettesDuFichier.lire(
                FichiersDEssai.octets(racine.resolve("morceau.aac"), moov)).secondes())
                .hasValue(77.0);
    }

    @Test
    @DisplayName("un wav dont les blocs sont de longueur impaire reste lisible")
    void leWavAuxBlocsImpairsResteLisible() throws IOException {
        byte[] contenu = FichiersDEssai.concatener(
                "WAVE".getBytes(StandardCharsets.US_ASCII),
                FichiersDEssai.blocRiff("LIST", new byte[5]),
                new byte[] {0},
                fmt(176_400),
                FichiersDEssai.blocRiff("data", new byte[176_400]));

        Path fichier = FichiersDEssai.octets(racine.resolve("impair.wav"),
                FichiersDEssai.blocRiff("RIFF", contenu));

        assertThat(EtiquettesDuFichier.lire(fichier).secondes()).hasValue(1.0);
    }

    private static byte[] fmt(int octetsParSeconde) {
        byte[] format = new byte[16];
        for (int rang = 0; rang < 4; rang++) {
            format[8 + rang] = (byte) ((octetsParSeconde >> (8 * rang)) & 0xFF);
        }
        return FichiersDEssai.blocRiff("fmt ", format);
    }

    @Test
    @DisplayName("une étiquette démesurée est ignorée sans empêcher les autres d'être lues")
    void lEtiquetteDemesureeEstIgnoree() throws IOException {
        Path fichier = FichiersDEssai.flac(racine.resolve("pochette.flac"),
                44_100L * 100, 44_100,
                Map.of("ENORME", "x".repeat(9000), "ARTIST", "Muse"));

        Etiquette etiquette = EtiquettesDuFichier.lire(fichier);

        assertThat(etiquette.secondes()).hasValue(100.0);
        assertThat(etiquette.artiste()).contains("Muse");
    }
}
