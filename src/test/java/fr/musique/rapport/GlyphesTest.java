package fr.musique.rapport;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Éprouve l'adaptation du rapport à ce que la sortie sait écrire. */
class GlyphesTest {

    /** Page de code d'une console Windows française par défaut. */
    private static final Charset CONSOLE_WINDOWS = Charset.forName("windows-1252");

    @Nested
    @DisplayName("Choix du jeu de caractères")
    class Choix {

        @Test
        @DisplayName("une sortie en UTF-8 garde les tracés et les accents")
        void lUtf8GardeToutLeRapport() {
            assertThat(Glyphes.pour(StandardCharsets.UTF_8, false).estAscii()).isFalse();
        }

        @Test
        @DisplayName("une console qui ne sait pas écrire « ─ » fait tomber tout le rapport en ASCII")
        void laConsoleSansTraceBasculeEnAscii() {
            assertThat(Glyphes.pour(CONSOLE_WINDOWS, false).estAscii()).isTrue();
            assertThat(Glyphes.pour(StandardCharsets.US_ASCII, false).estAscii()).isTrue();
        }

        @Test
        @DisplayName("l'ASCII demandé en ligne de commande l'emporte sur ce que la sortie sait")
        void lAsciiDemandeEstToujoursRespecte() {
            assertThat(Glyphes.pour(StandardCharsets.UTF_8, true).estAscii()).isTrue();
        }
    }

    @Nested
    @DisplayName("Dégradation")
    class Degradation {

        @Test
        @DisplayName("le filet est tracé en tirets et les guillemets deviennent droits")
        void leTraceDevientDuTexteOrdinaire() {
            Glyphes glyphes = Glyphes.ascii();

            assertThat(glyphes.separateur()).startsWith("----").doesNotContain("─");
            assertThat(glyphes.adapter("titre « Amélie », 2001"))
                    .isEqualTo("titre \"Amelie\", 2001");
        }

        @Test
        @DisplayName("un titre en NFD perd ses accents comme un titre en NFC")
        void lesDeuxFormesUnicodeDonnentLeMemeTexte() {
            Glyphes glyphes = Glyphes.ascii();

            assertThat(glyphes.adapter("Amélie")).isEqualTo(glyphes.adapter("Amélie"));
        }

        @Test
        @DisplayName("les ligatures et l'esperluette des noms de dossiers survivent lisiblement")
        void lesLigaturesRestentLisibles() {
            assertThat(Glyphes.ascii().adapter("N° 054 — Sœurs & frères…"))
                    .isEqualTo("No 054 - Soeurs & freres...");
        }

        @Test
        @DisplayName("un caractère qu'on ne sait pas rendre laisse une trace, jamais un vide")
        void leCaractereInconnuLaisseUnePlace() {
            assertThat(Glyphes.ascii().adapter("動物.mkv")).isEqualTo("??.mkv");
        }

        @Test
        @DisplayName("le rapport complet ne touche à rien")
        void leRapportCompletResteIntact() {
            String ligne = "titre « Amélie », 2001 ─";

            assertThat(Glyphes.completes().adapter(ligne)).isEqualTo(ligne);
        }
    }
}
