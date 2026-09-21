package fr.musique.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Éprouve le parcours sur une arborescence écrite pour l'occasion.
 *
 * <p>Les fichiers sont vraiment créés : ce qu'on vérifie ici, ce sont des décisions prises
 * d'après ce que le système de fichiers répond, et rien ne remplace sa réponse.
 */
class ParcoursTest {

    @TempDir
    private Path racine;

    private static final long UN_MEGAOCTET = 1024L * 1024;

    private Path fichier(String chemin, long octets) throws IOException {
        Path resolu = racine.resolve(chemin);
        Files.createDirectories(resolu.getParent());
        Files.write(resolu, new byte[(int) octets]);
        return resolu;
    }

    private Arborescence parcourir(Path... racines) {
        return new Parcours(CriteresDeScan.parDefaut()).parcourir(List.of(racines));
    }

    @Test
    @DisplayName("seuls les fichiers audio sont retenus, dossier par dossier")
    void seulsLesFichiersAudioSontRetenus() throws IOException {
        fichier("Muse/Absolution/01 - Titre.flac", UN_MEGAOCTET);
        fichier("Muse/Absolution/02 - Titre.flac", UN_MEGAOCTET);
        fichier("Muse/Absolution/cover.jpg", UN_MEGAOCTET);
        fichier("Muse/Absolution/album.log", 1024);

        Arborescence arborescence = parcourir(racine);

        assertThat(arborescence.dossiers()).hasSize(1);
        assertThat(arborescence.nombreDeFichiers()).isEqualTo(2);
        assertThat(arborescence.racines()).containsExactly(racine.toRealPath());
    }

    @Test
    @DisplayName("un fichier trop léger est écarté et compté")
    void leFichierTropLegerEstEcarte() throws IOException {
        fichier("Muse/Absolution/01 - Titre.flac", UN_MEGAOCTET);
        fichier("Muse/Absolution/02 - Silence.flac", 1024);

        Arborescence arborescence = parcourir(racine);

        assertThat(arborescence.nombreDeFichiers()).isEqualTo(1);
        assertThat(arborescence.fichiersIgnores()).isEqualTo(1);
    }

    @Test
    @DisplayName("les dossiers de pochettes et les corbeilles ne sont pas parcourus")
    void lesDossiersAnnexesSontEcartes() throws IOException {
        fichier("Muse/Absolution/01 - Titre.flac", UN_MEGAOCTET);
        fichier("Muse/Absolution/Scans/pochette.flac", UN_MEGAOCTET);
        fichier("$RECYCLE.BIN/ancien.flac", UN_MEGAOCTET);

        assertThat(parcourir(racine).nombreDeFichiers()).isEqualTo(1);
    }

    @Test
    @DisplayName("ce qui n'est pas de la musique est pesé au compte de l'album")
    void leHorsMusiqueEstPeseAuCompteDeLAlbum() throws IOException {
        fichier("Muse/Absolution/01 - Titre.flac", UN_MEGAOCTET);
        fichier("Muse/Absolution/cover.jpg", 2 * UN_MEGAOCTET);
        fichier("Muse/Absolution/album.log", 1024);

        DossierTrouve album = parcourir(racine).dossiers().get(0);

        assertThat(album.fichiers()).hasSize(1);
        assertThat(album.octetsHorsAudio()).isEqualTo(2 * UN_MEGAOCTET + 1024);
    }

    @Test
    @DisplayName("le poids d'un dossier de pochettes revient à l'album qui l'abrite")
    void lePoidsDesPochettesRevientALAlbum() throws IOException {
        // C'est le gisement de place qu'aucune autre ligne du rapport ne montrerait : un livret
        // numérisé pèse couramment plus lourd que le disque qu'il illustre.
        fichier("Muse/Absolution/01 - Titre.flac", UN_MEGAOCTET);
        fichier("Muse/Absolution/Scans/livret.tif", 40 * UN_MEGAOCTET);
        fichier("Muse/Absolution/Scans/2003/verso.tif", 10 * UN_MEGAOCTET);

        DossierTrouve album = parcourir(racine).dossiers().get(0);

        assertThat(album.nom()).isEqualTo("Absolution");
        assertThat(album.octetsHorsAudio()).isEqualTo(50 * UN_MEGAOCTET);
    }

    @Test
    @DisplayName("un dossier de pochettes ne devient jamais un album, même plein de flac")
    void leDossierDePochettesNEstJamaisUnAlbum() throws IOException {
        fichier("Muse/Absolution/01 - Titre.flac", UN_MEGAOCTET);
        fichier("Muse/Absolution/Scans/pochette.flac", UN_MEGAOCTET);

        Arborescence arborescence = parcourir(racine);

        assertThat(arborescence.dossiers()).hasSize(1);
        assertThat(arborescence.nombreDeFichiers()).isEqualTo(1);
        assertThat(arborescence.dossiers().get(0).octetsHorsAudio()).isEqualTo(UN_MEGAOCTET);
    }

