package fr.musique.nom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Éprouve les noms sur lesquels la lecture pourrait déraper.
 *
 * <p>Ce sont les cas qui ne figurent dans aucune convention de nommage : dossiers vides de sens,
 * séparateurs en cascade, titres réduits à un caractère. Aucun ne doit produire d'exception, et
 * aucun ne doit produire de clé vide, sous laquelle tous les albums mal nommés se retrouveraient
 * doublons les uns des autres.
 */
class CasLimitesDesNomsTest {

    private static final int ANNEE_MAXIMALE_FIGEE = 2027;

    private final ParseurDeNomDAlbum parseurDAlbum = new ParseurDeNomDAlbum(ANNEE_MAXIMALE_FIGEE);
    private final ParseurDeNomDePiste parseurDePiste = new ParseurDeNomDePiste();

    @Nested
    @DisplayName("Noms de dossiers")
    class Dossiers {

        @Test
        @DisplayName("un dossier réduit à un séparateur garde une clé non vide")
        void leDossierReduitAUnSeparateur() {
            assertThat(parseurDAlbum.analyser(" - ").cle()).isNotNull();
            assertThat(parseurDAlbum.analyser("()").cle()).isNotNull();
            assertThat(parseurDAlbum.analyser("").cle()).isEmpty();
        }

        @Test
        @DisplayName("plusieurs séparateurs de suite ne créent pas de segments vides")
        void lesSeparateursEnCascade() {
            NomDAlbum nom = parseurDAlbum.analyser("Muse  -  Absolution  -  2003");

            assertThat(nom.artiste()).contains("Muse");
            assertThat(nom.cle()).isEqualTo("absolution");
            assertThat(nom.annee()).hasValue(2003);
        }

        @Test
        @DisplayName("un groupe vide ou d'un seul caractère n'est pas un titre alternatif")
        void lesGroupesVidesSontIgnores() {
            assertThat(parseurDAlbum.analyser("Absolution ()").titresAlternatifs()).isEmpty();
            assertThat(parseurDAlbum.analyser("Absolution (!)").titresAlternatifs()).isEmpty();
            assertThat(parseurDAlbum.analyser("Absolution [2003]").annee()).hasValue(2003);
        }

        @Test
        @DisplayName("un dossier entièrement fait de mentions garde un titre")
        void leDossierToutEnMentionsGardeUnTitre() {
            assertThat(parseurDAlbum.analyser("Remastered").cle()).isEqualTo("remastered");
            assertThat(parseurDAlbum.analyser("Live").cle()).isEqualTo("live");
        }

        @Test
        @DisplayName("un dossier parent vide de sens ne donne pas d'artiste")
        void leParentVideNeDonnePasDArtiste() {
            assertThat(parseurDAlbum.analyser("Absolution", "   ").cleArtiste()).isEmpty();
            assertThat(parseurDAlbum.analyser("Absolution", "[FLAC]").cleArtiste())
                    .isEqualTo("flac");
        }

        @Test
        @DisplayName("un volume annoncé sans nombre reste dans le titre")
        void leVolumeSansNombreResteDansLeTitre() {
            assertThat(parseurDAlbum.analyser("Greatest Hits Vol").cle())
                    .isEqualTo("greatest hits vol");
        }
    }

    @Nested
    @DisplayName("Noms de fichiers")
    class Fichiers {

        @Test
        @DisplayName("un tiret sans rien derrière n'annonce pas d'artiste")
        void leTiretSansSuiteNAnnonceRien() {
            assertThat(parseurDePiste.analyser("Muse - .mp3").artiste()).isEmpty();
            assertThat(parseurDePiste.analyser("Muse - Hysteria.mp3").artiste()).contains("Muse");
        }

        @Test
        @DisplayName("un nom réduit à son numéro garde son numéro et perd son titre")
        void leNomReduitASonNumero() {
            NomDePiste piste = parseurDePiste.analyser("07.mp3");

            assertThat(piste.numero()).hasValue(7);
            assertThat(piste.cle()).isEmpty();
        }

        @Test
        @DisplayName("une lettre seule devant un tiret n'est ni un disque ni un artiste")
        void laLettreSeuleNEstNiDisqueNiArtiste() {
            NomDePiste piste = parseurDePiste.analyser("A - Hysteria.mp3");

            // Une lettre isolée est bien plus souvent une face de vinyle ou un rangement
            // alphabétique qu'un nom d'artiste : le titre est donc gardé entier.
            assertThat(piste.disque()).isEmpty();
            assertThat(piste.artiste()).isEmpty();
            assertThat(piste.titre()).isEqualTo("A - Hysteria");
        }

        @Test
        @DisplayName("un nom fait de séparateurs ne rend ni numéro ni titre")
        void leNomFaitDeSeparateurs() {
            NomDePiste piste = parseurDePiste.analyser("---.mp3");

            assertThat(piste.numero()).isEmpty();
            assertThat(piste.cle()).isEmpty();
            assertThat(piste.extension()).isEqualTo("mp3");
        }
    }

    @Nested
    @DisplayName("Patron de dossier")
    class Patron {

        @Test
        @DisplayName("un patron qui mangerait tout le nom s'arrête avant")
        void lePatronNeMangePasToutLeNom() {
            List<String> freres = List.of("Muse.mp3", "Muse.mp3", "Muse.mp3");
            PrefixeDeCollection prefixe = PrefixeDeCollection.detecter(freres);

            assertThat(prefixe.retirerDe("Muse")).isEqualTo("Muse");
        }

        @Test
        @DisplayName("des fichiers sans rien en commun ne forment pas de patron")
        void sansRienEnCommunAucunPatron() {
            PrefixeDeCollection prefixe = PrefixeDeCollection.detecter(
                    List.of("Hysteria.mp3", "Time.mp3", "Sing.mp3"));

            assertThat(prefixe.retirerDe("Hysteria.mp3")).isEqualTo("Hysteria.mp3");
        }

        @Test
        @DisplayName("un patron ne retire rien d'un nom qui ne commence pas par lui")
        void lePatronNeRetireRienDUnAutreNom() {
            PrefixeDeCollection prefixe = PrefixeDeCollection.detecter(List.of(
                    "Muse - Absolution - Hysteria.mp3",
                    "Muse - Absolution - Time.mp3",
                    "Muse - Absolution - Sing.mp3"));

            assertThat(prefixe.retirerDe("Radiohead - Creep.mp3"))
                    .isEqualTo("Radiohead - Creep.mp3");
        }
    }
}
