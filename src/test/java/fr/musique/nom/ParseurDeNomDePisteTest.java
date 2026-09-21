package fr.musique.nom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Éprouve la lecture des noms de fichiers audio, numéro de piste compris. */
class ParseurDeNomDePisteTest {

    private final ParseurDeNomDePiste parseur = new ParseurDeNomDePiste();

    @Nested
    @DisplayName("Numéro de piste")
    class Numero {

        @Test
        @DisplayName("le numéro en tête est lu puis retiré du titre")
        void leNumeroEnTeteEstLu() {
            NomDePiste piste = parseur.analyser("01 - Speak To Me.flac");

            assertThat(piste.numero()).hasValue(1);
            assertThat(piste.titre()).isEqualTo("Speak To Me");
            assertThat(piste.extension()).isEqualTo("flac");
            assertThat(piste.estSansPerte()).isTrue();
        }

        @Test
        @DisplayName("le point et le souligné séparent aussi bien que le tiret")
        void tousLesSeparateursSontAdmis() {
            assertThat(parseur.analyser("03. Money.mp3").titre()).isEqualTo("Money");
            assertThat(parseur.analyser("07_Time.mp3").titre()).isEqualTo("Time");
            assertThat(parseur.analyser("12 Eclipse.mp3").titre()).isEqualTo("Eclipse");
        }

        @Test
        @DisplayName("deux nombres en tête sont un disque et une piste")
        void leDisqueEtLaPisteSontDistingues() {
            NomDePiste piste = parseur.analyser("1-03 Breathe.m4a");

            assertThat(piste.disque()).hasValue(1);
            assertThat(piste.numero()).hasValue(3);
            assertThat(piste.titre()).isEqualTo("Breathe");
        }

        @Test
        @DisplayName("la face d'un vinyle vaut un numéro de disque")
        void laFaceDeVinyleEstUnDisque() {
            NomDePiste piste = parseur.analyser("B2 - Us And Them.flac");

            assertThat(piste.disque()).hasValue(2);
            assertThat(piste.numero()).hasValue(2);
            assertThat(piste.titre()).isEqualTo("Us And Them");
        }

        @Test
        @DisplayName("un fichier sans numéro n'en invente pas")
        void leFichierSansNumeroNEnPortePas() {
            NomDePiste piste = parseur.analyser("Speak To Me.ogg");

            assertThat(piste.numero()).isEmpty();
            assertThat(piste.titre()).isEqualTo("Speak To Me");
        }

        @Test
        @DisplayName("un nombre trop grand pour être une piste reste dans le titre")
        void leNombreTropGrandResteDansLeTitre() {
            assertThat(parseur.analyser("1979.mp3").numero()).isEmpty();
            assertThat(parseur.analyser("1979.mp3").titre()).isEqualTo("1979");
        }
    }

    @Nested
    @DisplayName("Artiste")
    class Artiste {

        @Test
        @DisplayName("le tiret entouré d'espaces sépare l'artiste du titre")
        void leTiretEspaceSepareLArtiste() {
            NomDePiste piste = parseur.analyser("01 - Pink Floyd - Speak To Me.mp3");

            assertThat(piste.artiste()).contains("Pink Floyd");
            assertThat(piste.titre()).isEqualTo("Speak To Me");
        }

        @Test
        @DisplayName("un tiret sans espaces appartient au nom")
        void leTiretColleNEstPasUnSeparateur() {
            NomDePiste piste = parseur.analyser("Jean-Jacques Goldman - Je te donne.mp3");

            assertThat(piste.artiste()).contains("Jean-Jacques Goldman");
            assertThat(piste.titre()).isEqualTo("Je te donne");
        }

        @Test
        @DisplayName("un fichier sans artiste n'en invente pas")
        void leFichierSansArtisteNEnPortePas() {
            assertThat(parseur.analyser("05 - Money.flac").artiste()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Patron du dossier")
    class Patron {

        @Test
        @DisplayName("le patron commun au dossier est retiré, le numéro est gardé")
        void lePatronEstRetireEtLeNumeroGarde() {
            List<String> freres = List.of(
                    "Pink Floyd - The Dark Side Of The Moon - 01 - Speak To Me.flac",
                    "Pink Floyd - The Dark Side Of The Moon - 02 - Breathe.flac",
                    "Pink Floyd - The Dark Side Of The Moon - 03 - On The Run.flac");
            PrefixeDeCollection prefixe = PrefixeDeCollection.detecter(freres);

            NomDePiste piste = parseur.analyser(freres.get(1), prefixe);

            assertThat(piste.numero()).hasValue(2);
            assertThat(piste.titre()).isEqualTo("Breathe");
        }

        @Test
        @DisplayName("deux fichiers seuls ne forment pas un patron")
        void deuxFichiersNeFormentPasUnPatron() {
            PrefixeDeCollection prefixe = PrefixeDeCollection.detecter(
                    List.of("Muse - Hysteria.mp3", "Muse - Time Is Running Out.mp3"));

            assertThat(parseur.analyser("Muse - Hysteria.mp3", prefixe).artiste())
                    .contains("Muse");
        }
    }

    @Nested
    @DisplayName("Extension")
    class Extension {

        @Test
        @DisplayName("l'extension est ramenée en minuscules")
        void lExtensionEstEnMinuscules() {
            assertThat(parseur.analyser("Money.FLAC").extension()).isEqualTo("flac");
        }

        @Test
        @DisplayName("un fichier sans extension n'en porte aucune")
        void lAbsenceDExtensionNEstPasUneErreur() {
            NomDePiste piste = parseur.analyser("Money");

            assertThat(piste.extension()).isEmpty();
            assertThat(piste.estSansPerte()).isFalse();
            assertThat(piste.titre()).isEqualTo("Money");
        }
    }
}
