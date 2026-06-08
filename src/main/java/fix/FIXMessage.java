package fix;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public final class FIXMessage {

    // FIX UTC date/time formats
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss[.SSS]");
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss[.SSS]");

    private final List<FIXField>               fields;      // all slots: [0, fieldCount) active, [fieldCount, size) pooled
    private final List<FIXField>               fieldsView;  // live read-only view of the active slice
    private final Map<Integer, FIXField>       firstIndex;  // first occurrence per tag
    private final Map<Integer, List<FIXField>> allIndex;    // all occurrences per tag

    private int    fieldCount; // number of currently active fields
    private byte[] buffer;     // the original input buffer
    private int    msgOffset;  // position in buffer where this message starts
    private int    msgLength;  // total bytes consumed (through the SOH of tag 10)

    FIXMessage() {
        this.fields     = new ArrayList<>(32);
        this.firstIndex = new HashMap<>(64);
        this.allIndex   = new HashMap<>(32);
        // AbstractList backed by the active slice; reflects fieldCount changes without allocation.
        this.fieldsView = new AbstractList<>() {
            @Override public FIXField get(int i) { return fields.get(i); }
            @Override public int      size()     { return fieldCount;    }
        };
    }

    /**
     * Resets the active-field cursor to zero, keeping all {@link FIXField} objects
     * in the backing list so they can be reused by the next round of parsing.
     */
    void clear() {
        fieldCount = 0;
        firstIndex.clear();
        allIndex.clear();
        buffer    = null;
        msgOffset = 0;
        msgLength = 0;
    }

    void addField(int tag, int fieldOffset, int valueOffset, String value) {
        if (fieldCount < fields.size()) {
            fields.get(fieldCount).reset(tag, fieldOffset, valueOffset, value);
        } else {
            fields.add(new FIXField(tag, fieldOffset, valueOffset, value));
        }
        fieldCount++;
    }

    /** Builds the lookup indices and records buffer bounds. Called once after all fields are added. */
    void seal(byte[] buffer, int msgOffset, int msgLength) {
        this.buffer    = buffer;
        this.msgOffset = msgOffset;
        this.msgLength = msgLength;
        for (int i = 0; i < fieldCount; i++) {
            FIXField f = fields.get(i);
            firstIndex.putIfAbsent(f.getTag(), f);
            allIndex.computeIfAbsent(f.getTag(), k -> new ArrayList<>()).add(f);
        }
    }

    // ── Presence ─────────────────────────────────────────────────────────────

    public boolean hasField(int tag) {
        return firstIndex.containsKey(tag);
    }

    // ── Raw field access ─────────────────────────────────────────────────────

    public FIXField getField(int tag) {
        return firstIndex.get(tag);
    }

    public List<FIXField> getAllFields(int tag) {
        return allIndex.getOrDefault(tag, Collections.emptyList());
    }

    public List<FIXField> getFields() {
        return fieldsView;
    }

    // ── Typed access helpers ─────────────────────────────────────────────────

    private FIXField require(int tag) {
        FIXField f = firstIndex.get(tag);
        if (f == null) {
            throw new RuntimeException("Tag " + tag + " is not present in the message");
        }
        return f;
    }

    // ── String ───────────────────────────────────────────────────────────────

    public String getString(int tag) {
        return require(tag).getValue();
    }

    public Optional<String> getOptional(int tag) {
        FIXField f = firstIndex.get(tag);
        return (f == null) ? Optional.empty() : Optional.of(f.getValue());
    }

    public int getInt(int tag) {
        String v = require(tag).getValue();
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Tag " + tag + " value \"" + v + "\" is not a valid integer", e);
        }
    }

    public double getDouble(int tag) {
        String v = require(tag).getValue();
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Tag " + tag + " value \"" + v + "\" is not a valid decimal", e);
        }
    }

    public BigDecimal getDecimal(int tag) {
        String v = require(tag).getValue();
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Tag " + tag + " value \"" + v + "\" is not a valid decimal", e);
        }
    }

    public char getChar(int tag) {
        String v = require(tag).getValue();
        if (v.length() != 1) {
            throw new RuntimeException("Tag " + tag + " value \"" + v + "\" is not a single character");
        }
        return v.charAt(0);
    }

    public boolean getBoolean(int tag) {
        char c = getChar(tag);
        if (c == 'Y') return true;
        if (c == 'N') return false;
        throw new RuntimeException("Tag " + tag + " value '" + c + "' is not a FIX boolean (Y/N)");
    }

    public LocalDate getDate(int tag) {
        String v = require(tag).getValue();
        try {
            return LocalDate.parse(v, DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new RuntimeException("Tag " + tag + " value \"" + v
                    + "\" is not a valid date (YYYYMMDD)", e);
        }
    }

    public LocalDateTime getTimestamp(int tag) {
        String v = require(tag).getValue();
        try {
            return LocalDateTime.parse(v, TS_FMT);
        } catch (DateTimeParseException e) {
            throw new RuntimeException("Tag " + tag + " value \"" + v
                    + "\" is not a valid UTCTimestamp (YYYYMMDD-HH:MM:SS[.sss])", e);
        }
    }

    // ── Convenience accessors ─────────────────────────────────────────────────

    /** Tag 8 — e.g. {@code "FIX.4.2"}. */
    public String getBeginString() { return getString(FIXTags.BEGIN_STRING); }

    /** Tag 9 — declared body length in bytes. */
    public int getBodyLength()     { return getInt(FIXTags.BODY_LENGTH);    }

    /** Tag 35 — message type, e.g. {@code "D"} for NewOrderSingle. */
    public String getMsgType()     { return getString(FIXTags.MSG_TYPE);    }

    /** Tag 10 — checksum as a 3-digit string, e.g. {@code "137"}. */
    public String getCheckSum()    { return getString(FIXTags.CHECK_SUM);   }


    public int getMessageLength()  { return msgLength; }


    public void validate() {
        validateBodyLength();
        validateCheckSum();
    }

    private void validateBodyLength() {
        FIXField blField = require(FIXTags.BODY_LENGTH);
        FIXField csField = require(FIXTags.CHECK_SUM);

        // Body starts at the first byte after "9=<value>\x01"
        int bodyStart = blField.getValueOffset() + blField.getValueLength() + 1;
        // Body ends at (and including) the SOH immediately before "10="
        int bodyEnd   = csField.getFieldOffset(); // exclusive upper bound

        int actual   = bodyEnd - bodyStart;
        int declared = Integer.parseInt(blField.getValue());

        if (actual != declared) {
            throw new RuntimeException("BodyLength mismatch: declared " + declared + " but actual " + actual);
        }
    }

    private void validateCheckSum() {
        FIXField csField = require(FIXTags.CHECK_SUM);

        // Sum every byte from the start of the message through the SOH before "10="
        int sum = 0;
        int end = csField.getFieldOffset();
        for (int i = msgOffset; i < end; i++) {
            sum += buffer[i] & 0xFF;
        }
        int expected = sum % 256;

        int declared;
        try {
            declared = Integer.parseInt(csField.getValue());
        } catch (NumberFormatException e) {
            throw new RuntimeException("CheckSum field (tag 10) is not a valid number: \""
                    + csField.getValue() + "\"", e);
        }

        if (expected != declared) {
            throw new RuntimeException(
                    "CheckSum mismatch: expected " + String.format("%03d", expected)
                    + " but declared " + String.format("%03d", declared));
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (FIXField f : fields) {
            if (sb.length() > 0) sb.append('|');
            sb.append(f.getTag()).append('=').append(f.getValue());
        }
        return sb.toString();
    }
}
