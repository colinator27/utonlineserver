package me.colinator27.packet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import me.colinator27.Pair;
import me.colinator27.Util;

/** Helper class to parse packet receive buffers */
public class PacketReader {
    private ByteBuffer bb;
    private byte[] data;
    
    private boolean validated;
    private String str;

    /**
     * Initializes a PacketReader, wrapping around a receive buffer
     *
     * @param receive the buffer to wrap around
     */
    public PacketReader(byte[] receive, int len) {
        data = Arrays.copyOfRange(receive, 0, len);
        bb = ByteBuffer.wrap(data);
        bb.order(ByteOrder.LITTLE_ENDIAN);
    }

    public boolean validate() {
    	if(validated) return true;
        if (data.length < 5) return false;
        if (!parseHeader()) return false;

        bb.mark();

        try {
            InboundPacketType type = parseType();
            StringBuilder sb = new StringBuilder();
            Map<String, Object> args = new LinkedHashMap<>();
            sb.append(type);
            sb.append(" ");
            
            switch (type) {
                case HEARTBEAT:
                    {
                        args.put("uuid", getUUID());
                    }
                    break;
                case LOGIN:
                    {
                        bb.reset();
                    }
                    break;
                case PLAYER_CHANGE_ROOM:
                    {
                    	args.put("uuid", getUUID());
                    	args.put("room", getShort());
                        args.put("sprite", getShort());
                        args.put("frame", getShort());
                        args.put("coords", new Pair<Float, Float>(getFloat(), getFloat()));
                    }
                    break;
                case PLAYER_VISUAL_UPDATE:
                    {
                    	args.put("uuid", getUUID());
                        args.put("sprite", getShort());
                        args.put("frame", getShort());
                        args.put("coords", new Pair<Float, Float>(getFloat(), getFloat()));
                    }
                    break;
                default:
                	{
                		byte[] contents = Arrays.copyOfRange(data, 4, data.length);
                		args.put("contents", Util.stringify(contents, contents.length));
                	}
            }
            sb.append(args.entrySet());
            str = sb.toString();
            bb.reset();
        } catch (Throwable e) {
            return false;
        }
        validated = true;
        return true;
    }

    /**
     * Parses the header of a packet, returning whether it is valid
     *
     * @return true if valid, false otherwise
     */
    public boolean parseHeader() {
    	if (bb.capacity() < 5) return false;
        if (bb.get() != 'U') return false;
        if (bb.get() != 'T') return false;
        if (bb.get() != 'O') return false;
        if (bb.get() != PacketBuilder.PROTOCOL_VERSION) return false;
        return true;
    }

    /**
     * Parses the type of the packet, and returns the enum value
     *
     * @return the packet type enum
     */
    public InboundPacketType parseType() {
        return InboundPacketType.fromValue(bb.get());
    }

    /** Returns the next byte in the packet and advances */
    public byte getByte() {
        return bb.get();
    }

    /** Returns the next short in the packet and advances */
    public short getShort() {
        return bb.getShort();
    }

    /** Returns the next int in the packet and advances */
    public int getInt() {
        return bb.getInt();
    }

    /** Returns the next float in the packet and advances */
    public float getFloat() {
        return bb.getFloat();
    }

    /** Returns the next long in the packet and advances */
    public long getLong() {
        return bb.getLong();
    }

    /** Returns the next double in the packet and advances */
    public double getDouble() {
        return bb.getDouble();
    }

    /** Returns the next UUID (128-bit) in the packet and advances */
    public UUID getUUID() {
        long mostSignificantBits = bb.getLong();
        long leastSignificantBits = bb.getLong();
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
    
    @Override
    public String toString() {
    	if(str == null) {
    		if(!this.validate()) {
    			str = Util.stringify(data, data.length);
    		}
    	}
    	return str;
    }
}
