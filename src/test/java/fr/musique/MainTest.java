package fr.musique;

import static org.assertj.core.api.Assertions.assertThat;

import fr.musique.media.FichiersDEssai;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Éprouve la commande de bout en bout, sur une arborescence temporaire.
 *
 * <p>Le test vérifie aussi ce que l'outil <b>ne fait pas</b> : après exécution, l'arborescence
 * analysée doit être exactement dans l'état où elle était. C'est la promesse principale du
 * programme, et la seule qui mérite d'être vérifiée par une assertion plutôt que par une
 * relecture.
 */
class MainTest {

    private static final long UN_MEGA = 1024L * 1024;

    /**
     * Propriétés par lesquelles la commande apprend comment sa sortie standard est encodée.
     *
     * <p>Le test détourne {@code System.out} vers un flux en UTF-8 ; il lui faut aussi le dire,
     * car le rapport choisit ses caractères d'après ces propriétés et non d'après le flux qu'on
     * lui donne. Sans cette déclaration, c'est la machine de construction qui décide, et les
     * assertions portant sur les accents passeraient sur l'une et tomberaient sur l'autre.
     */
    private static final List<String> PROPRIETES_D_ENCODAGE =
            List.of("stdout.encoding", "sun.stdout.encoding");

    private final Map<String, String> encodageDOrigine = new HashMap<>();

    @BeforeEach
    void annoncerUneSortieEnUtf8() {
        for (String propriete : PROPRIETES_D_ENCODAGE) {
            encodageDOrigine.put(propriete, System.getProperty(propriete));
            System.setProperty(propriete, StandardCharsets.UTF_8.name());
        }
    }

    @AfterEach
    void rendreLEncodageDOrigine() {
        encodageDOrigine.forEach((propriete, valeur) -> {
            if (valeur == null) {
                System.clearProperty(propriete);
            } else {
                System.setProperty(propriete, valeur);
            }
        });
        encodageDOrigine.clear();
    }

