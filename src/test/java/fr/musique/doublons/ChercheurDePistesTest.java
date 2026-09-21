package fr.musique.doublons;

import static fr.musique.doublons.AlbumsDEssai.albumDePistes;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Éprouve la recherche des morceaux présents dans plusieurs dossiers. */
class ChercheurDePistesTest {

    @Test
    @DisplayName("le même morceau dans deux dossiers est signalé, le meilleur format d'abord")
    void leMemeMorceauDansDeuxDossiers() {
        List<Album> albums = List.of(
                albumDePistes("/musique/Daft Punk/Discovery", 30_000_000L,
                        "01 - One More Time.flac", "02 - Aerodynamic.flac"),
                albumDePistes("/telechargements/Divers", 8_000_000L,
                        "Daft Punk - One More Time.mp3"));

        List<GroupeDePistes> groupes = ChercheurDePistes.chercher(albums, Set.of());

        assertThat(groupes).hasSize(1);
        assertThat(groupes.get(0).celleAGarder().nom().extension()).isEqualTo("flac");
        assertThat(groupes.get(0).placeRecuperable()).isEqualTo(8_000_000L);
        assertThat(groupes.get(0).motif()).contains("one more time");
    }

    @Test
    @DisplayName("deux pistes du même album ne se doublent pas l'une l'autre")
    void deuxPistesDuMemeAlbumNeComptentPas() {
        List<Album> albums = List.of(
                albumDePistes("/musique/Muse/Absolution", 9_000_000L,
                        "01 - Muse - Hysteria.mp3", "02 - Muse - Hysteria.mp3"));

        assertThat(ChercheurDePistes.chercher(albums, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("un dossier déjà signalé comme album en double est laissé de côté")
    void lAlbumDejaSignaleEstIgnore() {
        List<Album> albums = List.of(
                albumDePistes("/musique/Daft Punk/Discovery", 30_000_000L,
                        "01 - One More Time.flac"),
                albumDePistes("/sauvegarde/Daft Punk/Discovery", 8_000_000L,
                        "01 - One More Time.mp3"));

        assertThat(ChercheurDePistes.chercher(
                albums, Set.of(Path.of("/sauvegarde/Daft Punk/Discovery")))).isEmpty();
    }

    @Test
    @DisplayName("un fichier sans titre lisible n'est comparé à rien")
    void leFichierSansTitreNEstPasCompare() {
        List<Album> albums = List.of(
                albumDePistes("/musique/Muse/Absolution", 9_000_000L, "01.mp3", "02.mp3"),
                albumDePistes("/musique/Muse/Black Holes", 9_000_000L, "01.mp3", "02.mp3"));

        assertThat(ChercheurDePistes.chercher(albums, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("un morceau sans artiste connu n'est comparé à rien")
    void leMorceauSansArtisteNEstPasCompare() {
        List<Album> albums = List.of(
                albumDePistes("/Cendrillon.mp3 est ailleurs/Inconnu", 9_000_000L,
                        "Cendrillon.mp3"),
                albumDePistes("/Autre/Inconnu", 9_000_000L, "Cendrillon.mp3"));

        assertThat(ChercheurDePistes.chercher(albums, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("un morceau qui circule dans toute la discothèque n'est pas un gaspillage")
    void leMorceauTropRepanduEstEcarte() {
        List<Album> albums = List.of(
                albumDePistes("/musique/Téléphone/Album 1", 9_000_000L, "01 - Cendrillon.mp3"),
                albumDePistes("/musique/Téléphone/Album 2", 9_000_000L, "01 - Cendrillon.mp3"),
                albumDePistes("/musique/Téléphone/Album 3", 9_000_000L, "01 - Cendrillon.mp3"),
                albumDePistes("/musique/Téléphone/Album 4", 9_000_000L, "01 - Cendrillon.mp3"),
                albumDePistes("/musique/Téléphone/Album 5", 9_000_000L, "01 - Cendrillon.mp3"),
                albumDePistes("/musique/Téléphone/Album 6", 9_000_000L, "01 - Cendrillon.mp3"),
                albumDePistes("/musique/Téléphone/Album 7", 9_000_000L, "01 - Cendrillon.mp3"));

        assertThat(ChercheurDePistes.chercher(albums, Set.of())).isEmpty();
    }
}
