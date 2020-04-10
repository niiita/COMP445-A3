package ca.concordia;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Transporter {

    private static final Logger logger = LoggerFactory.getLogger(Transporter.class);

    public static String transport(String data, SocketAddress routerAddr, InetAddress peerAddress, int peerPort,
            boolean waitForResponse) {
        try (DatagramChannel channel = DatagramChannel.open()) {

            List<Packet> packets = Packet.buildPacketList(0, peerAddress, peerPort, data.getBytes());
            for (Packet p : packets) {
                channel.send(p.toBuffer(), routerAddr);
            }

            if (!waitForResponse) {
                return null;
            }

            // Try to receive a packet within timeout.
            channel.configureBlocking(false);
            Selector selector = Selector.open();
            channel.register(selector, OP_READ);
            logger.info("Waiting {} milliseconds for the response", Config.RESPONSE_TIMEOUT_MS);
            selector.select(Config.RESPONSE_TIMEOUT_MS);

            Set<SelectionKey> keys = selector.selectedKeys();
            if (keys.isEmpty()) {
                return "No response after timeout";
            }

            // We just want a single response.
            ByteBuffer buf = ByteBuffer.allocate(Config.PKT_MAX_LEN);
            SocketAddress router = channel.receive(buf);
            buf.flip();
            Packet resp = Packet.fromBuffer(buf);
            logger.info("Packet: {}", resp);
            logger.info("Router: {}", router);
            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
            keys.clear();
            return payload;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error during response: " + e.getMessage();
        }
    }

    public static NetworkMessage listenForMessage(int port) {
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));
            ByteBuffer buf = ByteBuffer.allocate(Config.PKT_MAX_LEN).order(ByteOrder.BIG_ENDIAN);
            while (true) {
                buf.clear();
                SocketAddress router = channel.receive(buf);
                ;

                // Parse a packet from the received raw data.
                buf.flip();
                Packet packet = Packet.fromBuffer(buf);

                String payload = new String(packet.getPayload(), UTF_8);

                logger.info("Packet: {}", packet);
                logger.info("Payload: {}", payload);
                logger.info("Router: {}", router);

                return new NetworkMessage(payload, router, packet.getPeerAddress(), packet.getPeerPort());
            }
        } catch (Exception e) {
            logger.error("Error receiving message from client: {}", e.getMessage());
            return null;
        }
    }
}