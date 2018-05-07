import org.apache.commons.cli.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import static java.lang.System.exit;

public class Pinger {
    private static ArrayList<Double> rtts = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(Option.builder("l").hasArg().desc("local port").build());
        options.addOption(Option.builder("h").hasArg().desc("remote hostname").build());
        options.addOption(Option.builder("r").hasArg().desc("remote port").build());
        options.addOption(Option.builder("c").hasArg().desc("package count").build());
        CommandLineParser parser = new DefaultParser();
        CommandLine parsed_args = parser.parse(options, args);
        check_args(parsed_args);
        if (parsed_args.hasOption("c"))
            ClientMode(Integer.parseInt(parsed_args.getOptionValue("l")), parsed_args.getOptionValue("h"),
                    Integer.parseInt(parsed_args.getOptionValue("r")), Integer.parseInt(parsed_args.getOptionValue("c")));
        else
            ServerMode(Integer.parseInt(parsed_args.getOptionValue("l")));
    }

    private static void check_args(CommandLine args) throws Exception {
        if (args.hasOption("c")) {
            if (args.hasOption("l") && args.hasOption("h") && args.hasOption("r")) return;
        } else {
            if (args.hasOption("l") && !args.hasOption("h") && !args.hasOption("r")) return;
        }
        System.out.println("missing or additional arguments");
        exit(0);
    }


    private static void ClientMode(int localport, String remote_host, int remote_port, int count) throws Exception {
        System.out.println("Pinger Start in Client Mode");
        DatagramSocket datagramSocket = new DatagramSocket(localport);
        datagramSocket.setSoTimeout(1000);
        InetAddress address = InetAddress.getByName(remote_host);

        // Some buffers
        byte[] buf = new byte[12];
        DatagramPacket packet;
        int received = 0;
        for (int seq_num = 0; seq_num < count; seq_num++) {
            Thread.sleep(1000);

            // Send packet
            packet = client_prepare_packet(seq_num, System.currentTimeMillis(), address, remote_port);
            datagramSocket.send(packet);

            // Receive respond
            try {
                do {
                    packet = new DatagramPacket(buf, buf.length);
                    datagramSocket.receive(packet);
                } while(client_seqnum_wrong(packet, seq_num));
            } catch (SocketTimeoutException e) {
                System.out.println(String.format("seq=%d Lost", seq_num));
                continue;
            }
            received++;
            client_process_packet(packet);
        }

        // print statistics
        System.out.print(String.format("sent=%d ", count));
        System.out.print(String.format("received=%d ", received));
        System.out.print(String.format("lost=%.2f%% ", 100.0 - (100. * received / count)));
        ImmutableTriple<Double, Double, Double> rtt_statistics = get_rtt_statistics();
        System.out.println(String.format("rtt mean/avg/max=%.0f/%.2f/%.0f", rtt_statistics.left, rtt_statistics.middle, rtt_statistics.right));
    }

    private static DatagramPacket client_prepare_packet(int seq_num, long time_stamp, InetAddress address, int port) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(12);
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
        dataOutputStream.writeInt(seq_num);
        dataOutputStream.writeLong(time_stamp);
        dataOutputStream.flush();
        byte[] buf = byteArrayOutputStream.toByteArray();
        return new DatagramPacket(buf, buf.length, address, port);
    }

    private static boolean client_seqnum_wrong(DatagramPacket packet, int seq_num) throws IOException {
        return extract_data(packet).left != seq_num;
    }

    private static void client_process_packet(DatagramPacket packet) throws IOException {
        ImmutablePair<Integer, Long> pair = extract_data(packet);
        double rtt = (double) (System.currentTimeMillis() - pair.right);
        rtts.add(rtt);

        System.out.print(String.format("size=%d ", packet.getLength()));
        System.out.print("from=" + packet.getAddress().getHostAddress() + ' ');
        System.out.print(String.format("seq=%d ", pair.left));
        System.out.println(String.format("rtt=%.0f", rtt));
    }

    private static ImmutablePair<Integer, Long> extract_data(DatagramPacket packet) throws IOException {
        byte[] buf = packet.getData();
        DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(buf));
        int seq_num = dataInputStream.readInt();
        long time_stamp = dataInputStream.readLong();
        return new ImmutablePair<>(seq_num, time_stamp);
    }

    private static void ServerMode(int localport) throws Exception {
        System.out.println("Pinger Start in Server Mode");
        DatagramSocket serverSocket = new DatagramSocket(localport);
        byte[] buf = new byte[12];
        DatagramPacket packet;
        while (true) {
            // Wait for client request
            packet = new DatagramPacket(buf, buf.length);
            serverSocket.receive(packet);

            // print statistics
            System.out.print(String.format("time=%d ", System.currentTimeMillis()));
            System.out.print("from="+packet.getAddress().getHostAddress() + ' ');
            System.out.println(String.format("seq=%d", extract_data(packet).left));

            // send back
            serverSocket.send(packet);
        }
    }

    private static ImmutableTriple<Double, Double, Double> get_rtt_statistics() {
        double avg = 0., max = 0, min = 1e9;
        for (double e : rtts) {
            if (e > max) max = e;
            if (e < min) min = e;
            avg += e;
        }
        avg /= rtts.size();
        return new ImmutableTriple<>(min, avg, max);
    }
}
