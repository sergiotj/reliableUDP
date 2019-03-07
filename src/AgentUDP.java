import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;

public class AgentUDP extends Thread {

    public void receive(DatagramSocket socket) throws IOException {

        int partsReceived = 0;
        int sizeOfPacket = 1024;
        int sizeOfHeader = 3;

        int iWritten = 0;

        FileOutputStream outToFile = new FileOutputStream(file);

        ArrayList<Integer> missingParts = new ArrayList<>();

        HashMap<Integer, byte[]> buffer = new HashMap<>();

        for (int i = 1; i <= nrParts; i++) {

            missingParts.add(i);
        }

        while(true) {

            byte[] message = new byte[sizeOfPacket];

            DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
            socket.setSoTimeout(0);
            socket.receive(receivedPacket);

            message = receivedPacket.getData();

            Packet p = new Packet(message);

            // Get port and address for sending acknowledgment
            InetAddress address = receivedPacket.getAddress();
            int port = receivedPacket.getPort();

            // Retrieve data from message
            byte[] newData = p.retrieveData(sizeOfPacket, sizeOfHeader);

            // Send acknowledgement
            sendAck(p.getSeqNumber(), socket, address, port);

            // removes seqNumber from list of parts missing
            missingParts.remove(Integer.valueOf(p.getSeqNumber()));

            if (iWritten == p.getSeqNumber()) {

                outToFile.write(newData);
                iWritten++;
            }

            else {

                // if part cant be written, it should go to buffer
                buffer.put(p.getSeqNumber(), newData);
            }

            partsReceived++;

            if (missingParts.isEmpty()) {

                // all parts received...
                while (iWritten <= nrParts) {

                    outToFile.write(buffer.get(iWritten));
                    iWritten++;
                }

                System.out.println("Ficheiro recebido com sucesso.");

                break;
            }

            else {

                // there are missing parts... so, while loop should continue
                continue;
            }

        }


    }

    private void sendAck(int seqNumber, DatagramSocket socket, InetAddress address, int port) throws IOException {

        // send acknowledgement
        byte[] ackPacket = new byte[2];

        ackPacket[0] = (byte) (seqNumber >> 8);
        ackPacket[1] = (byte) (seqNumber);

        // the datagram packet to be sent
        DatagramPacket acknowledgement = new DatagramPacket(ackPacket, ackPacket.length, address, port);
        socket.send(acknowledgement);

        System.out.println("Sent ack: Sequence Number = " + seqNumber);
    }

}
