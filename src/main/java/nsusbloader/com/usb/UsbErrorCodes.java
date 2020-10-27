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
package nsusbloader.com.usb;

import org.usb4java.LibUsb;

public class UsbErrorCodes {
    public static String getErrCode(int value){
        switch (value){
            case LibUsb.ERROR_ACCESS:
                return "ERROR_ACCESS";
            case LibUsb.ERROR_BUSY:
                return "ERROR_BUSY";
            case LibUsb.ERROR_INTERRUPTED:
                return "ERROR_INTERRUPTED";
            case LibUsb.ERROR_INVALID_PARAM:
                return "ERROR_INVALID_PARAM";
            case LibUsb.ERROR_IO:
                return "ERROR_IO";
            case LibUsb.ERROR_NO_DEVICE:
                return "ERROR_NO_DEVICE";
            case LibUsb.ERROR_NO_MEM:
                return "ERROR_NO_MEM";
            case LibUsb.ERROR_NOT_FOUND:
                return "ERROR_NOT_FOUND";
            case LibUsb.ERROR_NOT_SUPPORTED:
                return "ERROR_NOT_SUPPORTED";
            case LibUsb.ERROR_OTHER:
                return "ERROR_OTHER";
            case LibUsb.ERROR_OVERFLOW:
                return "ERROR_OVERFLOW";
            case LibUsb.ERROR_PIPE:
                return "ERROR_PIPE";
            case LibUsb.ERROR_TIMEOUT:
                return "ERROR_TIMEOUT";
            default:
                return Integer.toString(value);
        }
    }
}
