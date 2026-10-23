package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.DestinationGateway;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Gateways disponibles y lista de destinos activos (según {@code pagos.destinos-activos}). */
@Component
public class DestinationRegistry {

    private final Map<Destino, DestinationGateway> gateways = new EnumMap<>(Destino.class);
    private final List<Destino> activos;

    public DestinationRegistry(List<DestinationGateway> gateways, PagosProperties properties) {
        gateways.forEach(g -> this.gateways.put(g.destino(), g));
        this.activos = properties.destinosActivos();
        List<Destino> sinGateway = activos.stream().filter(d -> !this.gateways.containsKey(d)).toList();
        if (!sinGateway.isEmpty()) {
            throw new IllegalStateException("Destinos activos sin adaptador: " + sinGateway);
        }
    }

    public List<Destino> activos() {
        return activos;
    }

    public Optional<DestinationGateway> gateway(Destino destino) {
        return Optional.ofNullable(gateways.get(destino));
    }
}
