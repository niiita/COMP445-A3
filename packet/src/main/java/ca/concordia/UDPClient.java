package ca.concordia;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import java.util.Arrays;
import java.io.FileWriter;
import java.io.IOException;


import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;

public class UDPClient {

    private static final Logger logger = LoggerFactory.getLogger(UDPClient.class);

    private static boolean patternCheck = false;
    private final static String HTTP_METHOD_GET = "GET";
    private final static String HTTP_METHOD_POST = "POST";
    private final static String FILE_OPTION = "-f";
    private final static String DATA_OPTION = "-d";
    private final static String VERBOSE_OPTION = "-v";
    public final static int DEFAULT_PORT = 3001;

    private static boolean isVerbose = false;
    private static boolean isData = false;
    private static boolean isFile = false;
    private static boolean isHeader = false;

    private static String dataString = "";
    private static String headerString = "";
	private static File filename;

    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.accepts("router-host", "Router hostname")
                .withOptionalArg()
                .defaultsTo("localhost");

        parser.accepts("router-port", "Router port number")
                .withOptionalArg()
                .defaultsTo("3000");

        parser.accepts("server-host", "EchoServer hostname")
                .withOptionalArg()
                .defaultsTo("localhost");

        parser.accepts("server-port", "EchoServer listening port")
                .withOptionalArg()
                .defaultsTo("8007");

        OptionSet opts = parser.parse(args);

        // Router address
        String routerHost = (String) opts.valueOf("router-host");
        int routerPort = Integer.parseInt((String) opts.valueOf("router-port"));

        // Server address
        String serverHost = (String) opts.valueOf("server-host");
        int serverPort = Integer.parseInt((String) opts.valueOf("server-port"));

        SocketAddress routerAddress = new InetSocketAddress(routerHost, routerPort);
        InetSocketAddress serverAddress = new InetSocketAddress(serverHost, serverPort);

        System.out.println(routerAddress);
        System.out.println(serverAddress);

        String value;
        Console console = System.console();
        if (console == null) {
            System.out.println("No console available");
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            System.exit(0);
        }
        
        System.out.println("COMP 445 - Assignment #1 - Tse-Chun Lau (29676279), Joo Yeon Lee (25612950)\n");
        
