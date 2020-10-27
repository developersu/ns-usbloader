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
package nsusbloader.com.usb.common;

import org.usb4java.InterfaceDescriptor;

public class NsUsbInterfaceDescriptor {
    private final byte bLength;
    private final byte bDescriptorType;
    private final byte bInterfaceNumber;
    private final byte bAlternateSetting;
    private final byte bNumEndpoints;
    private final byte bInterfaceClass;
    private final byte bInterfaceSubClass;
    private final byte bInterfaceProtocol;
    private final byte iInterface;
    //private final int extralen;
    //private final ByteBuffer extra;
    private final NsUsbEndpointDescriptor[] endpointDescriptors;
    
    NsUsbInterfaceDescriptor(InterfaceDescriptor interfaceDescriptor){
        this.bLength = interfaceDescriptor.bLength();
        this.bDescriptorType = interfaceDescriptor.bDescriptorType();
        this.bInterfaceNumber = interfaceDescriptor.bInterfaceNumber();
        this.bAlternateSetting = interfaceDescriptor.bAlternateSetting();
        this.bNumEndpoints = interfaceDescriptor.bNumEndpoints();
        this.bInterfaceClass = interfaceDescriptor.bInterfaceClass();
        this.bInterfaceSubClass = interfaceDescriptor.bInterfaceSubClass();
        this.bInterfaceProtocol = interfaceDescriptor.bInterfaceProtocol();
        this.iInterface = interfaceDescriptor.iInterface();

        this.endpointDescriptors = NsUsbEndpointDescriptorUtils.convertFromNatives(interfaceDescriptor.endpoint());
    }

    public byte getbLength() {
        return bLength;
    }

    public byte getbDescriptorType() {
        return bDescriptorType;
    }

    public byte getbInterfaceNumber() {
        return bInterfaceNumber;
    }

    public byte getbAlternateSetting() {
        return bAlternateSetting;
    }

    public byte getbNumEndpoints() {
        return bNumEndpoints;
    }

    public byte getbInterfaceClass() {
        return bInterfaceClass;
    }

    public byte getbInterfaceSubClass() {
        return bInterfaceSubClass;
    }

    public byte getbInterfaceProtocol() {
        return bInterfaceProtocol;
    }

    public byte getiInterface() {
        return iInterface;
    }

    public NsUsbEndpointDescriptor[] getEndpointDescriptors() {
        return endpointDescriptors;
    }
}
