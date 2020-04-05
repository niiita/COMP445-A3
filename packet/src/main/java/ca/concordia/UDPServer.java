package ca.concordia;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class UDPServer {

    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);

    private static boolean isVerbose = false;
    private static boolean patternCheck = false;
    private static File filename;

    private void listenAndServe(int port) throws IOException {

        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));
            logger.info("EchoServer is listening at {}", channel.getLocalAddress());
            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN).order(ByteOrder.BIG_ENDIAN);

            System.out.println("1: " + buf);

            for (;;) {
                buf.clear();
                SocketAddress router = channel.receive(buf);

                // Parse a packet from the received raw data.
                buf.flip();
                Packet packet = Packet.fromBuffer(buf);
                buf.flip();

                String payload = new String(packet.getPayload(), UTF_8);
                logger.info("Packet: {}", packet);
                logger.info("Payload: {}", payload);
                logger.info("Router: {}", router);

                String[] req = payload.toString().split(" ", -2);
                System.out.println(req[0]);
                String path = "";
                for (int i = 1; i < req.length - 1; i++) {

                    path = path.concat(" " + req[i]);

                }

                System.out.println(path);

                String testing = "";

                if (req[0].toString().equalsIgnoreCase("GET")) {
                    System.out.println("Processing GET request: " + req[1]);
                    testing = get(path);
                } else if (req[0].toString().equalsIgnoreCase("POST")) {
                    System.out.println("Processing POST request: " + req[1]);
                    testing = post(path);
                } else {
                    testing = "Request invalid";
                }
                // System.out.println(testing);

                // Send the response to the router not the client.
                // The peer address of the packet is the address of the client already.
                // We can use toBuilder to copy properties of the current packet.
                // This demonstrate how to create a new packet from an existing packet.
                // String testing = "klk";
                // Packet resp = packet.toBuilder().setPayload(payload.getBytes()).create();
                Packet resp = packet.toBuilder().setPayload(testing.getBytes()).create();

                channel.send(resp.toBuffer(), router);

            }
        }
    }

    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("port", "p"), "Listening port").withOptionalArg().defaultsTo("8007");

        OptionSet opts = parser.parse(args);
        int port = Integer.parseInt((String) opts.valueOf("port"));
        UDPServer server = new UDPServer();
        server.listenAndServe(port);
    }

    // Lists all the files in a given directory
    public static void getFileNames(String path) {
        File directory = new File(path);

        File[] items = directory.listFiles();

        System.out.println(items);
        String line = "";
        if (items != null) {
            for (int i = 0; i < items.length; i++) {
                if (items[i].isFile())
                    line += items[i].getName() + "\r\n";
                else if (items[i].isDirectory())
                    line += "<DIRECTORY>" + items[i].getName() + "\r\n";
            }
            formattedOutputResponse(line);
        } else {
            System.out.println(errorResponse(404));
        }
    }

    public static String get(String path) {
        System.out.println("in get(): " + path);
        String body = "";
        String response = "";
        String filename;
        // If there is no path (i.e. just localhost:3001 is called)
        if (path.contains("-o")) {
            filename = path.substring(path.indexOf("-o") + 3, path.indexOf(".txt")) + ".txt";
            System.out.println("filename:" + filename);

            try {
                BufferedReader in = new BufferedReader(new FileReader(filename));
                String line = "";
                StringBuilder StringBuilder = new StringBuilder();

                while ((line = in.readLine()) != null) {
                    System.out.println("while?");
                    String formattedLine = line.replaceAll("[\\{\\}]", "").replaceAll("\\s", "");

                    String[] linesArray = formattedLine.split(",");
                    for (int i = 0; i < linesArray.length; i++) {

                        StringBuilder.append(linesArray[i] + ",");
                        System.out.println(linesArray[i]);
                    }
                    body = "{" + StringBuilder.toString().substring(0, StringBuilder.length() - 1) + "}";
                    response = "Received\r\n" + "HTTP/1.0 200 OK\r\n" + "Content-Length: " + body.length() + "\r\n"
                            + "Content-Disposition: inline" + "\r\n" + "Content-Disposition: attachment; filename=\""
                            + filename + "\"" + "\r\n" + "Content-Type: application/json\r\n\r\n" + body;

                }
                in.close();
                System.out.println("Response sent to client\n" + response);

            } catch (Exception e) {
                // Maybe we can create an actual 404 response from server here?
                System.out.println("Sorry the file you are looking for does not exist");
                response = "HTTP/1.0 404 Not Found\r\n" + "User Agent: Concordia\r\n";
                System.out.println("Response sent to client\n" + response);
                // e.printStackTrace();
            }

        } else {
            body = "{\"A3\" : \"sample body for get request\"}";

            response = "Received\r\n" + "HTTP/1.0 200 ok\r\n" + "Content-Length: " + body.length() + "\r\n"
                    + "Content-Disposition: inline" + "\r\n"
                    + "Content-Disposition: attachment; filename=\"default.json\"" + "\r\n"
                    + "Content-Type: application/json\r\n\r\n" + body;

        }
        return response;
    }

    public static String post(String path) {
        String body = "";
        String response = "";
        String directory = "";
        String filename = "";
        String data = "{\"A3\" : \"default body for post request\"}";
        System.out.println("in POST");
        System.out.println(path);
        if (path.contains("-f")) {
            directory = path.substring(path.indexOf("-f") + 3, path.lastIndexOf("/"));
            System.out.println("directory:" + directory);
            filename = path.substring(path.lastIndexOf("/") + 1, path.indexOf(".txt") + 4);
            System.out.println("filename:" + filename);
        }

        if (path.contains("-d")) {
            directory = path.substring(1, path.lastIndexOf("/") + 1);
            System.out.println(directory);
            directory = directory.replace("/", "\\");
            System.out.println(directory);
            // directory = "\\domingo\\";
            // System.out.println(directory);
            filename = path.substring(path.lastIndexOf("/") + 1, path.indexOf(".txt") + 4);
            System.out.println(filename);
            data = path.substring(path.indexOf("{"), path.lastIndexOf("}") + 1);
            System.out.println(data);
            path = directory.concat(filename);
        }

        System.out.println("Working Directory = " + System.getProperty("user.dir"));

        // ***NOTE*** if conditions hard coded ATM must refactor to verify path and
        // filename
        // probably need to get existing directories and files to compare given path
        File tmpDir = new File(System.getProperty("user.dir") + directory);
        if (tmpDir.exists()) {// directory exist
            System.out.println("directorio existe");
            File tmpFile = new File(System.getProperty("user.dir") + directory + "\\" + filename);

            if (tmpFile.exists()) { // filename already exists
                // append to corresponding file
                System.out.println("file existe");
                try {
                    FileWriter f = new FileWriter(System.getProperty("user.dir") + directory + "\\" + filename, true);
                    BufferedWriter b = new BufferedWriter(f);
                    PrintWriter extWriter = new PrintWriter(b);
                    extWriter.println(data);
                    extWriter.close();
                    body = data;
                    response = "Received\r\n" + "POST " + path + " HTTP/1.0\r\n" + "Content-Type:application/json\r\n"
                            + "Content-Length: " + body.length() + "\r\n" + "\r\n" + body;

                    System.out.println("Response sent to client\n" + response);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else { // if filename not in directory
                     // create new file
                try {
                    PrintWriter extWriter = new PrintWriter(
                            System.getProperty("user.dir") + directory + "\\" + filename);
                    extWriter.write(data);
                    extWriter.close();
                    body = data;
                    response = "Received\r\n" + "POST " + path + " HTTP/1.0\r\n" + "Content-Type:application/json\r\n"
                            + "Content-Length: " + body.length() + "\r\n" + "\r\n" + body;

                    System.out.println("Response sent to client\n" + response);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        } else {
            // must create directory
            // Creating a File object
            File file = new File(System.getProperty("user.dir") + directory);
            // Creating the directory
            boolean bool = file.mkdirs();
            if (bool) {
                System.out.println("Directory created successfully");
            } else {
                System.out.println("Sorry couldnt create specified directory");
            }
            try {
                File newFile = new File(System.getProperty("user.dir") + directory + "\\" + filename);
                if (newFile.createNewFile()) {
                    System.out.println("File created: " + newFile.getName());
                    try {
                        PrintWriter extWriter = new PrintWriter(
                                System.getProperty("user.dir") + directory + "\\" + filename);
                        extWriter.write(data);
                        extWriter.close();
                        body = data;
                        response = "Received\r\n" + "POST " + path + " HTTP/1.0\r\n"
                                + "Content-Type:application/json\r\n" + "Content-Length: " + body.length() + "\r\n"
                                + "\r\n" + body;

                        System.out.println("Response sent to client\n" + response);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("File already exists.");
                }
            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }
        }

        return response;

    }

    // Returns an error response
    private static String errorResponse(int errorStatusCode) {
        switch (errorStatusCode) {
            case 403:
                return "403 Forbidden";
            case 404:
                return "404 Not Found";
            default:
                return "400 Bad Request";
        }
    }

    private static void formattedOutputResponse(String response) {

        if (isVerbose) {
            System.out.println(response);
        } else {
            String[] responseFormatted = response.split("\n\n");

            for (int i = 1; i < responseFormatted.length; i++)
                System.out.println(responseFormatted[i]);
        }

    }
}