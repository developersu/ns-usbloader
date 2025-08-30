/*
    Copyright 2019-2025 Dmitry Isaenko
     
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
package nsusbloader.com.usb.gl;

public enum GoldleafCmd {
    GetDriveCount((byte) 1),
    GetDriveInfo((byte) 2),
    StatPath((byte) 3),
    GetFileCount((byte) 4),
    GetFile((byte) 5),
    GetDirectoryCount((byte) 6),
    GetDirectory((byte) 7),
    StartFile((byte) 8),
    ReadFile((byte) 9),
    WriteFile((byte) 10),
    EndFile((byte) 11),
    Create((byte) 12),
    Delete((byte) 13),
    Rename((byte) 14),
    GetSpecialPathCount((byte) 15),
    GetSpecialPath((byte) 16),
    SelectFile((byte) 17),
    CMD_UNKNOWN((byte) 255);

    private final byte id;

    GoldleafCmd(byte id) {
        this.id = id;
    }

    public static GoldleafCmd get(byte id) {
        for(GoldleafCmd cmd : values()) {
            if(cmd.id == id)
                return cmd;
        }
        return CMD_UNKNOWN;
    }
}