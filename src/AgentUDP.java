import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class AgentUDP extends Thread {

    public void receptionDataFlow(DatagramSocket socket, int sizeOfPacket, int sizeOfHeader, int nrParts) throws IOException {

        int iWritten = 0;

        FileOutputStream outToFile = new FileOutputStream(file);

        ArrayList<Integer> missingParts = new ArrayList<>();

        HashMap<Integer, byte[]> buffer = new HashMap<>();

        for (int i = 0; i < nrParts; i++) {

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

    public void dispatchDataFlow(DatagramSocket socket, int sizeOfPacket, int sizeOfHeader, int nrParts) throws IOException {

        File file = new File(file);

        // Create a byte array to store file
        byte[] fileContent = Files.readAllBytes(file.toPath());

        // Divide byte array in chunks
        byte[][] ret = new byte[(int)Math.ceil(fileContent.length / (double) 1021)][1021];

        for(int i = 0, start = 0; i < ret.length; i++) {

            ret[i] = Arrays.copyOfRange(fileContent, start, start + sizeOfPacket);
            start += sizeOfPacket;
        }

        // list of all chunks that were not sent with success to destiny
        ArrayList<Integer> notSentWithSuccess = new ArrayList<>();

        for (int i = 0; i < nrParts; i++) {

            notSentWithSuccess.add(i);
        }

        int sequenceNumber = 0;
        int ackSequence = 0;
        int w = 5;

        while (w > 0) {

            while (!notSentWithSuccess.isEmpty()) {

                int seqNumber = notSentWithSuccess.get(0);

                // Create message
                byte[] message = new byte[1024];
                message[0] = (byte) (seqNumber >> 8);
                message[1] = (byte) (seqNumber);

                System.arraycopy(ret[seqNumber], 0, message, 3, 1021);

                DatagramPacket sendPacket = new DatagramPacket(message, message.length, address, getPort());
                socket.send(sendPacket);

                w--;

                System.out.println("Sent: Sequence number = " + sequenceNumber);
            }
        }

        // receiving acknowledgments
        while (true) {

            // Create another packet by setting a byte array and creating data gram packet
            byte[] ack = new byte[2];
            DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

            try {

                // set the socket timeout for the packet acknowledgment
                socket.setSoTimeout(50);
                socket.receive(ackpack);

                ackSequence = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);

            }

            // we did not receive an ack
            catch (SocketTimeoutException e) {
                System.out.println("Socket timed out waiting for ACKs");
            }

            if (ackSequence == sequenceNumber) {

                // removes seqNumber from list
                notSentWithSuccess.remove(Integer.valueOf(sequenceNumber));

                System.out.println("Ack received: Sequence Number = " + ackSequence);
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
