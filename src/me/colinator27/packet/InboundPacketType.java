package me.colinator27.packet;

/**
 * The various inbound (from client) packet types
 */
public enum InboundPacketType
{
    LOGIN((byte)1),
    HEARTBEAT((byte)2),

    PLAYER_CHANGE_ROOM((byte)10),
    PLAYER_VISUAL_UPDATE((byte)11);

    public final byte id;

    InboundPacketType(byte id)
    {
        this.id = id;
    }

    public static InboundPacketType fromValue(byte id)
    {
        for (InboundPacketType t : values())
        {
            if (t.id == id)
                return t;
        }
        throw new IllegalArgumentException("no packet type with id " + id);
    }
}