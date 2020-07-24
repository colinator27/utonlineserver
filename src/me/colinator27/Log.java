package me.colinator27;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.logging.*;

/**
 * Class to deal with logging to console/files
 */
public class Log
{
    public Logger logger;
    private Formatter loggerFormat;
    private String type;

    public long lastInstantiation;

    /**
     * @param type  the string identifier of the log
     */
    public Log(String type)
    {
        this.type = type;
        instantiateLogger();
    }
    
    /**
     * Helper method to log exceptions
     * @param throwable exception
     */
    public void logException(Throwable throwable) {
    	if(throwable == null) return;
    	logger.severe(throwable.toString());
    	for(Object element : throwable.getStackTrace()) {
    		logger.severe("at " + element);
    	}
    }

    /**
     * Sets up the handlers and Java Logger for logging.
     * If it was set up previously, the old handlers are flushed and replaced.
     */
    public void instantiateLogger()
    {
        if (logger != null)
        {
            Handler[] old = logger.getHandlers();
            for (Handler h : old)
            {
                h.flush();
                h.close();
                logger.removeHandler(h);
            }
        }

        lastInstantiation = System.currentTimeMillis();
        logger = Logger.getLogger(type);

        SimpleDateFormat format = new SimpleDateFormat("M-d_HHmmss");

        try
        {
            logger.setUseParentHandlers(false);
            logger.addHandler(new ConsoleHandler());
            logger.addHandler(new FileHandler( Util.getWorkingDirectory() + "log_" + type + "_" + format.format(Calendar.getInstance().getTime()) + ".log"));
            loggerFormat = new Formatter() {
                @Override
                public String format(LogRecord record) {
                    SimpleDateFormat logTime = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
                    Calendar cal = new GregorianCalendar();
                    cal.setTimeInMillis(record.getMillis());
                    return record.getLevel() + " "
                            + logTime.format(cal.getTime())
                            + " || "
                            + record.getSourceClassName().substring(
                            record.getSourceClassName().lastIndexOf(".") + 1)
                            + " (" + type + ")"
                            + ": "
                            + record.getMessage() + "\n";
                }
            };
            for (Handler h : logger.getHandlers())
                h.setFormatter(loggerFormat);
        } catch (Exception e)
        {
            e.printStackTrace();
            return;
        }
    }
}
