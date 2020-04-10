package ca.concordia;

public class Config {
    public static final int PKT_PAYLOAD_MAX_LEN = 1013;
    public static final int PKT_MIN_LEN = 11;
    public static final int PKT_MAX_LEN = PKT_MIN_LEN + PKT_PAYLOAD_MAX_LEN;
    public static final int RESPONSE_TIMEOUT_MS = 5000;
}