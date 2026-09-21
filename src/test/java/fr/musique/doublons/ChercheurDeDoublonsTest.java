package fr.musique.doublons;

import static fr.musique.doublons.AlbumsDEssai.album;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Éprouve le regroupement des albums, et surtout ce qu'il refuse de regrouper.
 *
 * <p>Les cas qui séparent comptent plus que ceux qui réunissent : un doublon manqué coûte quelques
 * gigaoctets, un faux doublon peut coûter un disque.
 */
class ChercheurDeDoublonsTest {

    private final ChercheurDeDoublons chercheur = new ChercheurDeDoublons();

    @Nested
    @DisplayName("Doublons à trouver")
    class Doublons {

        @Test
        @DisplayName("le même album en deux formats, c'est un doublon, et l'on garde le sans perte")
        void lesDeuxFormatsDuMemeAlbum() {
            List<Album> albums = List.of(
                    album("/musique/Daft Punk/Discovery (2001)", "flac", 30_000_000L, 14),
                    album("/sauvegarde/Daft Punk/Discovery (2001)", "mp3", 8_000_000L, 14));

            List<GroupeDeDoublons> groupes = chercheur.chercher(albums);

            assertThat(groupes).hasSize(1);
            assertThat(groupes.get(0).confiance()).isEqualTo(NiveauDeConfiance.FORTE);
            assertThat(groupes.get(0).celuiAGarder().formatDominant()).isEqualTo("flac");
            assertThat(groupes.get(0).placeRecuperable()).isEqualTo(14 * 8_000_000L);
            assertThat(groupes.get(0).motif()).contains("formats différents");
        }

        @Test
        @DisplayName("deux rangements différents du même album se rejoignent par le titre")
        void deuxRangementsSeRejoignent() {
            List<Album> albums = List.of(
                    album("/musique/Muse/Absolution (2003)", "flac", 30_000_000L, 14),
                    album("/musique/Rock/Muse - Absolution", "mp3", 9_000_000L, 14));

            assertThat(chercheur.chercher(albums)).hasSize(1);
        }

        @Test
        @DisplayName("une réédition est un doublon, mais qu'il faut regarder")
        void laReeditionDescendDUnCran() {
            List<Album> albums = List.of(
                    album("/musique/Pink Floyd/The Wall (1979)", "flac", 30_000_000L, 12),
                    album("/musique/Pink Floyd/The Wall (2011)", "flac", 31_000_000L, 12));

            List<GroupeDeDoublons> groupes = chercheur.chercher(albums);

            assertThat(groupes).hasSize(1);
            assertThat(groupes.get(0).confiance()).isEqualTo(NiveauDeConfiance.MOYENNE);
            assertThat(groupes.get(0).motif()).contains("réédition ?");
        }

        @Test
        @DisplayName("un an d'écart ne fait pas une réédition")
        void unAnDEcartNeComptePas() {
            List<Album> albums = List.of(
                    album("/musique/Muse/Absolution (2003)", "flac", 30_000_000L, 14),
                    album("/musique/Muse/Absolution (2004)", "flac", 30_000_000L, 14));

            assertThat(chercheur.chercher(albums).get(0).confiance())
                    .isEqualTo(NiveauDeConfiance.FORTE);
        }

        @Test
        @DisplayName("un album amputé reste un doublon, signalé comme tel")
        void lAlbumAmputeResteUnDoublon() {
            List<Album> albums = List.of(
                    album("/musique/Muse/Absolution (2003)", "flac", 30_000_000L, 14),
                    album("/telechargements/Muse - Absolution", "flac", 30_000_000L, 4));

            List<GroupeDeDoublons> groupes = chercheur.chercher(albums);

            assertThat(groupes).hasSize(1);
            assertThat(groupes.get(0).confiance()).isEqualTo(NiveauDeConfiance.MOYENNE);
            assertThat(groupes.get(0).motif()).contains("14 pistes / 4 pistes");
            assertThat(groupes.get(0).celuiAGarder().nombreDePistes()).isEqualTo(14);
        }

        @Test
        @DisplayName("l'édition de luxe et l'édition simple sont rapprochées")
        void lEditionDeLuxeRejointLEditionSimple() {
            List<Album> albums = List.of(
                    album("/musique/Adele/21 (2011)", "mp3", 9_000_000L, 12),
                    album("/musique/Adele/21 (Deluxe Edition)", "mp3", 9_000_000L, 12));

            List<GroupeDeDoublons> groupes = chercheur.chercher(albums);

            assertThat(groupes).hasSize(1);
            assertThat(groupes.get(0).motif()).contains("éditions différentes");
        }

