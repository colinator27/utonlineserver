package me.colinator27;

import me.colinator27.packet.*;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class GameServer {

    public final ServerProperties properties;
    public final Log LOG;

    private ConnectionManager connectionManager;
    private SessionManager sessionManager;

    private ServerSocket socket;

    private ExecutorService executor;
    private Future<?> future;

    private List<CopyOnWriteArrayList<GamePlayer>> rooms;

    public GameServer(ServerProperties properties) {
        this.properties = properties;

        this.LOG = new Log("s" + properties.port);

        this.connectionManager = new ConnectionManager(this);
        this.sessionManager = new SessionManager(this);
        this.rooms = new ArrayList<>();
        for (int i = 0; i < properties.maxRoomID; i++)
            rooms.add(new CopyOnWriteArrayList<>());

        LOG.logger.info("Server opening on port " + properties.port);
        LOG.instantiateLogger();
        try {
            this.socket = new ServerSocket(properties.port);
        } catch (Exception e) {
            LOG.logException(e);
            return;
        }

        this.executor = Executors.newSingleThreadExecutor();
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public List<GamePlayer> getPlayersInRoom(int room) {
        if (!this.isValidRoom(room)) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(this.getEditableRoom(room));
    }

    public void addPlayerToRoom(GamePlayer player, int room) {
        if (this.isValidRoom(room) && player.room != room) {
            long now = System.currentTimeMillis();

            if (player.lastRoomChangeTime > -1 && now - player.lastRoomChangeTime < 200) {
                sessionManager.kick(player, "You are changing rooms too fast!");
                return;
            }
            if (player.room != -1) {
                this.removePlayerFromRoom(player, player.room);
            }
            player.lastRoomChangeTime = now;
            player.room = room;

            List<GamePlayer> list = this.getEditableRoom(room);
            PacketBuilder packet =
                    new PacketBuilder(OutboundPacketType.PLAYER_JOIN_ROOM)
                            .addInt(room)
                            .addShort((short) 1)
                            .addInt(player.id)
                            .addShort((short) player.spriteIndex)
                            .addShort((short) player.imageIndex)
                            .addFloat(player.x)
                            .addFloat(player.y);

            PacketBuilder packet2 =
                    new PacketBuilder(OutboundPacketType.PLAYER_JOIN_ROOM)
                            .addInt(room)
                            .addShort((short) list.size());

            for (GamePlayer other : list) {
                other.handler.sendPacket(packet);

                packet2.addInt(other.id)
                        .addShort((short) other.spriteIndex)
                        .addShort((short) other.imageIndex)
                        .addFloat(other.x)
                        .addFloat(other.y);
            }
            player.handler.sendPacket(packet2);
            list.add(player);
        }
    }

    public void removePlayerFromRoom(GamePlayer player, int room) {
        if (this.isValidRoom(room)) {
            List<GamePlayer> list = this.getEditableRoom(room);
            list.remove(player);

            PacketBuilder packet =
                    new PacketBuilder(OutboundPacketType.PLAYER_LEAVE_ROOM)
                            .addInt(room)
                            .addInt(player.id);

            for (GamePlayer other : list) other.handler.sendPacket(packet);
            player.room = -1;
        }
    }

    public boolean isValidRoom(int room) {
        return room > -1 && room <= properties.maxRoomID;
    }

    private List<GamePlayer> getEditableRoom(int room) {
        if (this.isValidRoom(room)) return rooms.get(room);
        return new ArrayList<>();
    }

    /**
     * Validates visuals and movement from a player, supplied its information and its latest packet
     * in case of error
     *
     * @return true if not kicked, false if kicked
     */
    public boolean validatePlayerVisuals(GamePlayer player, int spriteIndex, int imageIndex, float x, float y) {
        if (!properties.testingMode) {
            if (player.spriteIndex < 1088 || (player.spriteIndex > 1139 && (player.spriteIndex < 2373 || (player.spriteIndex > 2376 && player.spriteIndex != 2517)))
                || player.imageIndex < 0 || player.imageIndex > 10) {
                LOG.logger.info(player 
                				+ " kicked for invalid visuals ("
                                + player.spriteIndex
                                + ","
                                + player.imageIndex
                                + ")");
                sessionManager.kick(player, "Kicked for invalid visuals (may be a bug)");
                return false;
            }
        }
        
        if(Float.isNaN(x) || Float.isNaN(y) || Float.isInfinite(x) || Float.isInfinite(y)) {
        	LOG.logger.info(player + " kicked for invalid coordinates");
        	sessionManager.kick(player, "Invalid coordinates: (" + x + ", " + y + ")");
        	return false;
        }

        long now = System.currentTimeMillis();
        if (player.lastMovePacketTime != -1) {
            float elapsedFrames = ((now - player.lastMovePacketTime) / 1000f) * 30f;
            if (Math.abs(x - player.x) > elapsedFrames * 5f || Math.abs(y - player.y) > elapsedFrames * 5f) {
                if (properties.kickInvalidMovement) {
                    LOG.logger.info(player + " kicked for invalid movement");
                    sessionManager.kick(player, "Kicked for invalid movement (may be a bug)");
                    return false;
                } else {
                    player.handler.sendPacket(
                            new PacketBuilder(OutboundPacketType.FORCE_TELEPORT)
                                    .addFloat(player.x)
                                    .addFloat(player.y));
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

    public Future<?> start() {
        return future = executor.submit(this::run);
    }

    public void stop() {
        if (future != null && !future.isCancelled()) {
            future.cancel(true);
            sessionManager
                    .getPlayers()
                    .forEach(player -> sessionManager.kick(player, "Server halted"));
        }
    }

    public boolean isRunning() {
        return future != null && !future.isDone();
    }

    private void run() {
    	Socket socket;
        while (true) {
            try {
                socket = this.socket.accept();
                socket.setSoTimeout(4000);
                connectionManager.handleConnection(socket);
            } catch (Throwable e) {
                LOG.logException(e);
            }
        }
    }
}
