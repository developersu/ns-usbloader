/* Copyright WTFPL */
package integration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

public class Environment {
    public final String CONTAINER = "environment.txt";

    private String atmosphere;
    private String prodkeys;
    private String firmwares;
    private String saveTo;

    public Environment() throws Exception{
        if (Files.notExists(Path.of(CONTAINER))) {
            boolean createdTemplate = createTemplate();
            throw new Exception("'environment.txt' not found\n" +
                    "Please "+(createdTemplate?"":"create and ") +
                    "set values in file");
        }

        read();

        if (isNotValid())
            throw new Exception("'environment.txt' doesn't contain valid data\n");
    }
    private void read() throws Exception{
        HashMap<String, String> rawKeySet = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(CONTAINER))) {
            String fileLine;
            String[] keyValue;
            while ((fileLine = br.readLine()) != null) {
                keyValue = fileLine.trim().split("\\s+?=\\s+?", 2);
                if (keyValue.length == 2)
                    rawKeySet.put(keyValue[0], keyValue[1]);
            }
        }

        atmosphere = rawKeySet.get("ATMOSPHERE");
        prodkeys = rawKeySet.get("PRODKEYS");
        firmwares = rawKeySet.get("NS_GLOBAL_FIRMWARES");
        saveTo = rawKeySet.get("SAVE_TO");
    }
    private boolean isNotValid(){
        return atmosphere == null || atmosphere.isBlank() ||
                prodkeys == null || prodkeys.isBlank() ||
                firmwares == null || firmwares.isBlank();
    }
    private boolean createTemplate(){
        try(FileWriter writer = new FileWriter(CONTAINER)){
            writer.write(
                    "ATMOSPHERE = \n" +
                    "PRODKEYS = \n" +
                    "NS_GLOBAL_FIRMWARES = \n" +
                    "SAVE_TO = /tmp");
        }
        catch (Exception e){
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public String getAtmosphereLocation() {
        return atmosphere;
    }

    public String getProdkeysLocation() {
        return prodkeys;
    }

    public String getFirmwaresLocation() {
        return firmwares;
    }

    public String getSaveToLocation() {
        return saveTo;
    }
}
