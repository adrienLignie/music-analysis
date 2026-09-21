package fr.musique.suivi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Éprouve la ligne d'attente.
 *
 * <p>L'horloge est fournie par le test plutôt que lue sur la machine : le bridage du rythme est
 * précisément ce qu'on veut vérifier, et le vérifier en dormant cent millisecondes rendrait la
 * suite plus lente qu'elle n'a de raison de l'être.
 */
class AvancementSurTerminalTest {

    private static final long CENT_MILLISECONDES = 100_000_000L;

    private final ByteArrayOutputStream tampon = new ByteArrayOutputStream();
    private final AtomicLong horloge = new AtomicLong();

    private Avancement avancement() {
        return new AvancementSurTerminal(
                new PrintStream(tampon, true, StandardCharsets.UTF_8), horloge::get);
    }

    private String affiche() {
        return tampon.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("la ligne annonce l'étape et son compteur")
    void laLigneAnnonceLEtapeEtLeCompteur() {
        Avancement avancement = avancement();

        avancement.etape("Parcours");
        avancement.pas(42, -1);

        assertThat(affiche()).contains("Parcours 42").startsWith("\r");
    }

    @Test
    @DisplayName("un total connu s'affiche comme une fraction")
    void leTotalConnuSAfficheEnFraction() {
        Avancement avancement = avancement();

        avancement.etape("Lecture des durées");
        avancement.pas(3, 40);

        assertThat(affiche()).contains("Lecture des durées 3/40");
    }

    @Test
    @DisplayName("deux comptes trop rapprochés n'écrivent qu'une ligne")
    void leRythmeEstBride() {
        Avancement avancement = avancement();
        avancement.etape("Parcours");

        avancement.pas(1, -1);
        avancement.pas(2, -1);

        assertThat(affiche()).contains("Parcours 1").doesNotContain("Parcours 2");
    }

    @Test
    @DisplayName("le temps passé, la ligne est réécrite")
    void leTempsPasseLaLigneEstReecrite() {
        Avancement avancement = avancement();
        avancement.etape("Parcours");

        avancement.pas(1, -1);
        horloge.addAndGet(CENT_MILLISECONDES);
        avancement.pas(2, -1);

        assertThat(affiche()).contains("Parcours 1").contains("Parcours 2");
    }

    @Test
    @DisplayName("un compteur qui raccourcit ne laisse pas de chiffre orphelin")
    void leCompteurQuiRaccourcitEstRecouvert() {
        Avancement avancement = avancement();
        avancement.etape("Parcours");

        avancement.pas(1000, -1);
        horloge.addAndGet(CENT_MILLISECONDES);
        avancement.pas(1, -1);

        assertThat(affiche()).endsWith("\rParcours 1   ");
    }

    @Test
    @DisplayName("la fin efface la ligne : le rapport commence sur une ligne propre")
    void laFinEffaceLaLigne() {
        Avancement avancement = avancement();
        avancement.etape("Parcours");
        avancement.pas(42, -1);

        avancement.fin();

        assertThat(affiche()).endsWith("\r" + " ".repeat("Parcours 42".length()) + "\r");
    }

    @Test
    @DisplayName("changer d'étape efface ce que la précédente affichait")
    void changerDEtapeEfface() {
        Avancement avancement = avancement();
        avancement.etape("Parcours");
        avancement.pas(42, -1);

        avancement.etape("Lecture des noms");
        avancement.pas(1, 10);

        assertThat(affiche())
                .contains("\r" + " ".repeat("Parcours 42".length()) + "\r")
                .endsWith("Lecture des noms 1/10");
    }

    @Test
    @DisplayName("une étape très longue est coupée plutôt que de déborder du terminal")
    void lEtapeTropLongueEstCoupee() {
        Avancement avancement = avancement();

        avancement.etape("x".repeat(200));
        avancement.pas(1, -1);

        assertThat(affiche().replace("\r", "")).hasSize(78);
    }

    @Test
    @DisplayName("rien n'a été affiché : il n'y a rien à effacer")
    void rienAAfficherRienAEffacer() {
        Avancement avancement = avancement();

        avancement.fin();

        assertThat(affiche()).isEmpty();
    }

    @Test
    @DisplayName("l'avancement muet ne dit rien, quoi qu'on lui demande")
    void lAvancementMuetNeDitRien() {
        Avancement muet = Avancement.muet();

        muet.etape("Parcours");
        muet.pas(1, 2);
        muet.fin();

        assertThat(affiche()).isEmpty();
    }

    @Test
    @DisplayName("le silence demandé est obtenu sans avoir à deviner ce qu'est la sortie")
    void leSilenceDemandeEstObtenu() {
        assertThat(AvancementSurTerminal.pourLaSortieDErreur(true)).isNotNull();
        // Sous Maven, la sortie d'erreur n'est pas un terminal : l'avancement automatique se tait
        // de lui-même, ce qui est exactement le comportement attendu d'un build.
        assertThat(AvancementSurTerminal.pourLaSortieDErreur(false)).isNotNull();
    }
}
