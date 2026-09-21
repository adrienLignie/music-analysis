package fr.musique.doublons;

import static fr.musique.doublons.AlbumsDEssai.album;
import static fr.musique.doublons.AlbumsDEssai.albumSousUneRacine;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Éprouve les clés sous lesquelles un album peut être retrouvé.
 *
 * <p>Un rapprochement ne se fait jamais par ressemblance mais par égalité de l'une de ces clés.
 * Les éprouver séparément, c'est vérifier qu'aucune n'est ni trop large ni trop étroite.
 */
class ClesDeRapprochementTest {

    private final ChercheurDeDoublons chercheur = new ChercheurDeDoublons();

    @Test
    @DisplayName("l'article initial ne sépare pas deux rangements du même album")
    void lArticleNeSeparePas() {
        List<Album> albums = List.of(
                album("/musique/Pink Floyd/The Wall (1979)", "flac", 30_000_000L, 12),
                album("/sauvegarde/Pink Floyd/Wall (1979)", "mp3", 9_000_000L, 12));

        assertThat(chercheur.chercher(albums)).hasSize(1);
    }

    @Test
    @DisplayName("un titre alternatif entre parenthèses relie deux rangements")
    void leTitreAlternatifRelie() {
        List<Album> albums = List.of(
                album("/musique/Rammstein/Sehnsucht (Nostalgie Profonde)", "flac",
                        30_000_000L, 11),
                album("/sauvegarde/Rammstein/Nostalgie Profonde", "mp3", 9_000_000L, 11));

        assertThat(chercheur.chercher(albums)).hasSize(1);
    }

    @Test
    @DisplayName("deux compilations du même nom se rejoignent entre elles")
    void lesCompilationsSeRejoignent() {
        List<Album> albums = List.of(
                album("/musique/Various Artists/Nova Tunes 12", "flac", 30_000_000L, 18),
                album("/sauvegarde/Various Artists/Nova Tunes 12", "mp3", 9_000_000L, 18));

        List<GroupeDeDoublons> groupes = chercheur.chercher(albums);

        assertThat(groupes).hasSize(1);
        assertThat(groupes.get(0).motif()).contains("compilation");
    }

    @Test
    @DisplayName("un album sans artiste rejoint celui qui en a un, avec une confiance moindre")
    void lAlbumSansArtisteRejointParLeTitre() {
        List<Album> albums = List.of(
                album("/musique/Muse/Absolution (2003)", "flac", 30_000_000L, 14),
                albumSousUneRacine("/musique/Absolution", "mp3", 9_000_000L, 14));

        List<GroupeDeDoublons> groupes = chercheur.chercher(albums);

        assertThat(groupes).hasSize(1);
        assertThat(groupes.get(0).confiance()).isEqualTo(NiveauDeConfiance.MOYENNE);
        assertThat(groupes.get(0).motif()).contains("sans artiste");
    }

    @Test
    @DisplayName("le rapprochement est transitif : trois rangements ne font qu'un groupe")
    void leRapprochementEstTransitif() {
        List<Album> albums = List.of(
                album("/a/Muse/Absolution (2003)", "flac", 30_000_000L, 14),
                albumSousUneRacine("/b/Absolution", "mp3", 9_000_000L, 14),
                album("/c/Muse/Absolution (2003)", "mp3", 8_000_000L, 14));

        List<GroupeDeDoublons> groupes = chercheur.chercher(albums);

        assertThat(groupes).hasSize(1);
        assertThat(groupes.get(0).albums()).hasSize(3);
    }

    @Test
    @DisplayName("un dossier sans artiste ne fait pas pont entre deux artistes différents")
    void leDossierSansArtisteNeFaitPasPont() {
        List<Album> albums = List.of(
                album("/a/Muse/Absolution (2003)", "flac", 30_000_000L, 14),
                albumSousUneRacine("/b/Absolution", "mp3", 9_000_000L, 14),
                album("/c/Bob Dylan/Absolution (2003)", "mp3", 8_000_000L, 14));

        // Aucun groupe : les deux artistes nommés ne se rapprochent pas, et le dossier qui ne
        // nomme personne est réclamé par les deux. Le rattacher à l'un ferait proposer la
        // suppression d'un album de l'autre.
        assertThat(chercheur.chercher(albums)).isEmpty();
    }

    @Test
    @DisplayName("deux dossiers sans artiste suivent le même sort, réclamés par deux artistes")
    void lesDossiersSansArtisteSuiventLeMemeSort() {
        List<Album> albums = List.of(
                album("/a/Muse/Absolution (2003)", "flac", 30_000_000L, 14),
                albumSousUneRacine("/b/Absolution", "mp3", 9_000_000L, 14),
                albumSousUneRacine("/c/Absolution", "mp3", 8_000_000L, 14),
                album("/d/Bob Dylan/Absolution (2003)", "mp3", 7_000_000L, 14));

        // Les deux dossiers anonymes sont le même album l'un de l'autre : ils restent réunis
        // entre eux, et c'est en tant que classe qu'ils sont laissés à l'écart des deux artistes.
        List<GroupeDeDoublons> groupes = chercheur.chercher(albums);

        assertThat(groupes).hasSize(1);
        assertThat(groupes.get(0).albums()).extracting(Album::dossier)
                .containsExactlyInAnyOrder(Path.of("/b/Absolution"), Path.of("/c/Absolution"));
    }

    @Test
    @DisplayName("un dossier seul ne forme jamais un groupe")
    void leDossierSeulNeFormeRien() {
        assertThat(chercheur.chercher(
                List.of(album("/musique/Muse/Absolution", "flac", 30_000_000L, 14)))).isEmpty();
        assertThat(chercheur.chercher(List.of())).isEmpty();
    }
}
