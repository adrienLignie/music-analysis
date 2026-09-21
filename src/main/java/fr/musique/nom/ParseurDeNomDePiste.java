package fr.musique.nom;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecture du nom d'un fichier audio.
 *
 * <p>Le nom d'une piste porte trois choses au plus : un numéro, un artiste et un titre. Les trois
 * conventions qu'on rencontre réellement :
 *
 * <pre>
 * 01 - Speak To Me.flac                    numéro et titre
 * 01. Pink Floyd - Speak To Me.mp3         numéro, artiste et titre
 * 1-03 Breathe.m4a                         disque, numéro et titre
 * Speak To Me.ogg                          titre seul
 * A1 - Speak To Me.flac                    face de vinyle et titre
 * </pre>
 *
 * <p>Le numéro est ce qui compte le plus, plus encore que le titre : c'est de lui que se déduit un
 * album incomplet, et un trou dans la numérotation est le seul signe qu'un téléchargement s'est
 * interrompu au milieu d'un disque.
 */
public final class ParseurDeNomDePiste {

    /** Deux nombres en tête, séparés : disque et piste — {@code 1-03}, {@code 2.07}. */
    private static final Pattern DISQUE_ET_PISTE =
            Pattern.compile("^(\\d{1,2})[-_. ]+(\\d{1,2})(?=[-_. ]|$)");

    /** Un nombre en tête : le numéro de piste — {@code 01 - }, {@code 03.}, {@code 7_}. */
    private static final Pattern PISTE_SEULE = Pattern.compile("^(\\d{1,3})(?=[-_. ]|$)");

    /** Face et rang d'un vinyle : {@code A1}, {@code B3}, {@code D2}. */
    private static final Pattern FACE_DE_VINYLE =
            Pattern.compile("^([A-Da-d])(\\d{1,2})(?=[-_. ]|$)");

    /** Séparateur entre artiste et titre, tel qu'il s'écrit dans les noms de fichiers. */
    private static final Pattern SEPARATEUR_ARTISTE = Pattern.compile("\\s+-\\s+|\\s+–\\s+|_-_");

    /**
     * Sous cette longueur, ce qui précède un tiret n'est pas tenu pour un artiste.
     *
     * <p>Une lettre isolée est bien plus souvent une face de vinyle ou un rangement alphabétique
     * qu'un nom d'artiste. Le prix de cette précaution est connu et assumé : les rares artistes
     * dont le nom tient en une lettre gardent la leur dans le titre.
     */
    private static final int LONGUEUR_MINIMALE_D_UN_ARTISTE = 2;

    /** Analyse un nom de fichier audio, extension comprise, sans préfixe connu. */
    public NomDePiste analyser(String nomDeFichier) {
        return analyser(nomDeFichier, PrefixeDeCollection.aucun());
    }

    /**
     * Analyse un nom de fichier audio dont le dossier impose un patron.
     *
     * <p>Le préfixe est retiré d'abord : sans cela, l'artiste et l'album répétés au début de chaque
     * fichier passeraient pour le titre de la piste.
     */
    public NomDePiste analyser(String nomDeFichier, PrefixeDeCollection prefixe) {
        String extension = extensionDe(nomDeFichier);
        String sansExtension = retirerLExtension(nomDeFichier, extension);

        // Les séparateurs que le retrait du patron laisse en tête doivent tomber avant tout le
        // reste : « - 02 - Breathe » ne ressemble à aucun numéro de piste tant qu'il commence par
        // un tiret.
        String reste = nettoyerLesBords(prefixe.retirerDe(sansExtension));

        OptionalInt disque = OptionalInt.empty();
        OptionalInt numero = OptionalInt.empty();

        Matcher disqueEtPiste = DISQUE_ET_PISTE.matcher(reste);
        Matcher face = FACE_DE_VINYLE.matcher(reste);
        Matcher pisteSeule = PISTE_SEULE.matcher(reste);
        if (disqueEtPiste.find()) {
            disque = OptionalInt.of(Integer.parseInt(disqueEtPiste.group(1)));
            numero = lirePiste(disqueEtPiste.group(2));
            reste = reste.substring(disqueEtPiste.end());
        } else if (face.find()) {
            disque = OptionalInt.of(face.group(1).toLowerCase(Locale.ROOT).charAt(0) - 'a' + 1);
            numero = lirePiste(face.group(2));
            reste = reste.substring(face.end());
        } else if (pisteSeule.find()) {
            numero = lirePiste(pisteSeule.group(1));
            if (numero.isPresent()) {
                reste = reste.substring(pisteSeule.end());
            }
        }

        String sansNumero = nettoyerLesBords(reste);
        Optional<String> artiste = separerLArtiste(sansNumero);
        String titre = artiste.map(nom -> apresLeSeparateur(sansNumero)).orElse(sansNumero);
        return new NomDePiste(
                titre,
                CleDeTitre.normaliser(titre),
                artiste.map(String::trim),
                numero,
                disque,
                extension);
    }

