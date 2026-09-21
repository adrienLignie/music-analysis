package fr.musique.doublons;

import static fr.musique.doublons.AlbumsDEssai.album;
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

/** Éprouve ce que les étiquettes lues dans les fichiers font dire d'un rapprochement. */
class VerificationParLesEtiquettesTest {

    private final Map<Path, Etiquette> table = new HashMap<>();

    /** Donne à toutes les pistes d'un album la même identité et la même durée. */
    private void etiqueter(Album album, String artiste, String titre, double secondesParPiste) {
        for (Piste piste : album.pistes()) {
            table.put(piste.chemin(), new Etiquette(
                    OptionalDouble.of(secondesParPiste),
                    Optional.of(artiste),
                    Optional.of(titre),
                    Optional.empty(),
                    OptionalInt.empty(),
                    OptionalInt.empty()));
        }
    }

    /** Donne à chaque piste d'un album une empreinte tirée de son rang, toujours la même. */
    private void empreindre(Album album, String sorte) {
        for (int rang = 0; rang < album.pistes().size(); rang++) {
            Piste piste = album.pistes().get(rang);
            Etiquette connue = table.getOrDefault(piste.chemin(), Etiquette.vide());
            table.put(piste.chemin(), new Etiquette(
                    connue.secondes(),
                    connue.artiste(),
                    connue.album(),
                    connue.titre(),
                    connue.numero(),
                    connue.annee(),
                    Optional.of(sorte + ":piste" + rang)));
        }
    }

    private static GroupeDeDoublons groupe(NiveauDeConfiance confiance, Album... albums) {
        return new GroupeDeDoublons(List.of(albums), confiance, "motif");
    }

    @Test
    @DisplayName("des empreintes identiques rendent le rapprochement certain")
    void lesEmpreintesIdentiquesRendentLeRapprochementCertain() {
        Album premier = album("/a/Muse/Absolution", "flac", 30_000_000L, 14);
        Album second = album("/b/telechargements/Absolution", "flac", 30_000_000L, 14);
        empreindre(premier, "flac-md5");
        empreindre(second, "flac-md5");

        List<GroupeDeDoublons> revus = VerificationParLesEtiquettes.verifier(
                List.of(groupe(NiveauDeConfiance.MOYENNE, premier, second)),
                Etiquettes.de(table));

        assertThat(revus.get(0).confiance()).isEqualTo(NiveauDeConfiance.CERTAINE);
        assertThat(revus.get(0).motif()).contains("même son");
    }

    @Test
    @DisplayName("le son démontré identique tient malgré des étiquettes qui se contredisent")
    void leSonIdentiqueTientMalgreLesEtiquettes() {
        // C'est la signature du doublon le plus courant : un fichier recopié puis réétiqueté. Les
        // étiquettes décrivent ce que le fichier prétend être, l'empreinte ce qu'il est.
        Album premier = album("/a/Muse/Absolution", "flac", 30_000_000L, 14);
        Album second = album("/b/Divers/Absolution", "flac", 30_000_000L, 14);
        etiqueter(premier, "Muse", "Absolution", 240);
        etiqueter(second, "Inconnu", "Sans titre", 240);
        empreindre(premier, "flac-md5");
        empreindre(second, "flac-md5");

        List<GroupeDeDoublons> revus = VerificationParLesEtiquettes.verifier(
                List.of(groupe(NiveauDeConfiance.FORTE, premier, second)),
                Etiquettes.de(table));

        assertThat(revus.get(0).confiance()).isEqualTo(NiveauDeConfiance.CERTAINE);
    }

