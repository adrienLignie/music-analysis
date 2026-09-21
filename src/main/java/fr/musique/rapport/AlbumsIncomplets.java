package fr.musique.rapport;

import fr.musique.doublons.Album;
import fr.musique.doublons.Piste;
import fr.musique.media.Etiquettes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeSet;

/**
 * Repère les albums auxquels il manque des pistes.
 *
 * <p>C'est le pendant, pour une discothèque, des fichiers vidéo trop légers pour ce qu'ils
 * annoncent : la trace d'un téléchargement interrompu. Mais elle se lit ici bien plus sûrement,
 * parce que les pistes sont <b>numérotées</b> : un dossier qui contient les pistes 1, 2, 3, 7 et 8
 * n'est pas un album sobre, il lui manque trois morceaux.
 *
 * <p>Deux précautions évitent les fausses alertes. Il faut d'abord assez de pistes numérotées pour
 * que la numérotation veuille dire quelque chose — un album dont aucun fichier ne porte de numéro
 * n'est pas incomplet, il est mal nommé, ce qui est une autre section. Et chaque disque est
 * examiné séparément : un coffret recommence à 1 au second disque, et les réunir ferait voir des
 * trous partout.
 */
public final class AlbumsIncomplets {

    /** En deçà, l'absence d'une piste ne prouve rien : un maxi de trois titres en compte trois. */
    private static final int PISTES_MINIMUM = 4;

    /** Part des pistes qui doivent porter un numéro pour que la numérotation fasse foi. */
    private static final double PART_NUMEROTEE_MINIMALE = 0.6;

    private AlbumsIncomplets() {
        // Fonction d'analyse seule, pas d'instance.
    }

    /**
     * Un album auquel il manque des pistes.
     *
     * @param album     dossier concerné
     * @param attendues numéro de piste le plus haut rencontré, c'est-à-dire le nombre de pistes
     *                  que l'album devrait compter
     * @param manquants numéros absents, dans l'ordre
     */
    public record Incomplet(Album album, int attendues, List<Integer> manquants) {

        public Incomplet {
            manquants = List.copyOf(manquants);
        }
    }

    /** Cherche les albums à trous, du plus troué au moins troué. */
    public static List<Incomplet> chercher(List<Album> albums) {
        return chercher(albums, Etiquettes.aucune());
    }

    /**
     * Cherche les albums à trous, du plus troué au moins troué.
     *
     * <p>Quand les étiquettes ont été lues, c'est le numéro qu'elles portent qui fait foi : un
     * fichier renommé perd son numéro de nom bien avant de perdre celui qu'il porte en lui.
     */
    public static List<Incomplet> chercher(List<Album> albums, Etiquettes etiquettes) {
        List<Incomplet> trouves = new ArrayList<>();
        for (Album album : albums) {
            examiner(album, etiquettes).ifPresent(trouves::add);
        }
        trouves.sort(Comparator.comparingInt((Incomplet incomplet) -> incomplet.manquants().size())
                .reversed()
                .thenComparing(incomplet -> incomplet.album().dossier().toString()));
        return List.copyOf(trouves);
    }

    private static Optional<Incomplet> examiner(Album album, Etiquettes etiquettes) {
        if (album.nombreDePistes() < PISTES_MINIMUM) {
            return Optional.empty();
        }
        List<Integer> manquants = new ArrayList<>();
        int attendues = 0;
        for (OptionalInt disque : album.disques()) {
            List<Piste> duDisque = album.pistes().stream()
                    .filter(piste -> piste.nom().disque().equals(disque))
                    .toList();
            TreeSet<Integer> numeros = new TreeSet<>();
            for (Piste piste : duDisque) {
                numeroDe(piste, etiquettes).ifPresent(numeros::add);
            }
            if (duDisque.size() < PISTES_MINIMUM
                    || numeros.size() < duDisque.size() * PART_NUMEROTEE_MINIMALE) {
                continue;
            }
            int dernier = numeros.last();
            attendues += dernier;
            for (int numero = 1; numero <= dernier; numero++) {
                if (!numeros.contains(numero)) {
                    manquants.add(numero);
                }
            }
        }
        return manquants.isEmpty()
                ? Optional.empty()
                : Optional.of(new Incomplet(album, attendues, manquants));
    }

    /** Numéro de la piste : celui de son étiquette s'il a été lu, celui de son nom sinon. */
    private static OptionalInt numeroDe(Piste piste, Etiquettes etiquettes) {
        OptionalInt etiquette = etiquettes.de(piste.chemin()).numero();
        return etiquette.isPresent() ? etiquette : piste.nom().numero();
    }
}
