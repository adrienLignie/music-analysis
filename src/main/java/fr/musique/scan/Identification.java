package fr.musique.scan;

import fr.musique.doublons.Album;
import fr.musique.doublons.Piste;
import fr.musique.nom.IdentificationDAlbum;
import fr.musique.nom.NomDePiste;
import fr.musique.nom.ParseurDeNomDePiste;
import fr.musique.nom.PrefixeDeCollection;
import fr.musique.suivi.Avancement;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compose les albums à partir de ce que le parcours a vu.
 *
 * <h2>Pourquoi cette étape est séparée du parcours</h2>
 * Elle ne touche jamais au disque : elle n'a devant elle que des chaînes de caractères, et tout ce
 * qu'elle fait est de les découper. Sur cent mille fichiers, cela représente un travail de calcul
 * mesurable, que le parcours ne devrait pas avoir à attendre — et que rien n'empêche de mener sur
 * plusieurs fils, puisque aucun album n'a besoin de connaître les autres.
 *
 * <h2>Le rattachement des disques, fait avant tout le reste</h2>
 * Un coffret de trois disques arrive du parcours sous la forme de trois dossiers frères,
 * {@code CD1}, {@code CD2}, {@code CD3}. Les laisser tels quels aurait trois conséquences, toutes
 * fausses : le coffret compterait pour trois albums, chacun pèserait un tiers de son poids au
 * classement, et les {@code CD1} de deux coffrets différents se retrouveraient doublons l'un de
 * l'autre. Ils sont donc réunis sous le dossier du dessus avant que le moindre nom soit lu.
 */
public final class Identification {

    /**
     * En deçà, le parallélisme coûte plus qu'il ne rapporte.
     *
     * <p>Découper le travail, le répartir et le rassembler n'est pas gratuit ; sur un dossier
     * d'essai ou une petite discothèque, le faire sur un seul fil est plus rapide, et surtout plus
     * facile à suivre quand quelque chose ne va pas.
     */
    private static final int ALBUMS_AVANT_PARALLELISME = 64;

    private final IdentificationDAlbum identification;
    private final ParseurDeNomDePiste parseurDePiste;
    private final Avancement avancement;

    /** Identification silencieuse. */
    public Identification(IdentificationDAlbum identification) {
        this(identification, new ParseurDeNomDePiste(), Avancement.muet());
    }

    public Identification(
            IdentificationDAlbum identification,
            ParseurDeNomDePiste parseurDePiste,
            Avancement avancement) {
        this.identification = identification;
        this.parseurDePiste = parseurDePiste;
        this.avancement = avancement;
    }

    /**
     * Donne son identité à chaque album.
     *
     * <p>L'ordre des albums rendus ne dépend pas de celui dans lequel les fils ont fini : ils sont
     * rassemblés dans l'ordre où le parcours a vu leurs dossiers. Deux exécutions sur la même
     * bibliothèque rendent donc le même inventaire, ce qui permet de comparer leurs rapports.
     */
    public Inventaire identifier(Arborescence arborescence) {
        avancement.etape("Lecture des noms");
        int total = arborescence.nombreDeFichiers();
        AtomicInteger faits = new AtomicInteger();
        List<Regroupement> regroupes = rattacherLesDisques(arborescence.dossiers());
        List<Album> albums = (regroupes.size() >= ALBUMS_AVANT_PARALLELISME
                ? regroupes.parallelStream()
                : regroupes.stream())
                .map(regroupement -> {
                    Album album = composer(regroupement, arborescence.repetitionsPhysiques());
                    avancement.pas(faits.addAndGet(album.nombreDePistes()), total);
                    return album;
                })
                .toList();
        return new Inventaire(
                albums,
                arborescence.racines(),
                arborescence.repetitionsPhysiques(),
                arborescence.fichiersIgnores(),
                arborescence.dossiersIllisibles());
    }

    /**
     * Un album en construction : son dossier, et tout ce qui lui revient.
     *
     * <p>Mutable, parce qu'un coffret se compose en plusieurs fois : les disques arrivent l'un
     * après l'autre et versent chacun ses fichiers et son poids annexe dans le même album.
     */
    private static final class Regroupement {

        private final Path dossier;
        private final String nomDuParent;
        private final List<FichierTrouve> fichiers;
        private long octetsHorsAudio;

        Regroupement(Path dossier, String nomDuParent, List<FichierTrouve> fichiers,
                long octetsHorsAudio) {
            this.dossier = dossier;
            this.nomDuParent = nomDuParent;
            this.fichiers = fichiers;
            this.octetsHorsAudio = octetsHorsAudio;
        }
    }

