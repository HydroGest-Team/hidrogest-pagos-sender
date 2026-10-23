package gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb;

import gt.edu.umg.desarrolloweb.pagos.domain.ControlItem;
import gt.edu.umg.desarrolloweb.pagos.domain.ControlPort;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

import java.time.Instant;
import java.util.Optional;

@Repository
public class DynamoDbControlAdapter implements ControlPort {

    private final DynamoDbTable<ControlItem> table;

    public DynamoDbControlAdapter(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("pagos_control", TableSchema.fromBean(ControlItem.class));
    }

    @Override
    public Optional<Instant> getCursor() {
        ControlItem item = table.getItem(GetItemEnhancedRequest.builder()
            .key(k -> k.partitionValue(ControlItem.pk()))
            .build());
        return Optional.ofNullable(item).map(ControlItem::getCursorValue);
    }

    @Override
    public void updateCursor(Instant cursor) {
        ControlItem item = new ControlItem();
        item.setPk(ControlItem.pk());
        item.setCursorKey(ControlItem.cursorKey());
        item.setCursorValue(cursor);
        item.setUpdatedAt(Instant.now());
        table.updateItem(UpdateItemEnhancedRequest.builder(ControlItem.class)
            .item(item)
            .ignoreNulls(true)
            .build());
    }
}