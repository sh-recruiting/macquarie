package fix;

/**
 * Well-known FIX tag numbers. Covers the standard header/trailer and the most
 * common application-level fields across FIX 4.0 – 5.0.
 */
public final class FIXTags {
    private FIXTags() {}

    // ── Standard header ──────────────────────────────────────────────────────
    public static final int BEGIN_STRING        = 8;
    public static final int BODY_LENGTH         = 9;
    public static final int MSG_TYPE            = 35;
    public static final int SENDER_COMP_ID      = 49;
    public static final int TARGET_COMP_ID      = 56;
    public static final int MSG_SEQ_NUM         = 34;
    public static final int SENDING_TIME        = 52;
    public static final int POSS_DUP_FLAG       = 43;
    public static final int POSS_RESEND         = 97;
    public static final int ON_BEHALF_OF        = 115;
    public static final int DELIVER_TO_COMP_ID  = 128;

    public static final int CHECK_SUM           = 10;

    public static final int CL_ORD_ID           = 11;

    public static final int SYMBOL              = 55;

    public static final int SIDE                = 54;
    public static final int ORDER_QTY           = 38;
    public static final int PRICE               = 44;
    public static final int STOP_PX             = 99;

    // ── MsgType values ───────────────────────────────────────────────────────
    public static final String MSG_NEW_ORDER_SINGLE  = "D";
}
