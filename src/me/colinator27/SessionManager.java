package me.colinator27;

import me.colinator27.packet.OutboundPacketType;
import me.colinator27.packet.PacketBuilder;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SessionManager {

    private GameServer server;
    private Map<UUID, GamePlayer> sessions;
    private Map<InetAddress, List<UUID>> connections;

    private Set<Integer> playerIDs;
    private Log LOG;

    public SessionManager(GameServer server) {
        this.connections = new HashMap<>();
        this.playerIDs = new HashSet<>();
        this.sessions = new HashMap<>();
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

    public GamePlayer createPlayer(InetAddress address, int port) {
        if (playerIDs.size() == server.properties.maxPlayers) {
            return null;
        }
        int id;
        for (id = 0; id < server.properties.maxPlayers && playerIDs.contains(id); id++)
            ;

        playerIDs.add(id);

        UUID uuid = UUID.randomUUID();
        GamePlayer player = new GamePlayer(address, port, uuid, id, System.currentTimeMillis());

        connections.computeIfAbsent(address, a -> new ArrayList<>()).add(uuid);
        sessions.put(uuid, player);

        return player;
    }

    public void releaseAllPlayers(InetAddress address) {
        for (UUID uuid : new ArrayList<>(connections.get(address))) {
            this.releasePlayer(uuid);
        }
    }

    public void releasePlayer(GamePlayer player) {
        this.releasePlayer(player.uuid);
    }

    public void releasePlayer(UUID uuid) {
        if (uuid == null) return;
        GamePlayer player = sessions.get(uuid);
        if (player == null) return;

        LOG.logger.info("Removing player " + player.id + " (" + uuid + ")");
        for (List<UUID> list : connections.values()) {
            list.remove(uuid);
        }
        playerIDs.remove(player.id);
        sessions.remove(uuid);

        server.removePlayerFromRoom(player, player.room);
    }

    public List<GamePlayer> getPlayers(InetAddress address) {
        return connections.containsKey(address)
                ? connections.get(address).stream().map(sessions::get).collect(Collectors.toList())
                : Collections.emptyList();
    }

    public GamePlayer getPlayer(UUID uuid) {
        return sessions.get(uuid);
    }

    public void kick(GamePlayer player, String reason) {
        this.releasePlayer(player);

        server.sendPacket(
                player.connAddress,
                player.connPort,
                new PacketBuilder(OutboundPacketType.KICK_MESSAGE).addString(reason));
    }

    public void kick(UUID uuid, String reason) {
        this.kick(sessions.get(uuid), reason);
    }
}
