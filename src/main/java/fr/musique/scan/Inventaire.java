package fr.musique.scan;

import fr.musique.doublons.Album;
import fr.musique.doublons.Piste;
import fr.musique.media.Etiquettes;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Ce que l'analyse a retenu de l'arborescence.
 *
 * <h2>Pourquoi les répétitions physiques sont tenues à part</h2>
 * Un fichier atteint par deux chemins figure dans deux albums, parce que le rapprochement a besoin
 * des deux dossiers : c'est souvent sous l'un d'eux que l'album est reconnu. Mais il n'occupe la
 * place qu'une fois : son poids n'est compté que dans le premier des deux, et un album dont toutes
 * les pistes sont ainsi répétées ne pèse rien du tout.
 *
 * @param albums               tous les albums retenus
 * @param racines              racines effectivement analysées
 * @param repetitionsPhysiques chemins menant à un fichier déjà compté sous un autre chemin
 * @param fichiersIgnores      fichiers écartés parce que trop petits ou illisibles
 * @param dossiersIllisibles   dossiers que le système n'a laissé lire qu'en partie
 * @param etiquettes           étiquettes lues dans les fichiers qu'on a sondés ; vide tant que
 *                             l'utilisateur ne les a pas demandées
 */
public record Inventaire(
        List<Album> albums,
        Set<Path> racines,
        Set<Path> repetitionsPhysiques,
        int fichiersIgnores,
        int dossiersIllisibles,
        Etiquettes etiquettes) {

    public Inventaire {
        albums = List.copyOf(albums);
        racines = Set.copyOf(racines);
        repetitionsPhysiques = Set.copyOf(repetitionsPhysiques);
    }

    /** Inventaire dont les étiquettes n'ont pas été lues. */
    public Inventaire(List<Album> albums, Set<Path> racines, Set<Path> repetitionsPhysiques,
            int fichiersIgnores, int dossiersIllisibles) {
        this(albums, racines, repetitionsPhysiques, fichiersIgnores, dossiersIllisibles,
                Etiquettes.aucune());
    }

    /** Inventaire sans lien dur connu ni étiquette lue, forme la plus simple. */
    public Inventaire(List<Album> albums, int fichiersIgnores, int dossiersIllisibles) {
        this(albums, Set.of(), Set.of(), fichiersIgnores, dossiersIllisibles, Etiquettes.aucune());
    }

    /** Même inventaire, avec les étiquettes qu'on est allé lire. */
    public Inventaire avecEtiquettes(Etiquettes lues) {
        return new Inventaire(albums, racines, repetitionsPhysiques, fichiersIgnores,
                dossiersIllisibles, lues);
    }

    /**
     * Albums qui pèsent pour eux-mêmes sur le disque.
     *
     * <p>C'est la liste dont le total et le classement sont tirés : un dossier dont toutes les
     * pistes sont atteintes par un autre chemin n'occupe aucune place, et le présenter au
     * classement des plus lourds désignerait deux fois le même objet.
     */
    public List<Album> albumsDistincts() {
        return repetitionsPhysiques.isEmpty()
                ? albums
                : albums.stream().filter(album -> album.taille() > 0).toList();
    }

    /** Indique si cette piste mène à un fichier déjà compté ailleurs dans l'inventaire. */
    public boolean estUneRepetition(Piste piste) {
        return repetitionsPhysiques.contains(piste.chemin());
    }

    /** Nombre de pistes retenues, chaque fichier physique ne comptant qu'une fois. */
    public int nombreDePistes() {
        return (int) albums.stream()
                .flatMap(album -> album.pistes().stream())
                .filter(piste -> !estUneRepetition(piste))
                .count();
    }

    /** Poids cumulé des albums retenus, chaque fichier physique ne comptant qu'une fois. */
    public long tailleTotale() {
        return albums.stream().mapToLong(Album::taille).sum();
    }

    /**
     * Poids cumulé de ce qui occupe la place sans être un morceau.
     *
     * <p>Pochettes, livrets numérisés, journaux d'extraction, listes de lecture, fichiers écartés
     * pour leur petite taille. Rien de tout cela n'entre dans {@link #tailleTotale} ni dans le
     * classement — mais le disque, lui, le compte, et sur une grande discothèque cela pèse
     * plusieurs gigaoctets qu'aucune autre ligne du rapport ne montrerait.
     *
     * <p>Contrairement aux morceaux, ces fichiers ne sont pas dédoublonnés : le programme ne
     * relève les liens durs que là où ils changent une décision, et personne ne supprime un
     * dossier pour ses pochettes.
     */
    public long octetsHorsAudio() {
        return albums.stream().mapToLong(Album::octetsHorsAudio).sum();
    }
}
