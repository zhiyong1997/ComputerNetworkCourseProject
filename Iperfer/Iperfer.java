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
    private static final double Bytes2Mb = 8. / 1e6;

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
		byte clientGenData[] = new byte[1024];
		int sentDataBytes = 0;
		long startTime = 0, endTime = 0;
		try {
			Socket client = new Socket(server_hostname, server_port);
			OutputStream outputStream = client.getOutputStream();
			
			startTime = System.currentTimeMillis();
			endTime = System.currentTimeMillis();
			while ((endTime - startTime) / 1000. < time) {
				outputStream.write(clientGenData);
				outputStream.flush();
				endTime = System.currentTimeMillis();
				sentDataBytes += 1024;
			}
			client.close();
		} catch (Exception e) {
			e.printStackTrace();
			exit(1);
		}
        double usedTime = (endTime - startTime) / 1000.;
        System.out.print(String.format("sent=%.2f KB ", sentDataBytes / 1024.));
        System.out.print(String.format("rate=%.2f Mbps\n", sentDataBytes * Bytes2Mb / usedTime));
    }

    private static void ServerMode(int listen_port) throws Exception {
        System.out.println("Iperfer Start in Server Mode");
		ServerSocket serverSocket = new ServerSocket(listen_port);
		Socket socket = serverSocket.accept();
		InputStream inputStream = socket.getInputStream();
		byte[] input = new byte[1024];
		
        int receivedBytes = 0;
        inputStream.read(input);
        long startTime = System.currentTimeMillis();
        while (true) {
            if (inputStream.read() == -1) {
                break;
            }
            inputStream.read(input);
            receivedBytes += 1024;
        }
        double usedTime = (System.currentTimeMillis() - startTime) / 1000.;
		socket.close();
		serverSocket.close();

        System.out.print(String.format("received=%.2f KB", receivedBytes / 1024.));
        System.out.print(String.format(" rate=%.2f Mbps\n", receivedBytes * Bytes2Mb / usedTime));
    }
}

