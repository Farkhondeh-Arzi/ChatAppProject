import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Base64;

public class Database {

    private Statement statement;

    public void connect() {
        try {
            Class.forName("com.mysql.jdbc.Driver");

            Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_app", "root", "hourshid2001");
            statement = connection.createStatement();

        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    public void insert(User user) throws SQLException {
        String codedPassword = hashCode(user.password);
        statement.execute("INSERT INTO clients VALUES(\"" + user.username + "\", \"" + codedPassword + "\")");
    }

    public void delete(User user) throws SQLException {
        statement.execute("DELETE FROM clients WHERE username = \"" + user.username + "\"");
    }

    public User get(String username) throws SQLException {
        ResultSet result = statement.executeQuery("SELECT * FROM clients WHERE username = \"" + username + "\"");

        User user = new User();
        if (result.next()) {
            user.username = result.getString("username");
            user.password = hashDecode(result.getString("password"));
        } else {
            return null;
        }

        return user;
    }

    public String hashCode(String password) {
        return Base64.getUrlEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
    }

    public String hashDecode(String codedPassword) {
        return new String(Base64.getDecoder().decode(codedPassword));
    }
}

class User {
    String username;
    String password;
}
