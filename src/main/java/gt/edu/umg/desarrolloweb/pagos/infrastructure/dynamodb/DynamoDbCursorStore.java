package gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb;

import gt.edu.umg.desarrolloweb.pagos.domain.CursorStore;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/** Cursores en {@code pagos_control}. El valor es la marca de tiempo cruda de la BD de HidroGest. */
@Repository
public class DynamoDbCursorStore implements CursorStore {

    private final DynamoDbClient client;
    private final String table;

    public DynamoDbCursorStore(DynamoDbClient client, DynamoDbTables tables) {
        this.client = client;
        this.table = tables.control();
    }

    @Override
    public Optional<LocalDateTime> get(String nombre) {
        var response = client.getItem(r -> r.tableName(table).key(Map.of("pk", Attr.s(nombre))).consistentRead(true));
        return Optional.ofNullable(response.hasItem() ? Attr.str(response.item(), "valor") : null)
                .map(LocalDateTime::parse);
    }

    @Override
    public void put(String nombre, LocalDateTime valor) {
        client.putItem(r -> r.tableName(table).item(Map.of(
                "pk", Attr.s(nombre),
                "valor", Attr.s(valor.toString()),
                "updatedAt", Attr.iso(Instant.now()))));
    }
}
