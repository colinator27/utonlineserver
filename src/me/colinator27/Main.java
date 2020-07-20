package me.colinator27;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

public class Main
{
    private static Log LOG;

    private static volatile List<GameServer> servers = new ArrayList<>();

    public static Properties properties;

    private static boolean anythingRunning()
    {
        for (GameServer s : servers)
            if (s.isRunning())
                return true;
        return false;
    }

    private static void defaultProperties()
    {
        properties = new Properties();
        properties.setProperty("port", "1337");
    }

    private static void loadProperties()
    {
        LOG.logger.info("Loading properties...");
        File propFile = new File(Util.getWorkingDirectory() + "config.properties");
        if (propFile.exists())
        {
            try
            {
                FileReader reader = new FileReader(propFile);
                properties = new Properties();
                properties.load(reader);
                reader.close();
            } catch (Exception e)
            {
                LOG.logger.log(Level.WARNING, e.getMessage(), e);
                LOG.logger.warning("Failed to load properties; using defaults");
                defaultProperties();
            }
        } else
        {
            LOG.logger.info("Found no existing properties; generating a new one");

            defaultProperties();

            try
            {
                FileWriter writer = new FileWriter(propFile);
                properties.store(writer, "Server properties");
                writer.close();
            } catch (Exception e)
            {
                LOG.logger.log(Level.WARNING, e.getMessage(), e);
            }
        }
    }

    public static void main(String[] args)
    {
        LOG = new Log("main");
        LOG.logger.info("Initializing...");

        loadProperties();

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        {
            LOG.logger.info("Shutting down servers");
            for (GameServer s : servers)
                s.stop();
            LOG.logger.info("Completed shutdown");
        }));

        servers.add(new GameServer(Integer.parseInt(properties.getProperty("port"))));
        while (anythingRunning())
        {
            try
            {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
        }
    }
}
