package fr.musique.nom;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Éprouve la réduction des titres et des noms d'artistes à une forme comparable. */
class CleDeTitreTest {

    @Test
    @DisplayName("la casse, les accents et la ponctuation tombent")
    void laFormeEstRamenéeAuPlusSimple() {
        assertThat(CleDeTitre.normaliser("L'Été Indien !")).isEqualTo("l ete indien");
        assertThat(CleDeTitre.normaliser("The.Dark.Side.Of.The.Moon"))
                .isEqualTo("the dark side of the moon");
    }

    @Test
    @DisplayName("les deux formes Unicode d'un même titre donnent la même clé")
    void lesDeuxFormesUnicodeSeRejoignent() {
        String compose = Normalizer.normalize("Amélie", Normalizer.Form.NFC);
        String decompose = Normalizer.normalize("Amélie", Normalizer.Form.NFD);

        assertThat(CleDeTitre.normaliser(compose))
                .isEqualTo(CleDeTitre.normaliser(decompose))
                .isEqualTo("amelie");
    }

    @Test
    @DisplayName("les nombres du titre sont conservés")
    void lesNombresSontConserves() {
        assertThat(CleDeTitre.normaliser("21")).isEqualTo("21");
        assertThat(CleDeTitre.normaliser("99 Luftballons")).isEqualTo("99 luftballons");
    }

    @Test
    @DisplayName("l'article initial tombe dans la clé secondaire, et lui seul")
    void lArticleInitialTombe() {
        assertThat(CleDeTitre.normaliserSansArticle("The Wall")).isEqualTo("wall");
        assertThat(CleDeTitre.normaliserSansArticle("Les Rita Mitsouko"))
                .isEqualTo("rita mitsouko");
        assertThat(CleDeTitre.normaliserSansArticle("Thelonious")).isEqualTo("thelonious");
    }

    @Test
    @DisplayName("un titre d'un seul mot garde son mot, même s'il ressemble à un article")
    void leTitreDUnSeulMotSurvit() {
        assertThat(CleDeTitre.normaliserSansArticle("The")).isEqualTo("the");
    }

    @Test
    @DisplayName("un artiste rangé à l'envers retrouve son article")
    void lArtisteRangeALEnversEstRemisALEndroit() {
        assertThat(CleDeTitre.normaliserArtiste("Beatles, The")).isEqualTo("the beatles");
        assertThat(CleDeTitre.normaliserArtiste("Police, The")).isEqualTo("the police");
    }

    @Test
    @DisplayName("une virgule qui n'annonce pas un article est laissée tranquille")
    void laVirguleOrdinaireNeDeclencheRien() {
        assertThat(CleDeTitre.normaliserArtiste("Emerson, Lake & Palmer"))
                .isEqualTo("emerson lake palmer");
        assertThat(CleDeTitre.normaliserArtiste("Muse")).isEqualTo("muse");
    }
}
