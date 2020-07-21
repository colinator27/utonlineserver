package me.colinator27;

import java.io.File;

public class Util
{
    /**
     * @return  the parent directory of the JAR file as a String (assuming the Main class is in the JAR)
     */
    public static String getWorkingDirectory()
    {
        try
        {
            return new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getPath() + File.separator;
        } catch (Exception e)
        {
            e.printStackTrace();
            return "";
        }
    }
}
