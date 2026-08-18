package network;

import nsusbloader.NSLMain;
import nsusbloader.com.net.NETCommunications;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@DisplayName("NETCommunications: handshake + HTTP response structure")
@TestInstance(PER_CLASS)
public class NetCommunicationsTest {

    private static final String TEST1_NAME = "test1.nsp";
    private static final String TEST2_NAME = "test2.nsp";
    private static final String TEST3_NAME = "test3.nsp";

    private final long TEST1_SIZE = 0x40_0000;
    private final long TEST2_SIZE = 0x80_0000;
    private final long TEST3_SIZE = 300;

    private final String HOST_IP = "127.0.0.1";
    private final int HOST_PORT = 6062;
    private final int SWITCH_PORT = 2000;
    private final int SO_TIMEOUT_MS = (int) SECONDS.toMillis(20);

    @TempDir
    private File testFilesLocation;

    private File test1Nsp;
    private File test2Nsp;
    private File test3Nsp;

    private ServerSocket switchSocket;
    private Thread netThread;
    private Handshake handshake;

    @BeforeAll
    void beforeAll() throws Exception {
        NSLMain.isCli = true;

        test1Nsp = createFileWithPattern(TEST1_NAME, TEST1_SIZE, 1);
        test2Nsp = createFileWithPattern(TEST2_NAME, TEST2_SIZE, 2);
        test3Nsp = createFileWithPattern(TEST3_NAME, TEST3_SIZE, 3);

        switchSocket = new ServerSocket(SWITCH_PORT, 32, InetAddress.getByName(HOST_IP));
        switchSocket.setSoTimeout(SO_TIMEOUT_MS);

        var files = new ArrayList<>(List.of(test1Nsp, test2Nsp, test3Nsp));
        var netCommunications = new NETCommunications(
                files,
                HOST_IP,
                false,
                HOST_IP,
                String.valueOf(HOST_PORT),
                "");

        netThread = Thread.ofVirtual()
                .name("net-sut")
                .start(netCommunications);
        handshake = receiveHandshake();

        assertEquals(handshake.payload().length, handshake.lengthPrefix(),
                "handshake 4-byte prefix must equal the payload length");
    }

    @AfterAll
    void afterAll() throws Exception {
        try (var socket = new Socket(InetAddress.getByName(HOST_IP), HOST_PORT)) {
            socket.setSoTimeout(SO_TIMEOUT_MS);
            var out = socket.getOutputStream();
            out.write("DROP\r\n\r\n".getBytes(UTF_8));
            out.flush();
            socket.shutdownOutput();
        }
        if (netThread != null) {
            netThread.join(SECONDS.toMillis(10));
            if (netThread.isAlive())
                fail("NETCommunications did not terminate the serve loop after DROP");
        }
        if (switchSocket != null)
            switchSocket.close();
    }

    @DisplayName("handshake: length-prefixed body lists every file exactly once")
    @Test
    void handshakeSendsLengthPrefixedFileList() {
        var text = new String(handshake.payload(), UTF_8);
        assertTrue(text.endsWith("\n"), "every entry must be terminated with LF");
        var lines = text.split("\n", -1);
        var entries = new HashSet<>(List.of(Arrays.copyOf(lines, lines.length - 1)));
        assertEquals(Set.of(
                "127.0.0.1:%d/%s".formatted(HOST_PORT, TEST1_NAME),
                "127.0.0.1:%d/%s".formatted(HOST_PORT, TEST2_NAME),
                "127.0.0.1:%d/%s".formatted(HOST_PORT, TEST3_NAME)),
                entries, "handshake body must contain exactly the announced file list");
    }

    @DisplayName("HEAD /test1.nsp returns 200 with the full header structure, no body")
    @Test
    void headRequestReturns200WithFullStructure() throws Exception {
        var p = serveRequest("HEAD /%s HTTP/1.0".formatted(TEST1_NAME),
                "Host: 127.0.0.1");
        assert200(p, TEST1_SIZE);
    }

