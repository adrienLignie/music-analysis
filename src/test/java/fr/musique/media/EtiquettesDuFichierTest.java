package fr.musique.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Éprouve la lecture des durées et des étiquettes, format par format.
 *
 * <p>Chaque format a sa manière de mentir ou de se taire, et c'est précisément ce qu'on vérifie :
 * ce qui est lu quand tout va bien, et ce qui est rendu quand rien ne va.
 */
class EtiquettesDuFichierTest {

    @TempDir
    private Path racine;

    @Nested
    @DisplayName("flac")
    class Flac {

        @Test
        @DisplayName("la durée se déduit exactement du nombre d'échantillons")
        void laDureeEstExacte() throws IOException {
            Path fichier = FichiersDEssai.flac(
                    racine.resolve("01 - Hysteria.flac"), 44_100L * 240, 44_100,
                    FichiersDEssai.etiquettesOrdinaires());

            Etiquette etiquette = EtiquettesDuFichier.lire(fichier);

            assertThat(etiquette.secondes()).hasValue(240.0);
            assertThat(etiquette.artiste()).contains("Muse");
            assertThat(etiquette.album()).contains("Absolution");
            assertThat(etiquette.titre()).contains("Hysteria");
            assertThat(etiquette.numero()).hasValue(5);
            assertThat(etiquette.annee()).hasValue(2003);
        }

        @Test
        @DisplayName("l'empreinte du signal est lue dans le premier bloc")
        void lEmpreinteEstLue() throws IOException {
            Path fichier = FichiersDEssai.flac(
                    racine.resolve("01 - Hysteria.flac"), 44_100L * 240, 44_100, Map.of(),
                    FichiersDEssai.empreinte(0xAB));

            assertThat(EtiquettesDuFichier.lire(fichier).empreinteAudio())
                    .contains("flac-md5:" + "ab".repeat(16));
        }

        @Test
        @DisplayName("une empreinte nulle est tenue pour absente, non pour une valeur")
        void lEmpreinteNulleEstTenuePourAbsente() throws IOException {
            // Sans cette réserve, tous les fichiers d'un encodeur qui ne la calcule pas
            // porteraient la même empreinte, et seraient déclarés identiques entre eux.
            Path fichier = FichiersDEssai.flac(
                    racine.resolve("01 - Hysteria.flac"), 44_100L * 240, 44_100, Map.of());

            assertThat(EtiquettesDuFichier.lire(fichier).empreinteAudio()).isEmpty();
        }

        @Test
        @DisplayName("un premier bloc écourté livre la durée sans inventer d'empreinte")
        void leBlocEcourteNInventePasDEmpreinte() throws IOException {
            // Lire seize octets là où le bloc s'arrête reviendrait à prendre le début du bloc
            // suivant pour une empreinte, et donc à comparer des étiquettes à un son.
            Path fichier = FichiersDEssai.flacAuBlocEcourte(
                    racine.resolve("ecourte.flac"), 44_100L * 240, 44_100);

            Etiquette etiquette = EtiquettesDuFichier.lire(fichier);

            assertThat(etiquette.secondes()).hasValue(240.0);
            assertThat(etiquette.empreinteAudio()).isEmpty();
        }

        @Test
        @DisplayName("un flac sans échantillon ne rend pas de durée")
        void leFlacSansDureeNEnRendPas() throws IOException {
            Etiquette etiquette = EtiquettesDuFichier.lire(
                    FichiersDEssai.flacSansDuree(racine.resolve("muet.flac")));

            assertThat(etiquette.secondes()).isEmpty();
        }

