package fr.pioupia.courserecorder.Managers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class IndexManager {
    private static String rootDir;
    private static int index = 0;

    public static void init() {
        File file = new File(rootDir + "/index");

        if (!file.exists()) {
            save();
            return;
        }

        try {
            RandomAccessFile f = new RandomAccessFile(file, "r");
            byte[] b = new byte[(int) f.length()];
            f.readFully(b);

            setIndex(b[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        FileOutputStream outputStream;

        File file = new File(rootDir + "/index");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                return;
            }
        }

        try {
            outputStream = new FileOutputStream(rootDir + "/index");
            outputStream.write((byte) index);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int getIndex() {
        return index;
    }

    public static void setIndex(int index) {
        IndexManager.index = index;
    }

    public static void incIndex() {
        IndexManager.index++;
    }

    public static void decIndex() {
        IndexManager.index--;
    }

    public static void setRootDir(String rootDir) {
        IndexManager.rootDir = rootDir;
    }
}
