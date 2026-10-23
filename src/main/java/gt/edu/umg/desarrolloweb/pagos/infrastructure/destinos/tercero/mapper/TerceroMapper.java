package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.mapper;

import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.ThirdCustomerDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.ThirdPaymentRequestDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.TeamCodeDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Component
public class TerceroMapper {

    public ThirdPaymentRequestDto toRequest(Payment payment, String teamCode, String idempotencyKey) {
        ThirdPaymentRequestDto request = new ThirdPaymentRequestDto();
        request.setTeamCode(TeamCodeDto.fromValue(teamCode));
        request.setReference(payment.numeroRecibo());
        request.setCustomer(toCustomer(payment));
        request.setAmount(toMontoTexto(payment.monto()));
        request.setCurrency(ThirdPaymentRequestDto.CurrencyEnum.GTQ);
        request.setPaidAt(payment.fechaPagoConZona().atZone(Payment.ZONA_HORARIA).toOffsetDateTime());
        request.setMethod(ThirdPaymentRequestDto.MethodEnum.fromValue(payment.metodoTercero()));
        request.setPeriod(payment.periodoTercero());
        return request;
    }

    private ThirdCustomerDto toCustomer(Payment payment) {
        ThirdCustomerDto customer = new ThirdCustomerDto();
        customer.setCode(payment.clienteCode());
        customer.setName(truncate(payment.nombreCompletoCliente(), 150));
        return customer;
    }

    private String toMontoTexto(BigDecimal monto) {
        return monto.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}