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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class UDPServer {

    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);

    private void listenAndServe(int port) {
        for (;;) {
            NetworkMessage receivedMessage = Transporter.listenForMessage(port);
            if (receivedMessage == null) {
                continue;
            }

            String[] req = receivedMessage.getPayload().split(" ", -2);
            String path = "";
            for (int i = 1; i < req.length - 1; i++) {
                path = path.concat(" " + req[i]);
            }

            String responsePayload = "";

            if (req[0].equalsIgnoreCase("GET")) {
                logger.info("GET request received: " + req[1]);
                responsePayload = get(path);
            } else if (req[0].equalsIgnoreCase("POST")) {
                logger.info("POST request received: " + req[1]);
                responsePayload = post(path);
            } else {
                responsePayload = "Request invalid";
            }

            Transporter.transport(responsePayload, receivedMessage.getRouterAddress(), receivedMessage.getPeerAddress(),
                    receivedMessage.getPeerPort(), false);
            logger.info("Response sent successfully!");
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

    public static String get(String path) {
        String body = "";
        String response = "";
        String filename;
        if (path.length() >= 1) {

            if (path.contains("-o")) {
                filename = path.substring(path.indexOf("-o") + 3, path.indexOf(".txt")) + ".txt";

                try {
                    BufferedReader in = new BufferedReader(new FileReader(filename));
                    String line = "";
                    StringBuilder StringBuilder = new StringBuilder();

                    while ((line = in.readLine()) != null) {
                        String formattedLine = line.replaceAll("[\\{\\}]", "").replaceAll("\\s", " ");

                        String[] linesArray = formattedLine.split(",");
                        for (int i = 0; i < linesArray.length; i++) {

                            StringBuilder.append(linesArray[i] + ",");
                            // System.out.println(linesArray[i]);
                        }
                        body = "{" + StringBuilder.toString().substring(0, StringBuilder.length() - 1) + "}";
                        response = "Received\r\n" + "HTTP/1.0 200 OK\r\n" + "Content-Length: " + body.length() + "\r\n"
                                + "Content-Disposition: inline" + "\r\n"
                                + "Content-Disposition: attachment; filename=\"" + filename + "\"" + "\r\n"
                                + "Content-Type: application/json\r\n\r\n" + body;

                    }
                    in.close();
                    logger.info("Response sent to client\n" + response);

                } catch (Exception e) {
                    // Maybe we can create an actual 404 response from server here?
                    logger.info("Sorry the file you are looking for does not exist");
                    response = "HTTP/1.0 404 Not Found\r\n" + "User Agent: Concordia\r\n";
                    logger.info("Response sent to client\n" + response);
                    // e.printStackTrace();
                }

            } else {
                try (Stream<Path> walk = Files.walk(Paths.get(System.getProperty("user.dir")))) {

                    List<String> result = walk.filter(Files::isRegularFile).map(x -> x.toString())
                            .collect(Collectors.toList());
                    for (final ListIterator<String> i = result.listIterator(); i.hasNext();) {
                        final String element = i.next();
                        body = body.concat(element.substring(element.lastIndexOf("\\"), element.length()) + "\n");
                    }
                    response = "Received\r\n" + "HTTP/1.0 200 OK\r\n" + "Content-Length: " + body.length() + "\r\n"
                            + "Content-Disposition: inline" + "\r\n" + "Content-Disposition: attachment; filename=\""
                            + "NA" + "\"" + "\r\n" + "Content-Type: application/json\r\n\r\n"
                            + "Here is the list of files in the current directory:\r\n" + body;

                } catch (IOException e) {
                    e.printStackTrace();
                }
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
        boolean inline = false;
        logger.info("Processing POST request");
        System.out.println("path: " + path);
        if (path.contains("-f")) {
            directory = path.substring(path.indexOf("-f") + 3, path.lastIndexOf("/"));
            filename = path.substring(path.lastIndexOf("/") + 1, path.indexOf(".txt") + 4);
            System.out.println(directory);
            System.out.println(filename);
        }

        if (path.contains("-d")) {
            directory = path.substring(1, path.lastIndexOf("/") + 1);
            directory = directory.replace("/", "\\");
            filename = path.substring(path.lastIndexOf("/") + 1, path.indexOf(".txt") + 4);
            if (path.contains("{")) {
                data = path.substring(path.indexOf("{"), path.lastIndexOf("}") + 1);
                inline = true;
            }

            path = directory.concat(filename);
        }

        // System.out.println("Working Directory = " + System.getProperty("user.dir"));

        // ***NOTE*** if conditions hard coded ATM must refactor to verify path and
        // filename
        // probably need to get existing directories and files to compare given path
        File tmpDir = new File(System.getProperty("user.dir") + directory);
        if (tmpDir.exists()) {// directory exist
            System.out.println("directory exists");
            File tmpFile = new File(System.getProperty("user.dir") + directory + "\\" + filename);

            if (tmpFile.exists()) { // filename already exists
                // append to corresponding file
                System.out.println("filename exists");
                try {
                    if (inline) {
                        BufferedWriter extWriter = new BufferedWriter(
                                new FileWriter(System.getProperty("user.dir") + directory + "\\" + filename));
                        extWriter.newLine();
                        extWriter.write(data);
                        extWriter.close();
                        body = data;
                        response = "Received\r\n" + "POST " + path + " HTTP/1.0\r\n"
                                + "Content-Type:application/json\r\n" + "Content-Length: " + body.length() + "\r\n"
                                + "\r\n" + body;

                    } else {

                        BufferedReader in = new BufferedReader(
                                new FileReader(System.getProperty("user.dir") + directory + "\\" + filename));
                        String line = "";
                        StringBuilder StringBuilder = new StringBuilder();

                        while ((line = in.readLine()) != null) {
                            String formattedLine = line.replaceAll("[\\{\\}]", "").replaceAll("\\s", " ");

                            String[] linesArray = formattedLine.split(",");
                            for (int i = 0; i < linesArray.length; i++) {

                                StringBuilder.append(linesArray[i] + ",");
                                // System.out.println(linesArray[i]);
                            }
                            body = "{" + StringBuilder.toString().substring(0, StringBuilder.length() - 1) + "}";

                            response = "Received\r\n" + "POST " + path + " HTTP/1.0\r\n"
                                    + "Content-Type:application/json\r\n" + "Content-Length: " + body.length() + "\r\n"
                                    + "\r\n" + body;
                            in.close();
                        }

                    }
                    logger.info("Response sent to client\n" + response);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else { // if filename not in directory
                     // create new file
                try {
                    PrintWriter extWriter = new PrintWriter(
                            System.getProperty("user.dir") + directory + "\\" + filename);
                    extWriter.println(data);
                    extWriter.close();
                    body = data;
                    response = "Received\r\n" + "POST " + path + " HTTP/1.0\r\n" + "Content-Type:application/json\r\n"
                            + "Content-Length: " + body.length() + "\r\n" + "\r\n" + body;

                    logger.info("Response sent to client\n" + response);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        } else {
            System.out.println("pk");
            // must create directory
            // Creating a File object
            File file = new File(System.getProperty("user.dir") + directory);
            // Creating the directory
            boolean bool = file.mkdirs();
            if (bool) {
                logger.info("Directory created successfully");
            } else {
                logger.info("Sorry couldnt create specified directory");
            }
            try {
                File newFile = new File(System.getProperty("user.dir") + directory + "\\" + filename);
                if (newFile.createNewFile()) {
                    logger.info("File created: " + newFile.getName());
                    try {
                        PrintWriter extWriter = new PrintWriter(
                                System.getProperty("user.dir") + directory + "\\" + filename);
                        extWriter.println(data);
                        extWriter.close();
                        body = data;
                        response = "Received\r\n" + "POST " + path + " HTTP/1.0\r\n"
                                + "Content-Type:application/json\r\n" + "Content-Length: " + body.length() + "\r\n"
                                + "\r\n" + body;

                        logger.info("Response sent to client\n" + response);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    logger.info("File already exists.");
                }
            } catch (IOException e) {
                logger.info("An error occurred.");
                e.printStackTrace();
            }
        }

        return response;

    }

    public static String reassemble(String path) {

        return path;
    }

}