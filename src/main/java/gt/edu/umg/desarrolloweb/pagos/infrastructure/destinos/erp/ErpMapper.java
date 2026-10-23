package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp;

import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.Texto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpContribuyenteDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpDocumentoOrigenDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpEquipoDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpLineaDetalleDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpPeriodoDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpRegistroPagoDto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * HidroGest → {@code ErpRegistroPago}. Una sola línea de detalle {@code AGUA_POTABLE} con
 * {@code monto = total}, así la suma siempre cuadra. {@code periodo_fiscal} es el período facturado
 * de la lectura, como en el ejemplo del contrato (ver {@code docs/adr/0005-periodo-fiscal-erp.md}).
 */
public class ErpMapper {

    private static final Locale ES = Locale.forLanguageTag("es-GT");

    private final String teamCode;
    private final ZoneId zonaFechaPago;

    public ErpMapper(String teamCode, ZoneId zonaFechaPago) {
        this.teamCode = teamCode;
        this.zonaFechaPago = zonaFechaPago;
    }

    public ErpRegistroPagoDto toRequest(Payment payment, String numeroDocumento) {
        OffsetDateTime fechaPago = payment.fechaPagoEn(zonaFechaPago);

        ErpDocumentoOrigenDto documento = new ErpDocumentoOrigenDto();
        documento.setTipo(payment.metodo().esTarjeta()
                ? ErpDocumentoOrigenDto.TipoEnum.VOUCHER_TARJETA
                : ErpDocumentoOrigenDto.TipoEnum.RECIBO_CAJA);
        documento.setNumero(numeroDocumento);
        documento.setFechaEmision(fechaPago.toLocalDate());

        ErpContribuyenteDto contribuyente = new ErpContribuyenteDto();
        contribuyente.setCodigoCliente(payment.codigoCliente());
        contribuyente.setCodigoContador(Texto.recortar(payment.codigoContador(), 50));
        contribuyente.setNit("CF");

        ErpPeriodoDto periodo = new ErpPeriodoDto();
        periodo.setAnio(payment.periodo().getYear());
        periodo.setMes(payment.periodo().getMonthValue());

        String total = payment.montoTexto();
        ErpLineaDetalleDto linea = new ErpLineaDetalleDto();
        linea.setRubro(ErpLineaDetalleDto.RubroEnum.AGUA_POTABLE);
        linea.setDescripcion(Texto.recortar("Consumo "
                + payment.periodo().getMonth().getDisplayName(TextStyle.FULL, ES) + " " + payment.periodo().getYear(), 120));
        linea.setMonto(total);

        ErpRegistroPagoDto request = new ErpRegistroPagoDto();
        request.setEquipo(ErpEquipoDto.fromValue(teamCode));
        request.setDocumentoOrigen(documento);
        request.setContribuyente(contribuyente);
        request.setPeriodoFiscal(periodo);
        request.setFormaPago(payment.metodo().esTarjeta()
                ? ErpRegistroPagoDto.FormaPagoEnum.TARJETA
                : ErpRegistroPagoDto.FormaPagoEnum.EFECTIVO);
        request.setFechaPago(fechaPago);
        request.setTotal(total);
        request.setDetalle(List.of(linea));
        request.setObservaciones(Texto.recortar("Recibo " + payment.numeroRecibo(), 250));
        return request;
    }
}
