package fr.musique.doublons;

import fr.musique.media.Etiquettes;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

/**
 * L'ordre dans lequel les exemplaires d'un même album sont présentés, le meilleur d'abord.
 *
 * <h2>Pourquoi ce n'est pas le poids qui décide</h2>
 * Le plus gros dossier n'est pas le meilleur, et il peut même être le pire : un album au format
 * sans compression pèse trois fois un flac qui porte exactement le même son, et un téléchargement
 * interrompu de trois pistes sur douze l'emporterait sur l'album entier qu'il prétend doubler.
 *
 * <p>Le classement suit donc ce qui compte, du plus décisif au moins décisif :
 * <ol>
 *   <li>la <b>complétude</b>. Un album auquel il manque la moitié de ses pistes n'est pas un
 *       exemplaire de cet album, c'est un morceau d'exemplaire. Rien ne rachète des pistes
 *       absentes, pas même le meilleur format du monde : c'est pourquoi ce critère passe
 *       devant ;</li>
 *   <li>le <b>format</b>, qui est ici une donnée et non une déclaration : un fichier
 *       {@code .flac} est du flac, là où un film qui s'annonce en 1080p peut ne pas l'être ;</li>
 *   <li>le <b>débit</b> — réel quand on est allé le mesurer, annoncé par le nom sinon, et à
 *       défaut le poids moyen d'une piste, qui en tient lieu entre deux exemplaires du même
 *       album ;</li>
 *   <li>l'<b>édition</b> la plus complète, parce qu'une édition de luxe contient l'édition simple
 *       et que l'inverse est faux ;</li>
 *   <li>le <b>poids</b>, à égalité de tout le reste ;</li>
 *   <li>le <b>chemin</b>, pour que deux exécutions rendent le même rapport.</li>
 * </ol>
 *
 * <h2>Ce que le classement ne prétend pas</h2>
 * Il désigne l'exemplaire qu'on garderait si l'on ne devait en garder qu'un. Sans
 * {@code --etiquettes}, aucun de ces critères n'a écouté une seule seconde de son : c'est
 * pourquoi le rapport écrit {@code garder ?} avec un point d'interrogation.
 */
public final class Qualite {

    /**
     * Éditions qui contiennent les autres.
     *
     * <p>Entre une édition de luxe et une édition simple, supprimer la première perd des titres
     * que rien ne rendra ; supprimer la seconde ne perd rien. C'est la seule asymétrie franche du
     * lot, et elle justifie de passer devant le poids.
     */
    private static final Set<String> EDITIONS_PLUS_COMPLETES = Set.of(
            "deluxe", "luxe", "expanded", "bonus", "collector", "integrale", "complete",
            "ultimate", "coffret", "boxset", "anniversary", "anniversaire");

    /**
     * Écart de pistes en deçà duquel deux exemplaires sont tenus pour aussi complets l'un que
     * l'autre.
     *
     * <p>Une piste d'écart ne dit rien : une piste cachée, un fichier de silence en fin de disque,
     * une reprise en prime suffisent à l'expliquer. C'est à partir de deux que l'on peut parler
     * d'un album amputé.
     */
    private static final int PISTES_D_ECART_TOLEREES = 1;

    private Qualite() {
        // Comparateur seul, pas d'instance.
    }

    /**
     * Ordre des exemplaires d'un groupe, le meilleur d'abord.
     *
     * @param etiquettes étiquettes lues dans les fichiers ; elles affinent le seul critère qu'un
     *                   nom ne peut pas établir, celui du débit réel
     * @param membres    tous les exemplaires du groupe, dont se déduit le nombre de pistes de
     *                   référence
     */
    public static Comparator<Album> meilleurDAbord(Etiquettes etiquettes, List<Album> membres) {
        int reference = membres.stream().mapToInt(Album::nombreDePistes).max().orElse(0);
        return meilleurDAbord(etiquettes, reference);
    }

    /**
     * Ordre des exemplaires, la complétude étant jugée par rapport à un nombre de pistes fixé.
     *
     * <p>Le seuil est arrêté une fois pour le groupe, et non recalculé à chaque comparaison : un
     * critère qui dirait « à une piste près, c'est pareil » sans référence commune ne serait pas
     * transitif, et le tri lui-même finirait par s'en plaindre.
     */
    public static Comparator<Album> meilleurDAbord(Etiquettes etiquettes, int pistesDeReference) {
        return Comparator.comparingInt((Album album) -> rangDeCompletude(album, pistesDeReference))
                .reversed()
                .thenComparing(Comparator.comparingInt(Album::rangDeFormat).reversed())
                .thenComparing(Comparator.comparingLong(
                        (Album album) -> debit(album, etiquettes)).reversed())
                .thenComparing(Comparator.comparingInt(Qualite::rangDEdition).reversed())
                .thenComparing(Comparator.comparingLong(Album::taille).reversed())
                .thenComparing(album -> album.dossier().toString());
    }

    /** Un pour un album aussi complet que le plus complet du groupe, zéro pour un album amputé. */
    private static int rangDeCompletude(Album album, int pistesDeReference) {
        return album.nombreDePistes() >= pistesDeReference - PISTES_D_ECART_TOLEREES ? 1 : 0;
    }

    /**
     * Débit de l'album, dans l'ordre d'autorité décroissante : mesuré, annoncé, ou deviné.
     *
     * <p>Le poids moyen d'une piste n'est un indice qu'<b>entre deux exemplaires du même album</b>,
     * où les morceaux ont les mêmes durées des deux côtés. C'est exactement l'usage qui en est
     * fait ici, et nulle part ailleurs.
     */
    private static long debit(Album album, Etiquettes etiquettes) {
        OptionalLong mesure = debitMesure(album, etiquettes);
        if (mesure.isPresent()) {
            return mesure.getAsLong();
        }
        return album.nom().debitAnnonce().isPresent()
                ? album.nom().debitAnnonce().getAsInt() * 1000L / 8
                : album.poidsMoyenDUnePiste();
    }

    /**
     * Débit réellement mesuré sur les pistes qu'on a sondées, en octets par seconde.
     *
     * <p>La moyenne est prise sur les seules pistes dont la durée a été lue : sonder la moitié
     * d'un album suffit à le juger, et attendre que toutes répondent reviendrait à ne rien juger
     * du tout dès qu'un fichier est illisible.
     */
    public static OptionalLong debitMesure(Album album, Etiquettes etiquettes) {
        long octets = 0;
        double secondes = 0;
        for (Piste piste : album.pistes()) {
            var duree = etiquettes.dureeDe(piste.chemin());
            if (duree.isPresent() && duree.getAsDouble() > 0) {
                octets += piste.taille();
                secondes += duree.getAsDouble();
            }
        }
        return secondes <= 0 ? OptionalLong.empty() : OptionalLong.of((long) (octets / secondes));
    }

    /** Un pour une édition qui contient les autres, zéro sinon. */
    private static int rangDEdition(Album album) {
        return album.nom().editions().stream().anyMatch(EDITIONS_PLUS_COMPLETES::contains) ? 1 : 0;
    }
}
