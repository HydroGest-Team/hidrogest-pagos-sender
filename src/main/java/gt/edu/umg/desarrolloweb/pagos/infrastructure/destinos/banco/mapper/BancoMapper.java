package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.mapper;

import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.BankPaymentRequestDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.MoneyDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.PayerDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.TeamCodeDto;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class BancoMapper {

    public BankPaymentRequestDto toRequest(Payment payment, String teamCode, String idempotencyKey) {
        BankPaymentRequestDto request = new BankPaymentRequestDto();
        request.setTeamCode(TeamCodeDto.fromValue(teamCode));
        request.setPayer(toPayer(payment));
        request.setAmount(toMoney(payment));
        request.setTransactionDate(payment.fechaPagoConZona().atZone(Payment.ZONA_HORARIA).toOffsetDateTime());
        request.setChannel(BankPaymentRequestDto.ChannelEnum.fromValue(payment.canalBanco()));
        request.setDescription(payment.descripcionBanco());
        request.setMetadata(java.util.Map.of("sourceSystem", "oficina-agua-pagos"));
        return request;
    }

    private PayerDto toPayer(Payment payment) {
        PayerDto payer = new PayerDto();
        payer.setCustomerCode(payment.clienteCode());
        payer.setFullName(truncate(payment.nombreCompletoCliente(), 150));
        payer.setTaxId("CF");
        return payer;
    }

    private MoneyDto toMoney(Payment payment) {
        MoneyDto money = new MoneyDto();
        money.setValueInCents(payment.montoEnCentavos());
        money.setCurrency(MoneyDto.CurrencyEnum.GTQ);
        return money;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}