    @DisplayName("GET test1 Range: bytes=0-4095 returns 206 with matching body")
    @Test
    void getWithExplicitRangeReturns206AndExactBytes() throws Exception {
        var p = serveRequest("GET /%s HTTP/1.0".formatted(TEST1_NAME),
                "Range: bytes=0-4095");
        assert206(p, TEST1_SIZE, 0, 4095);
        assertArrayEquals(readSlice(test1Nsp, 0, 4096), p.body(), "body must equal the requested file bytes");
    }

    @DisplayName("GET test1 Range: bytes=1000000-1000999 returns 206 with matching body")
    @Test
    void getWithMidFileRangeReturns206AndExactBytes() throws Exception {
        var start = 1_000_000L;
        var end = 1_000_999L;
        var p = serveRequest("GET /%s HTTP/1.0".formatted(TEST1_NAME),
                "Range: bytes=%d-%d".formatted(start, end));
        assert206(p, TEST1_SIZE, start, end);
        assertArrayEquals(readSlice(test1Nsp, start, end - start + 1), p.body(), "body must equal the requested file bytes");
    }

    @DisplayName("GET test1 single-byte range returns 206 with exactly one byte")
    @Test
    void getSingleByteRangeReturns206AndExactByte() throws Exception {
        var offset = 250_000L;
        var p = serveRequest("GET /%s HTTP/1.0".formatted(TEST1_NAME),
                "Range: bytes=%d-%d".formatted(offset, offset));
        assert206(p, TEST1_SIZE, offset, offset);
        assertArrayEquals(readSlice(test1Nsp, offset, 1), p.body(), "body must be exactly one byte");
    }

    @DisplayName("GET test2 tail range returns 206 with matching body")
    @Test
    void getWithTailRangeReturns206AndExactBytes() throws Exception {
        var start = TEST2_SIZE - 608;
        var end = TEST2_SIZE - 1;
        var p = serveRequest("GET /%s HTTP/1.0".formatted(TEST2_NAME),
                "Range: bytes=%d-%d".formatted(start, end));
        assert206(p, TEST2_SIZE, start, end);
        assertArrayEquals(readSlice(test2Nsp, start, 608), p.body(), "body must equal the requested file bytes");
    }

    @DisplayName("GET test1 open-ended range bytes=500- returns 206 for the full tail")
    @Test
    void getWithOpenEndedRangeReturnsWholeTail() throws Exception {
        var start = 500L;
        var end = TEST1_SIZE - 1;
        var p = serveRequest("GET /%s HTTP/1.0".formatted(TEST1_NAME),
                "Range: bytes=500-");
        assert206(p, TEST1_SIZE, start, end);
        assertArrayEquals(readSlice(test1Nsp, start, TEST1_SIZE - start), p.body(), "body must be the full tail of the file");
    }

    @DisplayName("GET unknown file returns 404 structure")
    @Test
    void getUnknownFileReturns404() throws Exception {
        var p = serveRequest("GET /nope.nsp HTTP/1.0", "Host: 127.0.0.1");
        assert4xx(p, "HTTP/1.0 404 Not Found");
    }

    @DisplayName("GET Range: bytes=5000-1000 (start > end) returns 400")
    @Test
    void invertedRangeReturns400() throws Exception {
        var p = serveRequest("GET /%s HTTP/1.0".formatted(TEST1_NAME),
                "Range: bytes=5000-1000");
        assert4xx(p, "HTTP/1.0 400 invalid range");
    }

    @DisplayName("GET Range: bytes=abc-def returns 400")
    @Test
    void malformedRangeReturns400() throws Exception {
        var p = serveRequest("GET /%s HTTP/1.0".formatted(TEST1_NAME),
                "Range: bytes=abc-def");
        assert4xx(p, "HTTP/1.0 400 invalid range");
    }

    @DisplayName("GET small file (300 B) with suffix range returns 416")
    @Test
    void suffixRangeOnTinyFileReturns416() throws Exception {
        var p = serveRequest("GET /%s HTTP/1.0".formatted(TEST3_NAME),
                "Range: bytes=-100");
        assert4xx(p, "HTTP/1.0 416 Requested Range Not Satisfiable");
    }

    // response-structure assertions

