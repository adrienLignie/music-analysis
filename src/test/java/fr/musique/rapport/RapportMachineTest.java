package fr.musique.rapport;

import static fr.musique.doublons.AlbumsDEssai.album;
import static fr.musique.doublons.AlbumsDEssai.albumNumerote;
import static org.assertj.core.api.Assertions.assertThat;

import fr.musique.doublons.Album;
import fr.musique.doublons.ChercheurDeDoublons;
import fr.musique.doublons.GroupeDeDoublons;
import fr.musique.doublons.VerificationParLesEtiquettes;
import fr.musique.media.Etiquette;
import fr.musique.media.Etiquettes;
import fr.musique.scan.Inventaire;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Éprouve les deux formes destinées aux scripts.
 *
 * <p>Ce qu'on vérifie n'est pas la beauté du document mais son <b>contrat</b> : les poids en
 * octets, les champs présents, l'échappement. Un script qui lit ce document doit pouvoir compter
 * dessus.
 */
class RapportMachineTest {

    private final ByteArrayOutputStream tampon = new ByteArrayOutputStream();

    private PrintStream sortie() {
        return new PrintStream(tampon, true, StandardCharsets.UTF_8);
    }

    private String lu() {
        return tampon.toString(StandardCharsets.UTF_8);
    }

    private static Inventaire inventaire(Album... albums) {
        return new Inventaire(List.of(albums), Set.of(Path.of("/musique")), Set.of(), 0, 0);
    }

    private static Analyse analyseAvecUnDoublon() {
        Album flac = album("/musique/Muse/Absolution (2003)", "flac", 30_000_000L, 14);
        Album mp3 = album("/sauvegarde/Muse/Absolution (2003)", "mp3", 9_000_000L, 14);
        List<GroupeDeDoublons> groupes = new ChercheurDeDoublons().chercher(List.of(flac, mp3));
        return new Analyse(inventaire(flac, mp3), groupes);
    }

    @Nested
    @DisplayName("JSON")
    class Json {

        @Test
        @DisplayName("le document porte toutes les sections et les poids en octets")
        void leDocumentPorteToutesLesSections() {
            new RapportJson(sortie()).ecrire(analyseAvecUnDoublon(), 10);

            String json = lu();
            assertThat(json).contains("\"version\": 2");
            assertThat(json).contains("\"albums\": 2");
            assertThat(json).contains("\"octets\": 546000000");
            assertThat(json).contains("\"octetsRecuperables\": 126000000");
            assertThat(json).contains("\"confiance\": \"FORTE\"");
            assertThat(json).contains("\"garder\": true");
            assertThat(json).contains("\"format\": \"flac\"");
            assertThat(json).contains("\"morceauxEnDouble\": []");
            assertThat(json).contains("\"plusGrosAlbums\"");
            assertThat(json).contains("\"albumsIncomplets\"");
            assertThat(json).contains("\"pistesSuspectes\"");
            assertThat(json).contains("\"dossiersMalRanges\"");
        }

        @Test
        @DisplayName("un débit non mesuré est nul, pas absent")
        void leDebitNonMesureEstNul() {
            new RapportJson(sortie()).ecrire(analyseAvecUnDoublon(), 10);

            assertThat(lu()).contains("\"octetsParSeconde\": null");
        }

        @Test
        @DisplayName("un son non sondé est nul, pas faux")
        void leSonNonSondeEstNul() {
            // La nuance porte tout : un script qui prendrait « pas su » pour « pas le même son »
            // se priverait d'un doublon, l'inverse lui ferait supprimer autre chose.
            new RapportJson(sortie()).ecrire(analyseAvecUnDoublon(), 10);

            assertThat(lu()).contains("\"memeSonQueLExemplaireAGarder\": null");
        }

