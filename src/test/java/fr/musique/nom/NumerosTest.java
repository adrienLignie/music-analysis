package fr.musique.nom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Éprouve la lecture des nombres sous leurs trois écritures. */
class NumerosTest {

    @Test
    @DisplayName("les chiffres arabes, romains et les nombres en lettres se rejoignent")
    void lesTroisEcrituresSeRejoignent() {
        assertThat(Numeros.lireVolume("2")).hasValue(2);
        assertThat(Numeros.lireVolume("ii")).hasValue(2);
        assertThat(Numeros.lireVolume("two")).hasValue(2);
        assertThat(Numeros.lireVolume("deux")).hasValue(2);
    }

    @Test
    @DisplayName("un nombre hors de l'intervalle utile n'est pas un volume")
    void leNombreHorsBornesNEstPasUnVolume() {
        assertThat(Numeros.lireVolume("1984")).isEmpty();
        assertThat(Numeros.lireVolume("0")).isEmpty();
        assertThat(Numeros.lireVolume("absolution")).isEmpty();
    }

    @Test
    @DisplayName("un entier est lu sans contrainte d'intervalle, mais pas n'importe quoi")
    void lEntierEstLuSansContrainte() {
        assertThat(Numeros.lireEntier("1984")).hasValue(1984);
        assertThat(Numeros.lireEntier("007")).hasValue(7);
        assertThat(Numeros.lireEntier("")).isEmpty();
        assertThat(Numeros.lireEntier("12a")).isEmpty();
        assertThat(Numeros.lireEntier("1234567890")).isEmpty();
    }

    @Test
    @DisplayName("les chiffres romains sont reconnus comme tels")
    void lesRomainsSontReconnus() {
        assertThat(Numeros.estRomain("iv")).isTrue();
        assertThat(Numeros.estRomain("ivre")).isFalse();
    }

    @Test
    @DisplayName("les bornes disent ce qu'un volume et une piste peuvent être")
    void lesBornesSontClaires() {
        assertThat(Numeros.estUnVolumePlausible(Numeros.VOLUME_MAXIMUM)).isTrue();
        assertThat(Numeros.estUnVolumePlausible(Numeros.VOLUME_MAXIMUM + 1)).isFalse();
        assertThat(Numeros.estUnePistePlausible(Numeros.PISTE_MAXIMUM)).isTrue();
        assertThat(Numeros.estUnePistePlausible(Numeros.PISTE_MAXIMUM + 1)).isFalse();
        assertThat(Numeros.estUnePistePlausible(0)).isFalse();
    }
}
