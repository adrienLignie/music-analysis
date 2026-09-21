package fr.musique;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import picocli.CommandLine.IVersionProvider;

/**
 * Version du programme, telle que la construction l'a écrite.
 *
 * <h2>Pourquoi ne pas l'écrire dans l'annotation</h2>
 * Une version en dur dans {@code @Command} est vraie le jour où on l'écrit et fausse le lendemain.
 * Rien ne relie ce littéral à la version du {@code pom.xml} : le jour où le projet passe en
 * {@code 1.0.0}, {@code --version} continue d'annoncer {@code 1.0.0-SNAPSHOT}, aucun test ne
 * tombe, et un rapport archivé cesse de dire quel binaire l'a produit. C'est la même raison qui
 * fait figer la date des archives et isoler {@code java.release} dans une propriété : ce que la
 * construction sait, elle doit être seule à le dire.
 *
 * <h2>Là où la valeur est lue</h2>
 * Dans une ressource que Maven remplit au moment de la copie. Le manifeste du jar aurait pu
 * servir, mais l'image native n'en a pas : la ressource, elle, y est embarquée par un argument de
 * construction, et la même lecture répond dans les deux cas.
 *
 * <p>Faute de la trouver — un jar assemblé autrement, des classes lancées depuis un répertoire de
 * compilation — la réponse est « version inconnue » et non une valeur inventée. Mieux vaut avouer
 * l'ignorance que republier un numéro faux.
 */
public final class Version implements IVersionProvider {

    /** Ressource écrite par la construction, à côté des classes. */
    private static final String RESSOURCE = "/version.properties";

    private static final String INCONNUE = "inconnue";

    /** Nom du programme, tel qu'il s'annonce. */
    private static final String NOM = "music-analysis";

    @Override
    public String[] getVersion() {
        return new String[] {NOM + " " + lue()};
    }

    /** Version lue dans la ressource de construction, {@code inconnue} si elle manque. */
    public static String lue() {
        InputStream flux = Version.class.getResourceAsStream(RESSOURCE);
        if (flux == null) {
            return INCONNUE;
        }
        // Le lecteur est ce qu'on referme, et non le flux : c'est lui qui est ouvert en dernier,
        // et le refermer referme ce qu'il enveloppe. L'inverse laisserait le lecteur derrière soi.
        try (Reader lecteur = new InputStreamReader(flux, StandardCharsets.UTF_8)) {
            Properties proprietes = new Properties();
            proprietes.load(lecteur);
            return retenir(proprietes.getProperty("version"));
        } catch (IOException echec) {
            return INCONNUE;
        }
    }

    /**
     * Retient une valeur lue, ou avoue ne pas la connaître.
     *
     * <p>Trois façons de ne rien savoir : la propriété absente, vide, ou laissée telle que le
     * {@code pom.xml} l'écrit. La troisième est la plus traître — elle vient d'une ressource copiée
     * sans filtrage, c'est-à-dire d'une construction mal réglée, et afficher
     * {@code ${project.version}} sous l'option de version ferait passer un réglage manquant pour
     * un numéro.
     */
    static String retenir(String brute) {
        if (brute == null) {
            return INCONNUE;
        }
        String version = brute.trim();
        return version.isEmpty() || version.startsWith("${") ? INCONNUE : version;
    }
}
