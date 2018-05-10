import org.apache.commons.cli.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.*;
import java.util.ArrayList;

import static java.lang.System.exit;
import static java.lang.System.out;

/**
 * Created by siqi on 2018/5/8.
 */

public class Iperfer {

    private static byte[] clientGenData = new byte[1024];

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(Option.builder("c").hasArg(false).desc("client mode").build());
        options.addOption(Option.builder("s").hasArg(false).desc("server mode").build());
        options.addOption(Option.builder("h").hasArg().desc("server hostname").build());
        options.addOption(Option.builder("p").hasArg().desc("serverOrlisten port").build());
        options.addOption(Option.builder("t").hasArg().desc("time").build());
        CommandLineParser parser = new DefaultParser();
        CommandLine parsed_args = parser.parse(options, args);
        check_args(parsed_args);
        if (parsed_args.hasOption("c"))
            ClientMode(parsed_args.getOptionValue("h"), Integer.parseInt(parsed_args.getOptionValue("p")), Integer.parseInt(parsed_args.getOptionValue("t")));
        else
            ServerMode(Integer.parseInt(parsed_args.getOptionValue("p")));
    }

    private static void check_args(CommandLine args) throws Exception {
        if (args.hasOption("c")) {
            if (args.hasOption("h") && args.hasOption("p") && args.hasOption("t") && !args.hasOption("s")) return;
        } else if (args.hasOption("s")){
            if (args.hasOption("p") && !args.hasOption("h") && !args.hasOption("t")) return;
        }
        System.out.println("missing or additional arguments");
        exit(0);
    }

    private static void ClientMode(String server_hostname, int server_port, int time) throws Exception {
        System.out.println("Iperfer Start in Client Mode");
        //client asks for linking with "server_hostname" on port "server_port"
        Socket client = new Socket(server_hostname, server_port);
        client.setSoTimeout(10000);

        //get host address
        InetAddress address = InetAddress.getByName(server_hostname);

        OutputStream outputStream = client.getOutputStream();
        InputStream inputStream = client.getInputStream();

        int count = 0;
        double startTime = System.currentTimeMillis();
        while ((int)((System.currentTimeMillis() - startTime) / 1000) <= time) {
            try {
                outputStream.write(clientGenData);
                //get response from server
                byte[] getResponse = new byte[1024];
                int res = inputStream.read(getResponse);
            } catch (SocketTimeoutException e) {
                System.out.println("client: time out! get no response!");
            }

            count++;
        }

        if (client != null) {
            client.close();
        }

        // print statistics
        System.out.print(String.format("time=%d\n", time));
        System.out.print(String.format("sent=%d KB", (int)count * 1024 / 1024));
        System.out.print(String.format(" rate=%f Mbps", (double)count / 1024.0 / time));
    }

    private static void ServerMode(int listen_port) throws Exception {
        System.out.println("Iperfer Start in Server Mode");
        ServerSocket serverSocket = new ServerSocket(listen_port);
        Socket socket = serverSocket.accept();

        int received = 0;
        boolean getFirstBytes = false;
        double startTime = 0.0;
        while (true) {
            InputStream inputStream = socket.getInputStream();
            if (inputStream.read() == -1) {
                break;
            }
            byte[] input = new byte[1024];
            int tmp = inputStream.read(input);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(input);
            received++;
            if (!getFirstBytes) {
                getFirstBytes = true;
                startTime = System.currentTimeMillis();
            }
        }
        int usedTime = (int)((System.currentTimeMillis() - startTime) / 1000);
        socket.close();
        serverSocket.close();

        // print statistics
        System.out.print(String.format("time=%d\n", usedTime));
        System.out.print(String.format("received=%d KB", (int)received * 1024 / 1024));
        System.out.print(String.format(" rate=%f Mbps", (double)received / 1024.0 / usedTime));
    }
}
