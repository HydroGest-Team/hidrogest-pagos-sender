package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco;

import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.BankPaymentRequestDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.MoneyDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.PayerDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.TeamCodeDto;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.Texto;

import java.time.ZoneId;
import java.util.Map;

/** HidroGest → {@code BankPaymentRequest} (sección 7 del plan). */
public class BancoMapper {

    static final String SOURCE_SYSTEM = "oficina-agua-pagos";

    private final String teamCode;
    private final ZoneId zonaFechaPago;

    public BancoMapper(String teamCode, ZoneId zonaFechaPago) {
        this.teamCode = teamCode;
        this.zonaFechaPago = zonaFechaPago;
    }

    public BankPaymentRequestDto toRequest(Payment payment) {
        PayerDto payer = new PayerDto();
        payer.setCustomerCode(payment.codigoCliente());
        payer.setFullName(Texto.recortar(payment.nombreCliente(), 150));
        payer.setTaxId("CF");

        MoneyDto amount = new MoneyDto();
        amount.setValueInCents(payment.montoEnCentavos());
        amount.setCurrency(MoneyDto.CurrencyEnum.GTQ);

        BankPaymentRequestDto request = new BankPaymentRequestDto();
        request.setTeamCode(TeamCodeDto.fromValue(teamCode));
        request.setPayer(payer);
        request.setAmount(amount);
        request.setTransactionDate(payment.fechaPagoEn(zonaFechaPago));
        request.setChannel(payment.metodo().esTarjeta()
                ? BankPaymentRequestDto.ChannelEnum.CARD
                : BankPaymentRequestDto.ChannelEnum.CASH);
        request.setDescription("Servicio de agua " + payment.periodo());
        request.setMetadata(Map.of(
                "sourceSystem", SOURCE_SYSTEM,
                "numeroRecibo", Texto.recortar(payment.numeroRecibo(), 200)));
        return request;
    }
}
