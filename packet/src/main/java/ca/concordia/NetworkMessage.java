package ca.concordia;

import java.net.InetAddress;
import java.net.SocketAddress;

public class NetworkMessage {
    private String payload;
    private SocketAddress routerAddress;
    private InetAddress peerAddress;
    private int peerPort;

    public NetworkMessage(String payload, SocketAddress routerAddress, InetAddress peerAddress, int peerPort) {
        this.payload = payload;
        this.routerAddress = routerAddress;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
    }

    public String getPayload() {
        return payload;
    }

    public SocketAddress getRouterAddress() {
        return routerAddress;
    }

    public InetAddress getPeerAddress() {
        return peerAddress;
    }

    public int getPeerPort() {
        return peerPort;
    }
}