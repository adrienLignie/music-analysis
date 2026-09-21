package fr.musique.rapport;

import fr.musique.doublons.Album;
import fr.musique.doublons.GroupeDeDoublons;
import fr.musique.doublons.GroupeDePistes;
import fr.musique.doublons.Piste;
import fr.musique.doublons.Qualite;
import fr.musique.doublons.VerificationParLesEtiquettes;
import fr.musique.media.Etiquettes;
import fr.musique.scan.Inventaire;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Le même résultat, sous une forme qu'un script peut reprendre.
 *
 * <p>Le programme n'efface rien et n'effacera rien. Mais refuser d'écrire ne doit pas obliger à
 * relire cinq cents lignes de console à la main : le JSON permet à qui le veut de filtrer les
 * groupes qu'il juge sûrs et de bâtir sa propre commande. La décision et le geste lui
 * appartiennent alors entièrement, ce qui n'aurait pas été le cas d'une option
 * {@code --supprimer}.
 *
 * <p>Les poids sont en octets, les durées en secondes et les débits en octets par seconde, jamais
 * mis en forme : c'est la sortie lisible qui arrondit, pas celle-ci.
 *
 * <p>Le JSON est écrit à la main, sans bibliothèque. Le document n'a que quatre formes de valeurs
 * et l'échappement tient en dix lignes : une dépendance de plus se paierait à chaque montée de
 * version pour un service que le programme se rend seul.
 */
public final class RapportJson implements Rapport {

    /** Version du format, à incrémenter dès qu'un champ change de sens ou disparaît. */
    private static final int VERSION = 2;

    private final PrintStream sortie;

    public RapportJson(PrintStream sortie) {
        this.sortie = sortie;
    }

    @Override
    public void ecrire(Analyse analyse, int top) {
        Inventaire inventaire = analyse.inventaire();
        List<Album> distincts = inventaire.albumsDistincts();
        Etiquettes etiquettes = inventaire.etiquettes();
        sortie.println("{");
        sortie.printf("  \"version\": %d,%n", VERSION);
        sortie.printf("  \"inventaire\": %s,%n", inventaire(inventaire));
        sortie.printf("  \"doublons\": %s,%n",
                doublons(analyse.doublons(), etiquettes, analyse.groupesEcartes()));
        sortie.printf("  \"morceauxEnDouble\": %s,%n", pistesEnDouble(analyse.pistesEnDouble()));
        sortie.printf("  \"plusGrosAlbums\": %s,%n", plusGrosAlbums(distincts, top, etiquettes));
        sortie.printf("  \"albumsIncomplets\": %s,%n", incomplets(distincts, etiquettes));
        sortie.printf("  \"pistesSuspectes\": %s,%n", suspectes(distincts, etiquettes));
        sortie.printf("  \"dossiersMalRanges\": %s%n", rangements(inventaire));
        sortie.println("}");
        sortie.flush();
    }

    private static String inventaire(Inventaire inventaire) {
        return objet(
                nombre("albums", inventaire.albumsDistincts().size()),
                nombre("pistes", inventaire.nombreDePistes()),
                nombre("octets", inventaire.tailleTotale()),
                nombre("octetsHorsAudio", inventaire.octetsHorsAudio()),
                nombre("fichiersIgnores", inventaire.fichiersIgnores()),
                nombre("dossiersIllisibles", inventaire.dossiersIllisibles()),
                nombre("liensDurs", inventaire.repetitionsPhysiques().size()),
                nombre("fichiersSondes", inventaire.etiquettes().taille()));
    }

    private static String doublons(
            List<GroupeDeDoublons> groupes, Etiquettes etiquettes, int groupesEcartes) {
        long recuperable = groupes.stream().mapToLong(GroupeDeDoublons::placeRecuperable).sum();
        return objet(
                nombre("groupes", groupes.size()),
                nombre("octetsRecuperables", recuperable),
                nombre("groupesEcartesMemesFichiers", groupesEcartes),
                brut("liste", tableau(
                        groupes.stream().map(groupe -> groupe(groupe, etiquettes)).toList())));
    }

