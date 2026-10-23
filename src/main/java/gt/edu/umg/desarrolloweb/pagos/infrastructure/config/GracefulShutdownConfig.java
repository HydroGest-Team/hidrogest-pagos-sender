package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GracefulShutdownConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> gracefulShutdown() {
        return factory -> factory.addConnectorCustomizers(connector -> 
            connector.setProperty("connectionTimeout", "30000"));
    }
}