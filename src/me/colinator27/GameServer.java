package me.colinator27;

import javafx.util.Pair;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Class to handle connections for a specific port on a separate thread
 */
public class GameServer
{
    public final ServerProperties properties;

    private static List<Integer> usedPorts = new ArrayList<>();

    private Log LOG;
    private volatile boolean running;
    private DatagramSocket socket;
    private Thread thread;

    /**
     * The currently-occupied IDs of players
     */
    private HashSet<Integer> playerIDs;

    /**
     * The currently-connected addresses
     * Only populated if properties.disallowSameIP is true
     */
    private HashMap<InetAddress, Pair<Long, UUID>> connectionsIP;

    /**
     * The currently-connected sessions
     */
    private HashMap<UUID, GamePlayer> sessions;

    /**
     * The players in every room in the game
     */
    private List<List<GamePlayer>> rooms;

    public boolean isRunning()
    {
        return running;
    }

    /**
     * Routine to stop this server
     */
    public void stop()
    {
        LOG.logger.info("Shutting down");
        running = false;
    }

    /**
     * Initialize a new server with properties
     * @param properties    the properties to use
     */
    public GameServer(ServerProperties properties)
    {
        this.properties = properties;

        if (usedPorts.contains(properties.port))
            return;
        else
            usedPorts.add(properties.port);
        LOG = new Log("s" + properties.port);
        LOG.logger.info("Server opening on port " + properties.port);

        // Initialize socket
        try
        {
            socket = new DatagramSocket(properties.port);
        } catch (Exception e)
        {
            LOG.logger.log(Level.SEVERE, e.getMessage(), e);
            return;
        }

        LOG.logger.info("Starting server thread");

        running = true;

        // Start server thread
        thread = new Thread(() ->
        {
            LOG.logger.info("In server thread");
            run();
            LOG.logger.info("End of server thread");
            running = false;
        }){{start();}};
    }

