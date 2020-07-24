package me.colinator27.packet;

import java.net.DatagramPacket;

public class Packet {

    private DatagramPacket packet;
    private PacketReader reader;
    private Connection connection;

    public Packet(Connection connection, byte[] data, int length) {
        this(new DatagramPacket(data, length, connection.address, connection.port));
    }

    public Packet(DatagramPacket packet) {
        this.connection = new Connection(packet.getAddress(), packet.getPort());

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

    public Connection getConnection() {
        return connection;
    }

    public byte[] getData() {
        return packet.getData();
    }

    public int getLength() {
        return packet.getLength();
    }
}
