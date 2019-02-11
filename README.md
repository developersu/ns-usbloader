# NS-USBloader

NS-USBloader is a PC-side tinfoil NSP USB uploader. Replacement for default usb_install_pc.py
With GUI and cookies.
Read more: https://developersu.blogspot.com/2019/02/ns-usbloader-en.html

## License

Source code spreads under the GNU General Public License v.3. You can find it in LICENSE file.

## Requirements

JRE 8 or higher. See below.

## Used libraries
* OpenJFX https://wiki.openjdk.java.net/display/OpenJFX/Main
* usb4java: https://mvnrepository.com/artifact/org.usb4java/usb4java
* Few icons taken from: http://materialdesignicons.com/

## Usage
### Linux:

Install JRE/JDK 8 or higher (openJDK is good. Oracle's one is also good). JavaFX not needed, if you're interested (it's embedded).

`root # java -jar /path/to/NS-USBloader.jar`

### Windows: 

* Download Zadig: https://zadig.akeo.ie/
* Open tinfoil. Set 'Title Managment' -> 'Usb install NSP'
* Connect NS to pc
* Open Zadig, select NS in dropdown, select 'libusbK (v3.0.7.0)' (version may vary), click 'Install WCID Driver'
* Check that in device list of you system you have 'libusbK USB Devices' folder and your NS inside of it
* Download and install Java JRE (8+)
* Get this application (JAR file) double-click on on it (alternatively open 'cmd', go to place where jar located and execute via 'java -jar thisAppName.jar')
* Remember to have fun!

### macOS

Coming...

## Tips&tricks
### Add user to udev rules to use NS non-root (Linux):
`root # vim /etc/udev/rules.d/99-NS.rules`

`SUBSYSTEM=="usb", ATTRS{idVendor}=="057e", ATTRS{idProduct}=="3000", GROUP="plugdev"`

`root # udevadm control --reload-rules && udevadm trigger`

## Known bugs
* Unable to interrupt transmission when libusb awaiting for read event (when user sent NSP list but didn't selected anything on NS).

## TODO:
- [ ] macOS QA
- [x] Windows support
- [ ] code refactoring