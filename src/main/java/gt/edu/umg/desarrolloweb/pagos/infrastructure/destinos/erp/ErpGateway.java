package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp;

import gt.edu.umg.desarrolloweb.pagos.application.PagosProperties;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.HttpDestinationGateway;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpSaludDto;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

/** ERP: unicidad por {@code documento_origen.numero}, montos como texto, comprobante {@code numero_comprobante}. */
@Component
public class ErpGateway extends HttpDestinationGateway {

    private final ErpFeignClient client;
    private final ErpMapper mapper;

    public ErpGateway(ErpFeignClient client, PagosProperties properties, CircuitBreakerRegistry breakers) {
        super(Destino.ERP, breakers);
        this.client = client;
        this.mapper = new ErpMapper(properties.teamCode(), properties.zonaFechaPago());
    }

    @Override
    protected String doSend(Payment payment, String idempotencyKey) {
        return client.registrarPagoErp(mapper.toRequest(payment, idempotencyKey)).getNumeroComprobante();
    }

    @Override
    protected String doLookup(String idempotencyKey) {
        return client.consultarPagoErp(idempotencyKey).getNumeroComprobante();
    }

    @Override
    protected boolean doHealth() {
        ErpSaludDto salud = client.consultarSaludErp();
        return salud != null && salud.getEstado() == ErpSaludDto.EstadoEnum.ARRIBA;
    }
}
