package fr.musique.rapport;

/**
 * Une mise en forme du résultat de l'analyse.
 *
 * <p>Les trois formes répondent au même contenu : ce qui a été parcouru, les albums en double, le
 * classement des dossiers par poids, et ce qui cloche dans le rangement. Seule la présentation
 * change — l'une pour être lue, les deux autres pour être reprises par un script.
 */
public interface Rapport {

    /**
     * Écrit le rapport.
     *
     * @param analyse ce que l'analyse a produit
     * @param top     taille du classement des albums les plus lourds
     */
    void ecrire(Analyse analyse, int top);
}
