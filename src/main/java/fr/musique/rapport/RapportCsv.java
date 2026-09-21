package fr.musique.rapport;

import fr.musique.doublons.Album;
import fr.musique.doublons.GroupeDeDoublons;
import fr.musique.doublons.GroupeDePistes;
import fr.musique.doublons.Piste;
import fr.musique.media.Etiquettes;
import fr.musique.scan.Inventaire;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;

/**
 * Le même résultat, une ligne par dossier ou par fichier, pour un tableur ou un {@code awk}.
 *
 * <p>Toutes les sections du rapport tiennent dans une seule table, distinguées par la colonne
 * {@code section}. Deux tables auraient obligé à deux fichiers ou à deux exécutions, alors que ce
 * qu'on veut en sortir tient presque toujours en un filtre sur cette colonne.
 *
 * <p>La colonne {@code chemin} porte un <b>dossier</b> pour les albums et un <b>fichier</b> pour
 * les morceaux et les pistes suspectes. C'est la seule concession de cette table à la structure de
 * la bibliothèque, et la colonne {@code pistes} permet de la lever : elle est vide pour un
 * fichier.
 *
 * <p>Séparateur virgule et guillemets doublés, comme le veut la RFC 4180 : c'est ce que lisent les
 * outils en ligne de commande. Un tableur français demandera d'indiquer la virgule à
 * l'importation.
 */
public final class RapportCsv implements Rapport {

    private static final String EN_TETE = String.join(",",
            "section", "groupe", "confiance", "garder", "memes_fichiers", "octets",
            "octets_hors_audio", "pistes", "octets_recuperables", "format", "artiste", "album",
            "annee", "constate", "attendu", "motif", "chemin");

    private final PrintStream sortie;

    public RapportCsv(PrintStream sortie) {
        this.sortie = sortie;
    }

    @Override
    public void ecrire(Analyse analyse, int top) {
        Inventaire inventaire = analyse.inventaire();
        Etiquettes etiquettes = inventaire.etiquettes();
        sortie.println(EN_TETE);
        ecrireLesDoublons(analyse.doublons());
        ecrireLesPistesEnDouble(analyse.pistesEnDouble());
        ecrireLeClassement(inventaire.albumsDistincts(), top);
        ecrireLesIncomplets(inventaire.albumsDistincts(), etiquettes);
        ecrireLesSuspectes(inventaire.albumsDistincts(), etiquettes);
        ecrireLesRangements(inventaire);
        sortie.flush();
    }

    private void ecrireLesDoublons(List<GroupeDeDoublons> groupes) {
        for (int numero = 1; numero <= groupes.size(); numero++) {
            GroupeDeDoublons groupe = groupes.get(numero - 1);
            List<Album> albums = groupe.albums();
            for (int rang = 0; rang < albums.size(); rang++) {
                Album album = albums.get(rang);
                ligne("doublon",
                        String.valueOf(numero),
                        groupe.confiance().name(),
                        String.valueOf(rang == 0),
                        String.valueOf(groupe.estUnLienDur(album)),
                        String.valueOf(album.taille()),
                        String.valueOf(album.octetsHorsAudio()),
                        String.valueOf(album.nombreDePistes()),
                        String.valueOf(groupe.placeRecuperable()),
                        album.formatDominant(),
                        album.nom().artiste().orElse(""),
                        album.nom().cle(),
                        annee(album),
                        "",
                        "",
                        groupe.motif(),
                        album.dossier().toString());
            }
        }
    }

    private void ecrireLesPistesEnDouble(List<GroupeDePistes> groupes) {
        for (int numero = 1; numero <= groupes.size(); numero++) {
            GroupeDePistes groupe = groupes.get(numero - 1);
            List<Piste> pistes = groupe.pistes();
            for (int rang = 0; rang < pistes.size(); rang++) {
                Piste piste = pistes.get(rang);
                ligne("morceau",
                        String.valueOf(numero),
                        "",
                        String.valueOf(rang == 0),
                        "",
                        String.valueOf(piste.taille()),
                        "",
                        "",
                        String.valueOf(groupe.placeRecuperable()),
                        piste.nom().extension(),
                        piste.nom().artiste().orElse(""),
                        "",
                        "",
                        "",
                        "",
                        groupe.motif(),
                        piste.chemin().toString());
            }
        }
    }

