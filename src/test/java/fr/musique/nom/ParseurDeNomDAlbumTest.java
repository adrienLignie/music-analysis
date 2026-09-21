package fr.musique.nom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Éprouve la lecture des noms de dossiers d'albums, sur les conventions qu'une discothèque réelle
 * mélange.
 *
 * <p>L'année maximale est figée : sans cela, un test écrit aujourd'hui cesserait de dire la même
 * chose l'an prochain.
 */
class ParseurDeNomDAlbumTest {

    private static final int ANNEE_MAXIMALE_FIGEE = 2027;

    private final ParseurDeNomDAlbum parseur = new ParseurDeNomDAlbum(ANNEE_MAXIMALE_FIGEE);

    @Nested
    @DisplayName("Artiste et titre")
    class ArtisteEtTitre {

        @Test
        @DisplayName("le tiret sépare l'artiste du titre")
        void leTiretSepareLArtisteDuTitre() {
            NomDAlbum nom = parseur.analyser("Daft Punk - Discovery (2001) [FLAC]");

            assertThat(nom.artiste()).contains("Daft Punk");
            assertThat(nom.cle()).isEqualTo("discovery");
            assertThat(nom.annee()).hasValue(2001);
            assertThat(nom.origineDeLArtiste()).isEqualTo(OrigineDeLArtiste.DOSSIER_DE_L_ALBUM);
        }

        @Test
        @DisplayName("à défaut, l'artiste est le dossier parent")
        void lArtisteVientDuDossierParent() {
            NomDAlbum nom = parseur.analyser("Nevermind (1991)", "Nirvana");

            assertThat(nom.artiste()).contains("Nirvana");
            assertThat(nom.cle()).isEqualTo("nevermind");
            assertThat(nom.origineDeLArtiste()).isEqualTo(OrigineDeLArtiste.DOSSIER_PARENT);
            assertThat(nom.cleComplete()).isEqualTo("nirvana | nevermind");
        }

        @Test
        @DisplayName("sous une racine analysée, il n'y a pas d'artiste à emprunter")
        void sansParentIlNyAPasDArtiste() {
            NomDAlbum nom = parseur.analyser("Nevermind (1991)", null);

            assertThat(nom.artiste()).isEmpty();
            assertThat(nom.origineDeLArtiste()).isEqualTo(OrigineDeLArtiste.INCONNUE);
            assertThat(nom.cleComplete()).isEqualTo("nevermind");
        }

        @Test
        @DisplayName("le dossier parent perd ses crochets techniques")
        void leParentEstNettoye() {
            NomDAlbum nom = parseur.analyser("Absolution", "Muse [FLAC]");

            assertThat(nom.cleArtiste()).isEqualTo("muse");
        }

        @Test
        @DisplayName("un artiste rangé à l'envers retrouve son article")
        void lArticleRangeADroiteRevientDevant() {
            NomDAlbum nom = parseur.analyser("Abbey Road", "Beatles, The");

            assertThat(nom.cleArtiste()).isEqualTo("the beatles");
        }

