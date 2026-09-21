package fr.musique;

import fr.musique.doublons.Album;
import fr.musique.doublons.ChercheurDeDoublons;
import fr.musique.doublons.ChercheurDePistes;
import fr.musique.doublons.GroupeDeDoublons;
import fr.musique.doublons.GroupeDePistes;
import fr.musique.doublons.LiensDurs;
import fr.musique.doublons.NiveauDeConfiance;
import fr.musique.doublons.Piste;
import fr.musique.doublons.VerificationParLesEtiquettes;
import fr.musique.media.Etiquettes;
import fr.musique.media.EtiquettesDuFichier;
import fr.musique.nom.IdentificationDAlbum;
import fr.musique.nom.ParseurDeNomDAlbum;
import fr.musique.nom.ParseurDeNomDePiste;
import fr.musique.rapport.Analyse;
import fr.musique.rapport.FormatDeRapport;
import fr.musique.rapport.Glyphes;
import fr.musique.rapport.PistesSuspectes;
import fr.musique.rapport.Rapport;
import fr.musique.rapport.SortieDuRapport;
import fr.musique.scan.Arborescence;
import fr.musique.scan.CriteresDeScan;
import fr.musique.scan.Identification;
import fr.musique.scan.Inventaire;
import fr.musique.scan.Parcours;
import fr.musique.suivi.Avancement;
import fr.musique.suivi.AvancementSurTerminal;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Point d'entrée.
 *
 * <p>L'outil ne modifie rien : il lit des noms et des poids, et écrit un rapport. Il n'existe
 * volontairement aucune option de suppression, pas même derrière une confirmation. La sortie
 * {@code --format json} ou {@code csv} est là pour que ce refus ne condamne personne à relire
 * cinq cents lignes à la main.
 *
 * <p>L'analyse se fait en cinq temps, et l'ordre compte : le disque est parcouru, les albums sont
 * composés dossier par dossier, ils sont rapprochés, puis — si on le demande — les fichiers sont
 * ouverts pour confronter ces rapprochements à ce que les étiquettes disent. Les liens durs sont
 * démêlés en dernier, une fois que l'exemplaire à garder est arrêté.
 */
@Command(
        name = "music-analysis",
        mixinStandardHelpOptions = true,
        versionProvider = Version.class,
        description = "Recherche des albums en double et classe les dossiers par poids."
                + " Lecture seule.")
public final class Main implements Callable<Integer> {

    /**
     * Plafond du seuil de taille, en kilo-octets. Un gigaoctet est déjà absurde pour un seul
     * morceau ; la borne est là pour qu'une faute de frappe soit refusée plutôt que de déborder en
     * octets.
     */
    private static final long TAILLE_MINIMALE_MAXIMALE_EN_KO = 1024L * 1024;

    private static final long OCTETS_PAR_KO = 1024L;

    @Spec
    private CommandSpec specification;

    @Parameters(
            arity = "1..*",
            paramLabel = "CHEMIN",
            description = "Un ou plusieurs dossiers à analyser, comparés entre eux en un seul lot.")
    private List<Path> racines = new ArrayList<>();

    @Option(
            names = {"-x", "--exclure"},
            paramLabel = "DOSSIER",
            description = "Nom de dossier à ignorer, répétable.")
    private List<String> exclusions = new ArrayList<>();

    @Option(
            names = {"-t", "--top"},
            paramLabel = "N",
            description = "Taille du classement des albums les plus lourds"
                    + " (défaut : ${DEFAULT-VALUE}).")
    private int top = 50;

    @Option(
            names = {"--taille-min"},
            paramLabel = "KO",
            description = "Taille minimale d'un fichier audio, en ko (défaut : ${DEFAULT-VALUE}).")
    private long tailleMinimaleEnKo = CriteresDeScan.TAILLE_MINIMALE_PAR_DEFAUT / OCTETS_PAR_KO;

    @Option(
            names = {"-f", "--format"},
            paramLabel = "FORME",
            description = "Forme du rapport : ${COMPLETION-CANDIDATES} (défaut : ${DEFAULT-VALUE}).")
    private FormatDeRapport format = FormatDeRapport.TEXTE;

