package com.ultimateflashy.vpn;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Tests whether the configured proxy can open a tunnel to example.com:443. */
public final class ProxyTester {
    private ProxyTester() {}

    public record Result(boolean success, String message, long millis) {}

    public static Result test(String type, String host, int port, String username, String password) {
        long started = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 7000);
            socket.setSoTimeout(7000);
            if ("SOCKS5".equals(type)) {
                testSocks5(socket, username, password);
            } else {
                testHttpConnect(socket, username, password);
            }
            return new Result(true, "Proxy tunnel test succeeded", System.currentTimeMillis() - started);
        } catch (Exception exception) {
            String message = exception.getMessage();
            if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
            return new Result(false, "Test failed: " + message, System.currentTimeMillis() - started);
        }
    }

    private static void testSocks5(Socket socket, String username, String password) throws IOException {
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        boolean hasUser = username != null && !username.isBlank();
        out.write(hasUser ? new byte[] {5, 2, 0, 2} : new byte[] {5, 1, 0});
        out.flush();

        int version = in.read();
        int method = in.read();
        if (version != 5 || method < 0 || method == 0xFF) {
            throw new IOException("SOCKS5 negotiation rejected");
        }
        if (method == 2) {
            byte[] user = (username == null ? "" : username).getBytes(StandardCharsets.UTF_8);
            byte[] pass = (password == null ? "" : password).getBytes(StandardCharsets.UTF_8);
            if (user.length > 255 || pass.length > 255) throw new IOException("Credentials too long");
            out.write(1);
            out.write(user.length);
            out.write(user);
            out.write(pass.length);
            out.write(pass);
            out.flush();
            if (in.read() != 1 || in.read() != 0) throw new IOException("SOCKS5 authentication failed");
        } else if (method != 0) {
            throw new IOException("Unsupported SOCKS5 authentication method: " + method);
        }

        byte[] domain = "example.com".getBytes(StandardCharsets.US_ASCII);
        out.write(new byte[] {5, 1, 0, 3, (byte) domain.length});
        out.write(domain);
        out.write(new byte[] {1, (byte) 0xBB}); // destination port 443
        out.flush();

        byte[] reply = new byte[4];
        readFully(in, reply);
        if (reply[0] != 5 || reply[1] != 0) throw new IOException("SOCKS5 CONNECT rejected (code " + (reply[1] & 255) + ")");
        int addressType = reply[3] & 255;
        if (addressType == 1) {
            skipFully(in, 4);
        } else if (addressType == 3) {
            int length = in.read();
            if (length < 0) throw new EOFException("Missing SOCKS5 address length");
            skipFully(in, length);
        } else if (addressType == 4) {
            skipFully(in, 16);
        } else {
            throw new IOException("Invalid SOCKS5 reply address type");
        }
        skipFully(in, 2);
    }

    private static void testHttpConnect(Socket socket, String username, String password) throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        StringBuilder request = new StringBuilder()
            .append("CONNECT example.com:443 HTTP/1.1\r\n")
            .append("Host: example.com:443\r\n")
            .append("Proxy-Connection: Keep-Alive\r\n");
        if (username != null && !username.isBlank()) {
            String credentials = username + ":" + (password == null ? "" : password);
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            request.append("Proxy-Authorization: Basic ").append(encoded).append("\r\n");
        }
        request.append("\r\n");
        out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
        out.flush();

        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int value;
        while ((value = in.read()) != -1 && value != '\n') {
            if (value != '\r') line.write(value);
            if (line.size() > 2048) throw new IOException("HTTP response line too long");
        }
        String status = line.toString(StandardCharsets.US_ASCII);
        if (!status.matches("HTTP/\\d(?:\\.\\d)? 200(?: .*|$)")) {
            throw new IOException("HTTP CONNECT rejected: " + (status.isBlank() ? "no response" : status));
        }
    }

    private static void readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int count = in.read(buffer, offset, buffer.length - offset);
            if (count < 0) throw new EOFException("Unexpected end of SOCKS5 response");
            offset += count;
        }
    }

    private static void skipFully(InputStream in, int count) throws IOException {
        while (count > 0) {
            long skipped = in.skip(count);
            if (skipped <= 0) {
                if (in.read() < 0) throw new EOFException("Unexpected end of SOCKS5 response");
                skipped = 1;
            }
            count -= (int) skipped;
        }
    }
}
