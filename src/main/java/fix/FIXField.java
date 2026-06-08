package fix;

import java.nio.charset.StandardCharsets;

public final class FIXField {

    private int    tag;
    private int    fieldOffset; // position of the first tag-digit in the buffer
    private int    valueOffset; // position of the first value byte (after '=')
    private String value;       // decoded eagerly at parse time

    FIXField(int tag, int fieldOffset, int valueOffset, String value) {
        this.tag         = tag;
        this.fieldOffset = fieldOffset;
        this.valueOffset = valueOffset;
        this.value       = value;
    }

    /** Overwrites all fields so this instance can be reused by the message pool. */
    void reset(int tag, int fieldOffset, int valueOffset, String value) {
        this.tag         = tag;
        this.fieldOffset = fieldOffset;
        this.valueOffset = valueOffset;
        this.value       = value;
    }

    /** The FIX tag number. */
    public int getTag() {
        return tag;
    }

    /** The field value, decoded at parse time. */
    public String getValue() {
        return value;
    }

    /** A copy of the value re-encoded as US-ASCII bytes (does not include the SOH delimiter). */
    public byte[] getRawValue() {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    /** Number of characters in the value. */
    public int getValueLength() {
        return value.length();
    }

    /** Byte position in the original buffer where the tag digits start. */
    int getFieldOffset() {
        return fieldOffset;
    }

    /** Byte position in the original buffer where the value bytes start. */
    int getValueOffset() {
        return valueOffset;
    }

    @Override
    public String toString() {
        return tag + "=" + value;
    }
}
