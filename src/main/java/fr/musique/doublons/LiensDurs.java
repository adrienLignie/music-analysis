package fr.musique.doublons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Démêle les groupes où plusieurs dossiers mènent aux mêmes fichiers.
 *
 * <p>Un lien dur donne deux noms à un même contenu, qui n'occupe la place qu'une fois. Les
 * logiciels de partage en posent systématiquement : l'album reste en partage sous son nom de
 * publication et apparaît dans la bibliothèque sous son titre propre. Compté deux fois, il devient
 * un faux doublon dont la suppression ne libérerait rien — et, pire, dont la suppression
 * <b>détruirait</b> l'unique exemplaire.
 *
 * <p>{@link Files#isSameFile} répond exactement à cette question, et de façon portable. La
 * vérification ne porte que sur les groupes déjà formés, et deux dossiers ne sont comparés que
 * s'ils comptent autant de pistes du même poids total : un lien dur ne peut changer ni l'un ni
 * l'autre. Elle coûte donc quelques appels système, pas un parcours de plus.
 */
public final class LiensDurs {

    private static final Logger log = LoggerFactory.getLogger(LiensDurs.class);

    private LiensDurs() {
        // Vérification seule, pas d'instance.
    }

    /**
     * Groupes démêlés, et nombre de groupes qui n'en étaient pas.
     *
     * @param groupes        groupes conservés, avec leurs liens durs relevés, retriés par place
     *                       récupérable puisque celle-ci a pu changer
     * @param groupesEcartes groupes dont tous les exemplaires désignaient les mêmes fichiers : il
     *                       n'y avait là aucun doublon
     */
    public record Resultat(List<GroupeDeDoublons> groupes, int groupesEcartes) {}

    /** Cherche, dans chaque groupe, les dossiers qui désignent des fichiers déjà comptés. */
    public static Resultat demeler(List<GroupeDeDoublons> groupes) {
        List<GroupeDeDoublons> retenus = new ArrayList<>();
        int ecartes = 0;
        for (GroupeDeDoublons groupe : groupes) {
            GroupeDeDoublons demele = groupe.avecLiensDurs(chercherDansLeGroupe(groupe));
            if (demele.exemplairesDistincts() > 1) {
                retenus.add(demele);
            } else {
                ecartes++;
            }
        }
        retenus.sort(GroupeDeDoublons.DU_PLUS_GROS_GAIN);
        return new Resultat(List.copyOf(retenus), ecartes);
    }

    /**
     * Relève les dossiers dont les fichiers ont déjà été vus dans ce groupe.
     *
     * <p>Les albums arrivent dans l'ordre où le rapport les présentera, le meilleur d'abord : le
     * premier exemplaire de chaque dossier physique est donc toujours celui qu'on garde, et ce
     * sont ses jumeaux qui sont marqués. Tout réordonnancement — celui que la lecture des
     * étiquettes peut provoquer — doit donc avoir lieu avant cette étape.
     */
    private static Set<Path> chercherDansLeGroupe(GroupeDeDoublons groupe) {
        Set<Path> liens = new LinkedHashSet<>();
        List<Album> distincts = new ArrayList<>();
        for (Album album : groupe.albums()) {
            if (dejaVu(album, distincts)) {
                liens.add(album.dossier());
            } else {
                distincts.add(album);
            }
        }
        return liens;
    }

    private static boolean dejaVu(Album album, List<Album> distincts) {
        for (Album connu : distincts) {
            // Un lien dur désigne le même contenu : le nombre de pistes et le poids total sont
            // forcément identiques. Ce test écarte d'emblée la quasi-totalité des paires sans
            // toucher au disque.
            if (connu.nombreDePistes() == album.nombreDePistes()
                    && connu.taille() == album.taille()
                    && memesFichiers(connu, album)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vrai quand chaque piste d'un dossier est, fichier pour fichier, celle de l'autre.
     *
     * <p>Les pistes sont comparées dans l'ordre où elles ont été rangées, qui est le même des deux
     * côtés. Un seul désaccord suffit à conclure que les deux dossiers sont bien deux dossiers :
     * il vaut mieux annoncer un doublon qui n'en est pas qu'inviter à supprimer un original.
     */
    private static boolean memesFichiers(Album premier, Album second) {
        for (int rang = 0; rang < premier.pistes().size(); rang++) {
            Piste unePiste = premier.pistes().get(rang);
            Piste lAutre = second.pistes().get(rang);
            if (unePiste.taille() != lAutre.taille()
                    || !memeFichier(unePiste.chemin(), lAutre.chemin())) {
                return false;
            }
        }
        return true;
    }

    private static boolean memeFichier(Path premier, Path second) {
        try {
            return Files.isSameFile(premier, second);
        } catch (IOException echec) {
            // Un fichier disparu ou illisible depuis le parcours : on préfère laisser le groupe
            // tel quel plutôt que d'annoncer une place récupérable qu'on n'a pas vérifiée.
            log.debug("Comparaison impossible entre {} et {} : {}",
                    premier, second, echec.getMessage());
            return false;
        }
    }
}
