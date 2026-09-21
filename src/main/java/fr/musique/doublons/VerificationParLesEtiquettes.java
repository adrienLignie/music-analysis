package fr.musique.doublons;

import fr.musique.media.Etiquette;
import fr.musique.media.Etiquettes;
import fr.musique.nom.CleDeTitre;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Confronte un rapprochement à ce que les fichiers disent d'eux-mêmes.
 *
 * <h2>La seule preuve qui ne vienne pas d'un nom de dossier</h2>
 * Tout le reste du programme raisonne sur des conventions de rangement, qui varient d'une
 * bibliothèque à l'autre. Les étiquettes voyagent avec le fichier : deux dossiers rangés
 * autrement mais dont les fichiers portent le même artiste et le même album sont le même album,
 * et deux dossiers dont les étiquettes se contredisent méritent un regard avant toute
 * suppression.
 *
 * <p>La durée totale s'y ajoute : deux exemplaires du même album qui ne durent pas le même temps
 * ne portent pas le même contenu, même s'ils comptent le même nombre de fichiers — c'est la
 * signature d'un album amputé, ou de pistes coupées à l'encodage.
 *
 * <h2>Ce qui est fait de cette information</h2>
 * Le groupe n'est jamais <b>scindé</b>. Un album au rabais et un album complet restent un doublon
 * qu'il faut signaler — c'est même le cas où se tromper coûte le plus cher. Les étiquettes ne
 * servent donc pas à défaire le rapprochement mais à dire ce qu'il vaut, et à désigner
 * l'exemplaire à garder sur son débit réel plutôt que sur ce que son dossier annonce.
 */
public final class VerificationParLesEtiquettes {

    /**
     * Écart relatif de durée au-delà duquel deux dossiers ne portent pas le même contenu.
     *
     * <p>Assez large pour absorber ce qui ne change rien — une piste cachée, quelques secondes de
     * silence en fin de disque, un encodage qui arrondit —, assez étroit pour attraper un album
     * auquel il manque un morceau.
     */
    private static final double ECART_TOLERE = 0.10;

    private VerificationParLesEtiquettes() {
        // Vérification seule, pas d'instance.
    }

    /**
     * Révise ces groupes à la lumière des étiquettes lues.
     *
     * <p>Les exemplaires sont aussi remis dans l'ordre : le débit réel disqualifie les dossiers
     * qui annonçaient mieux qu'ils ne portent, et l'exemplaire à garder peut en changer.
     */
    public static List<GroupeDeDoublons> verifier(
            List<GroupeDeDoublons> groupes, Etiquettes etiquettes) {
        if (etiquettes.estVide()) {
            return groupes;
        }
        return groupes.stream()
                .map(groupe -> reviser(
                        groupe.avecOrdre(Qualite.meilleurDAbord(etiquettes, groupe.albums())),
                        etiquettes))
                .sorted(GroupeDeDoublons.DU_PLUS_GROS_GAIN)
                .toList();
    }

    /**
     * Révise un groupe, du témoignage le plus faible au plus fort.
     *
     * <p>Les étiquettes disent ce que les fichiers <b>prétendent</b> être, les durées ce qu'ils
     * <b>mesurent</b>, les empreintes ce qu'ils <b>sont</b>. L'ordre est donc celui-là, et le
     * dernier a le dernier mot : deux fichiers dont le son est démontré identique le restent quoi
     * que leurs étiquettes racontent — c'est même la signature du doublon le plus courant, celui
     * d'un fichier recopié puis réétiqueté.
     */
    private static GroupeDeDoublons reviser(GroupeDeDoublons groupe, Etiquettes etiquettes) {
        GroupeDeDoublons apresLesEtiquettes = confronterLesEtiquettes(groupe, etiquettes);
        GroupeDeDoublons apresLesDurees = confronterLesDurees(apresLesEtiquettes, etiquettes);
        return confronterLesEmpreintes(apresLesDurees, etiquettes);
    }

