package fr.musique.doublons;

import fr.musique.nom.Formats;
import fr.musique.nom.NomDAlbum;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Un album : un <b>répertoire</b>, ce qu'il contient, et ce qu'on a su lire de son chemin.
 *
 * <h2>Pourquoi le répertoire et non le fichier</h2>
 * C'est la différence de fond avec une vidéothèque, où un film est un fichier. Un album est un
 * dossier de douze fichiers, et c'est lui qu'on garde ou qu'on supprime : personne ne supprime six
 * pistes sur douze. Le classement par poids porte donc sur des dossiers, et un coffret de trois
 * disques compte pour un seul album, de la somme de ses trois dossiers — sans quoi il
 * apparaîtrait trois fois, à un tiers de son poids, et manquerait le classement qu'il mérite.
 *
 * @param dossier         chemin du dossier qui porte l'album
 * @param pistes          fichiers audio qu'il contient, ses disques réunis, triés par disque puis
 *                        par numéro de piste
 * @param nom             identité lue dans le chemin
 * @param octets          poids réel sur le disque : les pistes qui ne sont qu'un second chemin
 *                        vers un fichier déjà compté ailleurs n'y entrent pas
 * @param octetsHorsAudio poids de ce que le dossier contient d'autre que ses morceaux — pochettes,
 *                        livrets numérisés, journaux d'extraction. Ce n'est pas de la musique,
 *                        mais cela occupe bien la place, et parfois plus que la musique elle-même
 */
public record Album(
        Path dossier, List<Piste> pistes, NomDAlbum nom, long octets, long octetsHorsAudio) {

    public Album {
        pistes = List.copyOf(pistes);
    }

    /** Album dont aucune piste n'est un double chemin vers un fichier déjà compté. */
    public static Album de(Path dossier, List<Piste> pistes, NomDAlbum nom) {
        return de(dossier, pistes, nom, Set.of(), 0);
    }

    /**
     * Album dont certaines pistes sont atteintes par un second chemin.
     *
     * <p>Elles restent dans l'album — c'est bien ce que le dossier contient — mais ne pèsent plus :
     * un lien dur n'occupe la place qu'une fois, et les compter deux fois gonflerait le total de
     * la bibliothèque comme le gain annoncé d'une suppression.
     */
    public static Album de(Path dossier, List<Piste> pistes, NomDAlbum nom, Set<Path> repetitions) {
        return de(dossier, pistes, nom, repetitions, 0);
    }

    /** Album dont on a aussi pesé ce qui n'est pas de la musique. */
    public static Album de(Path dossier, List<Piste> pistes, NomDAlbum nom, Set<Path> repetitions,
            long octetsHorsAudio) {
        long octets = pistes.stream()
                .filter(piste -> !repetitions.contains(piste.chemin()))
                .mapToLong(Piste::taille)
                .sum();
        return new Album(dossier, pistes, nom, octets, octetsHorsAudio);
    }

    /** Poids réel de l'album sur le disque, en octets. */
    public long taille() {
        return octets;
    }

    /**
     * Poids total du dossier sur le disque, musique et reste confondus.
     *
     * <p>C'est ce que l'explorateur de fichiers annonce, et donc ce qu'on récupérerait vraiment en
     * supprimant le dossier. Le classement, lui, reste établi sur la musique seule : deux albums
     * ne se comparent pas au poids de leurs pochettes.
     */
    public long tailleDuDossier() {
        return octets + octetsHorsAudio;
    }

    /** Nombre de pistes du dossier, disques réunis. */
    public int nombreDePistes() {
        return pistes.size();
    }

    /**
     * Format qui pèse le plus lourd dans l'album.
     *
     * <p>Au poids et non au nombre de fichiers : un album de douze flac accompagné d'un mp3 de
     * présentation reste un album flac, et c'est le poids qui le dit le plus sûrement.
     */
    public String formatDominant() {
        Map<String, Long> parFormat = new LinkedHashMap<>();
        for (Piste piste : pistes) {
            parFormat.merge(piste.nom().extension(), piste.taille(), Long::sum);
        }
        return parFormat.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("");
    }

    /** Rang de qualité du format dominant. */
    public int rangDeFormat() {
        return Formats.rang(formatDominant());
    }

    /** Indique que l'album est dans un format qui restitue la source intacte. */
    public boolean estSansPerte() {
        return Formats.estSansPerte(formatDominant());
    }

    /**
     * Poids moyen d'une piste, en octets.
     *
     * <p>C'est le seul indice de débit qu'on ait sans ouvrir un fichier : à durée comparable, un
     * album dont les pistes pèsent deux fois plus lourd est encodé deux fois mieux. L'indice ne
     * vaut qu'entre deux exemplaires du <b>même</b> album, où les durées sont les mêmes des deux
     * côtés — ailleurs, il ne compare que la longueur des morceaux.
     */
    public long poidsMoyenDUnePiste() {
        return pistes.isEmpty() ? 0 : octets / pistes.size();
    }

    /**
     * Numéros de pistes lus, dans l'ordre, pour les pistes qui en portent un.
     *
     * <p>Les numéros de disque sont ignorés : un coffret de deux disques recommence à 1 au second,
     * et c'est ce qui rend la détection des trous impossible sans distinguer les disques. La
     * lecture se fait donc disque par disque, chez l'appelant.
     */
    public List<Integer> numerosDuDisque(OptionalInt disque) {
        return pistes.stream()
                .filter(piste -> memeDisque(piste, disque))
                .map(piste -> piste.nom().numero())
                .filter(OptionalInt::isPresent)
                .map(OptionalInt::getAsInt)
                .sorted()
                .distinct()
                .toList();
    }

    /** Numéros de disques rencontrés dans l'album, l'absence de numéro comptant pour un disque. */
    public List<OptionalInt> disques() {
        return pistes.stream()
                .map(piste -> piste.nom().disque())
                .distinct()
                .sorted(Comparator.comparingInt(disque -> disque.orElse(0)))
                .toList();
    }

    private static boolean memeDisque(Piste piste, OptionalInt disque) {
        return piste.nom().disque().equals(disque);
    }
}
