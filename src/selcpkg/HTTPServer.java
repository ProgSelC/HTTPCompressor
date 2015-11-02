package selcpkg;

public class HTTPServer {
    private int port;
    private String path;
    private ListenThread listenThread;

    public HTTPServer(int port, String path) {
        this.port = port;
        this.path = path;
    }

    public void start() {
        listenThread = new ListenThread(port, path);
        listenThread.start();
    }
    
    public void stop() {
    	listenThread.interrupt();
    }
}
