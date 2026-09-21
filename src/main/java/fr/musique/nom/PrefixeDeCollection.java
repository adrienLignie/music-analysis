package fr.musique.nom;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Préfixe commun aux fichiers d'un même dossier, à retirer avant de lire les titres des pistes.
 *
 * <p>Un dossier d'album nomme presque toujours ses pistes sur un même patron :
 *
 * <pre>
 * Pink Floyd - The Dark Side Of The Moon - 01 - Speak To Me.flac
 * Pink Floyd - The Dark Side Of The Moon - 02 - Breathe.flac
 * 01. Speak To Me.mp3
 * </pre>
 *
 * <p>Laissé en place, ce patron noie le titre de la piste sous le nom de l'artiste et de l'album :
 * toutes les pistes du dossier se ressemblent alors à quatre-vingt-dix pour cent, et le
 * rapprochement des pistes entre elles n'a plus aucun sens.
 *
 * <h2>Ce qui est retiré, et seulement cela</h2>
 * Uniquement les jetons <b>de tête</b>, et seulement tant qu'ils sont communs à tous les fichiers
 * du dossier. Les occurrences internes sont laissées : retirer partout le mot commun d'un dossier
 * {@code Love/} amputerait {@code Love Will Tear Us Apart} de son premier mot.
 *
 * <p>Le retrait s'arrête au premier <b>nombre</b>, et cette règle-là compte plus qu'elle n'en a
 * l'air : les numéros sont ce qui distingue les fichiers d'un dossier, donc ce qu'aucun patron
 * commun ne peut contenir, et c'est d'eux que se déduira plus tard un album à trous. Les lire est
 * le travail du parseur, pas celui-ci.
 *
 * <p>Le retrait s'interrompt aussi s'il ne devait plus rester un seul mot.
 */
public final class PrefixeDeCollection {

    /** En deçà, un patron commun relève de la coïncidence et non de la convention de nommage. */
    private static final int FRERES_MINIMUM = 3;

    private final Set<String> jetonsCommuns;

    private PrefixeDeCollection(Set<String> jetonsCommuns) {
        this.jetonsCommuns = jetonsCommuns;
    }

    /** Aucun préfixe : le nom des fichiers est rendu tel quel. */
    public static PrefixeDeCollection aucun() {
        return new PrefixeDeCollection(Set.of());
    }

    /**
     * Cherche les mots que tous les fichiers d'un dossier ont en commun.
     *
     * <p>Seuls les mots sont retenus, jamais les nombres : ce sont les numéros de piste qui varient
     * d'un fichier à l'autre, et ils sont traités à part au moment du retrait.
     */
    public static PrefixeDeCollection detecter(Collection<String> nomsDuDossier) {
        if (nomsDuDossier.size() < FRERES_MINIMUM) {
            return aucun();
        }
        Set<String> communs = null;
        for (String nom : nomsDuDossier) {
            Set<String> jetons = new HashSet<>(decouper(nom));
            jetons.removeIf(jeton -> Numeros.lireEntier(jeton).isPresent());
            if (communs == null) {
                communs = jetons;
            } else {
                communs.retainAll(jetons);
            }
            if (communs.isEmpty()) {
                return aucun();
            }
        }
        return new PrefixeDeCollection(Set.copyOf(communs));
    }

    /**
     * Retire le préfixe de ce nom.
     *
     * <p>Le découpage est fait sur la forme normalisée, mais la coupe sur le nom d'origine : les
     * parenthèses et les accents doivent survivre, car le parseur y lira encore un artiste et un
     * titre.
     */
    public String retirerDe(String nomDeFichier) {
        if (jetonsCommuns.isEmpty()) {
            return nomDeFichier;
        }
        String nom = Normalizer.normalize(nomDeFichier, Normalizer.Form.NFC);
        List<String> jetons = decouper(nom);
        int aRetirer = 0;
        while (aRetirer < jetons.size() - 1 && jetonsCommuns.contains(jetons.get(aRetirer))) {
            aRetirer++;
        }
        return aRetirer == 0 ? nomDeFichier : nom.substring(positionApres(nom, aRetirer)).trim();
    }

    /**
     * Position juste après le n-ième groupe de lettres et de chiffres.
     *
     * <p>Le nom est en forme composée avant d'arriver ici, ce qui garantit qu'un caractère accentué
     * compte pour une lettre et non pour une lettre suivie d'une marque : sans cette précaution, le
     * découpage et la coupe ne tomberaient pas au même endroit sur {@code Mélodie du Sud}.
     */
    private static int positionApres(String nom, int nombreDeJetons) {
        int jetonsVus = 0;
        int position = 0;
        while (position < nom.length() && jetonsVus < nombreDeJetons) {
            while (position < nom.length() && !Character.isLetterOrDigit(nom.charAt(position))) {
                position++;
            }
            while (position < nom.length() && Character.isLetterOrDigit(nom.charAt(position))) {
                position++;
            }
            jetonsVus++;
        }
        return position;
    }

    private static List<String> decouper(String texte) {
        return List.of(CleDeTitre.normaliser(texte).split(" "));
    }
}
