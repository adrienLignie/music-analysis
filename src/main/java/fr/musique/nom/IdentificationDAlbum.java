package fr.musique.nom;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Détermine l'identité d'un album à partir de son <b>chemin</b> et de ce que ses pistes portent.
 *
 * <p>C'est le point le plus sous-estimé de l'exercice. Les bibliothèques réelles mélangent plus de
 * conventions que les vidéothèques :
 *
 * <pre>
 * Pink Floyd/The Dark Side Of The Moon (1973)/01 - Speak To Me.flac   l'artiste est le parent
 * Daft Punk - Discovery (2001)/01 - One More Time.mp3                 tout est dans le dossier
 * Musique/Best Of/01 - Pink Floyd - Money.mp3                         seules les pistes parlent
 * Muse/Black Holes And Revelations/CD1/01 - Take A Bow.flac           le dossier n'est qu'un disque
 * </pre>
 *
 * <p>Se fier au seul nom du dossier réunirait sous une même clé tous les {@code CD1} de la
 * bibliothèque, et tous les {@code Greatest Hits} qui n'ont pas d'artiste dans leur nom. L'artiste
 * est donc cherché dans le dossier de l'album, puis dans son parent, puis dans les pistes
 * elles-mêmes — et l'on retient d'où il vient, parce qu'un rapprochement ne vaut pas la même chose
 * selon la réponse.
 */
public final class IdentificationDAlbum {

    /**
     * Noms de dossiers qui ne désignent pas un album mais l'un de ses disques.
     *
     * <p>Un coffret de trois disques est <b>un</b> album qui pèse la somme de ses trois dossiers.
     * Les compter séparément diviserait son poids par trois au classement, et ferait passer ses
     * trois disques pour trois albums en double les uns des autres dès qu'un autre coffret voisin
     * porterait les mêmes noms de dossiers.
     */
    private static final Pattern DOSSIER_DE_DISQUE = Pattern.compile(
            "(?i)^\\s*(cd|disc|disque|disk|dvd|vol|volume|part|partie)\\s*[-_.]?\\s*"
                    + "(\\d{1,2}|[ivx]{1,4})\\s*$");

    /** Noms de dossiers qui ne désignent rien du tout : le titre de l'album est ailleurs. */
    private static final Set<String> DOSSIERS_SANS_TITRE = Set.of(
            "album", "albums", "musique", "music", "audio", "divers", "nouveau dossier",
            "new folder", "sans titre", "unknown", "inconnu", "telechargements", "downloads",
            "mp3", "flac", "itunes", "media");

    private final ParseurDeNomDAlbum parseur;

    public IdentificationDAlbum(ParseurDeNomDAlbum parseur) {
        this.parseur = parseur;
    }

    /** Indique que ce nom de dossier désigne un disque d'un album plutôt qu'un album entier. */
    public static boolean estUnDossierDeDisque(String nomDuDossier) {
        return nomDuDossier != null && DOSSIER_DE_DISQUE.matcher(nomDuDossier).matches();
    }

    /** Indique que ce nom de dossier ne porte aucun titre d'album. */
    public static boolean estUnDossierSansTitre(String nomDuDossier) {
        return nomDuDossier == null
                || DOSSIERS_SANS_TITRE.contains(
                        CleDeTitre.normaliser(nomDuDossier).toLowerCase(Locale.ROOT));
    }

    /**
     * Identifie un album.
     *
     * @param nomDuDossier       nom du dossier qui porte l'album, disques déjà réunis
     * @param nomDuDossierParent nom du dossier au-dessus, ou {@code null} quand l'album est
     *                           directement sous une racine analysée : ce qui se trouve au-dessus
     *                           d'une racine n'appartient pas à la bibliothèque et ne peut donc
     *                           pas être un artiste
     * @param pistes             pistes de l'album, dont les noms sont la dernière source d'artiste
     */
    public NomDAlbum identifier(
            String nomDuDossier, String nomDuDossierParent, List<NomDePiste> pistes) {
        NomDAlbum depuisLeChemin = parseur.analyser(nomDuDossier, nomDuDossierParent);
        if (!depuisLeChemin.cleArtiste().isEmpty() || depuisLeChemin.estCompilation()) {
            return depuisLeChemin;
        }
        return artisteCommunDesPistes(pistes)
                .map(artiste -> avecArtiste(depuisLeChemin, artiste))
                .orElse(depuisLeChemin);
    }

    /**
     * Artiste que toutes les pistes s'accordent à nommer.
     *
     * <p>Toutes, sans exception : un dossier dont les pistes portent deux artistes différents est
     * une compilation, et lui donner celui de la majorité le rapprocherait de la discographie de
     * cet artiste. Un seul désaccord suffit donc à renoncer.
     */
    private static Optional<String> artisteCommunDesPistes(List<NomDePiste> pistes) {
        if (pistes.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> commun = Optional.empty();
        for (NomDePiste piste : pistes) {
            if (piste.artiste().isEmpty()) {
                return Optional.empty();
            }
            String cle = CleDeTitre.normaliserArtiste(piste.artiste().get());
            if (cle.isEmpty()) {
                return Optional.empty();
            }
            if (commun.isEmpty()) {
                commun = piste.artiste();
            } else if (!CleDeTitre.normaliserArtiste(commun.get()).equals(cle)) {
                return Optional.empty();
            }
        }
        return commun;
    }

    private static NomDAlbum avecArtiste(NomDAlbum nom, String artiste) {
        String cle = CleDeTitre.normaliserArtiste(artiste);
        if (MotsTechniques.estArtistesMultiples(cle)) {
            return nom;
        }
        return new NomDAlbum(
                Optional.of(artiste),
                cle,
                OrigineDeLArtiste.PISTES,
                nom.titre(),
                nom.cle(),
                nom.cleSansArticle(),
                nom.titresAlternatifs(),
                nom.annee(),
                nom.volume(),
                nom.editions(),
                nom.versions(),
                nom.debitAnnonce(),
                nom.estCompilation());
    }
}
