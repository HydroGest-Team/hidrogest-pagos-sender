package gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Conexión y tablas de DynamoDB ({@code aws.dynamodb.*}).
 *
 * @param endpoint     vacío = AWS real con la cadena de credenciales estándar; con valor = DynamoDB Local
 * @param crearTablas  crea las tablas si no existen (entorno local); en AWS las crea quien administre la cuenta
 * @param retencionIntentos TTL de la bitácora de intentos
 */
@Validated
@ConfigurationProperties(prefix = "aws.dynamodb")
public record DynamoDbProperties(
        @NotBlank String region,
        @DefaultValue("") String endpoint,
        @DefaultValue("false") boolean crearTablas,
        @DefaultValue("pagos_entregas") @NotBlank String tablaEntregas,
        @DefaultValue("pagos_intentos") @NotBlank String tablaIntentos,
        @DefaultValue("pagos_control") @NotBlank String tablaControl,
        @DefaultValue("90d") Duration retencionIntentos
) {
    public static final String GSI_ABIERTOS = "abiertos-index";
}
