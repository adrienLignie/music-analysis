package fr.musique.rapport;

import fr.musique.doublons.Album;
import fr.musique.doublons.Piste;
import fr.musique.media.Etiquettes;
import fr.musique.nom.Formats;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Repère les fichiers dont le poids ne cadre pas avec ce que leur format promet.
 *
 * <h2>Deux façons de juger, selon ce qu'on sait</h2>
 * Sans les étiquettes, on n'a que des poids, et un poids ne dit rien seul : un morceau d'une
 * minute pèse légitimement le dixième d'un morceau de dix. La comparaison se fait donc
 * <b>à l'intérieur d'un même dossier</b>, où les morceaux sont encodés de la même façon : une
 * piste huit fois plus légère que la médiane de ses voisines n'est pas un interlude, c'est un
 * fichier coupé.
 *
 * <p>Avec les étiquettes, c'est le <b>débit</b> qui juge, et il juge bien mieux : un flac à cent
 * kilobits par seconde n'est pas du flac, c'est un mp3 réencodé dans l'habit du sans perte. Aucun
 * nom de dossier, aucune extension ne trahit ce mensonge-là — seule la division du poids par la
 * durée le révèle.
 *
 * <p>Ni l'un ni l'autre n'est une certitude : c'est une liste de fichiers à écouter pour vérifier
 * qu'ils se lisent jusqu'au bout, ce que rien d'autre ne signale.
 */
public final class PistesSuspectes {

    /** En deçà, un dossier n'a pas assez de pistes pour qu'une médiane veuille dire quelque chose. */
    private static final int PISTES_MINIMUM = 4;

    /**
     * Rapport à la médiane du dossier en deçà duquel une piste est tenue pour coupée.
     *
     * <p>Huit fois plus légère : le seuil est volontairement grossier. Un interlude de vingt
     * secondes au milieu d'un album de morceaux de quatre minutes fait déjà un rapport de douze,
     * et l'on préfère laisser passer quelques fichiers coupés plutôt que de remplir la section
     * d'intermèdes légitimes.
     */
    private static final int RAPPORT_A_LA_MEDIANE = 8;

    private PistesSuspectes() {
        // Fonction d'analyse seule, pas d'instance.
    }

    /** Ce qui rend une piste suspecte. */
    public enum Nature {

        /** Son débit réel est trop bas pour le format qu'elle porte. */
        DEBIT,

        /** Son poids est sans commune mesure avec celui de ses voisines de dossier. */
        POIDS
    }

    /**
     * Une piste dont quelque chose ne va pas.
     *
     * @param piste    fichier concerné
     * @param nature   ce qui l'a désignée
     * @param constate débit mesuré en octets par seconde, ou poids en octets
     * @param attendu  débit plancher du format, ou poids médian du dossier
     */
    public record Suspecte(Piste piste, Nature nature, long constate, long attendu) {}

    /** Cherche les pistes suspectes sur leur seul poids, de la plus légère à la plus lourde. */
    public static List<Suspecte> chercher(List<Album> albums) {
        return chercher(albums, Etiquettes.aucune());
    }

    /** Cherche les pistes suspectes, de la plus légère à la plus lourde. */
    public static List<Suspecte> chercher(List<Album> albums, Etiquettes etiquettes) {
        List<Suspecte> trouvees = new ArrayList<>();
        for (Album album : albums) {
            long mediane = poidsMedian(album);
            for (Piste piste : album.pistes()) {
                examiner(piste, mediane, album.nombreDePistes(), etiquettes)
                        .ifPresent(trouvees::add);
            }
        }
        trouvees.sort(Comparator.comparingLong((Suspecte suspecte) -> suspecte.piste().taille())
                .thenComparing(suspecte -> suspecte.piste().chemin().toString()));
        return List.copyOf(trouvees);
    }

    /**
     * Examine une piste, par son débit si on le connaît, par son poids sinon.
     *
     * <p>Le débit l'emporte dès qu'il est mesuré : il répond à la question qu'on se pose vraiment,
     * là où le poids n'en donne qu'un indice tiré du voisinage.
     */
    private static Optional<Suspecte> examiner(
            Piste piste, long mediane, int pistesDuDossier, Etiquettes etiquettes) {
        OptionalLong debit = etiquettes.debitDe(piste.chemin(), piste.taille());
        OptionalLong plancher = Formats.debitPlancher(piste.nom().extension());
        if (debit.isPresent() && plancher.isPresent()) {
            return debit.getAsLong() < plancher.getAsLong()
                    ? Optional.of(new Suspecte(
                            piste, Nature.DEBIT, debit.getAsLong(), plancher.getAsLong()))
                    : Optional.empty();
        }
        if (pistesDuDossier < PISTES_MINIMUM || mediane <= 0) {
            return Optional.empty();
        }
        long plafond = mediane / RAPPORT_A_LA_MEDIANE;
        return piste.taille() < plafond
                ? Optional.of(new Suspecte(piste, Nature.POIDS, piste.taille(), mediane))
                : Optional.empty();
    }

    /**
     * Poids médian des pistes du dossier.
     *
     * <p>La médiane et non la moyenne : une seule piste énorme — un concert d'une heure rangé
     * parmi des chansons — tirerait la moyenne assez haut pour rendre suspecte la moitié de
     * l'album.
     */
    private static long poidsMedian(Album album) {
        List<Long> poids = album.pistes().stream()
                .map(Piste::taille)
                .sorted()
                .toList();
        return poids.isEmpty() ? 0 : poids.get(poids.size() / 2);
    }
}