        //HTTPC
        while (patternCheck != true) {
        	
        	System.out.println("MAIN MENU");
        	System.out.println("1- Enter \"1\" to submit an httpc request");
        	System.out.println("2- Enter \"2\" to open the help menu");
        	System.out.println("Enter anything else to exit the application");
        	String option = console.readLine();
        	
        	if(option.equals("1")) {

	            value = console.readLine("Enter string (0 to return to the main menu): ");
	       
	            //Exit if the value entered is 0
	            if (value.equals("0")) {
	                continue;
	            }
	
	            //Regex pattern; separate entities grouped within parenthesis
	            Pattern pattern = Pattern.compile("httpc(\\s+(get|post))((\\s+-v)?(\\s+-h\\s+([^\\s]+))?(\\s+-d\\s+('.+'))?(\\s+-f\\s+([^\\s]+))?)(\\s+'((http[s]?:\\/\\/www\\.|http[s]?:\\/\\/|www\\.)?([^\\/]+)(\\/.+)?)'*)");
	
	            // Now create matcher object.
	            Matcher m = pattern.matcher(value);
	
	            if (m.find()) {
	
	                patternCheck = true;
	                /*
	                 * Group 2: Get or Post				m.group(2)
	                 * Group 4: verbose -v				m.group(4)
	                 * Group 5: header -h				m.group(5)
	                 * Group 6: Header content 			m.group(6)
	                 * Group 7: data -d					m.group(7)
	                 * Group 8: Data content			m.group(8)
	                 * Group 9: file -f					m.group(9)
	                 * Group 10: File content			m.group(10)
	                 * Group 12: URL					m.group(12)
	                 * Group 14: Host					m.group(14)
	                 * Group 15: Path					m.group(15)
	                 */
	
	                /* 
	                 * To print out the different groups from Regex
	                for(int i = 0; i < 15; i++) {
	                		System.out.println("Group " + i + ": " + m.group(i));
	                }
	                **/
	
	                //POST or GET to upper case
	                String type = m.group(2).toUpperCase();
	
	                //Trim the host
	                String host = m.group(14).replaceAll("'", "").trim();
	
	                //Assign the path if not empty
	                String path = "";
	
	                if (m.group(15) != null) {
	                    path = m.group(15).replaceAll("'", "").trim();
	                }
	
	
	                //Check if -v
	                isVerbose = m.group(4) != null ? true : false;
	
	                //THIS MIGHT NEED TO BE MODIFIED FOR POST
	                //Check if -h
	                isHeader = m.group(5) != null ? true : false;
	                if (isHeader) {
	                    headerString = m.group(6);
	                }
	
	                //Check if -d
	                isData = m.group(7) != null ? true : false;
	                if (isData) {
	                    dataString = m.group(8);
	                }
	
	                //Check if -f
	                isFile = m.group(9) != null ? true : false;
	                if (isFile) {
	                    filename = new File(m.group(10));
	                }
	
	                //Additional check GET method for cURL
	                if (type.equals(HTTP_METHOD_GET) && (isData || isFile)) {
	                    System.out.println("The GET request cannot be combined with the -f or -d options.");
	                    patternCheck = false;
	                    continue;
	                }
	
	                //Additional check on POST method for cURL
	                if (type.equals(HTTP_METHOD_POST) && isData && isFile) {
	                    System.out.println("The POST request cannot be combined with the -f and the -d options.");
	                    patternCheck = false;
	                    continue;
	                }
                    
                    if (type.equals("GET")) {
                        getRequest(routerAddress, serverAddress);
                    } else if (type.equals("POST")) {
                        postRequest(routerAddress, serverAddress);
                    }
					// get(path, host, type, null, isData, isFile, isVerbose, filename, routerAddress, serverAddress);
					
					//if we can get redirect working
					// bonusRedirect();
	            } else {
	                System.out.println("The input was incorrect. Please try again. Enter '0' to exit");
	            }
        	}
        	
        	//Help
        	else if (option.equals("2")) {
        		
        		System.out.println("Enter one of the following: ");
        		System.out.println("1- Enter \"1\" to display the general help");
        		System.out.println("2- Enter \"2\" to display the get help");
        		System.out.println("3- Enter \"3\" to display the post help");
        		System.out.println("Enter anything else to return to the main menu");
        		String helpOption = console.readLine();
        		
        		if(helpOption.equals("1") || helpOption.equals("2") || helpOption.equals("3")) {
        			helpMenu(helpOption);
        			System.out.println("Enter any key to return to the main menu: ");
        			console.readLine();
        		}
        		else {
        			continue;
        		}
        	}
        	
        	//Exit
        	else  {
        		System.exit(0);
        	}
        

    }
}

// private static void get(String path, String host, String type, String query, boolean isData, boolean isFile, boolean isVerbose, File file, SocketAddress routerAddr, InetSocketAddress serverAddr) throws IOException ){
private static void getRequest(SocketAddress routerAddr, InetSocketAddress serverAddr) throws IOException {
        System.out.println("get");
    try(DatagramChannel channel = DatagramChannel.open()){
        String msg = "GET REQUEST";
        Packet p = new Packet.Builder()
                .setType(0)
                .setSequenceNumber(1L)
                .setPortNumber(serverAddr.getPort())
                .setPeerAddress(serverAddr.getAddress())
                .setPayload(msg.getBytes())
                .create();
        channel.send(p.toBuffer(), routerAddr);

        logger.info("Sending \"{}\" to router at {}", msg, routerAddr);

        // Try to receive a packet within timeout.
        channel.configureBlocking(false);
        Selector selector = Selector.open();
        channel.register(selector, OP_READ);
        logger.info("Waiting for the response");
        selector.select(5000);

        Set<SelectionKey> keys = selector.selectedKeys();
        if(keys.isEmpty()){
            logger.error("No response after timeout");
            return;
        }

        // We just want a single response.
        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
        SocketAddress router = channel.receive(buf);
        buf.flip();
        Packet resp = Packet.fromBuffer(buf);
        logger.info("Packet: {}", resp);
        logger.info("Router: {}", router);
        String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
        logger.info("Payload: {}",  payload);

        keys.clear();
    }
}

