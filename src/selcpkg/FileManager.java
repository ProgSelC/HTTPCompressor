package selcpkg;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentHashMap;

public class FileManager {
    private final static long cacheTTL = 20000L;
    private static ConcurrentHashMap<String, CacheRecord> map = new ConcurrentHashMap<String, CacheRecord>();
    private String path;

    public FileManager(String path) {
        // "c:\folder\" --> "c:\folder"
        if (path.endsWith("/") || path.endsWith("\\"))
            path = path.substring(0, path.length() - 1);

        this.path = path;
    }

    public byte[] get(String url) {
        try {
            if (map.get(url) != null) {
                long cacheAge = System.currentTimeMillis() - map.get(url).date;
                if (cacheAge < cacheTTL) {
                    System.out.println("Returned from cache");
                    return map.get(url).data;// in cache and fresh

                } else {
                    map.remove(url);
                    System.out.println("Cache cleared");
                }
            }

            // "c:\folder" + "/index.html" -> "c:/folder/index.html"
            String fullPath = path.replace('\\', '/') + url;

            RandomAccessFile f = new RandomAccessFile(fullPath, "r");
            byte[] buf;
            try {
                buf = new byte[(int) f.length()];
                f.read(buf, 0, buf.length);
            } finally {
                f.close();
            }

            System.out.println("Put new to cache");
            map.put(url, new CacheRecord(buf)); // put to cache
            return buf;
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