    /**
     * Réunit sous un même dossier les dossiers qui ne sont que des disques.
     *
     * <p>Un dossier n'est rattaché au dossier du dessus que si celui-ci n'est pas une racine
     * analysée : un {@code CD1} posé directement sous le point de départ de l'analyse ferait
     * autrement de ce point de départ un album, avec tout ce qu'il contient.
     *
     * <p>Le rattachement est fait en une passe, dans l'ordre du parcours : deux disques d'un même
     * coffret arrivent l'un après l'autre et se rejoignent naturellement sous leur parent commun.
     */
    private static List<Regroupement> rattacherLesDisques(List<DossierTrouve> dossiers) {
        Map<Path, Regroupement> parAlbum = new LinkedHashMap<>();
        for (DossierTrouve dossier : dossiers) {
            boolean estUnDisque = IdentificationDAlbum.estUnDossierDeDisque(dossier.nom())
                    && !dossier.parentEstUneRacine()
                    && dossier.chemin().getParent() != null;
            Path album = estUnDisque ? dossier.chemin().getParent() : dossier.chemin();
            String nomDuParent = estUnDisque ? nomDuGrandParent(dossier) : dossier.nomDuParent();
            Regroupement existant = parAlbum.get(album);
            if (existant == null) {
                parAlbum.put(album, new Regroupement(album, nomDuParent,
                        new ArrayList<>(dossier.fichiers()), dossier.octetsHorsAudio()));
            } else {
                existant.fichiers.addAll(dossier.fichiers());
                // Les pochettes des trois disques d'un coffret pèsent sur le coffret, pas sur le
                // premier disque venu.
                existant.octetsHorsAudio += dossier.octetsHorsAudio();
            }
        }
        return List.copyOf(parAlbum.values());
    }

    /** Nom du dossier deux niveaux au-dessus d'un disque : l'artiste, quand il y en a un. */
    private static String nomDuGrandParent(DossierTrouve disque) {
        Path album = disque.chemin().getParent();
        Path artiste = album == null ? null : album.getParent();
        Path nom = artiste == null ? null : artiste.getFileName();
        return nom == null ? null : nom.toString();
    }

    /**
     * Lit les noms d'un album et de toutes ses pistes.
     *
     * <p>Le patron commun aux fichiers du dossier est cherché une fois pour toutes les pistes
     * plutôt qu'une fois par piste : sur les quatre-vingt-dix fichiers d'un coffret, cela fait
     * quatre-vingt-dix détections rigoureusement identiques en moins.
     */
    private Album composer(Regroupement regroupement, Set<Path> repetitions) {
        List<String> noms = regroupement.fichiers.stream()
                .map(fichier -> fichier.chemin().getFileName().toString())
                .toList();
        PrefixeDeCollection prefixe = PrefixeDeCollection.detecter(noms);
        List<Piste> pistes = new ArrayList<>(regroupement.fichiers.size());
        for (FichierTrouve fichier : regroupement.fichiers) {
            NomDePiste nom = parseurDePiste.analyser(
                    fichier.chemin().getFileName().toString(), prefixe);
            pistes.add(new Piste(fichier.chemin(), fichier.taille(), nom, fichier.cleDuFichier()));
        }
        pistes.sort(ordreDesPistes());
        String nomDuDossier = nomDe(regroupement.dossier);
        return Album.de(
                regroupement.dossier,
                pistes,
                identification.identifier(
                        nomDuDossier,
                        regroupement.nomDuParent,
                        pistes.stream().map(Piste::nom).toList()),
                repetitions,
                regroupement.octetsHorsAudio);
    }

    /**
     * Ordre des pistes d'un album : par disque, puis par numéro, puis par chemin.
     *
     * <p>Les pistes sans numéro passent après celles qui en ont un, et le chemin tranche le reste.
     * Ce n'est pas de la coquetterie : c'est ce qui rend le rapport identique d'une exécution à
     * l'autre, l'ordre d'énumération d'un dossier n'étant garanti par aucun système.
     */
    private static Comparator<Piste> ordreDesPistes() {
        return Comparator.<Piste>comparingInt(piste -> piste.nom().disque().orElse(0))
                .thenComparingInt(piste -> piste.nom().numero().orElse(Integer.MAX_VALUE))
                .thenComparing(piste -> piste.chemin().toString());
    }

    private static String nomDe(Path dossier) {
        Path nom = dossier.getFileName();
        return nom == null ? dossier.toString() : nom.toString();
    }

    /** Numéro de disque d'une piste, tel que l'album le comprend. */
    static OptionalInt disqueDe(Piste piste) {
        return piste.nom().disque();
    }
}