    /**
     * Confronte les sons eux-mêmes, là où les fichiers en portent une empreinte.
     *
     * <h2>Le seul verdict de ce programme qui ne suppose rien</h2>
     * Tout le reste — le rangement, les étiquettes, les durées — décrit le fichier de l'extérieur.
     * L'empreinte que le flac écrit dans son premier bloc décrit le signal lui-même : deux
     * dossiers dont toutes les pistes la partagent, dans le même ordre, ne se ressemblent pas, ils
     * portent le même son. Il n'y a plus rien à vérifier avant d'en supprimer un.
     *
     * <h2>Ce qu'un désaccord ne prouve pas</h2>
     * Rien, et c'est pourquoi il ne fait jamais descendre la confiance. Un flac et un mp3 du même
     * album n'ont aucune empreinte commune par construction, et deux extractions du même disque à
     * des décalages différents non plus. Quand les deux empreintes sont pourtant de la même sorte,
     * la remarque a sa place dans le motif — deux flac au son différent sont deux extractions
     * distinctes, ce qui explique souvent un écart de poids qui autrement surprendrait.
     *
     * <p>Un seul exemplaire muet suffit à renoncer : la démonstration porte sur le groupe entier,
     * ou elle ne porte sur rien.
     */
    private static GroupeDeDoublons confronterLesEmpreintes(
            GroupeDeDoublons groupe, Etiquettes etiquettes) {
        List<String> empreintes = new ArrayList<>();
        for (Album album : groupe.albums()) {
            Optional<String> empreinte = empreinteDuDossier(album, etiquettes);
            if (empreinte.isEmpty()) {
                return groupe;
            }
            empreintes.add(empreinte.get());
        }
        // Passé la boucle, il y a autant d'empreintes que d'exemplaires, et un groupe en compte
        // toujours au moins deux : il n'y a pas de cas à écarter ici, contrairement aux étiquettes
        // dont un dossier peut simplement ne rien dire.
        if (empreintes.stream().distinct().count() == 1) {
            return groupe.avecVerdict(
                    NiveauDeConfiance.CERTAINE,
                    groupe.motif() + ", même son : empreintes identiques");
        }
        if (empreintes.stream().map(VerificationParLesEtiquettes::sorteDe).distinct().count() > 1) {
            // Deux sortes d'empreintes ne se comparent pas : leur désaccord était acquis d'avance
            // et ne renseigne sur rien.
            return groupe;
        }
        return groupe.avecVerdict(
                groupe.confiance(),
                groupe.motif() + ", sons différents : deux encodages distincts du même album");
    }

    /**
     * Empreinte du son d'un dossier : celles de ses pistes, dans l'ordre, ou rien.
     *
     * <p>Toutes les pistes, sans exception. Une seule qui manque et la comparaison porterait sur
     * des dossiers dont on n'aurait pas lu la même chose, ce qui les déclarerait différents pour
     * une raison qui n'a rien à voir avec leur son.
     *
     * <p>L'ordre est celui dont l'album a rangé ses pistes — disque, numéro, chemin —, le même des
     * deux côtés. Deux dossiers qui ne comptent pas le même nombre de pistes rendent donc deux
     * empreintes différentes, ce qui est exact : il leur manque du son.
     */
    public static Optional<String> empreinteDuDossier(Album album, Etiquettes etiquettes) {
        StringBuilder empreinte = new StringBuilder();
        for (Piste piste : album.pistes()) {
            Optional<String> dUnePiste = etiquettes.empreinteDe(piste.chemin());
            if (dUnePiste.isEmpty()) {
                return Optional.empty();
            }
            empreinte.append(dUnePiste.get()).append('\n');
        }
        return empreinte.length() == 0 ? Optional.empty() : Optional.of(empreinte.toString());
    }

    /** Sorte d'une empreinte de dossier : celle que porte sa première piste. */
    private static String sorteDe(String empreinte) {
        int deuxPoints = empreinte.indexOf(':');
        return deuxPoints < 0 ? empreinte : empreinte.substring(0, deuxPoints);
    }

