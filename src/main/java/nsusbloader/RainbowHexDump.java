package nsusbloader;

import java.nio.charset.StandardCharsets;

/**
 * Debug tool like hexdump <3
 */
public class RainbowHexDump {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLACK = "\u001B[30m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";


    public static void hexDumpUTF8(byte[] byteArray){
        System.out.print(ANSI_BLUE);
        for (int i=0; i < byteArray.length; i++)
            System.out.print(String.format("%02d-", i%100));
        System.out.println(">"+ANSI_RED+byteArray.length+ANSI_RESET);
        for (byte b: byteArray)
            System.out.print(String.format("%02x ", b));
        //System.out.println();
        System.out.print("\t\t\t"
                + new String(byteArray, StandardCharsets.UTF_8)
                + "\n");
    }

    public static void hexDumpUTF16LE(byte[] byteArray){
        System.out.print(ANSI_BLUE);
        for (int i=0; i < byteArray.length; i++)
            System.out.print(String.format("%02d-", i%100));
        System.out.println(">"+ANSI_RED+byteArray.length+ANSI_RESET);
        for (byte b: byteArray)
            System.out.print(String.format("%02x ", b));
        System.out.print("\t\t\t"
                + new String(byteArray, StandardCharsets.UTF_16LE)
                + "\n");
    }
}