    private static String groupe(GroupeDeDoublons groupe, Etiquettes etiquettes) {
        List<Album> albums = groupe.albums();
        List<String> exemplaires = new ArrayList<>();
        for (int rang = 0; rang < albums.size(); rang++) {
            exemplaires.add(exemplaire(groupe, albums.get(rang), rang == 0, etiquettes));
        }
        return objet(
                texte("confiance", groupe.confiance().name()),
                texte("motif", groupe.motif()),
                nombre("octetsRecuperables", groupe.placeRecuperable()),
                brut("exemplaires", tableau(exemplaires)));
    }

    private static String exemplaire(
            GroupeDeDoublons groupe, Album album, boolean aGarder, Etiquettes etiquettes) {
        return objet(
                texte("dossier", album.dossier().toString()),
                nombre("octets", album.taille()),
                nombre("octetsHorsAudio", album.octetsHorsAudio()),
                nombre("pistes", album.nombreDePistes()),
                booleen("garder", aGarder),
                booleen("memesFichiersQuUnAutre", groupe.estUnLienDur(album)),
                memeSon(groupe, album, etiquettes),
                texte("artiste", album.nom().artiste().orElse(null)),
                texte("album", album.nom().cle()),
                annee(album.nom().annee()),
                texte("format", album.formatDominant()),
                debit(Qualite.debitMesure(album, etiquettes)));
    }

    /**
     * Dit si cet exemplaire porte le même son que celui qu'on propose de garder.
     *
     * <p>C'est le seul champ du document qui affirme quelque chose du <b>contenu</b> : il vaut
     * {@code true} quand les fichiers eux-mêmes en portent la preuve, et un script peut alors
     * supprimer sans rien vérifier. {@code false} veut dire « les deux portent une empreinte, et
     * elles diffèrent » ; {@code null}, « on ne le sait pas » — faute d'avoir demandé
     * {@code --etiquettes}, ou parce que le format n'en porte aucune. Un script qui prendrait
     * {@code null} pour {@code false} se priverait de rien ; l'inverse détruirait.
     */
    private static String memeSon(GroupeDeDoublons groupe, Album album, Etiquettes etiquettes) {
        Optional<String> sienne =
                VerificationParLesEtiquettes.empreinteDuDossier(album, etiquettes);
        Optional<String> aGarder = VerificationParLesEtiquettes.empreinteDuDossier(
                groupe.celuiAGarder(), etiquettes);
        if (sienne.isEmpty() || aGarder.isEmpty()) {
            return texte("memeSonQueLExemplaireAGarder", null);
        }
        return booleen("memeSonQueLExemplaireAGarder", sienne.equals(aGarder));
    }

    private static String pistesEnDouble(List<GroupeDePistes> groupes) {
        return tableau(groupes.stream()
                .map(groupe -> objet(
                        texte("motif", groupe.motif()),
                        nombre("octetsRecuperables", groupe.placeRecuperable()),
                        brut("exemplaires", tableau(fichiersDuGroupe(groupe)))))
                .toList());
    }

    private static List<String> fichiersDuGroupe(GroupeDePistes groupe) {
        List<String> fichiers = new ArrayList<>();
        List<Piste> pistes = groupe.pistes();
        for (int rang = 0; rang < pistes.size(); rang++) {
            fichiers.add(objet(
                    texte("chemin", pistes.get(rang).chemin().toString()),
                    nombre("octets", pistes.get(rang).taille()),
                    booleen("garder", rang == 0)));
        }
        return fichiers;
    }

    private static String plusGrosAlbums(List<Album> albums, int top, Etiquettes etiquettes) {
        return tableau(albums.stream()
                .sorted(Comparator.comparingLong(Album::taille).reversed()
                        .thenComparing(album -> album.dossier().toString()))
                .limit(top)
                .map(album -> objet(
                        texte("dossier", album.dossier().toString()),
                        nombre("octets", album.taille()),
                        nombre("pistes", album.nombreDePistes()),
                        nombre("octetsHorsAudio", album.octetsHorsAudio()),
                        texte("format", album.formatDominant()),
                        texte("artiste", album.nom().artiste().orElse(null)),
                        texte("album", album.nom().cle()),
                        debit(Qualite.debitMesure(album, etiquettes))))
                .toList());
    }

