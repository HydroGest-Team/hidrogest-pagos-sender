package gt.edu.umg.desarrolloweb.pagos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients(basePackages = "gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos")
@EnableScheduling
public class PagosSenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(PagosSenderApplication.class, args);
    }
}