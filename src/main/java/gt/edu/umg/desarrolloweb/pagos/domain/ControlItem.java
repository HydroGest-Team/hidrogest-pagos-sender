package gt.edu.umg.desarrolloweb.pagos.domain;

import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

@DynamoDbBean
public class ControlItem {
    private String pk;
    private String cursorKey;
    private Instant cursorValue;
    private Instant updatedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("pk")
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbAttribute("cursorKey")
    public String getCursorKey() { return cursorKey; }
    public void setCursorKey(String cursorKey) { this.cursorKey = cursorKey; }

    @DynamoDbAttribute("cursorValue")
    public Instant getCursorValue() { return cursorValue; }
    public void setCursorValue(Instant cursorValue) { this.cursorValue = cursorValue; }

    @DynamoDbAttribute("updatedAt")
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static String pk() { return "PRODUCER_CURSOR"; }
    public static String cursorKey() { return "lastProcessedUpdatedAt"; }
}