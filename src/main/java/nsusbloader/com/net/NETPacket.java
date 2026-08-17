/*
    Copyright 2019-2026 Dmitry Isaenko

    This file is part of NS-USBloader.

    NS-USBloader is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NS-USBloader is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NS-USBloader.  If not, see <https://www.gnu.org/licenses/>.
*/
package nsusbloader.com.net;

import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.now;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

public class NETPacket {
    private static final String CODE_200 = """
            HTTP/1.0 200 OK\r
            Server: NS-USBloader\r
            Date: %s\r
            Content-Type: application/octet-stream\r
            Accept-Ranges: bytes\r
            Content-Range: bytes 0-%d/%d\r
            Content-Length: %d\r
            Last-Modified: Thu, 01 Jan 1970 00:00:00 GMT\r
            \r
            """;
    private static final String CODE_206 = """
            HTTP/1.0 206 Partial Content\r
            Server: NS-USBloader\r
            Date: %s\r
            Content-Type: application/octet-stream\r
            Accept-Ranges: bytes\r
            Content-Range: bytes %d-%d/%d\r
            Content-Length: %d\r
            Last-Modified: Thu, 01 Jan 1970 00:00:00 GMT\r
            \r
            """;
    private static final String CODE_400 = """
            HTTP/1.0 400 invalid range\r
            Server: NS-USBloader\r
            Date: %s\r
            Connection: close\r
            Content-Type: text/html;charset=utf-8\r
            Content-Length: 0\r
            \r
            """;
    private static final String CODE_404 = """
            HTTP/1.0 404 Not Found\r
            Server: NS-USBloader\r
            Date: %s\r
            Connection: close\r
            Content-Type: text/html;charset=utf-8\r
            Content-Length: 0\r
            \r
            """;
    private static final String CODE_416 = """
            HTTP/1.0 416 Requested Range Not Satisfiable\r
            Server: NS-USBloader\r
            Date: %s\r
            Connection: close\r
            Content-Type: text/html;charset=utf-8\r
            Content-Length: 0\r
            \r
            """;
    
    private static String getDate() {
        return now(UTC).format(RFC_1123_DATE_TIME);
    }
    
    public static String getCode200(long nspFileSize) {
        return String.format(CODE_200,
                getDate(),
                nspFileSize - 1,
                nspFileSize,
                nspFileSize);
    }

    public static String getCode206(long fileSize, long start, long end) {
        return String.format(CODE_206,
                getDate(),
                start,
                end,
                fileSize,
                end - start + 1);
    }

    public static String getCode404() {
        return String.format(CODE_404, getDate());
    }

    public static String getCode416() {
        return String.format(CODE_416, getDate());
    }

    public static String getCode400() {
        return String.format(CODE_400, getDate());
    }
}
