package me.colinator27.packet;

import me.colinator27.GamePlayer;
import me.colinator27.GameServer;
import me.colinator27.Log;
import me.colinator27.SessionManager;
import me.colinator27.Util;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PacketHandler {

    private Log LOG;
    private GameServer server;
    private InetAddress owner;
    private Queue<Packet> queue;
    private ExecutorService executor;

    private List<Long> timestamps;

    private final AtomicBoolean running, ratelimited;

    private final byte[] send;

    public PacketHandler(GameServer server, InetAddress owner) {
        this.ratelimited = new AtomicBoolean(false);
        this.running = new AtomicBoolean(false);
        this.queue = new ArrayDeque<>();

        this.executor = Executors.newSingleThreadExecutor();
        this.server = server;
        this.owner = owner;

        this.timestamps = new ArrayList<>();
        this.send = new byte[4096];

        this.LOG = server.LOG;
    }

    public void dispatch(Packet packet) {
        synchronized (queue) {
            queue.offer(packet);
        }
        if (!this.isRunning()) {
            this.start();
        }
    }

    public void start() {
        if (running.getAndSet(true)) {
            return;
        }
        executor.execute(
                () -> {
                    LOG.logger.info("Created packet handler for " + owner);

                    // ConnectionManager connectionManager = server.getConnectionManager();
                    SessionManager sessionManager = server.getSessionManager();

                    PacketBuilder builder;
                    PacketReader reader;
                    GamePlayer player;
                    UUID uuid;

                    int spriteIndex, imageIndex, room;
                    float x, y;
                    long now;

                    loop:
                    while (running.get()) {

                        player = null;
                        uuid = null;

                        while (queue.isEmpty()) {
                            if (!running.get()) {
                                break loop;
                            }
                        }
                        Packet packet;
                        synchronized (queue) {
                            ;
                            packet = queue.poll();
                        }

                        now = System.currentTimeMillis();

                        if (this.checkRatelimit()) {
                            if (!ratelimited.getAndSet(true)) {
                                LOG.logger.warning("Client at " + owner + " is hitting ratelimits");

                                for (GamePlayer p : sessionManager.getPlayers()) {
                                    if (p.connAddress.equals(owner)) {
                                        server.sendPacket(
                                                p.connAddress,
                                                p.connPort,
                                                new PacketBuilder(
                                                        OutboundPacketType.RATELIMIT_WARNING,
                                                        send));
                                    }
                                }
                            }
                            continue;
                        }
                        if (ratelimited.getAndSet(false)) {
                            LOG.logger.info(
                                    "Client at " + owner + " is no longer hitting ratelimits");
                        }

                        reader = packet.getReader();

                        if (!reader.validate()) {
                            LOG.logger.warning("Client at " + owner + " sent invalid data");
                            LOG.logger.warning(
                                    Util.stringify(packet.getData(), packet.getLength()));
                            continue;
                        }

                        try {
                            switch (reader.parseType()) {
                                case LOGIN:
                                    {
                                        if (server.properties.disallowSameIP
                                                && !sessionManager.getPlayers(owner).isEmpty()) {
                                            LOG.logger.info(
                                                    "Rejected session request from "
                                                            + owner
                                                            + ":"
                                                            + packet.getPort()
                                                            + " (same IPs disallowed)");
                                            continue;
                                        }
                                        player =
                                                sessionManager.createPlayer(
                                                        owner, packet.getPort());
                                        if (player == null) {
                                            LOG.logger.info(
                                                    "Rejected session request from "
                                                            + owner
                                                            + ":"
                                                            + packet.getPort()
                                                            + " (server is full)");
                                            server.sendPacket(
                                                    owner,
                                                    packet.getPort(),
                                                    new PacketBuilder(
                                                                    OutboundPacketType.KICK_MESSAGE,
                                                                    send)
                                                            .addString(
                                                                    "Cannot join this server; it"
                                                                        + " is at a maximum"
                                                                        + " capacity of "
                                                                            + server.properties
                                                                                    .maxPlayers
                                                                            + " players."));
                                            continue;
                                        }
                                        LOG.logger.info(
                                                String.format(
                                                        "Created session for %s:%s (id = %d, uuid"
                                                            + " = %s)",
                                                        owner,
                                                        packet.getPort(),
                                                        player.id,
                                                        player.uuid));
                                        server.sendPacket(
                                                owner,
                                                packet.getPort(),
                                                new PacketBuilder(OutboundPacketType.SESSION, send)
                                                        .addInt(player.id)
                                                        .addUUID(player.uuid));
                                    }
                                    break;
                                case HEARTBEAT:
                                    {
                                        uuid = reader.getUUID();
                                        player = sessionManager.getPlayer(uuid);

                                        if (player != null) {
                                            player.connAddress = owner;
                                            player.connPort = packet.getPort();

                                            player.lastPacketTime = System.currentTimeMillis();
                                            server.sendPacket(
                                                    owner,
                                                    packet.getPort(),
                                                    new PacketBuilder(
                                                            OutboundPacketType.HEARTBEAT, send));
                                        }
                                    }
                                    break;
                                case PLAYER_CHANGE_ROOM:
                                    {
                                        uuid = reader.getUUID();
                                        player = sessionManager.getPlayer(uuid);

                                        if (player != null) {
                                            room = reader.getShort();

                                            spriteIndex = reader.getShort();
                                            imageIndex = reader.getShort();
                                            x = reader.getFloat();
                                            y = reader.getFloat();

                                            player.lastMovePacketTime = -1;
                                            player.lastPacketTime = System.currentTimeMillis();
                                            if (server.validatePlayerVisuals(
                                                    player, spriteIndex, imageIndex, x, y)) {
                                                server.addPlayerToRoom(player, room);
                                            }
                                        }
                                    }
                                    break;
                                case PLAYER_VISUAL_UPDATE:
                                    {
                                        uuid = reader.getUUID();
                                        player = sessionManager.getPlayer(uuid);

                                        if (player != null) {
                                            spriteIndex = reader.getShort();
                                            imageIndex = reader.getShort();
                                            x = reader.getFloat();
                                            y = reader.getFloat();

                                            player.lastPacketTime = System.currentTimeMillis();
                                            if (server.validatePlayerVisuals(
                                                            player, spriteIndex, imageIndex, x, y)
                                                    && player.room != -1) {
                                                builder =
                                                        new PacketBuilder(
                                                                        OutboundPacketType
                                                                                .PLAYER_VISUAL_UPDATE,
                                                                        send)
                                                                .addLong(now)
                                                                .addInt(player.room)
                                                                .addInt(player.id)
                                                                .addShort((short) spriteIndex)
                                                                .addShort((short) imageIndex)
                                                                .addFloat(x)
                                                                .addFloat(y);

                                                for (GamePlayer other :
                                                        server.getPlayersInRoom(player.room)) {
                                                    if (other == player) {
                                                        continue;
                                                    }
                                                    server.sendPacket(
                                                            other.connAddress,
                                                            other.connPort,
                                                            builder);
                                                }
                                            }
                                        }
                                    }
                                    break;
                            }
                        } catch (Throwable e) {
                            LOG.logger.severe(
                                    "An internal error occured while processing a packet from "
                                            + packet.getAddress()
                                            + ":"
                                            + packet.getPort());

                            if (player == null) {
                                player = sessionManager.getPlayer(uuid);
                            }
                            if (player != null) {
                                sessionManager.kick(player, "Invalid message received");
                                LOG.logger.severe("Player " + player.id + " (" + player.uuid + ")");
                            }
                            LOG.logger.severe(
                                    "Bytes: "
                                            + Util.stringify(packet.getData(), packet.getLength()));
                            LOG.logException(e);
                        }
                    }
                });
    }

    public void stop() {
        this.running.set(false);
    }

    public void dispose() {
        this.stop();
        executor.shutdown();
    }

    public boolean isRunning() {
        return this.running.get();
    }

    private boolean checkRatelimit() {
        long time = System.currentTimeMillis();
        timestamps.removeIf(stamp -> time - stamp > 1000);
        timestamps.add(time);

        return timestamps.size() > 30;
    }
}