    @Option(
            names = {"-c", "--confiance"},
            paramLabel = "NIVEAU",
            description = "Ne garder que les groupes au moins aussi sûrs que ce niveau :"
                    + " ${COMPLETION-CANDIDATES} (défaut : ${DEFAULT-VALUE}).")
    private NiveauDeConfiance confianceMinimale = NiveauDeConfiance.A_VERIFIER;

    @Option(
            names = {"--ascii"},
            description = "Rapport sans accents ni caractères de tracé, pour une console qui ne"
                    + " sait pas les afficher.")
    private boolean ascii;

    @Option(
            names = {"-e", "--etiquettes"},
            description = "Ouvrir les fichiers concernés pour y lire leur durée et leurs"
                    + " étiquettes, et confronter les rapprochements à ce qu'elles disent. Seule"
                    + " option qui ouvre des fichiers ; elle n'en lit que les en-têtes et n'écrit"
                    + " jamais rien.")
    private boolean etiquettes;

    @Option(
            names = {"-p", "--pistes"},
            description = "Chercher aussi les morceaux présents dans plusieurs dossiers, hors des"
                    + " albums déjà signalés.")
    private boolean pistes;

    @Option(
            names = {"-q", "--silencieux"},
            description = "Ne rien afficher pendant l'analyse. Sans cette option, l'avancement"
                    + " s'écrit sur la sortie d'erreur quand celle-ci est un terminal.")
    private boolean silencieux;

    @Override
    public Integer call() {
        verifierLesOptions();
        Avancement avancement = AvancementSurTerminal.pourLaSortieDErreur(silencieux);
        try {
            return analyser(avancement);
        } finally {
            avancement.fin();
        }
    }

    private Integer analyser(Avancement avancement) {
        CriteresDeScan criteres = CriteresDeScan.parDefaut()
                .avecExclusions(exclusions)
                .avecTailleMinimale(tailleMinimaleEnKo * OCTETS_PAR_KO);

        Arborescence arborescence = new Parcours(criteres, avancement).parcourir(racines);
        Identification identification = new Identification(
                new IdentificationDAlbum(new ParseurDeNomDAlbum()),
                new ParseurDeNomDePiste(),
                avancement);
        Inventaire inventaire = identification.identifier(arborescence);

        List<GroupeDeDoublons> trouves = new ChercheurDeDoublons().chercher(inventaire.albums());
        Etiquettes lues = lireLesEtiquettes(inventaire, trouves, avancement);
        inventaire = inventaire.avecEtiquettes(lues);
        trouves = VerificationParLesEtiquettes.verifier(trouves, lues);

        // Les liens durs sont démêlés après le classement des exemplaires et avant le filtre de
        // confiance : un groupe qui n'en était pas un ne doit être compté nulle part, quel que
        // soit le niveau demandé.
        LiensDurs.Resultat demeles = LiensDurs.demeler(trouves);
        List<GroupeDeDoublons> retenus = retenirLesGroupes(demeles.groupes());

        avancement.fin();
        ecrire(new Analyse(
                inventaire,
                retenus,
                chercherLesPistes(inventaire, demeles.groupes()),
                demeles.groupesEcartes()));
        return inventaire.albums().isEmpty() ? CodeSortie.RIEN_A_ANALYSER : CodeSortie.SUCCES;
    }

