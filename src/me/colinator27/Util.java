package me.colinator27;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.colinator27.packet.OutboundPacketType;

public class Util {
    /**
     * @return the parent directory of the JAR file as a String (assuming the Main class is in the
     *     JAR)
     */
    public static String getWorkingDirectory() {
        try {
            return new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                            .getParentFile()
                            .getPath()
                    + File.separator;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * @param bytes byte array to read from
     * @param len number of bytes to stringify
     * @return the array of bytes in string form
     */
    public static String stringify(byte[] bytes, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("\\x%02x", bytes[i]));
        }
        return sb.toString();
    }
    
    /**
     * Converts a server packet (from a PacketBuilder most likely) into a human-readable string
     * 
     * This method assumes the packet is valid
     * 
     * @param bytes raw packet bytes
     * @param len length of packet
     * @return the packet in string form
     */
    public static String stringifyServerPacket(byte[] bytes, int len) {
    	try {
        	StringBuilder sb = new StringBuilder();
        	Map<String, Object> args = new LinkedHashMap<>();
        	ByteBuffer reader = ByteBuffer.wrap(bytes, 4, len-4);
        	reader.order(ByteOrder.LITTLE_ENDIAN);
        	
        	OutboundPacketType type = OutboundPacketType.fromValue(reader.get());
        	
        	sb.append(type);
        	sb.append(" ");
        	
        	switch(type) {
    		case FORCE_TELEPORT: {
    			args.put("coords", new Pair<Float, Float>(reader.getFloat(), reader.getFloat()));
    		} break;
    		case HEARTBEAT:
    			break;
    		case KICK_MESSAGE: {
    			StringBuilder message = new StringBuilder();
    			CharBuffer chars = reader.asCharBuffer();
    			while(chars.hasRemaining()) {
    				message.append(chars.get());
    			}
    			args.put("message", message);
    		} break;
    		case PLAYER_JOIN_ROOM: {
    			int room = reader.getInt();
    			int numPlayers = reader.getShort();
    			
    			args.put("room", room);
    			args.put("numPlayers", numPlayers);
    			
    			List<Object> players = new ArrayList<>();
    			Map<String, Object> playerArgs;
    			while(numPlayers-- > 0) {
    				playerArgs = new LinkedHashMap<>();
    				playerArgs.put("id", reader.getInt());
    				playerArgs.put("sprite", reader.getShort());
    				playerArgs.put("frame", reader.getShort());
    				playerArgs.put("coords", new Pair<Float, Float>(reader.getFloat(), reader.getFloat()));
    				players.add(playerArgs.entrySet());
    			}
    			args.put("players", players);
    		} break;
    		case PLAYER_LEAVE_ROOM: {
    			args.put("room", reader.getInt());
    			args.put("id", reader.getInt());
    		} break;
    		case PLAYER_VISUAL_UPDATE: {
    			args.put("timestamp", reader.getLong());
    			args.put("room", reader.getInt());
    			args.put("id", reader.getInt());
    			args.put("sprite", reader.getShort());
    			args.put("frame", reader.getShort());
    			args.put("coords", new Pair<Float, Float>(reader.getFloat(), reader.getFloat()));
    		} break;
    		case RATELIMIT_WARNING:
    			break;
    		case SESSION: {
    			args.put("id", reader.getInt());
    			args.put("uuid", new UUID(reader.getLong(), reader.getLong()));
    		} break;
    		default:
    			args.put("contents", stringify(Arrays.copyOfRange(bytes, 5, len-5), len-5));
        	}
        	sb.append(args.entrySet());
        	return sb.toString();
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    		return stringify(bytes, len);
    	}
    }
}
