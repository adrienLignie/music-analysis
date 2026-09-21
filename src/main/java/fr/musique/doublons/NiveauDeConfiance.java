package fr.musique.doublons;

/**
 * Degré de certitude d'un rapprochement.
 *
 * <p>Aucun niveau ne vaut « supprime celui-ci ». Ils servent à trier ce que tu regarderas en
 * premier, et à ne jamais présenter comme acquis ce qui ne l'est pas.
 *
 * <p>L'ordre de déclaration est celui de la certitude décroissante : c'est lui que l'option
 * {@code --confiance} compare pour ne retenir que les groupes au moins aussi sûrs qu'un niveau
 * donné.
 */
public enum NiveauDeConfiance {

    /**
     * Les fichiers eux-mêmes portent la preuve qu'ils tiennent le même son.
     *
     * <p>C'est le seul niveau qui ne repose sur aucun nom de dossier, et le seul où il n'y a rien
     * à vérifier : tous les exemplaires du groupe portent une empreinte de leur son, et ces
     * empreintes sont les mêmes. Deux flac dont le MD5 du signal concorde ne sont pas deux albums
     * qui se ressemblent, c'est le même son écrit deux fois.
     *
     * <p>Il ne s'atteint qu'avec {@code --etiquettes}, et seulement sur les formats qui portent une
     * telle empreinte — le flac, et le mp3 encodé par LAME.
     */
    CERTAINE,

    /**
     * Même artiste, même titre exact, même nombre de pistes ou presque. Il reste à choisir lequel
     * garder, pas à vérifier que c'est bien le même album.
     */
    FORTE,

    /**
     * Le rapprochement tient, mais sur un élément de moins : un artiste absent d'un côté ou
     * emprunté au dossier parent, des années éloignées — une réédition, le plus souvent —, ou
     * deux dossiers dont l'un compte bien moins de pistes que l'autre.
     */
    MOYENNE,

    /**
     * Groupe trop nombreux pour être honnête. Au-delà de quelques exemplaires, un « doublon » est
     * presque toujours une série de dossiers au nommage régulier qui a échappé au traitement. Le
     * groupe est montré pour que tu puisses le constater, jamais présenté comme un doublon.
     */
    A_VERIFIER
}
