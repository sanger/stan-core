package uk.ac.sanger.sccp.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.*;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

/**
 * Base class for a client that needs to post and get json.
 * @author dr6
 */
public abstract class BaseHttpClient {
    private int timeout = 2000; // 2 s
    private Proxy proxy;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Gets the connect and read timeout (milliseconds). Zero is no timeout.
     * @return the timeout in milliseconds.
     */
    public int getTimeout() {
        return this.timeout;
    }

    /**
     * Sets the connect and read timeout (milliseconds). Zero is no timeout.
     * @param timeout the timeout in milliseconds
     * @exception IllegalArgumentException if {@code timeout} is negative
     */
    public void setTimeout(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout cannot be negative");
        }
        this.timeout = timeout;
    }

    public Proxy getProxy() {
        return this.proxy;
    }

    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    protected boolean responseIsGood(int responseCode) {
        return (responseCode >= 200 && responseCode < 300);
    }

    /**
     * Reads the response from a connection's inputstream and converts it to the given type.
     * @param connection the connection to read
     * @param returnType the class of the expected return type
     * @param <T> the expected return type
     * @return a response of the given type read from the given connection
     * @exception IOException communication problem
     */
    protected <T> T readReturnValue(URLConnection connection, Class<T> returnType) throws IOException {
        if (returnType==null || returnType==Void.class) {
            return null;
        }
        String string = getResponseString(connection.getInputStream());
        if (returnType==String.class) {
            //noinspection unchecked
            return (T) string;
        }
        return objectMapper.readValue(string, returnType);
    }

    /**
     * Posts some JSON and returns a response whose type is as indicated by the supplied class.
     * @param url the address to post to
     * @param data the data to post
     * @param jsonReturnType the class of the expected return type
     * @param <T> the expected return type
     * @return a response whose type is indicated by the {@code jsonReturnType} argument
     * @exception IOException there was a communication problem
     */
    protected <T> T postJson(URL url, Object data, Class<T> jsonReturnType) throws IOException {
        HttpURLConnection connection = openConnection(url);
        try {
            setHeaders(connection);
            attemptPost(data, connection);
            return readReturnValue(connection, jsonReturnType);
        } finally {
            connection.disconnect();
        }
    }

    protected HttpURLConnection openConnection(URL url) throws IOException {
        Proxy proxy = getProxy();
        URLConnection con;
        if (proxy==null) {
            con = url.openConnection();
        } else {
            con = url.openConnection(proxy);
        }
        int timeout = getTimeout();
        con.setReadTimeout(timeout);
        con.setConnectTimeout(timeout);
        return (HttpURLConnection) con;
    }

    protected void attemptPost(Object data, HttpURLConnection connection) throws IOException {
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.connect();
        try (OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream())) {
            out.write(data.toString());
            out.flush();
            int responseCode = connection.getResponseCode();
            if (!responseIsGood(responseCode)) {
                if (responseCode == HTTP_NOT_FOUND) {
                    throw new Http404Exception();
                }
                throw new IOException(responseCode + " - " + getResponseString(connection.getErrorStream()));
            }
        }
    }

    protected void setHeaders(HttpURLConnection connection) {
        setUsualHeaders(connection);
    }

    /**
     * Reads from an {@code InputStream} line by line, concatenates the lines and returns the result.
     * @param is stream to read
     * @return the text read from the input stream
     * @exception IOException there was a communication problem
     */
    public static String getResponseString(InputStream is) throws IOException {
        if (is==null) {
            return null;
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * Sets up the headers on a connection.
     * The headers specify JSON in and JSON out
     * @param connection the connection to set the headers on
     */
    public static void setUsualHeaders(HttpURLConnection connection) {
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
    }

    /**
     * Custom exception indicating an HTTP 404
     * @author dr6
     */
    public static class Http404Exception extends IOException {
        public Http404Exception() {
            super(HTTP_NOT_FOUND + " - NOT FOUND");
        }
    }
}
