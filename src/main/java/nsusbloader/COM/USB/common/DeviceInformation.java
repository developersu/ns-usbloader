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

import nsusbloader.COM.USB.UsbErrorCodes;
import org.usb4java.*;

import java.util.ArrayList;
import java.util.List;

public class DeviceInformation {
    private static final byte DEFAULT_IN_EP_ADDRESS = -127; // 0x81
    private static final byte DEFAULT_OUT_EP_ADDRESS = 1;

    private Device device;
    private ConfigDescriptor configDescriptor;
    private List<NsUsbInterface> interfacesInformation = new ArrayList<>();

    private DeviceInformation(){}

    public static DeviceInformation build(DeviceHandle handler) throws Exception{
        Device device = LibUsb.getDevice(handler);
        return DeviceInformation.build(device);
    }
    public static DeviceInformation build(Device device) throws Exception{
        DeviceInformation deviceInformation = new DeviceInformation();
        deviceInformation.device = device;
        deviceInformation.claimConfigurationDescriptor();
        deviceInformation.collectInterfaces();
        deviceInformation.freeConfigurationDescriptor();
        return deviceInformation;
    }

    private void claimConfigurationDescriptor() throws Exception{
        configDescriptor = new ConfigDescriptor();
        int returningValue = LibUsb.getActiveConfigDescriptor(device, configDescriptor);

        if (returningValue != LibUsb.SUCCESS)
            throw new Exception("Get Active config descriptor failed: "+ UsbErrorCodes.getErrCode(returningValue));
    }

    private void collectInterfaces(){
        for (Interface intrface : configDescriptor.iface())
            interfacesInformation.add(new NsUsbInterface(intrface));
    }

    private void freeConfigurationDescriptor(){
        LibUsb.freeConfigDescriptor(configDescriptor);
    }

    /** Bulk transfer endpoint IN */
    public NsUsbEndpointDescriptor getSimplifiedDefaultEndpointDescriptorIn() throws Exception{
        return getSimplifiedDefaultEndpointDescriptor(true);
    }
    /** Bulk transfer endpoint OUT */
    public NsUsbEndpointDescriptor getSimplifiedDefaultEndpointDescriptorOut() throws Exception{
        return getSimplifiedDefaultEndpointDescriptor(false);
    }

    private NsUsbEndpointDescriptor getSimplifiedDefaultEndpointDescriptor(boolean isDescriptorIN) throws Exception{
        byte endpointAddress;

        if (isDescriptorIN)
            endpointAddress = DEFAULT_IN_EP_ADDRESS;
        else
            endpointAddress = DEFAULT_OUT_EP_ADDRESS;

        NsUsbInterface nsUsbInterface = interfacesInformation.get(0);

        NsUsbInterfaceDescriptor firstInterfaceDescriptor = nsUsbInterface.getInterfaceDescriptors()[0];
        NsUsbEndpointDescriptor[] endpointDescriptors = firstInterfaceDescriptor.getEndpointDescriptors();

        for (NsUsbEndpointDescriptor epDescriptor : endpointDescriptors){
            if (epDescriptor.getbEndpointAddress() == endpointAddress)
                return epDescriptor;
        }
        throw new Exception("No "+(isDescriptorIN?"IN":"OUT")+" endpoint descriptors found on default interface");
    }
}
