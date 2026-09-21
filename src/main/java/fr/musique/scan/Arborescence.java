package fr.musique.scan;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Ce que le parcours a vu, avant qu'aucun nom n'ait été lu.
 *
 * <p>C'est le partage exact entre les deux moitiés de l'analyse : au-dessus, des entrées-sorties
 * et rien d'autre ; en dessous, du calcul et rien d'autre. La frontière permet de mener la seconde
 * en parallèle sans que rien n'y touche au disque.
 *
 * @param dossiers             dossiers contenant au moins un fichier audio retenu
 * @param racines              racines effectivement analysées, une fois les recouvrements résolus
 * @param repetitionsPhysiques chemins dont on sait qu'ils mènent à un fichier déjà compté par un
 *                             autre chemin de l'arborescence : ils ne pèsent rien de plus sur le
 *                             disque
 * @param fichiersIgnores      fichiers écartés parce que trop petits ou illisibles
 * @param dossiersIllisibles   dossiers que le système n'a laissé lire qu'en partie
 */
public record Arborescence(
        List<DossierTrouve> dossiers,
        Set<Path> racines,
        Set<Path> repetitionsPhysiques,
        int fichiersIgnores,
        int dossiersIllisibles) {

    public Arborescence {
        dossiers = List.copyOf(dossiers);
        racines = Set.copyOf(racines);
        repetitionsPhysiques = Set.copyOf(repetitionsPhysiques);
    }

    /** Nombre de fichiers retenus, tous dossiers confondus. */
    public int nombreDeFichiers() {
        return dossiers.stream().mapToInt(dossier -> dossier.fichiers().size()).sum();
    }

    /** Poids de tout ce qui occupe la place sans être un morceau retenu. */
    public long octetsHorsAudio() {
        return dossiers.stream().mapToLong(DossierTrouve::octetsHorsAudio).sum();
    }
}