private static void postRequest(SocketAddress routerAddr, InetSocketAddress serverAddr) throws IOException {
    System.out.println("post");
    try(DatagramChannel channel = DatagramChannel.open()){
        String msg = "POST REQUEST";
        Packet p = new Packet.Builder()
                .setType(0)
                .setSequenceNumber(1L)
                .setPortNumber(serverAddr.getPort())
                .setPeerAddress(serverAddr.getAddress())
                .setPayload(msg.getBytes())
                .create();
        channel.send(p.toBuffer(), routerAddr);

        logger.info("Sending \"{}\" to router at {}", msg, routerAddr);

        // Try to receive a packet within timeout.
        channel.configureBlocking(false);
        Selector selector = Selector.open();
        channel.register(selector, OP_READ);
        logger.info("Waiting for the response");
        selector.select(5000);

        Set<SelectionKey> keys = selector.selectedKeys();
        if(keys.isEmpty()){
            logger.error("No response after timeout");
            return;
        }

        // We just want a single response.
        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
        SocketAddress router = channel.receive(buf);
        buf.flip();
        Packet resp = Packet.fromBuffer(buf);
        logger.info("Packet: {}", resp);
        logger.info("Router: {}", router);
        String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
        logger.info("Payload: {}",  payload);

        keys.clear();
    }
}


//Displays the help menu
public static void helpMenu(String helpOption) {
    	
    if(helpOption.equals("1")) {
        System.out.println("httpc help" + "\n");
        System.out.println("httpc is a curl-like application but supports HTTP protocol only.");
        System.out.println("Usage:");   
        System.out.println("\t" + "httpc command [arguments]");
        System.out.println("The commands are:");
        System.out.println("\t" + "get" + "\t" + "executes a HTTP GET request and prints the response.");
        System.out.println("\t" + "post" + "\t" + "executes a HTTP POST request and prints the response.");
        System.out.println("\t" + "help" + "\t" + "prints this screen." + "\n");
        System.out.println("Use \"httpc help [command]\" for more information about a command.");
    }

    else if (helpOption.equals("2")) {
        System.out.println("httpc help get" + "\n");
        System.out.println("usage: httpc get [-v] [-h key:value] URL" + "\n");
        System.out.println("Get executes a HTTP GET request for a given URL." + "\n");
        System.out.println("\t" + "-v" + "\t\t" + "Prints the detail of the response such as protocol, status, and headers.");
        System.out.println("\t" + "-h key:value" + "\t" + "Associates headers to HTTP Request with the format 'key:value'.");
    }
    else {
        System.out.println("httpc help post" + "\n");
        System.out.println("usage: httpc post [-v] [-h key:value] [-d inline-data] [-f file] URL" + "\n");
        System.out.println("Post executes a HTTP POST request for a given URL with inline data or from file." + "\n");
        System.out.println("\t" + "-v" + "\t\t" + "Prints the detail of the response such as protocol, status, and headers.");
        System.out.println("\t" + "-h key:value" + "\t" + "Associates headers to HTTP Request with the format 'key:value'.");
        System.out.println("\t" + "-d string" + "\t" + "Associates an inline data to the body HTTP POST request.");
        System.out.println("\t" + "-f file" + "\t\t" + "Associates the content of a file to the body HTTP POST request." + "\n");
        System.out.println("Either [-d] or [-f] can be used but not both.");
    }	
}


}
