package me.colinator27;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Helper class to parse packet receive buffers
 */
public class PacketReader
{
    private ByteBuffer bb;

    /**
     * Initializes a PacketReader, wrapping around a receive buffer
     *
     * @param receive   the buffer to wrap around
     */
    public PacketReader(byte[] receive)
    {
        bb = ByteBuffer.wrap(receive);
        bb.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Parses the header of a packet, returning whether it is valid
     *
     * @return  true if valid, false otherwise
     */
    public boolean parseHeader()
    {
        if (bb.get() != 'U')
            return false;
        if (bb.get() != 'T')
            return false;
        if (bb.get() != 'O')
            return false;
        if (bb.get() != PacketBuilder.PROTOCOL_VERSION)
            return false;
        return true;
    }

    /**
     * Parses the type of the packet, and returns the enum value
     *
     * @return  the packet type enum
     */
    public InboundPacketType parseType()
    {
        return InboundPacketType.fromValue(bb.get());
    }

    /**
     * Returns the next byte in the packet and advances
     */
    public byte getByte()
    {
        return bb.get();
    }

    /**
     * Returns the next short in the packet and advances
     */
    public short getShort()
    {
        return bb.getShort();
    }

    /**
     * Returns the next int in the packet and advances
     */
    public int getInt()
    {
        return bb.getInt();
    }

    /**
     * Returns the next float in the packet and advances
     */
    public float getFloat()
    {
        return bb.getFloat();
    }

    /**
     * Returns the next long in the packet and advances
     */
    public long getLong()
    {
        return bb.getLong();
    }

    /**
     * Returns the next double in the packet and advances
     */
    public double getDouble()
    {
        return bb.getDouble();
    }

    /**
     * Returns the next UUID (128-bit) in the packet and advances
     */
    public UUID getUUID()
    {
        long mostSignificantBits = bb.getLong();
        long leastSignificantBits = bb.getLong();
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
