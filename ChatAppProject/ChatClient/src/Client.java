/**
 * Have an instance of sender and receiver
 */
public class Client extends Thread {

    //Common fields between sender and receiver
    public static boolean loggedIn = false;
    public static boolean registered = false;
    public static User user = new User();
    public static String opponent = null;

    @Override
    public void run() {

        ClientSender sender = new ClientSender("localhost", 50000);

        if (sender.connect()) {
            ClientReceiver receiver = new ClientReceiver(sender.getSocket());

            //start threads
            sender.start();
            receiver.start();
        } else {
            System.err.println("Connection refused");
        }

    }
}
