package fr.musique.rapport;

import fr.musique.doublons.Album;
import fr.musique.nom.IdentificationDAlbum;
import fr.musique.nom.OrigineDeLArtiste;
import fr.musique.scan.Inventaire;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Repère les dossiers dont le rangement empêche de reconnaître un album.
 *
 * <h2>Pourquoi cette section existe</h2>
 * Les deux autres listes — albums en double, albums incomplets — ne peuvent rien dire des dossiers
 * qu'elles n'arrivent pas à identifier. Un dossier de deux cents fichiers posés en vrac ne sera
 * jamais rapproché de rien, et son poids apparaîtra au classement sous un nom qui ne veut rien
 * dire. Cette section est ce qui rend ce silence visible : ce sont les dossiers dont le rangement
 * est la seule chose qui manque pour que le reste de l'analyse fonctionne.
 *
 * <p>Elle ne dit pas qu'il y a un problème de place. Elle dit où le programme voit mal, et
 * pourquoi.
 */
public final class Rangements {

    private Rangements() {
        // Fonction d'analyse seule, pas d'instance.
    }

    /** Ce qui empêche de lire correctement un dossier. */
    public enum Nature {

        /** Des fichiers audio posés directement à la racine analysée, hors de tout album. */
        PISTES_EN_VRAC("pistes posées à la racine, hors de tout dossier d'album"),

        /** Un dossier qui porte des pistes et contient en même temps des dossiers d'albums. */
        DOSSIER_MIXTE("pistes mêlées à des dossiers d'albums"),

        /** Un dossier dont le nom ne porte aucun titre d'album exploitable. */
        SANS_TITRE("le nom du dossier ne porte aucun titre d'album"),

        /** Un album dont ni le chemin ni les fichiers ne nomment l'artiste. */
        SANS_ARTISTE("aucun artiste lisible : rapprochement possible sur le seul titre");

        private final String libelle;

        Nature(String libelle) {
            this.libelle = libelle;
        }

        /** Phrase qui dit ce qui a été constaté. */
        public String libelle() {
            return libelle;
        }
    }

    /**
     * Un dossier mal rangé.
     *
     * @param album  dossier concerné
     * @param nature ce qui a été constaté, le plus gênant d'abord quand plusieurs s'appliquent
     */
    public record Anomalie(Album album, Nature nature) {}

    /** Cherche les dossiers mal rangés, du plus lourd au plus léger. */
    public static List<Anomalie> chercher(Inventaire inventaire) {
        List<Album> albums = inventaire.albums();
        List<Anomalie> trouvees = new ArrayList<>();
        for (Album album : albums) {
            examiner(album, albums, inventaire).ifPresent(trouvees::add);
        }
        trouvees.sort(Comparator.comparingLong((Anomalie anomalie) -> anomalie.album().taille())
                .reversed()
                .thenComparing(anomalie -> anomalie.album().dossier().toString()));
        return List.copyOf(trouvees);
    }

    /**
     * Examine un dossier et rend le premier reproche qu'on puisse lui faire.
     *
     * <p>Un seul reproche, et le plus grave : un dossier posé à la racine est aussi, presque
     * toujours, un dossier sans artiste lisible. Les énumérer tous ferait trois lignes pour dire
     * une seule chose.
     */
    private static Optional<Anomalie> examiner(
            Album album, List<Album> tous, Inventaire inventaire) {
        if (inventaire.racines().contains(album.dossier())) {
            return Optional.of(new Anomalie(album, Nature.PISTES_EN_VRAC));
        }
        if (contientDAutresAlbums(album, tous)) {
            return Optional.of(new Anomalie(album, Nature.DOSSIER_MIXTE));
        }
        if (IdentificationDAlbum.estUnDossierSansTitre(nomDe(album.dossier()))) {
            return Optional.of(new Anomalie(album, Nature.SANS_TITRE));
        }
        if (album.nom().origineDeLArtiste() == OrigineDeLArtiste.INCONNUE
                && !album.nom().estCompilation()) {
            return Optional.of(new Anomalie(album, Nature.SANS_ARTISTE));
        }
        return Optional.empty();
    }

    /**
     * Vrai quand ce dossier en contient d'autres qui portent eux aussi des pistes.
     *
     * <p>C'est le rangement le plus trompeur : le dossier d'un artiste où traînent quelques
     * fichiers isolés à côté des dossiers de ses albums. Les fichiers isolés prennent alors le nom
     * de l'artiste pour titre d'album, et l'artiste lui-même se retrouve sans nom.
     */
    private static boolean contientDAutresAlbums(Album album, List<Album> tous) {
        Path dossier = album.dossier();
        return tous.stream()
                .anyMatch(autre -> !autre.dossier().equals(dossier)
                        && autre.dossier().startsWith(dossier));
    }

    private static String nomDe(Path dossier) {
        Path nom = dossier.getFileName();
        return nom == null ? dossier.toString() : nom.toString();
    }
}
