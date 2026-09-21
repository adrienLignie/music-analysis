package fr.musique.nom;

/**
 * D'où vient le nom d'artiste retenu pour un album.
 *
 * <p>La distinction n'est pas décorative : un artiste lu dans le nom du dossier de l'album est une
 * donnée, un artiste emprunté au dossier parent est une déduction — juste la plupart du temps,
 * puisque {@code Artiste/Album/} est la convention dominante des bibliothèques musicales, mais
 * fausse dès que le dossier parent est un genre, une lettre de classement ou un simple
 * {@code Musique/}.
 *
 * <p>Deux albums rapprochés dont l'un doit son artiste à son dossier parent reposent donc sur un
 * pas de raisonnement de plus, et le rapprochement ne peut pas être présenté comme aussi sûr que
 * les autres.
 */
public enum OrigineDeLArtiste {

    /** Le nom du dossier de l'album portait l'artiste : {@code Daft Punk - Discovery (2001)}. */
    DOSSIER_DE_L_ALBUM,

    /** Le dossier de l'album ne disait rien : c'est son dossier parent qui a parlé. */
    DOSSIER_PARENT,

    /** Ni l'un ni l'autre : les noms de pistes portaient tous le même artiste. */
    PISTES,

    /** Les étiquettes lues dans les fichiers, seule source qui ne soit pas un nom de dossier. */
    ETIQUETTES,

    /** Personne n'a rien dit : l'album n'a pas d'artiste connu. */
    INCONNUE
}
