package fr.musique.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Éprouve la lecture d'octets à une position donnée, sur laquelle tous les lecteurs reposent. */
class FenetreTest {

    @TempDir
    private Path racine;

    private Fenetre fenetreSur(byte[] contenu) throws IOException {
        Path fichier = racine.resolve("essai.bin");
        Files.write(fichier, contenu);
        SeekableByteChannel canal = Files.newByteChannel(fichier);
        return new Fenetre(canal);
    }

    @Test
    @DisplayName("les entiers se lisent dans les deux sens et sur la longueur demandée")
    void lesEntiersSeLisentDansLesDeuxSens() throws IOException {
        Fenetre fenetre = fenetreSur(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08});

        assertThat(fenetre.taille()).isEqualTo(8);
        assertThat(fenetre.entierGrosBoutien(0, 2)).isEqualTo(0x0102);
        assertThat(fenetre.entierPetitBoutien(0)).isEqualTo(0x04030201L);
        assertThat(fenetre.entierPetitBoutien(0, 2)).isEqualTo(0x0201);
    }

    @Test
    @DisplayName("une étiquette est lue caractère par caractère, sur la longueur demandée")
    void lEtiquetteEstLueTelleQuelle() throws IOException {
        Fenetre fenetre = fenetreSur("OggSOpus".getBytes(StandardCharsets.US_ASCII));

        assertThat(fenetre.etiquette(0)).isEqualTo("OggS");
        assertThat(fenetre.etiquette(4, 4)).isEqualTo("Opus");
    }

    @Test
    @DisplayName("une lecture au-delà de la fin est refusée plutôt que devinée")
    void laLectureAuDelaEstRefusee() throws IOException {
        Fenetre fenetre = fenetreSur(new byte[] {1, 2, 3, 4});

        assertThatExceptionOfType(EOFException.class)
                .isThrownBy(() -> fenetre.lire(2, 8));
        assertThatExceptionOfType(EOFException.class)
                .isThrownBy(() -> fenetre.lire(-1, 2));
    }

    @Test
    @DisplayName("la recherche part du début ou de la fin, selon ce qu'on cherche")
    void laRechercheVaDansLesDeuxSens() throws IOException {
        Fenetre fenetre = fenetreSur("OggS...OggS...".getBytes(StandardCharsets.US_ASCII));
        byte[] motif = "OggS".getBytes(StandardCharsets.US_ASCII);

        assertThat(fenetre.chercher(motif, 0, 14, false)).isZero();
        assertThat(fenetre.chercher(motif, 0, 14, true)).isEqualTo(7);
    }

    @Test
    @DisplayName("une recherche qui ne peut aboutir rend une position négative")
    void laRechercheImpossibleRendMoinsUn() throws IOException {
        Fenetre fenetre = fenetreSur("OggS".getBytes(StandardCharsets.US_ASCII));
        byte[] motif = "OggS".getBytes(StandardCharsets.US_ASCII);

        assertThat(fenetre.chercher(new byte[0], 0, 4, false)).isEqualTo(-1);
        assertThat(fenetre.chercher(motif, -1, 4, false)).isEqualTo(-1);
        assertThat(fenetre.chercher(motif, 4, 4, false)).isEqualTo(-1);
        assertThat(fenetre.chercher(motif, 2, 2, false)).isEqualTo(-1);
        assertThat(fenetre.chercher("Xing".getBytes(StandardCharsets.US_ASCII), 0, 4, false))
                .isEqualTo(-1);
    }
}