    /**
     * Sépare l'artiste du titre quand le nom porte les deux.
     *
     * <p>Le tiret n'est un séparateur que s'il est entouré d'espaces : {@code Jean-Jacques
     * Goldman} n'en porte pas, et {@code Wish You Were Here - Live} n'annonce pas un artiste nommé
     * « Wish You Were Here ». Seule la première occurrence est prise en compte, et ce qui la suit
     * doit rester non vide.
     */
    private static Optional<String> separerLArtiste(String nom) {
        String[] morceaux = SEPARATEUR_ARTISTE.split(nom, 2);
        if (morceaux.length < 2) {
            return Optional.empty();
        }
        String candidat = morceaux[0].trim();
        if (candidat.length() < LONGUEUR_MINIMALE_D_UN_ARTISTE || morceaux[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(candidat);
    }

    private static String apresLeSeparateur(String nom) {
        String[] morceaux = SEPARATEUR_ARTISTE.split(nom, 2);
        return morceaux.length < 2 ? nom : morceaux[1].trim();
    }

    private static OptionalInt lirePiste(String jeton) {
        OptionalInt valeur = Numeros.lireEntier(jeton);
        return valeur.isPresent() && Numeros.estUnePistePlausible(valeur.getAsInt())
                ? valeur
                : OptionalInt.empty();
    }

    /** Retire les séparateurs et espaces que le retrait du numéro a laissés en tête. */
    private static String nettoyerLesBords(String nom) {
        int debut = 0;
        while (debut < nom.length() && estUnSeparateur(nom.charAt(debut))) {
            debut++;
        }
        int fin = nom.length();
        while (fin > debut && estUnSeparateur(nom.charAt(fin - 1))) {
            fin--;
        }
        return nom.substring(debut, fin);
    }

    private static boolean estUnSeparateur(char caractere) {
        return caractere == ' ' || caractere == '-' || caractere == '_' || caractere == '.'
                || caractere == '–';
    }

    /** Extension en minuscules, sans le point ; chaîne vide quand le nom n'en porte pas. */
    public static String extensionDe(String nomDeFichier) {
        int point = nomDeFichier.lastIndexOf('.');
        if (point < 0 || point == nomDeFichier.length() - 1) {
            return "";
        }
        return nomDeFichier.substring(point + 1).toLowerCase(Locale.ROOT);
    }

    private static String retirerLExtension(String nomDeFichier, String extension) {
        return extension.isEmpty()
                ? nomDeFichier
                : nomDeFichier.substring(0, nomDeFichier.length() - extension.length() - 1);
    }

    /** Jetons normalisés d'un nom, séparateurs retirés. */
    static List<String> decouper(String texte) {
        List<String> jetons = new ArrayList<>();
        for (String jeton : CleDeTitre.normaliser(texte).split(" ")) {
            if (!jeton.isEmpty()) {
                jetons.add(jeton);
            }
        }
        return jetons;
    }
}