    /** Écrit un fichier de cette taille sans en occuper la place : seule la taille compte ici. */
    private static void creerFichier(Path chemin, long taille) throws IOException {
        Files.createDirectories(chemin.getParent());
        try (var canal = Files.newByteChannel(
                chemin, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            canal.position(taille - 1);
            canal.write(ByteBuffer.wrap(new byte[] {0}));
        }
    }

    /** Écrit un album entier, pistes numérotées de 1 à n. */
    private static void creerAlbum(Path dossier, String extension, long taille, int pistes)
            throws IOException {
        for (int numero = 1; numero <= pistes; numero++) {
            creerFichier(
                    dossier.resolve(String.format("%02d - Titre %02d.%s", numero, numero,
                            extension)),
                    taille);
        }
    }

    private static String executer(String... arguments) {
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(tampon, true, StandardCharsets.UTF_8));
            Main.commande().execute(arguments);
        } finally {
            System.setOut(original);
        }
        return tampon.toString(StandardCharsets.UTF_8);
    }

    private static int codeDe(String... arguments) {
        PrintStream erreurOriginale = System.err;
        try {
            System.setErr(new PrintStream(new ByteArrayOutputStream(), true,
                    StandardCharsets.UTF_8));
            return Main.commande().execute(arguments);
        } finally {
            System.setErr(erreurOriginale);
        }
    }

    @Test
    @DisplayName("la commande produit un rapport et ne touche à rien")
    void laCommandeProduitUnRapportSansRienToucher(@TempDir Path racine) throws IOException {
        Path flac = racine.resolve("Muse/Absolution (2003)");
        Path mp3 = racine.resolve("Sauvegarde/Muse - Absolution (2003)");
        creerAlbum(flac, "flac", 8 * UN_MEGA, 4);
        creerAlbum(mp3, "mp3", 2 * UN_MEGA, 4);
        Path temoin = flac.resolve("01 - Titre 01.flac");
        long empreinteAvant = Files.getLastModifiedTime(temoin).toMillis();

        String rapport = executer(racine.toString(), "--top", "3");

        assertThat(rapport).contains("2 albums retenus, 8 pistes");
        assertThat(rapport).contains("Muse — « absolution »");
        assertThat(rapport).contains("récupérable");
        assertThat(Files.exists(temoin)).isTrue();
        assertThat(Files.size(temoin)).isEqualTo(8 * UN_MEGA);
        assertThat(Files.getLastModifiedTime(temoin).toMillis()).isEqualTo(empreinteAvant);
    }

    @Test
    @DisplayName("le classement porte sur les dossiers et non sur les fichiers")
    void leClassementPorteSurLesDossiers(@TempDir Path racine) throws IOException {
        creerAlbum(racine.resolve("Muse/Absolution"), "flac", 8 * UN_MEGA, 10);
        creerFichier(racine.resolve("Divers/Un seul gros fichier.flac"), 40 * UN_MEGA);

        String rapport = executer(racine.toString(), "--top", "2");

        List<String> classement = rapport.lines()
                .dropWhile(ligne -> !ligne.startsWith("LES 2 ALBUMS"))
                .filter(ligne -> ligne.contains("pistes"))
                .toList();
        assertThat(classement.get(0)).contains("Absolution").contains("10 pistes");
    }

    @Test
    @DisplayName("un coffret compte pour un album, du poids de tous ses disques")
    void leCoffretCompterPourUnAlbum(@TempDir Path racine) throws IOException {
        creerAlbum(racine.resolve("Muse/Coffret/CD1"), "flac", 8 * UN_MEGA, 4);
        creerAlbum(racine.resolve("Muse/Coffret/CD2"), "flac", 8 * UN_MEGA, 4);

        String rapport = executer(racine.toString(), "--top", "3");

        assertThat(rapport).contains("1 albums retenus, 8 pistes");
        assertThat(rapport).contains("ALBUMS EN DOUBLE : aucun");
    }

    @Test
    @DisplayName("un dossier sans musique rend le code de sortie qui le dit")
    void unDossierSansMusiqueRendLeCodeAttendu(@TempDir Path racine) {
        assertThat(codeDe(racine.toString())).isEqualTo(CodeSortie.RIEN_A_ANALYSER);
    }

    @Test
    @DisplayName("un dossier exclu en ligne de commande n'est pas parcouru")
    void leDossierExcluNEstPasParcouru(@TempDir Path racine) throws IOException {
        creerAlbum(racine.resolve("Muse/Absolution"), "flac", 8 * UN_MEGA, 4);
        creerAlbum(racine.resolve("Archives/Muse - Absolution"), "flac", 8 * UN_MEGA, 4);

        String rapport = executer(racine.toString(), "--exclure", "Archives");

        assertThat(rapport).contains("1 albums retenus");
        assertThat(rapport).contains("ALBUMS EN DOUBLE : aucun");
    }

    @Test
    @DisplayName("une option absurde est refusée comme telle, pas comme une panne")
    void lOptionAbsurdeEstRefusee(@TempDir Path racine) {
        assertThat(codeDe(racine.toString(), "--top", "-5")).isEqualTo(2);
        assertThat(codeDe(racine.toString(), "--taille-min", "-1")).isEqualTo(2);
        assertThat(codeDe(racine.toString(), "--format", "yaml")).isEqualTo(2);
        assertThat(codeDe(racine.toString(), "--confiance", "totale")).isEqualTo(2);
    }

    @Test
    @DisplayName("le filtre de confiance ne garde que les groupes demandés")
    void leFiltreDeConfianceEcarteLesAutres(@TempDir Path racine) throws IOException {
        creerAlbum(racine.resolve("Muse/Absolution (2003)"), "flac", 8 * UN_MEGA, 10);
        creerAlbum(racine.resolve("Muse/Absolution (2020)"), "flac", 8 * UN_MEGA, 10);

        assertThat(executer(racine.toString())).contains("Confiance MOYENNE");
        assertThat(executer(racine.toString(), "--confiance", "forte"))
                .contains("ALBUMS EN DOUBLE : aucun");
    }

    @Test
    @DisplayName("le format JSON rend un document complet, poids en octets")
    void leFormatJsonRendUnDocument(@TempDir Path racine) throws IOException {
        creerAlbum(racine.resolve("Muse/Absolution (2003)"), "flac", 8 * UN_MEGA, 4);
        creerAlbum(racine.resolve("Sauvegarde/Muse - Absolution (2003)"), "mp3", 2 * UN_MEGA, 4);

        String rapport = executer(racine.toString(), "--format", "json");

        assertThat(rapport).startsWith("{").endsWith("}" + System.lineSeparator());
        assertThat(rapport).contains("\"groupes\": 1");
        assertThat(rapport).contains("\"octetsRecuperables\": " + 8 * UN_MEGA);
        assertThat(rapport).contains("\"garder\": true");
    }

    @Test
    @DisplayName("le format CSV donne une ligne par dossier, en-tête comprise")
    void leFormatCsvDonneUneLigneParDossier(@TempDir Path racine) throws IOException {
        creerAlbum(racine.resolve("Muse/Absolution"), "flac", 8 * UN_MEGA, 4);

        String rapport = executer(racine.toString(), "--format", "csv");

        assertThat(rapport.lines().toList()).hasSize(2);
        assertThat(rapport).startsWith("section,groupe,confiance");
        // Poids de la musique, poids annexe, nombre de pistes : dans cet ordre.
        assertThat(rapport).contains("top,,,,," + 4 * 8 * UN_MEGA + ",0,4,");
    }

    @Test
    @DisplayName("--version annonce le numéro que la construction a écrit, jamais un littéral")
    void laVersionVientDeLaConstruction() {
        // L'assertion ne vaut pas sur le numéro lui-même — il change à chaque montée de version,
        // et l'y écrire recréerait le littéral qu'on vient de supprimer. Ce qu'elle surveille,
        // c'est que la ressource soit bien filtrée : sans cela le binaire ne sait plus qui il est.
        String annonce = executer("--version");

        assertThat(annonce).startsWith("music-analysis ");
        assertThat(annonce).doesNotContain("inconnue").doesNotContain("${");
        assertThat(annonce.trim()).matches("music-analysis \\d+\\.\\d+\\.\\d+.*");
    }

    @Test
    @DisplayName("--ascii rend un rapport que n'importe quelle console sait afficher")
    void lAsciiRendUnRapportSansCaractereExotique(@TempDir Path racine) throws IOException {
        creerAlbum(racine.resolve("Téléphone/Dure Limite"), "mp3", 2 * UN_MEGA, 4);

        String rapport = executer(racine.toString(), "--ascii");

        assertThat(rapport).doesNotContain("─").doesNotContain("é");
        assertThat(rapport).contains("albums retenus").contains("Dure Limite");
    }

    @Test
    @DisplayName("les étiquettes lues confirment un rapprochement que les noms laissaient douteux")
    void lesEtiquettesConfirmentLeRapprochement(@TempDir Path racine) throws IOException {
        // Le second dossier est posé à même la racine : rien n'y nomme l'artiste, et le
        // rapprochement ne tient donc que sur le titre. C'est exactement le cas que les
        // étiquettes doivent trancher.
        Path premier = racine.resolve("Muse/Absolution");
        Path second = racine.resolve("Absolution");
        Map<String, String> champs = Map.of(
                "ARTIST", "Muse", "ALBUM", "Absolution", "TITLE", "Hysteria");
        for (int numero = 1; numero <= 4; numero++) {
            String nom = String.format("%02d - Titre %02d.flac", numero, numero);
            FichiersDEssai.flac(premier.resolve(nom), 44_100L * 240, 44_100, champs);
            FichiersDEssai.flac(second.resolve(nom), 44_100L * 240, 44_100, champs);
        }

        String sansEtiquettes = executer(racine.toString(), "--taille-min", "0");
        String avecEtiquettes = executer(racine.toString(), "--taille-min", "0", "--etiquettes");

        assertThat(sansEtiquettes).contains("Confiance MOYENNE");
        assertThat(avecEtiquettes).contains("Confiance FORTE");
        assertThat(avecEtiquettes).contains("étiquettes concordantes");
        assertThat(avecEtiquettes).contains("fichiers ont été ouverts");
    }

    @Test
    @DisplayName("--pistes ajoute la section des morceaux en double")
    void lOptionPistesAjouteLaSection(@TempDir Path racine) throws IOException {
        creerFichier(racine.resolve("Daft Punk/Discovery/01 - One More Time.flac"), 8 * UN_MEGA);
        creerFichier(racine.resolve("Daft Punk/Discovery/02 - Aerodynamic.flac"), 8 * UN_MEGA);
        creerFichier(racine.resolve("Tri/Daft Punk - One More Time.mp3"), 2 * UN_MEGA);

        assertThat(executer(racine.toString())).doesNotContain("MORCEAUX EN DOUBLE");
        assertThat(executer(racine.toString(), "--pistes"))
                .contains("MORCEAUX EN DOUBLE (1)")
                .contains("one more time");
    }
}
