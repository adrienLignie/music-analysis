package fr.musique.nom;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Ce qu'on a su lire du chemin d'un dossier d'album.
 *
 * @param artiste            artiste tel que lu, accents et casse d'origine conservés
 * @param cleArtiste         artiste normalisé, chaîne vide quand aucun n'a été trouvé
 * @param origineDeLArtiste  d'où vient cet artiste ; un artiste déduit du dossier parent ne vaut
 *                           pas un artiste lu, et la confiance d'un rapprochement s'en ressent
 * @param titre              titre de l'album tel que lu
 * @param cle                titre normalisé, seule forme utilisée pour comparer
 * @param cleSansArticle     clé secondaire, article initial retiré
 * @param titresAlternatifs  titres trouvés entre parenthèses, souvent le titre original ou sa
 *                           traduction
 * @param annee              année de parution, quand le nom en porte une
 * @param volume             numéro de volume, quand le titre en porte un : {@code Greatest Hits
 *                           Vol. 2} n'est pas {@code Greatest Hits Vol. 1}
 * @param editions           mentions telles que {@code Deluxe} ou {@code Remastered} : une autre
 *                           édition du même album, qu'il faut signaler comme doublon
 * @param versions           mentions telles que {@code Live}, {@code Unplugged} ou {@code Remixes}
 *                           : une <b>autre œuvre</b>, qui ne doit jamais être confondue avec
 *                           l'album dont elle porte le titre
 * @param debitAnnonce       débit annoncé dans le nom du dossier, en kilobits par seconde
 * @param estCompilation     vrai quand le dossier annonce plusieurs interprètes
 */
public record NomDAlbum(
        Optional<String> artiste,
        String cleArtiste,
        OrigineDeLArtiste origineDeLArtiste,
        String titre,
        String cle,
        String cleSansArticle,
        List<String> titresAlternatifs,
        OptionalInt annee,
        OptionalInt volume,
        Set<String> editions,
        Set<String> versions,
        OptionalInt debitAnnonce,
        boolean estCompilation) {

    public NomDAlbum {
        titresAlternatifs = List.copyOf(titresAlternatifs);
        editions = Set.copyOf(editions);
        versions = Set.copyOf(versions);
    }

    /**
     * Clé principale : l'artiste et le titre réunis, ou le titre seul quand aucun artiste n'est
     * connu.
     *
     * <p>Réunir les deux est ce qui empêche les innombrables {@code Greatest Hits} de la
     * bibliothèque de former un seul groupe de doublons.
     */
    public String cleComplete() {
        return cleArtiste.isEmpty() ? cle : cleArtiste + " | " + cle;
    }

    /** Numéro de volume effectif : celui qui a été lu, ou 1 quand le titre n'en porte aucun. */
    public int volumeEffectif() {
        return volume.orElse(1);
    }

    /** Indique que le titre seul ne suffit pas à désigner un album. */
    public boolean aUnTitreGenerique() {
        return MotsTechniques.estUnTitreGenerique(cle);
    }
}
