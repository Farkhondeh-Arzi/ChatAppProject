import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Scanner;

/**
 * Send coming messages from the user
 */
public class ClientSender extends Thread {

    Socket socket;
    private final String serverName;
    private final int serverPort;

    private OutputStream serverWriter;

    //If the user loges out
    private boolean exited = false;

    private final Scanner scanner = new Scanner(System.in);

    public ClientSender(String serverName, int serverPort) {
        this.serverName = serverName;
        this.serverPort = serverPort;
    }

    /**
     * Handle entrance functions and then call start method
     */
    @Override
    public void run() {
        //True when the user loges in or register
        boolean notEntered = true;

        //A loop to get name and password while the user failed to log in
        while (notEntered) {

            System.out.println("Username: ");
            Client.user.username = scanner.nextLine();
            System.out.println("Password: ");
            Client.user.password = scanner.nextLine();
            System.out.println("Register or Connect?");

            //Connect or Register
            String dilemma = scanner.nextLine();

            if (dilemma.equalsIgnoreCase("Connect")) {
                try {
                    logIn(Client.user.username, Client.user.password);

                    //Wait for receiver to set LoggedIn to true
                    try {
                        ClientSender.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (Client.loggedIn) {
                        notEntered = false;
                        while (notExit()) {

                            startMessaging(scanner.nextLine());
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (dilemma.equalsIgnoreCase("Register")) {
                try {
                    register(Client.user.username, Client.user.password);

                    //Wait for receiver to set registered to true
                    try {
                        ClientSender.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (Client.registered) {
                        notEntered = false;
                        while (notExit()) {
                            startMessaging(scanner.nextLine());
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    /**
     * Just create the socket
     * @return true if the creation is successful
     */
    public boolean connect() {
        try {
            socket = new Socket(serverName, serverPort);
            serverWriter = socket.getOutputStream();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Client: Connect -Option <user:user_name> -Option <pass:password>
     * Server: Connected -Option <username:#user_name>
     * ERROR -Option <reason:”str”>
     *
     * @param username get from logging in user (unique)
     * @param password get from logging in user
     */
    public void logIn(String username, String password) throws IOException {
        String command = "Connect -Option <user:" + username + "> -Option <pass:" + password + ">";
        sendToServer(command);
    }

    /**
     * Make the register command
     */
    private void register(String username, String password) throws IOException {
        String command = "Make -Option <user:" + username + "> -Option <pass:" + password + ">";
        sendToServer(command);
    }

    /**
     * Get commands
     * @param nextLine to find what kind of command it is
     */
    public void startMessaging(String nextLine) throws IOException {

        String[] splitLine = getTokens(nextLine);

        if (splitLine[0].equalsIgnoreCase("Group")) {
            groupJoin(splitLine);
        } else if (splitLine[0].equalsIgnoreCase("Users")) {
            getUsers();
        } else if (splitLine[0].equalsIgnoreCase("End")) {
            endOpponent();
        } else if (splitLine[0].equalsIgnoreCase("Finish")) {
            finish();
        } else {
            sendMessage(nextLine);
        }
    }

    /**
     * Make joining group
     * @param splitLine to find group name
     */
    private void groupJoin(String[] splitLine) throws IOException {

        if (Client.opponent == null) {
            String groupName = splitLine[1];
            Client.opponent = "#" + groupName;
            sendToServer("Group -Option <gname:" + groupName + ">");
        } else {
            System.err.println("You have now an opponent, please exit first.");
        }
    }

    /**
     * Send to server Users command and if the user is in a group can receive the answer
     */
    private void getUsers() throws IOException {

        if (Client.opponent != null) {
            if (Client.opponent.charAt(0) == '#') {
                sendToServer("Users -Option <gname:" + Client.opponent.substring(1) + ">");
            }
        } else {
            System.err.println("You have no opponent");
        }

    }

    /**
     * Same as others
     * Check the opponent is not null
     */
    private void endOpponent() throws IOException {
        if (Client.opponent != null) {
            sendToServer("End -Option <id:" + Client.opponent.substring(1) + ">");
            System.out.println("Ended: " + Client.opponent.substring(1));
            Client.opponent = null;
        } else {
            System.err.println("You have no opponent");
        }
    }

    /**
     * close socket to finish
     */
    private void finish() throws IOException {
        sendToServer("Finish");
        serverWriter.close();
        socket.close();
        exited = true;
    }

    public boolean notExit() {
        return !exited;
    }

    /**
     * Get multiple lines and send to opponent
     * @param nextLine is the first line of message
     */
    private void sendMessage(String nextLine) throws IOException {

        StringBuilder message = new StringBuilder(nextLine);

        String next = scanner.nextLine();

        //Get 2 enter to end of message
        while (!next.equals("")) {
            message.append("\r\n").append(next);
            next = scanner.nextLine();
        }

        if (Client.opponent != null) {
            if (Client.opponent.charAt(0) == '#')
                sendToServer("GM -Option <to:" + Client.opponent.substring(1) + "> -Option <message_len:" +
                        message.length() + "> -Option " + message);
            else if (Client.opponent.charAt(0) == '@') {
                sendToServer("PM -Option <to:" + Client.opponent.substring(1) + "> -Option <message_len:" +
                        message.length() + "> -Option " + message);
            }
        } else {// Start new direct message

            String[] parts = nextLine.split(" ", 2);
            String messagePart = message.substring(parts[0].length() + 1);
            if (parts.length == 2) {
                sendToServer("PM -Option <to:" + parts[0] + "> -Option <message_len:" +
                        messagePart.length() + "> -Option " + messagePart);
                Client.opponent = "@" + parts[0];
            } else {
                System.err.println("You don't have any opponent; please choose first.");
            }
        }
    }

    /**
     * split lines from the server
     */
    private String[] getTokens(String line) {
        return line.split("\\s+");
    }

    /**
     * Call from other method to send command to the server
     */
    private void sendToServer(String message) throws IOException {
        serverWriter.write((message + "\r\n").getBytes());
    }

    public Socket getSocket() {
        return socket;
    }
}
