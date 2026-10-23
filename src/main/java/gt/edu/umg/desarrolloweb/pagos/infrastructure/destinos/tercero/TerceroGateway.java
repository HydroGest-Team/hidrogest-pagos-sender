package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero;

import gt.edu.umg.desarrolloweb.pagos.application.PagosProperties;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.HttpDestinationGateway;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.HealthStatusDto;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

/** Tercera API: unicidad por header {@code Idempotency-Key}, monto como texto, comprobante {@code confirmationId}. */
@Component
public class TerceroGateway extends HttpDestinationGateway {

    private final TerceroFeignClient client;
    private final TerceroMapper mapper;

    public TerceroGateway(TerceroFeignClient client, PagosProperties properties, CircuitBreakerRegistry breakers) {
        super(Destino.TERCERO, breakers);
        this.client = client;
        this.mapper = new TerceroMapper(properties.teamCode(), properties.zonaFechaPago());
    }

    @Override
    protected String doSend(Payment payment, String idempotencyKey) {
        return client.sendThirdPayment(idempotencyKey, mapper.toRequest(payment)).getConfirmationId();
    }

    @Override
    protected String doLookup(String idempotencyKey) {
        return client.getThirdPayment(idempotencyKey).getConfirmationId();
    }

    @Override
    protected boolean doHealth() {
        HealthStatusDto health = client.getThirdHealth();
        return health != null && health.getStatus() == HealthStatusDto.StatusEnum.UP;
    }
}
