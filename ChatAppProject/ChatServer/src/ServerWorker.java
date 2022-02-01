import java.io.*;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handlers of each protocols
 */
public class ServerWorker extends Thread {

    private final Server server;
    private List<ServerWorker> workers;

    //Client socket
    private final Socket clientSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private BufferedReader reader;

    //Save entered groups
    private final List<String> groups = new ArrayList<>(5);

    //Save username and password
    private final User user = new User();

    //Save the opponent for leave
    private String opponent;

    private final Database database;

    /**
     * Handle log out if client socket went down
     */
    @Override
    public void run() {
        try {
            handleInputStream();
        } catch (IOException e) {
            try {
                handleLogOut();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    /**
     * Constructor
     * connect to the database at first
     * @param server to have other server workers
     * @param clientSocket corresponding client socket
     */
    ServerWorker(Server server, Socket clientSocket) {
        this.server = server;
        database = server.getDatabase();
        this.clientSocket = clientSocket;

        try {
            outputStream = clientSocket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        database.connect();
    }

    /**
     * Check all commands from clients and call corresponding method
     * If there is no "make" or "connect" command so can't do others
     */
    public void handleInputStream() throws IOException {

        inputStream = clientSocket.getInputStream();
        reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;

        while ((line = reader.readLine()) != null) {

            String[] tokens = line.split(" -Option ");
            String command = tokens[0];

            if (command.equalsIgnoreCase("Make")) {
                handleRegister(tokens);
            } else if (command.equalsIgnoreCase("Connect")) {
                handleLogin(tokens);
            } else if (loggedIn()) {
                if (command.equalsIgnoreCase("Group")) {
                    handleGroup(tokens);
                } else if (command.equalsIgnoreCase("Users")) {
                    showUsersList(tokens);
                } else if (command.equalsIgnoreCase("GM")) {
                    handleGroupMessage(tokens);
                } else if (command.equalsIgnoreCase("PM")) {
                    handleDirectMessage(tokens);
                } else if (command.equalsIgnoreCase("End")) {
                    handleLeave(tokens);
                } else if (command.equalsIgnoreCase("Finish")) {
                    handleLogOut();
                    break;
                } else {
                    send("ERROR -Option <reason:\"Unknown command: " + command + ">\"");
                }
            } else {
                send("ERROR -Option <reason:\"Not logged in\">");
            }
        }
    }

    /**
     * New user
     * Add username and password to the list
     * And check if the username is unique or not
     * Register:
     * Client: Make -Option <user:user_name> -Option <pass:password>
     * Server: User Accepted -Option <username:user_name>
     * ERROR -Option <reason:”str”>
     *
     * @param tokens: find username and password
     */
    private void handleRegister(String[] tokens) throws IOException {

        user.username = tokens[1].substring(6, tokens[1].length() - 1);
        user.password = tokens[2].substring(6, tokens[2].length() - 1);

        String reason;

        if (!isUnique()) {
            reason = "username is not unique!";
        } else if (user.username.length() < 6) {
            reason = "username must be 6 character at least!";
        } else if (user.password.length() < 6) {
            reason = "password must be 6 character at least!";
        } else {
            try {
                database.insert(user);
            } catch (SQLException throwable) {
                throwable.printStackTrace();
            }
            send("User Accepted -Option " + "<username:" + user.username + ">");
            return;
        }
        send("ERROR -Option " + "<reason:\"" + reason + "\">");
    }

    /**
     * Old user
     * Find out if the username and password is already exist or not
     * <p>
     * Log in:
     * Client: Connect -Option <user:user_name> -Option <pass:password>
     * Server: Connected -Option <username:#user_name>
     * ERROR -Option <reason:”str”>
     *
     * @param tokens: take username and password to check
     */
    private void handleLogin(String[] tokens) throws IOException {

        user.username = tokens[1].substring(6, tokens[1].length() - 1);
        user.password = tokens[2].substring(6, tokens[2].length() - 1);

        String message;
        //note that the user just can log in once
        if (userExists() && !opponentExists(user.username)) {
            message = "Connected -Option <username:" + user.username + ">";
        } else {
            String reason;
            if (opponentExists(user.username))
                reason = "This user has logged in";
            else
                reason = "username or password is incorrect";

            //Assign null to username and password 'cause there was an error
            user.username = null;
            user.password = null;

            message = "ERROR -Option " + "<reason:\"" + reason + "\">";
        }
        send(message);
    }

    /**
     * Joining group using group name
     * If the token group exists join else send an error
     * Group:
     * Client: Group -Option <gname:Group_Name>
     * Server: Group -Option <user_name> -Option joined.
     * Group -Option <user_name> -Option Hi
     *
     * @param tokens get group name
     * @throws IOException to send message to output stream
     */
    private void handleGroup(String[] tokens) throws IOException {

        String groupName = tokens[1].substring(7, tokens[1].length() - 1);
        groups.add(groupName);

        if (groupExists(groupName)) {

            //Add # sign to opponent to distinguish from Direct
            opponent = "#" + groupName;

            String onlineMessage;

            workers = server.getWorkers();

            for (ServerWorker worker : workers) {

                if (worker.isMemberOfGroup(groupName)) {
                    if (worker.equals(this)) {
                        // For new one to group --> welcome
                        onlineMessage = "Group -Option <" + user.username + "> -Option Hi";
                    } else {
                        // For others --> joined alert
                        onlineMessage = "Group -Option " + "<" + user.username + ">" + " -Option joined";
                    }
                    worker.send(onlineMessage);
                }
            }
        } else {
            send("ERROR -Option <reason:\"Group doesn't exist\">");
        }
    }

    /**
     * Send current user all other online users in a specific group
     * Get Online Users:
     * Client: Users -Option <gname:Group_Name>
     * Server: Users -Option <user_name1> -Option <user_name2> -Option <user_name3> -Option <user_name4>
     *
     * @param tokens to get group name
     */
    private void showUsersList(String[] tokens) throws IOException {

        //Online users may be changed
        workers = server.getWorkers();

        String groupName = tokens[1].substring(7, tokens[1].length() - 1);

        StringBuilder onlineUsers = new StringBuilder();

        for (ServerWorker worker : workers) {
            if (worker.isMemberOfGroup(groupName)) {
                onlineUsers.append("<").append(worker.getUserName()).append(">").append(" -Option ");
            }
        }
        if (onlineUsers.length() != 0) {
            onlineUsers.delete(onlineUsers.length() - 9, onlineUsers.length() - 1);
        }

        send("Users -Option " + onlineUsers);
    }

    /**
     * Sending message to a group that the user is a member of it
     * Send Message To a Group:
     * Client: GM -Option <to:GAPNAME> -Option <message_len:#> -Option <msg>
     * Server: GM -Option <from:username> -Option <to:GAPNAME> -Option <message_len:#> -Option <msg>
     *
     * @param tokens to find sending group
     */
    private void handleGroupMessage(String[] tokens) throws IOException {

        String sendTo = tokens[1].substring(4, tokens[1].length() - 1);

        if (isMemberOfGroup(sendTo)) {
            int messageLength = Integer.parseInt(tokens[2].substring(13, tokens[2].length() - 1));
            StringBuilder messageBody = new StringBuilder(tokens[3]);
            int lineLength = tokens[3].length();

            //to get all lines
            while (messageLength > lineLength) {
                String line = reader.readLine();
                lineLength += line.length() + 2;
                messageBody.append("\r\n").append(line);
            }

            String message = "GM -Option <from:" + user.username + "> -Option <to:" +
                    sendTo + "> -Option <message_len:" + messageLength + "> -Option " + messageBody;

            workers = server.getWorkers();
            for (ServerWorker worker : workers) {
                if (worker.isMemberOfGroup(sendTo)) {
                    worker.send(message);
                }
            }
        } else {
            send("ERROR -Option <reason:\"you're not member of this group\">");
        }
    }

    /**
     * Check if the send to user exists then send the received message
     * Send a message directly:
     * Client: PM -Option <to:user_name> -Option <message_len:#> -Option <msg>
     * Server: PM -Option <from:username> -Option <to:user_name> -Option <message_len:#> -Option <msg>
     *
     * @param tokens to find the opponent user
     */
    private void handleDirectMessage(String[] tokens) throws IOException {

        String sendTo = tokens[1].substring(4, tokens[1].length() - 1);

        if (opponentExists(sendTo)) {

            //Add @ sign to opponent to distinguish from Group
            opponent = "@" + sendTo;
            int messageLength = Integer.parseInt(tokens[2].substring(13, tokens[2].length() - 1));
            StringBuilder messageBody = new StringBuilder(tokens[3]);
            int lineLength = tokens[3].length();

            //to get all lines
            while (messageLength > lineLength) {
                String line = reader.readLine();
                lineLength += line.length() + 2;
                messageBody.append("\r\n").append(line);
            }

            String message = "PM -Option <from:" + user.username + "> -Option <to:" +
                    sendTo + "> -Option <message_len:" + messageLength + "> -Option " + messageBody;

            workers = server.getWorkers();
            for (ServerWorker worker : workers) {
                if (worker.getUserName().equals(sendTo)) {
                    //Send either opponent and sender the message
                    worker.send(message);
                    send(message);
                }
            }
        } else
            send("ERROR -Option <reason:\"the username you are looking for, doesn't exist\">");
    }

    /**
     * Check if the opponent is a Group or just a user then send the opponent corresponding message
     * Leave a Group or Direct chat:
     * Client: End -Option <id:user_name OR GAPNAME>
     * Server: End -Option <user_name> left
     *
     * @param tokens to get the opponent that the user wants to leave from
     */
    private void handleLeave(String[] tokens) throws IOException {

        String leavingOpponent = tokens[1].substring(4, tokens[1].length() - 1);

        if (opponent.charAt(0) == '#') {//so it is a group

            if (isMemberOfGroup(leavingOpponent)) {

                groups.remove(leavingOpponent);

                workers = server.getWorkers();
                String leavingMessage = "End -Option <" + user.username + "> -Option left";

                for (ServerWorker worker : workers) {
                    if (worker.isMemberOfGroup(leavingOpponent)) {
                        worker.send(leavingMessage);
                    }
                }
                opponent = null;
            } else {
                send("ERROR -Option <reason:\"you're not member of this group\">");
            }
        } else if (opponent.charAt(0) == '@') {//so it is a user

            workers = server.getWorkers();
            String leavingMessage = "End -Option <" + user.username + "> -Option left";

            for (ServerWorker worker : workers) {
                if (worker.getUserName().equals(leavingOpponent)) {
                    worker.send(leavingMessage);
                }
            }
            opponent = null;
        }
    }

    /**
     * Call the leave method first to go out from groups or direct
     * and then closes socket
     * Log out:
     * Client: Finish
     * Server: End -Option <user_name> left
     */
    private void handleLogOut() throws IOException {

        if (opponent != null) {
            String[] newTokens = {"End", "<id:" + opponent.substring(1) + ">"};
            handleLeave(newTokens);
        }
        server.removeWorker(this);
        inputStream.close();
        outputStream.close();
        clientSocket.close();
    }

    //End handlers

    /**
     * For log in checks if the given username exists or not
     *
     * @return true if can find such username in the database
     */
    private boolean userExists() {

        try {
            User receivedUser = database.get(user.username);
            return receivedUser != null && receivedUser.password.equals(user.password);
        } catch (SQLException throwable) {
            throwable.printStackTrace();
        }
        return false;
    }

    /**
     * Check if the opponent is logged in or not
     *
     * @param username username of opponent
     * @return true if there is a worker with given username
     */
    private boolean opponentExists(String username) {

        workers = server.getWorkers();
        for (ServerWorker worker : workers) {
            if (worker.getUserName() != null)
                if (worker.getUserName().equals(username) && !worker.equals(this)) return true;
        }

        return false;
    }

    /**
     * For register checks if the given username is token or not
     *
     * @return true if there is not such username in database
     */
    private boolean isUnique() {
        try {
            return database.get(user.username) == null;
        } catch (SQLException throwable) {
            throwable.printStackTrace();
        }
        return true;
    }

    /**
     * Check in the groups
     *
     * @return true if the groupName is in groups array
     */
    private boolean groupExists(String groupName) {
        for (String group : server.getGroups()) {
            if (groupName.equalsIgnoreCase(group)) return true;
        }

        return false;
    }

    /**
     * check if the user is logged in or not
     *
     * @return true if the username is nut null which that means log in or register function is called
     */
    public boolean loggedIn() {
        return user.username != null && user.password != null;
    }

    /**
     * @param group: joined groups
     */
    public boolean isMemberOfGroup(String group) {
        return groups.contains(group);
    }

    /**
     * send the protocol to client side
     *
     * @param message the protocol
     */
    private void send(String message) throws IOException {
        System.out.println(message);
        outputStream.write((message + "\r\n").getBytes());
    }

    /**
     * @return the client's name
     */
    private String getUserName() {
        return user.username;
    }
}
