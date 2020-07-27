package me.colinator27;

import java.net.Socket;
import java.util.UUID;

import me.colinator27.packet.PacketHandler;

/** Data for individual sessions/players */
public class GamePlayer {
    /** The public ID of the player */
    public final int id;

    /** The internal ID of the player */
    public final UUID uuid;

    /** The last time (in ms) of a move packet from this player being processed */
    public long lastMovePacketTime = -1;

    /** The last time (in ms) of a change room packet from this player being processed */
    public long lastRoomChangeTime = -1;

    /** The current GameMaker room index of the player (if visible) */
    public int room = -1;

    /** The current sprite index of the player */
    public int spriteIndex = 1088;

    /** The current image index of the player */
    public int imageIndex = 0;

    /** The current X coordinate of the player */
    public float x = 0f;

    /** The current Y coordinate of the player */
    public float y = 0f;

    /** The current connection of the player */
    public Socket socket;
    
    /** The {@link PacketHander} associate with this player */
    public PacketHandler handler;

    /**
     * Initialize a new player object
     *
     * @param connection the current connection of the player
     * @param id the public ID of the player
     * @param now the current time, aka when the login packet was processed
     */
    public GamePlayer(Socket socket, UUID uuid, int id) {
        this.socket = socket;
        this.uuid = uuid;
        this.id = id;
    }
}
