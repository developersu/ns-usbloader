package nsusbloader.Controllers;

import nsusbloader.NSLDataTypes.EFileStatus;

import java.io.File;

public class NSLRowModel {

    private String status;
    private File nspFile;
    private String nspFileName;
    private long nspFileSize;
    private boolean markForUpload;

    NSLRowModel(File nspFile, boolean checkBoxValue){
        this.nspFile = nspFile;
        this.markForUpload = checkBoxValue;
        this.nspFileName = nspFile.getName();
        this.nspFileSize = nspFile.length();
        this.status = "";
    }
    // Model methods start
    public String getStatus(){
        return status;
    }
    public String getNspFileName(){
        return nspFileName;
    }
    public long getNspFileSize() {
        return nspFileSize;
    }
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
            case INDETERMINATE:
                this.status = "...";
                break;
            case UNKNOWN:
                this.status = "Unknown";
                break;
            case INCORRECT_FILE_FAILED:
                this.status = "Failed: Bad file";
                markForUpload = false;
                break;
        }
    }
}
