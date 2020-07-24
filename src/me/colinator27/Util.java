package me.colinator27;

import java.io.File;

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
}
