package selcpkg;

public class FileObj {
	private String filename;
	private byte[] filebody;
	
	public FileObj(String filename, byte[] filebody) {
		super();
		this.filename = filename;
		this.filebody = filebody;
	}
	
	public String getFilename() {
		return filename;
	}
	
	public byte[] getFilebody() {
		return filebody;
	}
	
}