    private void assert200(HttpPacket p, long fileSize) {
        assertAll(
                () -> assertEquals("HTTP/1.0 200 OK", p.statusLine()),
                () -> assertEquals("NS-USBloader", p.headers().get("Server")),
                () -> assertRfc1123(p.headers().get("Date")),
                () -> assertEquals("application/octet-stream", p.headers().get("Content-type")),
                () -> assertEquals("bytes", p.headers().get("Accept-Ranges")),
                () -> assertEquals("bytes 0-%d/%d".formatted(fileSize - 1, fileSize), p.headers().get("Content-Range")),
                () -> assertEquals(Long.toString(fileSize), p.headers().get("Content-Length")),
                () -> assertEquals("Thu, 01 Jan 1970 00:00:00 GMT", p.headers().get("Last-Modified")),
                () -> assertEquals(0, p.body().length, "200 (HEAD) must not carry a body"));
    }

    private void assert206(HttpPacket p, long fileSize, long start, long end) {
        assertAll(
                () -> assertEquals("HTTP/1.0 206 Partial Content", p.statusLine()),
                () -> assertEquals("NS-USBloader", p.headers().get("Server")),
                () -> assertRfc1123(p.headers().get("Date")),
                () -> assertEquals("application/octet-stream", p.headers().get("Content-type")),
                () -> assertEquals("bytes", p.headers().get("Accept-Ranges")),
                () -> assertEquals("bytes %d-%d/%d".formatted(start, end, fileSize), p.headers().get("Content-Range")),
                () -> assertEquals(Long.toString(end - start + 1), p.headers().get("Content-Length")),
                () -> assertEquals("Thu, 01 Jan 1970 00:00:00 GMT", p.headers().get("Last-Modified")),
                () -> assertEquals(end - start + 1, p.body().length, "body length must match Content-Length"));
    }

    private void assert4xx(HttpPacket p, String expectedStatusLine) {
        assertAll(
                () -> assertEquals(expectedStatusLine, p.statusLine()),
                () -> assertEquals("NS-USBloader", p.headers().get("Server")),
                () -> assertRfc1123(p.headers().get("Date")),
                () -> assertEquals("close", p.headers().get("Connection")),
                () -> assertEquals("text/html;charset=utf-8", p.headers().get("Content-Type")),
                () -> assertEquals("0", p.headers().get("Content-Length")),
                () -> assertEquals(0, p.body().length, "error responses must not carry a body"));
    }

    private void assertRfc1123(String dateField) {
        var dt = assertDoesNotThrow(() -> ZonedDateTime.parse(dateField, DateTimeFormatter.RFC_1123_DATE_TIME),
                "Date header must be a valid RFC 1123 date-time, got: %s".formatted(dateField));
        assertEquals(UTC, dt.getOffset(), "Date must be expressed in UTC");
        assertTrue(Duration.between(ZonedDateTime.now(UTC), dt).abs().toMinutes() < 1,
                "Date must be close to the current time, got: %s".formatted(dateField));
    }

    private record Handshake(int lengthPrefix, byte[] payload) {}

    private record HttpPacket(String head, byte[] body) {
        static HttpPacket of(byte[] raw, int headerEnd) {
            var head = new String(raw, 0, headerEnd, UTF_8);
            // Use the bytes that were actually received. A HEAD (200) and every
            // error reply carry a Content-Length but no body on the wire, so slicing
            // by Content-Length would fabricate a spurious (zero-padded) body.
            return new HttpPacket(head, Arrays.copyOfRange(raw, headerEnd, raw.length));
        }

        String statusLine() {
            return head.split("\\r\\n", 2)[0];
        }

        Map<String, String> headers() {
            var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
            for (var line : head.split("\\r\\n")) {
                var idx = line.indexOf(':');
                if (idx > 0)
                    map.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
            }
            return map;
        }
    }

