package fr.musique.nom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Éprouve ce qu'un format audio vaut et ce qu'il promet. */
class FormatsTest {

    @Test
    @DisplayName("le sans perte passe devant le avec perte, et l'inconnu ferme la marche")
    void lOrdreDesFormats() {
        assertThat(Formats.rang("flac")).isGreaterThan(Formats.rang("mp3"));
        assertThat(Formats.rang("mp3")).isGreaterThan(Formats.rang("cbz"));
        assertThat(Formats.rang("cbz")).isEqualTo(Formats.RANG_INCONNU);
        assertThat(Formats.rang(null)).isEqualTo(Formats.RANG_INCONNU);
    }

    @Test
    @DisplayName("la casse de l'extension ne change rien")
    void laCasseNeChangeRien() {
        assertThat(Formats.estSansPerte("FLAC")).isTrue();
        assertThat(Formats.estSansPerte("mp3")).isFalse();
    }

    @Test
    @DisplayName("le m4a est rangé avec les formats à perte, faute de pouvoir trancher")
    void leM4aEstRangeAvecLesFormatsAPerte() {
        assertThat(Formats.estSansPerte("m4a")).isFalse();
    }

    @Test
    @DisplayName("le débit plancher du sans perte est bien plus haut que celui du avec perte")
    void lesPlanchersSuiventLeFormat() {
        assertThat(Formats.debitPlancher("flac")).hasValue(37_500L);
        assertThat(Formats.debitPlancher("mp3")).hasValue(6_000L);
        assertThat(Formats.debitPlancher("opus").getAsLong())
                .isLessThan(Formats.debitPlancher("mp3").getAsLong());
        assertThat(Formats.debitPlancher("cbz")).isEmpty();
    }

    @Test
    @DisplayName("le débit annoncé est lu dans les jetons, le meilleur l'emportant")
    void leDebitAnnonceEstLu() {
        assertThat(Formats.debitAnnonce(List.of("mp3", "320"))).hasValue(320);
        assertThat(Formats.debitAnnonce(List.of("v0"))).hasValue(245);
        assertThat(Formats.debitAnnonce(List.of("192", "320"))).hasValue(320);
        assertThat(Formats.debitAnnonce(List.of("flac"))).isEmpty();
    }
}
