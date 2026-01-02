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

import java.nio.charset.StandardCharsets;

import static nsusbloader.com.DataConvertUtils.arrToIntLE;

/* Separated from interface for easier fixes/replacement in future  */
public class GlString010 implements GlString {

    private final int length;
    private final String string;

    public GlString010(byte[] inputBytes, int startPosition){
        this.length = arrToIntLE(inputBytes, startPosition);
        this.string = new String(inputBytes, startPosition+4, length, StandardCharsets.UTF_8);
    }

    public int length(){
        return length;
    }

    @Override
    public String toString(){
        return string;
    }
}
