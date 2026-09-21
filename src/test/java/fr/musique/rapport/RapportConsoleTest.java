package fr.musique.rapport;

import static fr.musique.doublons.AlbumsDEssai.album;
import static fr.musique.doublons.AlbumsDEssai.albumDePistes;
import static fr.musique.doublons.AlbumsDEssai.albumNumerote;
import static org.assertj.core.api.Assertions.assertThat;

import fr.musique.doublons.Album;
import fr.musique.doublons.AlbumsDEssai;
import fr.musique.doublons.ChercheurDeDoublons;
import fr.musique.doublons.ChercheurDePistes;
import fr.musique.doublons.GroupeDeDoublons;
import fr.musique.scan.Inventaire;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Éprouve le rapport lisible : ce qu'il montre, et ce qu'il tait quand il n'y a rien à dire. */
class RapportConsoleTest {

    private final ByteArrayOutputStream tampon = new ByteArrayOutputStream();

    private String ecrire(Analyse analyse, int top) {
        new RapportConsole(new PrintStream(tampon, true, StandardCharsets.UTF_8))
                .ecrire(analyse, top);
        return tampon.toString(StandardCharsets.UTF_8);
    }

    private static Inventaire inventaire(Album... albums) {
        return new Inventaire(List.of(albums), Set.of(Path.of("/musique")), Set.of(), 0, 0);
    }

    @Test
    @DisplayName("l'en-tête compte les albums, les pistes et le poids")
    void lEnTeteCompteToutCeQuiAEteVu() {
        String rapport = ecrire(new Analyse(
                inventaire(album("/musique/Muse/Absolution", "flac", 30_000_000L, 14)),
                List.of()), 10);

        assertThat(rapport).contains("1 albums retenus, 14 pistes, 420,00 Mo au total");
        assertThat(rapport).contains("ALBUMS EN DOUBLE : aucun");
    }

    @Test
    @DisplayName("le poids qui n'est pas de la musique est annoncé et rattaché à son album")
    void lePoidsHorsMusiqueEstAnnonce() {
        Album album = AlbumsDEssai.albumAvecPoidsAnnexe(
                "/musique/Muse/Absolution", "flac", 30_000_000L, 14, 200_000_000L);

        String rapport = ecrire(new Analyse(inventaire(album), List.of()), 10);

        assertThat(rapport).contains("200,00 Mo de plus ne sont pas de la musique");
        assertThat(rapport).contains("(+ 200,00 Mo hors musique)");
    }

    @Test
    @DisplayName("quelques pochettes ordinaires ne méritent pas d'être signalées")
    void lesPochettesOrdinairesSeTaisent() {
        // Un dixième du poids de l'album : en deçà, tout album correctement rangé en porte, et le
        // dire ferait du classement une liste de remarques.
        Album album = AlbumsDEssai.albumAvecPoidsAnnexe(
                "/musique/Muse/Absolution", "flac", 30_000_000L, 14, 500_000L);

        String rapport = ecrire(new Analyse(inventaire(album), List.of()), 10);

        assertThat(rapport).contains("de plus ne sont pas de la musique");
        assertThat(rapport).doesNotContain("hors musique)");
    }

    @Test
    @DisplayName("le seuil du dixième est franchi à l'octet près")
    void leSeuilDuDixiemeEstFranchiALOctetPres() {
        // Le seuil ne vaut que s'il est surveillé : un test loin de la borne laisserait changer le
        // chiffre sans que rien ne tombe. Dix pistes d'un mégaoctet font un album de dix
        // mégaoctets, dont le dixième est exactement un mégaoctet.
        Album pile = AlbumsDEssai.albumAvecPoidsAnnexe(
                "/musique/Muse/Pile", "flac", 1_000_000L, 10, 1_000_000L);
        Album justeEnDessous = AlbumsDEssai.albumAvecPoidsAnnexe(
                "/musique/Muse/Dessous", "flac", 1_000_000L, 10, 999_999L);

        assertThat(ecrire(new Analyse(inventaire(pile), List.of()), 10))
                .contains("hors musique)");
        tampon.reset();
        assertThat(ecrire(new Analyse(inventaire(justeEnDessous), List.of()), 10))
                .doesNotContain("hors musique)");
    }

    @Test
    @DisplayName("un album sans rien d'autre que sa musique ne porte aucune mention")
    void lAlbumSansAnnexeNePorteAucuneMention() {
        Album album = AlbumsDEssai.albumAvecPoidsAnnexe(
                "/musique/Muse/Absolution", "flac", 1_000_000L, 10, 0L);

        String rapport = ecrire(new Analyse(inventaire(album), List.of()), 10);

        assertThat(rapport).doesNotContain("ne sont pas de la musique");
        assertThat(rapport).doesNotContain("hors musique)");
    }

