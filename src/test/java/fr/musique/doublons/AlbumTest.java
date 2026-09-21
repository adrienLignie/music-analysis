package fr.musique.doublons;

import static fr.musique.doublons.AlbumsDEssai.album;
import static fr.musique.doublons.AlbumsDEssai.albumDePistes;
import static fr.musique.doublons.AlbumsDEssai.albumNumerote;
import static fr.musique.doublons.AlbumsDEssai.piste;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Éprouve ce qu'un album sait dire de lui-même. */
class AlbumTest {

    @Test
    @DisplayName("le format dominant est celui qui pèse le plus lourd, pas le plus fréquent")
    void leFormatDominantEstCeluiQuiPese() {
        Album album = Album.de(
                Path.of("/musique/Muse/Absolution"),
                List.of(
                        piste("/musique/Muse/Absolution/01 - Apocalypse Please.flac", 30_000_000L),
                        piste("/musique/Muse/Absolution/02 - Time Is Running Out.mp3", 9_000_000L),
                        piste("/musique/Muse/Absolution/03 - Sing For Absolution.mp3", 9_000_000L)),
                album("/musique/Muse/Absolution", "flac", 1L, 1).nom());

        assertThat(album.formatDominant()).isEqualTo("flac");
        assertThat(album.estSansPerte()).isTrue();
        assertThat(album.taille()).isEqualTo(48_000_000L);
    }

    @Test
    @DisplayName("un album vide n'a pas de format et ne pèse rien")
    void lAlbumVideNAPasDeFormat() {
        Album vide = albumDePistes("/musique/Muse/Absolution", 0L);

        assertThat(vide.formatDominant()).isEmpty();
        assertThat(vide.poidsMoyenDUnePiste()).isZero();
        assertThat(vide.nombreDePistes()).isZero();
    }

    @Test
    @DisplayName("les pistes atteintes par un second chemin ne pèsent pas deux fois")
    void lesRepetitionsNePesentPas() {
        Album album = album("/musique/Muse/Absolution", "flac", 10_000_000L, 4);
        Set<Path> repetitions = Set.of(
                album.pistes().get(0).chemin(), album.pistes().get(1).chemin());

        Album allege = Album.de(
                album.dossier(), album.pistes(), album.nom(), repetitions);

        assertThat(allege.nombreDePistes()).isEqualTo(4);
        assertThat(allege.taille()).isEqualTo(20_000_000L);
    }

    @Test
    @DisplayName("les numéros sont rendus disque par disque")
    void lesNumerosSontRendusParDisque() {
        Album coffret = albumDePistes("/musique/Muse/Coffret", 10_000_000L,
                "1-01 - Titre.flac", "1-02 - Titre.flac", "2-01 - Titre.flac");

        assertThat(coffret.disques()).containsExactly(OptionalInt.of(1), OptionalInt.of(2));
        assertThat(coffret.numerosDuDisque(OptionalInt.of(1))).containsExactly(1, 2);
        assertThat(coffret.numerosDuDisque(OptionalInt.of(2))).containsExactly(1);
    }

    @Test
    @DisplayName("un album sans numéro de disque n'en a qu'un")
    void lAlbumSimpleNAQuUnDisque() {
        Album simple = albumNumerote("/musique/Muse/Absolution", "flac", 10_000_000L, 1, 2, 3);

        assertThat(simple.disques()).containsExactly(OptionalInt.empty());
        assertThat(simple.numerosDuDisque(OptionalInt.empty())).containsExactly(1, 2, 3);
        assertThat(simple.poidsMoyenDUnePiste()).isEqualTo(10_000_000L);
    }
}
