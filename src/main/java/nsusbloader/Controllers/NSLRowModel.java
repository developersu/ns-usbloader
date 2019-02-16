package nsusbloader.Controllers;

import nsusbloader.NSLDataTypes.FileStatus;

import java.io.File;

public class NSLRowModel {

    private String status;       // 0 = unknown, 1 = uploaded, 2 = bad file
    private File nspFile;
    private String nspFileName;
    private String nspFileSize;
    private boolean markForUpload;

    NSLRowModel(File nspFile, boolean checkBoxValue){
        this.nspFile = nspFile;
        this.markForUpload = checkBoxValue;
        this.nspFileName = nspFile.getName();
        this.nspFileSize = String.format("%.2f", nspFile.length()/1024.0/1024.0);
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

    public void setStatus(FileStatus status){                               // TODO: Localization
        switch (status){
            case FAILED:
                this.status = "Upload failed";
                break;
            case UPLOADED:
                this.status = "Uploaded";
                markForUpload = false;
                break;
            case INCORRECT:
                this.status = "File incorrect";
                markForUpload = false;
                break;
        }

    }
    public File getNspFile(){
        return nspFile;
    }
}
