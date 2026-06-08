package fix;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


public final class FIXParser {

    private static final byte SOH    = 0x01;
    private static final byte EQUALS = '=';

    private final FIXMessagePool pool = new FIXMessagePool();

    public FIXMessage parse(byte[] data) {
        return parse(data, 0, data.length);
    }

    public FIXMessage parse(byte[] data, int offset, int length) {
        FIXMessage msg = pool.acquireEmpty();
        int end = offset + length;
        int pos = offset;

        while (pos < end) {
            int eqPos = indexOf(data, EQUALS, pos, end);
            if (eqPos < 0) {
                throw new RuntimeException("Malformed FIX message: no '=' found after position " + pos);
            }
            if (eqPos == pos) {
                throw new RuntimeException("Malformed FIX message: empty tag at position " + pos);
            }

            int tag = parseTag(data, pos, eqPos);

            int sohPos = indexOf(data, SOH, eqPos + 1, end);
            if (sohPos < 0) {
                // Tolerate a missing trailing SOH on the very last field
                sohPos = end;
            }

            int valueOffset = eqPos + 1;
            int valueLength = sohPos - valueOffset;
            String value = new String(data, valueOffset, valueLength, StandardCharsets.US_ASCII);
            msg.addField(tag, pos, valueOffset, value);

            pos = sohPos + 1;

            // Tag 10 (CheckSum) always ends the message
            if (tag == FIXTags.CHECK_SUM) {
                break;
            }
        }

        if (msg.getFields().isEmpty()) {
            throw new RuntimeException("No FIX fields found in the supplied byte array");
        }

        msg.seal(data, offset, pos - offset);
        return msg;
    }

    public List<FIXMessage> parseAll(byte[] data) {
        List<FIXMessage> result = new ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            FIXMessage msg = parse(data, offset, data.length - offset);
            result.add(msg);
            int consumed = msg.getMessageLength();
            if (consumed == 0) break; // safety guard against infinite loop
            offset += consumed;
        }
        return result;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static int indexOf(byte[] data, byte target, int from, int end) {
        for (int i = from; i < end; i++) {
            if (data[i] == target) return i;
        }
        return -1;
    }

    private static int parseTag(byte[] data, int from, int to) {
        int value = 0;
        for (int i = from; i < to; i++) {
            byte b = data[i];
            if (b < '0' || b > '9') {
                throw new RuntimeException("Malformed tag number at position " + i + ": unexpected byte " + (b & 0xFF));
            }
            value = value * 10 + (b - '0');
        }
        return value;
    }

    // ── Object pool ───────────────────────────────────────────────────────────

    private static final class FIXMessagePool {

        private static final int SIZE = 10_000;

        private final FIXMessage[] pool = new FIXMessage[SIZE];
        private int next = 0;

        FIXMessagePool() {
            for (int i = 0; i < SIZE; i++) {
                pool[i] = new FIXMessage();
            }
        }

        FIXMessage acquireEmpty() {
            FIXMessage msg = pool[next];
            next = (next + 1) % SIZE;
            msg.clear();
            return msg;
        }
    }
}
