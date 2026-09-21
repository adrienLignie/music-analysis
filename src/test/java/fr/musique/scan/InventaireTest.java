package fr.musique.scan;

import static fr.musique.doublons.AlbumsDEssai.album;
import static org.assertj.core.api.Assertions.assertThat;

import fr.musique.doublons.Album;
import fr.musique.doublons.Piste;
import fr.musique.media.Etiquette;
import fr.musique.media.Etiquettes;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Éprouve ce que l'inventaire compte, et ce qu'il refuse de compter deux fois. */
class InventaireTest {

    private static final Album ALBUM = album("/musique/Muse/Absolution", "flac", 10_000_000L, 4);

    @Test
    @DisplayName("sans lien dur, tout ce qui a été vu est compté")
    void sansLienDurToutEstCompte() {
        Inventaire inventaire = new Inventaire(List.of(ALBUM), 2, 1);

        assertThat(inventaire.albumsDistincts()).containsExactly(ALBUM);
        assertThat(inventaire.nombreDePistes()).isEqualTo(4);
        assertThat(inventaire.tailleTotale()).isEqualTo(40_000_000L);
        assertThat(inventaire.fichiersIgnores()).isEqualTo(2);
        assertThat(inventaire.dossiersIllisibles()).isEqualTo(1);
        assertThat(inventaire.racines()).isEmpty();
        assertThat(inventaire.etiquettes().estVide()).isTrue();
    }

    @Test
    @DisplayName("un dossier dont tous les fichiers sont déjà comptés ne pèse rien et sort du classement")
    void leDossierEntierementRepeteSortDuClassement() {
        Set<Path> repetitions = Set.copyOf(
                ALBUM.pistes().stream().map(Piste::chemin).toList());
        Album fantome = Album.de(ALBUM.dossier(), ALBUM.pistes(), ALBUM.nom(), repetitions);

        Inventaire inventaire = new Inventaire(
                List.of(fantome), Set.of(), repetitions, 0, 0);

        assertThat(inventaire.albumsDistincts()).isEmpty();
        assertThat(inventaire.tailleTotale()).isZero();
        assertThat(inventaire.nombreDePistes()).isZero();
        assertThat(inventaire.estUneRepetition(fantome.pistes().get(0))).isTrue();
    }

    @Test
    @DisplayName("les étiquettes s'ajoutent sans rien changer d'autre")
    void lesEtiquettesSAjoutent() {
        Etiquettes lues = Etiquettes.de(Map.of(
                ALBUM.pistes().get(0).chemin(),
                new Etiquette(OptionalDouble.of(240), Optional.of("Muse"), Optional.empty(),
                        Optional.empty(), OptionalInt.empty(), OptionalInt.empty())));

        Inventaire avec = new Inventaire(List.of(ALBUM), 0, 0).avecEtiquettes(lues);

        assertThat(avec.etiquettes().taille()).isEqualTo(1);
        assertThat(avec.tailleTotale()).isEqualTo(40_000_000L);
        assertThat(avec.albums()).containsExactly(ALBUM);
    }
}
