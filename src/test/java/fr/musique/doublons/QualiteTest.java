package fr.musique.doublons;

import static fr.musique.doublons.AlbumsDEssai.album;
import static fr.musique.doublons.AlbumsDEssai.albumNumerote;
import static org.assertj.core.api.Assertions.assertThat;

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

/** Éprouve le choix de l'exemplaire à garder. */
class QualiteTest {

    /** Étiquettes qui ne portent qu'une durée, la même pour toutes les pistes de ces albums. */
    private static Etiquettes durees(double secondesParPiste, Album... albums) {
        Map<Path, Etiquette> table = new HashMap<>();
        for (Album album : albums) {
            for (Piste piste : album.pistes()) {
                table.put(piste.chemin(), new Etiquette(
                        OptionalDouble.of(secondesParPiste), Optional.empty(), Optional.empty(),
                        Optional.empty(), OptionalInt.empty(), OptionalInt.empty()));
            }
        }
        return Etiquettes.de(table);
    }

    private static Album meilleur(List<Album> albums, Etiquettes etiquettes) {
        return albums.stream().sorted(Qualite.meilleurDAbord(etiquettes, albums)).toList().get(0);
    }

    @Test
    @DisplayName("l'album complet passe devant l'album amputé, quel que soit son format")
    void laCompletudePasseAvantLeFormat() {
        Album complet = album("/b/Muse/Absolution", "mp3", 9_000_000L, 14);
        Album ampute = album("/a/Muse/Absolution", "flac", 30_000_000L, 4);

        assertThat(meilleur(List.of(ampute, complet), Etiquettes.aucune())).isEqualTo(complet);
    }

    @Test
    @DisplayName("à complétude égale, le sans perte l'emporte")
    void leSansPerteLEmporte() {
        Album flac = album("/a/Muse/Absolution", "flac", 30_000_000L, 14);
        Album mp3 = album("/b/Muse/Absolution", "mp3", 9_000_000L, 14);

        assertThat(meilleur(List.of(mp3, flac), Etiquettes.aucune())).isEqualTo(flac);
    }

    @Test
    @DisplayName("à format égal, le débit annoncé départage")
    void leDebitAnnonceDepartage() {
        Album meilleur = album("/a/Muse/Absolution [320]", "mp3", 9_000_000L, 14);
        Album moindre = album("/b/Muse/Absolution [128]", "mp3", 9_500_000L, 14);

        assertThat(meilleur(List.of(moindre, meilleur), Etiquettes.aucune())).isEqualTo(meilleur);
    }

    @Test
    @DisplayName("une piste d'écart ne fait pas un album amputé")
    void unePisteDEcartNeDisqualifiePas() {
        Album flac = albumNumerote("/a/Muse/Absolution", "flac", 30_000_000L,
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
        Album mp3 = album("/b/Muse/Absolution", "mp3", 9_000_000L, 14);

        assertThat(meilleur(List.of(mp3, flac), Etiquettes.aucune())).isEqualTo(flac);
    }

    @Test
    @DisplayName("le débit mesuré l'emporte sur ce que le nom annonce")
    void leDebitMesureCorrigeLAnnonce() {
        Album annonce = album("/a/Muse/Absolution [320]", "mp3", 3_000_000L, 10);
        Album honnete = album("/b/Muse/Absolution", "mp3", 9_000_000L, 10);

        assertThat(meilleur(List.of(annonce, honnete), durees(300, annonce, honnete)))
                .isEqualTo(honnete);
    }

    @Test
    @DisplayName("le débit mesuré est la moyenne des pistes qu'on a su lire")
    void leDebitMesureEstUneMoyenne() {
        Album album = album("/a/Muse/Absolution", "mp3", 9_000_000L, 10);

        assertThat(Qualite.debitMesure(album, durees(300, album))).hasValue(30_000L);
        assertThat(Qualite.debitMesure(album, Etiquettes.aucune())).isEmpty();
    }

    @Test
    @DisplayName("à égalité de tout, le chemin départage pour que l'ordre soit reproductible")
    void leCheminDepartageEnDernier() {
        Album premier = album("/a/Muse/Absolution", "mp3", 9_000_000L, 10);
        Album second = album("/b/Muse/Absolution", "mp3", 9_000_000L, 10);

        assertThat(meilleur(List.of(second, premier), Etiquettes.aucune())).isEqualTo(premier);
    }
}
