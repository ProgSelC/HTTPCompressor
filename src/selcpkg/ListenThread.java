package selcpkg;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ListenThread extends Thread {
    private int port;
    private String path;

    public ListenThread(int port, String path) {
        this.port = port;
        this.path = path;
    }

    public void run() {
        try {
            ServerSocket srv = new ServerSocket(port);
            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                while ( ! isInterrupted()) {
                    Socket socket = srv.accept();

                    Client client = new Client(socket, path);
                    pool.submit(client);

                    Thread.sleep(50);
                }
            } finally {
                srv.close();
                pool.shutdown();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
    }
}
