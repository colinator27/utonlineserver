package me.colinator27;

import me.colinator27.packet.OutboundPacketType;
import me.colinator27.packet.PacketBuilder;
import me.colinator27.packet.PacketReader;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/** Class to handle connections for a specific port on a separate thread */
public class OldGameServer {
    public final ServerProperties properties;

    private static List<Integer> usedPorts = new ArrayList<>();

    private Log LOG;
    private volatile boolean running;
    private DatagramSocket socket;

    private ExecutorService executor;

    /** The currently-occupied IDs of players */
    private Set<Integer> playerIDs;

    /** List of ratelimited addresses */
    private Set<InetAddress> ratelimited;

    /** The currently-connected addresses Only populated if properties.disallowSameIP is true */
    private Map<InetAddress, Pair<Long, UUID>> connectionsIP;

    /** Timestamps of packets Used for ratelimiting */
    private Map<InetAddress, List<Long>> timestamps;

    /** The currently-connected sessions */
    private Map<UUID, GamePlayer> sessions;

    /** The players in every room in the game */
    private List<List<GamePlayer>> rooms;

    public Log getLogger() {
        return LOG;
    }

    public Set<Integer> getPlayerIDs() {
        return playerIDs;
    }

    public GamePlayer getPlayer(UUID uuid) {
        return sessions.get(uuid);
    }

    public Map<UUID, GamePlayer> getSessions() {
        return Collections.unmodifiableMap(sessions);
    }

    public Pair<Long, UUID> getConnection(InetAddress address) {
        return connectionsIP.get(address);
    }

    public List<GamePlayer> getPlayersInRoom(int room) {
        return Collections.unmodifiableList(rooms.get(room));
    }

    public void setRoom(GamePlayer player, int room) {
        rooms.get(player.room).remove(player);
        rooms.get(room).add(player);

        player.lastRoomChangeTime = System.currentTimeMillis();
        player.room = room;
    }

    public int generatePlayerID() {
        for (int i = 0; i < properties.maxPlayers; i++) {
            if (!playerIDs.contains(i)) {
                playerIDs.add(i);
                return i;
            }
        }
        throw new IllegalStateException("max number of players reached (" + playerIDs.size() + ")");
    }

    public void releasePlayerID(int id) {
        playerIDs.remove(id);
    }

    public boolean isRunning() {
        return running;
    }

