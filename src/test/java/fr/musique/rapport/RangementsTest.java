package fr.musique.rapport;

import static fr.musique.doublons.AlbumsDEssai.album;
import static fr.musique.doublons.AlbumsDEssai.albumDePistes;
import static fr.musique.doublons.AlbumsDEssai.albumSousUneRacine;
import static org.assertj.core.api.Assertions.assertThat;

import fr.musique.doublons.Album;
import fr.musique.scan.Inventaire;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Éprouve la détection des rangements qui empêchent de reconnaître un album. */
class RangementsTest {

    private static Inventaire inventaire(List<Album> albums, String... racines) {
        return new Inventaire(
                albums,
                List.of(racines).stream().map(Path::of).collect(java.util.stream.Collectors.toSet()),
                Set.of(), 0, 0);
    }

    @Test
    @DisplayName("des pistes posées à la racine sont signalées comme telles")
    void lesPistesEnVracSontSignalees() {
        Album vrac = albumDePistes("/musique", 9_000_000L, "Daft Punk - One More Time.mp3");

        List<Rangements.Anomalie> anomalies =
                Rangements.chercher(inventaire(List.of(vrac), "/musique"));

        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).nature()).isEqualTo(Rangements.Nature.PISTES_EN_VRAC);
        assertThat(anomalies.get(0).nature().libelle()).contains("racine");
    }

    @Test
    @DisplayName("un dossier qui mêle ses pistes à des dossiers d'albums est signalé")
    void leDossierMixteEstSignale() {
        Album mixte = albumDePistes("/musique/Muse", 9_000_000L, "Hysteria.mp3");
        Album range = album("/musique/Muse/Absolution", "flac", 30_000_000L, 14);

        List<Rangements.Anomalie> anomalies =
                Rangements.chercher(inventaire(List.of(mixte, range), "/musique"));

        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).nature()).isEqualTo(Rangements.Nature.DOSSIER_MIXTE);
    }

    @Test
    @DisplayName("un dossier fourre-tout est signalé comme sans titre")
    void leDossierFourreToutEstSignale() {
        Album sansTitre = album("/musique/Muse/Nouveau dossier", "mp3", 9_000_000L, 5);

        List<Rangements.Anomalie> anomalies =
                Rangements.chercher(inventaire(List.of(sansTitre), "/musique"));

        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).nature()).isEqualTo(Rangements.Nature.SANS_TITRE);
    }

    @Test
    @DisplayName("un album dont personne ne nomme l'artiste est signalé")
    void lAlbumSansArtisteEstSignale() {
        Album orphelin = albumSousUneRacine("/musique/Absolution", "flac", 30_000_000L, 14);

        List<Rangements.Anomalie> anomalies =
                Rangements.chercher(inventaire(List.of(orphelin), "/musique"));

        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).nature()).isEqualTo(Rangements.Nature.SANS_ARTISTE);
    }

    @Test
    @DisplayName("un album bien rangé ne dit rien")
    void lAlbumBienRangeNeDitRien() {
        Album range = album("/musique/Muse/Absolution (2003)", "flac", 30_000_000L, 14);

        assertThat(Rangements.chercher(inventaire(List.of(range), "/musique"))).isEmpty();
    }

    @Test
    @DisplayName("les dossiers les plus lourds sont montrés en premier")
    void lesPlusLourdsDAbord() {
        Album leger = albumSousUneRacine("/musique/Petit", "mp3", 1_000_000L, 5);
        Album lourd = albumSousUneRacine("/musique/Gros", "flac", 30_000_000L, 14);

        List<Rangements.Anomalie> anomalies =
                Rangements.chercher(inventaire(List.of(leger, lourd), "/musique"));

        assertThat(anomalies).hasSize(2);
        assertThat(anomalies.get(0).album()).isEqualTo(lourd);
    }
}