    @Test
    @DisplayName("deux empreintes de la même sorte qui diffèrent signalent deux encodages")
    void lesEmpreintesDifferentesSignalentDeuxEncodages() {
        Album premier = album("/a/Muse/Absolution", "flac", 30_000_000L, 14);
        Album second = album("/b/Muse/Absolution", "flac", 28_000_000L, 14);
        empreindre(premier, "flac-md5");
        table.put(second.pistes().get(0).chemin(), new Etiquette(
                OptionalDouble.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                OptionalInt.empty(), OptionalInt.empty(), Optional.of("flac-md5:autre")));
        for (int rang = 1; rang < second.pistes().size(); rang++) {
            table.put(second.pistes().get(rang).chemin(), new Etiquette(
                    OptionalDouble.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    OptionalInt.empty(), OptionalInt.empty(),
                    Optional.of("flac-md5:piste" + rang)));
        }

        List<GroupeDeDoublons> revus = VerificationParLesEtiquettes.verifier(
                List.of(groupe(NiveauDeConfiance.FORTE, premier, second)),
                Etiquettes.de(table));

        // Un désaccord ne fait jamais descendre la confiance : deux extractions du même disque
        // restent deux exemplaires du même album.
        assertThat(revus.get(0).confiance()).isEqualTo(NiveauDeConfiance.FORTE);
        assertThat(revus.get(0).motif()).contains("sons différents");
    }

    @Test
    @DisplayName("un flac et un mp3 ne se comparent pas : leur désaccord ne dit rien")
    void lesSortesDEmpreintesNeSeComparentPas() {
        Album premier = album("/a/Muse/Absolution", "flac", 30_000_000L, 14);
        Album second = album("/b/Muse/Absolution", "mp3", 9_000_000L, 14);
        empreindre(premier, "flac-md5");
        empreindre(second, "mp3-lame");

        List<GroupeDeDoublons> revus = VerificationParLesEtiquettes.verifier(
                List.of(groupe(NiveauDeConfiance.FORTE, premier, second)),
                Etiquettes.de(table));

        assertThat(revus.get(0).confiance()).isEqualTo(NiveauDeConfiance.FORTE);
        assertThat(revus.get(0).motif()).doesNotContain("son");
    }

    @Test
    @DisplayName("un seul exemplaire muet suffit à renoncer à la démonstration")
    void unExemplaireMuetSuffitARenoncer() {
        Album premier = album("/a/Muse/Absolution", "flac", 30_000_000L, 14);
        Album second = album("/b/Muse/Absolution", "flac", 30_000_000L, 14);
        empreindre(premier, "flac-md5");
        etiqueter(second, "Muse", "Absolution", 240);

        List<GroupeDeDoublons> revus = VerificationParLesEtiquettes.verifier(
                List.of(groupe(NiveauDeConfiance.FORTE, premier, second)),
                Etiquettes.de(table));

        assertThat(revus.get(0).confiance()).isEqualTo(NiveauDeConfiance.FORTE);
        assertThat(revus.get(0).motif()).doesNotContain("même son");
    }

    @Test
    @DisplayName("des étiquettes concordantes rachètent un rapprochement moyen")
    void lesEtiquettesConcordantesFontMonterLaConfiance() {
        Album premier = album("/a/Muse/Absolution", "flac", 30_000_000L, 14);
        Album second = album("/b/Rock/Absolution", "mp3", 9_000_000L, 14);
        etiqueter(premier, "Muse", "Absolution", 240);
        etiqueter(second, "Muse", "Absolution", 240);

        List<GroupeDeDoublons> revus = VerificationParLesEtiquettes.verifier(
                List.of(groupe(NiveauDeConfiance.MOYENNE, premier, second)),
                Etiquettes.de(table));

        assertThat(revus.get(0).confiance()).isEqualTo(NiveauDeConfiance.FORTE);
        assertThat(revus.get(0).motif()).contains("étiquettes concordantes");
    }

    @Test
    @DisplayName("des étiquettes qui se contredisent font descendre la confiance")
    void lesEtiquettesDiscordantesFontDescendreLaConfiance() {
        Album premier = album("/a/Muse/Absolution", "flac", 30_000_000L, 14);
        Album second = album("/b/Muse/Absolution", "flac", 30_000_000L, 14);
        etiqueter(premier, "Muse", "Absolution", 240);
        etiqueter(second, "Muse", "Black Holes And Revelations", 240);

        List<GroupeDeDoublons> revus = VerificationParLesEtiquettes.verifier(
                List.of(groupe(NiveauDeConfiance.FORTE, premier, second)),
                Etiquettes.de(table));

        assertThat(revus.get(0).confiance()).isEqualTo(NiveauDeConfiance.MOYENNE);
        assertThat(revus.get(0).motif()).contains("étiquettes différentes");
    }