    /**
     * Va lire les étiquettes, mais seulement là où la réponse change quelque chose.
     *
     * <p>Sonder toute la discothèque coûterait une ouverture de fichier par piste, soit cent mille
     * ouvertures pour une bibliothèque ordinaire, la plupart en réseau, et pour une information
     * dont l'immense majorité ne servirait à rien. Deux populations la méritent : les pistes des
     * dossiers qu'on soupçonne d'être en double, dont elles disent s'ils portent bien le même
     * album, et celles que leur poids rend suspectes, qu'elles seules peuvent blanchir.
     */
    private Etiquettes lireLesEtiquettes(
            Inventaire inventaire, List<GroupeDeDoublons> groupes, Avancement avancement) {
        if (!etiquettes) {
            return Etiquettes.aucune();
        }
        Set<Path> aSonder = new LinkedHashSet<>();
        groupes.stream()
                .flatMap(groupe -> groupe.albums().stream())
                .flatMap(album -> album.pistes().stream())
                .forEach(piste -> retenirSiLisible(piste, aSonder));
        PistesSuspectes.chercher(inventaire.albumsDistincts()).stream()
                .map(PistesSuspectes.Suspecte::piste)
                .forEach(piste -> retenirSiLisible(piste, aSonder));
        return Etiquettes.lire(aSonder, avancement);
    }

    private static void retenirSiLisible(Piste piste, Set<Path> aSonder) {
        if (EtiquettesDuFichier.saitLire(piste.chemin().getFileName().toString())) {
            aSonder.add(piste.chemin());
        }
    }

    /**
     * Cherche les morceaux en double, si on l'a demandé.
     *
     * <p>Les dossiers déjà réunis en groupes d'albums sont laissés de côté, y compris ceux que le
     * filtre de confiance vient d'écarter du rapport : leur cas est connu, et le répéter morceau
     * par morceau ferait de cette section la plus longue du rapport pour n'y rien ajouter.
     */
    private List<GroupeDePistes> chercherLesPistes(
            Inventaire inventaire, List<GroupeDeDoublons> tousLesGroupes) {
        if (!pistes) {
            return List.of();
        }
        Set<Path> dejaSignales = tousLesGroupes.stream()
                .flatMap(groupe -> groupe.albums().stream())
                .map(Album::dossier)
                .collect(Collectors.toSet());
        return ChercheurDePistes.chercher(inventaire.albumsDistincts(), dejaSignales);
    }

    /**
     * Refuse les valeurs qui n'ont pas de sens.
     *
     * <p>Sans ce contrôle, {@code --top -5} remonte en erreur interne depuis les profondeurs de la
     * bibliothèque standard, code de sortie 1 : un script ne saurait pas distinguer cette faute de
     * frappe d'une vraie panne.
     */
    private void verifierLesOptions() {
        if (top < 0) {
            throw new ParameterException(specification.commandLine(),
                    "--top attend un nombre positif ou nul, reçu : " + top);
        }
        if (tailleMinimaleEnKo < 0 || tailleMinimaleEnKo > TAILLE_MINIMALE_MAXIMALE_EN_KO) {
            throw new ParameterException(specification.commandLine(),
                    "--taille-min attend un nombre de ko entre 0 et "
                            + TAILLE_MINIMALE_MAXIMALE_EN_KO + ", reçu : " + tailleMinimaleEnKo);
        }
    }

    /** Ne garde que les groupes au moins aussi sûrs que le niveau demandé. */
    private List<GroupeDeDoublons> retenirLesGroupes(List<GroupeDeDoublons> groupes) {
        return groupes.stream()
                .filter(groupe -> groupe.confiance().ordinal() <= confianceMinimale.ordinal())
                .toList();
    }

    private void ecrire(Analyse analyse) {
        Charset jeu = format.jeuDeCaracteres();
        PrintStream flux = SortieDuRapport.envelopper(System.out, jeu);
        Rapport rapport = format.creer(flux, Glyphes.pour(jeu, ascii));
        try {
            rapport.ecrire(analyse, top);
        } finally {
            flux.flush();
        }
    }

    /**
     * Commande prête à exécuter.
     *
     * <p>Les réglages sont ici et non dans {@link #main} pour que rien ne puisse diverger entre
     * l'outil lancé en ligne de commande et le même outil éprouvé par un test : une option
     * acceptée dans un cas doit l'être dans l'autre.
     */
    public static CommandLine commande() {
        return new CommandLine(new Main()).setCaseInsensitiveEnumValuesAllowed(true);
    }

    public static void main(String... arguments) {
        System.exit(commande().execute(arguments));
    }
}
