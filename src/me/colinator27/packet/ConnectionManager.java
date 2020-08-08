package me.colinator27.packet;

import me.colinator27.GameServer;
import me.colinator27.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConnectionManager {
	
	private ScheduledExecutorService cleanupService;

    private Map<Socket, PacketHandler> handlers;
    private GameServer server;
    
    private Log LOG;

    public ConnectionManager(GameServer server) {
    	this.cleanupService = Executors.newSingleThreadScheduledExecutor();
        this.handlers = new ConcurrentHashMap<>();
        this.server = server;
        
        this.LOG = server.LOG;
        
        cleanupService.scheduleAtFixedRate(() -> {
        	Set<Socket> dead = new HashSet<>();
        	for(Socket socket : handlers.keySet()) {
        		if(socket.isClosed() || !socket.isConnected()) {
        			dead.add(socket);
        		}
        	}
        	for(Socket socket :dead) {
        		handlers.remove(socket);
        		try {
        			socket.close();
        		}
        		catch(IOException e) {
        			LOG.logException(e);
        		}
        	}
        }, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized PacketHandler handleConnection(Socket socket) {
        if(!handlers.containsKey(socket)) {
        	PacketHandler handler = new PacketHandler(server, socket);
        	handlers.put(socket, handler);
        	handler.start();
        }
        return handlers.get(socket);
    }

    public void disconnectAll(InetAddress address) {
    	Set<Socket> sockets = new HashSet<>();
    	for(Socket socket : handlers.keySet()) {
    		if(socket.getInetAddress().equals(address)) {
    			sockets.add(socket);
    		}
    	}
    	for(Socket socket : sockets) {
    		server.getSessionManager().releasePlayer(socket.getRemoteSocketAddress());
    		handlers.remove(socket);
    		try {
    			socket.close();
    		}
    		catch(IOException e) {
    			LOG.logException(e);
    		}
    	}
    }

    public List<Socket> getConnectedSockets() {
        return new ArrayList<>(handlers.keySet());
    }
}
