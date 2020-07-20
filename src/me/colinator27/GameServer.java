package me.colinator27;

import javafx.util.Pair;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Level;

public class GameServer
{
    private static final boolean DISALLOW_SAME_IP = false;
    private static final int MAX_PLAYERS = 10;
    private static final boolean TESTING = false;
    private static final boolean KICK_BAD_MOVEMENT = false;

    private static List<Integer> usedPorts = new ArrayList<Integer>();

    private Log LOG;
    private volatile boolean running;
    private DatagramSocket socket;
    private Thread thread;

    private HashSet<Integer> playerIDs;
    private HashMap<InetAddress, Pair<Long, UUID>> connectionsIP;
    private HashMap<UUID, GamePlayer> sessions;

    private List<List<GamePlayer>> rooms;

    public boolean isRunning()
    {
        return running;
    }

    public void stop()
    {
        LOG.logger.info("Shutting down");
        running = false;
    }

    public GameServer(int port)
    {
        if (usedPorts.contains(port))
            return;
        else
            usedPorts.add(port);
        LOG = new Log("s" + port);
        LOG.logger.info("Server opening on port " + port);

        try
        {
            socket = new DatagramSocket(port);
        } catch (Exception e)
        {
            LOG.logger.log(Level.SEVERE, e.getMessage(), e);
            return;
        }

        LOG.logger.info("Starting server thread");

        running = true;

        thread = new Thread(() ->
        {
            LOG.logger.info("In server thread");
            run();
            LOG.logger.info("End of server thread");
            running = false;
        }){{start();}};
    }

