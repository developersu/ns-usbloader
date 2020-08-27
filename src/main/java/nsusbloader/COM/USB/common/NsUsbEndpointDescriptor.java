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
package nsusbloader.COM.USB.common;

import org.usb4java.EndpointDescriptor;

public class NsUsbEndpointDescriptor {
    private final byte bLength;
    private final byte bDescriptorType;
    private final byte bEndpointAddress;
    private final byte bmAttributes;
    //Ignoring: Transfer Type,  Synch Type, Usage Type
    private final short wMaxPacketSize;
    private final byte bInterval;

    NsUsbEndpointDescriptor(EndpointDescriptor endpointDescriptor){
        this.bLength = endpointDescriptor.bLength();
        this.bDescriptorType = endpointDescriptor.bDescriptorType();
        this.bEndpointAddress = endpointDescriptor.bEndpointAddress();
        this.bmAttributes = endpointDescriptor.bmAttributes();
        this.wMaxPacketSize = endpointDescriptor.wMaxPacketSize();
        this.bInterval = endpointDescriptor.bInterval();
    }

    public byte getbLength() {
        return bLength;
    }

    public byte getbDescriptorType() {
        return bDescriptorType;
    }

    public byte getbEndpointAddress() {
        return bEndpointAddress;
    }

    public byte getBmAttributes() {
        return bmAttributes;
    }

    public short getwMaxPacketSize() {
        return wMaxPacketSize;
    }

    public byte getbInterval() {
        return bInterval;
    }
}
