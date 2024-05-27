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
package nsusbloader;

import nsusbloader.Controllers.ISubscriber;
import nsusbloader.Controllers.Payload;
import nsusbloader.NSLDataTypes.EModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TransfersPublisher {
    private final AtomicBoolean isTransferActive = new AtomicBoolean(false);

    private final List<ISubscriber> subscribers = new ArrayList<>();

    public TransfersPublisher(ISubscriber... subscriber){
        subscribers.addAll(Arrays.asList(subscriber));
    }

    public void setTransferActive(EModule appModuleType, boolean isActive, Payload payload) {
        isTransferActive.set(isActive);
        subscribers.forEach(s->s.notify(appModuleType, isActive, payload));
    }

    public boolean getTransferActive() {
        return isTransferActive.get();
    }
}
