package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Configuración del servicio de pagos ({@code pagos.*}). Se valida al arrancar: si falta algo
 * obligatorio la aplicación no inicia.
 *
 * @param teamCode       equipo del curso; debe coincidir con la API key de cada destino
 * @param prefijoLlave   prefijo de la llave de idempotencia; vacío = derivado de {@code teamCode} ({@code EQUIPO_A → EQA-})
 * @param zonaFechaPago  zona en que HidroGest captura {@code fecha_pago} (columna sin zona)
 * @param destinosActivos destinos a los que se envía cada pago
 * @param desde          por destino, marca de tiempo mínima de los pagos a enviar (puesta al día de un destino nuevo)
 */
@Validated
@ConfigurationProperties(prefix = "pagos")
public record PagosProperties(
        @NotNull @Pattern(regexp = "EQUIPO_[A-D]") String teamCode,
        @DefaultValue("") @Pattern(regexp = "^$|^[A-Za-z0-9_-]{1,20}$") String prefijoLlave,
        @DefaultValue("America/Guatemala") ZoneId zonaFechaPago,
        @NotEmpty List<Destino> destinosActivos,
        Map<Destino, LocalDateTime> desde,
        @Valid @NotNull Producer producer,
        @Valid @NotNull Reintentos reintentos,
        @Valid @NotNull Reconciliador reconciliador
) {
    public PagosProperties {
        destinosActivos = List.copyOf(destinosActivos);
        desde = desde == null ? Map.of() : Map.copyOf(new EnumMap<>(desde));
    }

    public record Producer(
            @NotNull Duration intervalo,
            @Min(1) int lote,
            @NotNull Duration traslape
    ) {}

    /**
     * @param tiers esperas disponibles, de menor a mayor; cada una es una cola con TTL en RabbitMQ
     */
    public record Reintentos(
            @Min(1) int maxIntentos,
            @NotEmpty List<Duration> tiers
    ) {
        public Reintentos {
            tiers = tiers.stream().sorted().toList();
        }
    }

    /**
     * @param gracia         tiempo extra sobre {@code nextAttemptAt} antes de considerar perdido un mensaje
     * @param lease          duración del reclamo de un envío ({@code EN_VUELO})
     * @param barrido        cada cuánto se compara HidroGest contra DynamoDB buscando faltantes
     */
    public record Reconciliador(
            @NotNull Duration intervalo,
            @NotNull Duration lease,
            @NotNull Duration gracia,
            @NotNull Duration barrido,
            @Min(1) int lote
    ) {}
}
