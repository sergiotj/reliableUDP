import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

public class Packet {

    private byte[] fullPacket;
    private int seqNumber;
    private int hash;

    public Packet(byte[] fullPacket) {

        this.fullPacket = fullPacket;

    }

    public int getSeqNumber() {

        // sequence number
        this.seqNumber = this.fullPacket[0];

        return (seqNumber & 0xFF);
    }

    public byte[] retrieveData(int sizeOfPacket, int sizeOfHeader) {

        byte[] fileByteArray = new byte[sizeOfPacket - sizeOfHeader];

        // Retrieve data from message
        System.arraycopy(this.fullPacket, sizeOfHeader, fileByteArray, 0, sizeOfPacket - sizeOfHeader);

        return fileByteArray;
    }

    public static byte[][] fileToChunks(File filename, int sizeOfPacket) throws IOException {

        // Create a byte array to store file
        byte[] fileContent = Files.readAllBytes(filename.toPath());

        // Divide byte array in chunks
        byte[][] chunks = new byte[(int)Math.ceil(fileContent.length / (double) 1021)][1021];

        for(int i = 0, start = 0; i < chunks.length; i++) {

            chunks[i] = Arrays.copyOfRange(fileContent, start, start + sizeOfPacket);
            start += sizeOfPacket;
        }

        return chunks;
    }


}
