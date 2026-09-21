package fr.musique.doublons;

import static fr.musique.doublons.AlbumsDEssai.album;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Éprouve le démêlage des dossiers qui mènent aux mêmes fichiers.
 *
 * <p>Les liens durs sont posés pour de bon sur le disque : c'est le seul moyen d'éprouver un code
 * dont tout l'objet est de poser au système une question qu'aucune donnée fabriquée ne peut
 * simuler. Le test s'arrête de lui-même là où le système de fichiers refuse le lien.
 */
class LiensDursTest {

    @TempDir
    private Path racine;

    /** Crée un album sur le disque, et rend l'objet qui le décrit. */
    private Album creer(String dossier, int pistes, long octets) throws IOException {
        Path chemin = racine.resolve(dossier);
        Files.createDirectories(chemin);
        for (int numero = 1; numero <= pistes; numero++) {
            Files.write(
                    chemin.resolve(String.format("%02d - Titre %02d.flac", numero, numero)),
                    new byte[(int) octets]);
        }
        return album(chemin.toString(), "flac", octets, pistes);
    }

    /** Duplique un album par liens durs, ou rend {@code null} si le système les refuse. */
    private Album lier(Album original, String dossier) throws IOException {
        Path chemin = racine.resolve(dossier);
        Files.createDirectories(chemin);
        List<Path> poses = new ArrayList<>();
        for (Piste piste : original.pistes()) {
            Path lien = chemin.resolve(piste.chemin().getFileName());
            try {
                Files.createLink(lien, piste.chemin());
            } catch (IOException | UnsupportedOperationException refus) {
                return null;
            }
            poses.add(lien);
        }
        return album(chemin.toString(), "flac", poses.isEmpty() ? 0 : original.taille()
                / original.nombreDePistes(), original.nombreDePistes());
    }

    @Test
    @DisplayName("un groupe dont tous les dossiers mènent aux mêmes fichiers n'est pas un doublon")
    void leGroupeDeLiensDursEstEcarte() throws IOException {
        Album original = creer("Muse/Absolution", 4, 1024);
        Album lie = lier(original, "partage/Muse - Absolution");
        if (lie == null) {
            return;
        }
        GroupeDeDoublons groupe = new GroupeDeDoublons(
                List.of(original, lie), NiveauDeConfiance.FORTE, "motif");

        LiensDurs.Resultat resultat = LiensDurs.demeler(List.of(groupe));

        assertThat(resultat.groupes()).isEmpty();
        assertThat(resultat.groupesEcartes()).isEqualTo(1);
    }

    @Test
    @DisplayName("deux dossiers de fichiers distincts restent un doublon")
    void lesDossiersDistinctsRestentUnDoublon() throws IOException {
        Album premier = creer("Muse/Absolution", 4, 1024);
        Album second = creer("sauvegarde/Muse - Absolution", 4, 1024);
        GroupeDeDoublons groupe = new GroupeDeDoublons(
                List.of(premier, second), NiveauDeConfiance.FORTE, "motif");

        LiensDurs.Resultat resultat = LiensDurs.demeler(List.of(groupe));

        assertThat(resultat.groupes()).hasSize(1);
        assertThat(resultat.groupesEcartes()).isZero();
        assertThat(resultat.groupes().get(0).liensDurs()).isEmpty();
    }

    @Test
    @DisplayName("un dossier de poids différent n'est pas comparé au disque")
    void lePoidsDifferentEcarteLaComparaison() throws IOException {
        Album premier = creer("Muse/Absolution", 4, 2048);
        Album second = creer("sauvegarde/Muse - Absolution", 4, 1024);
        GroupeDeDoublons groupe = new GroupeDeDoublons(
                List.of(premier, second), NiveauDeConfiance.FORTE, "motif");

        assertThat(LiensDurs.demeler(List.of(groupe)).groupes()).hasSize(1);
    }

    @Test
    @DisplayName("un fichier disparu depuis le parcours ne fait pas échouer l'analyse")
    void leFichierDisparuNInterrompRien() throws IOException {
        Album present = creer("Muse/Absolution", 4, 1024);
        Album fantome = album(
                racine.resolve("disparu/Muse - Absolution").toString(), "flac", 1024, 4);
        GroupeDeDoublons groupe = new GroupeDeDoublons(
                List.of(present, fantome), NiveauDeConfiance.FORTE, "motif");

        assertThat(LiensDurs.demeler(List.of(groupe)).groupes()).hasSize(1);
    }
}
