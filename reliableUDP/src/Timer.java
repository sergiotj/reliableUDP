import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Timer implements Runnable {

    private AtomicInteger dataReceived;
    private AtomicBoolean stop;

    public Timer(AtomicInteger dataReceived, AtomicBoolean stop) {

        this.dataReceived = dataReceived;
        this.stop = stop;
    }

    @Override
    public void run() {

        int secs = 0;

        while (stop.get() == false) {

            try {

                Thread.sleep(1000);
                secs++;

                double velocity = dataReceived.get() / secs * 0.0000076294;
                String velocityS = String.format("%.2g", velocity);

                //System.out.println("Velocidade: " + velocityS + " MBs/seg");

            } catch (InterruptedException iex) {

                iex.printStackTrace();
            }


        }


    }
}