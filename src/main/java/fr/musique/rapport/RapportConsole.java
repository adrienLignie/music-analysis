package fr.musique.rapport;

import fr.musique.doublons.Album;
import fr.musique.doublons.GroupeDeDoublons;
import fr.musique.doublons.GroupeDePistes;
import fr.musique.doublons.NiveauDeConfiance;
import fr.musique.doublons.Piste;
import fr.musique.media.Etiquettes;
import fr.musique.scan.Inventaire;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rapport affiché dans le terminal.
 *
 * <p>Les sections viennent dans l'ordre où elles servent : ce qu'on peut supprimer sans rien
 * perdre, ce qui pèse le plus lourd, puis ce qui cloche. Chaque groupe de doublons porte son
 * motif, pour qu'aucun verdict n'ait à être cru sur parole.
 *
 * <p>Toutes les lignes passent par {@link #ligne}, qui les confie aux {@link Glyphes} : c'est là
 * que le rapport se réduit à l'ASCII quand la sortie ne sait pas écrire un {@code ─}.
 */
public final class RapportConsole implements Rapport {

    /**
     * Part du poids d'un album, en pour cent, au-delà de laquelle ce qui n'est pas de la musique
     * mérite d'être dit.
     *
     * <p>Un dixième : en deçà, ce sont les quelques centaines de kilo-octets de pochette que porte
     * tout album correctement rangé, et les signaler ferait du classement une liste de remarques.
     */
    private static final int HORS_MUSIQUE_NOTABLE_EN_POURCENT = 10;

    private final PrintStream sortie;
    private final Glyphes glyphes;
    private final String separateur;

    /** Rapport tracé au complet, pour une sortie qui sait tout écrire. */
    public RapportConsole(PrintStream sortie) {
        this(sortie, Glyphes.completes());
    }

    public RapportConsole(PrintStream sortie, Glyphes glyphes) {
        this.sortie = sortie;
        this.glyphes = glyphes;
        this.separateur = glyphes.separateur();
    }

    @Override
    public void ecrire(Analyse analyse, int top) {
        Inventaire inventaire = analyse.inventaire();
        ecrireEnTete(inventaire);
        ecrireLesDoublons(analyse.doublons(), analyse.groupesEcartes());
        ecrireLesPistesEnDouble(analyse.pistesEnDouble());
        ecrireLeClassement(inventaire.albumsDistincts(), top);
        ecrireLesAlbumsIncomplets(inventaire);
        ecrireLesPistesSuspectes(inventaire);
        ecrireLesRangements(inventaire);
        sortie.flush();
    }

    private void ecrireEnTete(Inventaire inventaire) {
        ligne(separateur);
        ligne("%d albums retenus, %d pistes, %s au total",
                inventaire.albumsDistincts().size(),
                inventaire.nombreDePistes(),
                Tailles.enTexte(inventaire.tailleTotale()));
        if (inventaire.octetsHorsAudio() > 0) {
            // Cette place existe pour de bon et n'apparaît dans aucune autre ligne : le classement
            // porte sur la musique, et les pochettes n'y entrent pas.
            ligne("%s de plus ne sont pas de la musique (pochettes, livrets, journaux)",
                    Tailles.enTexte(inventaire.octetsHorsAudio()));
        }
        if (inventaire.fichiersIgnores() > 0 || inventaire.dossiersIllisibles() > 0) {
            ligne("%d fichiers écartés (trop petits ou illisibles), %d dossiers partiellement lus",
                    inventaire.fichiersIgnores(), inventaire.dossiersIllisibles());
        }
        if (!inventaire.repetitionsPhysiques().isEmpty()) {
            // Ces chemins existent pour de bon et méritent d'être annoncés, mais les compter dans
            // le total ferait croire à une discothèque plus lourde qu'elle n'est.
            ligne("%d chemins de plus mènent à un fichier déjà compté (liens durs)",
                    inventaire.repetitionsPhysiques().size());
        }
        if (!inventaire.etiquettes().estVide()) {
            ligne("%d fichiers ont été ouverts pour y lire leurs étiquettes",
                    inventaire.etiquettes().taille());
        }
    }

    private void ecrireLesDoublons(List<GroupeDeDoublons> groupes, int groupesEcartes) {
        ligne("");
        ligne(separateur);
        if (groupes.isEmpty()) {
            ligne("ALBUMS EN DOUBLE : aucun");
            ecrireLesGroupesEcartes(groupesEcartes);
            return;
        }
        long recuperable = groupes.stream().mapToLong(GroupeDeDoublons::placeRecuperable).sum();
        ligne("ALBUMS EN DOUBLE : %d groupes, %s récupérables", groupes.size(),
                Tailles.enTexte(recuperable));
        ecrireLesGroupesEcartes(groupesEcartes);
        ligne(separateur);

        Map<NiveauDeConfiance, List<GroupeDeDoublons>> parConfiance =
                groupes.stream().collect(Collectors.groupingBy(GroupeDeDoublons::confiance));
        for (NiveauDeConfiance niveau : NiveauDeConfiance.values()) {
            List<GroupeDeDoublons> duNiveau = parConfiance.get(niveau);
            if (duNiveau == null) {
                continue;
            }
            ligne("");
            ligne("── Confiance %s (%d groupes) ──", niveau, duNiveau.size());
            duNiveau.forEach(this::ecrireUnGroupe);
        }
    }

    private void ecrireLesGroupesEcartes(int groupesEcartes) {
        if (groupesEcartes > 0) {
            ligne("  (%d groupes écartés : un même dossier atteint par plusieurs chemins)",
                    groupesEcartes);
        }
    }

    private void ecrireUnGroupe(GroupeDeDoublons groupe) {
        ligne("");
        ligne("  %s  |  récupérable : %s",
                groupe.motif(), Tailles.enTexte(groupe.placeRecuperable()));
        List<Album> albums = groupe.albums();
        for (int rang = 0; rang < albums.size(); rang++) {
            Album album = albums.get(rang);
            // Le rang et non l'égalité : deux exemplaires peuvent peser le même poids et porter le
            // même nom, et « garder ? » ne doit désigner qu'une seule ligne.
            String marque = rang == 0 ? "garder ?" : "        ";
            String taille = groupe.estUnLienDur(album)
                    ? "mêmes fichiers"
                    : Tailles.enTexte(album.taille());
            ligne("    %s %14s  %2d pistes  %-5s  %s",
                    marque, taille, album.nombreDePistes(), album.formatDominant(),
                    album.dossier());
        }
    }

    private void ecrireLesPistesEnDouble(List<GroupeDePistes> groupes) {
        if (groupes.isEmpty()) {
            return;
        }
        long recuperable = groupes.stream().mapToLong(GroupeDePistes::placeRecuperable).sum();
        ligne("");
        ligne(separateur);
        ligne("MORCEAUX EN DOUBLE (%d), %s récupérables", groupes.size(),
                Tailles.enTexte(recuperable));
        ligne("  Hors des albums déjà signalés. Un même morceau figure légitimement sur un album");
        ligne("  et sur une compilation : cette liste se lit, elle ne s'applique pas.");
        ligne(separateur);
        for (GroupeDePistes groupe : groupes) {
            ligne("");
            ligne("  %s  |  récupérable : %s",
                    groupe.motif(), Tailles.enTexte(groupe.placeRecuperable()));
            List<Piste> pistes = groupe.pistes();
            for (int rang = 0; rang < pistes.size(); rang++) {
                ligne("    %s %12s  %s",
                        rang == 0 ? "garder ?" : "        ",
                        Tailles.enTexte(pistes.get(rang).taille()),
                        pistes.get(rang).chemin());
            }
        }
    }

    private void ecrireLeClassement(List<Album> albums, int top) {
        ligne("");
        ligne(separateur);
        ligne("LES %d ALBUMS LES PLUS LOURDS", top);
        ligne(separateur);
        albums.stream()
                .sorted(Comparator.comparingLong(Album::taille).reversed()
                        .thenComparing(album -> album.dossier().toString()))
                .limit(top)
                .forEach(album -> ligne("  %12s  %2d pistes  %-5s  %s%s",
                        Tailles.enTexte(album.taille()),
                        album.nombreDePistes(),
                        album.formatDominant(),
                        album.dossier(),
                        horsMusique(album)));
    }

    /**
     * Mention du poids qui n'est pas de la musique, quand il compte.
     *
     * <p>Le classement reste établi sur la musique seule — deux albums ne se comparent pas au
     * poids de leurs pochettes — mais taire un livret numérisé plus lourd que le disque qu'il
     * illustre priverait le classement de son objet. Le seuil est relatif : quelques centaines de
     * kilo-octets de pochette accompagnent tous les albums du monde et n'intéressent personne.
     */
    private static String horsMusique(Album album) {
        long musique = album.taille();
        if (musique <= 0) {
            // Une part de rien ne veut rien dire. Un dossier dont toutes les pistes sont atteintes
            // par un autre chemin ne pèse pas et ne figure pas au classement ; le dire ici évite
            // d'annoncer que ses pochettes en représentent l'infini.
            return "";
        }
        long annexe = album.octetsHorsAudio();
        return annexe * 100 >= musique * HORS_MUSIQUE_NOTABLE_EN_POURCENT
                ? "  (+ " + Tailles.enTexte(annexe) + " hors musique)"
                : "";
    }

    private void ecrireLesAlbumsIncomplets(Inventaire inventaire) {
        List<AlbumsIncomplets.Incomplet> incomplets =
                AlbumsIncomplets.chercher(inventaire.albumsDistincts(), inventaire.etiquettes());
        if (incomplets.isEmpty()) {
            return;
        }
        ligne("");
        ligne(separateur);
        ligne("ALBUMS INCOMPLETS (%d)", incomplets.size());
        ligne("  Trous dans la numérotation : probables téléchargements interrompus.");
        ligne(separateur);
        for (AlbumsIncomplets.Incomplet incomplet : incomplets) {
            ligne("  %d pistes sur %d, manquent %s  %s",
                    incomplet.album().nombreDePistes(),
                    incomplet.attendues(),
                    numeros(incomplet.manquants()),
                    incomplet.album().dossier());
        }
    }

    /** Liste de numéros écourtée : au-delà de quelques-uns, le compte suffit. */
    private static String numeros(List<Integer> manquants) {
        if (manquants.size() <= 8) {
            return manquants.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return manquants.stream().limit(8).map(String::valueOf)
                .collect(Collectors.joining(", "))
                + "… (" + manquants.size() + " en tout)";
    }

    private void ecrireLesPistesSuspectes(Inventaire inventaire) {
        List<PistesSuspectes.Suspecte> suspectes =
                PistesSuspectes.chercher(inventaire.albumsDistincts(), inventaire.etiquettes());
        if (suspectes.isEmpty()) {
            return;
        }
        ligne("");
        ligne(separateur);
        ligne("PISTES SUSPECTES (%d)", suspectes.size());
        ligne("  Trop légères pour ce que leur format promet : à écouter pour vérifier.");
        ligne(separateur);
        for (PistesSuspectes.Suspecte suspecte : suspectes) {
            if (suspecte.nature() == PistesSuspectes.Nature.DEBIT) {
                ligne("  %s au lieu de %s attendus (%s)  %s",
                        Etiquettes.debitEnTexte(suspecte.constate()),
                        Etiquettes.debitEnTexte(suspecte.attendu()),
                        suspecte.piste().nom().extension(),
                        suspecte.piste().chemin());
            } else {
                ligne("  %10s contre %s en médiane dans le dossier  %s",
                        Tailles.enTexte(suspecte.constate()),
                        Tailles.enTexte(suspecte.attendu()),
                        suspecte.piste().chemin());
            }
        }
    }

    private void ecrireLesRangements(Inventaire inventaire) {
        List<Rangements.Anomalie> anomalies = Rangements.chercher(inventaire);
        if (anomalies.isEmpty()) {
            return;
        }
        ligne("");
        ligne(separateur);
        ligne("DOSSIERS MAL RANGÉS (%d)", anomalies.size());
        ligne("  Là où le programme voit mal : ces dossiers ne seront rapprochés de rien.");
        ligne(separateur);
        for (Rangements.Anomalie anomalie : anomalies) {
            ligne("  %12s  %2d pistes  %s", Tailles.enTexte(anomalie.album().taille()),
                    anomalie.album().nombreDePistes(), anomalie.album().dossier());
            ligne("               %s", anomalie.nature().libelle());
        }
    }

    /** Point de passage unique de toutes les lignes, et donc de l'adaptation des caractères. */
    private void ligne(String format, Object... arguments) {
        sortie.println(glyphes.adapter(String.format(format, arguments)));
    }
}
