package me.colinator27;

import java.net.InetAddress;

public class GamePlayer
{
    public int id;
    public long lastMovePacketTime = -1;
    public long lastPacketTime;

    public int room = -1;
    public int spriteIndex = 1088;
    public int imageIndex = 0;
    public float x = 0f;
    public float y = 0f;

    public InetAddress connAddress;
    public int connPort;

    public GamePlayer(InetAddress connAddress, int connPort, int id, long now)
    {
        this.connAddress = connAddress;
        this.connPort = connPort;
        this.id = id;
        lastPacketTime = now;
    }
}
