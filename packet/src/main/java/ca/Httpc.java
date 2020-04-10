package ca;

public class Httpc {

    public static String createGetRequest(String path) {
        return String.format("GET %s HTTP/1.0", path);
    }

    public static String createPostRequest(String path) {
        return String.format("POST %s HTTP/1.0", path);
    }
}