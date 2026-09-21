package fr.musique.rapport;

import static fr.musique.doublons.AlbumsDEssai.album;
import static fr.musique.doublons.AlbumsDEssai.albumDePistes;
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

/** Éprouve la détection des fichiers trop légers pour ce que leur format promet. */
class PistesSuspectesTest {

    /** Étiquettes qui donnent la même durée à toutes les pistes d'un album. */
    private static Etiquettes durees(Album album, double secondes) {
        Map<Path, Etiquette> table = new HashMap<>();
        for (Piste piste : album.pistes()) {
            table.put(piste.chemin(), new Etiquette(
                    OptionalDouble.of(secondes), Optional.empty(), Optional.empty(),
                    Optional.empty(), OptionalInt.empty(), OptionalInt.empty()));
        }
        return Etiquettes.de(table);
    }

    @Test
    @DisplayName("une piste sans commune mesure avec ses voisines est signalée")
    void laPisteHorsDeProportionEstSignalee() {
        Album album = albumDePistes("/musique/Téléphone/Dure Limite", 9_000_000L,
                "01 - Titre.mp3", "02 - Titre.mp3", "03 - Titre.mp3", "04 - Titre.mp3");
        Album avecCoupure = fabriquerAvecUneCoupure(album);

        List<PistesSuspectes.Suspecte> suspectes =
                PistesSuspectes.chercher(List.of(avecCoupure));

        assertThat(suspectes).hasSize(1);
        assertThat(suspectes.get(0).nature()).isEqualTo(PistesSuspectes.Nature.POIDS);
        assertThat(suspectes.get(0).constate()).isEqualTo(100_000L);
    }

    /** Remplace la dernière piste d'un album par une piste manifestement coupée. */
    private static Album fabriquerAvecUneCoupure(Album album) {
        List<Piste> pistes = new java.util.ArrayList<>(album.pistes());
        Piste derniere = pistes.remove(pistes.size() - 1);
        pistes.add(new Piste(derniere.chemin(), 100_000L, derniere.nom()));
        return Album.de(album.dossier(), pistes, album.nom());
    }

    @Test
    @DisplayName("un album régulier ne signale rien")
    void lAlbumRegulierNeSignaleRien() {
        assertThat(PistesSuspectes.chercher(
                List.of(album("/musique/Muse/Absolution", "flac", 30_000_000L, 12)))).isEmpty();
    }

    @Test
    @DisplayName("un dossier trop petit ne permet pas de comparer")
    void leDossierTropPetitNePermetPasDeComparer() {
        Album maxi = albumDePistes("/musique/Muse/Maxi", 9_000_000L, "01 - Titre.mp3");

        assertThat(PistesSuspectes.chercher(List.of(maxi))).isEmpty();
    }

    @Test
    @DisplayName("un flac au débit d'un mp3 est un mp3 déguisé")
    void leFlacAuDebitDUnMp3EstSignale() {
        Album faux = album("/musique/Muse/Absolution", "flac", 3_000_000L, 10);

        List<PistesSuspectes.Suspecte> suspectes =
                PistesSuspectes.chercher(List.of(faux), durees(faux, 240));

        assertThat(suspectes).hasSize(10);
        assertThat(suspectes.get(0).nature()).isEqualTo(PistesSuspectes.Nature.DEBIT);
        assertThat(suspectes.get(0).constate()).isEqualTo(12_500L);
        assertThat(suspectes.get(0).attendu()).isEqualTo(37_500L);
    }

    @Test
    @DisplayName("un flac au débit d'un flac n'est pas signalé")
    void leVraiFlacNEstPasSignale() {
        Album vrai = album("/musique/Muse/Absolution", "flac", 20_000_000L, 10);

        assertThat(PistesSuspectes.chercher(List.of(vrai), durees(vrai, 240))).isEmpty();
    }

    @Test
    @DisplayName("un morceau court cesse d'être suspect dès que sa durée est connue")
    void leMorceauCourtEstBlanchiParSaDuree() {
        Album album = albumDePistes("/musique/Muse/Absolution", 9_000_000L,
                "01 - Titre.mp3", "02 - Titre.mp3", "03 - Titre.mp3", "04 - Titre.mp3");
        Album avecInterlude = fabriquerAvecUneCoupure(album);
        Map<Path, Etiquette> table = new HashMap<>();
        for (Piste piste : avecInterlude.pistes()) {
            double secondes = piste.taille() == 100_000L ? 10 : 240;
            table.put(piste.chemin(), new Etiquette(
                    OptionalDouble.of(secondes), Optional.empty(), Optional.empty(),
                    Optional.empty(), OptionalInt.empty(), OptionalInt.empty()));
        }

        assertThat(PistesSuspectes.chercher(List.of(avecInterlude), Etiquettes.de(table)))
                .isEmpty();
    }

    @Test
    @DisplayName("un format qu'on ne connaît pas n'est jugé que sur son poids")
    void leFormatInconnuNEstJugeQueSurSonPoids() {
        Album album = albumDePistes("/musique/Muse/Absolution", 9_000_000L,
                "01 - Titre.ape", "02 - Titre.ape", "03 - Titre.ape", "04 - Titre.ape");

        assertThat(PistesSuspectes.chercher(List.of(album), durees(album, 240))).isEmpty();
    }
}
