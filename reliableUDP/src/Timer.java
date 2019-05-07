import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Timer implements Runnable {

    private AtomicInteger dataReceived;
    private AtomicBoolean stop;
    private int nrParts;
    private AtomicInteger iWritten;

    public Timer(AtomicInteger dataReceived, AtomicBoolean stop, int nrParts, AtomicInteger iWritten) {

        this.dataReceived = dataReceived;
        this.stop = stop;
        this.nrParts = nrParts;
        this.iWritten = iWritten;
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

                double status = iWritten.get() * 100 / nrParts;

                System.out.println("Velocidade: " + velocityS + " MBs/seg" + "----- Estado: " + status + "%");

            } catch (InterruptedException iex) {

                iex.printStackTrace();
            }


        }


    }
}