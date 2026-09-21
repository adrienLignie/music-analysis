package fr.musique.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Éprouve ce que le parcours fait des fichiers atteints par plusieurs chemins.
 *
 * <p>La question ne se pose qu'au disque : c'est lui qui dit si deux chemins mènent au même
 * contenu, par la clé que l'énumération fournit. Le test pose donc de vrais liens durs, et
 * s'arrête de lui-même là où le système de fichiers les refuse.
 */
class LiensDursDuParcoursTest {

    @TempDir
    private Path racine;

    private static final long UN_MEGAOCTET = 1024L * 1024;

    private Path fichier(String chemin) throws IOException {
        Path resolu = racine.resolve(chemin);
        Files.createDirectories(resolu.getParent());
        Files.write(resolu, new byte[(int) UN_MEGAOCTET]);
        return resolu;
    }

    @Test
    @DisplayName("un second chemin vers un même fichier est relevé, pas compté deux fois")
    void leSecondCheminEstReleve() throws IOException {
        Path original = fichier("Muse/Absolution/01 - Titre.flac");
        Path lien = racine.resolve("Partage/Muse - Absolution/01 - Titre.flac");
        Files.createDirectories(lien.getParent());
        try {
            Files.createLink(lien, original);
        } catch (IOException | UnsupportedOperationException refus) {
            return;
        }
        // Windows ne rend aucune clé de fichier à Java : les deux chemins y restent
        // indiscernables, et c'est une limite connue du programme plutôt qu'un défaut du test.
        if (Files.readAttributes(original, java.nio.file.attribute.BasicFileAttributes.class)
                .fileKey() == null) {
            return;
        }

        Arborescence arborescence =
                new Parcours(CriteresDeScan.parDefaut()).parcourir(List.of(racine));

        assertThat(arborescence.nombreDeFichiers()).isEqualTo(2);
        assertThat(arborescence.repetitionsPhysiques()).hasSize(1);

        Inventaire inventaire = new Identification(
                new fr.musique.nom.IdentificationDAlbum(
                        new fr.musique.nom.ParseurDeNomDAlbum(2027)))
                .identifier(arborescence);

        // Les deux dossiers existent, mais un seul poids est compté : celui du fichier physique.
        assertThat(inventaire.albums()).hasSize(2);
        assertThat(inventaire.albumsDistincts()).hasSize(1);
        assertThat(inventaire.tailleTotale()).isEqualTo(UN_MEGAOCTET);
        assertThat(inventaire.nombreDePistes()).isEqualTo(1);
    }
}
