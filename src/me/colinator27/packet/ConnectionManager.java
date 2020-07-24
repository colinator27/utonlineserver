package me.colinator27.packet;

import me.colinator27.GameServer;
import me.colinator27.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConnectionManager {

    private Map<Connection, PacketHandler> handlers;
    private Map<Connection, Long> lastPackets;
    private GameServer server;
    private Log LOG;

    public ConnectionManager(GameServer server) {
        this.lastPackets = new HashMap<>();
        this.handlers = new HashMap<>();
        this.server = server;

        this.LOG = server.LOG;
    }

    public Set<Connection> cleanup() {
        final long time = System.currentTimeMillis();
        Set<Connection> out =
                handlers.keySet().stream()
                        .filter(handler -> time - lastPackets.get(handler) > 4000)
                        .collect(Collectors.toSet());

        PacketHandler handler;
        for (Connection addr : out) {
            LOG.logger.info("Removing packet handler for " + addr);
            lastPackets.remove(addr);
            handler = handlers.remove(addr);

            if (handler != null) {
                handler.dispose();
            }
        }
        return out;
    }

    public synchronized void dispatch(Packet packet) {
        lastPackets.put(packet.getConnection(), System.currentTimeMillis());
        handlers.computeIfAbsent(
                        packet.getConnection(), $ -> new PacketHandler(server, packet.getConnection()))
                .dispatch(packet);
    }

    public void disconnectAll(Connection address) {
        server.getSessionManager().releasePlayer(address);
        lastPackets.remove(address);
        handlers.remove(address);
    }

    public List<Connection> getConnectedAddresses() {
        return new ArrayList<>(handlers.keySet());
    }
}
