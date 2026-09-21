package fr.musique.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Éprouve le lecteur de conteneurs ISO sur ses formes particulières.
 *
 * <p>Le format autorise deux écritures de longueur, des étiquettes textuelles ou numériques, et
 * des atomes qu'on ne trouve pas. Chacune de ces formes est présente dans une bibliothèque
 * ordinaire, et aucune ne doit interrompre la lecture.
 */
class Mp4FormesTest {

    @TempDir
    private Path racine;

    private static byte[] ascii(String texte) {
        return texte.getBytes(StandardCharsets.US_ASCII);
    }

    /** Un atome dont la longueur est écrite sur les huit octets qui suivent son nom. */
    private static byte[] atomeALongueEtendue(String nom, byte[] contenu) {
        byte[] longueur = new byte[8];
        long totale = contenu.length + 16L;
        for (int rang = 0; rang < 8; rang++) {
            longueur[7 - rang] = (byte) ((totale >> (8 * rang)) & 0xFF);
        }
        return FichiersDEssai.concatener(
                new byte[] {0, 0, 0, 1},
                ascii(nom),
                longueur,
                contenu);
    }

    @Test
    @DisplayName("un atome à longueur étendue est traversé comme les autres")
    void lAtomeALongueurEtendueEstTraverse() throws IOException {
        byte[] entete = new byte[100];
        entete[15] = 1;
        entete[19] = 100;
        byte[] moov = atomeALongueEtendue("moov", FichiersDEssai.atome("mvhd", entete));

        Path fichier = FichiersDEssai.octets(racine.resolve("etendu.m4a"), moov);

        assertThat(EtiquettesDuFichier.lire(fichier).secondes()).hasValue(100.0);
    }

    @Test
    @DisplayName("un numéro de piste écrit en nombre est lu comme un nombre")
    void leNumeroEnNombreEstLu() throws IOException {
        byte[] valeur = FichiersDEssai.concatener(
                new byte[] {0, 0, 0, 0},
                new byte[] {0, 0, 0, 0},
                new byte[] {0, 0, 0, 7, 0, 14});
        byte[] liste = FichiersDEssai.atome("ilst",
                FichiersDEssai.atome("trkn", FichiersDEssai.atome("data", valeur)));
        byte[] moov = FichiersDEssai.atome("moov",
                FichiersDEssai.atome("udta",
                        FichiersDEssai.atome("meta",
                                FichiersDEssai.concatener(new byte[4], liste))));

        Path fichier = FichiersDEssai.octets(racine.resolve("piste.m4a"), moov);

        assertThat(EtiquettesDuFichier.lire(fichier).numero()).hasValue(7);
    }

    @Test
    @DisplayName("une étiquette sans valeur ne fait rien échouer")
    void lEtiquetteSansValeurNeFaitRienEchouer() throws IOException {
        byte[] liste = FichiersDEssai.atome("ilst",
                FichiersDEssai.atome("trkn", ascii("rien")));
        byte[] moov = FichiersDEssai.atome("moov",
                FichiersDEssai.atome("udta",
                        FichiersDEssai.atome("meta",
                                FichiersDEssai.concatener(new byte[4], liste))));

        Path fichier = FichiersDEssai.octets(racine.resolve("creux.m4a"), moov);

        assertThat(EtiquettesDuFichier.lire(fichier).numero()).isEmpty();
    }

    @Test
    @DisplayName("un conteneur sans étiquettes rend quand même sa durée")
    void leConteneurSansEtiquettesRendSaDuree() throws IOException {
        byte[] entete = new byte[100];
        entete[15] = 1;
        entete[19] = 42;
        byte[] moov = FichiersDEssai.atome("moov", FichiersDEssai.atome("mvhd", entete));

        Path fichier = FichiersDEssai.octets(racine.resolve("sansetiquette.m4a"), moov);

        Etiquette etiquette = EtiquettesDuFichier.lire(fichier);

        assertThat(etiquette.secondes()).hasValue(42.0);
        assertThat(etiquette.artiste()).isEmpty();
    }

    @Test
    @DisplayName("un conteneur dont les métadonnées sont incomplètes rend ce qu'il peut")
    void lesMetadonneesIncompletesNInterrompentRien() throws IOException {
        byte[] entete = new byte[100];
        entete[15] = 1;
        entete[19] = 30;
        byte[] moov = FichiersDEssai.atome("moov", FichiersDEssai.concatener(
                FichiersDEssai.atome("mvhd", entete),
                FichiersDEssai.atome("udta", FichiersDEssai.atome("meta", new byte[8]))));

        Path fichier = FichiersDEssai.octets(racine.resolve("partiel.m4a"), moov);

        assertThat(EtiquettesDuFichier.lire(fichier).secondes()).hasValue(30.0);
    }

    @Test
    @DisplayName("un dernier atome de longueur nulle court jusqu'à la fin du fichier")
    void lAtomeDeLongueurNulleVaJusquALaFin() throws IOException {
        byte[] entete = new byte[100];
        entete[15] = 1;
        entete[19] = 60;
        byte[] contenu = FichiersDEssai.atome("mvhd", entete);
        byte[] moov = FichiersDEssai.concatener(
                new byte[] {0, 0, 0, 0},
                ascii("moov"),
                contenu);

        Path fichier = FichiersDEssai.octets(racine.resolve("ouvert.m4a"), moov);

        assertThat(EtiquettesDuFichier.lire(fichier).secondes()).hasValue(60.0);
    }
}
