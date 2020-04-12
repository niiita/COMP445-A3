package ca.concordia;

public class Config {

    public static final int PACKET_PAYLOAD_MAX_LEN = 1013;
    public static final int PACKET_MIN_LEN = 11;
    public static final int PACKET_MAX_LEN = PACKET_MIN_LEN + PACKET_PAYLOAD_MAX_LEN;
    public static final int RESPONSE_TIMEOUT_MILLIS = 500;

    // Number of time to wait for all data packets to be received
    public static final int ALL_DATA_PACKET_TIMEOUT_MILLIS = 500;
}