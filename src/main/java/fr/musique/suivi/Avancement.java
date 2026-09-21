package fr.musique.suivi;

/**
 * Ce que le programme dit de son travail pendant qu'il le fait.
 *
 * <p>Sur une bibliothèque en réseau, le parcours dure plusieurs minutes pendant lesquelles rien ne
 * distingue un programme qui travaille d'un programme bloqué. L'avancement n'a pas d'autre rôle
 * que celui-là : rendre l'attente lisible.
 *
 * <p>Il s'écrit sur la <b>sortie d'erreur</b>, jamais sur la sortie standard : le rapport doit
 * pouvoir être redirigé vers un fichier ou lu par un script sans recevoir une ligne d'attente
 * réécrite cinquante fois.
 */
public interface Avancement {

    /** Annonce l'étape en cours. Ce qui s'affichait de la précédente est effacé. */
    void etape(String libelle);

    /**
     * Rend compte du travail fait dans l'étape en cours.
     *
     * @param faits ce qui est fait
     * @param total ce qu'il y a à faire, ou une valeur négative ou nulle quand on l'ignore encore
     */
    void pas(long faits, long total);

    /** Efface la dernière ligne affichée : le rapport doit commencer sur une ligne propre. */
    void fin();

    /** Avancement qui ne dit rien, pour un script, un fichier de sortie ou un test. */
    static Avancement muet() {
        return new Avancement() {

            @Override
            public void etape(String libelle) {
                // Rien à annoncer.
            }

            @Override
            public void pas(long faits, long total) {
                // Rien à annoncer.
            }

            @Override
            public void fin() {
                // Rien à effacer.
            }
        };
    }
}