        @Test
        @DisplayName("un son démontré identique est affirmé, et le groupe devient certain")
        void leSonIdentiqueEstAffirme() {
            Album premier = album("/musique/Muse/Absolution", "flac", 30_000_000L, 2);
            Album second = album("/sauvegarde/Muse/Absolution", "flac", 30_000_000L, 2);
            Etiquettes etiquettes = etiquetterLeMemeSon(premier, second);
            List<GroupeDeDoublons> groupes = VerificationParLesEtiquettes.verifier(
                    new ChercheurDeDoublons().chercher(List.of(premier, second)), etiquettes);

            new RapportJson(sortie()).ecrire(new Analyse(
                    inventaire(premier, second).avecEtiquettes(etiquettes), groupes), 10);

            assertThat(lu()).contains("\"confiance\": \"CERTAINE\"");
            assertThat(lu()).contains("\"memeSonQueLExemplaireAGarder\": true");
            assertThat(lu()).doesNotContain("\"memeSonQueLExemplaireAGarder\": false");
        }

        /** Donne aux pistes de deux albums, rang par rang, la même empreinte de son. */
        private Etiquettes etiquetterLeMemeSon(Album premier, Album second) {
            Map<Path, Etiquette> table = new HashMap<>();
            for (Album album : List.of(premier, second)) {
                for (int rang = 0; rang < album.pistes().size(); rang++) {
                    table.put(album.pistes().get(rang).chemin(), new Etiquette(
                            OptionalDouble.of(240),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            OptionalInt.empty(),
                            OptionalInt.empty(),
                            Optional.of("flac-md5:piste" + rang)));
                }
            }
            return Etiquettes.de(table);
        }

        @Test
        @DisplayName("les numéros manquants sont un tableau de nombres")
        void lesNumerosManquantsSontUnTableau() {
            Album ampute = albumNumerote("/musique/Queen/A Night At The Opera", "mp3",
                    9_000_000L, 1, 2, 3, 7, 8);

            new RapportJson(sortie()).ecrire(new Analyse(inventaire(ampute), List.of()), 10);

            assertThat(lu()).contains("\"numerosManquants\": [4, 5, 6]");
        }

        @Test
        @DisplayName("les contre-obliques d'un chemin Windows sont doublées")
        void lesCheminsWindowsSontEchappes() {
            Album album = album("D:\\Musique\\Muse\\Absolution", "flac", 30_000_000L, 14);

            new RapportJson(sortie()).ecrire(new Analyse(inventaire(album), List.of()), 10);

            assertThat(lu()).contains("D:\\\\Musique\\\\Muse\\\\Absolution");
        }
    }

    @Nested
    @DisplayName("CSV")
    class Csv {

        @Test
        @DisplayName("chaque section a ses lignes, sous une même en-tête")
        void chaqueSectionADesLignes() {
            new RapportCsv(sortie()).ecrire(analyseAvecUnDoublon(), 10);

            List<String> lignes = lu().lines().toList();
            assertThat(lignes.get(0)).startsWith("section,groupe,confiance");
            assertThat(lignes).anyMatch(ligne -> ligne.startsWith("doublon,1,FORTE,true"));
            assertThat(lignes).anyMatch(ligne -> ligne.startsWith("top,"));
        }

        @Test
        @DisplayName("un champ qui porte une virgule est mis entre guillemets")
        void leChampAVirguleEstProtege() {
            new RapportCsv(sortie()).ecrire(analyseAvecUnDoublon(), 10);

            assertThat(lu()).contains("\"Muse — « absolution »");
        }

        @Test
        @DisplayName("les albums incomplets portent leur compte et leurs numéros manquants")
        void lesIncompletsPortentLeurCompte() {
            Album ampute = albumNumerote("/musique/Queen/A Night At The Opera", "mp3",
                    9_000_000L, 1, 2, 3, 7, 8);

            new RapportCsv(sortie()).ecrire(new Analyse(inventaire(ampute), List.of()), 10);

            assertThat(lu()).contains("incomplet,");
            assertThat(lu()).contains("numéros manquants : [4, 5, 6]");
        }
    }
}
