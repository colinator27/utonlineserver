package me.colinator27;

public class ServerProperties {
    public final int port;
    public final int maxPlayers;
    public final int maxRoomID;
    public final int minRoomChange;
    public final float maxSpeed;
    public final boolean debugMode;
    public final boolean verifyVisuals;
    public final boolean kickInvalidMovement;
    public final boolean disallowSameIP;

    public ServerProperties(
            int port,
            int maxPlayers,
            int maxRoomID,
            int minRoomChange,
            float maxSpeed,
            boolean debugMode,
            boolean verifyVisuals,
            boolean kickBadMovement,
            boolean disallowSameIP) {
        this.port = port;
        this.maxPlayers = maxPlayers;
        this.maxRoomID = maxRoomID;
        this.minRoomChange = minRoomChange;
        this.maxSpeed = maxSpeed;
        
        this.debugMode = debugMode;
        this.verifyVisuals = verifyVisuals;
        this.kickInvalidMovement = kickBadMovement;
        this.disallowSameIP = disallowSameIP;
    }
}
