package fr.musique.nom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Éprouve la construction de l'identité d'un album à partir du chemin et des pistes. */
class IdentificationDAlbumTest {

    private static final int ANNEE_MAXIMALE_FIGEE = 2027;

    private final ParseurDeNomDePiste parseurDePiste = new ParseurDeNomDePiste();
    private final IdentificationDAlbum identification =
            new IdentificationDAlbum(new ParseurDeNomDAlbum(ANNEE_MAXIMALE_FIGEE));

    private List<NomDePiste> pistes(String... noms) {
        return List.of(noms).stream().map(parseurDePiste::analyser).toList();
    }

    @Nested
    @DisplayName("Dossiers qui ne sont pas des albums")
    class Disques {

        @Test
        @DisplayName("un dossier de disque est reconnu sous toutes ses formes")
        void leDossierDeDisqueEstReconnu() {
            assertThat(IdentificationDAlbum.estUnDossierDeDisque("CD1")).isTrue();
            assertThat(IdentificationDAlbum.estUnDossierDeDisque("CD 2")).isTrue();
            assertThat(IdentificationDAlbum.estUnDossierDeDisque("Disque-3")).isTrue();
            assertThat(IdentificationDAlbum.estUnDossierDeDisque("Disc II")).isTrue();
        }

        @Test
        @DisplayName("un album dont le titre commence par un mot de disque n'en est pas un")
        void leTitreQuiCommenceCommeUnDisqueNEnEstPasUn() {
            assertThat(IdentificationDAlbum.estUnDossierDeDisque("CD1 - Nevermind")).isFalse();
            assertThat(IdentificationDAlbum.estUnDossierDeDisque("Discovery")).isFalse();
            assertThat(IdentificationDAlbum.estUnDossierDeDisque(null)).isFalse();
        }

        @Test
        @DisplayName("un dossier fourre-tout ne porte pas de titre d'album")
        void leDossierFourreToutEstReconnu() {
            assertThat(IdentificationDAlbum.estUnDossierSansTitre("Musique")).isTrue();
            assertThat(IdentificationDAlbum.estUnDossierSansTitre("Nouveau dossier")).isTrue();
            assertThat(IdentificationDAlbum.estUnDossierSansTitre(null)).isTrue();
            assertThat(IdentificationDAlbum.estUnDossierSansTitre("Nevermind")).isFalse();
        }
    }

    @Nested
    @DisplayName("Artiste")
    class Artiste {

        @Test
        @DisplayName("l'artiste du chemin l'emporte sur celui des pistes")
        void leCheminPasseAvantLesPistes() {
            NomDAlbum nom = identification.identifier(
                    "Daft Punk - Discovery", null,
                    pistes("01 - Stardust - Music Sounds Better.mp3"));

            assertThat(nom.cleArtiste()).isEqualTo("daft punk");
            assertThat(nom.origineDeLArtiste()).isEqualTo(OrigineDeLArtiste.DOSSIER_DE_L_ALBUM);
        }

        @Test
        @DisplayName("à défaut, l'artiste que toutes les pistes s'accordent à nommer")
        void lesPistesDonnentLArtisteQuandLeCheminSeTait() {
            NomDAlbum nom = identification.identifier(
                    "Best Of", null,
                    pistes("01 - Téléphone - Cendrillon.mp3", "02 - Téléphone - Un Autre Monde.mp3"));

            assertThat(nom.artiste()).contains("Téléphone");
            assertThat(nom.origineDeLArtiste()).isEqualTo(OrigineDeLArtiste.PISTES);
        }

        @Test
        @DisplayName("des pistes qui ne s'accordent pas ne donnent aucun artiste")
        void desPistesEnDesaccordNeDonnentRien() {
            NomDAlbum nom = identification.identifier(
                    "Best Of", null,
                    pistes("01 - Téléphone - Cendrillon.mp3", "02 - Indochine - L'Aventurier.mp3"));

            assertThat(nom.artiste()).isEmpty();
            assertThat(nom.origineDeLArtiste()).isEqualTo(OrigineDeLArtiste.INCONNUE);
        }

        @Test
        @DisplayName("une piste sans artiste suffit à renoncer")
        void unePisteMuetteSuffitARenoncer() {
            NomDAlbum nom = identification.identifier(
                    "Best Of", null,
                    pistes("01 - Téléphone - Cendrillon.mp3", "02 - Un Autre Monde.mp3"));

            assertThat(nom.artiste()).isEmpty();
        }

        @Test
        @DisplayName("un dossier sans piste ne donne pas d'artiste non plus")
        void leDossierVideNeDonneRien() {
            assertThat(identification.identifier("Best Of", null, List.of()).artiste()).isEmpty();
        }

        @Test
        @DisplayName("des pistes qui nomment une compilation ne donnent pas d'artiste")
        void laCompilationNeDonnePasDArtiste() {
            NomDAlbum nom = identification.identifier(
                    "Hits 2020", null,
                    pistes("01 - Various Artists - Un titre.mp3",
                            "02 - Various Artists - Un autre.mp3"));

            assertThat(nom.artiste()).isEmpty();
        }
    }
}
