package fr.musique.scan;

import static org.assertj.core.api.Assertions.assertThat;

import fr.musique.doublons.Album;
import fr.musique.nom.IdentificationDAlbum;
import fr.musique.nom.OrigineDeLArtiste;
import fr.musique.nom.ParseurDeNomDAlbum;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Éprouve la composition des albums à partir de ce que le parcours a vu.
 *
 * <p>Aucun fichier n'est écrit : cette étape ne touche jamais au disque, et c'est précisément ce
 * qui permet de l'éprouver sur une arborescence fabriquée.
 */
class IdentificationTest {

    private static final int ANNEE_MAXIMALE_FIGEE = 2027;
    private static final long UN_MEGAOCTET = 1024L * 1024;

    private final Identification identification = new Identification(
            new IdentificationDAlbum(new ParseurDeNomDAlbum(ANNEE_MAXIMALE_FIGEE)));

    /** Un dossier tel que le parcours le rend, avec des pistes numérotées de 1 à n. */
    private static DossierTrouve dossier(String chemin, boolean parentEstUneRacine, int pistes) {
        Path resolu = Path.of(chemin);
        List<FichierTrouve> fichiers = new ArrayList<>();
        for (int numero = 1; numero <= pistes; numero++) {
            fichiers.add(new FichierTrouve(
                    resolu.resolve(String.format("%02d - Titre %02d.flac", numero, numero)),
                    UN_MEGAOCTET,
                    null));
        }
        return new DossierTrouve(resolu, fichiers, parentEstUneRacine);
    }

    private Inventaire identifier(DossierTrouve... dossiers) {
        return identification.identifier(new Arborescence(
                List.of(dossiers), Set.of(Path.of("/musique")), Set.of(), 0, 0));
    }

    @Test
    @DisplayName("un dossier devient un album, artiste compris")
    void leDossierDevientUnAlbum() {
        Inventaire inventaire = identifier(
                dossier("/musique/Muse/Absolution (2003)", false, 14));

        assertThat(inventaire.albums()).hasSize(1);
        Album album = inventaire.albums().get(0);
        assertThat(album.nom().cleArtiste()).isEqualTo("muse");
        assertThat(album.nom().origineDeLArtiste()).isEqualTo(OrigineDeLArtiste.DOSSIER_PARENT);
        assertThat(album.nombreDePistes()).isEqualTo(14);
        assertThat(album.taille()).isEqualTo(14 * UN_MEGAOCTET);
    }

    @Test
    @DisplayName("les disques d'un coffret ne font qu'un album, du poids de tous")
    void lesDisquesSontReunis() {
        Inventaire inventaire = identifier(
                dossier("/musique/Muse/Black Holes And Revelations/CD1", false, 6),
                dossier("/musique/Muse/Black Holes And Revelations/CD2", false, 4));

        assertThat(inventaire.albums()).hasSize(1);
        Album album = inventaire.albums().get(0);
        assertThat(album.dossier()).isEqualTo(Path.of("/musique/Muse/Black Holes And Revelations"));
        assertThat(album.nombreDePistes()).isEqualTo(10);
        assertThat(album.nom().cleArtiste()).isEqualTo("muse");
        assertThat(album.nom().cle()).isEqualTo("black holes and revelations");
    }

    @Test
    @DisplayName("un dossier de disque posé sous une racine n'est rattaché à rien")
    void leDisqueSousUneRacineResteSeul() {
        Inventaire inventaire = identifier(dossier("/musique/CD1", true, 6));

        assertThat(inventaire.albums()).hasSize(1);
        assertThat(inventaire.albums().get(0).dossier()).isEqualTo(Path.of("/musique/CD1"));
    }

    @Test
    @DisplayName("les pistes sont rangées par disque puis par numéro")
    void lesPistesSontRangees() {
        Path album = Path.of("/musique/Muse/Coffret");
        DossierTrouve dossier = new DossierTrouve(album, List.of(
                new FichierTrouve(album.resolve("2-01 - Titre.flac"), UN_MEGAOCTET, null),
                new FichierTrouve(album.resolve("1-02 - Titre.flac"), UN_MEGAOCTET, null),
                new FichierTrouve(album.resolve("1-01 - Titre.flac"), UN_MEGAOCTET, null)),
                false);

        Album compose = identifier(dossier).albums().get(0);

        assertThat(compose.pistes().stream().map(piste -> piste.chemin().getFileName().toString()))
                .containsExactly("1-01 - Titre.flac", "1-02 - Titre.flac", "2-01 - Titre.flac");
    }

    @Test
    @DisplayName("une piste atteinte par un second chemin ne pèse pas deux fois")
    void laRepetitionNePesePas() {
        DossierTrouve dossier = dossier("/musique/Muse/Absolution", false, 4);
        Path repetee = dossier.fichiers().get(0).chemin();

        Inventaire inventaire = identification.identifier(new Arborescence(
                List.of(dossier), Set.of(Path.of("/musique")), Set.of(repetee), 0, 0));

        assertThat(inventaire.albums().get(0).nombreDePistes()).isEqualTo(4);
        assertThat(inventaire.albums().get(0).taille()).isEqualTo(3 * UN_MEGAOCTET);
        assertThat(inventaire.nombreDePistes()).isEqualTo(3);
    }

    @Test
    @DisplayName("l'inventaire compte ce qui pèse vraiment")
    void lInventaireCompteCeQuiPese() {
        Inventaire inventaire = identifier(
                dossier("/musique/Muse/Absolution", false, 4),
                dossier("/musique/Muse/Black Holes", false, 6));

        assertThat(inventaire.albumsDistincts()).hasSize(2);
        assertThat(inventaire.tailleTotale()).isEqualTo(10 * UN_MEGAOCTET);
        assertThat(inventaire.fichiersIgnores()).isZero();
    }
}
