package fr.musique.rapport;

import static fr.musique.doublons.AlbumsDEssai.album;
import static fr.musique.doublons.AlbumsDEssai.albumDePistes;
import static fr.musique.doublons.AlbumsDEssai.albumNumerote;
import static fr.musique.doublons.AlbumsDEssai.albumSousUneRacine;
import static org.assertj.core.api.Assertions.assertThat;

import fr.musique.doublons.Album;
import fr.musique.doublons.ChercheurDeDoublons;
import fr.musique.doublons.ChercheurDePistes;
import fr.musique.doublons.GroupeDeDoublons;
import fr.musique.doublons.Piste;
import fr.musique.media.Etiquette;
import fr.musique.media.Etiquettes;
import fr.musique.scan.Inventaire;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Éprouve les trois formes de rapport sur une analyse où <b>toutes</b> les sections ont quelque
 * chose à dire.
 *
 * <p>Les tests précédents vérifient chaque section pour elle-même ; celui-ci vérifie qu'aucune ne
 * disparaît quand les autres sont présentes, et que les trois formes rendent le même contenu.
 */
class RapportCompletTest {

    private final ByteArrayOutputStream tampon = new ByteArrayOutputStream();

    private PrintStream sortie() {
        return new PrintStream(tampon, true, StandardCharsets.UTF_8);
    }

    private String lu() {
        return tampon.toString(StandardCharsets.UTF_8);
    }

    /**
     * Une analyse qui porte un exemplaire de chaque cas : un doublon d'albums, un morceau répété,
     * un album à trous, une piste au débit invraisemblable et un dossier mal rangé.
     */
    private static Analyse analyseComplete() {
        Album flac = album("/musique/Muse/Absolution (2003)", "flac", 30_000_000L, 14);
        Album mp3 = album("/sauvegarde/Muse/Absolution (2003)", "mp3", 9_000_000L, 14);
        Album ampute = albumNumerote("/musique/Queen/A Night At The Opera (1975)", "mp3",
                9_000_000L, 1, 2, 3, 7, 8);
        Album fauxFlac = album("/musique/Pixies/Doolittle (1989)", "flac", 3_000_000L, 12);
        Album vrac = albumDePistes("/musique", 8_000_000L, "Daft Punk - One More Time.mp3");
        Album range = albumDePistes("/musique/Daft Punk/Discovery (2001)", 30_000_000L,
                "01 - One More Time.flac", "02 - Aerodynamic.flac");
        Album orphelin = albumSousUneRacine("/musique/Nevermind", "flac", 20_000_000L, 12);

        List<Album> tous = List.of(flac, mp3, ampute, fauxFlac, vrac, range, orphelin);
        List<GroupeDeDoublons> doublons = new ChercheurDeDoublons().chercher(tous);
        Inventaire inventaire = new Inventaire(
                tous, Set.of(Path.of("/musique")), Set.of(), 3, 1, durees(fauxFlac, 240));
        return new Analyse(
                inventaire,
                doublons,
                ChercheurDePistes.chercher(tous, Set.of()),
                2);
    }

    /** Étiquettes qui ne portent qu'une durée, pour que le débit d'un dossier soit mesurable. */
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
    @DisplayName("le rapport lisible montre toutes les sections à la fois")
    void leRapportLisibleMontreTout() {
        new RapportConsole(sortie()).ecrire(analyseComplete(), 5);

        String rapport = lu();
        assertThat(rapport).contains("fichiers écartés (trop petits ou illisibles)");
        assertThat(rapport).contains("fichiers ont été ouverts");
        assertThat(rapport).contains("ALBUMS EN DOUBLE :");
        assertThat(rapport).contains("2 groupes écartés");
        assertThat(rapport).contains("MORCEAUX EN DOUBLE");
        assertThat(rapport).contains("LES 5 ALBUMS LES PLUS LOURDS");
        assertThat(rapport).contains("ALBUMS INCOMPLETS");
        assertThat(rapport).contains("PISTES SUSPECTES");
        assertThat(rapport).contains("kbit/s");
        assertThat(rapport).contains("DOSSIERS MAL RANGÉS");
        assertThat(rapport).contains("pistes posées à la racine");
    }

    @Test
    @DisplayName("le JSON porte toutes les sections, chacune avec ses champs")
    void leJsonPorteTout() {
        // Sept dossiers au classement : le plus léger est celui dont le débit a été mesuré, et
        // c'est ce débit qu'on vient vérifier ici.
        new RapportJson(sortie()).ecrire(analyseComplete(), 7);

        String json = lu();
        assertThat(json).contains("\"groupesEcartesMemesFichiers\": 2");
        assertThat(json).contains("\"morceauxEnDouble\": [{");
        assertThat(json).contains("\"numerosManquants\": [4, 5, 6]");
        assertThat(json).contains("\"nature\": \"DEBIT\"");
        assertThat(json).contains("\"nature\": \"PISTES_EN_VRAC\"");
        assertThat(json).contains("\"octetsParSeconde\": 12500");
        assertThat(json).contains("\"fichiersIgnores\": 3");
        assertThat(json).contains("\"dossiersIllisibles\": 1");
    }

    @Test
    @DisplayName("le CSV donne une ligne par dossier et par fichier, section par section")
    void leCsvDonneUneLigneParObjet() {
        new RapportCsv(sortie()).ecrire(analyseComplete(), 5);

        List<String> sections = new ArrayList<>();
        for (String ligne : lu().lines().skip(1).toList()) {
            String section = ligne.substring(0, ligne.indexOf(','));
            if (!sections.contains(section)) {
                sections.add(section);
            }
        }

        assertThat(sections)
                .containsExactly("doublon", "morceau", "top", "incomplet", "suspecte", "rangement");
    }

    @Test
    @DisplayName("un classement de taille nulle ne rend aucune ligne")
    void leClassementVideNeRendRien() {
        new RapportCsv(sortie()).ecrire(analyseComplete(), 0);

        assertThat(lu().lines().filter(ligne -> ligne.startsWith("top,"))).isEmpty();
    }
}