    private void ecrireLeClassement(List<Album> albums, int top) {
        albums.stream()
                .sorted(Comparator.comparingLong(Album::taille).reversed()
                        .thenComparing(album -> album.dossier().toString()))
                .limit(top)
                .forEach(album -> ligne("top", "", "", "", "",
                        String.valueOf(album.taille()),
                        String.valueOf(album.octetsHorsAudio()),
                        String.valueOf(album.nombreDePistes()),
                        "",
                        album.formatDominant(),
                        album.nom().artiste().orElse(""),
                        album.nom().cle(),
                        annee(album),
                        "", "", "",
                        album.dossier().toString()));
    }

    private void ecrireLesIncomplets(List<Album> albums, Etiquettes etiquettes) {
        for (AlbumsIncomplets.Incomplet incomplet : AlbumsIncomplets.chercher(albums, etiquettes)) {
            ligne("incomplet", "", "", "", "",
                    String.valueOf(incomplet.album().taille()),
                    String.valueOf(incomplet.album().octetsHorsAudio()),
                    String.valueOf(incomplet.album().nombreDePistes()),
                    "",
                    incomplet.album().formatDominant(),
                    incomplet.album().nom().artiste().orElse(""),
                    incomplet.album().nom().cle(),
                    annee(incomplet.album()),
                    String.valueOf(incomplet.album().nombreDePistes()),
                    String.valueOf(incomplet.attendues()),
                    "numéros manquants : " + incomplet.manquants(),
                    incomplet.album().dossier().toString());
        }
    }

    private void ecrireLesSuspectes(List<Album> albums, Etiquettes etiquettes) {
        for (PistesSuspectes.Suspecte suspecte : PistesSuspectes.chercher(albums, etiquettes)) {
            ligne("suspecte", "", "", "", "",
                    String.valueOf(suspecte.piste().taille()),
                    "", "", "",
                    suspecte.piste().nom().extension(),
                    suspecte.piste().nom().artiste().orElse(""),
                    "", "",
                    String.valueOf(suspecte.constate()),
                    String.valueOf(suspecte.attendu()),
                    suspecte.nature().name(),
                    suspecte.piste().chemin().toString());
        }
    }

    private void ecrireLesRangements(Inventaire inventaire) {
        for (Rangements.Anomalie anomalie : Rangements.chercher(inventaire)) {
            ligne("rangement", "", "", "", "",
                    String.valueOf(anomalie.album().taille()),
                    String.valueOf(anomalie.album().octetsHorsAudio()),
                    String.valueOf(anomalie.album().nombreDePistes()),
                    "",
                    anomalie.album().formatDominant(),
                    anomalie.album().nom().artiste().orElse(""),
                    anomalie.album().nom().cle(),
                    annee(anomalie.album()),
                    "", "",
                    anomalie.nature().name() + " : " + anomalie.nature().libelle(),
                    anomalie.album().dossier().toString());
        }
    }

    private static String annee(Album album) {
        return album.nom().annee().isPresent()
                ? String.valueOf(album.nom().annee().getAsInt())
                : "";
    }

    private void ligne(String... colonnes) {
        StringBuilder ligne = new StringBuilder();
        for (String colonne : colonnes) {
            if (ligne.length() > 0) {
                ligne.append(',');
            }
            ligne.append(echapper(colonne));
        }
        sortie.println(ligne);
    }

    /**
     * Met une valeur entre guillemets dès qu'elle en a besoin.
     *
     * <p>Un titre d'album porte virgules et guillemets, un chemin porte des espaces : sans
     * guillemets, une seule ligne suffirait à décaler toutes les colonnes d'un tableur.
     */
    private static String echapper(String valeur) {
        boolean aProteger = valeur.contains(",") || valeur.contains("\"")
                || valeur.contains("\n") || valeur.contains("\r");
        return aProteger ? '"' + valeur.replace("\"", "\"\"") + '"' : valeur;
    }
}
