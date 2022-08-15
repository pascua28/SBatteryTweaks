package com.sammy.chargerateautomator;

import static java.lang.Boolean.TRUE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

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

    //From com.smartpack.kernelmanager.utils.Utils
    public static String removeSuffix(@Nullable String s, @Nullable String suffix) {
        if (s != null && suffix != null && s.endsWith(suffix)) {
            return s.substring(0, s.length() - suffix.length());
        }
        return s;
    }

    //From com.smartpack.kernelmanager.utils.root.RootUtils
    @NonNull
    public static String runAndGetOutput(String command) {
        StringBuilder sb = new StringBuilder();
        try {
            List<String> outputs = Shell.cmd(command).exec().getOut();
            if (ShellUtils.isValidOutput(outputs)) {
                for (String output : outputs) {
                    sb.append(output).append("\n");
                }
            }
            return removeSuffix(sb.toString(), "\n").trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isRooted() {
        Shell.getShell();
        if (TRUE.equals(Shell.isAppGrantedRoot())) {
            return true;
        } else {
            return false;
        }
    }
}
