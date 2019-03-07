import java.io.IOException;

public class reliableUDP {

    public static void main(String [] args) {

        Server sv = new Server();

        try {
            sv.startServer();
        } catch (IOException ioex) {
            ioex.printStackTrace();
        }

    }

}
