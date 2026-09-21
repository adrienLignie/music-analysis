package fr.musique.doublons;

import fr.musique.nom.CleDeTitre;
import fr.musique.nom.Formats;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Cherche les fichiers qui portent le même morceau, d'un dossier à l'autre.
 *
 * <h2>Pourquoi cette recherche est à part, et facultative</h2>
 * Un titre en double n'est pas un album en double. Le même morceau du même artiste figure
 * légitimement sur l'album d'origine, sur une compilation et sur un best of : les trois
 * exemplaires sont voulus, et les présenter comme des doublons donnerait une liste que personne ne
 * peut relire. La recherche ne se fait donc que si on la demande, et son résultat est présenté
 * pour ce qu'il est — une piste à suivre, pas un verdict.
 *
 * <h2>Ce qui la rend malgré tout utile</h2>
 * Elle attrape ce qu'aucune autre section ne voit : le dossier {@code Téléchargements} qui répète
 * morceau par morceau une discothèque déjà rangée, sans qu'aucun de ses dossiers ne ressemble à un
 * album.
 *
 * <p>Trois précautions la rendent lisible. Le rapprochement exige un <b>artiste</b> et un
 * <b>titre</b> connus des deux côtés — un fichier dont il ne reste qu'un numéro une fois le patron
 * du dossier retiré n'est comparé à rien. Les fichiers d'un même album ne sont jamais rapprochés
 * entre eux, disques multiples compris : la piste 1 du second disque d'un coffret n'est pas un
 * doublon de la piste 1 du premier. Et les dossiers déjà signalés comme albums en double sont
 * laissés de côté : leur cas est traité, le répéter morceau par morceau noierait le rapport.
 */
public final class ChercheurDePistes {

    /** Au-delà, ce n'est plus un doublon mais un morceau qui circule dans toute la discothèque. */
    private static final int EXEMPLAIRES_AU_PLUS = 6;

    /** En deçà, ce qui reste d'un nom de fichier ne désigne plus un morceau. */
    private static final int LETTRES_MINIMALES_D_UN_TITRE = 3;

    private ChercheurDePistes() {
        // Recherche seule, pas d'instance.
    }

    /**
     * Cherche les morceaux présents plusieurs fois.
     *
     * @param albums       tous les albums retenus
     * @param dejaSignales dossiers dont les albums figurent déjà parmi les doublons d'albums
     */
    public static List<GroupeDePistes> chercher(List<Album> albums, Set<Path> dejaSignales) {
        Map<String, List<Exemplaire>> parCle = new TreeMap<>();
        for (Album album : albums) {
            if (dejaSignales.contains(album.dossier())) {
                continue;
            }
            for (Piste piste : album.pistes()) {
                String artiste = artisteDe(album, piste);
                if (artiste.isEmpty() || !estUnTitreParlant(piste.nom().cle())) {
                    continue;
                }
                parCle.computeIfAbsent(
                                artiste + " | " + piste.nom().cle(),
                                inutilise -> new ArrayList<>())
                        .add(new Exemplaire(album.dossier(), piste, artiste));
            }
        }
        List<GroupeDePistes> groupes = new ArrayList<>();
        for (List<Exemplaire> exemplaires : parCle.values()) {
            composer(exemplaires).ifPresent(groupes::add);
        }
        groupes.sort(GroupeDePistes.DU_PLUS_GROS_GAIN);
        return List.copyOf(groupes);
    }

    /** Une piste, avec l'album d'où elle vient et l'artiste sous lequel elle a été indexée. */
    private record Exemplaire(Path album, Piste piste, String artiste) {}

    /**
     * Indique que ce qui reste du nom du fichier désigne encore un morceau.
     *
     * <p>Une fois retiré le patron commun au dossier, il ne reste parfois qu'un numéro :
     * {@code 01 - Titre 01.mp3} au milieu de ses onze frères donne {@code 01}. Indexer cela
     * rapprocherait la première piste de chaque album du même artiste, ce qui est le contraire de
     * ce qu'on cherche.
     */
    private static boolean estUnTitreParlant(String cle) {
        return cle.length() >= LETTRES_MINIMALES_D_UN_TITRE
                && cle.chars().anyMatch(Character::isLetter);
    }

    /**
     * Artiste d'une piste : celui que son nom porte, ou à défaut celui de son album.
     *
     * <p>Celui du fichier d'abord : sur une compilation, chaque morceau a le sien, et l'album n'en
     * a pas. C'est précisément là que le rapprochement a le plus de valeur.
     */
    private static String artisteDe(Album album, Piste piste) {
        if (piste.nom().artiste().isPresent()) {
            return CleDeTitre.normaliserArtiste(piste.nom().artiste().get());
        }
        return album.nom().cleArtiste();
    }

    /**
     * Compose un groupe, ou renonce quand ce qu'on a trouvé n'apprend rien.
     *
     * <p>Un seul exemplaire par album est retenu : deux fichiers du même album qui portent le même
     * titre sont une reprise en fin de disque ou deux pistes homonymes de deux galettes d'un
     * coffret, jamais une place à récupérer ailleurs. Et un morceau présent dans sept albums est
     * un morceau qui circule, pas un gaspillage.
     */
    private static Optional<GroupeDePistes> composer(List<Exemplaire> exemplaires) {
        List<Exemplaire> unParAlbum = new ArrayList<>(exemplaires.stream()
                .collect(Collectors.toMap(
                        Exemplaire::album,
                        exemplaire -> exemplaire,
                        (premier, suivant) -> premier,
                        TreeMap::new))
                .values());
        if (unParAlbum.size() < 2 || unParAlbum.size() > EXEMPLAIRES_AU_PLUS) {
            return Optional.empty();
        }
        List<Piste> retenues = unParAlbum.stream()
                .map(Exemplaire::piste)
                .sorted(meilleureDAbord())
                .toList();
        String motif = unParAlbum.get(0).artiste()
                + " — « " + retenues.get(0).nom().cle() + " », " + retenues.size()
                + " exemplaires";
        return Optional.of(new GroupeDePistes(retenues, motif));
    }

    /**
     * Ordre des exemplaires d'un morceau : le meilleur format, puis le plus lourd.
     *
     * <p>Le poids ne départage ici que deux fichiers de même format, où il n'est rien d'autre
     * qu'un débit : deux encodages du même morceau durent le même temps.
     */
    private static Comparator<Piste> meilleureDAbord() {
        return Comparator.comparingInt((Piste piste) -> Formats.rang(piste.nom().extension()))
                .reversed()
                .thenComparing(Comparator.comparingLong(Piste::taille).reversed())
                .thenComparing(piste -> piste.chemin().toString());
    }
}