        @Test
        @DisplayName("« Various Artists » n'est pas un artiste mais une compilation")
        void lesArtistesMultiplesNeSontPasUnArtiste() {
            NomDAlbum nom = parseur.analyser("NRJ Music Tour 2018", "Various Artists");

            assertThat(nom.estCompilation()).isTrue();
            assertThat(nom.cleArtiste()).isEmpty();
            assertThat(nom.artiste()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Année")
    class Annee {

        @Test
        @DisplayName("l'année en tête de nom est reconnue et retirée du titre")
        void lAnneeEnTeteEstReconnue() {
            NomDAlbum nom = parseur.analyser("1973 - The Dark Side Of The Moon");

            assertThat(nom.annee()).hasValue(1973);
            assertThat(nom.cle()).isEqualTo("the dark side of the moon");
        }

        @Test
        @DisplayName("un titre qui est un nombre garde son nom")
        void leTitreNumeriqueSurvit() {
            assertThat(parseur.analyser("1984").cle()).isEqualTo("1984");
            assertThat(parseur.analyser("Van Halen - 1984 (1984)").cle()).isEqualTo("1984");
        }

        @Test
        @DisplayName("l'année d'une réédition ne devient pas l'année de l'album")
        void lAnneeDUneReeditionNEstPasCelleDeLOeuvre() {
            NomDAlbum nom = parseur.analyser("The Wall [Remastered 2011]");

            assertThat(nom.cle()).isEqualTo("the wall");
            assertThat(nom.editions()).contains("remastered");
            assertThat(nom.annee()).isEmpty();
        }

        @Test
        @DisplayName("une année trop lointaine n'en est pas une")
        void uneAnneeHorsBornesEstIgnoree() {
            assertThat(parseur.analyser("Album 2099").annee()).isEmpty();
            assertThat(parseur.analyser("Album 1850").annee()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Versions et éditions")
    class VersionsEtEditions {

        @Test
        @DisplayName("une mention de version au milieu du titre ne l'ampute pas")
        void laMentionAuMilieuResteDansLeTitre() {
            NomDAlbum nom = parseur.analyser("Nirvana - MTV Unplugged In New York");

            assertThat(nom.cle()).isEqualTo("mtv unplugged in new york");
            assertThat(nom.versions()).contains("unplugged");
        }

        @Test
        @DisplayName("une mention de version en fin de titre en est retirée")
        void laMentionFinaleEstRetiree() {
            NomDAlbum nom = parseur.analyser("Daft Punk - Discovery (Remixes)");

            assertThat(nom.cle()).isEqualTo("discovery");
            assertThat(nom.versions()).contains("remixes");
        }

        @Test
        @DisplayName("l'édition de luxe est relevée sans changer le titre")
        void lEditionEstReleveeSansToucherAuTitre() {
            NomDAlbum nom = parseur.analyser("Adele - 21 (Deluxe Edition)");

            assertThat(nom.cle()).isEqualTo("21");
            assertThat(nom.editions()).contains("deluxe", "edition");
            assertThat(nom.versions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Ce qui n'appartient pas au titre")
    class Technique {

        @Test
        @DisplayName("le format et le débit sont lus puis écartés du titre")
        void leFormatEtLeDebitSontEcartes() {
            NomDAlbum nom = parseur.analyser("Muse - Absolution - 2003 - MP3 320");

            assertThat(nom.artiste()).contains("Muse");
            assertThat(nom.cle()).isEqualTo("absolution");
            assertThat(nom.annee()).hasValue(2003);
            assertThat(nom.debitAnnonce()).hasValue(320);
        }

        @Test
        @DisplayName("le préréglage V0 vaut un débit annoncé")
        void lePrereglageVautUnDebit() {
            assertThat(parseur.analyser("Placebo - Meds [V0]").debitAnnonce()).hasValue(245);
        }

        @Test
        @DisplayName("un numéro de volume sépare deux disques d'une même série")
        void leVolumeEstLu() {
            NomDAlbum premier = parseur.analyser("Queen - Greatest Hits Vol. 1");
            NomDAlbum second = parseur.analyser("Queen - Greatest Hits Vol. 2");

            assertThat(premier.cle()).isEqualTo("greatest hits");
            assertThat(premier.volumeEffectif()).isOne();
            assertThat(second.volumeEffectif()).isEqualTo(2);
        }

        @Test
        @DisplayName("un nombre isolé du titre n'est pas un numéro de volume")
        void leNombreDuTitreResteDansLeTitre() {
            assertThat(parseur.analyser("Led Zeppelin IV").cle()).isEqualTo("led zeppelin iv");
            assertThat(parseur.analyser("Chicago 17").cle()).isEqualTo("chicago 17");
        }

        @Test
        @DisplayName("un titre générique est reconnu comme tel")
        void leTitreGeneriqueEstReconnu() {
            assertThat(parseur.analyser("Best Of", "Téléphone").aUnTitreGenerique()).isTrue();
            assertThat(parseur.analyser("Dure Limite", "Téléphone").aUnTitreGenerique()).isFalse();
        }

        @Test
        @DisplayName("un dossier entièrement technique garde malgré tout un titre")
        void unDossierSansTitreLisibleNEstPasVide() {
            assertThat(parseur.analyser("[FLAC]").cle()).isNotEmpty();
        }

        @Test
        @DisplayName("le titre alternatif entre parenthèses est relevé")
        void leTitreAlternatifEstReleve() {
            NomDAlbum nom = parseur.analyser("Amon Tobin - Foley Room (La Chambre Des Bruits)");

            assertThat(nom.cle()).isEqualTo("foley room");
            assertThat(nom.titresAlternatifs()).containsExactly("La Chambre Des Bruits");
        }

        @Test
        @DisplayName("un sigle entre crochets n'est pas un titre alternatif")
        void leSigleNEstPasUnTitre() {
            assertThat(parseur.analyser("Discovery [PSA]").titresAlternatifs()).isEmpty();
        }
    }
}
