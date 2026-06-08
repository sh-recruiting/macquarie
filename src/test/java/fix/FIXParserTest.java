package fix;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FIXParserTest {

    private static final byte SOH = 0x01;

    /**
     * Builds a FIX message byte array.
     * The pipe character '|' in the input string is replaced by SOH (0x01).
     */
    private static byte[] msg(String pipeDelimited) {
        return pipeDelimited.replace('|', (char) SOH)
                            .getBytes(StandardCharsets.US_ASCII);
    }

    /** Valid NewOrderSingle with correct BodyLength=70 and CheckSum=137. */
    private static final byte[] NEW_ORDER = msg(
            "8=FIX.4.2|9=70|35=D|49=SENDER|56=TARGET|34=1|11=CLORD1|55=AAPL|54=1|38=100|44=123.45|10=137|");

    private FIXParser parser;

    @BeforeEach
    void setUp() {
        parser = new FIXParser();
    }

    // ── Basic parsing ─────────────────────────────────────────────────────────

    @Test
    void parsesBeginString() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertEquals("FIX.4.2", msg.getBeginString());
    }

    @Test
    void parsesBodyLength() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertEquals(70, msg.getBodyLength());
    }

    @Test
    void parsesMsgType() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertEquals("D", msg.getMsgType());
        assertEquals(FIXTags.MSG_NEW_ORDER_SINGLE, msg.getMsgType());
    }

    @Test
    void parsesCheckSum() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertEquals("137", msg.getCheckSum());
    }

    @Test
    void parsesAllFields() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        // 8, 9, 35, 49, 56, 34, 11, 55, 54, 38, 44, 10  →  12 fields
        assertEquals(12, msg.getFields().size());
    }

    // ── Typed accessors ───────────────────────────────────────────────────────

    @Test
    void getString() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertEquals("SENDER", msg.getString(FIXTags.SENDER_COMP_ID));
        assertEquals("TARGET", msg.getString(FIXTags.TARGET_COMP_ID));
        assertEquals("CLORD1", msg.getString(FIXTags.CL_ORD_ID));
        assertEquals("AAPL",   msg.getString(FIXTags.SYMBOL));
    }

    @Test
    void getInt() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertEquals(1,   msg.getInt(FIXTags.MSG_SEQ_NUM));
        assertEquals(100, msg.getInt(FIXTags.ORDER_QTY));
    }

    @Test
    void getChar() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertEquals('1', msg.getChar(FIXTags.SIDE));   // Buy
    }

    @Test
    void getDouble() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertEquals(123.45, msg.getDouble(FIXTags.PRICE), 1e-9);
    }

    @Test
    void getDecimal() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertEquals(new BigDecimal("123.45"), msg.getDecimal(FIXTags.PRICE));
    }

    // ── Presence checks ───────────────────────────────────────────────────────

    @Test
    void hasFieldReturnsTrueForPresentTag() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertTrue(msg.hasField(FIXTags.SYMBOL));
    }

    @Test
    void hasFieldReturnsFalseForAbsentTag() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertFalse(msg.hasField(FIXTags.STOP_PX));
    }

    @Test
    void getOptionalReturnsPresentValue() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertTrue(msg.getOptional(FIXTags.SYMBOL).isPresent());
        assertEquals("AAPL", msg.getOptional(FIXTags.SYMBOL).get());
    }

    @Test
    void getOptionalReturnsEmptyForAbsentTag() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertFalse(msg.getOptional(FIXTags.STOP_PX).isPresent());
    }

    @Test
    void getStringThrowsForAbsentTag() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertThrows(RuntimeException.class, () -> msg.getString(FIXTags.STOP_PX));
    }

    // ── Date / time ───────────────────────────────────────────────────────────

    @Test
    void getDate() {
        byte[] data = msg("8=FIX.4.2|9=17|35=0|52=20240315|10=XXX|");
        // Checksum is wrong (XXX) but we're only testing date parsing, not validate()
        FIXMessage msg = parser.parse(data);
        assertEquals(LocalDate.of(2024, 3, 15), msg.getDate(FIXTags.SENDING_TIME));
    }

    @Test
    void getTimestamp() {
        byte[] data = msg("8=FIX.4.2|9=25|35=0|52=20240315-14:30:00|10=XXX|");
        FIXMessage msg = parser.parse(data);
        assertEquals(LocalDateTime.of(2024, 3, 15, 14, 30, 0),
                     msg.getTimestamp(FIXTags.SENDING_TIME));
    }

    // ── Boolean ───────────────────────────────────────────────────────────────

    @Test
    void getBoolean() {
        byte[] data = msg("8=FIX.4.2|9=13|35=0|43=Y|97=N|10=XXX|");
        FIXMessage msg = parser.parse(data);
        assertTrue(msg.getBoolean(FIXTags.POSS_DUP_FLAG));
        assertFalse(msg.getBoolean(FIXTags.POSS_RESEND));
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void validatePassesForCorrectMessage() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertDoesNotThrow(msg::validate);
    }

    @Test
    void validateFailsForWrongCheckSum() {
        byte[] bad = msg("8=FIX.4.2|9=70|35=D|49=SENDER|56=TARGET|34=1|11=CLORD1|55=AAPL|54=1|38=100|44=123.45|10=999|");
        FIXMessage msg = parser.parse(bad);
        RuntimeException ex = assertThrows(RuntimeException.class, msg::validate);
        assertTrue(ex.getMessage().contains("CheckSum mismatch"));
    }

    @Test
    void validateFailsForWrongBodyLength() {
        byte[] bad = msg("8=FIX.4.2|9=99|35=D|49=SENDER|56=TARGET|34=1|11=CLORD1|55=AAPL|54=1|38=100|44=123.45|10=137|");
        FIXMessage msg = parser.parse(bad);
        RuntimeException ex = assertThrows(RuntimeException.class, msg::validate);
        assertTrue(ex.getMessage().contains("BodyLength mismatch"));
    }

    // ── Message length / buffer framing ───────────────────────────────────────

    @Test
    void messageLengthMatchesActualBytes() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertEquals(NEW_ORDER.length, msg.getMessageLength());
    }

    @Test
    void parseFromOffset() {
        // Prepend 5 junk bytes then verify offset-based parsing
        byte[] junk = "XXXXX".getBytes(StandardCharsets.US_ASCII);
        byte[] buf  = new byte[junk.length + NEW_ORDER.length];
        System.arraycopy(junk,      0, buf, 0,           junk.length);
        System.arraycopy(NEW_ORDER, 0, buf, junk.length, NEW_ORDER.length);

        FIXMessage msg = parser.parse(buf, junk.length, NEW_ORDER.length);
        assertEquals("FIX.4.2", msg.getBeginString());
        assertEquals("D",       msg.getMsgType());
        assertEquals(NEW_ORDER.length, msg.getMessageLength());
    }

    // ── Multi-message buffer ──────────────────────────────────────────────────

    @Test
    void parseAllReturnsMultipleMessages() {
        byte[] two = new byte[NEW_ORDER.length * 2];
        System.arraycopy(NEW_ORDER, 0, two, 0,                NEW_ORDER.length);
        System.arraycopy(NEW_ORDER, 0, two, NEW_ORDER.length, NEW_ORDER.length);

        List<FIXMessage> msgs = parser.parseAll(two);
        assertEquals(2, msgs.size());
        msgs.forEach(m -> assertEquals("D", m.getMsgType()));
    }

    // ── Repeating groups (same tag appears more than once) ───────────────────

    @Test
    void getAllFieldsHandlesRepeatingTag() {
        // Two hops for DeliverTo; tag 128 appears twice
        byte[] data = msg("8=FIX.4.2|9=20|35=0|128=HOP1|128=HOP2|10=XXX|");
        FIXMessage msg = parser.parse(data);

        List<FIXField> hops = msg.getAllFields(FIXTags.DELIVER_TO_COMP_ID);
        assertEquals(2, hops.size());
        assertEquals("HOP1", hops.get(0).getValue());
        assertEquals("HOP2", hops.get(1).getValue());

        // getField returns the first occurrence
        assertEquals("HOP1", msg.getString(FIXTags.DELIVER_TO_COMP_ID));
    }

    // ── Raw field access ──────────────────────────────────────────────────────

    @Test
    void getFieldReturnsNullForAbsentTag() {
        FIXMessage msg = parser.parse(NEW_ORDER);
        assertNull(msg.getField(FIXTags.STOP_PX));
    }

    @Test
    void rawValueMatchesStringValue() {
        FIXMessage msg    = parser.parse(NEW_ORDER);
        FIXField   symbol = msg.getField(FIXTags.SYMBOL);
        assertNotNull(symbol);
        byte[] raw = symbol.getRawValue();
        assertEquals("AAPL", new String(raw, StandardCharsets.US_ASCII));
    }

    // ── toString ─────────────────────────────────────────────────────────────

    @Test
    void toStringUsesPipeDelimiter() {
        FIXMessage msg  = parser.parse(NEW_ORDER);
        String     repr = msg.toString();
        assertTrue(repr.startsWith("8=FIX.4.2|9=70|35=D|"));
        assertTrue(repr.endsWith("|10=137"));
    }
}
