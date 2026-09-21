package fr.musique.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Durée et étiquettes d'un conteneur ISO — {@code .m4a}, {@code .mp4}, {@code .aac} d'Apple.
 *
 * <p>Le fichier est une suite d'« atomes » qui portent chacun leur longueur et un nom de quatre
 * lettres, emboîtés les uns dans les autres. La durée est dans {@code mvhd}, les étiquettes dans
 * {@code ilst}, tous deux quelque part sous {@code moov}. On saute d'atome en atome sans jamais
 * toucher au son, ce qui coûte deux lectures de huit octets par atome traversé.
 *
 * <p>Un détail du format vaut d'être écrit ici plutôt que découvert au débogueur : l'atome
 * {@code meta} glisse quatre octets de version avant ses enfants, là où tous les autres
 * commencent directement par le premier. Les sauter est ce qui sépare une lecture qui trouve les
 * étiquettes d'une lecture qui n'en trouve jamais.
 */
final class Mp4Audio {

    private static final String CONTENEUR = "moov";
    private static final String ENTETE_DU_FILM = "mvhd";
    private static final String DONNEES_UTILISATEUR = "udta";
    private static final String METADONNEES = "meta";
    private static final String LISTE = "ilst";
    private static final String VALEUR = "data";

    /** En-tête d'un atome : quatre octets de longueur, quatre de nom. */
    private static final int ENTETE = 8;

    /** Longueur annoncée à 1 : la vraie tient sur les huit octets suivants. */
    private static final int LONGUEUR_ETENDUE = 1;

    /** Atomes examinés au plus dans un même niveau, pour ne pas suivre un fichier malformé. */
    private static final int ATOMES_EXAMINES_AU_PLUS = 64;

    /** Version et drapeaux que l'atome {@code meta} intercale avant ses enfants. */
    private static final int ENTETE_DE_META = 4;

    /** Une étiquette de plus de quelques kilo-octets porte une pochette, pas un titre. */
    private static final int LONGUEUR_D_UNE_VALEUR_AU_PLUS = 8 * 1024;

    /** Étiquettes retenues. Le premier caractère de la plupart est le symbole du droit d'auteur. */
    private static final Map<String, String> CHAMPS = Map.of(
            "©ART", "artist",
            "aART", "albumartist",
            "©alb", "album",
            "©nam", "title",
            "trkn", "tracknumber",
            "©day", "date");

    private final Fenetre fenetre;

    private Mp4Audio(Fenetre fenetre) {
        this.fenetre = fenetre;
    }

    /** Étiquette lue dans l'en-tête, vide quand le fichier n'annonce rien. */
    static Etiquette lire(Fenetre fenetre) throws IOException {
        return new Mp4Audio(fenetre).chercher();
    }

    private Etiquette chercher() throws IOException {
        Atome conteneur = chercherAtome(0, fenetre.taille(), CONTENEUR);
        if (conteneur == null) {
            return Etiquette.vide();
        }
        Atome entete = chercherAtome(conteneur.debutDuContenu(), conteneur.fin(), ENTETE_DU_FILM);
        OptionalDouble duree = entete == null ? OptionalDouble.empty() : lireLaDuree(entete);
        return Etiquette.de(duree, lireLesChamps(conteneur));
    }

    /**
     * Lit l'échelle de temps et la durée de l'en-tête du film.
     *
     * <p>Deux dispositions existent selon la version de l'atome, annoncée par son premier octet :
     * la version 1 écrit les dates et la durée sur huit octets.
     */
    private OptionalDouble lireLaDuree(Atome entete) throws IOException {
        long contenu = entete.debutDuContenu();
        int version = (int) fenetre.entierGrosBoutien(contenu, 1);
        long apresVersionEtDrapeaux = contenu + 4;
        long echelle;
        long duree;
        if (version == 1) {
            echelle = fenetre.entierGrosBoutien(apresVersionEtDrapeaux + 16, 4);
            duree = fenetre.entierGrosBoutien(apresVersionEtDrapeaux + 20, 8);
        } else {
            echelle = fenetre.entierGrosBoutien(apresVersionEtDrapeaux + 8, 4);
            duree = fenetre.entierGrosBoutien(apresVersionEtDrapeaux + 12, 4);
        }
        // Une durée de zéro n'est pas une durée nulle mais une durée non renseignée : les
        // encodeurs qui écrivent l'en-tête avant de connaître la fin la laissent à zéro.
        return echelle <= 0 || duree <= 0
                ? OptionalDouble.empty()
                : OptionalDouble.of((double) duree / echelle);
    }

