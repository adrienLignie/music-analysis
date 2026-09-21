package fr.musique.rapport;

import static fr.musique.doublons.AlbumsDEssai.album;
import static fr.musique.doublons.AlbumsDEssai.albumDePistes;
import static fr.musique.doublons.AlbumsDEssai.albumNumerote;
import static org.assertj.core.api.Assertions.assertThat;

import fr.musique.doublons.Album;
import fr.musique.doublons.Piste;
import fr.musique.media.Etiquette;
import fr.musique.media.Etiquettes;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Éprouve la détection des albums à trous. */
class AlbumsIncompletsTest {

    @Test
    @DisplayName("un trou dans la numérotation est signalé, numéros à l'appui")
    void leTrouEstSignale() {
        Album ampute = albumNumerote("/musique/Queen/A Night At The Opera", "mp3", 9_000_000L,
                1, 2, 3, 7, 8);

        List<AlbumsIncomplets.Incomplet> trouves = AlbumsIncomplets.chercher(List.of(ampute));

        assertThat(trouves).hasSize(1);
        assertThat(trouves.get(0).attendues()).isEqualTo(8);
        assertThat(trouves.get(0).manquants()).containsExactly(4, 5, 6);
    }

    @Test
    @DisplayName("un album complet n'est pas signalé")
    void lAlbumCompletNEstPasSignale() {
        assertThat(AlbumsIncomplets.chercher(
                List.of(album("/musique/Muse/Absolution", "flac", 30_000_000L, 14)))).isEmpty();
    }

    @Test
    @DisplayName("chaque disque d'un coffret est examiné à part")
    void chaqueDisqueEstExamineAPart() {
        Album coffret = albumDePistes("/musique/Muse/Coffret", 10_000_000L,
                "1-01 - Titre.flac", "1-02 - Titre.flac", "1-03 - Titre.flac",
                "1-04 - Titre.flac",
                "2-01 - Titre.flac", "2-02 - Titre.flac", "2-03 - Titre.flac",
                "2-04 - Titre.flac");

        assertThat(AlbumsIncomplets.chercher(List.of(coffret))).isEmpty();
    }

    @Test
    @DisplayName("un dossier trop petit ne prouve rien")
    void leDossierTropPetitNeProuveRien() {
        Album maxi = albumNumerote("/musique/Muse/Maxi", "mp3", 9_000_000L, 2, 3);

        assertThat(AlbumsIncomplets.chercher(List.of(maxi))).isEmpty();
    }

    @Test
    @DisplayName("un dossier dont les fichiers ne portent pas de numéro n'est pas jugé")
    void leDossierSansNumeroNEstPasJuge() {
        Album sansNumero = albumDePistes("/musique/Muse/Absolution", 9_000_000L,
                "Hysteria.mp3", "Time Is Running Out.mp3", "Sing For Absolution.mp3",
                "Butterflies And Hurricanes.mp3");

        assertThat(AlbumsIncomplets.chercher(List.of(sansNumero))).isEmpty();
    }

    @Test
    @DisplayName("le numéro de l'étiquette l'emporte sur celui du nom de fichier")
    void leNumeroDeLEtiquetteLEmporte() {
        Album renomme = albumDePistes("/musique/Queen/A Night At The Opera", 9_000_000L,
                "a.mp3", "b.mp3", "c.mp3", "d.mp3");
        Map<Path, Etiquette> table = new HashMap<>();
        int[] numeros = {1, 2, 3, 6};
        List<Piste> pistes = renomme.pistes();
        for (int rang = 0; rang < pistes.size(); rang++) {
            table.put(pistes.get(rang).chemin(), new Etiquette(
                    OptionalDouble.of(200), Optional.empty(), Optional.empty(), Optional.empty(),
                    OptionalInt.of(numeros[rang]), OptionalInt.empty()));
        }

        List<AlbumsIncomplets.Incomplet> trouves =
                AlbumsIncomplets.chercher(List.of(renomme), Etiquettes.de(table));

        assertThat(trouves).hasSize(1);
        assertThat(trouves.get(0).manquants()).containsExactly(4, 5);
    }

    @Test
    @DisplayName("les albums les plus troués viennent en premier")
    void lesPlusTrouesDAbord() {
        Album troisTrous = albumNumerote("/musique/A/Album", "mp3", 9_000_000L, 1, 2, 3, 7, 8);
        Album unTrou = albumNumerote("/musique/B/Album", "mp3", 9_000_000L, 1, 2, 3, 5);

        List<AlbumsIncomplets.Incomplet> trouves =
                AlbumsIncomplets.chercher(List.of(unTrou, troisTrous));

        assertThat(trouves).hasSize(2);
        assertThat(trouves.get(0).album()).isEqualTo(troisTrous);
    }
}
