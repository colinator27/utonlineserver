package me.colinator27;

public class ServerProperties {
    public final int port;
    public final int maxPlayers;
    public final int maxRoomID;
    public final boolean testingMode;
    public final boolean kickInvalidMovement;
    public final boolean disallowSameIP;

    public ServerProperties(
            int port,
            int maxPlayers,
            int maxRoomID,
            boolean testingMode,
            boolean kickBadMovement,
            boolean disallowSameIP) {
        this.port = port;
        this.maxPlayers = maxPlayers;
        this.maxRoomID = maxRoomID;
        this.testingMode = testingMode;
        this.kickInvalidMovement = kickBadMovement;
        this.disallowSameIP = disallowSameIP;
    }
}
