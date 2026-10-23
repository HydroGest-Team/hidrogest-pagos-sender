package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    PagosProperties.class,
    BancoProperties.class,
    ErpProperties.class,
    TerceroProperties.class,
    AwsProperties.class
})
public class AppConfig {
}