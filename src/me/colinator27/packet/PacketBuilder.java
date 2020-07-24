package me.colinator27.packet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/** Helper class to fill packet send buffers with data */
public class PacketBuilder {
    /** The protocol version of packets being sent to and from clients */
    public static final byte PROTOCOL_VERSION = 0;

    /** The offset of the packet header plus type */
    public static final int SEND_OFFSET = 5;

    /**
     * Fills the send buffer with the standard packet header bytes
     *
     * @param send the send buffer to fill
     */
    public static void fillHeader(byte[] send) {
        send[0] = 'U';
        send[1] = 'T';
        send[2] = 'O';
        send[3] = PROTOCOL_VERSION;
    }

    public final byte[] send;
    private ByteBuffer bb;
    private int offset;

    /**
     * Initializes a new PacketBuilder to fill the send buffer with information
     *
     * @param type the type of packet to send
     * @param send the send buffer to fill
     */
    public PacketBuilder(OutboundPacketType type, byte[] send) {
        PacketBuilder.fillHeader(send);
        this.send = send;
        bb = ByteBuffer.wrap(send);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        send[4] = type.id;
        this.offset = SEND_OFFSET;
    }

    public PacketBuilder(OutboundPacketType type) {
        this(type, new byte[4096]);
    }

    /** @return the size of the packet data, including the header and type (SEND_OFFSET) */
    public int getSize() {
        return offset;
    }

    /**
     * Writes a byte to the packet, and advances
     *
     * @param val the byte to write
     * @return this PacketBuilder
     */
    public PacketBuilder addByte(byte val) {
        bb.put(offset, val);
        offset++;
        return this;
    }

    /**
     * Writes a short to the packet, and advances
     *
     * @param val the short to write
     * @return this PacketBuilder
     */
    public PacketBuilder addShort(short val) {
        bb.putShort(offset, val);
        offset += 2;
        return this;
    }

    /**
     * Writes an int to the packet, and advances
     *
     * @param val the int to write
     * @return this PacketBuilder
     */
    public PacketBuilder addInt(int val) {
        bb.putInt(offset, val);
        offset += 4;
        return this;
    }

    /**
     * Writes a float to the packet, and advances
     *
     * @param val the float to write
     * @return this PacketBuilder
     */
    public PacketBuilder addFloat(float val) {
        bb.putFloat(offset, val);
        offset += 4;
        return this;
    }

    /**
     * Writes a long to the packet, and advances
     *
     * @param val the long to write
     * @return this PacketBuilder
     */
    public PacketBuilder addLong(long val) {
        bb.putLong(offset, val);
        offset += 8;
        return this;
    }

    /**
     * Writes a double to the packet, and advances
     *
     * @param val the double to write
     * @return this PacketBuilder
     */
    public PacketBuilder addDouble(double val) {
        bb.putDouble(offset, val);
        offset += 8;
        return this;
    }

    /**
     * Writes a null-terminated String to the packet, and advances
     *
     * @param val the String to write
     * @return this PacketBuilder
     */
    public PacketBuilder addString(String val) {
        byte[] buff = val.getBytes();
        System.arraycopy(buff, 0, send, offset, buff.length);
        send[buff.length + offset] = 0;
        offset += buff.length + 1;
        return this;
    }

    /**
     * Writes a UUID (128-bit) to the packet, and advances
     *
     * @param val the UUID to write
     * @return this PacketBuilder
     */
    public PacketBuilder addUUID(UUID val) {
        return addLong(val.getMostSignificantBits()).addLong(val.getLeastSignificantBits());
    }
}
