package me.colinator27;

/**
 * The various outbound (to client) packet types
 */
public enum OutboundPacketType
{
    SESSION((byte)1),
    HEARTBEAT((byte)2),

    PLAYER_JOIN_ROOM((byte)10),
    PLAYER_LEAVE_ROOM((byte)11),
    PLAYER_VISUAL_UPDATE((byte)12),

    FORCE_TELEPORT((byte)254),
    KICK_MESSAGE((byte)255);

    public final byte id;

    OutboundPacketType(byte id)
    {
        this.id = id;
    }
}