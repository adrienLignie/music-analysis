package fr.musique.doublons;

import fr.musique.nom.IdentificationDAlbum;
import fr.musique.nom.NomDePiste;
import fr.musique.nom.ParseurDeNomDAlbum;
import fr.musique.nom.ParseurDeNomDePiste;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Fabrique des albums pour les tests, à partir d'un chemin et de rien d'autre.
 *
 * <p>Les albums sont construits par le <b>même</b> chemin de lecture que l'analyse réelle : le nom
 * du dossier et celui de son parent passent par l'identification, les noms de fichiers par le
 * parseur de pistes. Un test qui fabriquerait ses identités à la main vérifierait le regroupement
 * sur des données que le programme ne produit jamais.
 *
 * <p>Rien n'est écrit sur le disque : les chemins n'ont pas besoin d'exister pour que les noms
 * soient lus.
 */
public final class AlbumsDEssai {

    /** Année figée, pour qu'un test écrit aujourd'hui dise la même chose l'an prochain. */
    private static final int ANNEE_MAXIMALE_FIGEE = 2027;

    private static final ParseurDeNomDePiste PARSEUR_DE_PISTE = new ParseurDeNomDePiste();
    private static final IdentificationDAlbum IDENTIFICATION =
            new IdentificationDAlbum(new ParseurDeNomDAlbum(ANNEE_MAXIMALE_FIGEE));

    private AlbumsDEssai() {
        // Fabrique seule, pas d'instance.
    }

    /** Album dont les pistes sont numérotées de 1 à {@code nombreDePistes}, sans trou. */
    public static Album album(
            String dossier, String extension, long octetsParPiste, int nombreDePistes) {
        return albumNumerote(dossier, extension, octetsParPiste,
                IntStream.rangeClosed(1, nombreDePistes).toArray());
    }

    /**
     * Album accompagné d'un poids qui n'est pas de la musique.
     *
     * <p>Pochettes, livrets numérisés, journaux d'extraction : ce que le dossier contient d'autre
     * que ses morceaux, et qui occupe la place sans entrer dans le classement.
     */
    public static Album albumAvecPoidsAnnexe(String dossier, String extension,
            long octetsParPiste, int nombreDePistes, long octetsHorsAudio) {
        Album sansAnnexe = album(dossier, extension, octetsParPiste, nombreDePistes);
        return new Album(sansAnnexe.dossier(), sansAnnexe.pistes(), sansAnnexe.nom(),
                sansAnnexe.taille(), octetsHorsAudio);
    }

    /** Album dont on choisit les numéros de pistes, pour éprouver les trous. */
    public static Album albumNumerote(
            String dossier, String extension, long octetsParPiste, int... numeros) {
        List<Piste> pistes = new ArrayList<>();
        for (int numero : numeros) {
            pistes.add(piste(
                    dossier + "/" + String.format("%02d - Titre %02d.%s", numero, numero,
                            extension),
                    octetsParPiste));
        }
        return composer(dossier, pistes);
    }

    /** Album dont on écrit les noms de fichiers en toutes lettres. */
    public static Album albumDePistes(String dossier, long octetsParPiste, String... noms) {
        List<Piste> pistes = new ArrayList<>();
        for (String nom : noms) {
            pistes.add(piste(dossier + "/" + nom, octetsParPiste));
        }
        return composer(dossier, pistes);
    }

    /**
     * Album posé directement sous une racine analysée, donc sans artiste à emprunter.
     *
     * <p>C'est la situation d'un dossier d'album rangé à même le point de départ de l'analyse :
     * le dossier au-dessus est un choix de l'utilisateur, pas un nom d'artiste, et le parcours
     * réel le signale de la même façon.
     */
    public static Album albumSousUneRacine(
            String dossier, String extension, long octetsParPiste, int nombreDePistes) {
        List<Piste> pistes = new ArrayList<>();
        for (int numero = 1; numero <= nombreDePistes; numero++) {
            pistes.add(piste(
                    dossier + "/" + String.format("%02d - Titre %02d.%s", numero, numero,
                            extension),
                    octetsParPiste));
        }
        Path chemin = Path.of(dossier);
        return Album.de(
                chemin,
                pistes,
                IDENTIFICATION.identifier(
                        chemin.getFileName().toString(),
                        null,
                        pistes.stream().map(Piste::nom).toList()));
    }

    /** Une piste isolée, dont le nom est lu comme le ferait l'analyse. */
    public static Piste piste(String chemin, long octets) {
        Path resolu = Path.of(chemin);
        NomDePiste nom = PARSEUR_DE_PISTE.analyser(resolu.getFileName().toString());
        return new Piste(resolu, octets, nom);
    }

    /**
     * Assemble un album à partir de ses pistes, en lisant son identité dans son chemin.
     *
     * <p>Le dossier parent est celui du chemin, sauf quand il n'y en a pas : un album posé à la
     * racine d'un système de fichiers n'a pas d'artiste à emprunter.
     */
    private static Album composer(String dossier, List<Piste> pistes) {
        Path chemin = Path.of(dossier);
        Path parent = chemin.getParent();
        String nomDuParent = parent == null || parent.getFileName() == null
                ? null
                : parent.getFileName().toString();
        return Album.de(
                chemin,
                pistes,
                IDENTIFICATION.identifier(
                        chemin.getFileName().toString(),
                        nomDuParent,
                        pistes.stream().map(Piste::nom).toList()));
    }
}
