package gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma un {@code UpdateExpression} con nombres y valores parametrizados. Un valor {@code null}
 * se convierte en {@code REMOVE}: DynamoDB no admite cadenas vacías en llaves de índices.
 */
final class UpdateBuilder {

    private final List<String> sets = new ArrayList<>();
    private final List<String> removes = new ArrayList<>();
    private final Map<String, String> names = new HashMap<>();
    private final Map<String, AttributeValue> values = new HashMap<>();
    private String condition;

    UpdateBuilder set(String attribute, AttributeValue value) {
        String name = name(attribute);
        if (value == null) {
            removes.add(name);
        } else {
            String placeholder = ":" + attribute;
            values.put(placeholder, value);
            sets.add(name + " = " + placeholder);
        }
        return this;
    }

    UpdateBuilder remove(String attribute) {
        removes.add(name(attribute));
        return this;
    }

    /** Condición con {@code #atributo} y {@code :valor} ya declarados con {@link #value}/{@link #name}. */
    UpdateBuilder condition(String expression) {
        this.condition = expression;
        return this;
    }

    UpdateBuilder value(String placeholder, AttributeValue value) {
        values.put(placeholder, value);
        return this;
    }

    String name(String attribute) {
        String placeholder = "#" + attribute;
        names.put(placeholder, attribute);
        return placeholder;
    }

    String expression() {
        StringBuilder sb = new StringBuilder();
        if (!sets.isEmpty()) {
            sb.append("SET ").append(String.join(", ", sets));
        }
        if (!removes.isEmpty()) {
            sb.append(sb.isEmpty() ? "" : " ").append("REMOVE ").append(String.join(", ", removes));
        }
        return sb.toString();
    }

    String conditionExpression() {
        return condition;
    }

    Map<String, String> names() {
        return names;
    }

    Map<String, AttributeValue> values() {
        return values;
    }
}
