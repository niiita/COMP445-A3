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
import java.util.Arrays;

public class UDPServer {

    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);

    private void listenAndServe(int port) {
        for (;;) {
            NetworkMessage receivedMessage = Transporter.listenForMessage(port);
            if (receivedMessage == null) {
                continue;
            }

            String[] req = receivedMessage.getPayload().split(" ", -2);
            System.out.println("req array" + Arrays.toString(req));
            // String path = "";
            // for (int i = 1; i < req.length - 1; i++) {
            // path = path.concat(req[i]);
            // }

            String responsePayload = "";

            if (req[0].equalsIgnoreCase("GET")) {
                logger.info("GET request received: " + req[1]);
                responsePayload = get(req);
            } else if (req[0].equalsIgnoreCase("POST")) {
                logger.info("POST request received: " + Arrays.toString(req).split(" ", -2));
                responsePayload = post(req);
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

    public static String get(String[] req) {
        String body = "";
        String response = "{\"A3\" : \"sample body for get request\"}";
        String filename;
        System.out.println(Arrays.toString(req));
        System.out.println(req[1]);
        if (req.length >= 1) {

            if (req[1].contains(".txt")) {
                System.out.println("has a path" + req[1]);

                filename = req[1];

                System.out.println("filename" + filename);
                try {
                    BufferedReader in = new BufferedReader(new FileReader(System.getProperty("user.dir") + filename));
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

                    return response;

                } catch (Exception e) {
                    // Maybe we can create an actual 404 response from server here?
                    logger.info("Sorry the file you are looking for does not exist");
                    response = "HTTP/1.0 404 Not Found\r\n" + "User Agent: Concordia\r\n";
                    logger.info("Response sent to client\n" + response);
                    // e.printStackTrace();
                }

            }

            if (req[2].contains("-o")) {
                filename = req[3];
                System.out.println("filename" + filename);

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

                return response;

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
                // body = "{\"A3\" : \"sample body for get request\"}";

                // response = "Received\r\n" + "HTTP/1.0 200 ok\r\n" + "Content-Length: " +
                // body.length() + "\r\n"
                // + "Content-Disposition: inline" + "\r\n"
                // + "Content-Disposition: attachment; filename=\"default.json\"" + "\r\n"
                // + "Content-Type: application/json\r\n\r\n" + body;
                return response;
            }
        }
        return response;

    }

    public static String post(String[] req) {
        String body = "";
        String response = "";
        String directory = "";
        String filename = "";
        String newFilename = "";
        String data = "{\"A3\" : \"default body for post request\"}";
        boolean inline = false;
        String path = req[1];

        logger.info("Processing POST request");
        System.out.println("path: " + path);
        if (Arrays.toString(req).contains("-f")) {
            directory = req[1].replace("/", "\\");
            filename = req[3].replace("/", "\\");

            System.out.println(directory);
            System.out.println(filename);
            System.out.println(System.getProperty("user.dir") + filename);

            File fileInput = new File(System.getProperty("user.dir") + filename);
            if (fileInput.exists()) {// check given file exists

                System.out.println("directory to read from exists");
                try {
                    BufferedReader in = new BufferedReader(new FileReader(System.getProperty("user.dir") + filename));
                    String line = "";
                    StringBuilder StringBuilder = new StringBuilder();

                    while ((line = in.readLine()) != null) {
                        String formattedLine = line.replaceAll("[\\{\\}]", "").replaceAll("\\s", " ");

                        String[] linesArray = formattedLine.split(",");
                        for (int i = 0; i < linesArray.length; i++) {

                            StringBuilder.append(linesArray[i] + ",");
                            // System.out.println(linesArray[i]);
                        }
                        data = "{" + StringBuilder.toString().substring(0, StringBuilder.length() - 1) + "}";
                        System.out.println("1: " + data);

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
                System.out.println("directory:" + directory);
                int count = (int) directory.chars().filter(ch -> ch == '.').count();
                System.out.println(count);
                if (count > 1) {
                    directory = directory.substring(1, directory.lastIndexOf("\\") + 1);
                    newFilename = directory.substring(directory.lastIndexOf("\\") + 1, directory.indexOf(".txt") + 4);
                } else {
                    newFilename = directory;
                    directory = "";

                    System.out.println(newFilename);

                }
                // directory = directory.substring(1, directory.lastIndexOf("\\") + 1);
                // directory = directory.replace("/", "\\");
                // System.out.println(directory.substring(directory.lastIndexOf("\\") + 1,
                // directory.indexOf(".txt") + 4));
                // String newFilename = directory.substring(directory.lastIndexOf("\\") + 1,
                // directory.indexOf(".txt") + 4);

                File outputDirectory = new File(System.getProperty("user.dir") + directory);
                if (outputDirectory.exists()) {
                    File outputFilename = new File(System.getProperty("user.dir") + directory + newFilename);

                    if (outputFilename.exists()) { // check given directory file exists
                        System.out.println(
                                "filename exists: " + System.getProperty("user.dir") + directory + newFilename);

                        try {
                            BufferedWriter extWriter = new BufferedWriter(
                                    new FileWriter((System.getProperty("user.dir") + directory + newFilename), true));
                            extWriter.newLine();
                            extWriter.write(data);
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
                        boolean bool = false;
                        if (directory.length() > 1) {
                            // Creating the directory if not root
                            File file = new File(System.getProperty("user.dir") + directory);
                            bool = file.mkdirs();
                        }

                        if (bool) {
                            logger.info("Directory created successfully");
                        } else {
                            logger.info("Directory already exists or couldn't be created");
                        }
                        try {
                            File newFile = new File(System.getProperty("user.dir") + directory + newFilename);
                            if (newFile.createNewFile()) {
                                logger.info("File created: " + newFile.getName());
                                try {
                                    PrintWriter extWriter = new PrintWriter(
                                            System.getProperty("user.dir") + directory + newFilename);
                                    extWriter.println(data);
                                    extWriter.close();
                                    body = data;
                                    response = "Received\r\n" + "POST " + path + " HTTP/1.0\r\n"
                                            + "Content-Type:application/json\r\n" + "Content-Length: " + body.length()
                                            + "\r\n" + "\r\n" + body;

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

                }
            } else {
                response = "Sorry there is no such file or directory to read from";
            }

        } else if (Arrays.toString(req).contains("-d")) {
            System.out.println("-d");
            directory = path.substring(0, path.lastIndexOf("/") + 1);
            directory = directory.replace("/", "\\");
            filename = path.substring(path.lastIndexOf("/") + 1, path.indexOf(".txt") + 4);
            data = "";
            System.out.println(req[3]);
            System.out.println(req.length);
            for (int i = 3; i < req.length; i++) {
                data = data.concat(req[i] + " ");
            }
            data.replaceAll(", $", "");
            System.out.println("data" + data);

            if (data.contains("}")) {
                data = data.substring(0, data.length() - 10);
                System.out.println(data);
            }

            inline = true;

            path = directory.concat(filename);

            File tmpDir = new File(System.getProperty("user.dir") + directory);
            if (tmpDir.exists()) {// directory exist
                System.out.println("directory exists");
                File tmpFile = new File(System.getProperty("user.dir") + directory + "\\" + filename);

                if (tmpFile.exists()) { // filename already exists
                    // append to corresponding file
                    System.out.println("filename exists");
                    try {
                        if (inline) {
                            System.out
                                    .println("inline " + System.getProperty("user.dir") + directory + "\\" + filename);
                            BufferedWriter extWriter = new BufferedWriter(
                                    new FileWriter(System.getProperty("user.dir") + directory + "\\" + filename, true));
                            extWriter.newLine();
                            extWriter.write(data);
                            extWriter.close();
                            body = data;
                            response = "Received\r\n" + "POST " + path + " HTTP/1.0\r\n"
                                    + "Content-Type:application/json\r\n" + "Content-Length: " + body.length() + "\r\n"
                                    + "\r\n" + body;

                        } else {
                            System.out.println("not");

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
                                        + "Content-Type:application/json\r\n" + "Content-Length: " + body.length()
                                        + "\r\n" + "\r\n" + body;
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
                        extWriter.write(data);
                        extWriter.close();
                        body = data;
                        response = "Received\r\n" + "POST " + path + " HTTP/1.0\r\n"
                                + "Content-Type:application/json\r\n" + "Content-Length: " + body.length() + "\r\n"
                                + "\r\n" + body;

                        logger.info("Response sent to client\n" + response);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            } else {
                System.out.println("creating new directory" + directory);
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
        }

        return response;

    }

    public static String reassemble(String path) {

        return path;
    }

}