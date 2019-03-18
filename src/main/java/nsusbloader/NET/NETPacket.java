package nsusbloader.NET;

import nsusbloader.NSLMain;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class NETPacket {
    private static final String CODE_200 =
                                            "HTTP/1.0 200 OK\r\n" +
                                            "Server: NS-USBloader-"+NSLMain.appVersion+"\r\n" +
                                            "Date: %s\r\n" +
                                            "Content-type: application/octet-stream\r\n" +
                                            "Accept-Ranges: bytes\r\n" +
                                            "Content-Range: bytes 0-%d/%d\r\n" +
                                            "Content-Length: %d\r\n" +
                                            "Last-Modified: Thu, 01 Jan 1970 00:00:00 GMT\r\n\r\n";
    private static final String CODE_206 =
                                            "HTTP/1.0 206 Partial Content\r\n"+
                                            "Server: NS-USBloader-"+NSLMain.appVersion+"\r\n" +
                                            "Date: %s\r\n" +
                                            "Content-type: application/octet-stream\r\n"+
                                            "Accept-Ranges: bytes\r\n"+
                                            "Content-Range: bytes %d-%d/%d\r\n"+
                                            "Content-Length: %d\r\n"+
                                            "Last-Modified: Mon, 18 Mar 2019 12:57:33 GMT\r\n\r\n";

    public static String getCode200(long nspFileSize){
        return String.format(CODE_200, ZonedDateTime.now(ZoneId.of("GMT")).format(DateTimeFormatter.RFC_1123_DATE_TIME), nspFileSize-1, nspFileSize, nspFileSize);
    }
    public static String getCode206(long nspFileSize, long startPos, long endPos){
        return String.format(CODE_206, ZonedDateTime.now(ZoneId.of("GMT")).format(DateTimeFormatter.RFC_1123_DATE_TIME), startPos, endPos, nspFileSize, endPos-startPos+1);
    }

}
