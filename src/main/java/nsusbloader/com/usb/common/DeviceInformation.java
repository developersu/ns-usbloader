/*
    Copyright 2019-2026 Dmitry Isaenko

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

import org.usb4java.*;

import java.util.ArrayList;
import java.util.List;

public class DeviceInformation {
    private static final byte IN_ENDPOINT_ADDRESS = (byte) 0x81;
    private static final byte OUT_ENDPOINT_ADDRESS = 1;

    private Device device;
    private ConfigDescriptor configDescriptor;
    private final List<NsUsbInterface> interfacesInformation = new ArrayList<>();

    private DeviceInformation() {}

    public static DeviceInformation build(DeviceHandle handler) throws Exception {
        return DeviceInformation.build(LibUsb.getDevice(handler));
    }
    public static DeviceInformation build(Device device) throws Exception {
        var deviceInformation = new DeviceInformation();
        deviceInformation.device = device;
        deviceInformation.claimConfigurationDescriptor();
        deviceInformation.collectInterfaces();
        deviceInformation.freeConfigurationDescriptor();
        return deviceInformation;
    }

    private void claimConfigurationDescriptor() throws Exception {
        configDescriptor = new ConfigDescriptor();
        int returningValue = LibUsb.getActiveConfigDescriptor(device, configDescriptor);

        if (returningValue != LibUsb.SUCCESS)
            throw new Exception("Get Active config descriptor failed: " + LibUsb.errorName(returningValue));
    }

    private void collectInterfaces() {
        for (var iface: configDescriptor.iface())
            interfacesInformation.add(new NsUsbInterface(iface));
    }

    private void freeConfigurationDescriptor() {
        LibUsb.freeConfigDescriptor(configDescriptor);
    }

    /** Bulk transfer endpoint IN */
    public NsUsbEndpointDescriptor getSimplifiedDefaultEndpointDescriptorIn() throws Exception {
        return getSimplifiedDefaultEndpointDescriptor(true);
    }
    /** Bulk transfer endpoint OUT */
    public NsUsbEndpointDescriptor getSimplifiedDefaultEndpointDescriptorOut() throws Exception {
        return getSimplifiedDefaultEndpointDescriptor(false);
    }

    private NsUsbEndpointDescriptor getSimplifiedDefaultEndpointDescriptor(boolean isDescriptorIn) throws Exception {
        byte endpointAddress = isDescriptorIn ?
                IN_ENDPOINT_ADDRESS :
                OUT_ENDPOINT_ADDRESS;

        var endpointDescriptors = interfacesInformation.getFirst()
                .getInterfaceDescriptors()[0]
                .getEndpointDescriptors();

        for (var epDescriptor : endpointDescriptors) {
            if (epDescriptor.getbEndpointAddress() == endpointAddress)
                return epDescriptor;
        }
        throw new Exception("No %s endpoint descriptors found on default interface".formatted(
                (isDescriptorIn ? "IN" : "OUT")));
    }
}
