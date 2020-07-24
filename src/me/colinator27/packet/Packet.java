package me.colinator27.packet;

import java.net.DatagramPacket;
import java.net.InetAddress;

public class Packet {

    private DatagramPacket packet;
    private PacketReader reader;
    private InetAddress address;
    private int port;

    public Packet(InetAddress address, int port, byte[] data, int length) {
        this(new DatagramPacket(data, length, address, port));
    }

    public Packet(InetAddress address, int port, byte[] data) {
        this(address, port, data, data.length);
    }

    public Packet(DatagramPacket packet) {
        this.address = packet.getAddress();
        this.port = packet.getPort();

        this.packet = packet;
    }

    public DatagramPacket getRawPacket() {
        return packet;
    }

    public PacketReader getReader() {
        if (reader == null) {
            reader = new PacketReader(packet.getData(), packet.getLength());
        }
        return reader;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public byte[] getData() {
        return packet.getData();
    }

    public int getLength() {
        return packet.getLength();
    }
}
