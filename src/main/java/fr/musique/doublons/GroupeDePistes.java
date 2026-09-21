package fr.musique.doublons;

import java.util.Comparator;
import java.util.List;

/**
 * Un ensemble de fichiers qui semblent porter le même morceau, hors de tout album.
 *
 * <p>Ce n'est pas la même chose qu'un groupe d'albums en double, et cela ne se lit pas de la même
 * façon : le même morceau du même artiste figure légitimement sur son album, sur une compilation
 * et sur un best of. La liste sert à repérer un dossier de téléchargements qui répète une
 * discothèque déjà rangée, pas à désigner des fichiers à supprimer.
 *
 * @param pistes exemplaires, le meilleur d'abord
 * @param motif  phrase qui dit pourquoi ces fichiers ont été réunis
 */
public record GroupeDePistes(List<Piste> pistes, String motif) {

    /** Ordre dans lequel les groupes sont présentés : la place à récupérer d'abord. */
    public static final Comparator<GroupeDePistes> DU_PLUS_GROS_GAIN =
            Comparator.comparingLong(GroupeDePistes::placeRecuperable).reversed()
                    .thenComparing(groupe -> groupe.celleAGarder().chemin().toString());

    public GroupeDePistes {
        pistes = List.copyOf(pistes);
    }

    /** Fichier que le programme propose de garder : le meilleur format, puis le plus lourd. */
    public Piste celleAGarder() {
        return pistes.get(0);
    }

    /** Place qu'on récupérerait en ne gardant que ce fichier-là. */
    public long placeRecuperable() {
        return pistes.stream().mapToLong(Piste::taille).sum() - celleAGarder().taille();
    }
}
