package me.colinator27.packet;

import me.colinator27.GamePlayer;
import me.colinator27.GameServer;
import me.colinator27.Log;
import me.colinator27.SessionManager;
import me.colinator27.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PacketHandler {

    private Log LOG;
    private Socket owner;
    private GameServer server;
    private InputStream input;
    private OutputStream output;
    private ExecutorService executor;

    private List<Long> timestamps;

    private final AtomicBoolean running, ratelimited;

    public PacketHandler(GameServer server, Socket owner) {
        this.ratelimited = new AtomicBoolean(false);
        this.running = new AtomicBoolean(false);

        this.executor = Executors.newSingleThreadExecutor();
        this.server = server;
        this.owner = owner;

        this.timestamps = new CopyOnWriteArrayList<>();

        this.LOG = server.LOG;
        LOG.logger.info("Created packet handler for " + owner);
    }
    public synchronized void start() {
        if (running.getAndSet(true)) {
            return;
        }
        try {
        	output = owner.getOutputStream();
        	input = owner.getInputStream();
        }
        catch(IOException e) {
        	LOG.logException(e);
        	running.set(false);
        	return;
        }
        executor.execute(
                () -> {
                    SessionManager sessionManager = server.getSessionManager();

                    PacketBuilder builder;
                    PacketReader reader;
                    GamePlayer player;
                    UUID uuid;

                    int spriteIndex, imageIndex, room;
                    float x, y;
                    long now;
                    
                    byte[] receive = new byte[4096];
                    int amount;

                    while (running.get() && !owner.isClosed()) {
                    	try {
                            player = null;
                            uuid = null;
                            
                            amount = input.read(receive);
                            
                            if(amount < 0) {
                            	break;
                            }
                            
                            now = System.currentTimeMillis();

                            if (this.checkRatelimit()) {
                                if (!ratelimited.getAndSet(true)) {
                                    LOG.logger.warning("Client at " + owner + " is hitting ratelimits");

                                    this.sendPacket(new PacketBuilder(OutboundPacketType.RATELIMIT_WARNING));
                                }
                                continue;
                            }
                            if (ratelimited.getAndSet(false)) {
                                LOG.logger.info("Client at " + owner + " is no longer hitting ratelimits");
                            }

                            reader = new PacketReader(receive, amount);

                            if (!reader.validate()) {
                                LOG.logger.warning("Client at " + owner + " sent invalid data");
                                LOG.logger.warning(Util.stringify(receive, amount));
                                continue;
                            }
                            
                            if(server.properties.testingMode) {
                            	LOG.logger.info(String.format("Recv %s:%d - %s", owner.getInetAddress(), owner.getPort(), reader));
                            }

                            try {
                                switch (reader.parseType()) {
                                    case LOGIN:
                                        {
                                            if (server.properties.disallowSameIP
                                                    && sessionManager.playerFromIPExists(owner.getInetAddress())) {
                                                LOG.logger.info(
                                                        "Rejected session request from "
                                                                + owner
                                                                + " (same IPs disallowed)");
                                                continue;
                                            }
                                            player = sessionManager.getPlayer(owner.getRemoteSocketAddress());
                                            if(player != null) {
                                            	sessionManager.kick(player, "Only one player is allowed per connection");
                                            	break;
                                            }
                                            player = sessionManager.createPlayer(owner, this);
                                            if (player == null) {
                                                LOG.logger.info(
                                                        "Rejected session request from "
                                                                + owner
                                                                + " (server is full)");
                                                this.sendPacket(
                                                        new PacketBuilder(OutboundPacketType.KICK_MESSAGE)
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
                                                            "Created session for %s (id = %d, uuid"
                                                                + " = %s)",
                                                            owner,
                                                            player.id,
                                                            player.uuid));
                                            this.sendPacket(
                                                    new PacketBuilder(OutboundPacketType.SESSION)
                                                            .addInt(player.id)
                                                            .addUUID(player.uuid));
                                        }
                                        break;
                                    case HEARTBEAT:
                                        {
                                            uuid = reader.getUUID();
                                            player = sessionManager.getPlayer(uuid);

                                            if (player != null) {
                                                this.sendPacket(new PacketBuilder(OutboundPacketType.HEARTBEAT));
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

                                                if (server.validatePlayerVisuals(player, spriteIndex, imageIndex, x, y) && player.room != -1) {
                                                    builder = new PacketBuilder(OutboundPacketType.PLAYER_VISUAL_UPDATE)
                                                                    .addLong(now)
                                                                    .addInt(player.room)
                                                                    .addInt(player.id)
                                                                    .addShort((short) spriteIndex)
                                                                    .addShort((short) imageIndex)
                                                                    .addFloat(x)
                                                                    .addFloat(y);

                                                    for (GamePlayer other :
                                                            server.getPlayersInRoom(player.room)) {
                                                        if (other == player) continue;
                                                        other.handler.sendPacket(builder);
                                                    }
                                                }
                                                player.lastMovePacketTime = now;
                                            }
                                        }
                                        break;
                                }
                            } catch (Throwable e) {
                                LOG.logger.severe(
                                        "An internal error occured while processing a packet from "
                                                + owner.getRemoteSocketAddress());

                                if (player == null && uuid != null) {
                                    player = sessionManager.getPlayer(uuid);
                                }
                                if (player != null) {
                                    sessionManager.kick(player, "Invalid message received");
                                    LOG.logger.severe("Player " + player.id + " (" + player.uuid + ")");
                                }
                                LOG.logger.severe(
                                        "Bytes: "
                                                + Util.stringify(receive, amount));
                                LOG.logException(e);
                            }
          
                    	}
                    	catch(IOException e) {
                    		break;
                    	}
                    }
                    LOG.logger.info(owner + " disconnected");
                    sessionManager.releasePlayer(owner.getRemoteSocketAddress());
                    this.dispose();
                });
    }
    
    public boolean sendPacket(PacketBuilder packet) {
    	return this.sendPacket(packet.build());
    }
    public boolean sendPacket(byte[] bytes) {
    	return this.sendPacket(bytes, bytes.length);
    }
    public boolean sendPacket(byte[] bytes, int len) {
    	try {
    		if(server.properties.testingMode) {
    			LOG.logger.info(String.format("Send %s:%d - %s", owner.getInetAddress(), owner.getPort(), Util.stringifyServerPacket(bytes, len)));
    		}
    		output.write(bytes, 0, len);
    		output.flush();
    		return true;
    	}
    	catch(Throwable e) {
    		LOG.logException(e);
    		return false;
    	}
    }

    public void stop() {
        this.running.set(false);
        try {
        	owner.close();
        }
        catch(IOException e) {
        	LOG.logException(e);
        }
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
