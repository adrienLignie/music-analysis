package fr.musique.doublons;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Un ensemble de dossiers qui semblent porter le même album.
 *
 * @param albums    exemplaires, le meilleur d'abord ; c'est {@link Qualite} qui en décide, et non
 *                  le seul poids
 * @param confiance ce que vaut le rapprochement
 * @param motif     phrase qui dit pourquoi ces dossiers ont été réunis, pour que le verdict reste
 *                  vérifiable sans relire le code
 * @param liensDurs dossiers dont on a vérifié que leurs fichiers sont déjà présents dans le
 *                  groupe : les supprimer ne libérerait rien
 */
public record GroupeDeDoublons(
        List<Album> albums, NiveauDeConfiance confiance, String motif, Set<Path> liensDurs) {

    /**
     * Ordre dans lequel les groupes sont présentés : la place à récupérer d'abord, le chemin de
     * l'exemplaire à garder pour départager. Le chemin comme second critère est ce qui rend le
     * rapport comparable d'une exécution à l'autre.
     */
    public static final Comparator<GroupeDeDoublons> DU_PLUS_GROS_GAIN =
            Comparator.comparingLong(GroupeDeDoublons::placeRecuperable).reversed()
                    .thenComparing(groupe -> groupe.celuiAGarder().dossier().toString());

    public GroupeDeDoublons {
        albums = List.copyOf(albums);
        liensDurs = Set.copyOf(liensDurs);
    }

    /** Groupe dont les liens durs n'ont pas encore été cherchés. */
    public GroupeDeDoublons(List<Album> albums, NiveauDeConfiance confiance, String motif) {
        this(albums, confiance, motif, Set.of());
    }

    /** Même groupe, avec les liens durs relevés sur le disque. */
    public GroupeDeDoublons avecLiensDurs(Set<Path> releves) {
        return new GroupeDeDoublons(albums, confiance, motif, releves);
    }

    /**
     * Même groupe, ses exemplaires remis dans cet ordre.
     *
     * <p>Sert quand une information arrivée après coup — le débit réel lu dans les fichiers —
     * change l'exemplaire qu'il vaut mieux garder. Le réordonnancement doit avoir lieu
     * <b>avant</b> que les liens durs soient cherchés : c'est le premier exemplaire de chaque
     * dossier physique qui est conservé, et les suivants qui sont marqués.
     */
    public GroupeDeDoublons avecOrdre(Comparator<Album> ordre) {
        return new GroupeDeDoublons(
                albums.stream().sorted(ordre).toList(), confiance, motif, liensDurs);
    }

    /** Même groupe, avec une confiance et un motif révisés. */
    public GroupeDeDoublons avecVerdict(NiveauDeConfiance revue, String motifRevu) {
        return new GroupeDeDoublons(albums, revue, motifRevu, liensDurs);
    }

    /**
     * Place qu'on récupérerait en ne gardant que l'exemplaire à garder.
     *
     * <p>Les exemplaires reconnus comme liens durs d'un autre membre du groupe n'y entrent pas :
     * deux chemins vers les mêmes fichiers ne pèsent qu'une fois sur le disque, et en supprimer un
     * ne libère rien.
     */
    public long placeRecuperable() {
        return albums.stream()
                .filter(album -> !estUnLienDur(album))
                .mapToLong(Album::taille)
                .sum()
                - celuiAGarder().taille();
    }

    /** Indique si cet exemplaire désigne des fichiers déjà comptés ailleurs dans le groupe. */
    public boolean estUnLienDur(Album album) {
        return liensDurs.contains(album.dossier());
    }

    /** Nombre de dossiers réellement distincts sur le disque. */
    public int exemplairesDistincts() {
        return albums.size() - liensDurs.size();
    }

    /**
     * Exemplaire que le programme propose de garder.
     *
     * <p>C'est une proposition et non un verdict : elle repose sur ce que les noms annoncent, que
     * rien n'oblige à dire vrai.
     */
    public Album celuiAGarder() {
        return albums.get(0);
    }
}