    private void sendPacket(InetAddress address, int port, byte[] send, byte id, int len)
    {
        send[4] = id;
        DatagramPacket outPacket = new DatagramPacket(send, len + 5, address, port);
        try
        {
            socket.send(outPacket);
        } catch (IOException e)
        {
            LOG.logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    private void sendPacket(DatagramPacket inPacket, byte[] send, byte id, int len)
    {
        sendPacket(inPacket.getAddress(), inPacket.getPort(), send, id, len);
    }

    private void sendKickMessagePacket(DatagramPacket inPacket, byte[] send, String message)
    {
        byte[] buff = message.getBytes();
        for (int i = 0; i < buff.length; i++)
            send[i + 5] = buff[i];
        send[buff.length + 5] = 0;
        sendPacket(inPacket, send, (byte)255, message.length() + 1);
    }

    private void playerLeaveRoom(GamePlayer player, byte[] send)
    {
        List<GamePlayer> room = rooms.get(player.room);
        room.remove(player);

        ByteBuffer bbw = ByteBuffer.wrap(send);
        bbw.order(ByteOrder.LITTLE_ENDIAN);

        bbw.putInt(5, player.room);
        bbw.putInt(5 + 4, player.id);

        for (GamePlayer other : room)
            sendPacket(other.connAddress, other.connPort, send, (byte)11, 4 + 4);
    }

    private void kickPlayer(UUID uuid, GamePlayer player, DatagramPacket inPacket, byte[] send, String message, boolean removeSession)
    {
        if (player.room != -1)
            playerLeaveRoom(player, send);

        if (removeSession)
            sessions.remove(uuid);
        playerIDs.remove(player.id);
        if (message != null)
            sendKickMessagePacket(inPacket, send, message);
    }

    private void resetPlayer(GamePlayer player, DatagramPacket inPacket, byte[] send)
    {
        ByteBuffer bbw = ByteBuffer.wrap(send);
        bbw.order(ByteOrder.LITTLE_ENDIAN);

        bbw.putFloat(5, player.x);
        bbw.putFloat(5 + 4, player.y);

        sendPacket(inPacket, send, (byte)254, 4 + 4);
    }

    private boolean validatePlayerVisuals(UUID uuid, GamePlayer player, DatagramPacket inPacket, byte[] send, long now, int spriteIndex, int imageIndex, float x, float y)
    {
        if (!TESTING)
        {
            if (player.spriteIndex < 1088 || (player.spriteIndex > 1139 && (player.spriteIndex < 2373 || (player.spriteIndex > 2376 && player.spriteIndex != 2517))) ||
                    player.imageIndex < 0 || player.imageIndex > 10)
            {
                LOG.logger.info("Player ID " + player.id + " from " + inPacket.getAddress().toString() + " kicked for invalid visuals (" + player.spriteIndex + "," + player.imageIndex + ")");
                kickPlayer(uuid, player, inPacket, send, "Kicked for invalid visuals (may be a bug)", true);
                return false;
            }
        }

        if (player.lastMovePacketTime != -1)
        {
            float elapsedFrames = ((now - player.lastMovePacketTime) / 1000f) * 30f;
            if (Math.abs(x - player.x) > elapsedFrames * 6f ||
                Math.abs(y - player.y) > elapsedFrames * 6f)
            {
                if (KICK_BAD_MOVEMENT)
                {
                    LOG.logger.info("Player ID " + player.id + " from " + inPacket.getAddress().toString() + " kicked for invalid movement");
                    kickPlayer(uuid, player, inPacket, send, "Kicked for invalid movement (may be a bug)", true);
                    return false;
                } else
                {
                    resetPlayer(player, inPacket, send);
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

    private void run()
    {
        playerIDs = new HashSet<>();
        connectionsIP = new HashMap<>();
        sessions = new HashMap<>();

        // Initialize all rooms
        rooms = new ArrayList<>();
        for (int i = 0; i < 336; i++)
            rooms.add(new ArrayList<>());

        byte[] receive = new byte[4096];
        byte[] send = new byte[4096];
        send[0] = 'U';
        send[1] = 'T';
        send[2] = 'O';
        send[3] = 0;
        while (running)
        {
            Arrays.fill(receive, (byte)0);

            DatagramPacket packet = new DatagramPacket(receive, receive.length);
            try
            {
                socket.receive(packet);
            } catch (IOException e)
            {
                LOG.logger.log(Level.WARNING, e.getMessage(), e);
                continue;
            }

            ByteBuffer bb = ByteBuffer.wrap(receive);
            bb.order(ByteOrder.LITTLE_ENDIAN);

            // Parse header
            if (bb.get() != 'U') continue;
            if (bb.get() != 'T') continue;
            if (bb.get() != 'O') continue;
            if (bb.get() != 0) continue;

            long now = System.currentTimeMillis();

            // Switch to a new log every hour
            if (now - LOG.lastInstantiation >= 60*60*1000)
                LOG.instantiateLogger();

            if (DISALLOW_SAME_IP)
            {
                // Remove connections that haven't sent a packet in over 4 seconds
                for (Iterator<Map.Entry<InetAddress, Pair<Long, UUID>>> it = connectionsIP.entrySet().iterator(); it.hasNext(); )
                {
                    Map.Entry<InetAddress, Pair<Long, UUID>> e = it.next();
                    if (now - e.getValue().getKey() > 4000)
                    {
                        LOG.logger.info("Killed old connection from " + packet.getAddress().toString());
                        it.remove();
                        UUID uuid = e.getValue().getValue();
                        kickPlayer(uuid, sessions.get(uuid), packet, send, null, true);
                    }
                    // todo: we can handle rate-limiting here, potentially
                }
            } else
            {
                // Remove sessions that haven't sent a packet in over 4 seconds
                for (Iterator<Map.Entry<UUID, GamePlayer>> it = sessions.entrySet().iterator(); it.hasNext(); )
                {
                    Map.Entry<UUID, GamePlayer> e = it.next();
                    GamePlayer p = e.getValue();
                    if (now - p.lastPacketTime > 4000)
                    {
                        LOG.logger.info("Killed old session from " + packet.getAddress().toString() + ", ID " + p.id);
                        kickPlayer(e.getKey(), p, packet, send, null, false);
                        it.remove();
                    }
                    // todo: we can handle rate-limiting here, potentially
                }
            }

            switch (bb.get())
            {
                case 1: // Login packet
                {
                    if (DISALLOW_SAME_IP && connectionsIP.containsKey(packet.getAddress()))
                    {
                        LOG.logger.warning("Denied login packet from " + packet.getAddress().toString() + " (same IPs disallowed)");
                        break;
                    }

                    if (playerIDs.size() >= MAX_PLAYERS)
                    {
                        LOG.logger.warning("Denied login packet from " + packet.getAddress().toString() + " (max players reached)");
                        sendKickMessagePacket(packet, send, "Cannot join this server; it is at a maximum capacity of " + MAX_PLAYERS + " players.");
                        break;
                    }

                    UUID uuid;
                    do
                    {
                        uuid = UUID.randomUUID();
                    } while (sessions.containsKey(uuid));

                    int id;
                    for (id = 0; id < Integer.MAX_VALUE; id++)
                        if (!playerIDs.contains(id))
                            break;
                    playerIDs.add(id);

                    GamePlayer player = new GamePlayer(packet.getAddress(), packet.getPort(), id, now);
                    sessions.put(uuid, player);
                    if (DISALLOW_SAME_IP)
                        connectionsIP.put(packet.getAddress(), new Pair<>(now, uuid));

                    ByteBuffer bbw = ByteBuffer.wrap(send);
                    bbw.order(ByteOrder.LITTLE_ENDIAN);
                    bbw.putInt(5, id);
                    bbw.putLong(5 + 4, uuid.getMostSignificantBits());
                    bbw.putLong(5 + 4 + 8, uuid.getLeastSignificantBits());

                    LOG.logger.info("Login packet from " + packet.getAddress().toString() + ", assigning ID " + id);

                    sendPacket(packet, send, (byte)1, 20);
                    break;
                }
                case 2: // Heartbeat packet
                {
                    if (DISALLOW_SAME_IP && !connectionsIP.containsKey(packet.getAddress()))
                        break;

                    long mostSignificantBits = bb.getLong();
                    long leastSignificantBits = bb.getLong();
                    UUID uuid = new UUID(mostSignificantBits, leastSignificantBits);

                    GamePlayer p = sessions.get(uuid);
                    if (p != null)
                    {
                        p.connAddress = packet.getAddress();
                        p.connPort = packet.getPort();

                        //LOG.logger.info("Heartbeat packet from " + packet.getAddress().toString() + ", ID " + p.id);
                        if (DISALLOW_SAME_IP)
                            connectionsIP.put(packet.getAddress(), new Pair<>(now, uuid));
                        p.lastPacketTime = now;

                        sendPacket(packet, send, (byte)2, 0);
                    }
                    break;
                }
                case 10: // Change room packet
                {
                    if (DISALLOW_SAME_IP && !connectionsIP.containsKey(packet.getAddress()))
                        break;

                    long mostSignificantBits = bb.getLong();
                    long leastSignificantBits = bb.getLong();
                    UUID uuid = new UUID(mostSignificantBits, leastSignificantBits);

                    GamePlayer p = sessions.get(uuid);
                    if (p != null)
                    {
                        p.connAddress = packet.getAddress();
                        p.connPort = packet.getPort();

                        if (p.room != -1)
                            playerLeaveRoom(p, send);

                        int targetRoom = bb.getShort();
                        if (targetRoom < -1 || targetRoom >= 336)
                            targetRoom = -1;
                        p.room = targetRoom;

                        // Parse initial visuals
                        int spriteIndex = bb.getShort();
                        int imageIndex = bb.getShort();
                        float x = bb.getFloat();
                        float y = bb.getFloat();

                        p.lastMovePacketTime = -1;
                        if (!validatePlayerVisuals(uuid, p, packet, send, now, spriteIndex, imageIndex, x, y))
                            break;
                        p.lastPacketTime = now;

                        if (p.room != -1)
                        {
                            ByteBuffer bbw = ByteBuffer.wrap(send);
                            bbw.order(ByteOrder.LITTLE_ENDIAN);

                            // Send join packet to other players in the room
                            bbw.putInt(5, p.room);
                            bbw.putShort(5 + 4, (short)1);
                            bbw.putInt(5 + 4 + 2, p.id);
                            bbw.putShort(5 + 4 + 2 + 4, (short)p.spriteIndex);
                            bbw.putShort(5 + 4 + 2 + 4 + 2, (short)p.imageIndex);
                            bbw.putFloat(5 + 4 + 2 + 4 + 2 + 2, p.x);
                            bbw.putFloat(5 + 4 + 2 + 4 + 2 + 2 + 4, p.y);
                            List<GamePlayer> others = rooms.get(p.room);
                            for (GamePlayer other : others)
                                sendPacket(other.connAddress, other.connPort, send, (byte)10,  4 + 2 + 4 + 2 + 2 + 4 + 4);

                            // Send players in room packet to this player
                            if (others.size() != 0)
                            {
                                bbw.putInt(5, p.room);
                                bbw.putShort(5 + 4, (short)others.size());
                                int sendIndex = 5 + 4 + 2;
                                for (GamePlayer other : others)
                                {
                                    bbw.putInt(sendIndex, other.id); sendIndex += 4;
                                    bbw.putShort(sendIndex, (short)other.spriteIndex); sendIndex += 2;
                                    bbw.putShort(sendIndex, (short)other.imageIndex); sendIndex += 2;
                                    bbw.putFloat(sendIndex, other.x); sendIndex += 4;
                                    bbw.putFloat(sendIndex, other.y); sendIndex += 4;
                                }

                                sendPacket(packet, send, (byte)10, sendIndex);
                            }

                            rooms.get(p.room).add(p);
                        }
                    }
                    break;
                }
                case 11: // Visuals packet
                {
                    if (DISALLOW_SAME_IP && !connectionsIP.containsKey(packet.getAddress()))
                        break;

                    long mostSignificantBits = bb.getLong();
                    long leastSignificantBits = bb.getLong();
                    UUID uuid = new UUID(mostSignificantBits, leastSignificantBits);

                    GamePlayer p = sessions.get(uuid);
                    if (p != null)
                    {
                        p.connAddress = packet.getAddress();
                        p.connPort = packet.getPort();

                        int spriteIndex = bb.getShort();
                        int imageIndex = bb.getShort();
                        float x = bb.getFloat();
                        float y = bb.getFloat();

                        if (!validatePlayerVisuals(uuid, p, packet, send, now, spriteIndex, imageIndex, x, y))
                            break;
                        p.lastPacketTime = now;
                        p.lastMovePacketTime = now;

                        if (p.room != -1)
                        {
                            // Send movement packet to other players in the room
                            ByteBuffer bbw = ByteBuffer.wrap(send);
                            bbw.order(ByteOrder.LITTLE_ENDIAN);
                            bbw.putLong(5, now);
                            bbw.putInt(5 + 8, p.room);
                            bbw.putInt(5 + 8 + 4, p.id);
                            bbw.putShort(5 + 8 + 4 + 4, (short)p.spriteIndex);
                            bbw.putShort(5 + 8 + 4 + 4 + 2, (short)p.imageIndex);
                            bbw.putFloat(5 + 8 + 4 + 4 + 2 + 2, p.x);
                            bbw.putFloat(5 + 8 + 4 + 4 + 2 + 2 + 4, p.y);
                            for (GamePlayer other : rooms.get(p.room))
                            {
                                if (other == p)
                                    continue;
                                sendPacket(other.connAddress, other.connPort, send, (byte)12, 8 + 4 + 4 + 2 + 2 + 4 + 4);
                            }
                        }
                    }
                    break;
                }
            }
        }
    }
}