    @Test
    @DisplayName("deux durées totales trop éloignées font descendre la confiance")
    void lesDureesEloigneesFontDescendreLaConfiance() {
        Album complet = album("/a/Muse/Absolution", "flac", 30_000_000L, 14);
        Album court = album("/b/Muse/Absolution", "flac", 30_000_000L, 14);
        etiqueter(complet, "Muse", "Absolution", 240);
        etiqueter(court, "Muse", "Absolution", 120);

        List<GroupeDeDoublons> revus = VerificationParLesEtiquettes.verifier(
                List.of(groupe(NiveauDeConfiance.FORTE, complet, court)),
                Etiquettes.de(table));

        assertThat(revus.get(0).confiance()).isEqualTo(NiveauDeConfiance.MOYENNE);
        assertThat(revus.get(0).motif()).contains("durées différentes");
    }

    @Test
    @DisplayName("quelques secondes d'écart ne changent rien")
    void lesDureesVoisinesNeChangentRien() {
        Album premier = album("/a/Muse/Absolution", "flac", 30_000_000L, 14);
        Album second = album("/b/Muse/Absolution", "flac", 30_000_000L, 14);
        etiqueter(premier, "Muse", "Absolution", 240);
        etiqueter(second, "Muse", "Absolution", 238);

        List<GroupeDeDoublons> revus = VerificationParLesEtiquettes.verifier(
                List.of(groupe(NiveauDeConfiance.FORTE, premier, second)),
                Etiquettes.de(table));

        assertThat(revus.get(0).confiance()).isEqualTo(NiveauDeConfiance.FORTE);
        assertThat(revus.get(0).motif()).doesNotContain("durées différentes");
    }

    @Test
    @DisplayName("un seul dossier étiqueté ne vérifie rien")
    void unSeulDossierEtiqueteNeVerifieRien() {
        Album etiquete = album("/a/Muse/Absolution", "flac", 30_000_000L, 14);
        Album muet = album("/b/Muse/Absolution", "flac", 30_000_000L, 14);
        etiqueter(etiquete, "Muse", "Absolution", 240);

        List<GroupeDeDoublons> revus = VerificationParLesEtiquettes.verifier(
                List.of(groupe(NiveauDeConfiance.MOYENNE, etiquete, muet)),
                Etiquettes.de(table));

        assertThat(revus.get(0).confiance()).isEqualTo(NiveauDeConfiance.MOYENNE);
        assertThat(revus.get(0).motif()).isEqualTo("motif");
    }

    @Test
    @DisplayName("un dossier dont les étiquettes se contredisent en interne ne vaut aucune preuve")
    void leDossierIncoherentNEstPasUnePreuve() {
        Album melange = album("/a/Muse/Absolution", "flac", 30_000_000L, 4);
        Album net = album("/b/Muse/Absolution", "flac", 30_000_000L, 4);
        etiqueter(melange, "Muse", "Absolution", 240);
        table.put(melange.pistes().get(0).chemin(), new Etiquette(
                OptionalDouble.of(240), Optional.of("Radiohead"), Optional.of("OK Computer"),
                Optional.empty(), OptionalInt.empty(), OptionalInt.empty()));
        etiqueter(net, "Muse", "Absolution", 240);

        List<GroupeDeDoublons> revus = VerificationParLesEtiquettes.verifier(
                List.of(groupe(NiveauDeConfiance.MOYENNE, melange, net)),
                Etiquettes.de(table));

        assertThat(revus.get(0).confiance()).isEqualTo(NiveauDeConfiance.MOYENNE);
        assertThat(revus.get(0).motif()).doesNotContain("étiquettes différentes");
    }

    @Test
    @DisplayName("sans étiquette lue, les groupes sont rendus tels quels")
    void sansEtiquetteRienNeChange() {
        List<GroupeDeDoublons> groupes = List.of(groupe(
                NiveauDeConfiance.FORTE,
                album("/a/Muse/Absolution", "flac", 30_000_000L, 14),
                album("/b/Muse/Absolution", "flac", 30_000_000L, 14)));

        assertThat(VerificationParLesEtiquettes.verifier(groupes, Etiquettes.aucune()))
                .isSameAs(groupes);
    }
}
