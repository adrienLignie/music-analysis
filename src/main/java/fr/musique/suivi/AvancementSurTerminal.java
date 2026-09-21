package fr.musique.suivi;

import java.io.PrintStream;
import java.time.Duration;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Avancement affiché sur une seule ligne, réécrite en place.
 *
 * <p>Une ligne et non un défilement : le retour chariot ramène le curseur au début, et la ligne
 * suivante recouvre la précédente. Un terminal y voit un compteur qui tourne ; un fichier y verrait
 * cinquante mille lignes, et c'est pourquoi rien ne s'affiche quand la sortie d'erreur n'est pas
 * un terminal.
 *
 * <p>Le rythme est bridé : réécrire la ligne à chaque fichier coûte un appel système par fichier,
 * soit plus cher que le parcours lui-même sur un dossier local. Dix rafraîchissements par seconde
 * suffisent à ce que l'œil voie bouger quelque chose.
 */
public final class AvancementSurTerminal implements Avancement {

    private static final Duration INTERVALLE = Duration.ofMillis(100);

    /** Largeur au-delà de laquelle la ligne d'attente serait coupée par le terminal. */
    private static final int LARGEUR_MAXIMALE = 78;

    private final PrintStream sortie;
    private final LongSupplier horloge;
    private final long intervalleEnNanosecondes;

    private String etape = "";
    private long derniereEcriture;
    private int longueurAffichee;

    /**
     * Avancement écrit sur cette sortie.
     *
     * @param sortie  flux d'attente, normalement la sortie d'erreur
     * @param horloge source de temps en nanosecondes, que les tests remplacent pour ne pas
     *                dépendre du rythme réel de la machine
     */
    public AvancementSurTerminal(PrintStream sortie, LongSupplier horloge) {
        this.sortie = sortie;
        this.horloge = horloge;
        this.intervalleEnNanosecondes = INTERVALLE.toNanos();
        this.derniereEcriture = horloge.getAsLong() - intervalleEnNanosecondes;
    }

    /**
     * Avancement adapté à ce que la sortie d'erreur est.
     *
     * <p>Un terminal reçoit la ligne d'attente ; un fichier ou un tube n'en reçoit rien, car il
     * n'aurait aucun moyen d'en effacer les états successifs.
     *
     * @param silencieux vrai quand l'utilisateur a demandé le silence sans qu'on ait à deviner
     */
    public static Avancement pourLaSortieDErreur(boolean silencieux) {
        if (silencieux || System.console() == null) {
            return Avancement.muet();
        }
        return new AvancementSurTerminal(System.err, System::nanoTime);
    }

    @Override
    public void etape(String libelle) {
        effacer();
        this.etape = libelle;
        this.derniereEcriture = horloge.getAsLong() - intervalleEnNanosecondes;
    }

    @Override
    public void pas(long faits, long total) {
        long maintenant = horloge.getAsLong();
        if (maintenant - derniereEcriture < intervalleEnNanosecondes) {
            return;
        }
        derniereEcriture = maintenant;
        ecrire(total > 0
                ? String.format(Locale.FRANCE, "%s %d/%d", etape, faits, total)
                : String.format(Locale.FRANCE, "%s %d", etape, faits));
    }

    @Override
    public void fin() {
        effacer();
        sortie.flush();
    }

    private void ecrire(String ligne) {
        String tenue = ligne.length() > LARGEUR_MAXIMALE
                ? ligne.substring(0, LARGEUR_MAXIMALE)
                : ligne;
        // Les espaces recouvrent ce que la ligne précédente avait de plus long : sans eux, un
        // compteur qui passe de 1000 à 999 laisserait un chiffre orphelin derrière lui.
        sortie.print("\r" + tenue + " ".repeat(Math.max(0, longueurAffichee - tenue.length())));
        sortie.flush();
        longueurAffichee = tenue.length();
    }

    private void effacer() {
        if (longueurAffichee > 0) {
            sortie.print("\r" + " ".repeat(longueurAffichee) + "\r");
            longueurAffichee = 0;
        }
    }
}
