package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyAndKeysTest {

    private final RetryPolicy policy = new RetryPolicy(TestProperties.pagos());

    @Test
    void backoffPorNumeroDeIntentoUsandoLosTiers() {
        assertThat(policy.delayFor(1, null)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.delayFor(2, null)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.delayFor(3, null)).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.delayFor(4, null)).isEqualTo(Duration.ofMinutes(10));
        assertThat(policy.delayFor(9, null)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void respetaRetryAfterEligiendoElTierQueLoCubre() {
        // Primer intento sería 5 s, pero el destino pidió 20 s: se usa el tier de 30 s.
        assertThat(policy.delayFor(1, Duration.ofSeconds(20))).isEqualTo(Duration.ofSeconds(30));
        // Retry-After mayor que todos los tiers: el mayor disponible.
        assertThat(policy.delayFor(1, Duration.ofHours(1))).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void agotaAlLlegarAlMaximo() {
        assertThat(policy.agotado(5)).isFalse();
        assertThat(policy.agotado(6)).isTrue();
    }

    @Test
    void llaveEstableConPrefijoDerivadoDelEquipo() {
        IdempotencyKeys keys = new IdempotencyKeys(TestProperties.pagos());
        assertThat(keys.forPayment(123)).isEqualTo("EQA-000123");
        assertThat(keys.forPayment(900001)).isEqualTo("EQA-900001");
        assertThat(keys.forPayment(123)).isEqualTo(keys.forPayment(123));
    }

    @Test
    void prefijoConfigurableTieneprioridad() {
        IdempotencyKeys keys = new IdempotencyKeys(TestProperties.pagos("EQA-DEV-", List.of(Destino.BANCO)));
        assertThat(keys.forPayment(7)).isEqualTo("EQA-DEV-000007");
    }

    @Test
    void derivaPrefijoDeCadaEquipo() {
        assertThat(IdempotencyKeys.derivarPrefijo("EQUIPO_B")).isEqualTo("EQB-");
        assertThat(IdempotencyKeys.derivarPrefijo("EQUIPO_D")).isEqualTo("EQD-");
    }
}
