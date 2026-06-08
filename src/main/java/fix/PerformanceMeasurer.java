package fix;

import java.nio.charset.StandardCharsets;
import java.util.Random;

public final class PerformanceMeasurer {

    private static final char SOH           = 0x01;
    private static final int  MESSAGE_COUNT = 1_000_000;

    private static final String[] SYMBOLS = {"SPCX", "BAC"};
    private static final String[] MSG_TYPES = { "D", "F", "G" };

    public static void main(String[] args) {
        // ── Generation ───────────────────────────────────────────────────────
        System.out.printf("Generating %,d FIX messages...%n", MESSAGE_COUNT);
        long genStart = System.nanoTime();
        byte[] data = generateMessages(MESSAGE_COUNT);
        long genEnd   = System.nanoTime();
        System.out.printf("  Done: %,.1f MB in %.3f s%n%n",
                data.length / 1_000_000.0,
                (genEnd - genStart) / 1e9);

        FIXParser parser = new FIXParser();

        System.out.printf("Parsing %,d messages (timed)...%n", MESSAGE_COUNT);
        long start  = System.nanoTime();
        int  parsed = parseAll(parser, data);
        long end    = System.nanoTime();

        double seconds    = (end - start) / 1e9;
        double msgPerSec  = parsed / seconds;
        double mbPerSec   = (data.length / 1_000_000.0) / seconds;
        double usPerMsg   = (seconds * 1e6) / parsed;

        System.out.printf("  Messages parsed : %,d%n",    parsed);
        System.out.printf("  Elapsed time    : %.3f s%n", seconds);
        System.out.printf("  Throughput      : %,.0f msg/s%n", msgPerSec);
        System.out.printf("  Throughput      : %.1f MB/s%n",   mbPerSec);
        System.out.printf("  Avg per message : %.3f µs%n",     usPerMsg);
    }

    // ── Parsing loop ──────────────────────────────────────────────────────────

    private static int parseAll(FIXParser parser, byte[] data) {
        int offset = 0;
        int count  = 0;
        while (offset < data.length) {
            FIXMessage msg = parser.parse(data, offset, data.length - offset);
            // Touch a field so the JIT cannot eliminate the parse call entirely.
            if (msg.getMsgType() == null) throw new IllegalStateException();
            offset += msg.getMessageLength();
            count++;
        }
        return count;
    }

    // ── Message generation ────────────────────────────────────────────────────

    private static byte[] generateMessages(int count) {
        Random rng = new Random(42); // fixed seed → reproducible results

        // Two-pass: first build each message, then pack into one buffer.
        byte[][] messages = new byte[count][];
        int totalBytes = 0;
        for (int i = 0; i < count; i++) {
            messages[i] = buildMessage(
                    i + 1,
                    MSG_TYPES[i % MSG_TYPES.length],
                    "ORD" + (i + 1),
                    SYMBOLS[rng.nextInt(SYMBOLS.length)],
                    rng.nextBoolean() ? '1' : '2',
                    (rng.nextInt(100) + 1) * 100,
                    formatPrice(10.0 + rng.nextDouble() * 990.0));
            totalBytes += messages[i].length;
        }

        byte[] buf = new byte[totalBytes];
        int pos = 0;
        for (byte[] m : messages) {
            System.arraycopy(m, 0, buf, pos, m.length);
            pos += m.length;
        }
        return buf;
    }

    /**
     * Builds one syntactically valid FIX message with correct BodyLength and CheckSum.
     */
    private static byte[] buildMessage(int seqNum, String msgType, String clOrdId,
                                       String symbol, char side, int qty, String price) {
        // Assemble the body (everything between the tag-9 SOH and the tag-10 field).
        StringBuilder sb = new StringBuilder(160);
        field(sb, "35", msgType);
        field(sb, "49", "SENDER");
        field(sb, "56", "TARGET");
        field(sb, "34", Integer.toString(seqNum));
        field(sb, "52", "20240101-09:30:00.000");
        field(sb, "11", clOrdId);
        field(sb, "55", symbol);
        field(sb, "54", Character.toString(side));
        field(sb, "38", Integer.toString(qty));
        field(sb, "40", "2");
        field(sb, "44", price);
        byte[] bodyBytes = sb.toString().getBytes(StandardCharsets.US_ASCII);

        // Header: BeginString + BodyLength (value = body byte count).
        String headerStr = "8=FIX.4.2" + SOH + "9=" + bodyBytes.length + SOH;
        byte[] headerBytes = headerStr.getBytes(StandardCharsets.US_ASCII);

        // CheckSum = sum of all bytes in header + body, mod 256.
        int sum = 0;
        for (byte b : headerBytes) sum += b & 0xFF;
        for (byte b : bodyBytes)   sum += b & 0xFF;
        byte[] trailerBytes = ("10=" + String.format("%03d", sum % 256) + SOH)
                .getBytes(StandardCharsets.US_ASCII);

        byte[] msg = new byte[headerBytes.length + bodyBytes.length + trailerBytes.length];
        System.arraycopy(headerBytes,  0, msg, 0,                                       headerBytes.length);
        System.arraycopy(bodyBytes,    0, msg, headerBytes.length,                      bodyBytes.length);
        System.arraycopy(trailerBytes, 0, msg, headerBytes.length + bodyBytes.length,   trailerBytes.length);
        return msg;
    }

    private static void field(StringBuilder sb, String tag, String value) {
        sb.append(tag).append('=').append(value).append(SOH);
    }

    /** Formats a double price to two decimal places without String.format overhead. */
    private static String formatPrice(double price) {
        long cents = Math.round(price * 100);
        return (cents / 100) + "." + String.format("%02d", cents % 100);
    }
}
