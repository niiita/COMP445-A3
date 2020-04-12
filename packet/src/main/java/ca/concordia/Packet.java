package ca.concordia;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Packet represents a simulated network packet. As we don't have unsigned types
 * in Java, we can achieve this by using a larger type.
 */
public class Packet {

    public static final int DATA_PACKET_TYPE = 0;
    public static final int SYN_PACKET_TYPE = 1;
    public static final int SYNACK_PACKET_TYPE = 2;
    public static final int ACK_PACKET_TYPE = 3;
    public static final int PRE_DATA_PACKET_TYPE = 4;
    public static final int PRE_DATA_ACK_PACKET_TYPE = 5;
    public static final int RETRANSMIT_DATA_PACKET_TYPE = 6;
    public static final int GOT_ALL_DATA_PACKET_TYPE = 7;

    private final int type;
    private final long sequenceNumber;
    private final InetAddress peerAddress;
    private final int peerPort;
    private final byte[] payload;

    public Packet(int type, long sequenceNumber, InetAddress peerAddress, int peerPort, byte[] payload) {
        this.type = type;
        this.sequenceNumber = sequenceNumber;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.payload = payload;
    }

    public int getType() {
        return type;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public InetAddress getPeerAddress() {
        return peerAddress;
    }

    public int getPeerPort() {
        return peerPort;
    }

    public byte[] getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return String.format("#%d peer=%s:%d, size=%d", sequenceNumber, peerAddress, peerPort, payload.length);
    }

    /**
     * Writes a raw presentation of the packet to byte buffer. The order of the
     * buffer should be set as BigEndian.
     */
    private void write(ByteBuffer buf) {
        buf.put((byte) type);
        buf.putInt((int) sequenceNumber);
        buf.put(peerAddress.getAddress());
        buf.putShort((short) peerPort);
        buf.put(payload);
    }

    /**
     * Create a byte buffer in BigEndian for the packet. The returned buffer is
     * flipped and ready for get operations.
     */
    public ByteBuffer toBuffer() {
        ByteBuffer buf = ByteBuffer.allocate(Config.PACKET_MAX_LEN).order(ByteOrder.BIG_ENDIAN);
        write(buf);
        buf.flip();
        return buf;
    }

    /**
     * Returns a raw representation of the packet.
     */
    public byte[] toBytes() {
        ByteBuffer buf = toBuffer();
        byte[] raw = new byte[buf.remaining()];
        buf.get(raw);
        return raw;
    }

    /**
     * fromBuffer creates a packet from the given ByteBuffer in BigEndian.
     */
    public static Packet fromBuffer(ByteBuffer buf) throws IOException {
        if (buf.limit() < Config.PACKET_MIN_LEN || buf.limit() > Config.PACKET_MAX_LEN) {
            System.out.println(String.format("Buffer length is: %d", buf.limit()));
            throw new IOException("Invalid length");
        }

        int type = Byte.toUnsignedInt(buf.get());
        long seq = Integer.toUnsignedLong(buf.getInt());
        InetAddress addr = Inet4Address.getByAddress(new byte[] { buf.get(), buf.get(), buf.get(), buf.get() });
        int port = Short.toUnsignedInt(buf.getShort());
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);

        return new Packet(type, seq, addr, port, payload);
    }

    /**
     * fromBytes creates a packet from the given array of bytes.
     */
    public static Packet fromBytes(byte[] bytes) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(Config.PACKET_MAX_LEN).order(ByteOrder.BIG_ENDIAN);
        buf.put(bytes);
        buf.flip();
        return fromBuffer(buf);
    }

    public static List<Packet> buildPacketList(int type, InetAddress peerAddress, int portNumber, byte[] payload) {
        List<Packet> packetList = new ArrayList<Packet>();
        int packetCount = (int) Math.ceil((double) payload.length / Config.PACKET_PAYLOAD_MAX_LEN);

        for (int i = 0; i < packetCount; i++) {
            int endRange = (i + 1) * Config.PACKET_PAYLOAD_MAX_LEN;
            if (i + 1 == packetCount) {
                endRange = payload.length;
            }
            byte[] payloadChunk = Arrays.copyOfRange(payload, i * Config.PACKET_PAYLOAD_MAX_LEN, endRange);
            packetList.add(new Packet(type, i + 1, peerAddress, portNumber, payloadChunk));
        }

        return packetList;
    }

    public static Packet buildPreDataPacket(InetAddress peerAddress, int portNumber, int dataSize) {
        byte[] data = String.format("%d", dataSize).getBytes();
        return new Packet(PRE_DATA_PACKET_TYPE, 44444444, peerAddress, portNumber, data);
    }

    public static Packet buildPreDataAckPacket(InetAddress peerAddress, int portNumber) {
        return new Packet(PRE_DATA_ACK_PACKET_TYPE, 55555555, peerAddress, portNumber, "".getBytes());
    }

    public static Packet buildRetransmitRequestPacket(InetAddress peerAddress, int portNumber, int seqWanted) {
        byte[] data = String.format("%d", seqWanted).getBytes();
        return new Packet(RETRANSMIT_DATA_PACKET_TYPE, 66666666, peerAddress, portNumber, data);
    }

    public static Packet buildGotAllDataPacket(InetAddress peerAddress, int portNumber) {
        return buildHandshakePacket(GOT_ALL_DATA_PACKET_TYPE, peerAddress, portNumber, 77777777);
    }

    public static Packet buildSynPacket(InetAddress peerAddress, int portNumber) {
        return buildHandshakePacket(SYN_PACKET_TYPE, peerAddress, portNumber, 11111111);
    }

    public static Packet buildSynAckPacket(InetAddress peerAddress, int portNumber) {
        return buildHandshakePacket(SYNACK_PACKET_TYPE, peerAddress, portNumber, 22222222);
    }

    public static Packet buildAckPacket(InetAddress peerAddress, int portNumber) {
        return buildHandshakePacket(ACK_PACKET_TYPE, peerAddress, portNumber, 33333333);
    }

    private static Packet buildHandshakePacket(int packetType, InetAddress peerAddress, int portNumber,
            int sequenceNumber) {
        return new Packet(packetType, sequenceNumber, peerAddress, portNumber, "".getBytes());
    }

}