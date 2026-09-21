package fr.musique.rapport;

import fr.musique.doublons.GroupeDeDoublons;
import fr.musique.doublons.GroupeDePistes;
import fr.musique.scan.Inventaire;
import java.util.List;

/**
 * Tout ce que l'analyse a produit, sous la forme où le rapport le reçoit.
 *
 * <p>Un objet plutôt que cinq paramètres : les trois formes de rapport écrivent le même contenu, et
 * chaque section ajoutée se paierait autrement d'une signature modifiée en quatre endroits.
 *
 * @param inventaire     ce qu'a donné le parcours
 * @param doublons       groupes d'albums retenus, déjà triés
 * @param pistesEnDouble morceaux présents dans plusieurs dossiers, vide tant qu'on ne les a pas
 *                       demandés
 * @param groupesEcartes groupes qui n'étaient qu'un même dossier atteint par plusieurs chemins
 */
public record Analyse(
        Inventaire inventaire,
        List<GroupeDeDoublons> doublons,
        List<GroupeDePistes> pistesEnDouble,
        int groupesEcartes) {

    public Analyse {
        doublons = List.copyOf(doublons);
        pistesEnDouble = List.copyOf(pistesEnDouble);
    }

    /** Analyse qui n'a pas cherché les morceaux en double et n'a écarté aucun groupe. */
    public Analyse(Inventaire inventaire, List<GroupeDeDoublons> doublons) {
        this(inventaire, doublons, List.of(), 0);
    }
}
