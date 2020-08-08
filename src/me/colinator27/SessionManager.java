package me.colinator27;

import me.colinator27.packet.OutboundPacketType;
import me.colinator27.packet.PacketBuilder;
import me.colinator27.packet.PacketHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private GameServer server;
    private Set<InetAddress> addresses;
    private Map<UUID, GamePlayer> sessions;
    private Map<SocketAddress, UUID> connections;

    private Set<Integer> playerIDs;
    private Log LOG;

    public SessionManager(GameServer server) {
        this.connections = new ConcurrentHashMap<>();
        this.addresses = new HashSet<>();
        this.playerIDs = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.sessions = new ConcurrentHashMap<>();
        this.server = server;

        this.LOG = server.LOG;
    }

    public List<GamePlayer> getPlayers() {
        return new ArrayList<>(sessions.values());
    }

    public GamePlayer createPlayer(Socket socket, PacketHandler handler) {
        if (playerIDs.size() >= server.properties.maxPlayers) {
            return null;
        }
        int id;
        for (id = 0; id < server.properties.maxPlayers && playerIDs.contains(id); id++);

        playerIDs.add(id);

        UUID uuid = UUID.randomUUID();
        GamePlayer player = new GamePlayer(socket, handler, uuid, id);

        connections.put(socket.getRemoteSocketAddress(), uuid);
        addresses.add(socket.getInetAddress());
        sessions.put(uuid, player);

        return player;
    }

    public void releasePlayer(SocketAddress address) {
        this.releasePlayer(connections.get(address));
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
        addresses.remove(player.socket.getInetAddress());
    }

    public GamePlayer getPlayer(SocketAddress address) {
        return connections.containsKey(address)
                ? sessions.get(connections.get(address))
                : null;
    }

    public GamePlayer getPlayer(UUID uuid) {
        return sessions.get(uuid);
    }

    public boolean playerFromIPExists(InetAddress address) {
    	return addresses.contains(address);
    }

    public void kick(GamePlayer player, String reason) {
        this.releasePlayer(player);
        
        if(player.handler != null) {
        	player.handler.sendPacket(new PacketBuilder(OutboundPacketType.KICK_MESSAGE).addString(reason));
        	player.handler.stop();
        }
        try {
        	player.socket.close();
        }
        catch(IOException e) {}
    }

    public void kick(UUID uuid, String reason) {
        this.kick(sessions.get(uuid), reason);
    }
}