    /** Descend {@code udta / meta / ilst} et lit ce qui s'y trouve. */
    private Map<String, String> lireLesChamps(Atome conteneur) throws IOException {
        Map<String, String> champs = new HashMap<>();
        Atome donnees =
                chercherAtome(conteneur.debutDuContenu(), conteneur.fin(), DONNEES_UTILISATEUR);
        if (donnees == null) {
            return champs;
        }
        Atome meta = chercherAtome(donnees.debutDuContenu(), donnees.fin(), METADONNEES);
        if (meta == null) {
            return champs;
        }
        Atome liste =
                chercherAtome(meta.debutDuContenu() + ENTETE_DE_META, meta.fin(), LISTE);
        if (liste == null) {
            return champs;
        }
        parcourirLaListe(liste, champs);
        return champs;
    }

    private void parcourirLaListe(Atome liste, Map<String, String> champs) throws IOException {
        long position = liste.debutDuContenu();
        for (int examines = 0;
                examines < ATOMES_EXAMINES_AU_PLUS && position + ENTETE <= liste.fin();
                examines++) {
            long longueur = fenetre.entierGrosBoutien(position, 4);
            String nom = fenetre.etiquette(position + 4);
            long fin = position + longueur;
            if (longueur <= ENTETE || fin > liste.fin()) {
                return;
            }
            String champ = CHAMPS.get(nom);
            if (champ != null) {
                lireLaValeur(position + ENTETE, fin)
                        .ifPresent(valeur -> champs.putIfAbsent(champ, valeur));
            }
            position = fin;
        }
    }

    /**
     * Lit la valeur d'une étiquette, portée par un atome {@code data} qui dit son type.
     *
     * <p>Le type 1 annonce du texte en UTF-8 ; les autres annoncent des nombres, dont le numéro de
     * piste, écrit sur deux octets au milieu d'un petit tableau. Les deux formes sont rendues sous
     * forme de texte : c'est {@link Etiquette} qui décidera d'y lire un nombre.
     */
    private Optional<String> lireLaValeur(long debut, long fin) throws IOException {
        Atome valeur = chercherAtome(debut, fin, VALEUR);
        if (valeur == null || valeur.debutDuContenu() + 8 > valeur.fin()) {
            return Optional.empty();
        }
        int type = (int) fenetre.entierGrosBoutien(valeur.debutDuContenu(), 4);
        long contenu = valeur.debutDuContenu() + 8;
        int longueur = (int) Math.min(valeur.fin() - contenu, LONGUEUR_D_UNE_VALEUR_AU_PLUS);
        if (longueur <= 0) {
            return Optional.empty();
        }
        if (type == 1) {
            ByteBuffer octets = fenetre.lire(contenu, longueur);
            byte[] texte = new byte[longueur];
            octets.get(texte);
            String lu = new String(texte, StandardCharsets.UTF_8).trim();
            return lu.isEmpty() ? Optional.empty() : Optional.of(lu);
        }
        if (longueur >= 4) {
            // Le numéro de piste occupe le deuxième couple d'octets du petit tableau.
            long numero = fenetre.entierGrosBoutien(contenu + 2, 2);
            return numero > 0
                    ? Optional.of(String.valueOf(numero))
                    : Optional.empty();
        }
        return Optional.empty();
    }

    /** Un atome : où commence son contenu et où il s'arrête. */
    private record Atome(long debutDuContenu, long fin) {}

    private Atome chercherAtome(long debut, long fin, String nom) throws IOException {
        long position = debut;
        for (int examines = 0; position + ENTETE <= fin && examines < ATOMES_EXAMINES_AU_PLUS;
                examines++) {
            long longueurAnnoncee = fenetre.entierGrosBoutien(position, 4);
            String etiquette = fenetre.etiquette(position + 4);
            long debutDuContenu = position + ENTETE;
            long longueur = longueurAnnoncee;
            if (longueurAnnoncee == LONGUEUR_ETENDUE) {
                longueur = fenetre.entierGrosBoutien(debutDuContenu, 8);
                debutDuContenu += 8;
            } else if (longueurAnnoncee == 0) {
                // Longueur nulle : l'atome court jusqu'à la fin, ce qu'annonce le dernier atome
                // d'un flux écrit en continu.
                longueur = fin - position;
            }
            long finDeLAtome = position + longueur;
            if (longueur <= 0 || finDeLAtome > fin || debutDuContenu > finDeLAtome) {
                return null;
            }
            if (etiquette.equals(nom)) {
                return new Atome(debutDuContenu, finDeLAtome);
            }
            position = finDeLAtome;
        }
        return null;
    }
}
