package fr.musique;

/**
 * Codes de sortie du programme, pour qu'un script sache ce qui s'est passé.
 *
 * <p>Les codes 1 et 2 appartiennent à picocli, qui les rend respectivement pour une erreur interne
 * et pour une ligne de commande mal formée. Les codes propres au programme commencent donc à 3 :
 * réutiliser le 2 rendrait indiscernables une faute de frappe dans les options et une
 * bibliothèque vide, qui n'appellent pas la même réaction.
 */
public final class CodeSortie {

    /** Le rapport a été produit. */
    public static final int SUCCES = 0;

    /** Les chemins donnés ne contenaient aucun fichier audio : sans doute une erreur de chemin. */
    public static final int RIEN_A_ANALYSER = 3;

    private CodeSortie() {
        // Constantes seules, pas d'instance.
    }
}
