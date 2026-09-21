package fr.musique.nom;

import java.util.Map;
import java.util.OptionalInt;

/**
 * Lecture des nombres qu'un nom de dossier ou de piste peut porter : numéro de volume
 * ({@code Greatest Hits Vol. 2}), numéro de disque ({@code CD1}, {@code Disc II}) et numéro de
 * piste ({@code 03 - Money}).
 *
 * <p>Les trois formes — chiffres arabes, chiffres romains, nombres en lettres — coexistent au sein
 * d'une même discographie, parfois d'un même dossier. Sans normalisation commune,
 * {@code Live Vol. II} et {@code Live Vol. 2} passeraient pour deux albums différents.
 */
public final class Numeros {

    /**
     * Au-delà, un nombre n'est plus un numéro de volume mais un élément du titre :
     * {@code Berlin 1984}, {@code Studio 54}.
     */
    public static final int VOLUME_MAXIMUM = 30;

    /** Au-delà, un nombre en tête de nom de fichier n'est plus un numéro de piste. */
    public static final int PISTE_MAXIMUM = 99;

    private static final Map<String, Integer> ROMAINS = Map.ofEntries(
            Map.entry("i", 1), Map.entry("ii", 2), Map.entry("iii", 3), Map.entry("iv", 4),
            Map.entry("v", 5), Map.entry("vi", 6), Map.entry("vii", 7), Map.entry("viii", 8),
            Map.entry("ix", 9), Map.entry("x", 10), Map.entry("xi", 11), Map.entry("xii", 12),
            Map.entry("xiii", 13));

    private static final Map<String, Integer> EN_LETTRES = Map.ofEntries(
            Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4),
            Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7), Map.entry("eight", 8),
            Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11),
            Map.entry("twelve", 12), Map.entry("thirteen", 13),
            // Le français partage « six » avec l'anglais, déjà présent plus haut. Une seconde
            // entrée serait refusée par Map.ofEntries, et elle porterait la même valeur.
            Map.entry("un", 1), Map.entry("deux", 2), Map.entry("trois", 3), Map.entry("quatre", 4),
            Map.entry("cinq", 5), Map.entry("sept", 7), Map.entry("huit", 8),
            Map.entry("neuf", 9), Map.entry("dix", 10),
            Map.entry("premier", 1), Map.entry("premiere", 1), Map.entry("deuxieme", 2),
            Map.entry("second", 2), Map.entry("seconde", 2), Map.entry("troisieme", 3));

    private Numeros() {
        // Fonctions de lecture seules, pas d'instance.
    }

    /**
     * Lit un jeton comme numéro de volume ou de disque, quelle que soit sa forme.
     *
     * <p>Un nombre hors de l'intervalle utile est refusé, ce qui protège les titres numériques.
     */
    public static OptionalInt lireVolume(String jeton) {
        OptionalInt arabe = lireEntier(jeton);
        if (arabe.isPresent()) {
            return estUnVolumePlausible(arabe.getAsInt()) ? arabe : OptionalInt.empty();
        }
        Integer romain = ROMAINS.get(jeton);
        if (romain != null) {
            return OptionalInt.of(romain);
        }
        Integer enLettres = EN_LETTRES.get(jeton);
        return enLettres != null ? OptionalInt.of(enLettres) : OptionalInt.empty();
    }

    /** Lit un jeton entièrement composé de chiffres, sans contrainte d'intervalle. */
    public static OptionalInt lireEntier(String jeton) {
        if (jeton.isEmpty() || jeton.length() > 9) {
            return OptionalInt.empty();
        }
        for (int i = 0; i < jeton.length(); i++) {
            if (!Character.isDigit(jeton.charAt(i))) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.of(Integer.parseInt(jeton));
    }

    /** Indique si ce jeton est un chiffre romain isolé. */
    public static boolean estRomain(String jeton) {
        return ROMAINS.containsKey(jeton);
    }

    /** Indique si ce nombre peut être un numéro de volume ou de disque. */
    public static boolean estUnVolumePlausible(int valeur) {
        return valeur >= 1 && valeur <= VOLUME_MAXIMUM;
    }

    /** Indique si ce nombre peut être un numéro de piste. */
    public static boolean estUnePistePlausible(int valeur) {
        return valeur >= 1 && valeur <= PISTE_MAXIMUM;
    }
}
