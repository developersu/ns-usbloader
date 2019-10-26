package nsusbloader.COM.USB;

import org.usb4java.LibUsb;

class UsbErrorCodes {
    static String getErrCode(int value){
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