    /**
     * Compare ce que les fichiers de chaque dossier disent de leur artiste et de leur album.
     *
     * <p>Une seule identité connue ne compare rien : mieux vaut ne rien dire que de laisser croire
     * que le rapprochement a été vérifié.
     */
    private static GroupeDeDoublons confronterLesEtiquettes(
            GroupeDeDoublons groupe, Etiquettes etiquettes) {
        List<String> identites = new ArrayList<>();
        for (Album album : groupe.albums()) {
            identiteDe(album, etiquettes).ifPresent(identites::add);
        }
        if (identites.size() < 2) {
            return groupe;
        }
        long distinctes = identites.stream().distinct().count();
        if (distinctes > 1) {
            return groupe.avecVerdict(
                    unCranPlusBas(groupe.confiance()),
                    groupe.motif() + ", étiquettes différentes : "
                            + String.join(" / ", identites.stream().distinct().toList()));
        }
        if (identites.size() == groupe.albums().size()
                && groupe.confiance() == NiveauDeConfiance.MOYENNE) {
            return groupe.avecVerdict(
                    NiveauDeConfiance.FORTE,
                    groupe.motif() + ", étiquettes concordantes : " + identites.get(0));
        }
        return groupe.avecVerdict(
                groupe.confiance(),
                groupe.motif() + ", étiquettes concordantes : " + identites.get(0));
    }

    /**
     * Identité que les fichiers d'un dossier s'accordent à donner.
     *
     * <p>Toutes les pistes lues doivent dire la même chose : un dossier dont les étiquettes se
     * contredisent en interne est une compilation mal rangée, et sa première piste ne vaut pas
     * pour les autres.
     */
    private static Optional<String> identiteDe(Album album, Etiquettes etiquettes) {
        String commune = null;
        for (Piste piste : album.pistes()) {
            Etiquette etiquette = etiquettes.de(piste.chemin());
            if (etiquette.artiste().isEmpty() || etiquette.album().isEmpty()) {
                continue;
            }
            String identite = CleDeTitre.normaliserArtiste(etiquette.artiste().get())
                    + " — « " + CleDeTitre.normaliser(etiquette.album().get()) + " »";
            if (commune == null) {
                commune = identite;
            } else if (!commune.equals(identite)) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(commune);
    }

    /** Compare les durées totales des dossiers, quand au moins deux ont pu être mesurées. */
    private static GroupeDeDoublons confronterLesDurees(
            GroupeDeDoublons groupe, Etiquettes etiquettes) {
        List<Double> durees = new ArrayList<>();
        for (Album album : groupe.albums()) {
            OptionalDouble duree = dureeTotale(album, etiquettes);
            if (duree.isPresent()) {
                durees.add(duree.getAsDouble());
            }
        }
        if (durees.size() < 2) {
            return groupe;
        }
        double plusCourte = durees.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        double plusLongue = durees.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        if ((plusLongue - plusCourte) / plusLongue <= ECART_TOLERE) {
            return groupe;
        }
        return groupe.avecVerdict(
                unCranPlusBas(groupe.confiance()),
                groupe.motif() + ", durées différentes : " + Etiquettes.enTexte(plusCourte)
                        + " / " + Etiquettes.enTexte(plusLongue));
    }

    /**
     * Durée totale d'un dossier, vide tant qu'on n'a pas lu la durée de toutes ses pistes.
     *
     * <p>Toutes, sans exception : additionner les durées connues d'un album à moitié sondé
     * donnerait un album deux fois plus court qu'il n'est, et le rapport annoncerait une
     * différence là où il n'y en a pas.
     */
    private static OptionalDouble dureeTotale(Album album, Etiquettes etiquettes) {
        double total = 0;
        for (Piste piste : album.pistes()) {
            OptionalDouble duree = etiquettes.dureeDe(piste.chemin());
            if (duree.isEmpty()) {
                return OptionalDouble.empty();
            }
            total += duree.getAsDouble();
        }
        return total > 0 ? OptionalDouble.of(total) : OptionalDouble.empty();
    }

    private static NiveauDeConfiance unCranPlusBas(NiveauDeConfiance confiance) {
        return switch (confiance) {
            case CERTAINE -> NiveauDeConfiance.FORTE;
            case FORTE -> NiveauDeConfiance.MOYENNE;
            case MOYENNE, A_VERIFIER -> NiveauDeConfiance.A_VERIFIER;
        };
    }
}
