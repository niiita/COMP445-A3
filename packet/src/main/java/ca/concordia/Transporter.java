package ca.concordia;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Transporter {

    private static final Logger logger = LoggerFactory.getLogger(Transporter.class);

    public static String transmitFromClient(String data, SocketAddress routerAddr, InetAddress peerAddress,
            int peerPort) {
        try (DatagramChannel channel = DatagramChannel.open()) {

            // Begin threeway tcp handshake
            Packet synPacket = Packet.buildSynPacket(peerAddress, peerPort);
            logger.info("Sending syn packet: {}", synPacket);
            channel.send(synPacket.toBuffer(), routerAddr);

            // Try to receive a synack packet within timeout.
            channel.configureBlocking(false);
            Selector selector = Selector.open();
            channel.register(selector, OP_READ);
            logger.info("Waiting {} milliseconds for the synack response", Config.RESPONSE_TIMEOUT_MILLIS);
            selector.select(Config.RESPONSE_TIMEOUT_MILLIS);

            Set<SelectionKey> keys = selector.selectedKeys();
            if (keys.isEmpty()) {
                return "No response after timeout";
            }

            // We just want a single response.
            ByteBuffer buf = ByteBuffer.allocate(Config.PACKET_MAX_LEN);
            SocketAddress router = channel.receive(buf);
            buf.flip();
            Packet synackPacket = Packet.fromBuffer(buf);

            // Validate this is in fact a synack packet
            if (synackPacket.getType() != Packet.SYNACK_PACKET_TYPE) {
                logger.error("Expecting packet with synack type but got {} instead", synackPacket);
                return "";
            }

            // Log synack response to make debugging more sane
            logger.info("Got synack response from host: {}", synackPacket);

            selector.close();
            keys.clear();
            channel.configureBlocking(true);

            // Send Ack
            Packet ackPacket = Packet.buildAckPacket(peerAddress, peerPort);
            logger.info("Sending ack packet: {}", ackPacket);
            channel.send(ackPacket.toBuffer(), routerAddr);

            logger.info("Three-way handshake complete, connection established");

            // This loop will exit once we get an ack back from the data packet. In an ideal
            // world there would also
            // probably be forced to quit eventually by a long enough timeout but hey not a
            // requirement
            while (true) {

                if (selector.isOpen()) {
                    selector.close();
                    keys.clear();
                    channel.configureBlocking(true);
                }

                // Before we send any data we'll tell the server how much data to expect and
                // wait for the ack
                Packet preDataPacket = Packet.buildPreDataPacket(peerAddress, peerPort, data.getBytes().length);
                logger.info("Sending pre-data packet: {}", preDataPacket);
                channel.send(preDataPacket.toBuffer(), routerAddr);

                // wait for predata ack
                channel.configureBlocking(false);
                selector = Selector.open();
                channel.register(selector, OP_READ);
                logger.info("Waiting {} milliseconds for the pre-data-ack response", Config.RESPONSE_TIMEOUT_MILLIS);
                selector.select(Config.RESPONSE_TIMEOUT_MILLIS);

                keys = selector.selectedKeys();
                if (keys.isEmpty()) {
                    logger.info("No response to pre-data packet after timeout, sending again");
                    continue;
                }

                buf.clear();
                channel.receive(buf);
                buf.flip();
                Packet preDataAckPacket = Packet.fromBuffer(buf);
                if (preDataAckPacket.getType() != Packet.PRE_DATA_ACK_PACKET_TYPE) {
                    logger.error("State error - was expecting pre-data-ack packet but got: {}", preDataAckPacket);
                    return "";
                }
                // Done with the loop
                logger.info("Got pre-data-ack packet from server, ready to send data");
                break;
            }

            if (selector.isOpen()) {
                selector.close();
                keys.clear();
                channel.configureBlocking(true);
            }

            List<Packet> reqDataPackets = Packet.buildPacketList(0, peerAddress, peerPort, data.getBytes());
            for (Packet p : reqDataPackets) {
                channel.send(p.toBuffer(), routerAddr);
            }

            // wait for either retransmission requests or final got-all-data ack
            while (true) {

                channel.configureBlocking(false);
                selector = Selector.open();
                channel.register(selector, OP_READ);
                logger.info("Waiting {} milliseconds for retransmission or got-data response",
                        Config.RESPONSE_TIMEOUT_MILLIS);
                selector.select(Config.RESPONSE_TIMEOUT_MILLIS);

                keys = selector.selectedKeys();
                if (keys.isEmpty()) {
                    continue;
                }

                buf.clear();
                channel.receive(buf);
                buf.flip();
                Packet retransOrAck = Packet.fromBuffer(buf);

                if (retransOrAck.getType() == Packet.GOT_ALL_DATA_PACKET_TYPE) {
                    logger.info("Server confirmed it got all data packets - this send is complete.");
                    break;
                } else if (retransOrAck.getType() == Packet.RETRANSMIT_DATA_PACKET_TYPE) {
                    String retransPayload = new String(retransOrAck.getPayload(), UTF_8);
                    Integer seqToResend = Integer.parseInt(retransPayload);

                    if (selector.isOpen()) {
                        selector.close();
                        keys.clear();
                        channel.configureBlocking(true);
                    }

                    // make sure this sequence number isn't out of wack
                    if (seqToResend > reqDataPackets.size() || seqToResend < 1) {
                        logger.error("Sequence number in resend request out of bounds {}", seqToResend);
                        return "";
                    }

                    channel.send(reqDataPackets.get(seqToResend).toBuffer(), router);
                } else {
                    logger.error(
                            "State error - was expecting retransmission request or ack for all data received, got type {} -- {} instead",
                            retransOrAck.getType(), retransOrAck);
                    return "";
                }
            }

            // Start receiving data packets from the server response here

            // wait for pre-data message from server and then ack it
            if (!selector.isOpen()) {
                channel.configureBlocking(false);
                selector = Selector.open();
                channel.register(selector, OP_READ);
            }
            logger.info("Waiting {} milliseconds for the pre-data packet", Config.RESPONSE_TIMEOUT_MILLIS);
            selector.select(Config.RESPONSE_TIMEOUT_MILLIS);

            keys = selector.selectedKeys();
            if (keys.isEmpty()) {
                return "No response after timeout";
            }

            buf.clear();
            channel.receive(buf);
            buf.flip();
            Packet preDataRespPacket = Packet.fromBuffer(buf);

            if (preDataRespPacket.getType() != Packet.PRE_DATA_PACKET_TYPE) {
                logger.error("Was expecting pre-data packet but got type {} -- {}", preDataRespPacket.getType(),
                        preDataRespPacket);
                return "";
            }

            int expectedDataSize = Integer.parseInt(new String(preDataRespPacket.getPayload(), UTF_8));
            logger.info("Got pre-data packet for server response, expecting {} total data size", expectedDataSize);

            if (selector.isOpen()) {
                selector.close();
                keys.clear();
                channel.configureBlocking(true);
            }

            Packet preRespDataAck = Packet.buildPreDataAckPacket(peerAddress, peerPort);
            channel.send(preRespDataAck.toBuffer(), router);

            // OK - Ready to receive data for the response from the server

            long startTime = System.currentTimeMillis();
            Map<Integer, String> dataParts = new HashMap<>();

            while (true) {

                if (System.currentTimeMillis() - startTime > Config.ALL_DATA_PACKET_TIMEOUT_MILLIS) {
                    List<Integer> missingParts = new ArrayList<>();
                    // Which parts are missing
                    for (int i = 1; i == getNumberOfPacketsForDataSize(expectedDataSize); i++) {
                        if (!dataParts.containsKey(i)) {
                            missingParts.add(i);
                        }
                    }

                    for (Integer missingSeq : missingParts) {
                        Packet resendP = Packet.buildRetransmitRequestPacket(peerAddress, peerPort, missingSeq);
                        channel.send(resendP.toBuffer(), router);
                    }
                    startTime = System.currentTimeMillis();
                }

                buf.clear();
                channel.receive(buf);
                buf.flip();

                Packet respDataPart = Packet.fromBuffer(buf);

                String currentPayload = new String(respDataPart.getPayload(), UTF_8);
                Long seqLong = respDataPart.getSequenceNumber(); // conversion

                dataParts.put(seqLong.intValue(), currentPayload);

                // Check if we've got all the parts
                if (dataParts.size() == getNumberOfPacketsForDataSize(expectedDataSize)) {
                    StringBuilder payloadBuilder = new StringBuilder();
                    Set<Integer> keySet = dataParts.keySet();
                    List<Integer> keysList = new ArrayList<>(keySet);
                    Collections.sort(keysList);
                    for (Integer k : keysList) {
                        payloadBuilder.append(dataParts.get(k));
                    }

                    // Return an all-clear data message to the server
                    Packet gotAllDataPacket = Packet.buildGotAllDataPacket(peerAddress, peerPort);
                    channel.send(gotAllDataPacket.toBuffer(), router);

                    // Finally, we can return the full payload
                    logger.info("Got the full response from server");
                    return payloadBuilder.toString();

                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error during response: " + e.getMessage();
        }
    }

    public static void transmitResponseFromServer(DatagramChannel channel, String data, SocketAddress routerAddr,
            InetAddress peerAddress, int peerPort) {
        try {
            Packet preDataPacket = Packet.buildPreDataPacket(peerAddress, peerPort, data.length());
            channel.send(preDataPacket.toBuffer(), routerAddr);

            // Wait for ack
            ByteBuffer buf = ByteBuffer.allocate(Config.PACKET_MAX_LEN).order(ByteOrder.BIG_ENDIAN);

            channel.configureBlocking(false);
            Selector selector = Selector.open();
            channel.register(selector, OP_READ);
            logger.info("Waiting {} milliseconds for the synack response", Config.RESPONSE_TIMEOUT_MILLIS);
            selector.select(Config.RESPONSE_TIMEOUT_MILLIS);

            Set<SelectionKey> keys = selector.selectedKeys();
            if (keys.isEmpty()) {
                logger.error("No response after timeout - was waiting for preData ack - aborting connection");
                return;
            }

            buf.clear();
            channel.receive(buf);
            buf.flip();
            Packet preDataRespPacket = Packet.fromBuffer(buf);

            if (preDataRespPacket.getType() != Packet.PRE_DATA_ACK_PACKET_TYPE) {
                logger.error("Expecting pre-data ack packet, got {} - {}", preDataRespPacket.getType(),
                        preDataRespPacket);
                return;
            }

            if (selector.isOpen()) {
                selector.close();
                keys.clear();
                channel.configureBlocking(true);
            }

            List<Packet> respDataPackets = Packet.buildPacketList(0, peerAddress, peerPort, data.getBytes());
            for (Packet p : respDataPackets) {
                channel.send(p.toBuffer(), routerAddr);
            }

            // wait for either retransmission requests or final got-all-data ack
            while (true) {

                channel.configureBlocking(false);
                selector = Selector.open();
                channel.register(selector, OP_READ);
                logger.info("Waiting {} milliseconds for retransmission or got-data response",
                        Config.RESPONSE_TIMEOUT_MILLIS);
                selector.select(Config.RESPONSE_TIMEOUT_MILLIS);

                keys = selector.selectedKeys();
                if (keys.isEmpty()) {
                    continue;
                }

                buf.clear();
                channel.receive(buf);
                buf.flip();
                Packet retransOrAck = Packet.fromBuffer(buf);

                if (retransOrAck.getType() == Packet.GOT_ALL_DATA_PACKET_TYPE) {
                    logger.info("Server confirmed it got all data packets - this send is complete.");
                    selector.close();
                    break;
                } else if (retransOrAck.getType() == Packet.RETRANSMIT_DATA_PACKET_TYPE) {
                    String retransPayload = new String(retransOrAck.getPayload(), UTF_8);
                    Integer seqToResend = Integer.parseInt(retransPayload);

                    if (selector.isOpen()) {
                        selector.close();
                        keys.clear();
                        channel.configureBlocking(true);
                    }

                    // make sure this sequence number isn't out of wack
                    if (seqToResend > respDataPackets.size() || seqToResend < 1) {
                        logger.error("Sequence number in resend request out of bounds {}", seqToResend);
                        return;
                    }

                    channel.send(respDataPackets.get(seqToResend).toBuffer(), routerAddr);
                } else {
                    logger.error(
                            "State error - was expecting retransmission request or ack for all data received, got type {} -- {} instead",
                            retransOrAck.getType(), retransOrAck);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Failed to transmit data from server: {}", e.getMessage());
        }
    }

    public static NetworkMessage listenForMessage(DatagramChannel channel, int port) {
        try {
            channel.bind(new InetSocketAddress(port));
            ByteBuffer buf = ByteBuffer.allocate(Config.PACKET_MAX_LEN).order(ByteOrder.BIG_ENDIAN);

            // State management
            boolean awaitingAck = false;
            boolean connectionEstablished = false;
            boolean expectingData = false;
            int expectedDataSize = 0;

            long dataPacketStartTime = 0;

            // Incoming data
            Map<Integer, String> dataParts = new HashMap<>();

            while (true) {
                buf.clear();
                SocketAddress router = channel.receive(buf);

                // Parse a packet from the received raw data.
                buf.flip();
                Packet packet = Packet.fromBuffer(buf);

                switch (packet.getType()) {
                    case Packet.SYN_PACKET_TYPE:
                        logger.info("Server got SYN packet: {}", packet);
                        if (!awaitingAck && !connectionEstablished) {
                            awaitingAck = true;
                            Packet synAckPacket = Packet.buildSynAckPacket(packet.getPeerAddress(),
                                    packet.getPeerPort());
                            channel.send(synAckPacket.toBuffer(), router);
                        } else {
                            logger.error(
                                    "Server transport in bad state - got unexpected SYN packet.  Aborting connection");
                            return null;
                        }
                        break;
                    case Packet.ACK_PACKET_TYPE:
                        logger.info("Server got ACK packet: {}", packet);
                        if (awaitingAck && !connectionEstablished) {
                            connectionEstablished = true;
                            logger.info("Three-way handshake complete, awaiting data transfer.");
                        } else {
                            logger.error(
                                    "Server transport in bad state - got unexpected ACK packet.  Aborting connection");
                            return null;
                        }
                        break;
                    case Packet.PRE_DATA_PACKET_TYPE:
                        logger.info("Server got pre-data packet: {}", packet);
                        if (connectionEstablished && !expectingData) {
                            // Find out how much data we will expect.
                            String payload = new String(packet.getPayload(), UTF_8);
                            Integer dataSize = Integer.parseInt(payload);
                            if (dataSize == null || dataSize == 0) {
                                logger.error("Invalid pre-data packet payload - not sending ack.");
                                continue;
                            }
                            expectedDataSize = dataSize;
                            expectingData = true;
                            Packet preDataAckPacket = Packet.buildPreDataAckPacket(packet.getPeerAddress(),
                                    packet.getPeerPort());
                            channel.send(preDataAckPacket.toBuffer(), router);
                        } else {
                            logger.error(
                                    "Server transport in bad state - got unexpected pre-data packet.  Aborting connection");
                            return null;
                        }
                        break;
                    case Packet.DATA_PACKET_TYPE:
                        logger.info("Server got DATA packet: {}", packet);
                        if (connectionEstablished && expectingData) {

                            if (dataPacketStartTime == 0) {
                                dataPacketStartTime = System.currentTimeMillis();
                            }

                            String payload = new String(packet.getPayload(), UTF_8);
                            Long seqLong = packet.getSequenceNumber(); // conversion

                            dataParts.put(seqLong.intValue(), payload);

                            if (dataParts.size() < getNumberOfPacketsForDataSize(expectedDataSize)) {
                                if (System.currentTimeMillis()
                                        - dataPacketStartTime > Config.ALL_DATA_PACKET_TIMEOUT_MILLIS) {
                                    // We don't have all the parts and time's up, send resend requests, then reset
                                    // timer

                                    List<Integer> missingParts = new ArrayList<>();
                                    // Which parts are missing
                                    for (int i = 1; i == getNumberOfPacketsForDataSize(expectedDataSize); i++) {
                                        if (!dataParts.containsKey(i)) {
                                            missingParts.add(i);
                                        }
                                    }

                                    for (Integer missingSeq : missingParts) {
                                        Packet resendP = Packet.buildRetransmitRequestPacket(packet.getPeerAddress(),
                                                packet.getPeerPort(), missingSeq);
                                        channel.send(resendP.toBuffer(), router);
                                    }

                                    dataPacketStartTime = System.currentTimeMillis();
                                } else {
                                    continue;
                                }
                            } else {
                                // got all the parts, rebuild the payload!
                                StringBuilder payloadBuilder = new StringBuilder();
                                Set<Integer> keySet = dataParts.keySet();
                                List<Integer> keysList = new ArrayList<>(keySet);
                                Collections.sort(keysList);
                                for (Integer k : keysList) {
                                    payloadBuilder.append(dataParts.get(k));
                                }

                                // Return an all-clear data message to the client
                                Packet gotAllDataPacket = Packet.buildGotAllDataPacket(packet.getPeerAddress(),
                                        packet.getPeerPort());
                                channel.send(gotAllDataPacket.toBuffer(), router);

                                return new NetworkMessage(payloadBuilder.toString(), router, packet.getPeerAddress(),
                                        packet.getPeerPort());
                            }
                        } else {
                            logger.error(
                                    "Server transport in bad state - got unexpected DATA packet.  Aborting connection");
                        }
                        break;

                    default:
                        logger.error("Got unknown/unexpected packet type: {} -- {}", packet.getType(), packet);
                        return null;
                }
            }
        } catch (Exception e) {
            logger.error("Error receiving message from client: {}", e.getMessage());
            return null;
        }
    }

    public static int getNumberOfPacketsForDataSize(int dataSize) {
        return (int) Math.ceil((double) dataSize / Config.PACKET_PAYLOAD_MAX_LEN);
    }
}