    /**
     * Sends a packet to an address at a port (using a PacketBuilder)
     */
    private void sendPacket(InetAddress address, int port, PacketBuilder pb)
    {
        DatagramPacket outPacket = new DatagramPacket(pb.send, pb.getSize(), address, port);
        try
        {
            socket.send(outPacket);
        } catch (IOException e)
        {
            LOG.logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    /**
     * Sends a packet response to an inbound packet (using a PacketBuilder)
     */
    private void sendPacket(DatagramPacket inPacket, PacketBuilder pb)
    {
        sendPacket(inPacket.getAddress(), inPacket.getPort(), pb);
    }

    /**
     * Sends a kick message packet response to an inbound packet
     */
    private void sendKickMessagePacket(DatagramPacket inPacket, byte[] send, String message)
    {
        sendPacket(inPacket, new PacketBuilder(OutboundPacketType.KICK_MESSAGE, send).addString(message));
    }

    /**
     * Handles removing a player from a room
     * @param player    the player to remove
     * @param send      the send buffer
     */
    private void playerLeaveRoom(GamePlayer player, byte[] send)
    {
        List<GamePlayer> room = rooms.get(player.room);
        room.remove(player);

        PacketBuilder pb = new PacketBuilder(OutboundPacketType.PLAYER_LEAVE_ROOM, send)
                .addInt(player.room).addInt(player.id);

        for (GamePlayer other : room)
            sendPacket(other.connAddress, other.connPort, pb);
    }

    /**
     * Handles kicking a player from the server
     * @param uuid              the UUID of the player
     * @param player            the player's object
     * @param inPacket          most recent inbound packet from the player
     * @param send              the send buffer
     * @param message           (optional) a kick message to send to the player
     * @param removeSession     if true, removes the session of the player directly (it should be done outside if false)
     */
    private void kickPlayer(UUID uuid, GamePlayer player, DatagramPacket inPacket, byte[] send, String message, boolean removeSession)
    {
        if (player.room != -1)
            playerLeaveRoom(player, send);

        if (removeSession)
            sessions.remove(uuid);
        playerIDs.remove(player.id);
        if (message != null)
            sendKickMessagePacket(inPacket, send, message);
    }

    /**
     * Resets a player's position to their last recorded one
     * @param player    the player to reset
     * @param inPacket  most recent inbound packet from the player
     * @param send      the send buffer
     */
    private void resetPlayer(GamePlayer player, DatagramPacket inPacket, byte[] send)
    {
        sendPacket(inPacket, new PacketBuilder(OutboundPacketType.FORCE_TELEPORT, send)
                .addFloat(player.x).addFloat(player.y));
    }

    /**
     * Validates visuals and movement from a player, supplied its information and its latest packet in case of error
     * @return  true if not kicked, false if kicked
     */
    private boolean validatePlayerVisuals(UUID uuid, GamePlayer player, DatagramPacket inPacket, byte[] send, long now, int spriteIndex, int imageIndex, float x, float y)
    {
        if (!properties.testingMode)
        {
            if (player.spriteIndex < 1088 || (player.spriteIndex > 1139 && (player.spriteIndex < 2373 || (player.spriteIndex > 2376 && player.spriteIndex != 2517))) ||
                    player.imageIndex < 0 || player.imageIndex > 10)
            {
                LOG.logger.info("Player ID " + player.id + " from " + inPacket.getAddress().toString() + " kicked for invalid visuals (" + player.spriteIndex + "," + player.imageIndex + ")");
                kickPlayer(uuid, player, inPacket, send, "Kicked for invalid visuals (may be a bug)", true);
                return false;
            }
        }

        if (player.lastMovePacketTime != -1)
        {
            float elapsedFrames = ((now - player.lastMovePacketTime) / 1000f) * 30f;
            if (Math.abs(x - player.x) > elapsedFrames * 6f ||
                Math.abs(y - player.y) > elapsedFrames * 6f)
            {
                if (properties.kickBadMovement)
                {
                    LOG.logger.info("Player ID " + player.id + " from " + inPacket.getAddress().toString() + " kicked for invalid movement");
                    kickPlayer(uuid, player, inPacket, send, "Kicked for invalid movement (may be a bug)", true);
                    return false;
                } else
                {
                    resetPlayer(player, inPacket, send);
                    return true;
                }
            }
        }

        player.spriteIndex = spriteIndex;
        player.imageIndex = imageIndex;
        player.x = x;
        player.y = y;

        return true;
    }

    /**
     * The main server routine
     */
    private void run()
    {
        // Initialize player structures
        playerIDs = new HashSet<>();
        connectionsIP = new HashMap<>();
        sessions = new HashMap<>();

        // Initialize all rooms
        rooms = new ArrayList<>();
        for (int i = 0; i < 336; i++)
            rooms.add(new ArrayList<>());

        // Initialize the buffers (up to 4KB packets)
        byte[] receive = new byte[4096];
        byte[] send = new byte[4096];
        PacketBuilder.fillHeader(send);

        while (running)
        {
            // Fill the receiving buffer with 0
            Arrays.fill(receive, (byte)0);

            DatagramPacket packet = new DatagramPacket(receive, receive.length);
            try
            {
                socket.receive(packet);
            } catch (IOException e)
            {
                LOG.logger.log(Level.WARNING, e.getMessage(), e);
                continue;
            }

            PacketReader pr = new PacketReader(receive);

            // Ensure valid header
            if (!pr.parseHeader())
                continue;

            long now = System.currentTimeMillis();

            // Switch to a new log every hour
            if (now - LOG.lastInstantiation >= 60*60*1000)
                LOG.instantiateLogger();

            if (properties.disallowSameIP)
            {
                // Remove connections that haven't sent a packet in over 4 seconds
                for (Iterator<Map.Entry<InetAddress, Pair<Long, UUID>>> it = connectionsIP.entrySet().iterator(); it.hasNext(); )
                {
                    Map.Entry<InetAddress, Pair<Long, UUID>> e = it.next();
                    if (now - e.getValue().getKey() > 4000)
                    {
                        LOG.logger.info("Killed old connection from " + packet.getAddress().toString());
                        it.remove();
                        UUID uuid = e.getValue().getValue();
                        kickPlayer(uuid, sessions.get(uuid), packet, send, null, true);
                    }
                }
            } else
            {
                // Remove sessions that haven't sent a packet in over 4 seconds
                for (Iterator<Map.Entry<UUID, GamePlayer>> it = sessions.entrySet().iterator(); it.hasNext(); )
                {
                    Map.Entry<UUID, GamePlayer> e = it.next();
                    GamePlayer p = e.getValue();
                    if (now - p.lastPacketTime > 4000)
                    {
                        LOG.logger.info("Killed old session from " + packet.getAddress().toString() + ", ID " + p.id);
                        kickPlayer(e.getKey(), p, packet, send, null, false);
                        it.remove();
                    }
                }
            }

            // Handle the packets by type
            switch (pr.parseType())
            {
                case LOGIN:
                {
                    if (properties.disallowSameIP && connectionsIP.containsKey(packet.getAddress()))
                    {
                        LOG.logger.warning("Denied login packet from " + packet.getAddress().toString() + " (same IPs disallowed)");
                        break;
                    }

                    if (playerIDs.size() >= properties.maxPlayers)
                    {
                        LOG.logger.warning("Denied login packet from " + packet.getAddress().toString() + " (max players reached)");
                        sendKickMessagePacket(packet, send, "Cannot join this server; it is at a maximum capacity of " + properties.maxPlayers + " players.");
                        break;
                    }

                    // Choose private UUID
                    UUID uuid = UUID.randomUUID();

                    // Choose public ID
                    int id;
                    for (id = 0; id < Integer.MAX_VALUE; id++)
                        if (!playerIDs.contains(id))
                            break;
                    playerIDs.add(id);

                    // Add session
                    GamePlayer player = new GamePlayer(packet.getAddress(), packet.getPort(), id, now);
                    sessions.put(uuid, player);
                    if (properties.disallowSameIP)
                        connectionsIP.put(packet.getAddress(), new Pair<>(now, uuid));

                    // Send ID/UUID response
                    sendPacket(packet, new PacketBuilder(OutboundPacketType.SESSION, send).addInt(id).addUUID(uuid));

                    LOG.logger.info("Login packet from " + packet.getAddress().toString() + ", assigned ID " + id);
                    break;
                }

                case HEARTBEAT:
                {
                    if (properties.disallowSameIP && !connectionsIP.containsKey(packet.getAddress()))
                        break;

                    UUID uuid = pr.getUUID();
                    GamePlayer p = sessions.get(uuid);
                    if (p != null)
                    {
                        p.connAddress = packet.getAddress();
                        p.connPort = packet.getPort();

                        if (properties.disallowSameIP)
                            connectionsIP.put(packet.getAddress(), new Pair<>(now, uuid));
                        p.lastPacketTime = now;

                        sendPacket(packet, new PacketBuilder(OutboundPacketType.HEARTBEAT, send));
                    }
                    break;
                }

                case PLAYER_CHANGE_ROOM:
                {
                    if (properties.disallowSameIP && !connectionsIP.containsKey(packet.getAddress()))
                        break;

                    UUID uuid = pr.getUUID();
                    GamePlayer p = sessions.get(uuid);
                    if (p != null)
                    {
                        p.connAddress = packet.getAddress();
                        p.connPort = packet.getPort();

                        // Make the player leave the room they're already in
                        if (p.room != -1)
                            playerLeaveRoom(p, send);

                        // Verify target room
                        int targetRoom = pr.getShort();
                        if (targetRoom < -1 || targetRoom >= 336)
                            targetRoom = -1;
                        p.room = targetRoom;

                        // Parse initial visuals
                        int spriteIndex = pr.getShort();
                        int imageIndex = pr.getShort();
                        float x = pr.getFloat();
                        float y = pr.getFloat();

                        // Validate visuals
                        p.lastMovePacketTime = -1;
                        if (!validatePlayerVisuals(uuid, p, packet, send, now, spriteIndex, imageIndex, x, y))
                            break;
                        p.lastPacketTime = now;

                        // Ensure room changes aren't too frequent
                        if (p.lastRoomChangeTime != -1)
                        {
                            if (now - p.lastRoomChangeTime < 200)
                            {
                                LOG.logger.info("Player ID " + p.id + " from " + packet.getAddress().toString() + " kicked for room changing");
                                kickPlayer(uuid, p, packet, send, "Kicked for changing rooms too fast (may be a bug)", true);
                                break;
                            }
                        }
                        p.lastRoomChangeTime = now;

                        if (p.room != -1)
                        {
                            // Send join packet to other players in the room
                            PacketBuilder pb = new PacketBuilder(OutboundPacketType.PLAYER_JOIN_ROOM, send)
                                    .addInt(p.room)
                                    .addShort((short)1) // # of players
                                    .addInt(p.id)
                                    .addShort((short)p.spriteIndex)
                                    .addShort((short)p.imageIndex)
                                    .addFloat(p.x)
                                    .addFloat(p.y);
                            List<GamePlayer> others = rooms.get(p.room);
                            for (GamePlayer other : others)
                                sendPacket(other.connAddress, other.connPort, pb);

                            if (others.size() != 0)
                            {
                                // Send players in room packet to this player
                                pb = new PacketBuilder(OutboundPacketType.PLAYER_JOIN_ROOM, send)
                                        .addInt(p.room)
                                        .addShort((short)others.size());
                                for (GamePlayer other : others)
                                {
                                    pb.addInt(other.id)
                                        .addShort((short)other.spriteIndex)
                                        .addShort((short)other.imageIndex)
                                        .addFloat(other.x)
                                        .addFloat(other.y);
                                }

                                sendPacket(packet, pb);
                            }

                            // Actually add player to the room structure
                            rooms.get(p.room).add(p);
                        }
                    }
                    break;
                }

                case PLAYER_VISUAL_UPDATE:
                {
                    if (properties.disallowSameIP && !connectionsIP.containsKey(packet.getAddress()))
                        break;

                    UUID uuid = pr.getUUID();
                    GamePlayer p = sessions.get(uuid);
                    if (p != null)
                    {
                        p.connAddress = packet.getAddress();
                        p.connPort = packet.getPort();

                        // Parse visuals
                        int spriteIndex = pr.getShort();
                        int imageIndex = pr.getShort();
                        float x = pr.getFloat();
                        float y = pr.getFloat();

                        // Validate visuals
                        if (!validatePlayerVisuals(uuid, p, packet, send, now, spriteIndex, imageIndex, x, y))
                            break;
                        p.lastPacketTime = now;
                        p.lastMovePacketTime = now;

                        if (p.room != -1)
                        {
                            // Send visual update packet to other players in the room
                            PacketBuilder pb = new PacketBuilder(OutboundPacketType.PLAYER_VISUAL_UPDATE, send)
                                    .addLong(now)
                                    .addInt(p.room)
                                    .addInt(p.id)
                                    .addShort((short)p.spriteIndex)
                                    .addShort((short)p.imageIndex)
                                    .addFloat(p.x)
                                    .addFloat(p.y);
                            for (GamePlayer other : rooms.get(p.room))
                            {
                                if (other == p)
                                    continue;
                                sendPacket(other.connAddress, other.connPort, pb);
                            }
                        }
                    }
                    break;
                }
            }
        }
    }
}
