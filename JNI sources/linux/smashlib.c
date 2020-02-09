/* NS-USBloader - native libraries for 'special purposes'
 * Copyright (C) 2020  Dmitry Isaenko
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <linux/usb/ch9.h>
#include <errno.h>

#include "nsusbloader_Utilities_RcmSmash.h"

struct usbdevfs_urb urb;

JNIEXPORT jint JNICALL Java_nsusbloader_Utilities_RcmSmash_smashLinux
  (JNIEnv * jni_env, jclass this_class, jint bus_id, jint device_addr){
    int ret_value;

    char *usb_path = (char*)malloc(24 * sizeof(char));

    sprintf(usb_path, "/dev/bus/usb/%03d/%03d", bus_id, device_addr);
    int fd = open(usb_path, O_RDWR);
    if (fd == -1)
        return -1;
    
    struct usb_ctrlrequest* ctrl_req;

    __u8* buf[0x7000+sizeof(ctrl_req)];
    
    ctrl_req = (struct usb_ctrlrequest *) buf;
        ctrl_req->bRequestType = 0x82;
        ctrl_req->bRequest = USB_REQ_GET_STATUS;
        ctrl_req->wValue = 0;
        ctrl_req->wIndex = 0;
        ctrl_req->wLength = 0x7000;

    memset(&urb, 0, sizeof(urb));
        urb.type = USBDEVFS_URB_TYPE_CONTROL;
     	urb.endpoint = USB_DIR_IN | 0;
     	urb.buffer = buf;
     	urb.buffer_length = sizeof(buf);
    //Submit request
    ret_value = ioctl(fd, USBDEVFS_SUBMITURB, &urb);
    // If we failed on this step, it's a VERY bad sign. Nothing to do, let's report failure.
    if (ret_value != 0)
        return ret_value;
    // Wait 1/4 sec
    usleep(250000);
    struct usbdevfs_urb urb1;
    // Let's pick reply (everybody does it, right? In non-blocking manner.)
    ret_value = ioctl(fd, USBDEVFS_REAPURBNDELAY, &urb1);
    if (ret_value < 0){
        if (errno == EAGAIN){ // In case of resource temporarily unavailable
            // Wired.. so much time left. Let's cancel it!
            ret_value = ioctl(fd, USBDEVFS_DISCARDURB, &urb);
            // And wait a bit more..
            usleep(40000);
            // And try to pick reply. Yes, it's still possible. See /usr/src/linux/drivers/usb/core/devio.c
            ret_value = ioctl(fd, USBDEVFS_REAPURBNDELAY, &urb1);
        }
    }
    // Leftovers.
    free(usb_path);   
    // Let's try to close device, but even if we fail with this, nvm.
    close(fd);  // So we won't even write returned value somewhere.
    return 0;
}

JNIEXPORT jint JNICALL Java_nsusbloader_Utilities_RcmSmash_smashWindows
    (JNIEnv * jni_env, jclass this_class){
  return -1;
  }
