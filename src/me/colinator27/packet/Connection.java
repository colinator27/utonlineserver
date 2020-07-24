package me.colinator27.packet;

import java.net.InetAddress;
import java.util.Objects;

public class Connection {

    public InetAddress address;
    public int port;

    public Connection(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    @Override
    public String toString() {
        return address.toString() + ":" + port;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this ||
                (obj instanceof Connection &&
                        address.equals(((Connection) obj).address) &&
                        port == ((Connection) obj).port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address.hashCode(), port);
    }
}