    @Test
    @DisplayName("une corbeille n'est ni parcourue ni pesée : elle n'appartient à aucun album")
    void laCorbeilleNEstPasPesee() throws IOException {
        fichier("Muse/Absolution/01 - Titre.flac", UN_MEGAOCTET);
        fichier("$RECYCLE.BIN/ancien.flac", 100 * UN_MEGAOCTET);

        Arborescence arborescence = parcourir(racine);

        assertThat(arborescence.nombreDeFichiers()).isEqualTo(1);
        assertThat(arborescence.octetsHorsAudio()).isZero();
    }

    @Test
    @DisplayName("un téléchargement inachevé n'est pas de la musique")
    void leTelechargementInacheveEstEcarte() throws IOException {
        fichier("Muse/Absolution/01 - Titre.flac", UN_MEGAOCTET);
        fichier("Muse/Absolution/02 - Titre.flac.part", UN_MEGAOCTET);

        assertThat(parcourir(racine).nombreDeFichiers()).isEqualTo(1);
    }

    @Test
    @DisplayName("le dossier parent d'un album est reconnu, sauf quand c'est une racine")
    void leParentEstReconnuSaufALaRacine() throws IOException {
        fichier("Muse/Absolution/01 - Titre.flac", UN_MEGAOCTET);
        fichier("Direct/01 - Titre.flac", UN_MEGAOCTET);

        Arborescence arborescence = parcourir(racine);

        DossierTrouve album = arborescence.dossiers().stream()
                .filter(dossier -> dossier.nom().equals("Absolution"))
                .findFirst()
                .orElseThrow();
        DossierTrouve direct = arborescence.dossiers().stream()
                .filter(dossier -> dossier.nom().equals("Direct"))
                .findFirst()
                .orElseThrow();

        assertThat(album.nomDuParent()).isEqualTo("Muse");
        assertThat(album.parentEstUneRacine()).isFalse();
        assertThat(direct.nomDuParent()).isNull();
        assertThat(direct.parentEstUneRacine()).isTrue();
    }

    @Test
    @DisplayName("deux racines qui se recouvrent sont ramenées à une seule")
    void lesRacinesQuiSeRecouvrentSontRamenees() throws IOException {
        fichier("Muse/Absolution/01 - Titre.flac", UN_MEGAOCTET);

        Arborescence arborescence = parcourir(racine, racine.resolve("Muse"), racine);

        assertThat(arborescence.racines()).hasSize(1);
        assertThat(arborescence.nombreDeFichiers()).isEqualTo(1);
    }

    @Test
    @DisplayName("un chemin qui n'est pas un dossier est ignoré sans faire échouer le parcours")
    void leCheminQuiNEstPasUnDossierEstIgnore() throws IOException {
        Path fichier = fichier("Muse/Absolution/01 - Titre.flac", UN_MEGAOCTET);

        Arborescence arborescence = parcourir(fichier, racine);

        assertThat(arborescence.nombreDeFichiers()).isEqualTo(1);
    }

    @Test
    @DisplayName("un dossier vide de musique ne figure pas dans l'arborescence")
    void leDossierSansMusiqueNeFigurePas() throws IOException {
        Files.createDirectories(racine.resolve("Vide"));
        fichier("Muse/Absolution/01 - Titre.flac", UN_MEGAOCTET);

        assertThat(parcourir(racine).dossiers()).hasSize(1);
    }

    @Test
    @DisplayName("un seuil de taille plus bas rattrape les fichiers écartés")
    void leSeuilAbaisseRattrapeLesFichiers() throws IOException {
        fichier("Muse/Absolution/01 - Court.flac", 1024);

        Arborescence arborescence = new Parcours(
                CriteresDeScan.parDefaut().avecTailleMinimale(512))
                .parcourir(List.of(racine));

        assertThat(arborescence.nombreDeFichiers()).isEqualTo(1);
    }

    @Test
    @DisplayName("une exclusion demandée s'ajoute à celles du programme")
    void lExclusionDemandeeSAjoute() throws IOException {
        fichier("Muse/Absolution/01 - Titre.flac", UN_MEGAOCTET);
        fichier("Karaoke/01 - Titre.flac", UN_MEGAOCTET);

        Arborescence arborescence = new Parcours(
                CriteresDeScan.parDefaut().avecExclusions(List.of("Karaoke")))
                .parcourir(List.of(racine));

        assertThat(arborescence.nombreDeFichiers()).isEqualTo(1);
    }
}
