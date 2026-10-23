package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero;

import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.Texto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.TeamCodeDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.ThirdCustomerDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.ThirdPaymentRequestDto;

import java.time.ZoneId;

/** HidroGest → {@code ThirdPaymentRequest} (contrato provisional). */
public class TerceroMapper {

    private final String teamCode;
    private final ZoneId zonaFechaPago;

    public TerceroMapper(String teamCode, ZoneId zonaFechaPago) {
        this.teamCode = teamCode;
        this.zonaFechaPago = zonaFechaPago;
    }

    public ThirdPaymentRequestDto toRequest(Payment payment) {
        ThirdCustomerDto customer = new ThirdCustomerDto();
        customer.setCode(payment.codigoCliente());
        customer.setName(Texto.recortar(payment.nombreCliente(), 150));

        ThirdPaymentRequestDto request = new ThirdPaymentRequestDto();
        request.setTeamCode(TeamCodeDto.fromValue(teamCode));
        request.setReference(payment.numeroRecibo());
        request.setCustomer(customer);
        request.setAmount(payment.montoTexto());
        request.setCurrency(ThirdPaymentRequestDto.CurrencyEnum.GTQ);
        request.setPaidAt(payment.fechaPagoEn(zonaFechaPago));
        request.setMethod(payment.metodo().esTarjeta()
                ? ThirdPaymentRequestDto.MethodEnum.CARD
                : ThirdPaymentRequestDto.MethodEnum.CASH);
        request.setPeriod(payment.periodo().toString());
        return request;
    }
}
