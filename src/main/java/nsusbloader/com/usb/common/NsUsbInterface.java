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

import org.usb4java.Interface;
import org.usb4java.InterfaceDescriptor;

import java.util.LinkedList;

/**
 * Adapter for easier access to USB devices which has only one interface with one interface descriptor (BULK)
 *
 * After few JVM failed to core few 'holders' were added: such as NsUsbEndpoint descriptor and NsUsbInterfaceDescriptor
 * */

public class NsUsbInterface {
    private final Interface iface;
    private final LinkedList<NsUsbInterfaceDescriptor> interfaceDescriptors;

    public NsUsbInterface(Interface iface){
        this.iface = iface;
        this.interfaceDescriptors = new LinkedList<>();
        collectDescriptors();
    }

    private void collectDescriptors(){
        for (InterfaceDescriptor ifaceDescriptor : iface.altsetting()){
            interfaceDescriptors.add(new NsUsbInterfaceDescriptor(ifaceDescriptor));
        }
    }
    public NsUsbInterfaceDescriptor[] getInterfaceDescriptors(){
        return interfaceDescriptors.toArray(new NsUsbInterfaceDescriptor[0]);
    }
}
