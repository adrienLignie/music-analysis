package fr.musique.media;

import static org.assertj.core.api.Assertions.assertThat;

import fr.musique.suivi.Avancement;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Éprouve la table des étiquettes lues et ce qu'on en tire. */
class EtiquettesTest {

    @Test
    @DisplayName("les fils de lecture sont refermés, appel après appel")
    void lesFilsDeLectureSontRefermes(@TempDir Path racine) throws IOException {
        // Trente-deux lectures de front, c'est trente-deux fils par appel. Non refermés, dix
        // appels en laisseraient trois cents derrière eux, et un programme qui ne rendrait plus la
        // main après avoir écrit son rapport. Rien d'autre que ce compte ne le dirait.
        List<Path> fichiers = new ArrayList<>();
        for (int rang = 0; rang < 40; rang++) {
            fichiers.add(FichiersDEssai.flac(
                    racine.resolve(rang + " - Titre.flac"), 44_100L * 240, 44_100, Map.of()));
        }
        int avant = Thread.activeCount();

        for (int appel = 0; appel < 10; appel++) {
            assertThat(Etiquettes.lire(fichiers, Avancement.muet()).taille()).isEqualTo(40);
        }

        // Une marge large : d'autres fils peuvent vivre dans la machine virtuelle pendant un test.
        // Ce qui est surveillé est l'ordre de grandeur, et dix ensembles fuités le dépasseraient
        // de très loin.
        assertThat(Thread.activeCount()).isLessThan(avant + 64);
    }

    @TempDir
    private Path racine;

    @Test
    @DisplayName("aucune étiquette demandée, aucune table")
    void lAbsenceDeDemandeNeLitRien() {
        Etiquettes aucune = Etiquettes.lire(List.of(), Avancement.muet());

        assertThat(aucune.estVide()).isTrue();
        assertThat(aucune.taille()).isZero();
        assertThat(aucune.de(racine.resolve("absent.flac")).estVide()).isTrue();
        assertThat(aucune.dureeDe(racine.resolve("absent.flac"))).isEmpty();
    }

    @Test
    @DisplayName("les fichiers illisibles ne figurent pas dans la table")
    void lesFichiersIllisiblesNeFigurentPas() throws IOException {
        Path lisible = FichiersDEssai.flac(
                racine.resolve("01 - Titre.flac"), 44_100L * 200, 44_100, Map.of());
        Path illisible = FichiersDEssai.fichierQuiNEnEstPasUn(racine.resolve("02 - Titre.flac"));

        Etiquettes lues = Etiquettes.lire(List.of(lisible, illisible), Avancement.muet());

        assertThat(lues.taille()).isEqualTo(1);
        assertThat(lues.dureeDe(lisible)).hasValue(200.0);
        assertThat(lues.dureeDe(illisible)).isEmpty();
    }

    @Test
    @DisplayName("le débit est le poids divisé par la durée")
    void leDebitEstLePoidsDiviseParLaDuree() throws IOException {
        Path fichier = FichiersDEssai.flac(
                racine.resolve("01 - Titre.flac"), 44_100L * 200, 44_100, Map.of());
        Etiquettes lues = Etiquettes.lire(List.of(fichier), Avancement.muet());

        assertThat(lues.debitDe(fichier, 20_000_000L)).hasValue(100_000L);
        assertThat(lues.debitDe(racine.resolve("absent.flac"), 20_000_000L)).isEmpty();
    }

    @Test
    @DisplayName("les durées sont écrites comme on parle d'un morceau, puis d'un album")
    void lesDureesSontLisibles() {
        assertThat(Etiquettes.enTexte(245)).isEqualTo("4 min 05 s");
        assertThat(Etiquettes.enTexte(3725)).isEqualTo("1 h 02 min");
    }

    @Test
    @DisplayName("le débit est écrit en kilobits, comme les encodeurs le disent")
    void leDebitEstLisible() {
        assertThat(Etiquettes.debitEnTexte(40_000L)).isEqualTo("320 kbit/s");
    }
}
