package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.*;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.config.MetricsService;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.config.PagosProperties;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb.DynamoDbAttemptLogAdapter;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.gateway.BancoGateway;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.gateway.ErpGateway;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.gateway.TerceroGateway;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DeliveryProcessor {

    private final PaymentSource paymentSource;
    private final DeliveryStateStore stateStore;
    private final AttemptLogPort attemptLog;
    private final Map<Destino, DestinationGateway> gateways;
    private final PagosProperties properties;
    private final MetricsService metricsService;

    public DeliveryProcessor(PaymentSource paymentSource,
                              DeliveryStateStore stateStore,
                              AttemptLogPort attemptLog,
                              List<DestinationGateway> gateways,
                              PagosProperties properties,
                              MetricsService metricsService) {
        this.paymentSource = paymentSource;
        this.stateStore = stateStore;
        this.attemptLog = attemptLog;
        this.gateways = Map.of(
            Destino.BANCO, gateways.stream().filter(g -> g.destino() == Destino.BANCO).findFirst().orElseThrow(),
            Destino.ERP, gateways.stream().filter(g -> g.destino() == Destino.ERP).findFirst().orElseThrow(),
            Destino.TERCERO, gateways.stream().filter(g -> g.destino() == Destino.TERCERO).findFirst().orElseThrow()
        );
        this.properties = properties;
        this.metricsService = metricsService;
    }

    @RabbitListener(queues = {"pagos.banco", "pagos.erp", "pagos.tercero"})
    public void process(@Payload DeliveryMessage message,
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                         @Header(AmqpHeaders.CHANNEL) com.rabbitmq.client.Channel channel) {
        String correlationId = message.correlationId();
        MDC.put("X-Correlation-Id", correlationId);

        try {
            Destino destino = Destino.valueOf(message.destino());
            DestinationGateway gateway = gateways.get(destino);
            
            Optional<DeliveryState> stateOpt = stateStore.findByPaymentIdAndDestino(message.paymentId(), destino.name());
            if (stateOpt.isEmpty()) {
                ack(channel, deliveryTag);
                return;
            }
            
            DeliveryState state = stateOpt.get();
            
            if (state.isTerminal()) {
                ack(channel, deliveryTag);
                return;
            }

            Instant leaseUntil = Instant.now().plus(properties.getReconciliador().getLease());
            if (!stateStore.claimForProcessing(message.paymentId(), destino.name(), leaseUntil)) {
                ack(channel, deliveryTag);
                return;
            }

            Optional<Payment> paymentOpt = Optional.ofNullable(paymentSource.findById(message.paymentId()));
            if (paymentOpt.isEmpty()) {
                stateStore.markDeadLetter(message.paymentId(), destino.name(), 0, "PAYMENT_NOT_FOUND");
                ack(channel, deliveryTag);
                return;
            }

            Payment payment = paymentOpt.get();
            
            var validation = PaymentValidator.validate(payment, state.getIdempotencyKey(), destino);
            if (!validation.isValid()) {
                stateStore.markDeadLetter(message.paymentId(), destino.name(), 400, validation.code());
                ack(channel, deliveryTag);
                return;
            }

            if ("DESCONOCIDO".equals(state.getEstado()) && gateway.supportsLookup()) {
                Optional<DestinationGateway.Receipt> receipt = gateway.lookup(state.getIdempotencyKey());
                if (receipt.isPresent()) {
                    stateStore.markDelivered(message.paymentId(), destino.name(), receipt.get().receiptId());
                    ack(channel, deliveryTag);
                    return;
                }
            }

            Instant start = Instant.now();
            DestinationGateway.SendResult result = gateway.send(payment);
            long duration = Duration.between(start, Instant.now()).toMillis();

            metricsService.recordLatency(destino.name(), duration);

            attemptLog.save(DynamoDbAttemptLogAdapter.createLog(
                message.paymentId(), destino.name(), result.outcome().name(),
                result.httpStatus(), result.errorCode(), result.correlationId(),
                result.receiptId(), duration
            ));

            handleResult(message.paymentId(), destino.name(), state.getIntentos() + 1, result);
            ack(channel, deliveryTag);

        } catch (Exception e) {
            nack(channel, deliveryTag);
        } finally {
            MDC.clear();
        }
    }

    private void handleResult(Long paymentId, String destino, int attempt, DestinationGateway.SendResult result) {
        metricsService.recordDelivery(destino, result.outcome().name());
        switch (result.outcome()) {
            case DELIVERED -> stateStore.markDelivered(paymentId, destino, result.receiptId());
            case RETRY -> {
                Instant nextAttempt = calculateNextAttempt(attempt, result.httpStatus());
                stateStore.updateForRetry(paymentId, destino, nextAttempt, result.httpStatus(), result.errorCode());
            }
            case UNKNOWN -> stateStore.updateForUnknown(paymentId, destino);
            case DEAD_LETTER -> stateStore.markDeadLetter(paymentId, destino, result.httpStatus(), result.errorCode());
        }
    }

    private Instant calculateNextAttempt(int attempt, int httpStatus) {
        List<String> tiers = properties.getReintentos().getTiers();
        int tierIndex = Math.min(attempt - 1, tiers.size() - 1);
        String tier = tiers.get(tierIndex);
        long millis = parseDuration(tier);
        return Instant.now().plusMillis(millis);
    }

    private long parseDuration(String duration) {
        if (duration.endsWith("s")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 1000;
        } else if (duration.endsWith("m")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 60 * 1000;
        }
        return 5000;
    }

    private void ack(com.rabbitmq.client.Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception ignored) {}
    }

    private void nack(com.rabbitmq.client.Channel channel, long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception ignored) {}
    }
}