    @Test
    @DisplayName("un dossier qui ne pèse rien ne se compare à rien")
    void leDossierSansPoidsNeSeCompareARien() {
        // Toutes ses pistes sont atteintes par un autre chemin : il n'occupe aucune place. Dire
        // que ses pochettes en représentent le dixième n'aurait aucun sens, et la part qu'elles
        // représentent serait infinie.
        Album reel = album("/musique/Muse/Absolution", "flac", 1_000_000L, 10);
        Album fantome = new Album(
                reel.dossier(), reel.pistes(), reel.nom(), 0L, 4_000_000L);

        String rapport = ecrire(new Analyse(inventaire(fantome), List.of()), 10);

        assertThat(rapport).doesNotContain("hors musique)");
    }

    @Test
    @DisplayName("un groupe de doublons montre son motif, son gain et l'exemplaire à garder")
    void leGroupeMontreCeQuiCompte() {
        Album flac = album("/musique/Muse/Absolution (2003)", "flac", 30_000_000L, 14);
        Album mp3 = album("/sauvegarde/Muse/Absolution (2003)", "mp3", 9_000_000L, 14);
        List<GroupeDeDoublons> groupes = new ChercheurDeDoublons().chercher(List.of(flac, mp3));

        String rapport = ecrire(new Analyse(inventaire(flac, mp3), groupes), 10);

        assertThat(rapport).contains("ALBUMS EN DOUBLE : 1 groupes, 126,00 Mo récupérables");
        assertThat(rapport).contains("Confiance FORTE");
        assertThat(rapport).contains("garder ?");
        assertThat(rapport).contains("14 pistes  flac");
    }

    @Test
    @DisplayName("le classement porte sur les dossiers, du plus lourd au plus léger")
    void leClassementPorteSurLesDossiers() {
        Album lourd = album("/musique/Muse/Absolution", "flac", 30_000_000L, 14);
        Album leger = album("/musique/Adele/21", "mp3", 9_000_000L, 12);

        String rapport = ecrire(new Analyse(inventaire(leger, lourd), List.of()), 2);

        assertThat(rapport).contains("LES 2 ALBUMS LES PLUS LOURDS");
        assertThat(rapport.indexOf("Absolution")).isLessThan(rapport.indexOf("Adele"));
    }

    @Test
    @DisplayName("les sections sans rien à dire n'apparaissent pas")
    void lesSectionsVidesNApparaissentPas() {
        String rapport = ecrire(new Analyse(
                inventaire(album("/musique/Muse/Absolution", "flac", 30_000_000L, 14)),
                List.of()), 10);

        assertThat(rapport).doesNotContain("ALBUMS INCOMPLETS");
        assertThat(rapport).doesNotContain("PISTES SUSPECTES");
        assertThat(rapport).doesNotContain("DOSSIERS MAL RANGÉS");
        assertThat(rapport).doesNotContain("MORCEAUX EN DOUBLE");
    }

    @Test
    @DisplayName("un album à trous est montré avec les numéros qui manquent")
    void lAlbumATrousEstMontre() {
        Album ampute = albumNumerote("/musique/Queen/A Night At The Opera", "mp3", 9_000_000L,
                1, 2, 3, 7, 8);

        String rapport = ecrire(new Analyse(inventaire(ampute), List.of()), 10);

        assertThat(rapport).contains("ALBUMS INCOMPLETS (1)");
        assertThat(rapport).contains("5 pistes sur 8, manquent 4, 5, 6");
    }

    @Test
    @DisplayName("les morceaux en double sont présentés avec leur avertissement")
    void lesMorceauxEnDoubleSontPresentes() {
        Album range = albumDePistes("/musique/Daft Punk/Discovery", 30_000_000L,
                "01 - One More Time.flac");
        Album vrac = albumDePistes("/musique/Divers/Tri", 8_000_000L,
                "Daft Punk - One More Time.mp3");
        Analyse analyse = new Analyse(
                inventaire(range, vrac),
                List.of(),
                ChercheurDePistes.chercher(List.of(range, vrac), Set.of()),
                0);

        String rapport = ecrire(analyse, 10);

        assertThat(rapport).contains("MORCEAUX EN DOUBLE (1)");
        assertThat(rapport).contains("cette liste se lit, elle ne s'applique pas");
    }

    @Test
    @DisplayName("les groupes écartés pour cause de liens durs sont annoncés")
    void lesGroupesEcartesSontAnnonces() {
        String rapport = ecrire(new Analyse(
                inventaire(album("/musique/Muse/Absolution", "flac", 30_000_000L, 14)),
                List.of(), List.of(), 2), 10);

        assertThat(rapport).contains("2 groupes écartés");
    }

    @Test
    @DisplayName("le rapport en ASCII ne porte ni accent ni tracé")
    void leRapportAsciiEstEntierementTranslittere() {
        Album ampute = albumNumerote("/musique/Queen/A Night At The Opera", "mp3", 9_000_000L,
                1, 2, 3, 7);
        new RapportConsole(
                new PrintStream(tampon, true, StandardCharsets.UTF_8), Glyphes.ascii())
                .ecrire(new Analyse(inventaire(ampute), List.of()), 10);

        String rapport = tampon.toString(StandardCharsets.UTF_8);

        assertThat(rapport).contains("ALBUMS INCOMPLETS");
        assertThat(rapport).doesNotContain("é").doesNotContain("─").doesNotContain("«");
    }
}
