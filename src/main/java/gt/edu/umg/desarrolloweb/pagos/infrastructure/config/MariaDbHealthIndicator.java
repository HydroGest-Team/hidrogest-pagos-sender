package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MariaDbHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public MariaDbHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Health.up().withDetail("database", "MariaDB").build();
        } catch (Exception e) {
            return Health.down().withDetail("database", "MariaDB").withException(e).build();
        }
    }
}