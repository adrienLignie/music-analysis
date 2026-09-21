package fr.musique.scan;

import java.nio.file.Path;

/**
 * Un fichier audio retenu par le parcours, avant qu'on ait cherché à lire son nom.
 *
 * @param chemin        chemin du fichier
 * @param taille        poids en octets, tel que le parcours l'a reçu du système
 * @param cleDuFichier  ce qui désigne le fichier <b>physique</b> — couple périphérique-inœud sous
 *                      Unix, index et numéro de volume sous Windows —, ou {@code null} quand le
 *                      système de fichiers n'en fournit pas. Deux chemins qui portent la même clé
 *                      mènent au même contenu, et ce contenu n'occupe la place qu'une fois
 */
public record FichierTrouve(Path chemin, long taille, Object cleDuFichier) {}
