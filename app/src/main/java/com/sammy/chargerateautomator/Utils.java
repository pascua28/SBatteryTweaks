package com.sammy.chargerateautomator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Utils {
    private static StringBuilder stringBuilder = new StringBuilder();
    private static BufferedReader buf = null;
    public static String readFile(File file) {
        buf = null;
        try {
            buf = new BufferedReader(new FileReader(file));
            stringBuilder.setLength(0);

            String line;
            while ((line = buf.readLine()) != null) {
                stringBuilder.append(line);
            }

            return stringBuilder.toString();
        } catch (IOException ignored) {
        } finally {
            try {
                if (buf != null) buf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