        @Test
        @DisplayName("un fichier qui n'est pas un flac ne rend rien")
        void leFauxFlacNeRendRien() throws IOException {
            assertThat(EtiquettesDuFichier.lire(
                    FichiersDEssai.fichierQuiNEnEstPasUn(racine.resolve("faux.flac"))).estVide())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("mp3")
    class Mp3 {

        @Test
        @DisplayName("les étiquettes sont lues et la durée déduite du débit")
        void lesEtiquettesEtLaDureeSontLues() throws IOException {
            Map<String, String> champs = new LinkedHashMap<>();
            champs.put("TPE1", "Téléphone");
            champs.put("TALB", "Dure Limite");
            champs.put("TIT2", "Ce Que Je Veux");
            champs.put("TRCK", "3/10");
            champs.put("TYER", "1982");
            Path fichier = FichiersDEssai.mp3(racine.resolve("03 - Titre.mp3"), 100, 128, champs);

            Etiquette etiquette = EtiquettesDuFichier.lire(fichier);

            assertThat(etiquette.artiste()).contains("Téléphone");
            assertThat(etiquette.album()).contains("Dure Limite");
            assertThat(etiquette.numero()).hasValue(3);
            assertThat(etiquette.annee()).hasValue(1982);
            assertThat(etiquette.secondes()).isPresent();
            assertThat(etiquette.secondes().getAsDouble()).isCloseTo(2.61, within(0.1));
        }

        @Test
        @DisplayName("la table des encodeurs à débit variable donne la durée exacte")
        void laTableDeTramesDonneLaDureeExacte() throws IOException {
            Path fichier = FichiersDEssai.mp3AvecTableDeTrames(
                    racine.resolve("variable.mp3"), 1000, 128);

            assertThat(EtiquettesDuFichier.lire(fichier).secondes().getAsDouble())
                    .isCloseTo(26.12, within(0.1));
        }

        @Test
        @DisplayName("l'extension de l'encodeur donne une empreinte du flux audio")
        void lExtensionDeLEncodeurDonneUneEmpreinte() throws IOException {
            Path fichier = FichiersDEssai.mp3AvecTableDeTrames(
                    racine.resolve("variable.mp3"), 1000, 128, "LAME3.100", 417_000L, 0x1234);

            assertThat(EtiquettesDuFichier.lire(fichier).empreinteAudio())
                    .contains("mp3-lame:3e8:65ce8:1234");
        }

        @Test
        @DisplayName("l'empreinte est retrouvée quand la table annonce tous ses champs")
        void lEmpreinteEstRetrouveeApresTousLesChamps() throws IOException {
            // Poids du flux, table de cent positions, indice de qualité : chacun repousse
            // l'extension de l'encodeur. Se tromper d'un octet dans ce calcul ferait lire du son
            // à la place des mesures, et fabriquerait une empreinte tirée au hasard.
            Path fichier = FichiersDEssai.mp3AvecTableDeTramesCompletes(
                    racine.resolve("variable.mp3"), 1000, 320, "LAME3.100", 417_000L, 0x1234);

            assertThat(EtiquettesDuFichier.lire(fichier).empreinteAudio())
                    .contains("mp3-lame:3e8:65ce8:1234");
        }

        @Test
        @DisplayName("sans nom d'encodeur lisible, aucune empreinte n'est inventée")
        void sansEncodeurAucuneEmpreinte() throws IOException {
            // Ce qui suit la table n'est alors que du son : le lire comme des mesures rendrait une
            // empreinte tirée au hasard, donc capable de déclarer identiques deux fichiers
            // quelconques.
            Path fichier = FichiersDEssai.mp3AvecTableDeTrames(
                    racine.resolve("variable.mp3"), 1000, 128);

            assertThat(EtiquettesDuFichier.lire(fichier).empreinteAudio()).isEmpty();
        }

        @Test
        @DisplayName("un encodeur qui n'a pas mesuré son flux ne donne pas d'empreinte")
        void lEncodeurSansMesureNeDonneRien() throws IOException {
            Path fichier = FichiersDEssai.mp3AvecTableDeTrames(
                    racine.resolve("variable.mp3"), 1000, 128, "LAME3.100", 0L, 0);

            assertThat(EtiquettesDuFichier.lire(fichier).empreinteAudio()).isEmpty();
        }

        @Test
        @DisplayName("il faut les deux mesures, pas une seule")
        void lesDeuxMesuresSontExigees() throws IOException {
            // Chacune des deux à zéro suffit à renoncer, et la surveiller séparément est la seule
            // façon de s'assurer qu'aucune des deux n'a été oubliée de la condition.
            Path sansLongueur = FichiersDEssai.mp3AvecTableDeTrames(
                    racine.resolve("sans-longueur.mp3"), 1000, 128, "LAME3.100", 0L, 0x1234);
            Path sansControle = FichiersDEssai.mp3AvecTableDeTrames(
                    racine.resolve("sans-controle.mp3"), 1000, 128, "LAME3.100", 417_000L, 0);

            assertThat(EtiquettesDuFichier.lire(sansLongueur).empreinteAudio()).isEmpty();
            assertThat(EtiquettesDuFichier.lire(sansControle).empreinteAudio()).isEmpty();
        }

        @Test
        @DisplayName("un nom d'encodeur qui s'abîme en cours de route est refusé")
        void leNomDEncodeurAbimeEstRefuse() throws IOException {
            // La première lettre est bonne, la suite non : c'est la forme que prend du son pris
            // pour un nom, et vérifier le seul premier caractère la laisserait passer.
            Path fichier = FichiersDEssai.mp3AvecTableDeTrames(
                    racine.resolve("abime.mp3"), 1000, 128, "L\u0001ME3.100", 417_000L, 0x1234);

            assertThat(EtiquettesDuFichier.lire(fichier).empreinteAudio()).isEmpty();
        }

        @Test
        @DisplayName("un nom d'encodeur qui ne commence pas par une lettre est refusé")
        void leNomDEncodeurSansLettreInitialeEstRefuse() throws IOException {
            // Les mesures qui suivent sont ici parfaitement valides : c'est le seul nom qui
            // dit s'il y a bien une extension à cet endroit, ou du son qui en a la forme.
            Path fichier = FichiersDEssai.mp3AvecTableDeTrames(
                    racine.resolve("chiffre.mp3"), 1000, 128, "3AME3.100", 417_000L, 0x1234);

            assertThat(EtiquettesDuFichier.lire(fichier).empreinteAudio()).isEmpty();
        }

        @Test
        @DisplayName("des données quelconques ne passent pas pour du son")
        void lesDonneesQuelconquesNeSontPasDuSon() throws IOException {
            byte[] bruit = new byte[4096];
            for (int rang = 0; rang < bruit.length; rang++) {
                // Onze bits à un tous les deux octets : la fausse synchronisation même.
                bruit[rang] = (byte) (rang % 2 == 0 ? 0xFF : 0xFB);
            }

            Etiquette etiquette = EtiquettesDuFichier.lire(
                    FichiersDEssai.octets(racine.resolve("bruit.mp3"), bruit));

            assertThat(etiquette.secondes()).isEmpty();
        }
    }

    @Nested
    @DisplayName("conteneur ISO")
    class ConteneurIso {

        @Test
        @DisplayName("la durée et les étiquettes sont lues sous l'atome des métadonnées")
        void laDureeEtLesEtiquettesSontLues() throws IOException {
            Map<String, String> champs = new LinkedHashMap<>();
            champs.put("©ART", "Daft Punk");
            champs.put("©alb", "Discovery");
            champs.put("©day", "2001");
            Path fichier = FichiersDEssai.m4a(racine.resolve("01 - One More Time.m4a"),
                    320.5, 0, champs);

            Etiquette etiquette = EtiquettesDuFichier.lire(fichier);

            assertThat(etiquette.secondes()).hasValue(320.5);
            assertThat(etiquette.artiste()).contains("Daft Punk");
            assertThat(etiquette.album()).contains("Discovery");
            assertThat(etiquette.annee()).hasValue(2001);
        }

        @Test
        @DisplayName("la forme à durée longue est lue comme l'autre")
        void laFormeALongueDureeEstLue() throws IOException {
            Path fichier = FichiersDEssai.m4a(racine.resolve("long.m4a"), 3600, 1, Map.of());

            assertThat(EtiquettesDuFichier.lire(fichier).secondes()).hasValue(3600.0);
        }

        @Test
        @DisplayName("une durée nulle n'est pas une durée")
        void laDureeNulleNEnEstPasUne() throws IOException {
            Path fichier = FichiersDEssai.m4a(racine.resolve("vide.m4a"), 0, 0, Map.of());

            assertThat(EtiquettesDuFichier.lire(fichier).secondes()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Ogg")
    class Ogg {

        @Test
        @DisplayName("l'Opus compte ses échantillons à quarante-huit mille par seconde")
        void lOpusCompteAQuaranteHuitMille() throws IOException {
            Path fichier = FichiersDEssai.opus(racine.resolve("01 - Titre.opus"),
                    48_000L * 180, Map.of("ARTIST", "Björk", "ALBUM", "Homogenic"));

            Etiquette etiquette = EtiquettesDuFichier.lire(fichier);

            assertThat(etiquette.secondes()).hasValue(180.0);
            assertThat(etiquette.artiste()).contains("Björk");
            assertThat(etiquette.album()).contains("Homogenic");
        }

        @Test
        @DisplayName("le Vorbis suit la fréquence annoncée par son identification")
        void leVorbisSuitSaFrequence() throws IOException {
            Path fichier = FichiersDEssai.ogg(racine.resolve("02 - Titre.ogg"),
                    44_100L * 120, 44_100);

            assertThat(EtiquettesDuFichier.lire(fichier).secondes()).hasValue(120.0);
        }

        @Test
        @DisplayName("un flux qui n'est pas un Ogg ne rend rien")
        void leFauxOggNeRendRien() throws IOException {
            assertThat(EtiquettesDuFichier.lire(
                    FichiersDEssai.fichierQuiNEnEstPasUn(racine.resolve("faux.ogg"))).estVide())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("wav")
    class Wav {

        @Test
        @DisplayName("la durée est le son divisé par le débit")
        void laDureeEstLeSonDiviseParLeDebit() throws IOException {
            Path fichier = FichiersDEssai.wav(racine.resolve("01 - Titre.wav"), 176_400, 176_400);

            assertThat(EtiquettesDuFichier.lire(fichier).secondes()).hasValue(1.0);
        }

        @Test
        @DisplayName("un wav sans bloc de son ne rend pas de durée")
        void leWavSansSonNeRendRien() throws IOException {
            Path fichier = FichiersDEssai.octets(racine.resolve("court.wav"),
                    FichiersDEssai.blocRiff("RIFF",
                            "WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));

            assertThat(EtiquettesDuFichier.lire(fichier).estVide()).isTrue();
        }
    }

    @Nested
    @DisplayName("Ce que le programme refuse de lire")
    class Refus {

        @Test
        @DisplayName("les formats qu'on ne sait pas lire sont annoncés comme tels")
        void lesFormatsInconnusSontAnnonces() {
            assertThat(EtiquettesDuFichier.saitLire("01 - Titre.flac")).isTrue();
            assertThat(EtiquettesDuFichier.saitLire("01 - Titre.mp3")).isTrue();
            assertThat(EtiquettesDuFichier.saitLire("01 - Titre.ape")).isFalse();
            assertThat(EtiquettesDuFichier.saitLire("sans extension")).isFalse();
        }

        @Test
        @DisplayName("un fichier absent ne fait pas échouer l'analyse")
        void leFichierAbsentNInterrompRien() {
            assertThat(EtiquettesDuFichier.lire(racine.resolve("absent.flac")).estVide()).isTrue();
        }

        @Test
        @DisplayName("un fichier vide ne fait pas échouer l'analyse")
        void leFichierVideNInterrompRien() throws IOException {
            assertThat(EtiquettesDuFichier.lire(
                    FichiersDEssai.octets(racine.resolve("vide.flac"), new byte[0])).estVide())
                    .isTrue();
        }

        @Test
        @DisplayName("un fichier d'un format non lu ne rend rien, sans se plaindre")
        void leFormatNonLuNeRendRien() throws IOException {
            assertThat(EtiquettesDuFichier.lire(
                    FichiersDEssai.fichierQuiNEnEstPasUn(racine.resolve("morceau.ape"))).estVide())
                    .isTrue();
        }
    }
}
