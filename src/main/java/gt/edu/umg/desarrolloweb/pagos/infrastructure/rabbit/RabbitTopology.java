package gt.edu.umg.desarrolloweb.pagos.infrastructure.rabbit;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class RabbitTopology {

    public static final String EXCHANGE_PAGOS = "pagos.exchange";
    public static final String DLX = "pagos.dlx";

    private static final String[] DESTINOS = {"banco", "erp", "tercero"};

    @Value("${pagos.reintentos.tiers:5s,30s,2m,10m}")
    private List<String> retryTiers;

    @Bean
    public DirectExchange pagosExchange() {
        return new DirectExchange(EXCHANGE_PAGOS, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue dlq() {
        return QueueBuilder.durable("pagos.dlq").build();
    }

    @Bean
    public Binding dlqBinding(@Qualifier("dlxExchange") DirectExchange dlxExchange) {
        return BindingBuilder.bind(dlq()).to(dlxExchange).with("pagos.dlq");
    }

    @Bean
    public Queue pagosBancoQueue() {
        return buildMainQueue("pagos.banco");
    }

    @Bean
    public Queue pagosErpQueue() {
        return buildMainQueue("pagos.erp");
    }

    @Bean
    public Queue pagosTerceroQueue() {
        return buildMainQueue("pagos.tercero");
    }

    @Bean
    public Binding pagosBancoBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosBancoQueue()).to(pagosExchange).with("pagos.banco");
    }

    @Bean
    public Binding pagosErpBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosErpQueue()).to(pagosExchange).with("pagos.erp");
    }

    @Bean
    public Binding pagosTerceroBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosTerceroQueue()).to(pagosExchange).with("pagos.tercero");
    }

    private Queue buildMainQueue(String name) {
        return QueueBuilder.durable(name)
            .withArgument("x-dead-letter-exchange", DLX)
            .withArgument("x-dead-letter-routing-key", "pagos.dlq")
            .build();
    }

    // Retry queues for each destination and tier
    @Bean
    public Queue pagosBancoRetry5s() { return buildRetryQueue("pagos.banco", "5s", 0); }
    @Bean
    public Queue pagosBancoRetry30s() { return buildRetryQueue("pagos.banco", "30s", 1); }
    @Bean
    public Queue pagosBancoRetry2m() { return buildRetryQueue("pagos.banco", "2m", 2); }
    @Bean
    public Queue pagosBancoRetry10m() { return buildRetryQueue("pagos.banco", "10m", 3); }

    @Bean
    public Queue pagosErpRetry5s() { return buildRetryQueue("pagos.erp", "5s", 0); }
    @Bean
    public Queue pagosErpRetry30s() { return buildRetryQueue("pagos.erp", "30s", 1); }
    @Bean
    public Queue pagosErpRetry2m() { return buildRetryQueue("pagos.erp", "2m", 2); }
    @Bean
    public Queue pagosErpRetry10m() { return buildRetryQueue("pagos.erp", "10m", 3); }

    @Bean
    public Queue pagosTerceroRetry5s() { return buildRetryQueue("pagos.tercero", "5s", 0); }
    @Bean
    public Queue pagosTerceroRetry30s() { return buildRetryQueue("pagos.tercero", "30s", 1); }
    @Bean
    public Queue pagosTerceroRetry2m() { return buildRetryQueue("pagos.tercero", "2m", 2); }
    @Bean
    public Queue pagosTerceroRetry10m() { return buildRetryQueue("pagos.tercero", "10m", 3); }

    @Bean
    public Binding pagosBancoRetry5sBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosBancoRetry5s()).to(pagosExchange).with("pagos.banco.retry.5s");
    }
    @Bean
    public Binding pagosBancoRetry30sBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosBancoRetry30s()).to(pagosExchange).with("pagos.banco.retry.30s");
    }
    @Bean
    public Binding pagosBancoRetry2mBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosBancoRetry2m()).to(pagosExchange).with("pagos.banco.retry.2min");
    }
    @Bean
    public Binding pagosBancoRetry10mBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosBancoRetry10m()).to(pagosExchange).with("pagos.banco.retry.10min");
    }

    @Bean
    public Binding pagosErpRetry5sBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosErpRetry5s()).to(pagosExchange).with("pagos.erp.retry.5s");
    }
    @Bean
    public Binding pagosErpRetry30sBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosErpRetry30s()).to(pagosExchange).with("pagos.erp.retry.30s");
    }
    @Bean
    public Binding pagosErpRetry2mBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosErpRetry2m()).to(pagosExchange).with("pagos.erp.retry.2min");
    }
    @Bean
    public Binding pagosErpRetry10mBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosErpRetry10m()).to(pagosExchange).with("pagos.erp.retry.10min");
    }

    @Bean
    public Binding pagosTerceroRetry5sBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosTerceroRetry5s()).to(pagosExchange).with("pagos.tercero.retry.5s");
    }
    @Bean
    public Binding pagosTerceroRetry30sBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosTerceroRetry30s()).to(pagosExchange).with("pagos.tercero.retry.30s");
    }
    @Bean
    public Binding pagosTerceroRetry2mBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosTerceroRetry2m()).to(pagosExchange).with("pagos.tercero.retry.2min");
    }
    @Bean
    public Binding pagosTerceroRetry10mBinding(@Qualifier("pagosExchange") DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosTerceroRetry10m()).to(pagosExchange).with("pagos.tercero.retry.10min");
    }

    private Queue buildRetryQueue(String destino, String tier, int tierIndex) {
        String retryQueueName = "pagos." + destino + ".retry." + tier.replace("m", "min").replace("s", "s");
        String nextQueue;
        if (tierIndex + 1 < retryTiers.size()) {
            String nextTier = retryTiers.get(tierIndex + 1);
            nextQueue = "pagos." + destino + ".retry." + nextTier.replace("m", "min").replace("s", "s");
        } else {
            nextQueue = "pagos." + destino;
        }

        long ttl = parseDuration(tier);

        return QueueBuilder.durable(retryQueueName)
            .withArgument("x-dead-letter-exchange", EXCHANGE_PAGOS)
            .withArgument("x-dead-letter-routing-key", nextQueue)
            .withArgument("x-message-ttl", ttl)
            .build();
    }

    private long parseDuration(String duration) {
        if (duration.endsWith("s")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 1000;
        } else if (duration.endsWith("m")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 60 * 1000;
        }
        return 5000;
    }
}