package me.colinator27;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Main {
    private static final Log LOG = new Log("main");

    /** The running server threads (on different ports) */
    private static volatile List<GameServer> servers = new ArrayList<>();

    /** The config properties for the server(s) */
    public static Properties properties;

    /** @return whether any of the servers are running */
    private static boolean anythingRunning() {
        for (GameServer s : servers) if (s.isRunning()) return true;
        return false;
    }

    /** Sets the default config properties */
    private static void defaultProperties() {
        properties = new Properties();

        // Number of servers (on different ports/threads)
        properties.setProperty("num-servers", "1");

        // Ports for servers
        properties.setProperty("port", "1337");

        // Max players for servers
        properties.setProperty("max-players", "20");

        // Max room IDs for the servers
        properties.setProperty("max-room-id", "335");

        // Minimum time (in ms) between room changes
        properties.setProperty("min-room-change", "200");

        // Whether to verify visuals (sprites, etc., excluding movement)
        properties.setProperty("verify-visuals", "true");

        // Kick bad movement packets rather than teleporting backward
        // (highly recommended to be false)
        properties.setProperty("kick-invalid-movement", "false");

        // Whether to disallow more than one connection at a time from an IP
        // (recommended to be false)
        properties.setProperty("disallow-same-ip", "false");
    }

    /** Loads the properties config file */
    private static void loadProperties() {
        LOG.logger.info("Loading properties...");
        defaultProperties();

        File propFile = new File(Util.getWorkingDirectory() + "config.properties");
        if (propFile.exists()) {
            try {
                FileReader reader = new FileReader(propFile);
                properties.load(reader);
                reader.close();
            } catch (Exception e) {
                LOG.logger.log(Level.WARNING, e.getMessage(), e);
                LOG.logger.warning("Failed to load properties; using defaults");
                defaultProperties();
            }
        } else {
            LOG.logger.info("Found no existing properties; generating a new one");
        }

        // Write output file, regardless of if just loaded
        try {
            FileWriter writer = new FileWriter(propFile);
            properties.store(
                    writer,
                    "Server properties\n"
                            + "For multiple ports/threads, separate values with commas,\n"
                            + "like port=1337,1338");
            writer.close();
        } catch (Exception e) {
            LOG.logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        LOG.logger.info("Initializing...");

        loadProperties();

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    LOG.logger.info("Shutting down servers");
                                    for (GameServer s : servers) s.stop();
                                    LOG.logger.info("Completed shutdown");
                                }));

        // Add the servers on different ports
        int count = Integer.parseInt(properties.getProperty("num-servers"));
        List<Integer> ports =
                Arrays.stream(properties.getProperty("port").split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
        assert ports.size() == count;
        List<Integer> maxPlayers =
                Arrays.stream(properties.getProperty("max-players").split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
        assert maxPlayers.size() == count;
        List<Integer> maxRoomIDs =
                Arrays.stream(properties.getProperty("max-room-id").split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
        assert maxRoomIDs.size() == count;
        List<Integer> minRoomChanges =
                Arrays.stream(properties.getProperty("min-room-change").split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
        assert minRoomChanges.size() == count;
        List<Boolean> verifyVisuals =
                Arrays.stream(properties.getProperty("verify-visuals").split(","))
                        .map(Boolean::parseBoolean)
                        .collect(Collectors.toList());
        assert verifyVisuals.size() == count;
        List<Boolean> kickInvalidMovement =
                Arrays.stream(properties.getProperty("kick-invalid-movement").split(","))
                        .map(Boolean::parseBoolean)
                        .collect(Collectors.toList());
        assert kickInvalidMovement.size() == count;
        List<Boolean> disallowSameIP =
                Arrays.stream(properties.getProperty("disallow-same-ip").split(","))
                        .map(Boolean::parseBoolean)
                        .collect(Collectors.toList());
        assert disallowSameIP.size() == count;
        for (int i = 0; i < count; i++)
            servers.add(
                    new GameServer(
                            new ServerProperties(
                                    ports.get(i),
                                    maxPlayers.get(i),
                                    maxRoomIDs.get(i),
                                    minRoomChanges.get(i),
                                    verifyVisuals.get(i),
                                    kickInvalidMovement.get(i),
                                    disallowSameIP.get(i))));
        servers.forEach(GameServer::start);
        // Wait for all of the servers to stop
        while (anythingRunning()) {
            try {
                Thread.sleep(100);
                // todo: do asynchronous operations here
            } catch (InterruptedException e) {
            }
        }
    }
}
