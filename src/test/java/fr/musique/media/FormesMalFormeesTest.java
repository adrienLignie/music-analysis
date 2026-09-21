package fr.musique.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Éprouve ce que les lecteurs font des fichiers mal formés.
 *
 * <p>C'est la moitié du travail d'un lecteur d'en-tête : ne jamais faire échouer une analyse pour
 * un fichier qui ne répond pas de ce qu'il annonce. Les cas rassemblés ici sont ceux qu'une
 * bibliothèque réelle finit par produire — fichiers coupés en cours d'écriture, en-têtes
 * tronqués, longueurs qui débordent.
 */
class FormesMalFormeesTest {

    @TempDir
    private Path racine;

    private static byte[] ascii(String texte) {
        return texte.getBytes(StandardCharsets.US_ASCII);
    }

    @Nested
    @DisplayName("En-têtes coupés")
    class EntetesCoupes {

        @Test
        @DisplayName("un flac réduit à sa signature ne rend rien")
        void leFlacSansBlocNeRendRien() throws IOException {
            Path fichier = FichiersDEssai.octets(
                    racine.resolve("court.flac"), ascii("fLaCXXXX"));

            assertThat(EtiquettesDuFichier.lire(fichier).estVide()).isTrue();
        }

        @Test
        @DisplayName("un flac dont un bloc déborde du fichier ne rend rien")
        void leFlacQuiDeborde() throws IOException {
            byte[] contenu = FichiersDEssai.concatener(
                    ascii("fLaC"),
                    new byte[] {(byte) 0x80, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
                    new byte[] {1, 2, 3});

            Path fichier = FichiersDEssai.octets(racine.resolve("deborde.flac"), contenu);

            assertThat(EtiquettesDuFichier.lire(fichier).estVide()).isTrue();
        }

        @Test
        @DisplayName("un conteneur ISO sans atome de film ne rend rien")
        void leConteneurSansFilmNeRendRien() throws IOException {
            Path fichier = FichiersDEssai.octets(racine.resolve("sansmoov.m4a"),
                    FichiersDEssai.atome("ftyp", ascii("M4A ")));

            assertThat(EtiquettesDuFichier.lire(fichier).estVide()).isTrue();
        }

        @Test
        @DisplayName("un conteneur ISO dont un atome annonce une longueur absurde ne rend rien")
        void lAtomeDeLongueurAbsurdeEstRefuse() throws IOException {
            byte[] contenu = FichiersDEssai.concatener(
                    new byte[] {0, 0, 0, 2},
                    ascii("moov"),
                    new byte[] {0, 0, 0, 0});

            Path fichier = FichiersDEssai.octets(racine.resolve("absurde.m4a"), contenu);

            assertThat(EtiquettesDuFichier.lire(fichier).estVide()).isTrue();
        }

        @Test
        @DisplayName("un Ogg sans dernière page lisible ne rend pas de durée")
        void lOggSansCompteurNeRendRien() throws IOException {
            Path fichier = FichiersDEssai.octets(racine.resolve("court.ogg"),
                    FichiersDEssai.concatener(ascii("OggS"), new byte[32]));

            assertThat(EtiquettesDuFichier.lire(fichier).secondes()).isEmpty();
        }

        @Test
        @DisplayName("un wav dont le bloc de format manque ne rend pas de durée")
        void leWavSansFormatNeRendRien() throws IOException {
            byte[] contenu = FichiersDEssai.concatener(
                    ascii("WAVE"),
                    FichiersDEssai.blocRiff("data", new byte[64]));

            Path fichier = FichiersDEssai.octets(racine.resolve("sansfmt.wav"),
                    FichiersDEssai.blocRiff("RIFF", contenu));

            assertThat(EtiquettesDuFichier.lire(fichier).estVide()).isTrue();
        }

        @Test
        @DisplayName("un wav dont un bloc déborde ne rend pas de durée")
        void leWavQuiDeborde() throws IOException {
            byte[] contenu = FichiersDEssai.concatener(
                    ascii("WAVE"),
                    ascii("data"),
                    new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F});

            Path fichier = FichiersDEssai.octets(racine.resolve("deborde.wav"),
                    FichiersDEssai.blocRiff("RIFF", contenu));

            assertThat(EtiquettesDuFichier.lire(fichier).estVide()).isTrue();
        }
    }

    @Nested
    @DisplayName("Étiquettes hors normes")
    class EtiquettesHorsNormes {

        @Test
        @DisplayName("une entrée sans signe égal est ignorée sans faire échouer la lecture")
        void lEntreeSansEgalEstIgnoree() throws IOException {
            Path fichier = FichiersDEssai.flac(racine.resolve("bizarre.flac"),
                    44_100L * 100, 44_100, Map.of("SANSVALEUR", ""));

            Etiquette etiquette = EtiquettesDuFichier.lire(fichier);

            assertThat(etiquette.secondes()).hasValue(100.0);
            assertThat(etiquette.artiste()).isEmpty();
        }

        @Test
        @DisplayName("un champ vide vaut un champ absent")
        void leChampVideVautUnChampAbsent() throws IOException {
            Path fichier = FichiersDEssai.flac(racine.resolve("vide.flac"),
                    44_100L * 100, 44_100, Map.of("ARTIST", "   "));

            assertThat(EtiquettesDuFichier.lire(fichier).artiste()).isEmpty();
        }

        @Test
        @DisplayName("un numéro de piste illisible n'est pas un numéro")
        void leNumeroIllisibleNEnEstPasUn() throws IOException {
            Path fichier = FichiersDEssai.flac(racine.resolve("piste.flac"),
                    44_100L * 100, 44_100, Map.of("TRACKNUMBER", "A5", "DATE", "1899"));

            Etiquette etiquette = EtiquettesDuFichier.lire(fichier);

            assertThat(etiquette.numero()).isEmpty();
            assertThat(etiquette.annee()).isEmpty();
        }

        @Test
        @DisplayName("l'artiste de l'album sert quand l'artiste de la piste manque")
        void lArtisteDeLAlbumSertDeRepli() throws IOException {
            Path fichier = FichiersDEssai.flac(racine.resolve("album.flac"),
                    44_100L * 100, 44_100, Map.of("ALBUMARTIST", "Pink Floyd"));

            assertThat(EtiquettesDuFichier.lire(fichier).artiste()).contains("Pink Floyd");
        }
    }
}
