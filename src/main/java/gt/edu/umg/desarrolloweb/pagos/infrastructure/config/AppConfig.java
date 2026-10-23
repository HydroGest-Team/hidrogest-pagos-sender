package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import gt.edu.umg.desarrolloweb.pagos.application.PagosProperties;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.PaymentValidator;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.DestinosProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

@Configuration
@EnableConfigurationProperties({PagosProperties.class, DestinosProperties.class, AdminProperties.class})
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PaymentValidator paymentValidator(Clock clock, PagosProperties properties) {
        return new PaymentValidator(clock, properties.zonaFechaPago());
    }

    /** Falla al arrancar si un destino activo no tiene API key: mejor que 403 en cada pago. */
    @Bean
    public InitializingBean apiKeysPresentes(PagosProperties pagos, DestinosProperties destinos) {
        return () -> {
            List<Destino> sinLlave = pagos.destinosActivos().stream()
                    .filter(d -> http(destinos, d).apiKey().isBlank())
                    .toList();
            if (!sinLlave.isEmpty()) {
                throw new IllegalStateException("Falta la API key de los destinos activos " + sinLlave
                        + " (variables BANK_API_KEY / ERP_API_KEY / THIRD_API_KEY en .env)");
            }
        };
    }

    static DestinosProperties.Http http(DestinosProperties destinos, Destino destino) {
        return switch (destino) {
            case BANCO -> destinos.banco();
            case ERP -> destinos.erp();
            case TERCERO -> destinos.tercero();
        };
    }
}
