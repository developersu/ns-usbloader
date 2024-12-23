/*
    Copyright 2019-2024 Dmitry Isaenko
     
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
package nsusbloader.Controllers;

import nsusbloader.NSLDataTypes.EFileStatus;

import java.util.Collections;
import java.util.Map;

public class Payload {
    private final String message;
    private final Map<String, EFileStatus> statusMap;

    public Payload(){
        this("");
    }
    public Payload(String message){
        this(message, Collections.emptyMap());
    }
    public Payload(String message, Map<String, EFileStatus> statusMap){
        this.message = message;
        this.statusMap = statusMap;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, EFileStatus> getStatusMap() {
        return statusMap;
    }
}