    /** Routine to stop this server */
    public void stop() {
        LOG.logger.info("Shutting down");
        running = false;

        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * Initialize a new server with properties
     *
     * @param properties the properties to use
     */
    public OldGameServer(ServerProperties properties) {
        this.properties = properties;

        if (usedPorts.contains(properties.port)) return;
        else usedPorts.add(properties.port);
        LOG = new Log("s" + properties.port);
        LOG.logger.info("Server opening on port " + properties.port);

        // Initialize socket
        try {
            socket = new DatagramSocket(properties.port);
        } catch (Exception e) {
            LOG.logger.log(Level.SEVERE, e.getMessage(), e);
            return;
        }

        LOG.logger.info("Starting server thread");

        running = true;

        executor = Executors.newSingleThreadExecutor();

        // Start server thread
        executor.execute(
                () -> {
                    LOG.logger.info("In server thread");
                    run();
                    LOG.logger.info("End of server thread");
                    running = false;

                    executor.shutdown();
                });
    }

    /** Sends a packet to an address at a port (using a PacketBuilder) */
    public void sendPacket(InetAddress address, int port, PacketBuilder pb) {
        DatagramPacket outPacket = new DatagramPacket(pb.send, pb.getSize(), address, port);
        try {
            socket.send(outPacket);
        } catch (IOException e) {
            LOG.logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    /** Sends a packet response to an inbound packet (using a PacketBuilder) */
    public void sendPacket(DatagramPacket inPacket, PacketBuilder pb) {
        sendPacket(inPacket.getAddress(), inPacket.getPort(), pb);
    }

    /** Sends a kick message packet response to an inbound packet */
    public void sendKickMessagePacket(DatagramPacket inPacket, byte[] send, String message) {
        sendPacket(
                inPacket,
                new PacketBuilder(OutboundPacketType.KICK_MESSAGE, send).addString(message));
    }

    /**
     * Handles removing a player from a room
     *
     * @param player the player to remove
     * @param send the send buffer
     */
    public void playerLeaveRoom(GamePlayer player, byte[] send) {
        List<GamePlayer> room = rooms.get(player.room);
        room.remove(player);

        PacketBuilder pb =
                new PacketBuilder(OutboundPacketType.PLAYER_LEAVE_ROOM, send)
                        .addInt(player.room)
                        .addInt(player.id);

        for (GamePlayer other : room) sendPacket(other.connAddress, other.connPort, pb);
    }

    /**
     * Handles kicking a player from the server
     *
     * @param uuid the UUID of the player
     * @param player the player's object
     * @param inPacket most recent inbound packet from the player
     * @param send the send buffer
     * @param message (optional) a kick message to send to the player
     * @param removeSession if true, removes the session of the player directly (it should be done
     *     outside if false)
     */
    public void kickPlayer(
            UUID uuid,
            GamePlayer player,
            DatagramPacket inPacket,
            byte[] send,
            String message,
            boolean removeSession) {
        if (player.room != -1) playerLeaveRoom(player, send);

        if (removeSession) sessions.remove(uuid);
        playerIDs.remove(player.id);
        if (message != null) sendKickMessagePacket(inPacket, send, message);
    }

    /**
     * Resets a player's position to their last recorded one
     *
     * @param player the player to reset
     * @param inPacket most recent inbound packet from the player
     * @param send the send buffer
     */
    public void resetPlayer(GamePlayer player, DatagramPacket inPacket, byte[] send) {
        sendPacket(
                inPacket,
                new PacketBuilder(OutboundPacketType.FORCE_TELEPORT, send)
                        .addFloat(player.x)
                        .addFloat(player.y));
    }

    /**
     * Validates visuals and movement from a player, supplied its information and its latest packet
     * in case of error
     *
     * @return true if not kicked, false if kicked
     */
    public boolean validatePlayerVisuals(
            UUID uuid,
            GamePlayer player,
            DatagramPacket inPacket,
            byte[] send,
            long now,
            int spriteIndex,
            int imageIndex,
            float x,
            float y) {
        if (!properties.testingMode) {
            if (player.spriteIndex < 1088
                    || (player.spriteIndex > 1139
                            && (player.spriteIndex < 2373
                                    || (player.spriteIndex > 2376 && player.spriteIndex != 2517)))
                    || player.imageIndex < 0
                    || player.imageIndex > 10) {
                LOG.logger.info(
                        "Player ID "
                                + player.id
                                + " from "
                                + inPacket.getAddress().toString()
                                + " kicked for invalid visuals ("
                                + player.spriteIndex
                                + ","
                                + player.imageIndex
                                + ")");
                kickPlayer(
                        uuid,
                        player,
                        inPacket,
                        send,
                        "Kicked for invalid visuals (may be a bug)",
                        true);
                return false;
            }
        }

        if (player.lastMovePacketTime != -1) {
            float elapsedFrames = ((now - player.lastMovePacketTime) / 1000f) * 30f;
            if (Math.abs(x - player.x) > elapsedFrames * 6f
                    || Math.abs(y - player.y) > elapsedFrames * 6f) {
                if (properties.kickBadMovement) {
                    LOG.logger.info(
                            "Player ID "
                                    + player.id
                                    + " from "
                                    + inPacket.getAddress().toString()
                                    + " kicked for invalid movement");
                    kickPlayer(
                            uuid,
                            player,
                            inPacket,
                            send,
                            "Kicked for invalid movement (may be a bug)",
                            true);
                    return false;
                } else {
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

    private boolean isRatelimited(InetAddress addr, long time) {
        List<Long> timestamps = this.timestamps.computeIfAbsent(addr, a -> new ArrayList<>());
        timestamps.removeIf(stamp -> time - stamp > 1000);
        timestamps.add(time);

        return timestamps.size() > 30;
    }

    /** The main server routine */
    private void run() {
        // Initialize player structures
        playerIDs = new HashSet<>();
        ratelimited = new HashSet<>();
        connectionsIP = new HashMap<>();
        timestamps = new HashMap<>();
        sessions = new HashMap<>();

        // Initialize all rooms
        rooms = new ArrayList<>();
        for (int i = 0; i < 336; i++) rooms.add(new ArrayList<>());

        // Initialize the buffers (up to 4KB packets)
        byte[] receive = new byte[4096];
        byte[] send = new byte[4096];
        PacketBuilder.fillHeader(send);

        DatagramPacket packet;
        PacketReader pr;
        GamePlayer p;

        UUID uuid;

        while (running) {
            // Fill the receiving buffer with 0
            Arrays.fill(receive, (byte) 0);

            uuid = null;
            p = null;

            packet = new DatagramPacket(receive, receive.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                LOG.logger.log(Level.WARNING, e.getMessage(), e);
                continue;
            }

            pr = new PacketReader(receive, packet.getLength());

            long now = System.currentTimeMillis();

            // Check for spam
            if (isRatelimited(packet.getAddress(), now)) {
                if (ratelimited.add(packet.getAddress())) {
                    LOG.logger.warning(
                            "Client at " + packet.getAddress() + " is hitting ratelimits");

                    for (GamePlayer player : sessions.values()) {
                        if (player.connAddress.equals(packet.getAddress())) {
                            sendPacket(
                                    player.connAddress,
                                    player.connPort,
                                    new PacketBuilder(OutboundPacketType.RATELIMIT_WARNING, send));
                        }
                    }
                }
                continue;
            }
            if (ratelimited.remove(packet.getAddress())) {
                LOG.logger.info(
                        "Client at " + packet.getAddress() + " is no longer hitting ratelimits");
            }

            // Ensure valid packet
            if (!pr.validate()) {
                LOG.logger.warning("Client at " + packet.getAddress() + " sent invalid data");
                LOG.logger.warning(Util.stringify(receive, packet.getLength()));
                continue;
            }

            // Switch to a new log every hour
            if (now - LOG.lastInstantiation >= 60 * 60 * 1000) LOG.instantiateLogger();

            if (properties.disallowSameIP) {
                // Remove connections that haven't sent a packet in over 4 seconds
                Set<InetAddress> toRemove = new HashSet<>();
                for (Map.Entry<InetAddress, Pair<Long, UUID>> e : connectionsIP.entrySet()) {
                    if (now - e.getValue().getKey() > 4000) {
                        LOG.logger.info(
                                "Killed old connection from " + packet.getAddress().toString());
                        uuid = e.getValue().getValue();
                        kickPlayer(uuid, sessions.get(uuid), packet, send, null, true);
                        toRemove.add(e.getKey());
                    }
                }
                toRemove.forEach(connectionsIP::remove);
            } else {
                // Remove sessions that haven't sent a packet in over 4 seconds
                Set<UUID> toRemove = new HashSet<>();
                for (Map.Entry<UUID, GamePlayer> e : sessions.entrySet()) {
                    p = e.getValue();
                    if (now - p.lastPacketTime > 4000) {
                        LOG.logger.info(
                                "Killed old session from "
                                        + packet.getAddress().toString()
                                        + ", ID "
                                        + p.id);
                        kickPlayer(e.getKey(), p, packet, send, null, false);
                        toRemove.add(e.getKey());
                    }
                }
                toRemove.forEach(sessions::remove);
            }

            // Handle the packets by type
            try {
                switch (pr.parseType()) {
                    case LOGIN:
                        {
                            if (properties.disallowSameIP
                                    && connectionsIP.containsKey(packet.getAddress())) {
                                LOG.logger.warning(
                                        "Denied login packet from "
                                                + packet.getAddress().toString()
                                                + " (same IPs disallowed)");
                                break;
                            }

                            if (playerIDs.size() >= properties.maxPlayers) {
                                LOG.logger.warning(
                                        "Denied login packet from "
                                                + packet.getAddress().toString()
                                                + " (max players reached)");
                                sendKickMessagePacket(
                                        packet,
                                        send,
                                        "Cannot join this server; it is at a maximum capacity of "
                                                + properties.maxPlayers
                                                + " players.");
                                break;
                            }

                            // Choose private UUID
                            uuid = UUID.randomUUID();

                            // Choose public ID
                            int id;
                            for (id = 0; id < Integer.MAX_VALUE; id++)
                                if (!playerIDs.contains(id)) break;
                            playerIDs.add(id);

                            // Add session
                            p =
                                    new GamePlayer(
                                            packet.getAddress(), packet.getPort(), uuid, id, now);
                            sessions.put(uuid, p);
                            if (properties.disallowSameIP)
                                connectionsIP.put(packet.getAddress(), new Pair<>(now, uuid));

                            // Send ID/UUID response
                            sendPacket(
                                    packet,
                                    new PacketBuilder(OutboundPacketType.SESSION, send)
                                            .addInt(id)
                                            .addUUID(uuid));

                            LOG.logger.info(
                                    "Login packet from "
                                            + packet.getAddress().toString()
                                            + ", assigned ID "
                                            + id
                                            + " ("
                                            + uuid
                                            + ")");
                            break;
                        }

                    case HEARTBEAT:
                        {
                            if (properties.disallowSameIP
                                    && !connectionsIP.containsKey(packet.getAddress())) break;

                            uuid = pr.getUUID();
                            p = sessions.get(uuid);
                            if (p != null) {
                                p.connAddress = packet.getAddress();
                                p.connPort = packet.getPort();

                                if (properties.disallowSameIP)
                                    connectionsIP.put(packet.getAddress(), new Pair<>(now, uuid));
                                p.lastPacketTime = now;

                                sendPacket(
                                        packet,
                                        new PacketBuilder(OutboundPacketType.HEARTBEAT, send));
                            }
                            break;
                        }

                    case PLAYER_CHANGE_ROOM:
                        {
                            if (properties.disallowSameIP
                                    && !connectionsIP.containsKey(packet.getAddress())) break;

                            uuid = pr.getUUID();
                            p = sessions.get(uuid);
                            if (p != null) {
                                p.connAddress = packet.getAddress();
                                p.connPort = packet.getPort();

                                // Make the player leave the room they're already in
                                if (p.room != -1) playerLeaveRoom(p, send);

                                // Verify target room
                                int targetRoom = pr.getShort();
                                if (targetRoom < -1 || targetRoom >= 336) targetRoom = -1;
                                p.room = targetRoom;

                                // Parse initial visuals
                                int spriteIndex = pr.getShort();
                                int imageIndex = pr.getShort();
                                float x = pr.getFloat();
                                float y = pr.getFloat();

                                // Validate visuals
                                p.lastMovePacketTime = -1;
                                if (!validatePlayerVisuals(
                                        uuid, p, packet, send, now, spriteIndex, imageIndex, x, y))
                                    break;
                                p.lastPacketTime = now;

                                // Ensure room changes aren't too frequent
                                if (p.lastRoomChangeTime != -1) {
                                    if (now - p.lastRoomChangeTime < 200) {
                                        LOG.logger.info(
                                                "Player ID "
                                                        + p.id
                                                        + " from "
                                                        + packet.getAddress().toString()
                                                        + " kicked for room changing");
                                        kickPlayer(
                                                uuid,
                                                p,
                                                packet,
                                                send,
                                                "Kicked for changing rooms too fast (may be a bug)",
                                                true);
                                        break;
                                    }
                                }
                                p.lastRoomChangeTime = now;

                                if (p.room != -1) {
                                    // Send join packet to other players in the room
                                    PacketBuilder pb =
                                            new PacketBuilder(
                                                            OutboundPacketType.PLAYER_JOIN_ROOM,
                                                            send)
                                                    .addInt(p.room)
                                                    .addShort((short) 1) // # of players
                                                    .addInt(p.id)
                                                    .addShort((short) p.spriteIndex)
                                                    .addShort((short) p.imageIndex)
                                                    .addFloat(p.x)
                                                    .addFloat(p.y);
                                    List<GamePlayer> others = rooms.get(p.room);
                                    for (GamePlayer other : others)
                                        sendPacket(other.connAddress, other.connPort, pb);

                                    if (others.size() != 0) {
                                        // Send players in room packet to this player
                                        pb =
                                                new PacketBuilder(
                                                                OutboundPacketType.PLAYER_JOIN_ROOM,
                                                                send)
                                                        .addInt(p.room)
                                                        .addShort((short) others.size());
                                        for (GamePlayer other : others) {
                                            pb.addInt(other.id)
                                                    .addShort((short) other.spriteIndex)
                                                    .addShort((short) other.imageIndex)
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
                            if (properties.disallowSameIP
                                    && !connectionsIP.containsKey(packet.getAddress())) break;

                            uuid = pr.getUUID();
                            p = sessions.get(uuid);
                            if (p != null) {
                                p.connAddress = packet.getAddress();
                                p.connPort = packet.getPort();

                                // Parse visuals
                                int spriteIndex = pr.getShort();
                                int imageIndex = pr.getShort();
                                float x = pr.getFloat();
                                float y = pr.getFloat();

                                // Validate visuals
                                if (!validatePlayerVisuals(
                                        uuid, p, packet, send, now, spriteIndex, imageIndex, x, y))
                                    break;
                                p.lastPacketTime = now;
                                p.lastMovePacketTime = now;

                                if (p.room != -1) {
                                    // Send visual update packet to other players in the room
                                    PacketBuilder pb =
                                            new PacketBuilder(
                                                            OutboundPacketType.PLAYER_VISUAL_UPDATE,
                                                            send)
                                                    .addLong(now)
                                                    .addInt(p.room)
                                                    .addInt(p.id)
                                                    .addShort((short) p.spriteIndex)
                                                    .addShort((short) p.imageIndex)
                                                    .addFloat(p.x)
                                                    .addFloat(p.y);
                                    for (GamePlayer other : rooms.get(p.room)) {
                                        if (other == p) continue;
                                        sendPacket(other.connAddress, other.connPort, pb);
                                    }
                                }
                            }
                            break;
                        }
                }
            } catch (Throwable e) {
                String bytes = Util.stringify(receive, packet.getLength());
                LOG.logger.warning(
                        "Invalid message received from "
                                + packet.getAddress()
                                + ":"
                                + packet.getPort());
                if (uuid != null && p != null) {
                    kickPlayer(uuid, p, packet, send, "Invalid payload: " + bytes, true);
                }
                LOG.logger.warning(bytes);
                LOG.logger.warning(e.toString());
                for (Object element : e.getStackTrace()) {
                    LOG.logger.warning("at " + element);
                }
            }
        }
    }
}
