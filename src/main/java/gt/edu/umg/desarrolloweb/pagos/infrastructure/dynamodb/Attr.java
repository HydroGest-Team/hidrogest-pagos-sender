package gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.Map;

/** Conversión entre tipos Java y {@link AttributeValue}; {@code null} entra y sale como {@code null}. */
final class Attr {

    private Attr() {
    }

    static AttributeValue s(String value) {
        return value == null ? null : AttributeValue.fromS(value);
    }

    static AttributeValue n(Number value) {
        return value == null ? null : AttributeValue.fromN(value.toString());
    }

    /** Instantes como epoch millis (N) para que se ordenen bien en el índice. */
    static AttributeValue millis(Instant value) {
        return value == null ? null : AttributeValue.fromN(Long.toString(value.toEpochMilli()));
    }

    static AttributeValue iso(Instant value) {
        return value == null ? null : AttributeValue.fromS(value.toString());
    }

    static String str(Map<String, AttributeValue> item, String name) {
        AttributeValue v = item.get(name);
        return v == null ? null : v.s();
    }

    static Long lng(Map<String, AttributeValue> item, String name) {
        AttributeValue v = item.get(name);
        return v == null || v.n() == null ? null : Long.parseLong(v.n());
    }

    static Integer integer(Map<String, AttributeValue> item, String name) {
        Long v = lng(item, name);
        return v == null ? null : v.intValue();
    }

    static Instant fromMillis(Map<String, AttributeValue> item, String name) {
        Long v = lng(item, name);
        return v == null ? null : Instant.ofEpochMilli(v);
    }

    static Instant fromIso(Map<String, AttributeValue> item, String name) {
        String v = str(item, name);
        return v == null ? null : Instant.parse(v);
    }

    static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
