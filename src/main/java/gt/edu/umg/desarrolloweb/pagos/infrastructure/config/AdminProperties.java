package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credenciales del endpoint {@code /admin} y de las métricas ({@code admin.*}). Si la contraseña
 * está vacía, esas rutas quedan cerradas para todos.
 */
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        @DefaultValue("admin") String usuario,
        @DefaultValue("") String password
) {
    public boolean habilitado() {
        return !password.isBlank();
    }

    @Override
    public String toString() {
        return "AdminProperties[usuario=" + usuario + ", password=<oculta>]";
    }
}
