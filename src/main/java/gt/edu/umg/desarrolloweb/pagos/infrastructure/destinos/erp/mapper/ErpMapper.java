package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.mapper;

import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpContribuyenteDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpDocumentoOrigenDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpEquipoDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpLineaDetalleDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpPeriodoDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpRegistroPagoDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;

@Component
public class ErpMapper {

    public ErpRegistroPagoDto toRequest(Payment payment, String teamCode, String idempotencyKey) {
        ErpRegistroPagoDto request = new ErpRegistroPagoDto();
        request.setEquipo(ErpEquipoDto.fromValue(teamCode));
        request.setDocumentoOrigen(toDocumentoOrigen(idempotencyKey, payment));
        request.setContribuyente(toContribuyente(payment));
        request.setPeriodoFiscal(toPeriodo(payment));
        request.setFormaPago(ErpRegistroPagoDto.FormaPagoEnum.fromValue(payment.formaPagoErp()));
        request.setFechaPago(payment.fechaPagoConZona().atZone(Payment.ZONA_HORARIA).toOffsetDateTime());
        request.setTotal(toMontoTexto(payment.monto()));
        request.setDetalle(List.of(toDetalle(payment)));
        request.setObservaciones("Recibo: " + payment.numeroRecibo());
        return request;
    }

    private ErpDocumentoOrigenDto toDocumentoOrigen(String idempotencyKey, Payment payment) {
        ErpDocumentoOrigenDto doc = new ErpDocumentoOrigenDto();
        doc.setTipo(ErpDocumentoOrigenDto.TipoEnum.fromValue(payment.tipoDocumentoErp()));
        doc.setNumero(idempotencyKey);
        doc.setFechaEmision(LocalDate.parse(payment.fechaPagoConZona().toLocalDate().toString()));
        return doc;
    }

    private ErpContribuyenteDto toContribuyente(Payment payment) {
        ErpContribuyenteDto contrib = new ErpContribuyenteDto();
        contrib.setCodigoCliente(payment.clienteCode());
        contrib.setCodigoContador(payment.codigoContador());
        contrib.setNit("CF");
        return contrib;
    }

    private ErpPeriodoDto toPeriodo(Payment payment) {
        ErpPeriodoDto periodo = new ErpPeriodoDto();
        periodo.setAnio(payment.anioPeriodo());
        periodo.setMes(payment.mesPeriodo());
        return periodo;
    }

    private String toMontoTexto(BigDecimal monto) {
        return monto.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }

    private ErpLineaDetalleDto toDetalle(Payment payment) {
        ErpLineaDetalleDto linea = new ErpLineaDetalleDto();
        linea.setRubro(ErpLineaDetalleDto.RubroEnum.fromValue("AGUA_POTABLE"));
        linea.setDescripcion(payment.descripcionDetalleErp());
        linea.setMonto(toMontoTexto(payment.monto()));
        return linea;
    }
}