        @Test
        @DisplayName("les groupes sont triés par place récupérable décroissante")
        void lesGroupesSontTriesParGain() {
            List<Album> albums = List.of(
                    album("/a/Muse/Absolution (2003)", "flac", 30_000_000L, 10),
                    album("/b/Muse/Absolution (2003)", "flac", 30_000_000L, 10),
                    album("/a/Adele/21 (2011)", "mp3", 9_000_000L, 10),
                    album("/b/Adele/21 (2011)", "mp3", 9_000_000L, 10));

            List<GroupeDeDoublons> groupes = chercheur.chercher(albums);

            assertThat(groupes).hasSize(2);
            assertThat(groupes.get(0).placeRecuperable())
                    .isGreaterThan(groupes.get(1).placeRecuperable());
        }
    }

    @Nested
    @DisplayName("Ce qui ne doit jamais être rapproché")
    class Separations {

        @Test
        @DisplayName("un album acoustique n'est pas l'album studio du même nom")
        void laVersionNEstPasLAlbum() {
            List<Album> albums = List.of(
                    album("/musique/Nirvana/Nevermind (1991)", "flac", 30_000_000L, 12),
                    album("/musique/Nirvana/Nevermind (Live) (1992)", "flac", 30_000_000L, 12));

            assertThat(chercheur.chercher(albums)).isEmpty();
        }

        @Test
        @DisplayName("deux volumes d'une même série restent deux albums")
        void deuxVolumesRestentSepares() {
            List<Album> albums = List.of(
                    album("/musique/Queen/Greatest Hits Vol. 1", "mp3", 9_000_000L, 17),
                    album("/musique/Queen/Greatest Hits Vol. 2", "mp3", 9_000_000L, 17));

            assertThat(chercheur.chercher(albums)).isEmpty();
        }

        @Test
        @DisplayName("deux artistes différents ne partagent pas un titre d'album")
        void deuxArtistesNePartagentPasUnTitre() {
            List<Album> albums = List.of(
                    album("/musique/Weezer/Weezer (1994)", "flac", 30_000_000L, 10),
                    album("/musique/Metallica/Metallica (1991)", "flac", 30_000_000L, 12),
                    album("/musique/Ramones/Ramones (1976)", "flac", 30_000_000L, 14));

            assertThat(chercheur.chercher(albums)).isEmpty();
        }

        @Test
        @DisplayName("les « Best Of » de toute une discothèque ne forment pas un groupe")
        void lesTitresGeneriquesNeSeRejoignentPas() {
            List<Album> albums = List.of(
                    album("/musique/Téléphone/Best Of", "mp3", 9_000_000L, 15),
                    album("/musique/Indochine/Best Of", "mp3", 9_000_000L, 15),
                    album("/musique/Noir Désir/Best Of", "mp3", 9_000_000L, 15));

            assertThat(chercheur.chercher(albums)).isEmpty();
        }

        @Test
        @DisplayName("une compilation ne rejoint pas l'album homonyme d'un artiste")
        void laCompilationResteAPart() {
            List<Album> albums = List.of(
                    album("/musique/Various Artists/Nevermind (2015)", "mp3", 9_000_000L, 12),
                    album("/musique/Nirvana/Nevermind (1991)", "flac", 30_000_000L, 12));

            assertThat(chercheur.chercher(albums)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Groupes trop nombreux")
    class Soupcon {

        @Test
        @DisplayName("au-delà de quatre exemplaires, le groupe n'est plus présenté comme sûr")
        void leGroupeTropNombreuxEstAVerifier() {
            List<Album> albums = List.of(
                    album("/a/Muse/Absolution (2003)", "flac", 10_000_000L, 10),
                    album("/b/Muse/Absolution (2003)", "flac", 10_000_000L, 10),
                    album("/c/Muse/Absolution (2003)", "flac", 10_000_000L, 10),
                    album("/d/Muse/Absolution (2003)", "flac", 10_000_000L, 10),
                    album("/e/Muse/Absolution (2003)", "flac", 10_000_000L, 10));

            List<GroupeDeDoublons> groupes = chercheur.chercher(albums);

            assertThat(groupes).hasSize(1);
            assertThat(groupes.get(0).confiance()).isEqualTo(NiveauDeConfiance.A_VERIFIER);
            assertThat(groupes.get(0).motif()).contains("5 exemplaires");
        }
    }
}