    private static String incomplets(List<Album> albums, Etiquettes etiquettes) {
        return tableau(AlbumsIncomplets.chercher(albums, etiquettes).stream()
                .map(incomplet -> objet(
                        texte("dossier", incomplet.album().dossier().toString()),
                        nombre("pistes", incomplet.album().nombreDePistes()),
                        nombre("pistesAttendues", incomplet.attendues()),
                        brut("numerosManquants", tableau(incomplet.manquants().stream()
                                .map(String::valueOf)
                                .toList()))))
                .toList());
    }

    private static String suspectes(List<Album> albums, Etiquettes etiquettes) {
        return tableau(PistesSuspectes.chercher(albums, etiquettes).stream()
                .map(suspecte -> objet(
                        texte("chemin", suspecte.piste().chemin().toString()),
                        nombre("octets", suspecte.piste().taille()),
                        texte("nature", suspecte.nature().name()),
                        nombre("constate", suspecte.constate()),
                        nombre("attendu", suspecte.attendu()),
                        texte("format", suspecte.piste().nom().extension())))
                .toList());
    }

    private static String rangements(Inventaire inventaire) {
        return tableau(Rangements.chercher(inventaire).stream()
                .map(anomalie -> objet(
                        texte("dossier", anomalie.album().dossier().toString()),
                        nombre("octets", anomalie.album().taille()),
                        nombre("pistes", anomalie.album().nombreDePistes()),
                        texte("nature", anomalie.nature().name()),
                        texte("constat", anomalie.nature().libelle())))
                .toList());
    }

    /**
     * Débit mesuré, en octets par seconde, ou {@code null} quand on n'est pas allé le mesurer.
     *
     * <p>Un débit absent ne veut pas dire « débit nul » mais « étiquettes non demandées, ou non
     * lisibles ». Aucun script ne doit conclure de son absence.
     */
    private static String debit(OptionalLong octetsParSeconde) {
        return octetsParSeconde.isPresent()
                ? nombre("octetsParSeconde", octetsParSeconde.getAsLong())
                : texte("octetsParSeconde", null);
    }

    private static String objet(String... champs) {
        return "{" + String.join(", ", champs) + "}";
    }

    private static String tableau(List<String> valeurs) {
        return "[" + String.join(", ", valeurs) + "]";
    }

    /** Champ de texte. Une valeur absente devient {@code null}, jamais une chaîne vide. */
    private static String texte(String nom, String valeur) {
        return citer(nom) + ": " + (valeur == null ? "null" : citer(valeur));
    }

    private static String nombre(String nom, long valeur) {
        return citer(nom) + ": " + valeur;
    }

    private static String booleen(String nom, boolean valeur) {
        return citer(nom) + ": " + valeur;
    }

    /** Champ dont la valeur est du JSON déjà formé : objet ou tableau. */
    private static String brut(String nom, String jsonDejaForme) {
        return citer(nom) + ": " + jsonDejaForme;
    }

    private static String annee(OptionalInt annee) {
        return annee.isPresent() ? nombre("annee", annee.getAsInt()) : texte("annee", null);
    }

    /**
     * Met une chaîne entre guillemets, échappée.
     *
     * <p>Les chemins Windows sont pleins de contre-obliques, que le JSON double ; les noms de
     * fichiers peuvent porter guillemets et tabulations. Tout ce qui est en deçà de l'espace est
     * échappé par son code, ce qui couvre les caractères de contrôle qu'un système de fichiers
     * tolère parfois.
     */
    private static String citer(String valeur) {
        StringBuilder resultat = new StringBuilder(valeur.length() + 2).append('"');
        for (int i = 0; i < valeur.length(); i++) {
            char caractere = valeur.charAt(i);
            switch (caractere) {
                case '"' -> resultat.append("\\\"");
                case '\\' -> resultat.append("\\\\");
                case '\n' -> resultat.append("\\n");
                case '\r' -> resultat.append("\\r");
                case '\t' -> resultat.append("\\t");
                case '\b' -> resultat.append("\\b");
                case '\f' -> resultat.append("\\f");
                default -> {
                    if (caractere < 0x20) {
                        resultat.append(String.format("\\u%04x", (int) caractere));
                    } else {
                        resultat.append(caractere);
                    }
                }
            }
        }
        return resultat.append('"').toString();
    }
}
