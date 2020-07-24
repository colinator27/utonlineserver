package me.colinator27.packet;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import me.colinator27.GameServer;
import me.colinator27.Log;

public class ConnectionManager {

	private Map<InetAddress, PacketHandler> handlers;
	private Map<InetAddress, Long> lastPackets; 
	private GameServer server;
	private Log LOG;
	
	public ConnectionManager(GameServer server) {
		this.lastPackets = new HashMap<>();
		this.handlers = new HashMap<>();
		this.server = server;
		
		this.LOG = server.LOG;
	}
	
	public Set<InetAddress> cleanup() {
		final long time = System.currentTimeMillis();
		Set<InetAddress> out = handlers.keySet()
									   .stream()
									   .filter(handler -> time-lastPackets.get(handler) > 4000)
									   .collect(Collectors.toSet());
		
		PacketHandler handler;
		for(InetAddress addr : out) {
			LOG.logger.info("Removing packet handler for " + addr);
			lastPackets.remove(addr);
			handler = handlers.remove(addr);
			
			if(handler != null) {
				handler.dispose();
			}
		}
		return out;
	}
	public synchronized void dispatch(Packet packet) {
		lastPackets.put(packet.getAddress(), System.currentTimeMillis());
		handlers.computeIfAbsent(packet.getAddress(), $ -> new PacketHandler(server, packet.getAddress())).dispatch(packet);
	}
	public void disconnectAll(InetAddress address) {
		server.getSessionManager().releaseAllPlayers(address);
		lastPackets.remove(address);
		handlers.remove(address);
	}
	public List<InetAddress> getConnectedAddresses() {
		return new ArrayList<>(handlers.keySet());
	}
}
