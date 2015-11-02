package selcpkg;

public class CacheRecord {
    public byte[] data;
    public long date;

    public CacheRecord(byte[] data) {
        this.data = data;
        this.date = System.currentTimeMillis();
    }
}
