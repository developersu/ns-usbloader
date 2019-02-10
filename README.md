# NS-USBloader

NS-USBloader is a PC-side tinfoil NSP USB uploader. Replacement for default usb_install_pc.py
With GUI and cookies.

## License

Source code spreads under the GNU General Public License v.3. You can find it in LICENSE file.

## Used libraries
* OpenJFX https://wiki.openjdk.java.net/display/OpenJFX/Main
* usb4java: https://mvnrepository.com/artifact/org.usb4java/usb4java

## Tips&tricks
### Add user to udev rules to use NS non-root:
root # vim /etc/udev/rules.d/99-NS.rules
SUBSYSTEM=="usb", ATTRS{idVendor}=="057e", ATTRS{idProduct}=="3000", GROUP="plugdev"
root # udevadm control --reload-rules && udevadm trigger

## Known bugs
* Unable to interrupt transmission when libusb awaiting for read event (when user sent NSP list but didn't selected anything on NS).

## TODO:
- [ ] macOS QA
- [ ] Windows support