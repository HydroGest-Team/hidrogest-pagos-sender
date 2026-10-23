package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "aws")
@Validated
public class AwsProperties {
    private String region = "us-east-1";
    private String dynamodbEndpoint;

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getDynamodbEndpoint() { return dynamodbEndpoint; }
    public void setDynamodbEndpoint(String dynamodbEndpoint) { this.dynamodbEndpoint = dynamodbEndpoint; }
}