package fr.musique;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Éprouve ce que le programme répond quand on lui demande son numéro.
 *
 * <p>Le numéro lui-même n'est pas vérifié : il change à chaque montée de version, et l'écrire ici
 * recréerait exactement le littéral que cette classe existe pour supprimer. Ce qui est vérifié,
 * c'est qu'une construction mal réglée soit avouée plutôt que déguisée en version.
 */
class VersionTest {

    @Test
    @DisplayName("la version écrite par la construction est lue")
    void laVersionEcriteEstLue() {
        assertThat(Version.lue()).matches("\\d+\\.\\d+\\.\\d+.*");
        assertThat(new Version().getVersion()).hasSize(1);
        assertThat(new Version().getVersion()[0]).startsWith("music-analysis ");
    }

    @Test
    @DisplayName("une ressource copiée sans filtrage n'est pas prise pour un numéro")
    void leJetonNonFiltreNEstPasUnNumero() {
        assertThat(Version.retenir("${project.version}")).isEqualTo("inconnue");
    }

    @Test
    @DisplayName("une propriété absente ou vide s'avoue au lieu de s'inventer")
    void laProprieteAbsenteSAvoue() {
        assertThat(Version.retenir(null)).isEqualTo("inconnue");
        assertThat(Version.retenir("   ")).isEqualTo("inconnue");
    }

    @Test
    @DisplayName("les espaces autour du numéro ne le dénaturent pas")
    void lesEspacesSontOtes() {
        assertThat(Version.retenir("  2.1.0  ")).isEqualTo("2.1.0");
    }
}
