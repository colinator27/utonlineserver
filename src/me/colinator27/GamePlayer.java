package me.colinator27;

import java.net.InetAddress;
import java.util.UUID;

/**
 * Data for individual sessions/players
 */
public class GamePlayer
{
    /**
     * The public ID of the player
     */
    public final int id;
    
    /**
     * The internal ID of the player
     */
    public final UUID uuid;

    /**
     * The last time (in ms) of a move packet from this player being processed
     */
    public long lastMovePacketTime = -1;

    /**
     * The last time (in ms) of any packet from this player being processed
     */
    public long lastPacketTime;

    /**
     * The last time (in ms) of a change room packet from this player being processed
     */
    public long lastRoomChangeTime = -1;

    /**
     * The current GameMaker room index of the player (if visible)
     */
    public int room = -1;

    /**
     * The current sprite index of the player
     */
    public int spriteIndex = 1088;

    /**
     * The current image index of the player
     */
    public int imageIndex = 0;

    /**
     * The current X coordinate of the player
     */
    public float x = 0f;

    /**
     * The current Y coordinate of the player
     */
    public float y = 0f;

    /**
     * The current IP address of the player
     */
    public InetAddress connAddress;

    /**
     * The current port of the player
     */
    public int connPort;

    /**
     * Initialize a new player object
     * @param connAddress   the current IP address of the player
     * @param connPort      the current port of the player
     * @param id            the public ID of the player
     * @param now           the current time, aka when the login packet was processed
     */
    public GamePlayer(InetAddress connAddress, int connPort, UUID uuid, int id, long now)
    {
        this.connAddress = connAddress;
        this.connPort = connPort;
        this.uuid = uuid;
        this.id = id;
        
        lastPacketTime = now;
    }
}