    private HttpPacket serveRequest(String... requestLines) throws Exception {
        try (var socket = new Socket(InetAddress.getByName(HOST_IP), HOST_PORT)) {
            socket.setSoTimeout(SO_TIMEOUT_MS);
            var out = socket.getOutputStream();
            // HTTP requests are terminated by a blank line (CRLF CRLF). The
            // SUT only invokes its request handler when it reads an empty line,
            // so a lone trailing CRLF is not enough.
            out.write((String.join("\r\n", requestLines) + "\r\n\r\n").getBytes(UTF_8));
            out.flush();
            socket.shutdownOutput();

            var received = new ByteArrayOutputStream();
            var in = socket.getInputStream();
            while (!responseComplete(received)) {
                var chunk = new byte[65536];
                var n = in.read(chunk);
                if (n < 0)
                    break;
                received.write(chunk, 0, n);
            }

            var raw = received.toByteArray();
            var headerEnd = findHeaderEnd(raw);
            assertTrue(headerEnd >= 0,
                    "no complete response headers received; raw: %s"
                            .formatted(new String(raw, UTF_8).replace('\n', ' ')));
            return HttpPacket.of(raw, headerEnd);
        }
    }

    private boolean responseComplete(ByteArrayOutputStream received) {
        var raw = received.toByteArray();
        var headerEnd = findHeaderEnd(raw);
        if (headerEnd < 0)
            return false;
        var contentLength = contentLengthOf(raw, headerEnd);
        return received.size() >= headerEnd + contentLength;
    }

    private int findHeaderEnd(byte[] raw) {
        for (var i = 0; i + 3 < raw.length; i++) {
            if (raw[i] == '\r' && raw[i + 1] == '\n' && raw[i + 2] == '\r' && raw[i + 3] == '\n')
                return i + 4;
        }
        for (var i = 0; i + 1 < raw.length; i++) {
            if (raw[i] == '\n' && raw[i + 1] == '\n')
                return i + 2;
        }
        return -1;
    }

    private long contentLengthOf(byte[] raw, int headerEnd) {
        var head = new String(raw, 0, headerEnd, UTF_8);
        for (var line : head.split("\\r\\n")) {
            var trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith("content-length:"))
                return Long.parseLong(trimmed.substring("content-length:".length()).trim());
        }
        return 0;
    }

    private Handshake receiveHandshake() throws IOException {
        try (var conn = switchSocket.accept()) {
            conn.setSoTimeout(SO_TIMEOUT_MS);
            var in = conn.getInputStream();
            var lenBuf = readFully(in, 4);
            var len = ByteBuffer.wrap(lenBuf).getInt();
            assertTrue(len > 0 && len < 1_000_000,
                    "sanity: handshake length out of bounds: %d".formatted(len));
            return new Handshake(len, readFully(in, len));
        }
    }

    private byte[] readFully(InputStream in, int n) throws IOException {
        var buf = new byte[n];
        var off = 0;
        while (off < n) {
            var k = in.read(buf, off, n - off);
            if (k < 0)
                throw new EOFException("peer closed connection before handshake been complete");
            off += k;
        }
        return buf;
    }

    private File createFileWithPattern(String name, long size, int seed) throws IOException {
        var file = new File(testFilesLocation, name);
        try (var fos = new FileOutputStream(file)) {
            var buf = new byte[65536];
            long pos = 0;
            while (pos < size) {
                int n = (int) Math.min(buf.length, size - pos);
                for (var i = 0; i < n; i++)
                    buf[i] = (byte) (((pos + i) * seed) % 251);
                fos.write(buf, 0, n);
                pos += n;
            }
        }
        assertEquals(size, file.length());
        return file;
    }

    private byte[] readSlice(File file, long start, long length) throws IOException {
        try (var in = new FileInputStream(file)) {
            if (in.skip(start) != start)
                throw new IOException("unable to skip to offset " + start);
            var out = new ByteArrayOutputStream((int) Math.min(length, Integer.MAX_VALUE));
            long remaining = length;
            while (remaining > 0) {
                var chunk = new byte[(int) Math.min(65536, remaining)];
                var n = in.read(chunk);
                if (n < 0)
                    throw new IOException("unexpected EOF while reading slice");
                out.write(chunk, 0, n);
                remaining -= n;
            }
            return out.toByteArray();
        }
    }
}
