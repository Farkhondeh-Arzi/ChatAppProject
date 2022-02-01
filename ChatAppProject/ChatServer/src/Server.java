import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Farkhondeh Arzi
 * Has a list of workers
 * And create worker thread
 */
public class Server {

    private final int port = 50000;

    private final Database database = new Database();
    private final List<ServerWorker> workers = new ArrayList<>();
    private String[] groups = new String[]{"G1", "G2", "G3", "G4", "G5"};

    public void run() {

        try {

            ServerSocket serverSocket = new ServerSocket(port);

            //Server always running
            while (true) {

                Socket client = serverSocket.accept();
                ServerWorker worker = new ServerWorker(this, client);
                worker.start();
                addWorker(worker);

            }

        } catch (IOException e) {
            System.err.println("can't connect to the server!");
            e.printStackTrace();
        }
    }

    //Setters & Getters
    public List<ServerWorker> getWorkers() {
        return workers;
    }

    public void addWorker(ServerWorker worker) {
        workers.add(worker);
    }
    public void removeWorker(ServerWorker worker) {
        workers.remove(worker);
    }

    public Database getDatabase() {
        return database;
    }

    public String[] getGroups() {
        return groups;
    }
}
