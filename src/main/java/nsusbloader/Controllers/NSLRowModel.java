/*
    Copyright 2019-2020 Dmitry Isaenko

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
package nsusbloader.Controllers;

import nsusbloader.AppPreferences;
import nsusbloader.NSLDataTypes.EFileStatus;

import java.io.File;
import java.util.Locale;
import java.util.ResourceBundle;

public class NSLRowModel {
    private Locale userLocale = AppPreferences.getInstance().getLocale();
    private ResourceBundle rb = ResourceBundle.getBundle("locale", userLocale);
    private String status;
    private File nspFile;
    private String nspFileName;
    private long nspFileSize;
    private boolean markForUpload;

    NSLRowModel(File nspFile, boolean checkBoxValue){
        this.nspFile = nspFile;
        this.markForUpload = checkBoxValue;
        this.nspFileName = nspFile.getName();
        if (nspFile.isFile())
            this.nspFileSize = nspFile.length();
        else {
            File[] subFilesArr = nspFile.listFiles((file, name) -> name.matches("[0-9]{2}"));
            if (subFilesArr != null) {
                for (File subFile : subFilesArr)
                    this.nspFileSize += subFile.length();
            }
        }
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
    public void setStatus(EFileStatus status){
        switch (status){
            case UPLOADED:
                this.status = rb.getString("tab1_table_Lbl_Success");
                markForUpload = false;
                break;
            case FAILED:
                this.status = rb.getString("tab1_table_Lbl_Failed");
                break;
            case INDETERMINATE:
                this.status = "...";
                break;
            case UNKNOWN:
                this.status = rb.getString("tab1_table_Lbl_Unknown");
                break;
            case INCORRECT_FILE_FAILED:
                this.status = rb.getString("tab1_table_Lbl_BadFile");
                markForUpload = false;
                break;
        }
    }
}
