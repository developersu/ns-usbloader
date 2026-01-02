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
package nsusbloader.com;

import java.nio.ByteBuffer;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

public class DataConvertUtils {
    /**
     * Convert INT (Little endian) value to bytes-array representation
     * */
    public static byte[] intToArrLE(int value){
        return ByteBuffer.allocate(Integer.BYTES).order(LITTLE_ENDIAN)
                .putInt(value)
                .array();
    }
    /**
     * Convert LONG (Little endian) value to bytes-array representation
     * */
    public static byte[] longToArrLE(long value){
        return ByteBuffer.allocate(Long.BYTES).order(LITTLE_ENDIAN)
                .putLong(value)
                .array();
    }
    /**
     * Convert bytes-array to INT value (Little endian)
     * */
    public static int arrToIntLE(byte[] byteArrayWithInt, int intStartPosition){
        return ByteBuffer.wrap(byteArrayWithInt).order(LITTLE_ENDIAN)
                .getInt(intStartPosition);
    }
    /**
     * Convert bytes-array to LONG value (Little endian)
     * */
    public static long arrToLongLE(byte[] byteArrayWithLong, int intStartPosition){
        return ByteBuffer.wrap(byteArrayWithLong).order(LITTLE_ENDIAN)
                .getLong(intStartPosition);
    }
}
