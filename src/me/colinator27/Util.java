package me.colinator27;

import java.io.File;

public class Util
{
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
