package fr.musique.rapport;

import java.util.Locale;

/** Mise en forme des poids, en octets. */
public final class Tailles {

    private static final long KILO = 1000L;
    private static final String[] UNITES = {"o", "ko", "Mo", "Go", "To"};

    private Tailles() {
        // Fonction de présentation seule, pas d'instance.
    }

    /**
     * Rend une taille lisible, en unités décimales.
     *
     * <p>Décimales et non binaires : c'est ce qu'affichent l'explorateur de fichiers et les
     * fabricants de disques, et le rapport doit pouvoir être comparé à ce que tu as sous les yeux.
     */
    public static String enTexte(long octets) {
        double valeur = octets;
        int unite = 0;
        while (valeur >= KILO && unite < UNITES.length - 1) {
            valeur /= KILO;
            unite++;
        }
        return unite == 0
                ? octets + " o"
                : String.format(Locale.FRANCE, "%.2f %s", valeur, UNITES[unite]);
    }
}
