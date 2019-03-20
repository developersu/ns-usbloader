# NS-USBloader

NS-USBloader is a PC-side TinFoil (USB and Network) and GoldLeaf (USB) NSP installer. Replacement for default **usb_install_pc.py**, **remote_install_pc.py** *(never ever use this. even if you brave. no idea why it works.)* and **GoldTree**.

With GUI and cookies. Wokrs on Windows, macOS and Linux.

Sometimes I add new posts [on my home page](https://developersu.blogspot.com/search/label/NS-USBloader) about this project.

![Screenshot](https://farm8.staticflickr.com/7834/47133893471_37fd9689c4_o.png)

### License

Source code spreads under the GNU General Public License v.3. You can find it in LICENSE file.

### Used libraries
* [OpenJFX](https://wiki.openjdk.java.net/display/OpenJFX/Main)
* [usb4java](https://mvnrepository.com/artifact/org.usb4java/usb4java)
* Few icons taken from: [materialdesignicons](http://materialdesignicons.com/)

### System requirements

JRE 8u60 or higher.

### Usage
#### How to start it on..
##### Linux:

1. Install JRE/JDK 8u60 or higher (openJDK is good. Oracle's one is also good). JavaFX not needed, if you're interested (it's embedded).

2. `root # java -jar /path/to/NS-USBloader.jar`

##### macOS

Double-click on downloaded .jar file. Follow instructions. Or see 'Linux' section.

Set 'Security & Privacy' settings if needed.

If you use different MacOS (not Mojave) - check release section for another JAR file.

##### Windows: 

* Download Zadig: [https://zadig.akeo.ie/](https://zadig.akeo.ie/)
* Open TinFoil. Set 'Title Management' -> 'Usb install NSP'
* Connect NS to PC
* Open Zadig
* Click 'Options' and select 'List All Devices'
* Select NS in dropdown, select 'libusbK (v3.0.7.0)' (version may vary), click 'Install WCID Driver'
* Check that in device list of you system you have 'libusbK USB Devices' folder and your NS inside of it
* Download and install Java JRE (8+)
* Get this application (JAR file) double-click on on it (alternatively open 'cmd', go to place where jar located and execute via `java -jar thisAppName.jar`)
* Remember to have fun!

#### And how to use it?

The first thing you should do it install TinFoil ([Adubbz](https://github.com/Adubbz/Tinfoil/)) or GoldLeaf ([XorTroll](https://github.com/XorTroll/Goldleaf)) on your NS. I recommend using TinFoil, but it ups to you. Take a look on app, find where is the option to install from USB and/or Network. Maybe [this article](https://developersu.blogspot.com/2019/02/ns-usbloader-en.html) will be helpful.

Here is the version of 'not perfect but anyway' [tinfoil I use](https://cloud.mail.ru/public/DwbX/H8d2p3aYR).
Ok, I'm almost sure that this version has bugs. I don't remember where I downloaded it. But it works for me somehow. 

Let's rephrase, if you have working version of TinFoil **DO NOT** use this one. Ok. let's begin.

There are three tabs. Firs one is main.

##### First tab.

At the top of you selecting from drop-down application and protocol that you're going to use. For GoldLeaf only USB is available. Lamp icon stands for switching themes (light or dark).

Then you may drag-n-drop folder with NSPs OR files to application or use 'Select NSP files' button. Multiple selection for files available. Click it again and select files from another folder it you want, it will be added into the table.

Table.

There you can select checkbox for files that will be send to application (TF/GL). Since GoldLeaf allow you only one file transmission per time, only one file is available for selection. Also you can use space to select/un-select files and 'delete' button for deleting. By right-mouse-click you can see context menu where you can delete one OR all items from the table.

##### Second tab.

Here you can configure settings for network file transmission. Usually you shouldn't change anything. But it you're cool hacker, go ahead! The most interesting option here is 'Don't serve requests'. Architecture of the TinFoil networking is working interesting way. When you select in TF network NSP transfer, application will wait at port 2000 for the information about where should it take files from. Like '192.168.1.5:6060/my_file.nsp'. Usually NS-USBloader serves requests by implementing simplified HTTP server and bringing it up and so on. But if this option selected, you can define path to remote location of the files. For example if you set in settings 'shared.lan:80/ROMS/NS/' and add in table file 'my file.nsp' then NS-USBloader will simply tell TinFoil "Hey, go take files from 'shared.lan:80/ROMS/NS/my+file.nsp' ". Of course you have to bring 'shared.lan' host up and make file accessible from such address. All this requires more investigation. BTW, the issue could be that NS-USBloader encodes 'space' char as '+' and some web-servers understand 'space' as '%20D'. It could be fixed in later versions of NS-USBloader if I go deeper in it or you leave me feedback with information/request. As I said, this feature is interesting, but I guess won't be popular.

##### Third tab.

That's where all logs dropped. Verbose information about transmissions comes here.

Why when 'Net' once started it never ends?

Because there is HTTP server inside of application. It can't determine the moment when all transmissions finished (unless they failed). So you have to look on your NS screen and 'Interrupt' is once done.

### Tips&tricks
#### Linux: Add user to 'udev' rules to use NS not-from-root-account
```
root # vim /etc/udev/rules.d/99-NS.rules
SUBSYSTEM=="usb", ATTRS{idVendor}=="057e", ATTRS{idProduct}=="3000", GROUP="plugdev"
root # udevadm control --reload-rules && udevadm trigger
```

### Known bugs
* Unable to interrupt transmission when libusb awaiting for read event (when user sent NSP list but didn't selected anything on NS). Also, sometimes, when network transmission started and nothing received from NS.
* Unable to use it on macOS version lower then Mojave. See: [Failing to claim interface on OSX](https://github.com/developersu/ns-usbloader/issues/2). Could be solved by using different build (different JAR).

#### NOTES
Table 'Status' = 'Uploaded' does not mean that file installed. It means that it has been sent to NS without any issues! That's what this app about. 
Handling successful/failed installation is a purpose of the other side application (TinFoil/GoldLeaf). (And they don't provide any feedback interfaces so I can't detect success/failure.)

### Translators! Traductores! Übersetzer! Թարգմանիչներ!
If you want to see this app translated to your language, go grab [this file](https://github.com/developersu/ns-usbloader/blob/master/src/main/resources/locale.properties) and translate it.
Upload somewhere (pastebin? google drive? whatever else). [Create new issue](https://github.com/developersu/ns-usbloader/issues) and post a link. I'll grab it and add.

#### Thanks for great work done by our translater~~s team~~!

Français by [Stephane Meden (JackFromNice)](https://github.com/JackFromNice) 


#### TODO (maybe):
- [x] macOS QA v0.1  (Mojave)
- [x] macOS QA v0.2.2 (Mojave)
- [ ] macOS QA v0.3 (Mojave, High Sierra)
- [x] Windows support
- [x] code refactoring
- [x] GoldLeaf support
- [ ] XCI support
- [ ] File order sort (non-critical)
- [ ] More deep file analyze before uploading.
- [x] Network mode support for TinFoil

#### Thanks
Appreciate assistance and support of both Vitaliy and Konstantin. Without you all this magic would not have happened.

[Konstanin Kelemen](https://github.com/konstantin-kelemen)

[Vitaliy Natarov](https://github.com/SebastianUA)