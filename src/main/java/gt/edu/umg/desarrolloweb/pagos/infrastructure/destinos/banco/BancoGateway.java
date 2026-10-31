package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco;

import gt.edu.umg.desarrolloweb.pagos.application.PagosProperties;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.BankPaymentReceiptDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.HealthStatusDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.HttpDestinationGateway;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.Montos;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

/** Banco: unicidad por header {@code Idempotency-Key}, monto en centavos, comprobante {@code receiptId}. */
@Component
public class BancoGateway extends HttpDestinationGateway {

    private final BancoFeignClient client;
    private final BancoMapper mapper;

    public BancoGateway(BancoFeignClient client, PagosProperties properties, CircuitBreakerRegistry breakers) {
        super(Destino.BANCO, breakers);
        this.client = client;
        this.mapper = new BancoMapper(properties.teamCode(), properties.zonaFechaPago());
    }

    @Override
    protected String doSend(Payment payment, String idempotencyKey) {
        return client.sendBankPayment(idempotencyKey, mapper.toRequest(payment)).getReceiptId();
    }

    @Override
    protected Registro doLookup(String idempotencyKey) {
        BankPaymentReceiptDto receipt = client.getBankPayment(idempotencyKey);
        Long centavos = receipt.getAmount() == null ? null : receipt.getAmount().getValueInCents();
        return new Registro(receipt.getReceiptId(), Montos.desdeCentavos(centavos));
    }

    @Override
    protected boolean doHealth() {
        HealthStatusDto health = client.getBankHealth();
        return health != null && health.getStatus() == HealthStatusDto.StatusEnum.UP;
    }
}
