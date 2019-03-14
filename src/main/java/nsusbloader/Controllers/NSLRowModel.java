package nsusbloader.Controllers;

import nsusbloader.NSLDataTypes.EFileStatus;

import java.io.File;

public class NSLRowModel {

    private String status;
    private File nspFile;
    private String nspFileName;
    private String nspFileSize;
    private boolean markForUpload;

    NSLRowModel(File nspFile, boolean checkBoxValue){
        this.nspFile = nspFile;
        this.markForUpload = checkBoxValue;
        this.nspFileName = nspFile.getName();
        if (nspFile.length()/1024.0/1024.0/1024.0 > 1)
            this.nspFileSize = String.format("%.2f", nspFile.length()/1024.0/1024.0/1024.0)+" GB";
        else if (nspFile.length()/1024.0/1024.0 > 1)
            this.nspFileSize = String.format("%.2f", nspFile.length()/1024.0/1024.0)+" MB";
        else
            this.nspFileSize = String.format("%.2f", nspFile.length()/1024.0)+" kB";
        this.status = "";
    }
    // Model methods start
    public String getStatus(){
        return status;
    }
    public String getNspFileName(){
        return nspFileName;
    }
    public String getNspFileSize() { return nspFileSize; }
    public boolean isMarkForUpload() {
        return markForUpload;
    }
    // Model methods end

    public void setMarkForUpload(boolean value){
        markForUpload = value;
    }
    public File getNspFile(){ return nspFile; }
    public void setStatus(EFileStatus status){                               // TODO: Localization
        switch (status){
            case UPLOADED:
                this.status = "Success";
                markForUpload = false;
                break;
            case FAILED:
                this.status = "Failed";
                break;
            case UNKNOWN:
                this.status = "...";
                break;
            case INCORRECT_FILE_FAILED:
                this.status = "Failed: Incorrect file";
                markForUpload = false;
                break;
        }
    }
}
