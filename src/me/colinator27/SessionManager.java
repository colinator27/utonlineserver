package me.colinator27;

import me.colinator27.packet.Connection;
import me.colinator27.packet.OutboundPacketType;
import me.colinator27.packet.PacketBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SessionManager {

    private GameServer server;
    private Map<UUID, GamePlayer> sessions;
    private Map<Connection, UUID> connections;

    private Set<Integer> playerIDs;
    private Log LOG;

    public SessionManager(GameServer server) {
        this.connections = new ConcurrentHashMap<>();
        this.playerIDs = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.sessions = new ConcurrentHashMap<>();
        this.server = server;

        this.LOG = server.LOG;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();

        // I can't just for-each this in the stream as it will throw a
        // ConcurrentModificationException
        List<GamePlayer> dead =
                sessions.values().stream()
                        .filter(player -> now - player.lastPacketTime > 4000)
                        .collect(Collectors.toList());
        dead.forEach(this::releasePlayer);
    }

    public List<GamePlayer> getPlayers() {
        return new ArrayList<>(sessions.values());
    }

    public GamePlayer createPlayer(Connection connection) {
        if (playerIDs.size() == server.properties.maxPlayers) {
            return null;
        }
        int id;
        for (id = 0; id < server.properties.maxPlayers && playerIDs.contains(id); id++)
            ;

        playerIDs.add(id);

        UUID uuid = UUID.randomUUID();
        GamePlayer player = new GamePlayer(connection, uuid, id, System.currentTimeMillis());

        connections.put(connection, uuid);
        sessions.put(uuid, player);

        return player;
    }

    public void releasePlayer(Connection connection) {
        this.releasePlayer(connections.get(connection));
    }

    public void releasePlayer(GamePlayer player) {
        this.releasePlayer(player.uuid);
    }

    public void releasePlayer(UUID uuid) {
        if (uuid == null) return;
        GamePlayer player = sessions.get(uuid);
        if (player == null) return;

        LOG.logger.info("Removing player " + player.id + " (" + uuid + ")");
        connections.values().remove(uuid);
        playerIDs.remove(player.id);
        sessions.remove(uuid);

        server.removePlayerFromRoom(player, player.room);
    }

    public GamePlayer getPlayer(Connection connection) {
        return connections.containsKey(connection)
                ? sessions.get(connections.get(connection))
                : null;
    }

    public GamePlayer getPlayer(UUID uuid) {
        return sessions.get(uuid);
    }

    public void kick(GamePlayer player, String reason) {
        this.releasePlayer(player);

        server.sendPacket(player.connection, new PacketBuilder(OutboundPacketType.KICK_MESSAGE).addString(reason));
    }

    public void kick(UUID uuid, String reason) {
        this.kick(sessions.get(uuid), reason);
    }
}
