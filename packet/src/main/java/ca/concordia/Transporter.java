package ca.concordia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static java.nio.channels.SelectionKey.OP_READ;

import java.nio.channels.SelectionKey;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public class Transporter {
    private static final Logger logger = LoggerFactory.getLogger(UDPClient.class);

    public static String transport(String req, SocketAddress routerAddr, InetSocketAddress serverAddr) {
        try (DatagramChannel channel = DatagramChannel.open()) {

            System.out.println("with request: " + req);
            // Packet p = new
            // Packet.Builder().setType(0).setSequenceNumber(1L).setPortNumber(serverAddr.getPort())
            // .setPeerAddress(serverAddr.getAddress()).setPayload(req.getBytes()).create();
            List<Packet> packets = Packet.buildPacketList(0, serverAddr.getAddress(), serverAddr.getPort(),
                    req.getBytes());
            for (Packet p : packets) {
                channel.send(p.toBuffer(), routerAddr);
            }

            // logger.info("Sending \"{}\" to router at {}", req, routerAddr);

            // Try to receive a packet within timeout.
            channel.configureBlocking(false);
            Selector selector = Selector.open();
            channel.register(selector, OP_READ);
            logger.info("Waiting {} milliseconds for the response", Config.RESPONSE_TIMEOUT_MS);
            selector.select(Config.RESPONSE_TIMEOUT_MS);

            Set<SelectionKey> keys = selector.selectedKeys();
            if (keys.isEmpty()) {
                logger.error("No response after timeout");
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
            return "Error during response: " + e.getMessage();
        }

    }
}