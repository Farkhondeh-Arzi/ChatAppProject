import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 * Receives all protocol coming from the server
 */
public class ClientReceiver extends Thread {

    private BufferedReader serverReader;

    /**
     * Constructor
     * @param socket to set buffer reader
     */
    ClientReceiver(Socket socket) {
        try {
            serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get all possible messages from the server and call corresponding method
     */
    @Override
    public void run() {

        try {
            String[] response;
            while ((response = getTokens()) != null) {
                switch (response[0]) {
                    case "Connected":
                    case "User Accepted":
                        handleConnected(response);
                        break;
                    case "Group":
                        handleGroup(response);
                        break;
                    case "Users":
                        handleUsers(response);
                        break;
                    case "GM":
                        handleGM(response);
                        break;
                    case "PM":
                        handlePM(response);
                        break;
                    case "End":
                        handleEnd(response);
                        break;
                    case "ERROR":
                        handleError(response);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Set Logged in and registered to true
     *
     * Server: Connected -Option <username:#user_name>
     * ERROR -Option <reason:”str”>
     *
     * @param response to find username
     */
    private void handleConnected(String[] response) {
        Client.loggedIn = true;
        Client.registered = true;
        System.out.println(response[1].substring(10, response[1].length() - 1) + ": You are connected");
    }

    /**
     * Joining group
     * Send hi message to entrance user and send joined message to others
     * @param response to find the username
     */
    private void handleGroup(String[] response) {
        String username = response[1];
        if (response[2].equals("joined"))
            System.out.println(username + " joined the chat room.");
        else if (response[2].equals("Hi"))
            System.out.println("Hi " + username + ", welcome to the chat room.");
    }

    /**
     * Read the users line and separate them
     *
     * USERS_LIST:
     * <user_name1>|<user_name2>|<user_name3>|<user_name4>
     *
     * @param response to find users
     */
    private void handleUsers(String[] response) {

        StringBuilder onlineUsers = new StringBuilder();

        for (int i = 1; i < response.length; i++) {
            String user = response[i];
            onlineUsers.append(user).append("|");
        }
        if (onlineUsers.length() != 0) {
            onlineUsers.deleteCharAt(onlineUsers.length() - 1);
        }

        System.out.println("USERS_LIST:\r\n" + onlineUsers);
    }

    /**
     * Get group message and show it
     *
     * Server: GM -Option <from:username> -Option <to:GAPNAME> -Option <message_len:#> -Option <msg>
     *
     * @param response to find sender, group name and message body
     */
    private void handleGM(String[] response) throws IOException {
        String sender = response[1].substring(6, response[1].length() - 1);
        String gapName = response[2].substring(4, response[2].length() - 1);
        int messageLength = Integer.parseInt(response[3].substring(13, response[3].length() - 1));
        StringBuilder message = new StringBuilder(response[4]);

        int lineLength = response[4].length();

        //Read line by line
        while (messageLength > lineLength) {
            String line = serverReader.readLine();
            lineLength += line.length() + 2;
            message.append("\r\n").append(line);
        }

        //Check if the user is sender or not
        if (sender.equals(Client.user.username)) {
            System.out.println("You: in " + gapName + ":" + " \"" + message + "\"");
        } else
            System.out.println(sender + " in " + gapName + ":" + " \"" + message + "\"");
    }

    /**
     * Get direct message and show it to both
     *
     * Server: PM -Option <from:username> -Option <to:user_name> -Option <message_len:#> -Option <msg>
     *
     * @param response to find sender and message body
     */
    private void handlePM(String[] response) throws IOException {
        String sender = response[1].substring(6, response[1].length() - 1);
        int messageLength = Integer.parseInt(response[3].substring(13, response[3].length() - 1));
        StringBuilder message = new StringBuilder(response[4]);

        int lineLength = response[4].length();

        while (messageLength > lineLength) {
            String line = serverReader.readLine();
            lineLength += line.length() + 2;
            message.append("\r\n").append(line);
        }

        //Check if the user is sender or not
        if (sender.equals(Client.user.username)) {
            System.out.println("You:" + " \"" + message + "\"");
        } else
            System.out.println(sender + ":" + " \"" + message + "\"");
    }

    /**
     * Get leave of others and show it to user
     * @param response to find leaving opponent
     */
    private void handleEnd(String[] response) {
        String leavingOpponent = response[1];
        System.out.println(leavingOpponent + " left the chat room.");
    }

    /**
     * Get all errors
     * and check if the error related to "opponent not exists", change the opponent to null
     * @param response to get error message
     */
    private void handleError(String[] response) {
        String errorMessage = response[1].substring(9, response[1].length() - 2);
        if (errorMessage.equals("the username you are looking for, doesn't exist")) {
            Client.opponent = null;
        }
        System.err.println(errorMessage);
    }

    //End of handlers

    /**
     * split all coming lines from the server
     * @return the separated line
     */
    private String[] getTokens() throws IOException {
        String line = serverReader.readLine();

        //final condition
        if (line == null) return null;
        return line.split(" -Option ");
    }
}

